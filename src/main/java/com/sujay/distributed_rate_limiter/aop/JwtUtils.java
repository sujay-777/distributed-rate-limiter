package com.sujay.distributed_rate_limiter.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;

import java.util.Base64;

@Slf4j
public class JwtUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String extractSubject(String token) {
        try {
            // Split on "." — a JWT always has exactly 3 parts
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.debug("[JwtUtils] Malformed JWT — expected 3 parts, got {}", parts.length);
                return "anonymous";
            }

            // Base64URL decode the payload (middle part).
            // Base64URL uses '-' and '_' instead of '+' and '/'.
            // Java's Base64.getUrlDecoder() handles this correctly.
            byte[] payloadBytes = Base64.getUrlDecoder().decode(
                    // Add padding if needed — Base64URL sometimes strips '=' padding
                    addBase64Padding(parts[1])
            );

            String payloadJson = new String(payloadBytes, java.nio.charset.StandardCharsets.UTF_8);

            // Parse the JSON payload as a Map and extract "sub"
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = MAPPER.readValue(payloadJson, Map.class);

            Object subject = claims.get("sub");
            if (subject == null) {
                log.debug("[JwtUtils] JWT has no 'sub' claim");
                return "anonymous";
            }

            return subject.toString();

        } catch (Exception e) {
            // Malformed token, expired token, anything — treat as anonymous.
            // Don't throw — a bad JWT should just get the default (tight) rate limit.
            log.debug("[JwtUtils] Could not parse JWT: {}", e.getMessage());
            return "anonymous";
        }
    }

    private static String addBase64Padding(String base64) {
        // Base64 strings must have length divisible by 4.
        // JWT strips trailing '=' padding. Add it back for Java's decoder.
        int remainder = base64.length() % 4;
        if (remainder == 2) return base64 + "==";
        if (remainder == 3) return base64 + "=";
        return base64;
    }
}
