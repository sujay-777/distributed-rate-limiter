package com.sujay.distributed_rate_limiter;

import com.sujay.distributed_rate_limiter.algorithm.FixedWindowAlgorithm;
import com.sujay.distributed_rate_limiter.algorithm.RateLimiterAlgorithm;
import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DistributedRateLimiterApplication {

	public static void main(String[] args) throws InterruptedException {
//		SpringApplication.run(DistributedRateLimiterApplication.class, args);

        RateLimiterAlgorithm algorithm = new FixedWindowAlgorithm();

        // Use a 3 second window so we don't wait 60 seconds in our test
        RateLimitRequest request = new RateLimitRequest("user-123", 3, 3);

        System.out.println("=== Fixed Window Test — With Time Reset ===");
        System.out.println("Limit: 3 requests per 3 seconds");
        System.out.println("-------------------------------------------");

        System.out.println("\n[Window 1]");
        for (int i = 1; i <= 4; i++) {
            RateLimitResponse response = algorithm.check(request);
            System.out.printf(
                    "Request %d → allowed=%-5s remaining=%d%n",
                    i, response.isAllowed(), response.getRemaining()
            );
        }

        System.out.println("\n[Waiting 4 seconds for window to reset...]");
        Thread.sleep(4000);

        System.out.println("\n[Window 2 — should be fresh]");
        for (int i = 1; i <= 4; i++) {
            RateLimitResponse response = algorithm.check(request);
            System.out.printf(
                    "Request %d → allowed=%-5s remaining=%d%n",
                    i, response.isAllowed(), response.getRemaining()
            );
        }
    }

}
