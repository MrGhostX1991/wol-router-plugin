#!/bin/sh
# ============================================
# WOL 网络唤醒插件 - 一键安装
# 适用: OpenWrt 类路由器（需 uhttpd + lua + luasocket）
# 用法: sh install.sh [端口] [用户名] [密码]
# ============================================
set -e

PORT="${1:-__PORT__}"
USER="${2:-admin}"
PASS="${3:-}"
SRC_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "================================"
echo " WOL 网络唤醒插件 安装"
echo "================================"

# 1. root 检查
if [ "$(id -u)" != "0" ]; then
    echo "❌ 需要 root 权限安装"
    exit 1
fi

# 2. 依赖检查
MISSING=""
for cmd in uhttpd lua openssl; do
    command -v "$cmd" >/dev/null 2>&1 || MISSING="$MISSING $cmd"
done
if [ -n "$MISSING" ]; then
    echo "❌ 缺少依赖:$MISSING"
    echo "   OpenWrt 安装: opkg update && opkg install uhttpd lua openssl-util"
    exit 1
fi
lua -e 'require("socket")' 2>/dev/null || {
    echo "❌ 缺少 lua socket 库（luasocket）"
    echo "   OpenWrt 安装: opkg install lua-socket"
    exit 1
}
echo "✅ 依赖检查通过"

# 3. 选择安装目录（支持 WOL_INSTALL_DIR 环境变量覆盖）
INSTALL_DIR="${WOL_INSTALL_DIR:-}"
if [ -z "$INSTALL_DIR" ]; then
    for d in /data /overlay/upper /etc /usr/share /tmp; do
        if [ -d "$d" ] && [ -w "$d" ]; then
            INSTALL_DIR="$d/wol"
            break
        fi
    done
fi
if [ -z "$INSTALL_DIR" ]; then
    echo "❌ 找不到可写目录（可用 WOL_INSTALL_DIR=/path 指定）"
    exit 1
fi
echo "✅ 安装目录: $INSTALL_DIR"

# 4. 密码
if [ -z "$PASS" ]; then
    PASS=$(head -c 8 /dev/urandom | md5sum | cut -c1-12)
    echo "✅ 生成随机密码"
fi

# 5. 复制文件
mkdir -p "$INSTALL_DIR/cgi-bin"
cp -f "$SRC_DIR/files/index.html" "$INSTALL_DIR/" 2>/dev/null || true
cp -f "$SRC_DIR/files/wol.lua" "$INSTALL_DIR/" 2>/dev/null || true
cp -f "$SRC_DIR/files/totp.lua" "$INSTALL_DIR/" 2>/dev/null || true
cp -f "$SRC_DIR/files/gen_secret.lua" "$INSTALL_DIR/" 2>/dev/null || true
cp -f "$SRC_DIR/files/manifest.json" "$INSTALL_DIR/" 2>/dev/null || true
cp -f "$SRC_DIR/files/icon-192.png" "$INSTALL_DIR/" 2>/dev/null || true
cp -f "$SRC_DIR/files/icon-512.png" "$INSTALL_DIR/" 2>/dev/null || true
cp -f "$SRC_DIR/files/cgi-bin/"* "$INSTALL_DIR/cgi-bin/" 2>/dev/null || true
# 复制卸载脚本 + 记录端口
cp -f "$SRC_DIR/uninstall.sh" "$INSTALL_DIR/uninstall.sh" 2>/dev/null || true
chmod 755 "$INSTALL_DIR/uninstall.sh" 2>/dev/null || true
echo "$PORT" > "$INSTALL_DIR/port.txt"

# 6. 路径替换（/data/wol -> 实际目录）
for f in "$INSTALL_DIR"/cgi-bin/*; do
    sed -i "s|/data/wol|$INSTALL_DIR|g" "$f" 2>/dev/null || true
done
sed -i "s|/data/wol|$INSTALL_DIR|g" "$INSTALL_DIR/wol.lua" 2>/dev/null || true

# 7. 生成 start.sh（带端口）
sed "s|__INSTALL_DIR__|$INSTALL_DIR|g; s|__PORT__|$PORT|g" "$SRC_DIR/files/start.sh" > "$INSTALL_DIR/start.sh"
chmod 755 "$INSTALL_DIR/start.sh"
chmod 755 "$INSTALL_DIR/cgi-bin/"* 2>/dev/null || true
chmod 644 "$INSTALL_DIR/index.html" "$INSTALL_DIR/wol.lua" "$INSTALL_DIR/totp.lua" 2>/dev/null || true

# 8. Basic Auth 配置
echo "/:$USER:$PASS" > "$INSTALL_DIR/httpd.conf"
chmod 600 "$INSTALL_DIR/httpd.conf"

# 9. 2FA 密钥
if [ -f "$INSTALL_DIR/gen_secret.lua" ]; then
    lua "$INSTALL_DIR/gen_secret.lua" > "$INSTALL_DIR/2fa_secret" 2>/dev/null || rm -f "$INSTALL_DIR/2fa_secret"
    chmod 600 "$INSTALL_DIR/2fa_secret" 2>/dev/null || true
fi

# 10. 初始设备列表
if [ ! -f "$INSTALL_DIR/devices.txt" ]; then
    cat > "$INSTALL_DIR/devices.txt" <<'EOF'
我的电脑|AA:BB:CC:DD:EE:FF
EOF
fi
chmod 644 "$INSTALL_DIR/devices.txt"

# 11. 启动服务
sh "$INSTALL_DIR/start.sh"
sleep 1

# 12. cron 持久化
CRON_FILE=/etc/crontabs/root
if [ -d /etc/crontabs ]; then
    grep -q "$INSTALL_DIR/start.sh" "$CRON_FILE" 2>/dev/null || \
        echo "* * * * * $INSTALL_DIR/start.sh >/dev/null 2>&1" >> "$CRON_FILE"
    /etc/init.d/cron restart >/dev/null 2>&1 || true
fi

# 13. 显示信息
LANIP=$(uci get network.lan.ipaddr 2>/dev/null)
[ -z "$LANIP" ] && LANIP=$(ip -4 addr show br-lan 2>/dev/null | grep -o 'inet [0-9.]*' | head -1 | cut -d' ' -f2)
[ -z "$LANIP" ] && LANIP=$(ip -4 addr 2>/dev/null | grep -o 'inet [0-9.]*' | grep -v '127.0.0.1' | head -1 | cut -d' ' -f2)
[ -z "$LANIP" ] && LANIP="<路由器IP>"

echo ""
echo "=============================================="
echo "  ✅ WOL 插件安装完成！"
echo "=============================================="
echo "  访问地址: http://$LANIP:$PORT/"
echo "  用户名:   $USER"
echo "  密码:     $PASS"
if [ -f "$INSTALL_DIR/2fa_secret" ]; then
    echo "  2FA密钥:  $(cat "$INSTALL_DIR/2fa_secret")"
    echo "  （手机装 Google Authenticator 录入此密钥）"
fi
echo ""
echo "  安装目录: $INSTALL_DIR"
echo "  卸载命令: sh $INSTALL_DIR/uninstall.sh"
echo "=============================================="
