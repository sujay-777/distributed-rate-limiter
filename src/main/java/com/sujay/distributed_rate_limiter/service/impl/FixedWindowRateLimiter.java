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

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixedWindowRateLimiter implements RateLimiterService {

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> fixedWindowScript;
    private final RateLimiterProperties properties;
    private final MeterRegistry meterRegistry;   // injected by Spring — Micrometer auto-configures this

    @Override
    public Algorithm getAlgorithm(){
        return Algorithm.FIXED_WINDOW;
    }

    @Override
    public RateLimitResponse evaluate(RateLimitRequest request){

        String key = request.buildRedisKey(Algorithm.FIXED_WINDOW);

        int maxRequests = resolveMaxRequests(request);
        int windowSecs = properties.getAlgorithms().getFixedWindow().getWindowSizeSeconds();

        try{
            long start = System.currentTimeMillis();
            // im sending the key, script, max_req and window_sec
            //once this is executed we get the result in the list format

            List<Long> result = (List<Long>) redisTemplate.execute(
                    fixedWindowScript,
                    Collections.singletonList(key),
                    String.valueOf(maxRequests),
                    String.valueOf(windowSecs)
            );

            recordRedisLatency(System.currentTimeMillis() - start, Algorithm.FIXED_WINDOW);

            return buildResponse(result, maxRequests, Algorithm.FIXED_WINDOW);
        }catch (Exception e) {
            return handleRedisFailure(request, maxRequests, Algorithm.FIXED_WINDOW, e);
        }
    }

    protected int resolveMaxRequests(RateLimitRequest request) {
        // Request-level override → wins. Useful for premium users.
        if (request.getCustomMaxRequests() != null && request.getCustomMaxRequests() > 0) {
            return request.getCustomMaxRequests();
        }
        return properties.getAlgorithms().getFixedWindow().getMaxRequests();
    }

    // this is for the rate limiting logic..

    protected RateLimitResponse buildResponse(List<Long> luaResult,
                                              int maxRequests, Algorithm algorithm) {
        // this typically means that if allowed return truue
        boolean allowed   = luaResult.get(0) == 1L;
        long remaining    = luaResult.get(1);
        long ttlMs        = luaResult.get(2);
        long resetAtMs    = System.currentTimeMillis() + ttlMs;

        // Increment Prometheus counter — tagged with algorithm and decision
        // This gives you the Grafana query:
        //   sum(ratelimiter_decisions_total{decision="denied"}) by (algorithm)
        String decision = allowed ? "allowed" : "denied";
        meterRegistry.counter("ratelimiter.decisions.total",
                "algorithm", algorithm.name(),
                "decision",  decision
        ).increment();

        // the logic goes to the allowed method where the builder pattern is being used
        if (allowed) {
            return RateLimitResponse.allowed(remaining, maxRequests, resetAtMs, algorithm);
        } else {
            return RateLimitResponse.denied(maxRequests, ttlMs, resetAtMs, algorithm);
        }
    }

    protected RateLimitResponse handleRedisFailure(RateLimitRequest request,
                                                   int maxRequests,
                                                   Algorithm algorithm,
                                                   Exception e) {
        log.error("[RateLimiter] Redis unavailable. client={} strategy={} error={}",
                request.getClientKey(),
                properties.getRedisFailureStrategy(),
                e.getMessage()
        );

        // Track Redis failures as a metric — alert on this in Grafana
        meterRegistry.counter("ratelimiter.redis.failures.total",
                "algorithm", algorithm.name()
        ).increment();

        boolean failOpen = "FAIL_OPEN".equalsIgnoreCase(properties.getRedisFailureStrategy());
        long now = System.currentTimeMillis();

        if (failOpen) {
            return RateLimitResponse.allowed(maxRequests, maxRequests, now + 60_000L, algorithm);
        } else {
            return RateLimitResponse.denied(maxRequests, 5_000L, now + 5_000L, algorithm);
        }
    }

    protected void recordRedisLatency(long durationMs, Algorithm algorithm) {
        meterRegistry.timer("ratelimiter.redis.duration.ms",
                "algorithm", algorithm.name()
        ).record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }









}
