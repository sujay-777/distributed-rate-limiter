package com.sujay.distributed_rate_limiter.factory;

import com.sujay.distributed_rate_limiter.configuration.RateLimiterProperties;
import com.sujay.distributed_rate_limiter.enums.Algorithm;
import com.sujay.distributed_rate_limiter.service.RateLimiterService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RateLimiterFactory {

    private final Map<Algorithm, RateLimiterService> serviceMap;
    private final RateLimiterProperties properties;

    public RateLimiterFactory(List<RateLimiterService> services,
                              RateLimiterProperties properties) {
        this.properties = properties;
        // Spring injects ALL beans implementing RateLimiterService.
        // This map builds itself — add a 4th algorithm class and it appears here automatically.
        this.serviceMap = services.stream()
                .collect(Collectors.toMap(RateLimiterService::getAlgorithm, Function.identity()));
    }

    public RateLimiterService getService(Algorithm requested) {
        Algorithm resolved = requested != null
                ? requested
                : Algorithm.valueOf(properties.getDefaultAlgorithm());

        RateLimiterService service = serviceMap.get(resolved);
        if (service == null) {
            throw new IllegalArgumentException(
                    "No implementation for algorithm: " + resolved +
                            ". Available: " + serviceMap.keySet()
            );
        }
        return service;
    }

}
