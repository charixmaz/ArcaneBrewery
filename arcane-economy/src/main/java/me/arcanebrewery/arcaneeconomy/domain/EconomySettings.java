package me.arcanebrewery.arcaneeconomy.domain;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Objects;

public record EconomySettings(
        String singularName,
        String pluralName,
        String symbol,
        MoneyPolicy money,
        Path sqliteFile,
        int busyTimeoutMillis
) {
    public EconomySettings {
        singularName = required(singularName, "singular name");
        pluralName = required(pluralName, "plural name");
        symbol = Objects.requireNonNull(symbol);
        Objects.requireNonNull(money);
        Objects.requireNonNull(sqliteFile);
        if (busyTimeoutMillis < 0 || busyTimeoutMillis > 60_000)
            throw new IllegalArgumentException("busy timeout must be within 0..60000 ms");
    }

    public static MoneyPolicy policy(int scale, String starting, String minimum, String maximum) {
        return new MoneyPolicy(scale, new BigDecimal(starting), new BigDecimal(minimum), new BigDecimal(maximum));
    }

    private static String required(String value, String name) {
        value = Objects.requireNonNull(value).strip();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " cannot be blank");
        return value;
    }
}
