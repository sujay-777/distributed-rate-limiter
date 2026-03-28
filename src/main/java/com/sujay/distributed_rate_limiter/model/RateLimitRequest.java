package com.sujay.distributed_rate_limiter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sujay.distributed_rate_limiter.enums.Algorithm;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;


@Value
@Builder
public class RateLimitRequest {

    @NotBlank(message = "clientKey is required")
    @JsonProperty("clientKey")
    String clientKey;

    @NotBlank(message = "endpoint is required")
    String endpoint;

    Algorithm algorithm;

    // this is for the premium users or for the custom purposes
    Integer customMaxRequests;

    public String buildRedisKey(Algorithm resolvedAlgorithm) {
        return String.format("%s::%s::%s", clientKey, endpoint, resolvedAlgorithm.name());
    }
}
