package com.sujay.distributed_rate_limiter.configuration;


import com.sujay.distributed_rate_limiter.algorithm.FixedWindowAlgorithm;
import io.prometheus.metrics.core.metrics.SlidingWindow;
import lombok.Data;
import org.apache.kafka.common.metrics.stats.TokenBucket;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    private String defaultAlgorithm = "TOKEN_BUCKET";
    private String redisFailureStrategy = "FAIL_OPEN";
    private Algorithms algorithms = new Algorithms();

    @Data
    private static class Algorithms{
        private FixedWindow fixedWindow = new FixedWindow();
        private SlidingWindow slidingWindow = new SlidingWindow();
        private TokenBucket tokenBucket = new TokenBucket();
    }

    @Data
    private static class FixedWindow{
        private Integer maxRequests = 100;
        private Integer windowSizeSeconds = 60;
    }

    @Data
    public static class SlidingWindow {
        private int maxRequests = 100;
        private int windowSizeSeconds = 60;
    }

    @Data
    public static class TokenBucket {
        private int capacity = 10;
        private double refillRate = 2.0;  // tokens per second
    }


}
