package com.sujay.distributed_rate_limiter.controller;

public class CheckRequest {
    private String clientKey;
    private int limit;
    private int windowSeconds;

    public CheckRequest() {}

    public String getClientKey() { return clientKey; }
    public int getLimit() { return limit; }
    public int getWindowSeconds() { return windowSeconds; }

    public void setClientKey(String clientKey) { this.clientKey = clientKey; }
    public void setLimit(int limit) { this.limit = limit; }
}
