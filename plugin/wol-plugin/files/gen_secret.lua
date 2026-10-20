#!/usr/bin/lua
-- 生成 Base32 随机密钥（16 字节 = 128 bit）
local f = io.open("/dev/urandom", "rb")
if not f then print("ERR"); os.exit(1) end
local raw = f:read(16)
f:close()
local chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
local bits, buf, out = 0, 0, {}
for i = 1, #raw do
    buf = buf * 256 + string.byte(raw, i)
    bits = bits + 8
    while bits >= 5 do
        bits = bits - 5
        local div = 2 ^ bits
        local v = math.floor(buf / div) % 32
        out[#out+1] = chars:sub(v + 1, v + 1)
        buf = buf % div
    end
end
if bits > 0 then
    local v = math.floor(buf * 2 ^ (5 - bits)) % 32
    out[#out+1] = chars:sub(v + 1, v + 1)
end
print(table.concat(out))
