#!/usr/bin/lua
-- /data/wol/totp.lua <secret_base32> [time_offset]
-- TOTP RFC 6238, HMAC-SHA1, 6 digits
local bit = require("bit")

local function base32_decode(s)
    s = s:gsub("[^A-Za-z2-7]", ""):upper()
    local chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    local map = {}
    for i = 1, 32 do map[chars:sub(i,i)] = i - 1 end
    local bytes = {}
    local bits, buf = 0, 0
    for i = 1, #s do
        local v = map[s:sub(i,i)]
        if not v then return nil end
        buf = buf * 32 + v
        bits = bits + 5
        if bits >= 8 then
            bits = bits - 8
            local div = 2 ^ bits
            bytes[#bytes+1] = math.floor(buf / div) % 256
            buf = buf % div
        end
    end
    local out = {}
    for _, b in ipairs(bytes) do out[#out+1] = string.char(b) end
    return table.concat(out)
end

local function hexstr(s)
    return (s:gsub(".", function(c) return string.format("%02x", string.byte(c)) end))
end

local function hmac_sha1(key, msg)
    local f = io.open("/tmp/.totp_msg", "wb")
    f:write(msg)
    f:close()
    local p = io.popen("openssl dgst -sha1 -mac HMAC -macopt hexkey:" .. hexstr(key) .. " -binary /tmp/.totp_msg 2>/dev/null")
    local mac = p:read("*a")
    p:close()
    os.remove("/tmp/.totp_msg")
    return mac
end

local function totp(secret_b32, t)
    local key = base32_decode(secret_b32)
    if not key or #key == 0 then return nil end
    local counter = math.floor(t / 30)
    local msg = string.char(
        math.floor(counter / 2^56) % 256,
        math.floor(counter / 2^48) % 256,
        math.floor(counter / 2^40) % 256,
        math.floor(counter / 2^32) % 256,
        math.floor(counter / 2^24) % 256,
        math.floor(counter / 2^16) % 256,
        math.floor(counter / 2^8) % 256,
        counter % 256)
    local mac = hmac_sha1(key, msg)
    if #mac < 20 then return nil end
    local off = bit.band(string.byte(mac, 20), 0x0f) + 1
    local b1 = string.byte(mac, off)
    local b2 = string.byte(mac, off + 1) or 0
    local b3 = string.byte(mac, off + 2) or 0
    local b4 = string.byte(mac, off + 3) or 0
    local bin = bit.bor(
        bit.lshift(bit.band(b1, 0x7f), 24),
        bit.lshift(bit.band(b2, 0xff), 16),
        bit.lshift(bit.band(b3, 0xff), 8),
        bit.band(b4, 0xff))
    return string.format("%06d", bin % 1000000)
end

local secret = arg[1] or ""
local t = tonumber(arg[2]) or os.time()
print(totp(secret, t) or "ERR")
