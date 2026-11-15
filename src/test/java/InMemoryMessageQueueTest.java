import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Checks that a fixed worker pool drains queued tasks and processes every submitted job.
 */
class InMemoryMessageQueueTest {

    @Test
    void workersProcessQueuedJobs() throws Exception {
        InMemoryMessageQueue queue = new InMemoryMessageQueue(2);
        Set<String> processed = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(3);

        queue.submit(() -> {
            processed.add("job-1");
            latch.countDown();
        });
        queue.submit(() -> {
            processed.add("job-2");
            latch.countDown();
        });
        queue.submit(() -> {
            processed.add("job-3");
            latch.countDown();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(Set.of("job-1", "job-2", "job-3"), processed);

        queue.shutdown();
    }
}
