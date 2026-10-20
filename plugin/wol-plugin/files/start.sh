#!/bin/sh
# WOL 服务启动（通用版）
DIR="__INSTALL_DIR__"
if ! netstat -tln 2>/dev/null | grep -q ":__PORT__ "; then
    uhttpd -p 0.0.0.0:__PORT__ -h "$DIR" -x /cgi-bin -c "$DIR/httpd.conf" -T 30 -f >/dev/null 2>&1 &
    sleep 1
fi
