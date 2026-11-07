# 聚合登录

为 Halo 博客接入微信、QQ、支付宝登录。访客可以用三方账号直接登录，也可以在个人中心绑定已有账号，用法和 Halo 官方的 GitHub 登录插件一样。

[![Release](https://img.shields.io/github/v/release/bilibilichengbuwan/juhedenglu)](https://github.com/bilibilichengbuwan/juhedenglu/releases/tag/v1.0.1)
[![Halo](https://img.shields.io/badge/Halo-%3E%3D%202.20.0-blue)](https://halo.run)
[![License](https://img.shields.io/badge/license-GPL--3.0-green)](./LICENSE)

## 功能

- 支持微信、QQ、支付宝三种登录方式，登录页自动显示对应图标
- 访客三方登录后自动创建 Halo 账号，下次直接登录
- 已登录用户可以在个人中心绑定或解绑三方账号
- 登录会话、记住我、登录日志均由 Halo 核心管理
- 微信、QQ、支付宝图标内置，无需额外配置

## 安装

1. 在 [Releases](https://github.com/bilibilichengbuwan/juhedenglu/releases/tag/v1.0.1) 下载 `juhedenglu-1.0.1.jar`
2. 打开 Halo Console → **插件** → **安装**，上传 JAR 并启用插件
3. 在聚合登录服务商后台登记回调地址（见下节），获取 AppID 和 AppKey
4. 打开 Halo Console → **用户** → **身份认证**，启用 **微信**、**QQ**、**支付宝**
5. 打开 Halo Console → **插件** → **聚合登录** → **设置**，填入 AppID 和 AppKey，拉到页面最下方点 **保存**

> AppID 和 AppKey 只在你自己的 Console 里填写，请勿发给他人，也不要写进代码或文档。

## 回调地址

在聚合登录服务商后台登记以下回调地址：

```
https://你的域名/login/mapay/callback
```

例如你的站点是 `https://example.com`，就登记 `https://example.com/login/mapay/callback`。

插件设置里还有两个可选项，一般不用改：

- **聚合登录服务地址**：默认为 `https://login.mapay.cn`，使用其他聚合登录服务时再改
- **站点对外地址**：留空时自动使用 Halo 配置的外部访问地址；如果站点在反向代理后面，或想用另一个域名做回调，在这里填写，如 `https://你的域名`（不要带末尾斜杠）

## 使用方法

**访客登录**：在登录页点微信、QQ 或支付宝图标，完成三方授权后即可进入博客；首次登录会自动创建账号。

**绑定账号**：登录后进入个人中心，在绑定设置里点微信、QQ 或支付宝，完成授权后即与当前账号绑定；解绑后可以重新绑定。

## 常见问题

**点绑定后直接跳回个人中心，没有去三方授权页？**

授权发起失败时会显示错误页并写明原因。最常见的是插件设置填完没有点保存（保存按钮在设置页最下方）；其次是 AppID 或 AppKey 填错，或服务器连不上聚合登录服务。

**用三方登录创建的账号没有邮箱？**

聚合登录服务不返回邮箱，因此这类账号没有已验证邮箱，不能用邮箱登录或找回密码。

**新用户登录失败？**

请确认 Halo 已开启「允许注册」，并设置了默认角色（不能是超级管理员）。关闭注册时，新用户无法通过三方登录创建账号。

**昵称是中文，用户名变成了一串随机字符？**

中文昵称不符合 Halo 用户名规则时，Halo 会自动生成 `user-xxxxxxxx` 形式的用户名，显示名仍使用你的昵称。

**授权和回跳要在同一个浏览器里完成**

发起登录和授权回跳必须在同一个浏览器会话中进行。换浏览器、使用无痕窗口或中途清掉会话后回跳会被拒绝，需要重新发起登录。

## 隐私说明

本插件会连接外部聚合登录服务完成微信、QQ、支付宝登录。

- **服务**：聚合登录服务，默认地址为 `https://login.mapay.cn`。插件设置里的“聚合登录服务地址”可以改成其他兼容的服务地址，实际使用的以你的设置为准。
- **用途**：仅用于发起三方登录授权、换取三方用户信息。插件不做统计、不做遥测、不投放广告、不追踪用户。
- **发送给服务的数据**：AppID、AppKey、回调地址（`https://你的域名/login/mapay/callback`）、授权成功后服务返回的 code。按服务官方文档，接口仅支持 GET 请求，AppKey 作为 URL 请求参数经 HTTPS 发送；插件不会在日志、错误页或异常信息中记录完整请求地址或 AppKey。
- **从服务接收的数据**：对方账号 ID（social_uid）、昵称、头像地址、性别、所在地。
- **写入 Halo 的数据**：Halo 核心据此创建用户（用户名、显示名、头像）并创建用户连接（登录方式 + 对方账号 ID）。插件本身不在数据库、文件或日志中保存任何用户信息；AppID 和 AppKey 只保存在 Halo 的插件设置里。
- **解绑与删除**：在个人中心解绑会删除对应的用户连接；删除 Halo 用户时，其用户连接会一并删除（Halo 核心行为）。卸载插件后，已创建的用户账号和绑定关系仍保留在 Halo 中，插件不会删除它们；但微信、QQ、支付宝登录入口会消失，此前仅通过三方登录的账号将无法再用三方登录，需要管理员在 Console 中处理。
- **数据保存位置和期限**：用户数据保存在你自己的 Halo 数据库中，保存期限由你控制；服务商一侧的数据处理以服务商的隐私政策为准。

## 更新日志

### 1.0.1

- 修复微信登录回调：聚合登录服务回跳时 type=wx，之前会被拒绝导致微信登录和绑定失败
- Java 包名由 `run.halo.mapay` 改为 `com.chengbuwan.mapay`
- 新增隐私说明；日志不再记录异常详情，避免泄露请求地址或 AppKey

### 1.0.0

- 首次发布：微信、QQ、支付宝聚合登录，支持登录与个人中心绑定

## 从源码构建

需要 Java 21：

```bash
./gradlew build
```

构建产物在 `build/libs/juhedenglu-1.0.1.jar`。

## 问题反馈

遇到问题或有改进建议，欢迎在 [Issues](https://github.com/bilibilichengbuwan/juhedenglu/issues) 提交。

## 开源协议

本项目基于 [halo-sigs/plugin-oauth2](https://github.com/halo-sigs/plugin-oauth2) 开发，遵循 [GPL-3.0](./LICENSE) 开源协议。
