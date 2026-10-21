package me.arcanebrewery.arcaneBrewery.storage.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public record SqlMigration(int version, List<String> statements) {
    public SqlMigration {
        statements = List.copyOf(statements);
        if (version <= 0 || statements.isEmpty() || statements.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("invalid migration");
    }

    public String checksum() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            statements.forEach(sql -> { digest.update(sql.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0); });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
