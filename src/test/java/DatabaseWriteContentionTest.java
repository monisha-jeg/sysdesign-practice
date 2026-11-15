import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the main database contention patterns and confirms stale writes are rejected.
 */
class DatabaseWriteContentionTest {

    private static final String JDBC_URL = "jdbc:h2:mem:contentiondb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private static DatabaseWriteContention contention;

    @BeforeAll
    static void setUp() throws SQLException {
        DataSource dataSource = createDataSource();
        contention = new DatabaseWriteContention(dataSource);
        contention.createSchema();
    }

    @BeforeEach
    void resetState() throws SQLException {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE accounts");
        }
    }

    private static DataSource createDataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(JDBC_URL);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        return dataSource;
    }

    @Test
    void conditionalWriteRejectsStaleBalance() throws SQLException {
        contention.saveAccount(1L, 100);

        int firstResult = contention.conditionalWrite(1L, 10, 100);
        assertEquals(110, firstResult);

        assertThrows(SQLException.class, () -> contention.conditionalWrite(1L, 5, 100));
    }

    @Test
    void pessimisticLockSerializesConcurrentWrites() throws Exception {
        // Step 1: initialize the account so both threads are updating the same row.
        contention.saveAccount(1L, 100);

        // Step 2: create two workers that will race on the same account.
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);

        // Step 3: worker A waits until both are ready, then tries to lock and update the row.
        Future<Integer> first = executor.submit(() -> {
            latch.await();
            return contention.pessimisticLockWrite(1L, 10);
        });

        // Step 4: worker B waits for the same signal, then tries to lock and update the row.
        Future<Integer> second = executor.submit(() -> {
            latch.await();
            return contention.pessimisticLockWrite(1L, 20);
        });

        // Step 5: release both threads at the same time so the lock contention happens.
        latch.countDown();

        // Step 6: both operations finish, and we verify that the final balance is consistent.
        Set<Integer> results = new HashSet<>(Set.of(first.get(), second.get()));
        assertEquals(2, results.size());
        assertTrue(results.contains(130));
        assertEquals(130, contention.getBalance(1L));

        // Step 7: stop the executor after the test is complete.
        executor.shutdown();
    }

    @Test
    void optimisticLockRejectsStaleVersion() throws SQLException {
        contention.saveAccount(1L, 100);
        int currentVersion = contention.getVersion(1L);

        int updated = contention.optimisticLockWrite(1L, 10, currentVersion);
        assertEquals(110, updated);

        assertThrows(SQLException.class, () -> contention.optimisticLockWrite(1L, 5, currentVersion));
        assertEquals(110, contention.getBalance(1L));
    }

    @Test
    void isolationLevelMethodsReturnExpectedBalance() throws SQLException {
        contention.saveAccount(1L, 100);

        assertEquals(100, contention.readCommittedBalance(1L));
        assertEquals(100, contention.repeatableReadBalance(1L));
        assertEquals(100, contention.serializableBalance(1L));
    }

    @Test
    void distributedLockOnlyAllowsOneOwnerAtATime() throws SQLException {
        assertTrue(contention.acquireDistributedLock("invoice-42", "node-a", 60_000));
        assertFalse(contention.acquireDistributedLock("invoice-42", "node-b", 60_000));

        contention.releaseDistributedLock("invoice-42", "node-a");
        assertTrue(contention.acquireDistributedLock("invoice-42", "node-b", 60_000));
    }
}
