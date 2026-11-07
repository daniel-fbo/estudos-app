package br.estudos.infra.ai;

import br.estudos.app.ai.AiCallRepository;
import br.estudos.app.ai.BudgetStatus;
import br.estudos.domain.*;
import br.estudos.infra.db.Database;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcAiCallRepository implements AiCallRepository {
    private final Database db;

    public JdbcAiCallRepository(Database db) { this.db = db; }

    @Override
    public AiCall insert(AiCall a) {
        String sql = "INSERT INTO ai_call(cache_key,runner,model,task,prompt,response,status,input_tokens,"
                + "output_tokens,cost_usd,duration_ms,created_at,cache_hit) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, a.cacheKey());
            ps.setString(2, a.runner().name());
            ps.setString(3, a.model());
            ps.setString(4, a.task().name());
            ps.setString(5, a.prompt());
            ps.setString(6, a.response());
            ps.setString(7, a.status().name());
            ps.setObject(8, a.inputTokens());
            ps.setObject(9, a.outputTokens());
            ps.setObject(10, a.costUsd());
            ps.setLong(11, a.durationMs());
            ps.setString(12, ts(a.createdAt()));
            ps.setInt(13, a.cacheHit() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                return new AiCall(k.getLong(1), a.cacheKey(), a.runner(), a.model(), a.task(), a.prompt(),
                        a.response(), a.status(), a.inputTokens(), a.outputTokens(), a.costUsd(),
                        a.durationMs(), a.createdAt(), a.cacheHit());
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override
    public Optional<AiCall> findOk(String cacheKey) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM ai_call WHERE cache_key=? AND status='OK' AND cache_hit=0 ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, cacheKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs, true)) : Optional.empty();
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override
    public BudgetStatus usageSince(Instant from, int dailyLimit) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*), COALESCE(SUM(COALESCE(input_tokens,0)+COALESCE(output_tokens,0)),0) "
                        + "FROM ai_call WHERE cache_hit=0 AND created_at >= ?")) {
            ps.setString(1, ts(from));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new BudgetStatus(rs.getInt(1), rs.getLong(2), dailyLimit);
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override
    public List<AiCall> statsSince(Instant from) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT id,cache_key,runner,model,task,status,input_tokens,output_tokens,cost_usd,duration_ms,"
                        + "created_at,cache_hit FROM ai_call WHERE created_at >= ? ORDER BY id")) {
            ps.setString(1, ts(from));
            List<AiCall> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs, false));
            }
            return out;
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    private static AiCall map(ResultSet rs, boolean full) throws SQLException {
        return new AiCall(rs.getLong("id"), rs.getString("cache_key"),
                AiRunnerKind.valueOf(rs.getString("runner")), rs.getString("model"),
                AiTask.valueOf(rs.getString("task")),
                full ? rs.getString("prompt") : null, full ? rs.getString("response") : null,
                AiCallStatus.valueOf(rs.getString("status")),
                (Long) longOrNull(rs, "input_tokens"), (Long) longOrNull(rs, "output_tokens"),
                rs.getObject("cost_usd") == null ? null : rs.getDouble("cost_usd"),
                rs.getLong("duration_ms"), Instant.parse(rs.getString("created_at")),
                rs.getInt("cache_hit") == 1);
    }

    private static Long longOrNull(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col) == null ? null : rs.getLong(col);
    }

    private static String ts(Instant i) {
        return i.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
