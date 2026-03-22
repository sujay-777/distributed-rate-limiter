package com.sujay.distributed_rate_limiter.model;

import jdk.jfr.DataAmount;


public class RateLimitRequest {
    private final String clientKey;
    private final int limit;
    private final int windowSeconds;

    public RateLimitRequest(String clientKey, int limit, int windowSeconds) {
        this.clientKey = clientKey;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    public String getClientKey() {
        return clientKey;
    }

    public int getLimit() {
        return limit;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }
}
