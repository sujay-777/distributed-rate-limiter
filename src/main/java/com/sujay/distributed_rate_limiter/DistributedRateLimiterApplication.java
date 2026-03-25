package com.sujay.distributed_rate_limiter;

import com.sujay.distributed_rate_limiter.algorithm.FixedWindowAlgorithm;
import com.sujay.distributed_rate_limiter.algorithm.RateLimiterAlgorithm;
import com.sujay.distributed_rate_limiter.configuration.TestingYML;
import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DistributedRateLimiterApplication {

	public static void main(String[] args) throws InterruptedException {
		SpringApplication.run(DistributedRateLimiterApplication.class, args);
    }

}
