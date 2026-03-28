package com.sujay.distributed_rate_limiter.service.impl;

import com.sujay.distributed_rate_limiter.configuration.RateLimiterProperties;
import com.sujay.distributed_rate_limiter.enums.Algorithm;
import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;
import com.sujay.distributed_rate_limiter.service.RateLimiterService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBucketRateLimiter implements RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;
    private final RateLimiterProperties properties;
    private final MeterRegistry meterRegistry;

    @Override
    public Algorithm getAlgorithm() {
        return Algorithm.TOKEN_BUCKET;
    }

    @Override
    public RateLimitResponse evaluate(RateLimitRequest request) {
        String key = request.buildRedisKey(Algorithm.TOKEN_BUCKET);

        RateLimiterProperties.TokenBucket config = properties.getAlgorithms().getTokenBucket();
        int capacity    = resolveCapacity(request, config);
        double refillRate  = config.getRefillRate();

        // Auto-compute TTL: time to fully refill the bucket × 2 for safety margin
        // e.g. capacity=10, rate=2/s → full refill = 5s → TTL = 10s
        long ttlSeconds = (long) ((capacity / refillRate) * 2);

        try {
            long start = System.currentTimeMillis();

            List<Long> result = (List<Long>) redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(ttlSeconds)
            );

            recordRedisLatency(System.currentTimeMillis() - start, Algorithm.TOKEN_BUCKET);

            boolean allowed        = result.get(0) == 1L;
            long tokensRemaining   = result.get(1);
            long retryAfterMs      = result.get(2);
            long resetAtMs         = System.currentTimeMillis() + retryAfterMs;

            String decision = allowed ? "allowed" : "denied";
            meterRegistry.counter("ratelimiter.decisions.total",
                    "algorithm", Algorithm.TOKEN_BUCKET.name(),
                    "decision",  decision
            ).increment();

            return allowed
                    ? RateLimitResponse.allowed(tokensRemaining, capacity, resetAtMs, Algorithm.TOKEN_BUCKET)
                    : RateLimitResponse.denied(capacity, retryAfterMs, resetAtMs, Algorithm.TOKEN_BUCKET);

        } catch (Exception e) {
            return handleRedisFailure(request, capacity, e);
        }
    }

    // this is to tell if the user has a customised capacity
    // if not then use the default capacity
    private int resolveCapacity(RateLimitRequest request,
                                RateLimiterProperties.TokenBucket config) {
        if (request.getCustomMaxRequests() != null && request.getCustomMaxRequests() > 0) {
            return request.getCustomMaxRequests();
        }
        return config.getCapacity();
    }

    private RateLimitResponse handleRedisFailure(RateLimitRequest request, int capacity, Exception e) {
        // this is for the metics part
        log.error("[TokenBucket] Redis unavailable. client={} error={}", request.getClientKey(), e.getMessage());
        meterRegistry.counter("ratelimiter.redis.failures.total", "algorithm", "TOKEN_BUCKET").increment();
        boolean failOpen = "FAIL_OPEN".equalsIgnoreCase(properties.getRedisFailureStrategy());
        long now = System.currentTimeMillis();
        // this is the main logic for the redis failure
        return failOpen
                ? RateLimitResponse.allowed(capacity, capacity, now + 60_000L, Algorithm.TOKEN_BUCKET)
                : RateLimitResponse.denied(capacity, 5_000L, now + 5_000L, Algorithm.TOKEN_BUCKET);
    }

    private void recordRedisLatency(long durationMs, Algorithm algorithm) {
        meterRegistry.timer("ratelimiter.redis.duration.ms", "algorithm", algorithm.name())
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
