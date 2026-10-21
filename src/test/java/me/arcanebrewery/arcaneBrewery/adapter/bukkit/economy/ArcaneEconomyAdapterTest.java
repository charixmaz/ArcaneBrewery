package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import me.arcanebrewery.arcaneeconomy.api.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ArcaneEconomyAdapterTest {
    static final UUID PLAYER = UUID.randomUUID();
    static final class Ledger implements ArcaneEconomyService {
        BigDecimal balance = new BigDecimal("100.00");
        BigDecimal received;
        int calls;
        int version = API_VERSION;
        EconomyResult.Status forced;
        boolean throwAfterCommit;
        final Map<UUID, EconomyTransactionRecord> records = new HashMap<>();
        public int apiVersion() { return version; }
        public BigDecimal balance(UUID p) { return balance; }
        public EconomyResult deposit(UUID p, BigDecimal amount, UUID id) { return write(p, amount, id, EconomyTransactionRecord.Type.DEPOSIT); }
        public EconomyResult withdraw(UUID p, BigDecimal amount, UUID id) { return write(p, amount, id, EconomyTransactionRecord.Type.WITHDRAW); }
        private EconomyResult write(UUID p, BigDecimal amount, UUID id, EconomyTransactionRecord.Type type) {
            received = amount; calls++;
            if (forced != null) return new EconomyResult(id, forced, balance, "forced");
            var previous = records.get(id);
            if (previous != null) return new EconomyResult(id,
                    previous.type() == type && previous.sourcePlayerId().equals(p) && previous.amount().compareTo(amount) == 0
                            ? EconomyResult.Status.DUPLICATE : EconomyResult.Status.INVALID, balance, "replay");
            if (type == EconomyTransactionRecord.Type.WITHDRAW && balance.compareTo(amount) < 0)
                return new EconomyResult(id, EconomyResult.Status.INSUFFICIENT_FUNDS, balance, "insufficient");
            balance = type == EconomyTransactionRecord.Type.DEPOSIT ? balance.add(amount) : balance.subtract(amount);
            records.put(id, new EconomyTransactionRecord(id,type,p,Optional.empty(),amount,balance,Optional.empty(),0));
            if (throwAfterCommit) throw new IllegalStateException("response lost after commit");
            return new EconomyResult(id,EconomyResult.Status.APPLIED,balance,"");
        }
        public EconomyResult setBalance(UUID p,BigDecimal a,UUID id) { throw new UnsupportedOperationException(); }
        public EconomyResult transfer(UUID p,UUID q,BigDecimal a,UUID id) { throw new UnsupportedOperationException(); }
        public Optional<EconomyTransactionRecord> transaction(UUID id) { return Optional.ofNullable(records.get(id)); }
        public List<EconomyTransactionRecord> history(UUID p,int n) { return List.copyOf(records.values()); }
    }
    @Test void depositsAndWithdrawalsUseExactAmountAndOriginalTransactionId() {
        var ledger = new Ledger(); var adapter = new ArcaneEconomyAdapter(() -> ledger, () -> true);
        UUID deposit = UUID.randomUUID(), withdraw = UUID.randomUUID();
        var amount = new BigDecimal("90071992547409.123456");
        assertTrue(adapter.deposit(PLAYER,amount,deposit));
        assertSame(amount,ledger.received);
        assertEquals(amount,ledger.records.get(deposit).amount());
        assertTrue(adapter.withdraw(PLAYER,new BigDecimal("0.123456"),withdraw));
        assertEquals(EconomyTransactionRecord.Type.WITHDRAW, ledger.records.get(withdraw).type());
        assertEquals(new BigDecimal("90071992547509.000000"), ledger.balance);
    }
    @Test void affordabilityIsAdvisoryAndInsufficientFundsRejectWithdrawal() {
        var ledger = new Ledger(); var adapter = new ArcaneEconomyAdapter(() -> ledger, () -> true);
        assertTrue(adapter.canAfford(PLAYER,new BigDecimal("100.00")));
        assertFalse(adapter.canAfford(PLAYER,new BigDecimal("100.01")));
        assertFalse(adapter.withdraw(PLAYER,new BigDecimal("100.01"),UUID.randomUUID()));
        assertEquals(new BigDecimal("100.00"),ledger.balance);
    }
    @Test void exactReplaySucceedsWithoutSecondMutationButConflictFails() {
        var ledger = new Ledger(); var adapter = new ArcaneEconomyAdapter(() -> ledger, () -> true);
        UUID id = UUID.randomUUID();
        assertTrue(adapter.deposit(PLAYER,new BigDecimal("1.25"),id));
        assertTrue(adapter.deposit(PLAYER,new BigDecimal("1.250"),id));
        assertFalse(adapter.deposit(PLAYER,new BigDecimal("2.25"),id));
        assertFalse(adapter.withdraw(PLAYER,new BigDecimal("1.25"),id));
        assertFalse(adapter.deposit(UUID.randomUUID(),new BigDecimal("1.25"),id));
        assertEquals(new BigDecimal("101.25"),ledger.balance);
    }
    @Test void allExplicitFailureStatusesReject() {
        var ledger = new Ledger(); var adapter = new ArcaneEconomyAdapter(() -> ledger, () -> true);
        for (var status : List.of(EconomyResult.Status.INVALID, EconomyResult.Status.OUT_OF_RANGE, EconomyResult.Status.INSUFFICIENT_FUNDS)) {
            ledger.forced = status;
            assertFalse(adapter.deposit(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
            assertFalse(adapter.withdraw(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        }
    }
    @Test void missingReplacedAndIncompatibleProvidersFailClosed() {
        var provider = new AtomicReference<ArcaneEconomyService>();
        var adapter = new ArcaneEconomyAdapter(provider::get, () -> true);
        assertFalse(adapter.deposit(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        assertFalse(adapter.canAfford(PLAYER,BigDecimal.ONE));
        var first = new Ledger(); provider.set(first);
        assertTrue(adapter.deposit(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        provider.set(null);
        assertFalse(adapter.withdraw(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        var replacement = new Ledger(); replacement.version = 99; provider.set(replacement);
        assertFalse(adapter.deposit(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        assertEquals(0,replacement.calls);
        replacement.version = 1;
        assertTrue(adapter.deposit(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        assertEquals(1,first.calls);
    }
    @Test void serverThreadAndInvalidRequestsNeverCallTheLedger() {
        var ledger = new Ledger(); var main = new ArcaneEconomyAdapter(() -> ledger, () -> false);
        assertFalse(main.deposit(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        assertFalse(main.withdraw(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        assertFalse(main.canAfford(PLAYER,BigDecimal.ONE));
        var adapter = new ArcaneEconomyAdapter(() -> ledger, () -> true);
        assertFalse(adapter.deposit(PLAYER,BigDecimal.ZERO,UUID.randomUUID()));
        assertFalse(adapter.withdraw(PLAYER,BigDecimal.ONE,null));
        assertFalse(adapter.canAfford(PLAYER,new BigDecimal("-1")));
        assertEquals(0,ledger.calls);
    }
    @Test void lostWriteResponseAndUnverifiableReplayRequireReconciliation() {
        var ledger = new Ledger(); ledger.throwAfterCommit = true;
        var adapter = new ArcaneEconomyAdapter(() -> ledger, () -> true);
        UUID id = UUID.randomUUID();
        assertThrows(EconomyOperationUncertainException.class, () -> adapter.deposit(PLAYER,BigDecimal.ONE,id));
        assertEquals(new BigDecimal("101.00"),ledger.balance);
        assertTrue(adapter.deposit(PLAYER,BigDecimal.ONE,id));
        ledger.forced = EconomyResult.Status.DUPLICATE;
        assertThrows(EconomyOperationUncertainException.class, () -> adapter.deposit(PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        assertThrows(EconomyOperationUncertainException.class, () -> adapter.deposit(PLAYER,BigDecimal.TEN,id));
    }
}
