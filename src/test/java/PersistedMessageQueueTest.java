import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks that queued jobs are persisted, processed, and tracked to completion.
 */
class PersistedMessageQueueTest {

    @TempDir
    Path tempDir;

    @Test
    void persistedQueueCapitalizesSubmittedStrings() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            PersistedMessageQueue queue = new PersistedMessageQueue(tempDir.resolve("persisted-queue"), 1);
            List<String> jobs = List.of("hello world", "java is fun", "systems design");

            for (String job : jobs) {
                queue.submit(job);
            }

            while (!jobs.stream().allMatch(queue::isFinished)) {
                Thread.sleep(20);
            }

            for (String job : jobs) {
                assertEquals("DONE", queue.jobStatusForPayload(job));
                assertEquals(job.toUpperCase(), queue.resultForPayload(job));
            }

            queue.shutdown();
        });
    }

}
