local kind = redis.call('TYPE', KEYS[1]).ok
if kind == 'none' then return {} end
local count = tonumber(ARGV[1])
if kind == 'list' then return redis.call('LRANGE', KEYS[1], -count, -1) end
if kind ~= 'hash' then return redis.error_reply('Unexpected conversation key type') end
local messages = cjson.decode(redis.call('HGET', KEYS[1], 'messages'))
local result = {}
for i = math.max(1, #messages - count + 1), #messages do result[#result + 1] = messages[i] end
return result
