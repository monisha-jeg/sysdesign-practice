
/**
 * Allows a bounded number of requests to pass while refilling capacity over time.
 * This implementation models a classic token bucket where each request consumes one token.
 */
public class TokenBucketRateLimiter {
    private final double capacity;
    private final double refillRatePerMillisecond;
    private double tokens;
    private long lastRefillMillis;

    public TokenBucketRateLimiter(int capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerMillisecond = refillRatePerSecond / 1000.0;
        this.tokens = capacity;
        this.lastRefillMillis = 0L;
    }

    public boolean allowRequest(long nowMillis) {
        double elapsedMs = Math.max(0, nowMillis - lastRefillMillis);
        tokens = Math.min(capacity, tokens + elapsedMs * refillRatePerMillisecond);
        lastRefillMillis = nowMillis;

        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }
}
