package com.sujay.distributed_rate_limiter.service;

import com.sujay.distributed_rate_limiter.configuration.RateLimiterProperties;
import com.sujay.distributed_rate_limiter.enums.Algorithm;
import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;
import com.sujay.distributed_rate_limiter.service.impl.TokenBucketRateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenBucketRateLimiterTest {

    private RedisTemplate<String, String> redisTemplate;
    private DefaultRedisScript<List> script;
    private TokenBucketRateLimiter rateLimiter;
    private RateLimiterProperties properties;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        script = mock(DefaultRedisScript.class);
        properties = buildDefaultProperties();

        rateLimiter = new TokenBucketRateLimiter(
                redisTemplate, script, properties,
                new SimpleMeterRegistry()
        );
    }

    @Test
    void shouldAllowRequest_whenTokensAvailable() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 4L, 0L));

        RateLimitRequest request = buildRequest("user:test1", Algorithm.TOKEN_BUCKET);
        RateLimitResponse response = rateLimiter.evaluate(request);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getRemainingRequests()).isEqualTo(4);
        assertThat(response.getRetryAfterMs()).isNull();
    }

    @Test
    void shouldDenyRequest_whenBucketEmpty() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(0L, 0L, 500L));

        RateLimitRequest request = buildRequest("user:test2", Algorithm.TOKEN_BUCKET);
        RateLimitResponse response = rateLimiter.evaluate(request);

        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getRemainingRequests()).isEqualTo(0);
        assertThat(response.getRetryAfterMs()).isEqualTo(500L);
    }

    @Test
    void shouldFailOpen_whenRedisThrowsException() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("Connection refused"));

        RateLimitRequest request = buildRequest("user:test3", Algorithm.TOKEN_BUCKET);
        RateLimitResponse response = rateLimiter.evaluate(request);

        assertThat(response.isAllowed()).isTrue();
    }

    @Test
    void shouldRespectCustomMaxRequests_fromRequest() {
        when(redisTemplate.execute(any(), anyList(), any(Object[].class)))
                .thenReturn(List.of(1L, 499L, 0L));

        RateLimitRequest request = RateLimitRequest.builder()
                .clientKey("user:premium1")
                .endpoint("/api/analytics")
                .algorithm(Algorithm.TOKEN_BUCKET)
                .customMaxRequests(500)
                .build();

        RateLimitResponse response = rateLimiter.evaluate(request);

        assertThat(response.isAllowed()).isTrue();
        assertThat(response.getLimit()).isEqualTo(500);
    }

    private RateLimitRequest buildRequest(String clientKey, Algorithm algorithm) {
        return RateLimitRequest.builder()
                .clientKey(clientKey)
                .endpoint("/api/test")
                .algorithm(algorithm)
                .build();
    }

    private RateLimiterProperties buildDefaultProperties() {
        RateLimiterProperties props = new RateLimiterProperties();
        props.setRedisFailureStrategy("FAIL_OPEN");
        RateLimiterProperties.TokenBucket tb = new RateLimiterProperties.TokenBucket();
        tb.setCapacity(10);
        tb.setRefillRate(2.0);
        props.getAlgorithms().setTokenBucket(tb);
        return props;
    }
}