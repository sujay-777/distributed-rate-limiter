-- KEYS[1] = the rate limit key (sorted set name)
-- ARGV[1] = max requests
-- ARGV[2] = window size in milliseconds (note: ms not seconds)
-- ARGV[3] = current time in milliseconds — ALWAYS passed from Java, never from redis.call('TIME')
local key          = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_ms    = tonumber(ARGV[2])
-- the reason why we are making use of the java time is that redis has multiple servers
-- like the primary and the replica server and if i make use of the redis time then there might be a small differnce
-- in the time that is stored in the replica server and it does not follow the deterministic behaviour
local now_ms       = tonumber(ARGV[3])


-- this is obtained by subtracting the current time with the window size
-- this will give me the window starting time
local window_start = now_ms - window_ms

-- this basically removes the requests that were made outside the window
redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

local current_count = redis.call('ZCARD', key)

if current_count < max_requests then
	--we are making the member unique so that each request is unique and it counts them properly
	local member = tostring(now_ms) .. ':' .. tostring(math.random(1, 999999))
	redis.call('ZADD', key, now_ms, member)

	-- if no new request is made within the give time frame (here window_ms + 1000) then the key is deleted
	-- else the key will be permanent in the cache memory
	redis.call('PEXPIRE', key, window_ms + 1000)

	local remaining = max_requests - current_count - 1
	return { 1, remaining, window_ms }

else
--	this means that the max request has been reached and the user must wait until the oldest one expires
--	the oldest request expires only when the sliding window reaches the retry_after = (oldest_ts + window_ms) - now_ms
	local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')

	local retry_after_ms = window_ms  -- fallback if set is somehow empty
	if oldest and oldest[2] then
		local oldest_ts = tonumber(oldest[2])
		retry_after_ms  = (oldest_ts + window_ms) - now_ms
		-- Clamp to 0 if the calculation goes negative due to clock skew
		if retry_after_ms < 0 then retry_after_ms = 0 end
	end

	return { 0, 0, retry_after_ms }
end
