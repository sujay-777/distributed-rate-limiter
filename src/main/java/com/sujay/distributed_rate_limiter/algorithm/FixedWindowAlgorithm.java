package com.sujay.distributed_rate_limiter.algorithm;

import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FixedWindowAlgorithm implements RateLimiterAlgorithm{

    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();

    private final Map<String, Long> windowStartTime = new ConcurrentHashMap<>();

    @Override
    public RateLimitResponse check(RateLimitRequest request){

        String clientKey = request.getClientKey();
        long currentTime = System.currentTimeMillis();
        long windowDuration = request.getWindowSeconds() * 1000L;
        int currentCount = requestCounts.getOrDefault(clientKey, 0);

        long windowStart = windowStartTime.getOrDefault(clientKey, 0L);

        if(currentTime - windowStart > windowDuration){
            requestCounts.put(clientKey, 0);
            windowStartTime.put(clientKey, currentTime);
        }

        if(currentCount >= request.getLimit()){
            long windowEndsAt = windowStart + windowDuration;
            long resetAfter = (windowEndsAt - currentTime) / 1000;
            return new RateLimitResponse(
                    false,
                    0,
                    resetAfter,
                    "Limit is " + request.getLimit()
                            + " requests per " + request.getWindowSeconds() + " seconds"
            );
        }
        requestCounts.put(clientKey , currentCount + 1);
        int remaining = request.getLimit() - (currentCount + 1);
        return new RateLimitResponse(true, remaining, 0, "OK");

    }

}
