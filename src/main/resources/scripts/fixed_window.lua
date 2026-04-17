local key          = KEYS[1]
local max_requests = tonumber(ARGV[1])
local window_secs  = tonumber(ARGV[2])

local current_count = redis.call('INCR', key)

-- this means that if the request is fresh/  for the first time
-- current_count = no. of requests that i make.
if current_count == 1 then
	redis.call('EXPIRE', key, window_secs)
end

-- this gives me the remaining time  (total - used)
local ttl_seconds = redis.call('TTL', key)

-- redis sometimes gives -1,-2 values so we are taking care of the edge cases
if ttl_seconds < 0 then
	ttl_seconds = window_secs
end

if current_count <= max_requests then
-- Allow. remaining = how many more they can make after this one.
-- remaining tells me the no. of requests left here and the TTL tells me the remaining time
	local remaining = max_requests - current_count
	return { 1, remaining, ttl_seconds * 1000 }
else
-- Deny. remaining is 0. ttl_ms tells them when to retry.
	return { 0, 0, ttl_seconds * 1000 }
end

