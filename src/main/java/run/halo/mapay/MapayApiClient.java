package run.halo.mapay;

import java.time.Duration;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Server-side client for the Mapay aggregated-login API.
 *
 * <p>Mapay is not a standard OAuth2 provider: there is no token endpoint and
 * no separate user-info endpoint. The flow is:
 *
 * <ol>
 *   <li>{@code GET /connect.php?act=login&appid=..&appkey=..&type=..&redirect_uri=..}
 *       returns {@code {code, url}}; the browser is sent to {@code url}.</li>
 *   <li>After the user authorises, Mapay redirects to
 *       {@code redirect_uri?type=..&code=..}.</li>
 *   <li>{@code GET /connect.php?act=callback&appid=..&appkey=..&type=..&code=..}
 *       returns the verified social user ({@code social_uid}, {@code nickname},
 *       {@code faceimg}, ...).</li>
 * </ol>
 *
 * <p>All calls are made server-side over HTTPS with {@link WebClient}
 * (non-blocking; no blocking I/O on reactive paths). Credentials are never
 * logged and the request URL is never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapayApiClient {

    /** Built-in service base URL, used when the setting is blank. */
    static final String DEFAULT_BASE_URL = "https://login.mapay.cn";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
        new ParameterizedTypeReference<>() {
        };

    private final WebClient webClient = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create().responseTimeout(Duration.ofSeconds(15))))
        .build();

    /** Resolves the configured base URL, falling back to the default. */
    static String resolveBaseUrl(String configured) {
        if (StringUtils.hasText(configured)) {
            var base = configured.trim().replaceAll("/+$", "");
            if (base.startsWith("https://")) {
                return base;
            }
        }
        return DEFAULT_BASE_URL;
    }

    /**
     * Step 1: ask Mapay for the authorisation address of a provider.
     *
     * @param appId       AppID from plugin settings
     * @param appKey      AppKey from plugin settings (never logged)
     * @param type        Mapay {@code type} value: {@code qq}, {@code wx}
     *                    or {@code alipay} (see https://login.mapay.cn/doc.php).
     *                    Callers must map the Halo provider name to this
     *                    value first ({@code wechat} → {@code wx}).
     * @param redirectUri callback URL registered with Mapay
     * @return login result; {@code code == 0} means success
     */
    public Mono<LoginResult> login(String appId, String appKey, String type,
        String redirectUri, String apiBaseUrl) {
        var uri = UriComponentsBuilder.fromUriString(resolveBaseUrl(apiBaseUrl) + "/connect.php")
            .queryParam("act", "login")
            .queryParam("appid", appId)
            .queryParam("appkey", appKey)
            .queryParam("type", type)
            .queryParam("redirect_uri", redirectUri)
            .build(true)
            .toUri();
        return webClient.get().uri(uri).retrieve()
            .bodyToMono(MAP_TYPE)
            .map(MapayApiClient::toLoginResult)
            .doOnError(e -> log.warn("Failed to request Mapay login address for type {}", type));
    }

    /**
     * Step 3: exchange the callback {@code code} for the verified social user.
     *
     * @param appId  AppID from plugin settings
     * @param appKey AppKey from plugin settings (never logged)
     * @param type   Mapay {@code type} value: {@code qq}, {@code wx} or
     *               {@code alipay} (callers map the Halo provider name first:
     *               {@code wechat} → {@code wx})
     * @param code   code returned by Mapay to the callback URL
     * @return social user; {@code code == 0} and a non-blank {@code socialUid}
     *     mean success
     */
    public Mono<SocialUser> callback(String appId, String appKey, String type, String code,
        String apiBaseUrl) {
        var uri = UriComponentsBuilder.fromUriString(resolveBaseUrl(apiBaseUrl) + "/connect.php")
            .queryParam("act", "callback")
            .queryParam("appid", appId)
            .queryParam("appkey", appKey)
            .queryParam("type", type)
            .queryParam("code", code)
            .build(true)
            .toUri();
        return webClient.get().uri(uri).retrieve()
            .bodyToMono(MAP_TYPE)
            .map(body -> toSocialUser(type, body))
            .doOnError(e -> log.warn("Failed to exchange Mapay code for type {}", type));
    }

    private static LoginResult toLoginResult(Map<String, Object> body) {
        var result = new LoginResult();
        result.setCode(asInt(body.get("code")));
        result.setMessage(asString(body.get("msg")));
        result.setType(asString(body.get("type")));
        result.setUrl(asString(body.get("url")));
        return result;
    }

    private static SocialUser toSocialUser(String type, Map<String, Object> body) {
        var user = new SocialUser();
        user.setCode(asInt(body.get("code")));
        user.setMessage(asString(body.get("msg")));
        user.setType(StringUtils.hasText(asString(body.get("type")))
            ? asString(body.get("type")) : type);
        user.setSocialUid(asString(body.get("social_uid")));
        user.setNickname(asString(body.get("nickname")));
        user.setFaceimg(asString(body.get("faceimg")));
        user.setGender(asString(body.get("gender")));
        user.setLocation(asString(body.get("location")));
        return user;
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Result of {@code act=login}. */
    @Data
    public static class LoginResult {
        private int code = -1;
        private String message;
        private String type;
        private String url;

        public boolean isSuccess() {
            return code == 0 && StringUtils.hasText(url);
        }
    }

    /** Verified social user returned by {@code act=callback}. */
    @Data
    public static class SocialUser {
        private int code = -1;
        private String message;
        private String type;
        private String socialUid;
        private String nickname;
        private String faceimg;
        private String gender;
        private String location;

        public boolean isSuccess() {
            return code == 0 && StringUtils.hasText(socialUid);
        }
    }
}
