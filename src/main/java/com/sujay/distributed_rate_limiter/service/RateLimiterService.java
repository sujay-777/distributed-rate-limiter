package com.sujay.distributed_rate_limiter.service;

import com.sujay.distributed_rate_limiter.enums.Algorithm;
import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;
import org.springframework.stereotype.Service;

// this wasn't there, i just added it
@Service
public interface RateLimiterService {

    RateLimitResponse evaluate(RateLimitRequest rateLimitRequest);
    Algorithm getAlgorithm();

}
