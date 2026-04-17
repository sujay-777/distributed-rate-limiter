-- KEYS[1]  = the rate limit key (a Redis Hash)
-- ARGV[1]  = bucket capacity (max tokens, e.g. "10")
-- ARGV[2]  = refill rate (tokens per second, e.g. "2.0")
-- ARGV[3]  = current timestamp in milliseconds
-- ARGV[4]  = TTL for the key in seconds (auto-cleanup)
local key          = KEYS[1]
local capacity     = tonumber(ARGV[1])
local refill_rate  = tonumber(ARGV[2])
local now_ms       = tonumber(ARGV[3])
local ttl_seconds  = tonumber(ARGV[4])

local bucket       = redis.call('HMGET', key, 'tokens', 'last_refill')
-- tells me the no. of tokens that are still available in the bucket
local tokens       = tonumber(bucket[1])
local last_refill  = tonumber(bucket[2])

-- First request ever from this user — initialize the bucket to full capacity
if tokens == nil then
	tokens = capacity
	last_refill = now_ms
end

-- Calculate refill
-- elapsed is in seconds (we stored ms, divide by 1000)
local elapsed_seconds = (now_ms - last_refill) / 1000.0

-- How many tokens accumulated while the user was idle?
local tokens_to_add = elapsed_seconds * refill_rate

-- Add them, but never exceed capacity (math.min caps the bucket)
tokens = math.min(capacity, tokens + tokens_to_add)

-- Decision
local allowed       = 0
local retry_after_ms = 0

if tokens >= 1.0 then
	tokens  = tokens - 1.0     -- consume one token
	allowed = 1
else
-- How many ms until we have 1 full token?
-- tokens_needed_for_one = (1.0 - tokens)
-- time_needed_seconds   = tokens_needed / refill_rate
	local deficit = 1.0 - tokens
	retry_after_ms = math.ceil((deficit / refill_rate) * 1000)
end

-- Persist the updated bucket state back to Redis
redis.call('HMSET', key,
	'tokens',      tokens,
	'last_refill', now_ms
)

-- Refresh TTL — the bucket should expire if unused for a long time
redis.call('EXPIRE', key, ttl_seconds)

local tokens_floor = math.floor(tokens)
return {allowed, tokens_floor, retry_after_ms}