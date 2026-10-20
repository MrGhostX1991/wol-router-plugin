#!/usr/bin/lua
-- wol.lua: send Wake-on-LAN magic packet
-- usage: lua wol.lua <MAC>
local socket = require("socket")

local mac = arg[1] or ""
mac = mac:gsub("[^%x]", "")
if #mac ~= 12 then
    print("ERR:bad mac")
    os.exit(1)
end

local parts = {}
for i = 1, 12, 2 do
    parts[#parts + 1] = string.char(tonumber(mac:sub(i, i + 1), 16))
end
local mac_bin = table.concat(parts)
local packet = string.rep("\255", 6) .. string.rep(mac_bin, 16)

-- 动态获取 LAN 接口 IP 推算子网广播地址（/24 假设）
local function get_broadcast()
    local f = io.popen("ip -4 addr show br-lan 2>/dev/null | grep -o 'inet [0-9.]*' | head -1")
    local line = f:read("*l")
    f:close()
    if line then
        local ip = line:match("inet (%d+%.%d+%.%d+%.%d+)")
        if ip then
            local a, b, c = ip:match("^(%d+)%.(%d+)%.(%d+)%.")
            if a then return a .. "." .. b .. "." .. c .. ".255" end
        end
    end
    return nil
end

local ok = false
local udp = socket.udp()
udp:setoption("broadcast", true)
local targets = { "255.255.255.255" }
local bcast = get_broadcast()
if bcast then table.insert(targets, 1, bcast) end
for _, ip in ipairs(targets) do
    udp:setsockname("*", 0)
    local r = udp:sendto(packet, ip, 9)
    if r then ok = true end
    local r2 = udp:sendto(packet, ip, 7)
    if r2 then ok = true end
end
udp:close()
if ok then
    print("OK")
else
    print("ERR:send failed")
    os.exit(1)
end
