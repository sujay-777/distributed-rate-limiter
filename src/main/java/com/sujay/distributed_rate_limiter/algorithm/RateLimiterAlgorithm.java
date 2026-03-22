package com.sujay.distributed_rate_limiter.algorithm;

import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;

public interface RateLimiterAlgorithm {

    RateLimitResponse check(RateLimitRequest request);
}
