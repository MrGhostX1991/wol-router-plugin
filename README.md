# WOL 网络唤醒工具（路由器插件 + 原生 Android APP）

给 OpenWrt 类路由器使用的局域网 Wake-on-LAN 管理工具，包含：
- **路由器插件**：Web 管理界面 + CGI API（uhttpd + Lua）
- **原生 Android APP**：不依赖 WebView 的原生客户端

## 功能

- ⚡ 发送 WOL Magic Packet 唤醒设备
- 🟢 设备在线状态检测（DHCP 租约 + ping）
- 🟡 唤醒中实时计时
- ✅ 唤醒成功/失败提示（90 秒超时）
- ⏱ 最近 3 次唤醒耗时记录与对比（+/-）
- 📡 扫描局域网设备（IP + MAC + 主机名）
- ➕ 手动添加 / 编辑 / 删除设备
- 🔐 Basic Auth 密码保护
- 🔑 双因素认证（TOTP，Google Authenticator 兼容）
- 🔄 路由器重启自动恢复（cron）
- 📱 PWA 支持（手机浏览器添加到主屏幕）

## 目录结构

```
├── plugin/              # 路由器插件
│   ├── install.sh       # 一键安装脚本
│   ├── uninstall.sh     # 卸载脚本
│   ├── README.md        # 插件说明
│   └── files/           # 插件文件
│       ├── index.html   # Web 管理界面
│       ├── manifest.json # PWA 配置
│       ├── wol.lua      # magic packet 发送
│       ├── totp.lua     # 2FA 算法
│       ├── gen_secret.lua # 2FA 密钥生成
│       └── cgi-bin/     # CGI 接口
├── app/                 # 原生 Android APP
│   ├── build_apk.bat    # 手动构建脚本（无需 Gradle）
│   └── src/             # 源码（Java + XML）
└── docs/                # 文档
```

## 路由器插件安装

### 依赖

```sh
opkg update
opkg install uhttpd lua lua-socket openssl-util
```

### 安装

```sh
scp -O wol-plugin.tar.gz root@路由器IP:/tmp/
ssh root@路由器IP
cd /tmp
tar xzf wol-plugin.tar.gz
cd wol-plugin
sh install.sh
```

安装脚本会输出：访问地址、用户名、随机密码、2FA 密钥。

### 自定义参数

```sh
sh install.sh 8081 admin 你的密码
```

### 卸载

```sh
sh /data/wol/uninstall.sh
```

## 安卓 APP

### 安装

1. 下载 [Releases](https://github.com/USERNAME/wol-router/releases) 中的 `wol-app.apk`
2. 手机安装（允许未知来源）
3. 首次打开设置服务器地址 + 用户名密码
4. 输入 2FA 验证码

### APP 特性

- 原生 Android UI（ListView，无 WebView）
- 原生 HTTP 请求（HttpURLConnection + Basic Auth）
- 原生 JSON 解析（org.json）
- 直接调用路由器 CGI API

### 从源码构建

需要 JDK 17 + Android SDK build-tools 34 + platform 34。

```powershell
# Windows
cd app
build_apk.bat
```

构建产物：`app/build/wol-app.apk`

不依赖 Gradle，使用 aapt2 + javac + d8 + apksigner 手动构建链。

## CGI API

| 接口 | 功能 |
|---|---|
| `GET /cgi-bin/check2fa` | 2FA 状态检查 |
| `GET /cgi-bin/verify2fa?code=xxxxxx` | 提交 2FA 验证码 |
| `GET /cgi-bin/devices` | 设备列表 |
| `GET /cgi-bin/devices?action=add&name=xx&mac=xx` | 添加设备 |
| `GET /cgi-bin/devices?action=upd&idx=N&name=xx&mac=xx` | 修改设备 |
| `GET /cgi-bin/devices?action=del&idx=N` | 删除设备 |
| `GET /cgi-bin/wol?mac=xx` | 发送唤醒包 |
| `GET /cgi-bin/status?mac=xx` | 设备在线检测 |
| `GET /cgi-bin/scan` | 扫描网络设备 |
| `GET /cgi-bin/wakelog?mac=xx` | 查询唤醒历史 |
| `GET /cgi-bin/wakelog?mac=xx&cost=N` | 记录唤醒耗时 |

## 技术栈

- 路由器端：OpenWrt (uhttpd + busybox sh + Lua)
- APP：Java + Android framework（无 androidx 依赖）
- 2FA：TOTP (RFC 6238) + Lua 实现
- WOL：Lua + luasocket UDP 广播

## 安全说明

- 插件包/APK 不包含任何密码/密钥（安装时全新生成）
- 公网访问建议通过 HTTPS 反向代理或 VPN
- 2FA 密钥需自行保管

## License

MIT
