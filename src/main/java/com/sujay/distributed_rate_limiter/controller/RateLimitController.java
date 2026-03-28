package com.sujay.distributed_rate_limiter.controller;

import com.sujay.distributed_rate_limiter.factory.RateLimiterFactory;
import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/ratelimit")
public class RateLimitController {

    private final RateLimiterFactory factory;

    @PostMapping("/check")
    public ResponseEntity<RateLimitResponse> check(
            @Valid @RequestBody RateLimitRequest request) {

        log.debug("[Controller] Evaluating: client={} endpoint={} algorithm={}",
                request.getClientKey(), request.getEndpoint(), request.getAlgorithm());

        RateLimitResponse response = factory
                .getService(request.getAlgorithm())
                .evaluate(request);

        HttpStatus status = response.isAllowed()
                ? HttpStatus.OK
                : HttpStatus.TOO_MANY_REQUESTS;

        return ResponseEntity
                .status(status)
                // Standard rate limit headers — clients read these to implement backoff
                .header("X-RateLimit-Limit",     String.valueOf(response.getLimit()))
                .header("X-RateLimit-Remaining", String.valueOf(response.getRemainingRequests()))
                .header("X-RateLimit-Reset",     String.valueOf(response.getResetAtEpochMs()))
                // Retry-After: only meaningful when denied. Seconds (not ms) per RFC 7231.
                .header("Retry-After", response.getRetryAfterMs() != null
                        ? String.valueOf(response.getRetryAfterMs() / 1000)
                        : "0")
                .body(response);
    }

    // Health + smoke test endpoint — useful for k6 warmup
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("rate-limiter-service is up");
    }




}
