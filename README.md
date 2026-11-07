# Halo 聚合登录插件（微信 / QQ / 支付宝）

让 Halo 访客用微信、QQ、支付宝登录，并在个人中心（UC）绑定账号，用法和 Halo 官方 GitHub 登录插件一样。

- 插件名：`juhedenglu`，版本 1.0.0
- 适用 Halo：`>= 2.20.0`（按 Halo 2.27.2 设计和验证源码）
- 构建产物：`juhedenglu/build/libs/juhedenglu-1.0.0.jar`
- 许可证：GPL-3.0（派生自 [halo-sigs/plugin-oauth2](https://github.com/halo-sigs/plugin-oauth2)，LICENSE 文件随项目保留）

## 原理（简述）

聚合登录服务商（login.mapay.cn）不是标准 OAuth2：插件先服务端向服务商请求授权地址，浏览器去授权；授权完成后服务商把浏览器带回插件的回调地址，插件再服务端换取已验证的三方用户（social_uid、昵称、头像），然后交给 Halo 核心一个 Spring `OAuth2AuthenticationToken`，由 Halo 核心负责创建用户、绑定关系、会话、记住我、设备和登录日志。插件自己不建用户、不建会话、不直接操作绑定关系。

## 安装

1. 在 Halo Console → **插件** → **安装**，上传 `juhedenglu-1.0.0.jar`，启用插件。
2. 在聚合登录服务商后台登记回调地址（见下节），拿到 AppID 和 AppKey。
3. 在 Halo Console → **用户** → **认证**，启用 **微信**、**QQ**、**支付宝** 三个登录方式。
4. 在 Halo Console → **插件** → **聚合登录（微信/QQ/支付宝）** → **设置**：
   - **AppID**：直接填服务商给的 AppID。
   - **AppKey**：直接填服务商给的 AppKey，和 AppID 一样就是个输入框，填完拉到最下面点保存。
   - **聚合登录服务地址**：默认就是服务商的地址，一般不用改。
   - **站点对外地址（可选）**：留空时自动取 Halo 配置的外部访问地址来拼回调地址；如果你的站点在反向代理后面、或者想用另一个域名做回调，在这里填，如 `https://你的域名`（不要带末尾斜杠）。

> 凭证只由你在 Console 里填，不要把 AppID / AppKey 发给任何人、也不要写进代码或文档。

## 回调地址（去服务商后台登记）

```
<你的站点对外地址>/login/mapay/callback
```

比如你的站点是 `https://example.com`，就登记 `https://example.com/login/mapay/callback`。如果在插件设置里填了“站点对外地址”，就以填的那个为准；没填就按 Halo 配置的外部访问地址来。

## 发布到 GitHub 和 Halo 商店

版本号保持 1.0.0，不要随便升，改代码重新打包还是 1.0.0。

1. 源码已传到 GitHub 仓库 `bilibilichengbuwan/juhedenglu`（仓库根目录就是 Gradle 工程：`build.gradle`、`settings.gradle`、`src/`、`README.md` 等）。`plugin.yaml` 里的 homepage / repo / issues 现在写的是 `https://github.com/bilibilichengbuwan/juhedenglu`。
2. 在 GitHub 上创建 Release `v1.0.0`，把 `juhedenglu-1.0.0.jar` 作为附件传上去。
3. 上架 Halo 商店：向 [halo-sigs/awesome-halo](https://github.com/halo-sigs/awesome-halo) 发 PR 提交应用信息，并勾选“上架到 Halo 应用市场”，等官方审核。商店要求都已满足：`plugin.yaml` 里有 homepage、issues、license，logo 是自定义的，README 里有介绍和使用文档，许可证是 GPL-3.0（派生自官方 OAuth2 插件，LICENSE 文件随项目保留）。

## 已知限制

- **点绑定后跳回 /uc**：如果点绑定后直接回到个人中心、没有跳去三方授权页，说明发起授权那一步失败了。新版本会直接显示一个错误页写明原因，最常见的是插件设置填完没点保存（设置页拉到最下面有个保存按钮，没点等于没配）；其次是 AppID / AppKey 填错、或服务器连不上聚合登录服务，Halo 日志里搜 `Failed to start Mapay login` 能看到原因。
- **没有邮箱**：服务商不返回邮箱，用三方登录创建的 Halo 用户没有已验证邮箱，不能用邮箱登录或找回密码。
- **微信桌面端**：电脑浏览器上插件会显示一个扫码页，微信扫码完成授权；如果扫码后本页没有变化，点页面上的「在浏览器中继续」。
- **同一浏览器会话**：发起授权和授权回跳必须在同一个浏览器里完成（插件用浏览器会话做防伪检查，服务商本身不提供 state 参数）。换浏览器、无痕窗口或清掉会话后回跳会被拒绝，需要重新发起登录。
- **新用户注册**：Halo 的「允许注册」开关要打开，并设置好默认角色（不能是超级管理员），新用户才能通过三方登录创建账号；关闭注册时新用户会创建失败。
- **昵称**：中文昵称不符合 Halo 用户名规则时，Halo 会自动生成一个 `user-xxxxxxxx` 形式的用户名，显示名仍用昵称。

## 自行构建

需要 Java 21：

```bash
cd juhedenglu
./gradlew build
```

产物在 `juhedenglu/build/libs/juhedenglu-1.0.0.jar`。

## 验证情况

已在本机验证：

- `./gradlew build` 通过（Java 21，平台 2.27.0）。
- JAR 内含 `plugin.yaml`（name `juhedenglu`，requires `>=2.20.0`）、3 个 AuthProvider（wechat / qq / alipay）、Setting、ReverseProxy，以及 `static/` 下的 wechat.jpg、qq.jpg、alipay.jpg（微信、QQ、支付宝官方图标）。
- 代码按 Halo 2.27.2 源码核对过：AuthProvider 字段、MapOAuth2AuthenticationFilter 的 token 路由、OAuth2RegistrationService 的用户名/显示名规则、插件配置读取方式。

未验证（本机没有可用的 Halo 实例 / Docker，需要在你的 Halo 2.27.2 上逐项核对）：

1. 登录页显示微信、QQ、支付宝三个图标。
2. 新用户：点 QQ → 授权 → 跳到 `/login?oauth2_select` → 创建账号 → 进入 `/uc`，Console 里出现一条 `UserConnection`（registrationId 为 qq）。
3. 第二次登录直接进入，不用再注册。
4. 已登录时绑定另一个三方账号，会加到同一个账号上；绑定已被别人绑过的账号会被拒绝。
5. 在 UC 里解绑后可以重新绑定。
6. 记住我：关掉浏览器再打开仍保持登录。
7. Console 登录日志里出现相应记录。
8. 关闭「允许注册」后，新用户创建失败且有明确提示。
9. 微信桌面端扫码页可用。
