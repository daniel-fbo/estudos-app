package br.estudos.infra.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    @TempDir Path dir;

    @Test
    void createsAllTablesFromV1() throws Exception {
        Database db = new Database(dir.resolve("a/b/estudos.db"));

        assertEquals(2, db.migrate());

        Set<String> expected = Set.of("schema_version", "study_session", "goal", "code_test_case",
                "code_run", "ai_call", "lesson", "book", "card", "cf_submission", "hint", "calendar_block");
        assertEquals(new TreeSet<>(expected), tables(db));
    }

    @Test
    void migrateIsIdempotent() throws Exception {
        Database db = new Database(dir.resolve("estudos.db"));
        db.migrate();

        assertEquals(0, db.migrate());
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version, name FROM schema_version")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
            assertEquals("base", rs.getString(2));
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
            assertEquals("ai_call_cache_hit", rs.getString(2));
            assertFalse(rs.next());
        }
    }

    @Test
    void foreignKeysAreEnforced() throws Exception {
        Database db = new Database(dir.resolve("estudos.db"));
        db.migrate();
        try (Connection c = db.connect(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.execute(
                    "INSERT INTO calendar_block(goal_id, starts_at, ends_at) VALUES (999, 'x', 'y')"));
        }
    }

    @Test
    void failedMigrationIsRolledBack() throws Exception {
        Database db = new Database(dir.resolve("estudos.db"));
        Migrator m = new Migrator(db);
        List<Migrator.Migration> bad = List.of(
                new Migrator.Migration(1, "ok", "CREATE TABLE t1(x INTEGER);"),
                new Migrator.Migration(2, "ruim", "CREATE TABLE t2(x INTEGER); ISSO NAO E SQL;"));

        assertThrows(SQLException.class, () -> m.migrate(bad));
        Set<String> t = tables(db);
        assertTrue(t.contains("t1"));
        assertFalse(t.contains("t2"));
    }

    @Test
    void splitIgnoresComments() {
        List<String> s = Migrator.splitStatements("""
                -- cabeçalho; com ponto e vírgula
                CREATE TABLE a(x TEXT); -- fim; aqui
                CREATE TABLE b(y TEXT);
                """);
        assertEquals(2, s.size());
        assertTrue(s.get(1).startsWith("CREATE TABLE b"));
    }

    private static Set<String> tables(Database db) throws SQLException {
        Set<String> names = new TreeSet<>();
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }
}
