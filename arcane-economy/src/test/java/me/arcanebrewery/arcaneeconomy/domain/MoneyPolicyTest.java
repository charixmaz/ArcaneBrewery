package me.arcanebrewery.arcaneeconomy.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyPolicyTest {
    private final MoneyPolicy policy = new MoneyPolicy(
            2,
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("1000.00")
    );

    @Test
    void convertsMoneyExactlyToMinorUnits() {
        assertEquals(12345, policy.amountToMinor(new BigDecimal("123.45")));
        assertEquals(new BigDecimal("123.45"), policy.fromMinor(12345));
    }

    @Test
    void rejectsFractionBeyondConfiguredScale() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.amountToMinor(new BigDecimal("1.001")));
    }

    @Test
    void rejectsZeroAndNegativeTransactionAmounts() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.amountToMinor(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> policy.amountToMinor(new BigDecimal("-1.00")));
    }

    @Test
    void balanceBoundsAreExplicit() {
        assertTrue(policy.withinBounds(0));
        assertTrue(policy.withinBounds(100_000));
        assertFalse(policy.withinBounds(-1));
        assertFalse(policy.withinBounds(100_001));
    }
}
