package com.sujay.distributed_rate_limiter.model;

import com.sujay.distributed_rate_limiter.enums.Algorithm;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class RateLimitResponse {

         boolean allowed;

         long remainingRequests;

         long limit;

         long resetAtEpochMs;

         Long retryAfterMs;

         Algorithm algorithm;

         Instant decidedAt;

    public static RateLimitResponse allowed(long remaining, long limit,
                                            long resetAtMs, Algorithm algorithm) {
        return RateLimitResponse.builder()
                .allowed(true)
                .remainingRequests(remaining)
                .limit(limit)
                .resetAtEpochMs(resetAtMs)
                .retryAfterMs(null)            // not needed when allowed
                .algorithm(algorithm)
                .decidedAt(Instant.now())
                .build();
    }

    public static RateLimitResponse denied(long limit, long retryAfterMs,
                                           long resetAtMs, Algorithm algorithm) {
        return RateLimitResponse.builder()
                .allowed(false)
                .remainingRequests(0)
                .limit(limit)
                .resetAtEpochMs(resetAtMs)
                .retryAfterMs(retryAfterMs)
                .algorithm(algorithm)
                .decidedAt(Instant.now())
                .build();
    }



}
