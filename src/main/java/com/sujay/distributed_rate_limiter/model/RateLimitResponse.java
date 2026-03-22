package com.sujay.distributed_rate_limiter.model;

public class RateLimitResponse {
    private final boolean allowed;
    private final int remaining;
    private final long resetAfterSeconds;
    private final String reason;

    public RateLimitResponse(boolean allowed, int remaining,
                             long resetAfterSeconds, String reason) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.resetAfterSeconds = resetAfterSeconds;
        this.reason = reason;
    }

    public boolean isAllowed() { return allowed; }
    public int getRemaining() { return remaining; }
    public long getResetAfterSeconds() { return resetAfterSeconds; }
    public String getReason() { return reason; }
}
