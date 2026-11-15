import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Objects;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

/**
 * Demonstrates common database concurrency patterns such as conditional writes,
 * optimistic locking, pessimistic locking, and isolation-level tradeoffs.
 */
public class DatabaseWriteContention {
    private final DataSource dataSource;

    public DatabaseWriteContention(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    public void createSchema() throws SQLException {
        // Create the account table plus a simple lock table to model distributed coordination.
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS accounts (" +
                "id BIGINT PRIMARY KEY, " +
                "balance INT NOT NULL, " +
                "version INT NOT NULL DEFAULT 0)"
            );
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS distributed_locks (" +
                "lock_name VARCHAR(255) PRIMARY KEY, " +
                "owner VARCHAR(255) NOT NULL, " +
                "acquired_at TIMESTAMP NOT NULL, " +
                "expires_at TIMESTAMP NOT NULL)"
            );
        }
    }

    public void saveAccount(long id, int balance) throws SQLException {
        // Convenience wrapper used by tests and demos when versioning is not necessary.
        saveAccount(id, balance, 0);
    }

    public void saveAccount(long id, int balance, int version) throws SQLException {
        // Upsert the row so tests can seed data without needing to manually create the account first.
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement selectStatement = connection.prepareStatement(
                     "SELECT 1 FROM accounts WHERE id = ?")) {
                selectStatement.setLong(1, id);
                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    if (resultSet.next()) {
                        try (PreparedStatement updateStatement = connection.prepareStatement(
                                 "UPDATE accounts SET balance = ?, version = ? WHERE id = ?")) {
                            updateStatement.setInt(1, balance);
                            updateStatement.setInt(2, version);
                            updateStatement.setLong(3, id);
                            updateStatement.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement insertStatement = connection.prepareStatement(
                                 "INSERT INTO accounts (id, balance, version) VALUES (?, ?, ?)")) {
                            insertStatement.setLong(1, id);
                            insertStatement.setInt(2, balance);
                            insertStatement.setInt(3, version);
                            insertStatement.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    public int getBalance(long accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT balance FROM accounts WHERE id = ?")) {
            statement.setLong(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("No account found for id=" + accountId);
                }
                return resultSet.getInt("balance");
            }
        }
    }

    public int getVersion(long accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT version FROM accounts WHERE id = ?")) {
            statement.setLong(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("No account found for id=" + accountId);
                }
                return resultSet.getInt("version");
            }
        }
    }

    /**
     * Conditional write pattern.
     * This updates only if the current balance exactly matches the expected value.
     * If the row changed unexpectedly, no update happens and the caller gets a failure.
     */
    public int conditionalWrite(long accountId, int delta, int expectedBalance) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ? WHERE id = ? AND balance = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, delta);
            statement.setLong(2, accountId);
            statement.setInt(3, expectedBalance);

            int updatedRows = statement.executeUpdate();
            if (updatedRows == 0) {
                throw new SQLException("Conditional write failed: expected balance " + expectedBalance + " for id=" + accountId);
            }

            return getBalance(accountId);
        }
    }

    /**
     * Pessimistic locking pattern.
     * This holds a row lock while the value is read and updated so concurrent writers cannot
     * race on the same row. It is useful when a transaction must be serialized.
     */
    public int pessimisticLockWrite(long accountId, int delta) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            try (PreparedStatement selectStatement = connection.prepareStatement(
                     "SELECT balance FROM accounts WHERE id = ? FOR UPDATE")) {
                selectStatement.setLong(1, accountId);
                try (ResultSet resultSet = selectStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException("No account found for id=" + accountId);
                    }

                    int currentBalance = resultSet.getInt("balance");
                    int newBalance = currentBalance + delta;

                    try (PreparedStatement updateStatement = connection.prepareStatement(
                             "UPDATE accounts SET balance = ? WHERE id = ?")) {
                        updateStatement.setInt(1, newBalance);
                        updateStatement.setLong(2, accountId);
                        updateStatement.executeUpdate();
                    }

                    connection.commit();
                    return newBalance;
                }
            }
        } catch (SQLException exc) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // rollback failure is secondary to the original SQL error
                }
            }
            throw exc;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                    // ignore close failures
                }
            }
        }
    }

    /**
     * Optimistic locking pattern.
     * The update is allowed only if the version still matches the caller's expected version.
     * This avoids blocking and relies on version checks to detect stale updates.
     */
    public int optimisticLockWrite(long accountId, int delta, int expectedVersion) throws SQLException {
        String sql = "UPDATE accounts SET balance = balance + ?, version = version + 1 WHERE id = ? AND version = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, delta);
            statement.setLong(2, accountId);
            statement.setInt(3, expectedVersion);

            int updatedRows = statement.executeUpdate();
            if (updatedRows == 0) {
                throw new SQLException("Optimistic lock failed: expected version " + expectedVersion + " for id=" + accountId);
            }

            return getBalance(accountId);
        }
    }

    /**
     * Demonstrates READ_COMMITTED behavior by reading the balance on a connection configured
     * for the smallest practical isolation level used in most OLTP systems.
     */
    public int readCommittedBalance(long accountId) throws SQLException {
        return readBalanceWithIsolation(accountId, Connection.TRANSACTION_READ_COMMITTED);
    }

    /**
     * Demonstrates REPEATABLE_READ behavior by forcing the transaction to keep a consistent
     * snapshot for the life of the transaction.
     */
    public int repeatableReadBalance(long accountId) throws SQLException {
        return readBalanceWithIsolation(accountId, Connection.TRANSACTION_REPEATABLE_READ);
    }

    /**
     * Demonstrates SERIALIZABLE behavior by reading the row in a transaction that ensures no
     * concurrent update can cause phantom or inconsistent reads.
     */
    public int serializableBalance(long accountId) throws SQLException {
        return readBalanceWithIsolation(accountId, Connection.TRANSACTION_SERIALIZABLE);
    }

    private int readBalanceWithIsolation(long accountId, int isolationLevel) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setTransactionIsolation(isolationLevel);
            try (PreparedStatement statement = connection.prepareStatement(
                     "SELECT balance FROM accounts WHERE id = ?")) {
                statement.setLong(1, accountId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new SQLException("No account found for id=" + accountId);
                    }
                    return resultSet.getInt("balance");
                }
            }
        }
    }

    /**
     * Simple distributed lock implementation backed by a database row. Only one owner can hold
     * the lock for a given name until the lease expires or it is explicitly released.
     */
    public boolean acquireDistributedLock(String lockName, String ownerId, long leaseMillis) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // Step 1: start a transaction so the lock check and lock creation happen atomically.
            connection.setAutoCommit(false);

            try {
                // Step 2: lock the row for this specific lock name so only one process can inspect it at a time.
                try (PreparedStatement selectStatement = connection.prepareStatement(
                         "SELECT owner, expires_at FROM distributed_locks WHERE lock_name = ? FOR UPDATE")) {
                    selectStatement.setString(1, lockName);
                    try (ResultSet resultSet = selectStatement.executeQuery()) {
                        // Step 3: if the row already exists, another process may already own this lock.
                        if (resultSet.next()) {
                            String existingOwner = resultSet.getString("owner");
                            Timestamp expiresAt = resultSet.getTimestamp("expires_at");
                            Timestamp now = new Timestamp(System.currentTimeMillis());

                            // Step 4: if the same owner is asking again, extend the lease and keep the lock.
                            if (existingOwner.equals(ownerId)) {
                                Timestamp newExpiry = new Timestamp(System.currentTimeMillis() + leaseMillis);
                                try (PreparedStatement updateStatement = connection.prepareStatement(
                                         "UPDATE distributed_locks SET acquired_at = CURRENT_TIMESTAMP, expires_at = ? WHERE lock_name = ?")) {
                                    updateStatement.setTimestamp(1, newExpiry);
                                    updateStatement.setString(2, lockName);
                                    updateStatement.executeUpdate();
                                }
                                connection.commit();
                                return true;
                            }

                            // Step 5: if the lock is expired, this node can reclaim it and replace the owner.
                            if (expiresAt != null && expiresAt.before(now)) {
                                try (PreparedStatement updateStatement = connection.prepareStatement(
                                         "UPDATE distributed_locks SET owner = ?, acquired_at = CURRENT_TIMESTAMP, expires_at = ? WHERE lock_name = ?")) {
                                    updateStatement.setString(1, ownerId);
                                    updateStatement.setTimestamp(2, new Timestamp(System.currentTimeMillis() + leaseMillis));
                                    updateStatement.setString(3, lockName);
                                    updateStatement.executeUpdate();
                                }
                                connection.commit();
                                return true;
                            }

                            // Step 6: another owner is active and the lease is still valid, so deny this request.
                            connection.rollback();
                            return false;
                        }
                    }
                }

                // Step 7: no row exists yet, so create the lock record as the first owner.
                try (PreparedStatement insertStatement = connection.prepareStatement(
                         "INSERT INTO distributed_locks (lock_name, owner, acquired_at, expires_at) VALUES (?, ?, CURRENT_TIMESTAMP, ?)")) {
                    insertStatement.setString(1, lockName);
                    insertStatement.setString(2, ownerId);
                    insertStatement.setTimestamp(3, new Timestamp(System.currentTimeMillis() + leaseMillis));
                    insertStatement.executeUpdate();
                    connection.commit();
                    return true;
                } catch (SQLException duplicateKeyException) {
                    // Step 8: another process won the race and inserted the same lock row at the same time.
                    connection.rollback();
                    return false;
                }
            } catch (SQLException exc) {
                // Step 9: any unexpected failure means the transaction was not committed; roll back cleanly.
                connection.rollback();
                throw exc;
            } finally {
                // Step 10: reset autocommit so the connection returns to the default behavior for the pool.
                connection.setAutoCommit(true);
            }
        }
    }

    public void releaseDistributedLock(String lockName, String ownerId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "DELETE FROM distributed_locks WHERE lock_name = ? AND owner = ?")) {
            statement.setString(1, lockName);
            statement.setString(2, ownerId);
            statement.executeUpdate();
        }
    }

    // Run: mvn compile && java -cp target/classes DatabaseWriteContention
    public static void main(String[] args) throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:contention_demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        dataSource.setPassword("");

        DatabaseWriteContention contention = new DatabaseWriteContention(dataSource);
        contention.createSchema();
        contention.saveAccount(1L, 100, 0);

        System.out.println("Conditional write: " + contention.conditionalWrite(1L, 25, 100));
        contention.saveAccount(1L, 100, 0);

        System.out.println("Pessimistic lock write: " + contention.pessimisticLockWrite(1L, 25));
        contention.saveAccount(1L, 100, 0);

        int version = contention.getVersion(1L);
        System.out.println("Optimistic lock write: " + contention.optimisticLockWrite(1L, 25, version));

        System.out.println("Read committed balance: " + contention.readCommittedBalance(1L));
        System.out.println("Repeatable read balance: " + contention.repeatableReadBalance(1L));
        System.out.println("Serializable balance: " + contention.serializableBalance(1L));

        boolean firstLock = contention.acquireDistributedLock("invoice-42", "node-a", 60_000L);
        System.out.println("First distributed lock acquired: " + firstLock);

        boolean secondLock = contention.acquireDistributedLock("invoice-42", "node-b", 60_000L);
        System.out.println("Second distributed lock acquired: " + secondLock);

        contention.releaseDistributedLock("invoice-42", "node-a");
        System.out.println("Lock released by node-a");
    }
}
