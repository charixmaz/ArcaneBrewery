package me.arcanebrewery.arcaneeconomy.storage;

import me.arcanebrewery.arcaneeconomy.api.*;
import me.arcanebrewery.arcaneeconomy.domain.MoneyPolicy;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class SqliteLedger implements ArcaneEconomyService, AutoCloseable {
    private static final int SCHEMA_VERSION = 1;
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
    public EconomyResult deposit(UUID playerId, BigDecimal amount, UUID transactionId) {
        return mutate(playerId, amount, transactionId, "DEPOSIT", false);
    }

    @Override
    public EconomyResult withdraw(UUID playerId, BigDecimal amount, UUID transactionId) {
        return mutate(playerId, amount, transactionId, "WITHDRAW", true);
    }

    @Override
    public EconomyResult setBalance(UUID playerId, BigDecimal balance, UUID transactionId) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(transactionId);
        final long requested;
        try {
            requested = money.balanceToMinor(balance);
        } catch (IllegalArgumentException invalid) {
            return new EconomyResult(transactionId, EconomyResult.Status.INVALID, balance(playerId), invalid.getMessage());
        }
        if (!money.withinBounds(requested)) {
            return new EconomyResult(transactionId, EconomyResult.Status.OUT_OF_RANGE, balance(playerId), "balance outside configured bounds");
        }

        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Long duplicate = existingTransactionBalance(connection, transactionId);
                if (duplicate != null) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.DUPLICATE, money.fromMinor(duplicate), "transaction already applied");
                }
                loadOrCreate(connection, playerId);
                writeBalance(connection, playerId, requested);
                record(connection, transactionId, "SET", playerId, null, requested, requested);
                connection.commit();
                return new EconomyResult(transactionId, EconomyResult.Status.APPLIED, money.fromMinor(requested), "");
            } catch (RuntimeException | SQLException failure) {
                rollback(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    @Override
    public EconomyResult transfer(UUID fromPlayerId, UUID toPlayerId, BigDecimal amount, UUID transactionId) {
        Objects.requireNonNull(fromPlayerId);
        Objects.requireNonNull(toPlayerId);
        Objects.requireNonNull(transactionId);
        if (fromPlayerId.equals(toPlayerId)) {
            return new EconomyResult(transactionId, EconomyResult.Status.INVALID, balance(fromPlayerId), "cannot transfer to the same account");
        }

        final long minor;
        try {
            minor = money.amountToMinor(amount);
        } catch (IllegalArgumentException invalid) {
            return new EconomyResult(transactionId, EconomyResult.Status.INVALID, balance(fromPlayerId), invalid.getMessage());
        }

        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Long duplicate = existingTransactionBalance(connection, transactionId);
                if (duplicate != null) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.DUPLICATE, money.fromMinor(duplicate), "transaction already applied");
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
                    return new EconomyResult(transactionId, EconomyResult.Status.OUT_OF_RANGE, money.fromMinor(from), "balance overflow");
                }
                if (!money.withinBounds(nextFrom)) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.INSUFFICIENT_FUNDS, money.fromMinor(from), "insufficient funds");
                }
                if (!money.withinBounds(nextTo)) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.OUT_OF_RANGE, money.fromMinor(from), "target balance outside configured bounds");
                }

                writeBalance(connection, fromPlayerId, nextFrom);
                writeBalance(connection, toPlayerId, nextTo);
                record(connection, transactionId, "TRANSFER", fromPlayerId, toPlayerId, minor, nextFrom);
                connection.commit();
                return new EconomyResult(transactionId, EconomyResult.Status.APPLIED, money.fromMinor(nextFrom), "");
            } catch (RuntimeException | SQLException failure) {
                rollback(connection);
                throw failure;
            }
        } catch (SQLException failure) {
            throw storageFailure(failure);
        }
    }

    private EconomyResult mutate(UUID playerId, BigDecimal amount, UUID transactionId, String type, boolean subtract) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(transactionId);
        final long minor;
        try {
            minor = money.amountToMinor(amount);
        } catch (IllegalArgumentException invalid) {
            return new EconomyResult(transactionId, EconomyResult.Status.INVALID, balance(playerId), invalid.getMessage());
        }

        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Long duplicate = existingTransactionBalance(connection, transactionId);
                if (duplicate != null) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.DUPLICATE, money.fromMinor(duplicate), "transaction already applied");
                }
                long current = loadOrCreate(connection, playerId);
                long next;
                try {
                    next = subtract ? Math.subtractExact(current, minor) : Math.addExact(current, minor);
                } catch (ArithmeticException overflow) {
                    connection.rollback();
                    return new EconomyResult(transactionId, EconomyResult.Status.OUT_OF_RANGE, money.fromMinor(current), "balance overflow");
                }
                if (!money.withinBounds(next)) {
                    connection.rollback();
                    EconomyResult.Status status = subtract
                            ? EconomyResult.Status.INSUFFICIENT_FUNDS
                            : EconomyResult.Status.OUT_OF_RANGE;
                    return new EconomyResult(transactionId, status, money.fromMinor(current), "balance outside configured bounds");
                }
                writeBalance(connection, playerId, next);
                record(connection, transactionId, type, playerId, null, minor, next);
                connection.commit();
                return new EconomyResult(transactionId, EconomyResult.Status.APPLIED, money.fromMinor(next), "");
            } catch (RuntimeException | SQLException failure) {
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
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS economy_meta (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);
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
            try (PreparedStatement upsert = connection.prepareStatement("""
                    INSERT INTO economy_meta(key, value) VALUES('schema_version', ?)
                    ON CONFLICT(key) DO UPDATE SET value=excluded.value
                    """)) {
                upsert.setString(1, Integer.toString(SCHEMA_VERSION));
                upsert.executeUpdate();
            }
        } catch (SQLException failure) {
            throw storageFailure(failure);
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

    private Long existingTransactionBalance(Connection connection, UUID transactionId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT resulting_balance_minor FROM economy_transactions WHERE transaction_id=?")) {
            select.setString(1, transactionId.toString());
            try (ResultSet rows = select.executeQuery()) {
                return rows.next() ? rows.getLong(1) : null;
            }
        }
    }

    private void record(Connection connection, UUID transactionId, String type,
            UUID source, UUID target, long amount, long resultingBalance) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO economy_transactions(
                    transaction_id, type, source_uuid, target_uuid,
                    amount_minor, resulting_balance_minor, created_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, transactionId.toString());
            insert.setString(2, type);
            insert.setString(3, source.toString());
            if (target == null) insert.setNull(4, Types.VARCHAR);
            else insert.setString(4, target.toString());
            insert.setLong(5, amount);
            insert.setLong(6, resultingBalance);
            insert.setLong(7, Instant.now().toEpochMilli());
            insert.executeUpdate();
        }
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
}
