import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Tracks recent requests inside a fixed time window and rejects the next request once the limit is reached.
 */
public class SlidingWindowRateLimiter {
    private final int limit;
    private final long windowMs;
    private final Deque<Long> requests = new ArrayDeque<>();

    public SlidingWindowRateLimiter(int limit, long windowMs) {
        this.limit = limit;
        this.windowMs = windowMs;
    }

    public boolean allowRequest(long nowMillis) {
        while (!requests.isEmpty() && nowMillis - requests.peekFirst() > windowMs) {
            requests.removeFirst();
        }
        if (requests.size() >= limit) {
            return false;
        }
        requests.addLast(nowMillis);
        return true;
    }

    public List<Long> snapshot() {
        return new ArrayList<>(requests);
    }
}
