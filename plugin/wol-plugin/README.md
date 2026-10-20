# WOL 网络唤醒插件

给 OpenWrt 类路由器用的局域网唤醒（Wake-on-LAN）Web 管理插件。

## 功能

- ⚡ 发送 WOL magic packet 唤醒设备
- 🟢 设备在线状态检测（DHCP 租约 + ping）
- 📡 扫描局域网设备（IP + MAC + 主机名）
- ➕ 手动添加 / 编辑 / 删除设备
- ⏱ 最近 3 次唤醒耗时记录与对比（+/-）
- 🔐 Basic Auth 密码保护
- 🔑 双因素认证（TOTP，Google Authenticator 兼容）
- 🔄 重启自动恢复（cron 守护）
- 📱 PWA 支持（手机"添加到主屏幕"当 App 用）

## 依赖

- `uhttpd`（OpenWrt 默认 Web 服务器）
- `lua`（OpenWrt 默认）
- `lua-socket`（luasocket，用于 UDP 广播）
- `openssl`（TOTP 2FA 计算）

### 安装依赖（OpenWrt）

```sh
opkg update
opkg install uhttpd lua lua-socket openssl-util
```

## 安装

```sh
# 上传到路由器
scp -O wol-plugin.tar.gz root@路由器IP:/tmp/

# SSH 登录路由器
ssh root@路由器IP

# 解压安装（默认端口 8081，用户名 admin，密码随机生成）
cd /tmp
tar xzf wol-plugin.tar.gz
cd wol-plugin
sh install.sh
```

### 自定义参数

```sh
sh install.sh 8081 admin 你的密码    # 指定端口、用户名、密码
```

## 安装完成

脚本会输出：

```
访问地址: http://路由器IP:8081/
用户名:   admin
密码:     xxxxxxxx
2FA密钥:  XXXXXXXXXXXXXXXX
```

### 配置 2FA（可选）

用 Google Authenticator / Microsoft Authenticator / Authy 手动录入上面的密钥。

## 卸载

```sh
sh /data/wol/uninstall.sh
```

## 文件结构

```
/data/wol/
├── index.html          # Web 页面
├── manifest.json       # PWA 配置（手机添加到主屏幕）
├── icon-192.png        # PWA 图标
├── icon-512.png
├── wol.lua             # magic packet 发送
├── totp.lua            # TOTP 计算
├── gen_secret.lua      # 2FA 密钥生成
├── httpd.conf          # Basic Auth 配置
├── 2fa_secret          # 2FA 密钥（自动生成）
├── devices.txt         # 设备列表
├── start.sh            # 启动脚本
├── wake_history.txt    # 唤醒历史（自动生成）
└── cgi-bin/            # API 接口
    ├── _auth.sh        # 2FA session 检查
    ├── check2fa        # 2FA 状态检查
    ├── verify2fa       # 2FA 验证码提交
    ├── devices         # 设备 CRUD
    ├── scan            # 网络扫描
    ├── status          # 设备在线检测
    ├── wakehistory/wakelog  # 唤醒历史
    ├── wol             # 发送唤醒包
    └── ping            # 健康检查
```

## 手机 APP

无需安装 APK，直接用手机浏览器打开页面：

1. 打开 Web 页面
2. 浏览器菜单 → 「添加到主屏幕」
3. 桌面会出现图标，点击全屏打开（像 App 一样）

## 故障排查

| 问题 | 解决 |
|---|---|
| 页面 404 | 检查 uhttpd 是否运行：`netstat -tln \| grep 8081` |
| 502 Bad Gateway | CGI 脚本语法错误：`sh -n /data/wol/cgi-bin/wol` |
| 唤醒无效 | 目标设备 BIOS/Windows 需开启 WOL，关闭快速启动 |
| 扫描不到设备 | 需要设备与路由器同网段 |
| 2FA 验证失败 | 检查手机时间和路由器时间是否同步 |

## 安全建议

1. 修改默认密码
2. 启用 2FA
3. 如需公网访问，建议通过 VPN 或反向代理（HTTPS），不要直接暴露 HTTP 端口
