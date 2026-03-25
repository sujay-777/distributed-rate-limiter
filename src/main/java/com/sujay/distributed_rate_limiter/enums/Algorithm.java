package com.sujay.distributed_rate_limiter.enums;

public enum Algorithm {
    FIXED_WINDOW,
    SLIDING_WINDOW,
    TOKEN_BUCKET;

    // Utility: safe parse with meaningful error
    public static Algorithm fromString(String value) {
        try {
            return Algorithm.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown algorithm: " + value + ". Valid values: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET"
            );
        }
    }
}
