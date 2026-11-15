import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Confirms the token bucket and sliding-window rate limiters reject excess traffic as expected.
 */
class RateLimitingTest {

    @Test
    void tokenBucketAllowsUntilCapacityIsReached() {
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(2, 1.0);

        assertTrue(rateLimiter.allowRequest(0));
        assertTrue(rateLimiter.allowRequest(0));
        assertFalse(rateLimiter.allowRequest(0));
    }

    @Test
    void tokenBucketRefillsOverTime() {
        TokenBucketRateLimiter bucket = new TokenBucketRateLimiter(2, 1.0);

        assertTrue(bucket.allowRequest(0));
        assertTrue(bucket.allowRequest(0));
        assertFalse(bucket.allowRequest(0));
        assertTrue(bucket.allowRequest(1000));
    }

    @Test
    void slidingWindowRejectsRequestsOutsideWindow() {
        SlidingWindowRateLimiter window = new SlidingWindowRateLimiter(2, 1000);

        assertTrue(window.allowRequest(0));
        assertTrue(window.allowRequest(500));
        assertFalse(window.allowRequest(1000));
    }
}
