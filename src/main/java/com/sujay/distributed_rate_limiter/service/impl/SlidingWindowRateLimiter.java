package com.sujay.distributed_rate_limiter.service.impl;

import com.sujay.distributed_rate_limiter.configuration.RateLimiterProperties;
import com.sujay.distributed_rate_limiter.enums.Algorithm;
import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;
import com.sujay.distributed_rate_limiter.service.RateLimiterService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

import java.util.List;

@Data
@Service
@RequiredArgsConstructor
public class SlidingWindowRateLimiter implements RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> slidingWindowScript;
    private final RateLimiterProperties properties;
    private final MeterRegistry meterRegistry;

    @Override
    public Algorithm getAlgorithm() {
        return Algorithm.SLIDING_WINDOW;
    }

    @Override
    public RateLimitResponse evaluate(RateLimitRequest request) {
        String key = request.buildRedisKey(Algorithm.SLIDING_WINDOW);

        int maxRequests = resolveMaxRequests(request);
        long windowMs   = properties.getAlgorithms().getSlidingWindow().getWindowSizeSeconds() * 1000L;

        // Pass current time as ARGV — never use redis.call('TIME') inside Lua
        long nowMs = System.currentTimeMillis();

        try {
            long start = System.currentTimeMillis();

            List<Long> result = (List<Long>) redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(key),
                    String.valueOf(maxRequests),
                    String.valueOf(windowMs),
                    String.valueOf(nowMs)
            );

            recordRedisLatency(System.currentTimeMillis() - start, Algorithm.SLIDING_WINDOW);

            return buildResponse(result, maxRequests, Algorithm.SLIDING_WINDOW);

        } catch (Exception e) {
            return handleRedisFailure(request, maxRequests, Algorithm.SLIDING_WINDOW, e);
        }
    }

    // Delegate shared helpers to the same pattern as FixedWindow
    // In Session 3 we'll refactor these into a base class properly
    // customMaxRequests is any no of requests that is done by the user  (premium/subscription)
    private int resolveMaxRequests(RateLimitRequest request) {
        if (request.getCustomMaxRequests() != null && request.getCustomMaxRequests() > 0) {
            return request.getCustomMaxRequests();
        }
        return properties.getAlgorithms().getSlidingWindow().getMaxRequests();
    }

    private RateLimitResponse buildResponse(List<Long> result, int maxRequests, Algorithm algorithm) {
        boolean allowed = result.get(0) == 1L;
        long remaining  = result.get(1);
        long ttlMs      = result.get(2);
        long resetAtMs  = System.currentTimeMillis() + ttlMs;

        String decision = allowed ? "allowed" : "denied";
        meterRegistry.counter("ratelimiter.decisions.total",
                "algorithm", algorithm.name(),
                "decision",  decision
        ).increment();

        return allowed
                ? RateLimitResponse.allowed(remaining, maxRequests, resetAtMs, algorithm)
                : RateLimitResponse.denied(maxRequests, ttlMs, resetAtMs, algorithm);
    }

    private RateLimitResponse handleRedisFailure(RateLimitRequest request,
                                                 int maxRequests, Algorithm algorithm, Exception e) {
        log.error("[RateLimiter] Redis unavailable. client={} error={}", request.getClientKey(), e.getMessage());
        meterRegistry.counter("ratelimiter.redis.failures.total", "algorithm", algorithm.name()).increment();
        boolean failOpen = "FAIL_OPEN".equalsIgnoreCase(properties.getRedisFailureStrategy());
        long now = System.currentTimeMillis();
        return failOpen
                ? RateLimitResponse.allowed(maxRequests, maxRequests, now + 60_000L, algorithm)
                : RateLimitResponse.denied(maxRequests, 5_000L, now + 5_000L, algorithm);
    }

    private void recordRedisLatency(long durationMs, Algorithm algorithm) {
        meterRegistry.timer("ratelimiter.redis.duration.ms", "algorithm", algorithm.name())
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
