package br.estudos.infra.cf;

import br.estudos.app.cf.*;
import br.estudos.infra.db.Database;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcCfRepository implements CfRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Database db;

    public JdbcCfRepository(Database db) { this.db = db; }

    @Override
    public int upsertSubmissions(List<CfSubmission> subs) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            int added = 0;
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT INTO cf_submission(id,contest_id,problem_index,problem_name,rating,tags,verdict,submitted_at) "
                            + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET verdict=excluded.verdict, "
                            + "rating=excluded.rating, tags=excluded.tags")) {
                for (CfSubmission s : subs) {
                    boolean exists;
                    try (PreparedStatement q = c.prepareStatement("SELECT 1 FROM cf_submission WHERE id=?")) {
                        q.setLong(1, s.id());
                        try (ResultSet r = q.executeQuery()) { exists = r.next(); }
                    }
                    ins.setLong(1, s.id());
                    if (s.contestId() == null) ins.setNull(2, Types.INTEGER); else ins.setInt(2, s.contestId());
                    ins.setString(3, s.problemIndex());
                    ins.setString(4, s.problemName());
                    if (s.rating() == null) ins.setNull(5, Types.INTEGER); else ins.setInt(5, s.rating());
                    ins.setString(6, JSON.writeValueAsString(s.tags()));
                    ins.setString(7, s.verdict());
                    ins.setString(8, s.submittedAt());
                    ins.executeUpdate();
                    if (!exists) added++;
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
            return added;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<CfSubmission> submissions() {
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT id,contest_id,problem_index,problem_name,rating,tags,verdict,submitted_at "
                     + "FROM cf_submission ORDER BY submitted_at")) {
            List<CfSubmission> out = new ArrayList<>();
            while (r.next()) {
                Integer cid = (Integer) r.getObject(2), rt = (Integer) r.getObject(5);
                List<String> tags = r.getString(6) == null ? List.of()
                        : JSON.readValue(r.getString(6), new TypeReference<List<String>>() {});
                out.add(new CfSubmission(r.getLong(1), cid, r.getString(3), r.getString(4), rt, tags,
                        r.getString(7), r.getString(8)));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void replaceRatings(List<CfRatingPoint> points) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement();
                 PreparedStatement ps = c.prepareStatement("INSERT INTO cf_rating VALUES (?,?,?,?,?)")) {
                st.executeUpdate("DELETE FROM cf_rating");
                for (CfRatingPoint p : points) {
                    ps.setInt(1, p.contestId());
                    ps.setString(2, p.contestName());
                    ps.setString(3, p.ratedAt());
                    ps.setInt(4, p.oldRating());
                    ps.setInt(5, p.newRating());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<CfRatingPoint> ratings() {
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT contest_id,contest_name,rated_at,old_rating,new_rating FROM cf_rating ORDER BY rated_at")) {
            List<CfRatingPoint> out = new ArrayList<>();
            while (r.next()) out.add(new CfRatingPoint(r.getInt(1), r.getString(2), r.getString(3), r.getInt(4), r.getInt(5)));
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Optional<String> hint(String problemRef, int level) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT text FROM hint WHERE problem_ref=? AND level=?")) {
            ps.setString(1, problemRef);
            ps.setInt(2, level);
            try (ResultSet r = ps.executeQuery()) { return r.next() ? Optional.of(r.getString(1)) : Optional.empty(); }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void saveHint(String problemRef, int level, String text, String createdAt) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "INSERT OR REPLACE INTO hint(problem_ref,level,text,created_at) VALUES (?,?,?,?)")) {
            ps.setString(1, problemRef);
            ps.setInt(2, level);
            ps.setString(3, text);
            ps.setString(4, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int maxHintLevel(String problemRef) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(level),0) FROM hint WHERE problem_ref=?")) {
            ps.setString(1, problemRef);
            try (ResultSet r = ps.executeQuery()) { r.next(); return r.getInt(1); }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
