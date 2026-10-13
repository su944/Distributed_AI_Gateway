

-- KEYS[1]: tenant bucket. ARGV: capacity, refill tokens/second, request cost.
-- Result: allowed (0/1), whole remaining tokens, retry seconds, full-refill seconds.
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + tonumber(time[2]) / 1000
local state = redis.call('HMGET', KEYS[1], 'tokens', 'updated_at')
local tokens = tonumber(state[1]) or capacity
local previous = tonumber(state[2]) or now
-- Preserve the last timestamp if the server clock steps backwards.
local updated = math.max(now, previous)
tokens = math.min(capacity, tokens + math.max(0, now - previous) * rate / 1000)
local allowed = 0
local retry = 0
if tokens >= cost then
    tokens = tokens - cost
    allowed = 1
else
    retry = math.max(1, math.ceil((cost - tokens) / rate + (updated - now) / 1000))
end
local full = (capacity - tokens) / rate + (updated - now) / 1000
redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'updated_at', tostring(updated))
-- Never expire before a depleted bucket could have refilled completely.
redis.call('PEXPIRE', KEYS[1], math.max(1000, math.ceil(full * 1000)))
return {allowed, math.floor(tokens), retry, math.ceil(full)}
