-- One key holds both the generation and the message window. No cross-slot operations.
local kind = redis.call('TYPE', KEYS[1]).ok
if kind == 'hash' then
    local generation = redis.call('HGET', KEYS[1], 'generation')
    local messages = redis.call('HGET', KEYS[1], 'messages')
    if not generation or not messages then
        return redis.error_reply('Invalid conversation state')
    end
    return {generation, messages}
end
local messages = '[]'
local ttlMillis = tonumber(ARGV[2]) * 1000
if kind == 'list' then
    -- Preserve valid legacy history and its remaining lifetime, in this same atomic script.
    local legacy = redis.call('LRANGE', KEYS[1], -tonumber(ARGV[3]), -1)
    if #legacy > 0 then messages = cjson.encode(legacy) end
    local oldTtl = redis.call('PTTL', KEYS[1])
    if oldTtl >= 0 then ttlMillis = math.max(1, oldTtl) end
elseif kind ~= 'none' then
    return redis.error_reply('Unexpected conversation key type')
end
-- Do all potentially failing reads/encoding before changing legacy state.
redis.call('DEL', KEYS[1])
redis.call('HMSET', KEYS[1], 'generation', ARGV[1], 'messages', messages)
redis.call('PEXPIRE', KEYS[1], ttlMillis)
return {ARGV[1], messages}
