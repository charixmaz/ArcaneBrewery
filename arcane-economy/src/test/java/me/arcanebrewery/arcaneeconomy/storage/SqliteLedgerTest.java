package me.arcanebrewery.arcaneeconomy.storage;

import me.arcanebrewery.arcaneeconomy.api.EconomyResult;
import me.arcanebrewery.arcaneeconomy.domain.MoneyPolicy;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

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
    void setBalanceIsIdempotentAndBounded() {
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        try (var ledger = new SqliteLedger(file, money, 1000)) {
            assertEquals(EconomyResult.Status.APPLIED,
                    ledger.setBalance(player, new BigDecimal("200.00"), tx).status());
            assertEquals(EconomyResult.Status.DUPLICATE,
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
}
