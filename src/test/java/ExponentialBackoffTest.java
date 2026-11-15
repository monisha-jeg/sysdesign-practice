import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Ensures the retry delay doubles up to the configured cap.
 */
class ExponentialBackoffTest {

    @Test
    void backoffDelayDoublesUntilCap() {
        ExponentialBackoff backoff = new ExponentialBackoff(10, 100, 7);

        assertEquals(10, backoff.getDelayMs(0));
        assertEquals(20, backoff.getDelayMs(1));
        assertEquals(40, backoff.getDelayMs(2));
        assertEquals(80, backoff.getDelayMs(3));
        assertEquals(100, backoff.getDelayMs(4));
    }
}
