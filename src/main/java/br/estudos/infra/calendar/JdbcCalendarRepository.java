package br.estudos.infra.calendar;

import br.estudos.app.calendar.CalendarRepository;
import br.estudos.domain.Area;
import br.estudos.domain.CalendarBlock;
import br.estudos.infra.db.Database;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcCalendarRepository implements CalendarRepository {
    private static final String SEL = "SELECT b.*, g.title AS gtitle, g.area AS garea FROM calendar_block b "
            + "LEFT JOIN goal g ON g.id = b.goal_id ";
    private final Database db;

    public JdbcCalendarRepository(Database db) { this.db = db; }

    @Override
    public CalendarBlock save(CalendarBlock b) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO calendar_block(goal_id,google_event_id,starts_at,ends_at,done) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setObject(1, b.goalId());
            ps.setString(2, b.googleEventId());
            ps.setString(3, b.startsAt().toString());
            ps.setString(4, b.endsAt().toString());
            ps.setInt(5, b.done() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                return new CalendarBlock(k.getLong(1), b.goalId(), b.title(), b.area(), b.googleEventId(),
                        b.startsAt(), b.endsAt(), b.done());
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    private List<CalendarBlock> query(String where, Object... args) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(SEL + where)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            List<CalendarBlock> out = new ArrayList<>();
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    long gid = r.getLong("goal_id");
                    Long goalId = r.wasNull() ? null : gid;
                    String area = r.getString("garea");
                    out.add(new CalendarBlock(r.getLong("id"), goalId,
                            r.getString("gtitle") == null ? "Estudo" : r.getString("gtitle"),
                            area == null ? Area.OUTRO : Area.valueOf(area), r.getString("google_event_id"),
                            Instant.parse(r.getString("starts_at")), Instant.parse(r.getString("ends_at")),
                            r.getInt("done") == 1));
                }
            }
            return out;
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override
    public List<CalendarBlock> between(Instant from, Instant to) {
        // Instants gravados com fração fixa de segundos zero; ordenação lexicográfica ISO é suficiente.
        return query("WHERE b.starts_at >= ? AND b.starts_at < ? ORDER BY b.starts_at", from.toString(), to.toString());
    }

    @Override
    public Optional<CalendarBlock> find(long id) {
        return query("WHERE b.id = ?", id).stream().findFirst();
    }

    @Override
    public void markDone(long id) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "UPDATE calendar_block SET done = 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override
    public java.util.Set<Integer> loadAvailability() {
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT weekday, hour FROM availability")) {
            java.util.Set<Integer> out = new java.util.TreeSet<>();
            while (r.next()) out.add(r.getInt(1) * 100 + r.getInt(2));
            return out;
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }

    @Override
    public void saveAvailability(java.util.Set<Integer> cells) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM availability");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO availability(weekday, hour) VALUES (?,?)")) {
                for (int v : cells) { ps.setInt(1, v / 100); ps.setInt(2, v % 100); ps.addBatch(); }
                ps.executeBatch();
            }
            c.commit();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
}
