package run.halo.mapay;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.security.web.server.savedrequest.WebSessionServerRequestCache;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.HtmlUtils;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.security.AuthenticationSecurityWebFilter;

/**
 * Authentication filter for Mapay aggregated login (WeChat / QQ / Alipay).
 *
 * <p>Endpoints (all under {@code /login}, which Halo permits anonymously):
 *
 * <ul>
 *   <li>{@code GET /login/mapay/authorize/{type}} — starts the login or bind
 *       flow. Reads the AppID and AppKey from plugin settings, asks Mapay
 *       for an authorisation address and redirects the browser there.
 *       The browser is always sent to the address Mapay returns, for all
 *       three providers; no QR page is rendered in between.</li>
 *   <li>{@code GET /login/mapay/callback?type=..&code=..} — Mapay redirects
 *       here after authorisation. The {@code code} is exchanged server-side
 *       for the verified social user, which is then handed to Halo core as a
 *       Spring {@link OAuth2AuthenticationToken}. Halo core
 *       ({@code MapOAuth2AuthenticationFilter}, present since Halo 2.20.0)
 *       takes it from there: it finds or creates the Halo user, creates the
 *       {@code UserConnection}, establishes the session, remember-me, device
 *       and login logs. This plugin never creates users, sessions or
 *       {@code UserConnection}s itself.</li>
 * </ul>
 *
 * <p>Mapay does not support a {@code state} parameter, so CSRF protection is
 * done with a pending-login marker kept in the current WebSession: the
 * callback is only accepted when this session started an authorisation for
 * the same provider within the last 10 minutes, and the marker is consumed
 * on first use. A callback arriving in a different browser session is
 * rejected, so the login must be finished in the browser that started it.
 *
 * <p>Credentials are never logged and the Mapay request URL (which carries
 * the AppKey as a query parameter) is never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapayAuthWebFilter implements AuthenticationSecurityWebFilter {

    private static final String AUTHORIZE_PREFIX = "/login/mapay/authorize/";

    private static final String CALLBACK_PATH = "/login/mapay/callback";

    private static final String SESSION_PENDING_ATTR = "mapay.pending";

    /** Pending authorisations are only valid for 10 minutes. */
    private static final long PENDING_TTL_MILLIS = 10 * 60 * 1000L;

    private static final List<String> SUPPORTED_TYPES = List.of("wechat", "qq", "alipay");

    /**
     * Halo provider name → Mapay {@code type} value (see
     * https://login.mapay.cn/doc.php): Mapay uses {@code wx}, not
     * {@code wechat}. QQ and Alipay use the same value on both sides.
     */
    private static String mapayType(String provider) {
        return "wechat".equals(provider) ? "wx" : provider;
    }

    private static final String LOGIN_ERROR_URL = "/login?oauth2_error";

    private final MapayApiClient mapayApiClient;

    private final ReactiveSettingFetcher settingFetcher;

    private final ExternalUrlSupplier externalUrlSupplier;

    private final ServerSecurityContextRepository securityContextRepository;

    private final DefaultServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().value();
        if (path.startsWith(AUTHORIZE_PREFIX)) {
            var type = path.substring(AUTHORIZE_PREFIX.length());
            var slash = type.indexOf('/');
            if (slash >= 0) {
                type = type.substring(0, slash);
            }
            if (!SUPPORTED_TYPES.contains(type)) {
                return redirect(exchange, LOGIN_ERROR_URL);
            }
            return handleAuthorize(exchange, type);
        }
        if (CALLBACK_PATH.equals(path)) {
            return handleCallback(exchange, chain);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> handleAuthorize(ServerWebExchange exchange, String type) {
        return loadCredentials()
            .flatMap(credentials -> exchange.getSession()
                .flatMap(session -> {
                    session.getAttributes().put(SESSION_PENDING_ATTR,
                        new PendingLogin(type, System.currentTimeMillis()));
                    return session.save().thenReturn(credentials);
                })
                .flatMap(saved -> mapayApiClient.login(
                    saved.appId(), saved.appKey(), mapayType(type),
                    callbackUrl(exchange, saved.siteUrl()), saved.apiBaseUrl())))
            .flatMap(result -> {
                if (!result.isSuccess()) {
                    log.warn("Mapay login request failed for provider {}: code={}",
                        type, result.getCode());
                    var detail = "聚合登录服务返回 code=" + result.getCode()
                        + (StringUtils.hasText(result.getMessage())
                        ? "（" + result.getMessage() + "）" : "")
                        + "。请检查插件设置里的 AppID 和 AppKey 是否正确，"
                        + "再确认服务商后台的 AppID / AppKey 没有填错。";
                    return renderErrorPage(exchange, "聚合登录失败", detail);
                }
                if (!result.getUrl().startsWith("https://")) {
                    log.warn("Mapay returned a non-https authorisation url for provider {}", type);
                    return renderErrorPage(exchange, "聚合登录失败",
                        "聚合登录服务返回了一个无效的登录地址，请稍后重试，"
                            + "或联系服务商确认 AppID / AppKey 是否正确。");
                }
                // The Mapay doc says to jump to the url it returns (Step 2).
                // An intermediate QR page was tried before and dropped: the
                // qrcode address does not render as an image, so WeChat and
                // Alipay logins stalled there. Redirecting straight to the
                // url lets Mapay's own login page handle it.
                return redirect(exchange, result.getUrl());
            })
            .onErrorResume(e -> {
                log.warn("Failed to start Mapay login for provider {}: {}", type, e.getMessage());
                if (e instanceof IllegalStateException) {
                    // loadCredentials() already phrases the exact missing piece.
                    return renderErrorPage(exchange, "插件还没有配置好", e.getMessage());
                }
                return renderErrorPage(exchange, "连接聚合登录服务失败",
                    "服务器连不上聚合登录服务或请求超时了，请检查服务器网络，"
                        + "或查看 Halo 日志里 Failed to start Mapay login 的那一行。");
            });
    }

    private Mono<Void> handleCallback(ServerWebExchange exchange, WebFilterChain chain) {
        var query = exchange.getRequest().getQueryParams();
        var type = query.getFirst("type");
        var code = query.getFirst("code");
        if (!SUPPORTED_TYPES.contains(type) || !StringUtils.hasText(code)) {
            return redirect(exchange, LOGIN_ERROR_URL);
        }
        return exchange.getSession()
            .flatMap(session -> {
                var pending = (PendingLogin) session.getAttributes().get(SESSION_PENDING_ATTR);
                if (pending == null || !pending.type().equals(type)
                    || System.currentTimeMillis() - pending.createdAtMillis() > PENDING_TTL_MILLIS) {
                    log.warn("Mapay callback rejected for provider {}: no pending login in session", type);
                    return Mono.error(new IllegalStateException("No pending Mapay login in session"));
                }
                session.getAttributes().remove(SESSION_PENDING_ATTR);
                return session.save().thenReturn(type);
            })
            .flatMap(validatedType -> loadCredentials()
                .flatMap(credentials -> mapayApiClient.callback(
                    credentials.appId(), credentials.appKey(), mapayType(validatedType), code,
                    credentials.apiBaseUrl())))
            .flatMap(user -> {
                if (!user.isSuccess()) {
                    log.warn("Mapay callback failed for provider {}: code={}", type, user.getCode());
                    if (user.getCode() == 2) {
                        return renderErrorPage(exchange, "登录还没有完成",
                            "聚合登录服务说这次登录还没完成，请回到本站重新点一次绑定或登录。");
                    }
                    var detail = "聚合登录服务返回 code=" + user.getCode()
                        + (StringUtils.hasText(user.getMessage())
                        ? "（" + user.getMessage() + "）" : "")
                        + "。请检查插件设置里的 AppID 和 AppKey 是否正确，"
                        + "或回到本站重新点一次绑定或登录。";
                    return renderErrorPage(exchange, "聚合登录失败", detail);
                }
                var token = buildToken(type, user);
                return securityContextRepository.save(exchange, new SecurityContextImpl(token))
                    .then(redirectToUserCenter(exchange, chain, token));
            })
            .onErrorResume(e -> {
                log.warn("Failed to handle Mapay callback for provider {}", type);
                if (e instanceof IllegalStateException) {
                    return renderErrorPage(exchange, "这次登录会话已过期",
                        "这次回调不是从本站发起的、或者已经超过 10 分钟，"
                            + "请回到本站重新点一次绑定或登录。");
                }
                return renderErrorPage(exchange, "聚合登录失败",
                    "服务器处理登录回调时出错了，请回到本站重新点一次绑定或登录，"
                        + "或查看 Halo 日志里 Failed to handle Mapay callback 的那一行。");
            });
    }

    /**
     * Builds the token Halo core understands. The registration id must equal
     * the AuthProvider {@code metadata.name}; the principal name
     * ({@code social_uid}) is what core uses as the provider user id.
     * Halo's registration flow reads {@code nickname}/{@code name} for the
     * display name and falls back to a generated username when the nickname
     * is not a valid Halo username.
     */
    private static OAuth2AuthenticationToken buildToken(String type, MapayApiClient.SocialUser user) {
        var nickname = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getSocialUid();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("social_uid", user.getSocialUid());
        attributes.put("nickname", nickname);
        attributes.put("name", nickname);
        if (StringUtils.hasText(user.getFaceimg())) {
            attributes.put("picture", user.getFaceimg());
        }
        var principal = new DefaultOAuth2User(List.of(), attributes, "social_uid");
        return new OAuth2AuthenticationToken(principal, List.of(), type);
    }

    /**
     * Redirects to the user center after login. The redirect only sets status
     * and Location headers; the response is committed later, so Halo core's
     * {@code MapOAuth2AuthenticationFilter} can still overwrite the target
     * with {@code /login?oauth2_select} for users who still need to register.
     */
    private Mono<Void> redirectToUserCenter(ServerWebExchange exchange, WebFilterChain chain,
        OAuth2AuthenticationToken token) {
        var successHandler = new RedirectServerAuthenticationSuccessHandler("/uc");
        successHandler.setRequestCache(new WebSessionServerRequestCache());
        return successHandler.onAuthenticationSuccess(new WebFilterExchange(exchange, chain), token);
    }

    private Mono<Void> redirect(ServerWebExchange exchange, String location) {
        return redirectStrategy.sendRedirect(exchange, URI.create(location));
    }

    /**
     * Renders a plain error page instead of silently bouncing the user.
     * Previously failures redirected to {@code /login?oauth2_error}, which
     * for an already logged-in user (binding from /uc) just bounced straight
     * back to /uc with no explanation. The page never includes credentials.
     */
    private Mono<Void> renderErrorPage(ServerWebExchange exchange, String title, String detail) {
        var safeTitle = HtmlUtils.htmlEscape(title);
        var safeDetail = HtmlUtils.htmlEscape(detail);
        var html = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s</title>
            <style>
              body{margin:0;background:#f7f6f2;color:#1f1e1b;
                font-family:"PingFang SC","Microsoft YaHei",system-ui,sans-serif;
                display:flex;min-height:100vh;align-items:center;justify-content:center}
              .card{background:#fff;border:1px solid #e8e7e3;border-radius:12px;
                padding:32px 40px;text-align:center;max-width:420px}
              h1{font-size:20px;margin:0 0 12px}
              p{font-size:14px;color:#6b6a67;line-height:1.7;text-align:left}
              a.btn{display:inline-block;margin-top:16px;background:#1f1e1b;color:#fff;
                padding:10px 20px;border-radius:8px;text-decoration:none}
            </style></head>
            <body><div class="card">
            <h1>%s</h1>
            <p>%s</p>
            <a class="btn" href="/uc">返回个人中心</a>
            </div></body></html>
            """.formatted(safeTitle, safeTitle, safeDetail);
        var response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.TEXT_HTML);
        var buffer = response.bufferFactory().wrap(html.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Builds the callback URL. Priority: the user-configured site URL in the
     * plugin settings, then Halo's configured external URL, then the current
     * request's own scheme + host. Nothing here is hardcoded to any domain.
     */
    private String callbackUrl(ServerWebExchange exchange, String siteUrl) {
        String base;
        if (StringUtils.hasText(siteUrl)) {
            base = siteUrl.trim().replaceAll("/+$", "");
        } else {
            var raw = externalUrlSupplier.getRaw();
            if (raw != null) {
                base = raw.toString().replaceAll("/+$", "");
            } else {
                var uri = exchange.getRequest().getURI();
                base = uri.getScheme() + "://" + uri.getAuthority();
            }
        }
        return base + CALLBACK_PATH;
    }

    /**
     * Loads the AppID and AppKey from plugin settings. Nothing here is ever
     * logged, and the Mapay request URL (which carries the AppKey as a query
     * parameter) is never logged either.
     */
    private Mono<Credentials> loadCredentials() {
        return settingFetcher.fetch("mapay", MapayProperties.class)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "插件设置还是空的：请回到 Console → 插件 → juhedenglu 的设置页，"
                    + "填好 AppID 和 AppKey，然后拉到最下面点保存。")))
            .flatMap(props -> {
                if (!StringUtils.hasText(props.getAppId())
                    || !StringUtils.hasText(props.getAppKey())) {
                    return Mono.error(new IllegalStateException(
                        "AppID 或 AppKey 还没填：请回到插件设置页填好，"
                            + "然后拉到最下面点保存。"));
                }
                return Mono.just(new Credentials(props.getAppId(), props.getAppKey().trim(),
                    props.getApiBaseUrl(), props.getSiteUrl()));
            });
    }

    /** Marker kept in the WebSession between authorize and callback. */
    private record PendingLogin(String type, long createdAtMillis) implements Serializable {
    }

    private record Credentials(String appId, String appKey, String apiBaseUrl, String siteUrl) {
    }
}
