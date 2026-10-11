-- Never recreate a missing, cleared, expired or superseded generation.
if redis.call('TYPE', KEYS[1]).ok ~= 'hash' then return 0 end
if redis.call('HGET', KEYS[1], 'generation') ~= ARGV[1] then return 0 end
local messages = cjson.decode(redis.call('HGET', KEYS[1], 'messages'))
messages[#messages + 1] = ARGV[2]
messages[#messages + 1] = ARGV[3]
local window = {}
local first = math.max(1, #messages - tonumber(ARGV[4]) + 1)
for i = first, #messages do window[#window + 1] = messages[i] end
-- The pair, trim and TTL are not observable as separate writes by other clients.
local encoded = cjson.encode(window)
redis.call('HSET', KEYS[1], 'messages', encoded)
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]))
return 1
