import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple in-memory FIFO task queue backed by a fixed worker pool.
 * The queue is intentionally small and easy to reason about for single-producer demos.
 */
public class InMemoryMessageQueue {
    // The queue stores jobs in FIFO order.
    private final Deque<Runnable> jobs = new ArrayDeque<>();

    // The worker pool executes jobs concurrently.
    private final ExecutorService workers;

    // The workers use this lock to coordinate access to the queue.
    private final Object lock = new Object();

    // Shutdown stops workers after the queue is drained.
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public InMemoryMessageQueue(int workerCount) {
        // Start a fixed pool so work is processed by a bounded number of threads.
        workers = Executors.newFixedThreadPool(workerCount);
        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::workerLoop);
        }
    }

    public void submit(Runnable job) {
        // Prevent new work from being accepted after shutdown starts.
        if (shutdown.get()) {
            throw new IllegalStateException("Queue is shut down");
        }

        // Add tasks to the tail so the oldest queued job is processed first.
        synchronized (lock) {
            jobs.addLast(job);
            lock.notify();
        }
    }

    public void shutdown() {
        // Flip the shutdown flag and wake every waiting worker so they can exit cleanly.
        synchronized (lock) {
            shutdown.set(true);
            lock.notifyAll();
        }
        workers.shutdown();
    }

    private void workerLoop() {
        while (true) {
            Runnable job;

            // Workers wait on the shared monitor until work exists or shutdown begins.
            synchronized (lock) {
                while (jobs.isEmpty() && !shutdown.get()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                // When shutdown is active and the queue is empty, the worker can exit.
                if (jobs.isEmpty() && shutdown.get()) {
                    return;
                }

                job = jobs.removeFirst();
            }

            // Run the actual job after releasing the lock to avoid blocking the queue unnecessarily.
            try {
                job.run();
            } catch (RuntimeException ex) {
                System.err.println("Job failed: " + ex.getMessage());
            }
        }
    }
}
