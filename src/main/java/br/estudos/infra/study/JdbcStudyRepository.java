package br.estudos.infra.study;

import br.estudos.app.study.Dated;
import br.estudos.app.study.StudyRepository;
import br.estudos.domain.*;
import br.estudos.infra.db.Database;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;

public class JdbcStudyRepository implements StudyRepository {
    private final Database db;

    public JdbcStudyRepository(Database db) { this.db = db; }

    private static RuntimeException wrap(SQLException e) { return new IllegalStateException(e); }

    @Override
    public StudySession saveSession(StudySession s) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO study_session(area,subject,started_at,ended_at,duration_s,note) VALUES (?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.area().name());
            ps.setString(2, s.subject());
            ps.setString(3, s.startedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
            ps.setString(4, s.endedAt() == null ? null : s.endedAt().toString());
            ps.setLong(5, s.durationSeconds());
            ps.setString(6, s.note());
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                return new StudySession(k.getLong(1), s.area(), s.subject(), s.startedAt(), s.endedAt(),
                        s.durationSeconds(), s.note());
            }
        } catch (SQLException e) { throw wrap(e); }
    }

    private List<StudySession> querySessions(String sql, Object... args) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            List<StudySession> out = new ArrayList<>();
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    String end = r.getString("ended_at");
                    out.add(new StudySession(r.getLong("id"), Area.valueOf(r.getString("area")),
                            r.getString("subject"), Instant.parse(r.getString("started_at")),
                            end == null ? null : Instant.parse(end), r.getLong("duration_s"), r.getString("note")));
                }
            }
            return out;
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public List<StudySession> sessionsSince(Instant from) {
        // Texto ISO-8601 UTC: compara com o prefixo sem o "Z" final, para que valores com fração
        // ("...:00.100Z") não fiquem abaixo de "...:00Z" na ordenação lexicográfica.
        String prefix = from.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        prefix = prefix.substring(0, prefix.length() - 1);
        return querySessions("SELECT * FROM study_session WHERE ended_at IS NOT NULL AND started_at >= ?", prefix);
    }

    @Override
    public List<StudySession> lastSessions(int n) {
        return querySessions("SELECT * FROM study_session WHERE ended_at IS NOT NULL ORDER BY started_at DESC, id DESC LIMIT ?", n);
    }

    @Override
    public List<Instant> allSessionStarts() {
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT started_at FROM study_session WHERE ended_at IS NOT NULL")) {
            List<Instant> out = new ArrayList<>();
            while (r.next()) out.add(Instant.parse(r.getString(1)));
            return out;
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public Goal saveGoal(Goal g) {
        try (Connection c = db.connect()) {
            if (g.id() == 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO goal(title,area,kind,period,target,auto_count_ac,active,created_at) VALUES (?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    bind(ps, g);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) {
                        k.next();
                        return new Goal(k.getLong(1), g.title(), g.area(), g.kind(), g.period(), g.target(),
                                g.source(), g.active(), g.createdAt());
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE goal SET title=?,area=?,kind=?,period=?,target=?,auto_count_ac=?,active=?,created_at=? WHERE id=?")) {
                bind(ps, g);
                ps.setLong(9, g.id());
                ps.executeUpdate();
                return g;
            }
        } catch (SQLException e) { throw wrap(e); }
    }

    private static void bind(PreparedStatement ps, Goal g) throws SQLException {
        ps.setString(1, g.title());
        ps.setString(2, g.area().name());
        ps.setString(3, g.kind().name());
        ps.setString(4, g.period().name());
        ps.setDouble(5, g.target());
        ps.setInt(6, g.source().code());
        ps.setInt(7, g.active() ? 1 : 0);
        ps.setString(8, g.createdAt().toString());
    }

    @Override
    public void deleteGoal(long id) {
        try (Connection c = db.connect()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE calendar_block SET goal_id=NULL WHERE goal_id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM goal WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public List<Goal> goals(boolean onlyActive) {
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT * FROM goal" + (onlyActive ? " WHERE active=1" : "") + " ORDER BY id")) {
            List<Goal> out = new ArrayList<>();
            while (r.next()) {
                out.add(new Goal(r.getLong("id"), r.getString("title"), Area.valueOf(r.getString("area")),
                        GoalKind.valueOf(r.getString("kind")), GoalPeriod.valueOf(r.getString("period")),
                        r.getDouble("target"), GoalSource.of(r.getInt("auto_count_ac")),
                        r.getInt("active") == 1, Instant.parse(r.getString("created_at"))));
            }
            return out;
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public void logCountDelta(long goalId, int delta, Instant at) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO goal_count_log(goal_id,delta,at) VALUES (?,?,?)")) {
            ps.setLong(1, goalId);
            ps.setInt(2, delta);
            ps.setString(3, at.toString());
            ps.executeUpdate();
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public List<Dated> countDeltas(long goalId) {
        return datedQuery("SELECT at, delta FROM goal_count_log WHERE goal_id=?", goalId);
    }

    @Override
    public List<Dated> pagesRead(Area area) {
        return datedQuery("SELECT l.at, l.pages FROM reading_log l JOIN book b ON b.id=l.book_id "
                + "WHERE b.area=? AND l.pages > 0", area.name());
    }

    private List<Dated> datedQuery(String sql, Object arg) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, arg);
            List<Dated> out = new ArrayList<>();
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) out.add(new Dated(Instant.parse(r.getString(1)), r.getLong(2)));
            }
            return out;
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public List<Instant> cfFirstAcTimes() {
        Map<String, Instant> first = new HashMap<>();
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT contest_id, problem_index, submitted_at FROM cf_submission WHERE verdict='OK'")) {
            while (r.next()) {
                first.merge(r.getLong(1) + "/" + r.getString(2), Instant.parse(r.getString(3)),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        } catch (SQLException e) { throw wrap(e); }
        return new ArrayList<>(first.values());
    }

    @Override
    public void updateSession(StudySession s) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE study_session SET ended_at=?, duration_s=?, note=? WHERE id=?")) {
            ps.setString(1, s.endedAt() == null ? null : s.endedAt().toString());
            ps.setLong(2, s.durationSeconds());
            ps.setString(3, s.note());
            ps.setLong(4, s.id());
            ps.executeUpdate();
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public void deleteSession(long id) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM study_session WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw wrap(e); }
    }

    @Override
    public Optional<StudySession> openSession() {
        List<StudySession> l = querySessions("SELECT * FROM study_session WHERE ended_at IS NULL ORDER BY started_at DESC, id DESC LIMIT 1");
        return l.stream().findFirst();
    }

    @Override
    public List<Instant> firstAcTimes() {
        // mínimo calculado em Java: ran_at é Instant.toString(), cuja fração varia de tamanho
        // e quebra a comparação de texto do MIN() do SQL
        Map<String, Instant> first = new HashMap<>();
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT source_path, ran_at FROM code_run WHERE verdict='AC'")) {
            while (r.next()) {
                first.merge(r.getString(1), Instant.parse(r.getString(2)),
                        (a, b) -> a.isBefore(b) ? a : b);
            }
        } catch (SQLException e) { throw wrap(e); }
        return new ArrayList<>(first.values());
    }
}
