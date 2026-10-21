package me.arcanebrewery.arcaneeconomy.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class MoneyPolicy {
    private final int scale;
    private final long startingMinor;
    private final long minimumMinor;
    private final long maximumMinor;

    public MoneyPolicy(int scale, BigDecimal starting, BigDecimal minimum, BigDecimal maximum) {
        if (scale < 0 || scale > 6) throw new IllegalArgumentException("currency scale must be within 0..6");
        this.scale = scale;
        this.minimumMinor = toMinorExact(Objects.requireNonNull(minimum));
        this.maximumMinor = toMinorExact(Objects.requireNonNull(maximum));
        this.startingMinor = toMinorExact(Objects.requireNonNull(starting));
        if (minimumMinor > maximumMinor) throw new IllegalArgumentException("minimum balance exceeds maximum");
        if (startingMinor < minimumMinor || startingMinor > maximumMinor)
            throw new IllegalArgumentException("starting balance is outside configured bounds");
    }

    public int scale() { return scale; }
    public long startingMinor() { return startingMinor; }
    public long minimumMinor() { return minimumMinor; }
    public long maximumMinor() { return maximumMinor; }

    public long amountToMinor(BigDecimal amount) {
        Objects.requireNonNull(amount);
        long minor = toMinorExact(amount);
        if (minor <= 0) throw new IllegalArgumentException("amount must be positive");
        return minor;
    }

    public long balanceToMinor(BigDecimal balance) {
        return toMinorExact(Objects.requireNonNull(balance));
    }

    public BigDecimal fromMinor(long minor) {
        return BigDecimal.valueOf(minor, scale);
    }

    public boolean withinBounds(long minor) {
        return minor >= minimumMinor && minor <= maximumMinor;
    }

    private long toMinorExact(BigDecimal value) {
        if (value.scale() > scale) {
            try {
                value = value.setScale(scale, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException fractional) {
                throw new IllegalArgumentException("value exceeds configured currency scale", fractional);
            }
        }
        try {
            return value.movePointRight(scale).longValueExact();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("currency value is outside supported range", overflow);
        }
    }
}
