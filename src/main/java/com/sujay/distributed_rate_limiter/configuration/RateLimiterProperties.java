package com.sujay.distributed_rate_limiter.configuration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    private String defaultAlgorithm = "TOKEN_BUCKET";
    private String redisFailureStrategy = "FAIL_OPEN";

    private Algorithms algorithms = new Algorithms();

    // ✅ Tiers config
    private Map<String, Tier> tiers = new HashMap<>();

    // ================= ALGORITHMS =================

    @Data
    public static class Algorithms {
        private FixedWindow fixedWindow = new FixedWindow();
        private SlidingWindow slidingWindow = new SlidingWindow();
        private TokenBucket tokenBucket = new TokenBucket();
    }

    @Data
    public static class FixedWindow {
        private int maxRequests = 100;
        private int windowSizeSeconds = 60;
    }

    @Data
    public static class SlidingWindow {
        private int maxRequests = 100;
        private int windowSizeSeconds = 60;
    }

    @Data
    public static class TokenBucket {
        private int capacity = 10;
        private double refillRate = 2.0;
    }

    @Data
    public static class Tier {
        private int maxRequests;
        private int windowSizeSeconds;
        private String algorithm;
    }


    public Tier getTier(String tierName) {
        return tiers.getOrDefault(tierName, null);
    }
}