package com.sujay.distributed_rate_limiter.aop;

import com.sujay.distributed_rate_limiter.configuration.RateLimiterProperties;
import com.sujay.distributed_rate_limiter.enums.Algorithm;
import com.sujay.distributed_rate_limiter.factory.RateLimiterFactory;
import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterFactory factory;
    private final RateLimiterProperties properties;

    @Around("@annotation(rateLimit)")
    public Object enforcedRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable{


        HttpServletRequest httpRequest = getCurrentHttpRequest();
        if (httpRequest == null) {
            // Should never happen in a web context, but defensive code is good code.
            log.warn("[RateLimitAspect] No HTTP request in context — proceeding without rate limiting");
            return joinPoint.proceed();
        }

        String clientKey = resolveClientKey(httpRequest, rateLimit);
        String endpoint  = httpRequest.getRequestURI();

        Integer customMax = null;
        Integer customWindow = null;
        Algorithm algorithm = rateLimit.algorithm();

// Tier takes priority
        if (!rateLimit.tier().isBlank()) {
            RateLimiterProperties.Tier tier = properties.getTier(rateLimit.tier());

            if (tier != null) {
                customMax = tier.getMaxRequests();
                customWindow = tier.getWindowSizeSeconds();
                algorithm = Algorithm.valueOf(tier.getAlgorithm());
            }
        } else {
            customMax = rateLimit.maxRequests() > 0 ? rateLimit.maxRequests() : null;
            customWindow = rateLimit.windowSeconds() > 0 ? rateLimit.windowSeconds() : null;
        }

        RateLimitRequest rateLimitRequest = RateLimitRequest.builder()
                .clientKey(clientKey)
                .endpoint(endpoint)
                .algorithm(algorithm)
                .customMaxRequests(customMax)
                .customWindowSeconds(customWindow)   // we'll add this field to RateLimitRequest
                .build();

        RateLimitResponse response = factory
                .getService(algorithm)
                .evaluate(rateLimitRequest);



        if (response.isAllowed()) {
            // Proceed calls the actual annotated method.
            // The return value from that method flows back to the HTTP client.
            return joinPoint.proceed();

        } else {
            // Short-circuit: we NEVER call joinPoint.proceed().
            // The original method never runs. We return 429 directly.
            // This is the power of @Around — we control whether the method executes.

            log.warn("[RateLimitAspect] DENIED client={} endpoint={} algo={} retryAfter={}ms",
                    clientKey, endpoint,
                    rateLimit.algorithm().name(),
                    response.getRetryAfterMs()
            );

            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    // These headers are the industry standard. Every major API uses them.
                    // RFC 6585 defines 429. RFC 7231 defines Retry-After.
                    .header("X-RateLimit-Limit",     String.valueOf(response.getLimit()))
                    .header("X-RateLimit-Remaining", "0")
                    .header("X-RateLimit-Reset",     String.valueOf(response.getResetAtEpochMs()))
                    // Retry-After is in SECONDS per the RFC, not milliseconds
                    .header("Retry-After", String.valueOf(
                            response.getRetryAfterMs() != null
                                    ? response.getRetryAfterMs() / 1000
                                    : 1
                    ))
                    .body(response);
        }
    }

    private String resolveClientKey(HttpServletRequest request, RateLimit annotation) {
        return switch (annotation.clientIdentifier()) {

            case IP_ADDRESS -> {
                // Must handle reverse proxies (nginx, AWS ALB, Cloudflare).
                // These proxies forward the real client IP in X-Forwarded-For.
                // Without this, every user looks like the proxy's IP — one shared limit.
                String ip = getClientIp(request);
                yield "ip:" + ip;
            }

            case JWT_SUBJECT -> {
                // Extract the user ID from the JWT in the Authorization header.
                // We'll implement full JWT parsing in the next section.
                String subject = extractJwtSubject(request);
                yield "user:" + subject;
            }

            case API_KEY -> {
                // B2B clients pass their API key in a header.
                // Rate limit per key = rate limit per company/integration.
                String apiKey = request.getHeader("X-API-Key");
                if (apiKey == null || apiKey.isBlank()) {
                    // No key provided — treat as anonymous with a tight limit
                    yield "apikey:anonymous";
                }
                // Hash the key before using it in Redis.
                // Never store raw API keys anywhere, even as a cache key.
                yield "apikey:" + hashApiKey(apiKey);
            }
        };
    }
    private String getClientIp(HttpServletRequest request) {
        // X-Forwarded-For can be a comma-separated list when requests
        // pass through multiple proxies: "client, proxy1, proxy2"
        // The FIRST address is always the real client.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        // X-Real-IP is set by nginx directly — single IP, no parsing needed
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        // Fallback: direct TCP connection (works locally, not behind proxy)
        return request.getRemoteAddr();
    }

    private String extractJwtSubject(HttpServletRequest request) {
        // We implement the full version in section 1d.
        // For now, a stub that reads the Authorization header.
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return "anonymous";
        }
        // Delegate to the JWT utility — built in 1d
        return JwtUtils.extractSubject(authHeader.substring(7));
    }

    private String hashApiKey(String rawKey) {
        // SHA-256 the key before using it as a Redis key prefix.
        // Why? Redis keys are visible in redis-cli, logs, metrics.
        // A hashed key leaks nothing about the original.
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // Take first 16 bytes as hex = 32 char string. Short enough for a key prefix.
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (Exception e) {
            // If SHA-256 fails (it won't), fall back to a truncated raw key
            return rawKey.length() > 16 ? rawKey.substring(0, 16) : rawKey;
        }
    }

    private HttpServletRequest getCurrentHttpRequest() {
        try {
            return ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (IllegalStateException e) {
            return null;
        }
    }



}
