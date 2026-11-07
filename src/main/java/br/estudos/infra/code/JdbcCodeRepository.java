package br.estudos.infra.code;

import br.estudos.app.code.CodeRepository;
import br.estudos.domain.TestCase;
import br.estudos.infra.db.Database;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class JdbcCodeRepository implements CodeRepository {
    private final Database db;

    public JdbcCodeRepository(Database db) { this.db = db; }

    @Override
    public TestCase saveCase(String sourcePath, String input, String expected, String origin) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO code_test_case(source_path,input,expected,origin) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sourcePath);
            ps.setString(2, input);
            ps.setString(3, expected);
            ps.setString(4, origin);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                return new TestCase(k.getLong(1), sourcePath, input, expected, origin);
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override
    public void deleteCase(long id) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM code_test_case WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override
    public List<TestCase> cases(String sourcePath) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM code_test_case WHERE source_path=? ORDER BY id")) {
            ps.setString(1, sourcePath);
            List<TestCase> out = new ArrayList<>();
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) out.add(new TestCase(r.getLong("id"), r.getString("source_path"),
                        r.getString("input"), r.getString("expected"), r.getString("origin")));
            }
            return out;
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override
    public void saveRun(String sourcePath, String verdict, int passed, int total, String detail) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO code_run(source_path,ran_at,verdict,passed,total,detail) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, sourcePath);
            ps.setString(2, Instant.now().toString());
            ps.setString(3, verdict);
            ps.setInt(4, passed);
            ps.setInt(5, total);
            ps.setString(6, detail);
            ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
}
