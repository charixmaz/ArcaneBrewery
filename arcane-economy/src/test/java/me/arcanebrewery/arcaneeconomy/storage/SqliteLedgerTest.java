package me.arcanebrewery.arcaneeconomy.storage;

import me.arcanebrewery.arcaneeconomy.api.*;
import me.arcanebrewery.arcaneeconomy.domain.MoneyPolicy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SqliteLedgerTest {
    @TempDir Path temp;
    private MoneyPolicy money;
    private Path file;

    @BeforeEach
    void setup() {
        money = new MoneyPolicy(
                2,
                new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                new BigDecimal("1000.00")
        );
        file = temp.resolve("economy.db");
    }

    @Test
    void newAccountReceivesStartingBalanceAndPersistsAcrossReopen() {
        UUID player = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            assertEquals(new BigDecimal("10.00"), ledger.balance(player));
            assertTrue(ledger.deposit(player, new BigDecimal("5.25"), UUID.randomUUID()).applied());
        }

        try (var reopened = new SqliteLedger(file, money, 1000)) {
            assertEquals(new BigDecimal("15.25"), reopened.balance(player));
        }
    }

    @Test
    void duplicateDepositTransactionCannotPayTwice() {
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            var first = ledger.deposit(player, new BigDecimal("4.00"), tx);
            var duplicate = ledger.deposit(player, new BigDecimal("4.00"), tx);

            assertEquals(EconomyResult.Status.APPLIED, first.status());
            assertEquals(EconomyResult.Status.DUPLICATE, duplicate.status());
            assertEquals(new BigDecimal("14.00"), ledger.balance(player));
        }
    }

    @Test
    void sameTransactionIdCannotBeReusedForDifferentRequest() {
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            assertEquals(EconomyResult.Status.APPLIED,
                    ledger.deposit(player, new BigDecimal("4.00"), tx).status());

            var conflicting = ledger.deposit(player, new BigDecimal("9.00"), tx);

            assertEquals(EconomyResult.Status.INVALID, conflicting.status());
            assertTrue(conflicting.message().contains("different request"));
            assertEquals(new BigDecimal("14.00"), ledger.balance(player));
        }
    }

    @Test
    void concurrentRetriesApplyOnlyOnce() throws Exception {
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 5000);
             var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<EconomyResult> task = () -> {
                start.await(5, TimeUnit.SECONDS);
                return ledger.deposit(player, new BigDecimal("7.00"), tx);
            };

            Future<EconomyResult> first = executor.submit(task);
            Future<EconomyResult> second = executor.submit(task);
            start.countDown();

            var statuses = Set.of(
                    first.get(10, TimeUnit.SECONDS).status(),
                    second.get(10, TimeUnit.SECONDS).status()
            );
            assertEquals(Set.of(EconomyResult.Status.APPLIED, EconomyResult.Status.DUPLICATE), statuses);
            assertEquals(new BigDecimal("17.00"), ledger.balance(player));
        }
    }

    @Test
    void withdrawalCannotCrossMinimumBalance() {
        UUID player = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            var result = ledger.withdraw(player, new BigDecimal("11.00"), UUID.randomUUID());

            assertEquals(EconomyResult.Status.INSUFFICIENT_FUNDS, result.status());
            assertEquals(new BigDecimal("10.00"), ledger.balance(player));
        }
    }

    @Test
    void depositCannotCrossMaximumBalance() {
        UUID player = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            ledger.setBalance(player, new BigDecimal("999.00"), UUID.randomUUID());
            var result = ledger.deposit(player, new BigDecimal("2.00"), UUID.randomUUID());

            assertEquals(EconomyResult.Status.OUT_OF_RANGE, result.status());
            assertEquals(new BigDecimal("999.00"), ledger.balance(player));
        }
    }

    @Test
    void transferIsAtomicAndIdempotent() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            ledger.setBalance(from, new BigDecimal("100.00"), UUID.randomUUID());
            ledger.setBalance(to, new BigDecimal("20.00"), UUID.randomUUID());

            var first = ledger.transfer(from, to, new BigDecimal("35.50"), tx);
            var duplicate = ledger.transfer(from, to, new BigDecimal("35.50"), tx);

            assertEquals(EconomyResult.Status.APPLIED, first.status());
            assertEquals(EconomyResult.Status.DUPLICATE, duplicate.status());
            assertEquals(new BigDecimal("64.50"), ledger.balance(from));
            assertEquals(new BigDecimal("55.50"), ledger.balance(to));
        }
    }

    @Test
    void failedTransferDoesNotCreditTarget() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            ledger.setBalance(from, new BigDecimal("5.00"), UUID.randomUUID());
            ledger.setBalance(to, new BigDecimal("20.00"), UUID.randomUUID());

            var result = ledger.transfer(from, to, new BigDecimal("6.00"), UUID.randomUUID());

            assertEquals(EconomyResult.Status.INSUFFICIENT_FUNDS, result.status());
            assertEquals(new BigDecimal("5.00"), ledger.balance(from));
            assertEquals(new BigDecimal("20.00"), ledger.balance(to));
        }
    }

    @Test
    void transferHistoryIncludesBothBalancesAndBothPlayers() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            ledger.setBalance(from, new BigDecimal("100.00"), UUID.randomUUID());
            ledger.setBalance(to, new BigDecimal("20.00"), UUID.randomUUID());
            ledger.transfer(from, to, new BigDecimal("35.50"), tx);

            var record = ledger.transaction(tx).orElseThrow();
            assertEquals(EconomyTransactionRecord.Type.TRANSFER, record.type());
            assertEquals(from, record.sourcePlayerId());
            assertEquals(Optional.of(to), record.targetPlayerId());
            assertEquals(new BigDecimal("35.50"), record.amount());
            assertEquals(new BigDecimal("64.50"), record.sourceBalanceAfter());
            assertEquals(Optional.of(new BigDecimal("55.50")), record.targetBalanceAfter());

            assertTrue(ledger.history(from, 10).stream().anyMatch(entry -> entry.transactionId().equals(tx)));
            assertTrue(ledger.history(to, 10).stream().anyMatch(entry -> entry.transactionId().equals(tx)));
        }
    }

    @Test
    void historyLimitIsBounded() {
        try (var ledger = new SqliteLedger(file, money, 1000)) {
            assertThrows(IllegalArgumentException.class, () -> ledger.history(UUID.randomUUID(), 0));
            assertThrows(IllegalArgumentException.class, () -> ledger.history(UUID.randomUUID(), 101));
        }
    }

    @Test
    void setBalanceIsIdempotentAndBounded() {
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            assertEquals(EconomyResult.Status.APPLIED,
                    ledger.setBalance(player, new BigDecimal("200.00"), tx).status());
            assertEquals(EconomyResult.Status.INVALID,
                    ledger.setBalance(player, new BigDecimal("500.00"), tx).status());
            assertEquals(new BigDecimal("200.00"), ledger.balance(player));

            assertEquals(EconomyResult.Status.OUT_OF_RANGE,
                    ledger.setBalance(player, new BigDecimal("1001.00"), UUID.randomUUID()).status());
            assertEquals(new BigDecimal("200.00"), ledger.balance(player));
        }
    }

    @Test
    void invalidScaleFailsWithoutMutation() {
        UUID player = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            var result = ledger.deposit(player, new BigDecimal("1.001"), UUID.randomUUID());

            assertEquals(EconomyResult.Status.INVALID, result.status());
            assertEquals(new BigDecimal("10.00"), ledger.balance(player));
        }
    }

    @Test
    void migratesV1DatabaseWithoutLosingAccountsOrTransactions() throws Exception {
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();
        createV1Database(player, tx);

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            assertEquals(new BigDecimal("12.34"), ledger.balance(player));

            var legacy = ledger.transaction(tx).orElseThrow();
            assertEquals(EconomyTransactionRecord.Type.DEPOSIT, legacy.type());
            assertEquals(new BigDecimal("2.34"), legacy.amount());
            assertTrue(legacy.targetBalanceAfter().isEmpty());

            var duplicate = ledger.deposit(player, new BigDecimal("99.00"), tx);
            assertEquals(EconomyResult.Status.DUPLICATE, duplicate.status());

            UUID fresh = UUID.randomUUID();
            assertEquals(EconomyResult.Status.APPLIED,
                    ledger.deposit(player, new BigDecimal("1.00"), fresh).status());
            assertEquals(new BigDecimal("13.34"), ledger.balance(player));
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             PreparedStatement select = connection.prepareStatement(
                     "SELECT value FROM economy_meta WHERE key='schema_version'")) {
            try (ResultSet rows = select.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("2", rows.getString(1));
            }
        }
    }

    @Test
    void refusesDatabaseFromUnknownFutureSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE economy_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO economy_meta(key, value) VALUES('schema_version', '99')");
        }

        var failure = assertThrows(IllegalStateException.class,
                () -> new SqliteLedger(file, money, 1000));
        assertTrue(failure.getMessage().contains("newer than supported"));
    }

    private void createV1Database(UUID player, UUID tx) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE economy_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO economy_meta(key, value) VALUES('schema_version', '1')");
            statement.execute("""
                    CREATE TABLE economy_accounts (
                        player_uuid TEXT PRIMARY KEY,
                        balance_minor INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE economy_transactions (
                        transaction_id TEXT PRIMARY KEY,
                        type TEXT NOT NULL,
                        source_uuid TEXT NOT NULL,
                        target_uuid TEXT,
                        amount_minor INTEGER NOT NULL,
                        resulting_balance_minor INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            try (PreparedStatement account = connection.prepareStatement(
                    "INSERT INTO economy_accounts(player_uuid, balance_minor, updated_at) VALUES(?, ?, ?)")) {
                account.setString(1, player.toString());
                account.setLong(2, 1234);
                account.setLong(3, 1);
                account.executeUpdate();
            }
            try (PreparedStatement transaction = connection.prepareStatement("""
                    INSERT INTO economy_transactions(
                        transaction_id, type, source_uuid, target_uuid,
                        amount_minor, resulting_balance_minor, created_at
                    ) VALUES(?, 'DEPOSIT', ?, NULL, 234, 1234, 1)
                    """)) {
                transaction.setString(1, tx.toString());
                transaction.setString(2, player.toString());
                transaction.executeUpdate();
            }
        }
    }
}
