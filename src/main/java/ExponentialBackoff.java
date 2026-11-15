/**
 * Models exponential backoff by increasing retry delay after each failed attempt,
 * which helps distributed and networked systems avoid thundering-herd spikes.
 */
public class ExponentialBackoff {
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final long maxRetries;

    public ExponentialBackoff(long initialDelayMs, long maxDelayMs, long maxRetries) {
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.maxRetries = maxRetries;
    }

    public long getDelayMs(int attemptNumber) {
        // Once the configured retry ceiling is passed, the client should hold the max backoff value.
        if (attemptNumber > maxRetries) {
            return maxDelayMs;
        }
        // Each retry doubles the delay until the cap is reached; the bit shift is a compact form of doubling.
        long delay = initialDelayMs << Math.min(attemptNumber, 62);
        return Math.min(delay, maxDelayMs);
    }
}
