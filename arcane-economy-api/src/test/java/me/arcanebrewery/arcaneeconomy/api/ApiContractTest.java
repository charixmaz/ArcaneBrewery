package me.arcanebrewery.arcaneeconomy.api;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiContractTest {
    @Test void receiptsPreserveExactDecimalsAndSeparateReplayFromNewWrite() {
        UUID id = UUID.randomUUID();
        var exact = new BigDecimal("90071992547409.123456");
        var applied = new EconomyResult(id, EconomyResult.Status.APPLIED, exact, null);
        var duplicate = new EconomyResult(id, EconomyResult.Status.DUPLICATE, exact, "replay");
        assertEquals(exact, applied.balance());
        assertTrue(applied.applied());
        assertFalse(duplicate.applied());
        assertEquals("", applied.message());
    }
    @Test void publicApiHasOnlyJdkTypesAndNoImplementationOrPaperDependency() {
        for (var api : List.of(ArcaneEconomyService.class, TransactionLookup.class,
                EconomyResult.class, EconomyTransactionRecord.class)) {
            for (var method : api.getDeclaredMethods()) {
                for (var type : method.getParameterTypes()) assertPublicType(type);
                assertPublicType(method.getReturnType());
            }
        }
    }
    private void assertPublicType(Class<?> type) {
        assertTrue(type.isPrimitive() || type.getName().startsWith("java.")
                || type.getName().startsWith("me.arcanebrewery.arcaneeconomy.api."), type.getName());
    }
    @Test void invalidPublicRecordsAreRejected() {
        assertThrows(NullPointerException.class, () -> new EconomyResult(null, EconomyResult.Status.APPLIED, BigDecimal.ZERO, ""));
        assertThrows(IllegalArgumentException.class, () -> new EconomyTransactionRecord(UUID.randomUUID(),
                EconomyTransactionRecord.Type.DEPOSIT, UUID.randomUUID(), Optional.empty(),
                BigDecimal.ONE, BigDecimal.ONE, Optional.empty(), -1));
    }
}
