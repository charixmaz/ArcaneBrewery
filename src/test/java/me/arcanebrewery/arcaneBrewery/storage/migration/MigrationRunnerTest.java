package me.arcanebrewery.arcaneBrewery.storage.migration;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.storage.jdbc.JdbcTransactions;
import me.arcanebrewery.arcaneBrewery.storage.sqlite.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MigrationRunnerTest {
    @TempDir Path directory;
    private SqliteConnectionFactory connections;

    @BeforeEach void prepare() throws ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");
        connections = new SqliteConnectionFactory(directory.resolve("migrations.db"));
    }

    private MigrationRunner runner(List<SqlMigration> migrations) { return new MigrationRunner(connections, migrations); }

    private boolean tableExists(String table) {
        return JdbcTransactions.run(connections, false, connection -> {
            try (var statement = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
                statement.setString(1, table);
                try (var result = statement.executeQuery()) { return result.next(); }
            }
        });
    }

    @Test
    void sqliteV2MigrationCreatesDiscoveryAndMarketTables() {
        assertEquals(SqliteSchema.VERSION, runner(SqliteSchema.migrations()).migrate());
        assertTrue(tableExists("discoveries"));
        assertTrue(tableExists("recipe_grants"));
        assertTrue(tableExists("market_transactions"));
        assertTrue(tableExists("market_escrow"));
        assertEquals(SqliteSchema.VERSION, JdbcTransactions.run(connections, false, MigrationRunner::currentVersion));
        int indexes = JdbcTransactions.run(connections, false, connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name IN "
                            + "('discoveries_owner_idx','discoveries_visibility_idx','grants_recipient_idx',"
                            + "'market_transactions_status_idx','market_transactions_player_idx','market_escrow_state_idx')");
                 var rows = statement.executeQuery()) {
                assertTrue(rows.next()); return rows.getInt(1);
            }
        });
        assertEquals(6, indexes);
    }

    @Test
    void repeatedMigrationsAreIdempotentAndRecordedExactlyOnce() {
        var runner = runner(SqliteSchema.migrations());
        assertEquals(SqliteSchema.VERSION, runner.migrate());
        assertEquals(SqliteSchema.VERSION, runner.migrate());
        assertEquals(SqliteSchema.VERSION, runner.migrate());
        JdbcTransactions.run(connections, false, connection -> {
            try (var statement = connection.prepareStatement("SELECT version, checksum FROM schema_migrations");
                 var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
                assertEquals(SqliteSchema.migrations().getFirst().checksum(), rows.getString(2));
                assertTrue(rows.next());
                assertEquals(2, rows.getInt(1));
                assertEquals(SqliteSchema.migrations().get(1).checksum(), rows.getString(2));
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
                assertEquals(SqliteSchema.migrations().get(2).checksum(), rows.getString(2));
                assertTrue(rows.next());
                assertEquals(4,rows.getInt(1));
                assertEquals(SqliteSchema.migrations().get(3).checksum(),rows.getString(2));
                assertTrue(rows.next());assertEquals(5,rows.getInt(1));assertEquals(SqliteSchema.migrations().get(4).checksum(),rows.getString(2));
                assertFalse(rows.next());
            }
            return null;
        });
    }

    @Test
    void failedMigrationRollsBackDdlAndHistoryAndRetainsPriorVersion() {
        runner(SqliteSchema.migrations()).migrate();
        var broken = new SqlMigration(6, List.of("CREATE TABLE must_rollback (id INTEGER)", "THIS IS NOT VALID SQL"));
        var runner = runner(List.of(SqliteSchema.migrations().get(0), SqliteSchema.migrations().get(1), SqliteSchema.migrations().get(2), SqliteSchema.migrations().get(3), SqliteSchema.migrations().get(4), broken));
        assertThrows(StorageException.class, runner::migrate);
        assertFalse(tableExists("must_rollback"));
        assertEquals(SqliteSchema.VERSION, JdbcTransactions.run(connections, false, MigrationRunner::currentVersion));
        assertTrue(tableExists("discoveries"));
    }

    @Test
    void failedInitialMigrationLeavesNoPartiallyCreatedSchema() {
        var broken = new SqlMigration(1, List.of("CREATE TABLE must_rollback (id INTEGER)", "INVALID SQL"));
        assertThrows(StorageException.class, () -> runner(List.of(broken)).migrate());
        assertFalse(tableExists("must_rollback"));
        assertFalse(tableExists("schema_migrations"));
    }

    @Test
    void successfulFutureMigrationsExecuteInOrderAndAreNotRerun() {
        var first = new SqlMigration(1, List.of("CREATE TABLE example (id INTEGER PRIMARY KEY)"));
        var second = new SqlMigration(2, List.of("INSERT INTO example(id) VALUES (1)"));
        var runner = runner(List.of(first, second));
        assertEquals(2, runner.migrate());
        assertEquals(2, runner.migrate());
    }

    @Test
    void outOfOrderMigrationsAndChangedChecksumsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> runner(List.of(new SqlMigration(2, List.of("SELECT 1")))));
        runner(SqliteSchema.migrations()).migrate();
        assertThrows(CorruptRecordException.class,
                () -> runner(List.of(new SqlMigration(1, List.of("SELECT 1")))).migrate());
    }

    @Test
    void newerDatabaseSchemaFailsExplicitly() {
        runner(SqliteSchema.migrations()).migrate();
        JdbcTransactions.run(connections, true, connection -> {
            try (var statement = connection.prepareStatement("INSERT INTO schema_migrations(version, checksum) VALUES (?, ?)")) {
                statement.setInt(1, 6); statement.setString(2, "future"); statement.executeUpdate();
            }
            return null;
        });
        assertThrows(UnsupportedStorageVersionException.class, () -> runner(SqliteSchema.migrations()).migrate());
    }

    @Test
    void concurrentMigrationRunnersSerializeThroughDatabase() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> migrate = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return runner(SqliteSchema.migrations()).migrate();
            };
            var first = pool.submit(migrate); var second = pool.submit(migrate); start.countDown();
            assertEquals(SqliteSchema.VERSION, first.get(15, TimeUnit.SECONDS));
            assertEquals(SqliteSchema.VERSION, second.get(15, TimeUnit.SECONDS));
            assertEquals(SqliteSchema.VERSION, JdbcTransactions.run(connections, false, MigrationRunner::currentVersion));
        }
    }
}
