-- KEYS[1] = hash key
-- ARGV[1] = capacity (max tokens, integer)
-- ARGV[2] = refill rate (tokens per second, decimal e.g. "2.5")
-- ARGV[3] = current timestamp in milliseconds
-- ARGV[4] = TTL in seconds for auto-cleanup
--
-- Returns: { allowed, tokens_remaining_floor, retry_after_ms }

local key          = KEYS[1]
local capacity     = tonumber(ARGV[1])
local refill_rate  = tonumber(ARGV[2])
local now_ms       = tonumber(ARGV[3])
local ttl_seconds  = tonumber(ARGV[4])

-- Read current bucket state
local data        = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens      = tonumber(data[1])
local last_refill = tonumber(data[2])

-- New user: initialize their bucket full
-- Why full? First visit should never be denied — UX decision.
-- Some systems start at 0 for stricter enforcement. Config-driven ideally.
if tokens == nil or last_refill == nil then
	tokens      = capacity
	last_refill = now_ms
end

-- ── Refill calculation ────────────────────────────────────────────
-- elapsed is in SECONDS (we convert from ms) — refill_rate is per second
local elapsed_seconds = (now_ms - last_refill) / 1000.0

-- How many tokens accumulated while this user was doing other things?
local new_tokens = elapsed_seconds * refill_rate

-- Add to current supply, but never exceed the bucket capacity.
-- math.min is critical — without it tokens grow forever and you've
-- accidentally given a user a million-request burst.
tokens = math.min(capacity, tokens + new_tokens)

-- ── Decision ──────────────────────────────────────────────────────
local allowed        = 0
local retry_after_ms = 0

if tokens >= 1.0 then
	tokens  = tokens - 1.0    -- consume exactly one token
	allowed = 1
else
-- How long until we have 1 full token?
-- We need (1.0 - current_tokens) more tokens.
-- At refill_rate tokens/second, that takes (deficit / refill_rate) seconds.
-- Convert to ms and round up (math.ceil) — we don't want to say "retry in 499ms"
-- when the actual refill takes 500ms.
	local deficit        = 1.0 - tokens
	retry_after_ms       = math.ceil((deficit / refill_rate) * 1000)
end

-- ── Persist updated state ─────────────────────────────────────────
-- Always write back, even on deny — the token count and last_refill
-- must reflect the state after this request's refill calculation.
-- If we skip the write on deny, next request recalculates from stale state
-- and the user gets tokens they shouldn't have.
redis.call('HMSET', key,
	'tokens',      tostring(tokens),
	'last_refill', tostring(now_ms)
)

-- Reset TTL — user is active, so keep their bucket alive
redis.call('EXPIRE', key, ttl_seconds)

-- Floor the token display — showing "3.7 tokens remaining" is confusing UX
return { allowed, math.floor(tokens), retry_after_ms }