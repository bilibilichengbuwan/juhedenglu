# 聚合登录

为 Halo 博客接入微信、QQ、支付宝登录。访客可以用三方账号直接登录，也可以在个人中心绑定已有账号，用法和 Halo 官方的 GitHub 登录插件一样。

[![Release](https://img.shields.io/github/v/release/bilibilichengbuwan/juhedenglu)](https://github.com/bilibilichengbuwan/juhedenglu/releases/tag/v1.0.0)
[![Halo](https://img.shields.io/badge/Halo-%3E%3D%202.20.0-blue)](https://halo.run)
[![License](https://img.shields.io/badge/license-GPL--3.0-green)](./LICENSE)

## 功能

- 支持微信、QQ、支付宝三种登录方式，登录页自动显示对应图标
- 访客三方登录后自动创建 Halo 账号，下次直接登录
- 已登录用户可以在个人中心绑定或解绑三方账号
- 登录会话、记住我、登录日志均由 Halo 核心管理
- 微信、QQ、支付宝图标内置，无需额外配置

## 安装

1. 在 [Releases](https://github.com/bilibilichengbuwan/juhedenglu/releases/tag/v1.0.0) 下载 `juhedenglu-1.0.0.jar`
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

## 从源码构建

需要 Java 21：

```bash
./gradlew build
```

构建产物在 `build/libs/juhedenglu-1.0.0.jar`。

## 问题反馈

遇到问题或有改进建议，欢迎在 [Issues](https://github.com/bilibilichengbuwan/juhedenglu/issues) 提交。

## 开源协议

本项目基于 [halo-sigs/plugin-oauth2](https://github.com/halo-sigs/plugin-oauth2) 开发，遵循 [GPL-3.0](./LICENSE) 开源协议。
