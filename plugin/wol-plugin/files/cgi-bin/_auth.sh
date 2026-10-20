#!/bin/sh
# 2FA session 检查函数（被 source）
# 用法: source /data/wol/cgi-bin/_auth.sh; check_2fa || { echo '{"ok":false,"err":"未验证2FA"}'; exit 0; }
check_2fa() {
    local cookie
    cookie=$(echo "$HTTP_COOKIE" | grep -o 'wol_2fa=[^;]*' 2>/dev/null | head -1 | cut -d= -f2)
    [ -z "$cookie" ] && return 1
    local line
    line=$(grep "^$cookie|" /tmp/.wol_2fa_sessions 2>/dev/null | head -1)
    [ -z "$line" ] && return 1
    local exp
    exp=$(echo "$line" | cut -d'|' -f2)
    local now
    now=$(date +%s)
    [ "$now" -gt "$exp" ] && return 1
    return 0
}
