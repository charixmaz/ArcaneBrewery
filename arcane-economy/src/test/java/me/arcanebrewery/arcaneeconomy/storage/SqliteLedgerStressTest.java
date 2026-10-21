package me.arcanebrewery.arcaneeconomy.storage;

import me.arcanebrewery.arcaneeconomy.api.EconomyResult;
import me.arcanebrewery.arcaneeconomy.domain.MoneyPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class SqliteLedgerStressTest {
    @TempDir Path temp;

    @Test
    void thousandConcurrentDepositsDoNotLoseUpdates() throws Exception {
        var money = new MoneyPolicy(2, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000000.00"));
        UUID player = UUID.randomUUID();

        try (var ledger = new SqliteLedger(temp.resolve("deposits.db"), money, 5000);
             var pool = Executors.newFixedThreadPool(16)) {
            int operations = 1_000;
            var futures = new ArrayList<Future<EconomyResult>>(operations);
            for (int i = 0; i < operations; i++) {
                UUID tx = UUID.nameUUIDFromBytes(("deposit-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                futures.add(pool.submit(() -> ledger.deposit(player, new BigDecimal("1.00"), tx)));
            }
            for (var future : futures) {
                assertEquals(EconomyResult.Status.APPLIED, future.get(30, TimeUnit.SECONDS).status());
            }
            assertEquals(new BigDecimal("1000.00"), ledger.balance(player));
        }
    }

    @Test
    void hundredConcurrentReplaysOfSameTransactionApplyExactlyOnce() throws Exception {
        var money = new MoneyPolicy(2, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000000.00"));
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        try (var ledger = new SqliteLedger(temp.resolve("replay.db"), money, 5000);
             var pool = Executors.newFixedThreadPool(16)) {
            var futures = new ArrayList<Future<EconomyResult>>();
            for (int i = 0; i < 100; i++) {
                futures.add(pool.submit(() -> ledger.deposit(player, new BigDecimal("25.00"), tx)));
            }

            int applied = 0;
            int duplicate = 0;
            for (var future : futures) {
                var status = future.get(30, TimeUnit.SECONDS).status();
                if (status == EconomyResult.Status.APPLIED) applied++;
                else if (status == EconomyResult.Status.DUPLICATE) duplicate++;
                else fail("unexpected replay result: " + status);
            }

            assertEquals(1, applied);
            assertEquals(99, duplicate);
            assertEquals(new BigDecimal("25.00"), ledger.balance(player));
        }
    }

    @Test
    void transferRingConservesTotalBalanceUnderContention() throws Exception {
        var money = new MoneyPolicy(2, new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("1000000.00"));
        List<UUID> players = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> UUID.nameUUIDFromBytes(("player-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .toList();

        try (var ledger = new SqliteLedger(temp.resolve("ring.db"), money, 5000);
             var pool = Executors.newFixedThreadPool(16)) {
            players.forEach(ledger::balance);
            var futures = new ArrayList<Future<EconomyResult>>();
            for (int i = 0; i < 2_000; i++) {
                int index = i % players.size();
                UUID from = players.get(index);
                UUID to = players.get((index + 1) % players.size());
                UUID tx = UUID.nameUUIDFromBytes(("ring-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                futures.add(pool.submit(() -> ledger.transfer(from, to, new BigDecimal("1.00"), tx)));
            }
            for (var future : futures) {
                assertEquals(EconomyResult.Status.APPLIED, future.get(60, TimeUnit.SECONDS).status());
            }

            BigDecimal total = players.stream()
                    .map(ledger::balance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("20000.00"), total);
        }
    }
}
