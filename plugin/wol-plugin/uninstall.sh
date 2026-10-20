#!/bin/sh
# WOL 插件卸载
INSTALL_DIR="$(cd "$(dirname "$0")" && pwd)"
PORT=$(cat "$INSTALL_DIR/port.txt" 2>/dev/null || echo "8081")

echo "卸载 WOL 插件: $INSTALL_DIR (端口 $PORT)"
echo "按 Ctrl+C 取消，3 秒后继续..."
sleep 3

# 停止服务：只杀监听该端口的 uhttpd
PID=$(netstat -tlnp 2>/dev/null | grep ":$PORT " | awk '{print $NF}' | cut -d'/' -f1 | head -1)
if [ -n "$PID" ]; then
    kill "$PID" 2>/dev/null
    echo "  已停止 uhttpd (PID $PID)"
fi
sleep 1

# 删除 cron
if [ -f /etc/crontabs/root ]; then
    sed -i "\|$INSTALL_DIR/start.sh|d" /etc/crontabs/root 2>/dev/null || true
    /etc/init.d/cron restart >/dev/null 2>&1 || true
    echo "  已清理 cron"
fi

# 删除安装目录
cd /
rm -rf "$INSTALL_DIR"
echo "✅ 已卸载 WOL 插件"
