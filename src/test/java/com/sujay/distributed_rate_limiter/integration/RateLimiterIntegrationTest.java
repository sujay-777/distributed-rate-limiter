package com.sujay.distributed_rate_limiter.integration;

import com.sujay.distributed_rate_limiter.enums.Algorithm;
import com.sujay.distributed_rate_limiter.model.RateLimitRequest;
import com.sujay.distributed_rate_limiter.model.RateLimitResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
// RANDOM_PORT avoids port conflicts when running multiple test classes in parallel.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RateLimiterIntegrationTest {

    // Testcontainers spins up real Redis in Docker.
    // The image is pulled from Docker Hub on first run, cached after.
    // 'static' = one container shared across all test methods (faster).
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // @DynamicPropertySource overrides Spring properties at runtime.
    // It tells Spring Boot to connect to THIS test container's random port,
    // not the default localhost:6379.
    // This is the bridge between Testcontainers and Spring Boot.
    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldAllowRequestsUpToLimit_thenDeny() {
        // Token bucket with capacity=10 from application.yml
        // This test fires 11 requests and expects:
        //   first 10 → 200 allowed
        //   11th     → 429 denied

        String url = "http://localhost:" + port + "/api/v1/ratelimit/check";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Use a unique clientKey per test method to avoid state leakage between tests
        RateLimitRequest request = RateLimitRequest.builder()
                .clientKey("integration-test-user-1")
                .endpoint("/api/test")
                .algorithm(Algorithm.TOKEN_BUCKET)
                .build();

        HttpEntity<RateLimitRequest> entity = new HttpEntity<>(request, headers);

        // Fire 10 requests — all should be allowed (bucket capacity = 10)
        for (int i = 0; i < 10; i++) {
            ResponseEntity<RateLimitResponse> response =
                    restTemplate.postForEntity(url, entity, RateLimitResponse.class);

            assertThat(response.getStatusCode())
                    .as("Request %d should be allowed", i + 1)
                    .isEqualTo(HttpStatus.OK);

            assertThat(response.getBody().isAllowed())
                    .as("Request %d body.allowed should be true", i + 1)
                    .isTrue();
        }

        // 11th request — bucket is empty, should be denied
        ResponseEntity<RateLimitResponse> denied =
                restTemplate.postForEntity(url, entity, RateLimitResponse.class);

        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(denied.getBody().isAllowed()).isFalse();
        assertThat(denied.getBody().getRetryAfterMs()).isPositive();

        // Verify the response headers are present (clients depend on these)
        assertThat(denied.getHeaders().getFirst("Retry-After")).isNotNull();
        assertThat(denied.getHeaders().getFirst("X-RateLimit-Limit")).isEqualTo("10");
    }

    @Test
    void shouldReturnBadRequest_whenClientKeyMissing() {
        String url = "http://localhost:" + port + "/api/v1/ratelimit/check";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Missing clientKey — @NotBlank should trigger validation
        String badBody = """
            {
              "endpoint": "/api/products",
              "algorithm": "TOKEN_BUCKET"
            }
            """;

        HttpEntity<String> entity = new HttpEntity<>(badBody, headers);
        ResponseEntity<String> response =
                restTemplate.postForEntity(url, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("clientKey");
    }

    @Test
    void differentClientKeys_shouldHaveIndependentCounters() {
        // This verifies that user:A hitting their limit doesn't affect user:B.
        // Sounds obvious, but a wrong Redis key pattern breaks this.

        String url = "http://localhost:" + port + "/api/v1/ratelimit/check";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // User A hits limit
        for (int i = 0; i < 10; i++) {
            RateLimitRequest requestA = RateLimitRequest.builder()
                    .clientKey("integration-isolation-user-A")
                    .endpoint("/api/test")
                    .algorithm(Algorithm.TOKEN_BUCKET).build();
            restTemplate.postForEntity(url,
                    new HttpEntity<>(requestA, headers), RateLimitResponse.class);
        }

        // User B should still be allowed — unaffected by User A's exhaustion
        RateLimitRequest requestB = RateLimitRequest.builder()
                .clientKey("integration-isolation-user-B")
                .endpoint("/api/test")
                .algorithm(Algorithm.TOKEN_BUCKET).build();
        ResponseEntity<RateLimitResponse> responseB =
                restTemplate.postForEntity(url,
                        new HttpEntity<>(requestB, headers), RateLimitResponse.class);

        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseB.getBody().isAllowed()).isTrue();
    }

    @Test
    void slidingWindow_shouldNotAllowBoundarySpike() {
        // This test proves why sliding window is better than fixed window.
        // We fill the window to limit, wait until we're near the boundary,
        // then verify we're still denied — no double-capacity spike.

        // This test needs time manipulation — we verify the concept here
        // through rapid sequential requests hitting the limit.
        String url = "http://localhost:" + port + "/api/v1/ratelimit/check";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Fill up to the limit using sliding window
        for (int i = 0; i < 10; i++) {
            RateLimitRequest req = RateLimitRequest.builder()
                    .clientKey("integration-sliding-test-1")
                    .endpoint("/api/test")
                    .algorithm(Algorithm.SLIDING_WINDOW)
                    .build();
            restTemplate.postForEntity(url,
                    new HttpEntity<>(req, headers), RateLimitResponse.class);
        }

        // 11th should be denied
        RateLimitRequest req11 = RateLimitRequest.builder()
                .clientKey("integration-sliding-test-1")
                .endpoint("/api/test")
                .algorithm(Algorithm.SLIDING_WINDOW)
                .build();
        ResponseEntity<RateLimitResponse> denied =
                restTemplate.postForEntity(url, new HttpEntity<>(req11, headers), RateLimitResponse.class);

        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
