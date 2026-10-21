package me.arcanebrewery.arcaneeconomy.storage;

import me.arcanebrewery.arcaneeconomy.api.*;
import me.arcanebrewery.arcaneeconomy.domain.MoneyPolicy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.UUID;

public final class SqliteLedger implements ArcaneEconomyService, TransactionLookup, AutoCloseable {
    private static final int SCHEMA_VERSION = 2;
    private final String jdbcUrl;
    private final MoneyPolicy money;
    private final int busyTimeoutMillis;

    public SqliteLedger(Path file, MoneyPolicy money, int busyTimeoutMillis) {
        this.money = Objects.requireNonNull(money);
        this.busyTimeoutMillis = busyTimeoutMillis;
        Objects.requireNonNull(file);
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (Exception failure) {
            throw new IllegalStateException("unable to create economy data directory", failure);
        }
        this.jdbcUrl = "jdbc:sqlite:" + file.toAbsolutePath();
        migrate();
    }

    @Override
    public BigDecimal balance(UUID playerId) {
        Objects.requireNonNull(playerId);
        try (Connection connection = open()) {
            return money.fromMinor(loadOrCreate(connection, playerId));
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    @Override
    public synchronized EconomyResult deposit(UUID playerId, BigDecimal amount, UUID transactionId) {
        return mutate(playerId, amount, transactionId, "DEPOSIT", false);
    }

    @Override
    public synchronized EconomyResult withdraw(UUID playerId, BigDecimal amount, UUID transactionId) {
        return mutate(playerId, amount, transactionId, "WITHDRAW", true);
    }

    @Override
    public synchronized EconomyResult setBalance(UUID playerId, BigDecimal balance, UUID transactionId) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(transactionId);
        final long requested;
        try {
            requested = money.balanceToMinor(balance);
        } catch (IllegalArgumentException invalid) {
            return invalid(transactionId, playerId, invalid.getMessage());
        }
        if (!money.withinBounds(requested)) {
            return new EconomyResult(transactionId, EconomyResult.Status.OUT_OF_RANGE,
                    balance(playerId), "balance outside configured bounds");
        }

        String requestHash = requestHash("SET", playerId, null, requested);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Replay replay = replay(connection, transactionId, requestHash);
                if (replay.present()) {
                    connection.rollback();
                    return replayResult(transactionId, replay);
                }

                loadOrCreate(connection, playerId);
                writeBalance(connection, playerId, requested);
                record(connection, transactionId, "SET", playerId, null,
                        requested, requested, null, requestHash);
                connection.commit();
                return new EconomyResult(transactionId, EconomyResult.Status.APPLIED,
                        money.fromMinor(requested), "");
            } catch (SQLException failure) {
                rollback(connection);
                if (isTransactionIdConflict(failure)) {
                    return replayAfterConflict(transactionId, requestHash, playerId);
                }
                throw failure;
            } catch (RuntimeException failure) {
                rollback(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    @Override
    public synchronized EconomyResult transfer(UUID fromPlayerId, UUID toPlayerId, BigDecimal amount, UUID transactionId) {
        Objects.requireNonNull(fromPlayerId);
        Objects.requireNonNull(toPlayerId);
        Objects.requireNonNull(transactionId);
        if (fromPlayerId.equals(toPlayerId)) {
            return new EconomyResult(transactionId, EconomyResult.Status.INVALID,
                    balance(fromPlayerId), "cannot transfer to the same account");
        }

        final long minor;
        try {
            minor = money.amountToMinor(amount);
        } catch (IllegalArgumentException invalid) {
            return invalid(transactionId, fromPlayerId, invalid.getMessage());
        }

        String requestHash = requestHash("TRANSFER", fromPlayerId, toPlayerId, minor);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Replay replay = replay(connection, transactionId, requestHash);
                if (replay.present()) {
                    connection.rollback();
                    return replayResult(transactionId, replay);
                }

                long from = loadOrCreate(connection, fromPlayerId);
                long to = loadOrCreate(connection, toPlayerId);
                long nextFrom;
                long nextTo;
                try {
                    nextFrom = Math.subtractExact(from, minor);
                    nextTo = Math.addExact(to, minor);
                } catch (ArithmeticException overflow) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.OUT_OF_RANGE,
                            money.fromMinor(from), "balance overflow");
                }
                if (!money.withinBounds(nextFrom)) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.INSUFFICIENT_FUNDS,
                            money.fromMinor(from), "insufficient funds");
                }
                if (!money.withinBounds(nextTo)) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.OUT_OF_RANGE,
                            money.fromMinor(from), "target balance outside configured bounds");
                }

                writeBalance(connection, fromPlayerId, nextFrom);
                writeBalance(connection, toPlayerId, nextTo);
                record(connection, transactionId, "TRANSFER", fromPlayerId, toPlayerId,
                        minor, nextFrom, nextTo, requestHash);
                connection.commit();
                return new EconomyResult(transactionId, EconomyResult.Status.APPLIED,
                        money.fromMinor(nextFrom), "");
            } catch (SQLException failure) {
                rollback(connection);
                if (isTransactionIdConflict(failure)) {
                    return replayAfterConflict(transactionId, requestHash, fromPlayerId);
                }
                throw failure;
            } catch (RuntimeException failure) {
                rollback(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    @Override
    public Optional<EconomyTransactionRecord> transaction(UUID transactionId) {
        Objects.requireNonNull(transactionId);
        try (Connection connection = open();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT transaction_id, type, source_uuid, target_uuid,
                            amount_minor, resulting_balance_minor,
                            target_resulting_balance_minor, created_at
                     FROM economy_transactions WHERE transaction_id=?
                     """)) {
            select.setString(1, transactionId.toString());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? Optional.of(toRecord(rows)) : Optional.empty();
            }
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    @Override
    public List<EconomyTransactionRecord> history(UUID playerId, int limit) {
        Objects.requireNonNull(playerId);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("history limit must be within 1..100");
        try (Connection connection = open();
             PreparedStatement select = connection.prepareStatement("""
                     SELECT transaction_id, type, source_uuid, target_uuid,
                            amount_minor, resulting_balance_minor,
                            target_resulting_balance_minor, created_at
                     FROM economy_transactions
                     WHERE source_uuid=? OR target_uuid=?
                     ORDER BY created_at DESC, rowid DESC
                     LIMIT ?
                     """)) {
            select.setString(1, playerId.toString());
            select.setString(2, playerId.toString());
            select.setInt(3, limit);
            var result = new ArrayList<EconomyTransactionRecord>();
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) result.add(toRecord(rows));
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    private EconomyResult mutate(UUID playerId, BigDecimal amount, UUID transactionId,
            String type, boolean subtract) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(transactionId);
        final long minor;
        try {
            minor = money.amountToMinor(amount);
        } catch (IllegalArgumentException invalid) {
            return invalid(transactionId, playerId, invalid.getMessage());
        }

        String requestHash = requestHash(type, playerId, null, minor);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Replay replay = replay(connection, transactionId, requestHash);
                if (replay.present()) {
                    connection.rollback();
                    return replayResult(transactionId, replay);
                }

                long current = loadOrCreate(connection, playerId);
                long next;
                try {
                    next = subtract ? Math.subtractExact(current, minor) : Math.addExact(current, minor);
                } catch (ArithmeticException overflow) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.OUT_OF_RANGE,
                            money.fromMinor(current), "balance overflow");
                }
                if (!money.withinBounds(next)) {
                    connection.rollback();
                    EconomyResult.Status status = subtract
                            ? EconomyResult.Status.INSUFFICIENT_FUNDS
                            : EconomyResult.Status.OUT_OF_RANGE;
                    return new EconomyResult(transactionId, status,
                            money.fromMinor(current), "balance outside configured bounds");
                }

                writeBalance(connection, playerId, next);
                record(connection, transactionId, type, playerId, null,
                        minor, next, null, requestHash);
                connection.commit();
                return new EconomyResult(transactionId, EconomyResult.Status.APPLIED,
                        money.fromMinor(next), "");
            } catch (SQLException failure) {
                rollback(connection);
                if (isTransactionIdConflict(failure)) {
                    return replayAfterConflict(transactionId, requestHash, playerId);
                }
                throw failure;
            } catch (RuntimeException failure) {
                rollback(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=" + busyTimeoutMillis);
        }
        return connection;
    }

    private void migrate() {
        try (Connection connection = open()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS economy_meta (
                            key TEXT PRIMARY KEY,
                            value TEXT NOT NULL
                        )
                        """);
            }

            connection.setAutoCommit(false);
            try {
                int version = schemaVersion(connection);
                if (version > SCHEMA_VERSION) {
                    throw new IllegalStateException(
                            "database schema " + version + " is newer than supported " + SCHEMA_VERSION);
                }
                if (version < 1) {
                    migrateV1(connection);
                    setSchemaVersion(connection, 1);
                    version = 1;
                }
                if (version < 2) {
                    migrateV2(connection);
                    setSchemaVersion(connection, 2);
                }
                connection.commit();
            } catch (RuntimeException | SQLException failure) {
                rollback(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    private void migrateV1(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS economy_accounts (
                        player_uuid TEXT PRIMARY KEY,
                        balance_minor INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS economy_transactions (
                        transaction_id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        source_uuid TEXT NOT NULL,
                        target_uuid TEXT,
                        amount_minor INTEGER NOT NULL,
                        resulting_balance_minor INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    private void migrateV2(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE economy_transactions ADD COLUMN request_hash TEXT");
            statement.execute("ALTER TABLE economy_transactions ADD COLUMN target_resulting_balance_minor INTEGER");
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS economy_transactions_source_created_idx
                    ON economy_transactions(source_uuid, created_at DESC)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS economy_transactions_target_created_idx
                    ON economy_transactions(target_uuid, created_at DESC)
                    """);
        }
    }

    private int schemaVersion(Connection connection) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT value FROM economy_meta WHERE key='schema_version'")) {
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) return 0;
                try {
                    return Integer.parseInt(rows.getString(1));
                } catch (NumberFormatException malformed) {
                    throw new IllegalStateException("invalid economy schema version", malformed);
                }
            }
        }
    }

    private void setSchemaVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement("""
                INSERT INTO economy_meta(key, value) VALUES('schema_version', ?)
                ON CONFLICT(key) DO UPDATE SET value=excluded.value
                """)) {
            upsert.setString(1, Integer.toString(version));
            upsert.executeUpdate();
        }
    }

    private long loadOrCreate(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT balance_minor FROM economy_accounts WHERE player_uuid=?")) {
            select.setString(1, playerId.toString());
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) return rows.getLong(1);
            }
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO economy_accounts(player_uuid, balance_minor, updated_at)
                VALUES(?, ?, ?)
                ON CONFLICT(player_uuid) DO NOTHING
                """)) {
            insert.setString(1, playerId.toString());
            insert.setLong(2, money.startingMinor());
            insert.setLong(3, Instant.now().toEpochMilli());
            insert.executeUpdate();
        }
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT balance_minor FROM economy_accounts WHERE player_uuid=?")) {
            select.setString(1, playerId.toString());
            try (ResultSet rows = select.executeQuery()) {
                if (rows.next()) return rows.getLong(1);
            }
        }
        throw new SQLException("account creation failed");
    }

    private void writeBalance(Connection connection, UUID playerId, long balance) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE economy_accounts SET balance_minor=?, updated_at=? WHERE player_uuid=?
                """)) {
            update.setLong(1, balance);
            update.setLong(2, Instant.now().toEpochMilli());
            update.setString(3, playerId.toString());
            if (update.executeUpdate() != 1) throw new SQLException("account update failed");
        }
    }

    private Replay replay(Connection connection, UUID transactionId, String requestHash) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT resulting_balance_minor, request_hash
                FROM economy_transactions WHERE transaction_id=?
                """)) {
            select.setString(1, transactionId.toString());
            try (ResultSet rows = select.executeQuery()) {
                if (!rows.next()) return Replay.absent();
                long balance = rows.getLong(1);
                String storedHash = rows.getString(2);
                boolean same = storedHash == null || storedHash.equals(requestHash);
                return new Replay(true, same, balance);
            }
        }
    }

    private EconomyResult replayAfterConflict(UUID transactionId, String requestHash, UUID balanceOwner) {
        try (Connection connection = open()) {
            Replay replay = replay(connection, transactionId, requestHash);
            if (replay.present()) return replayResult(transactionId, replay);
            return new EconomyResult(transactionId, EconomyResult.Status.INVALID,
                    balance(balanceOwner), "transaction conflict could not be resolved");
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    private EconomyResult replayResult(UUID transactionId, Replay replay) {
        if (!replay.sameRequest()) {
            return new EconomyResult(transactionId, EconomyResult.Status.INVALID,
                    money.fromMinor(replay.resultingBalance()),
                    "transaction id already used for a different request");
        }
        return new EconomyResult(transactionId, EconomyResult.Status.DUPLICATE,
                money.fromMinor(replay.resultingBalance()), "transaction already applied");
    }

    private EconomyResult invalid(UUID transactionId, UUID playerId, String message) {
        return new EconomyResult(transactionId, EconomyResult.Status.INVALID,
                balance(playerId), message);
    }

    private void record(Connection connection, UUID transactionId, String type,
            UUID source, UUID target, long amount, long resultingBalance,
            Long targetResultingBalance, String requestHash) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO economy_transactions(
                    transaction_id, type, source_uuid, target_uuid,
                    amount_minor, resulting_balance_minor,
                    target_resulting_balance_minor, created_at, request_hash
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, transactionId.toString());
            insert.setString(2, type);
            insert.setString(3, source.toString());
            if (target == null) insert.setNull(4, Types.VARCHAR);
            else insert.setString(4, target.toString());
            insert.setLong(5, amount);
            insert.setLong(6, resultingBalance);
            if (targetResultingBalance == null) insert.setNull(7, Types.BIGINT);
            else insert.setLong(7, targetResultingBalance);
            insert.setLong(8, Instant.now().toEpochMilli());
            insert.setString(9, requestHash);
            insert.executeUpdate();
        }
    }

    private EconomyTransactionRecord toRecord(ResultSet rows) throws SQLException {
        String target = rows.getString("target_uuid");
        long targetBalance = rows.getLong("target_resulting_balance_minor");
        boolean targetBalanceNull = rows.wasNull();
        return new EconomyTransactionRecord(
                UUID.fromString(rows.getString("transaction_id")),
                EconomyTransactionRecord.Type.valueOf(rows.getString("type")),
                UUID.fromString(rows.getString("source_uuid")),
                target == null ? Optional.empty() : Optional.of(UUID.fromString(target)),
                money.fromMinor(rows.getLong("amount_minor")),
                money.fromMinor(rows.getLong("resulting_balance_minor")),
                targetBalanceNull ? Optional.empty() : Optional.of(money.fromMinor(targetBalance)),
                rows.getLong("created_at")
        );
    }

    private String requestHash(String type, UUID source, UUID target, long amountMinor) {
        String canonical = type + "|" + source + "|" + (target == null ? "-" : target) + "|" + amountMinor;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private boolean isTransactionIdConflict(SQLException failure) {
        return failure.getErrorCode() == 19
                && failure.getMessage() != null
                && failure.getMessage().contains("economy_transactions.transaction_id");
    }

    private void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) {}
    }

    private IllegalStateException storageFailure(SQLException failure) {
        return new IllegalStateException("ArcaneEconomy storage failure", failure);
    }

    @Override
    public void close() {
        // Connections are short-lived and transaction-scoped; nothing persistent to close.
    }

    private record Replay(boolean present, boolean sameRequest, long resultingBalance) {
        static Replay absent() { return new Replay(false, false, 0); }
    }
}
