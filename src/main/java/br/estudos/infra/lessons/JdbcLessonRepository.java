package br.estudos.infra.lessons;

import br.estudos.app.lessons.LessonRepository;
import br.estudos.domain.Lesson;
import br.estudos.domain.LessonStatus;
import br.estudos.infra.db.Database;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class JdbcLessonRepository implements LessonRepository {
    private final Database db;

    public JdbcLessonRepository(Database db) { this.db = db; }

    @Override
    public Lesson insert(Lesson l) {
        String sql = "INSERT INTO lesson(kind,area,title,topics,status,pdf_path,md_path,log,created_at) VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, l);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                return new Lesson(k.getLong(1), l.kind(), l.area(), l.title(), l.topics(), l.status(),
                        l.pdfPath(), l.mdPath(), l.log(), l.createdAt());
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void update(Lesson l) {
        String sql = "UPDATE lesson SET kind=?,area=?,title=?,topics=?,status=?,pdf_path=?,md_path=?,log=?,created_at=? WHERE id=?";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, l);
            ps.setLong(10, l.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int failGenerating(String log) {
        try (Connection c = db.connect(); PreparedStatement ps =
                c.prepareStatement("UPDATE lesson SET status='FALHOU', log=? WHERE status='GERANDO'")) {
            ps.setString(1, log);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Lesson> search(String q, String area) {
        StringBuilder sql = new StringBuilder("SELECT * FROM lesson WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND lower(title) LIKE ? ESCAPE '\\'");
            args.add("%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (area != null && !area.isBlank()) {
            sql.append(" AND area = ?");
            args.add(area);
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setString(i + 1, args.get(i));
            List<Lesson> out = new ArrayList<>();
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    out.add(new Lesson(r.getLong("id"), r.getString("kind"), r.getString("area"), r.getString("title"),
                            r.getString("topics"), LessonStatus.valueOf(r.getString("status")), r.getString("pdf_path"),
                            r.getString("md_path"), r.getString("log"), Instant.parse(r.getString("created_at"))));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void bind(PreparedStatement ps, Lesson l) throws SQLException {
        ps.setString(1, l.kind());
        ps.setString(2, l.area());
        ps.setString(3, l.title());
        ps.setString(4, l.topics());
        ps.setString(5, l.status().name());
        ps.setString(6, l.pdfPath());
        ps.setString(7, l.mdPath());
        ps.setString(8, l.log());
        ps.setString(9, l.createdAt().toString());
    }
}
