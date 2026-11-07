package br.estudos.infra.library;

import br.estudos.app.library.BookRepository;
import br.estudos.domain.*;
import br.estudos.infra.db.Database;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcBookRepository implements BookRepository {
    private final Database db;

    public JdbcBookRepository(Database db) { this.db = db; }

    @Override
    public Book insert(Book b) {
        String sql = "INSERT INTO book(title,authors,area,kind,pdf_path,pages,current_page,status,tags,notes,indexed_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, b);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                return new Book(k.getLong(1), b.title(), b.authors(), b.area(), b.kind(), b.pdfPath(), b.pages(),
                        b.currentPage(), b.status(), b.tags(), b.notes(), b.indexedAt());
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void update(Book b) {
        String sql = "UPDATE book SET title=?,authors=?,area=?,kind=?,pdf_path=?,pages=?,current_page=?,status=?,"
                + "tags=?,notes=?,indexed_at=? WHERE id=?";
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, b);
            ps.setLong(12, b.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM book WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Optional<Book> find(long id) {
        return one("SELECT * FROM book WHERE id=?", String.valueOf(id));
    }

    @Override
    public Optional<Book> findByPath(String pdfPath) {
        return one("SELECT * FROM book WHERE pdf_path=?", pdfPath);
    }

    private Optional<Book> one(String sql, String arg) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, arg);
            try (ResultSet r = ps.executeQuery()) {
                return r.next() ? Optional.of(map(r)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Book> list(String q, Area area, BookStatus status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM book WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (title LIKE ? OR authors LIKE ? OR tags LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        if (area != null) { sql.append(" AND area=?"); args.add(area.name()); }
        if (status != null) { sql.append(" AND status=?"); args.add(status.name()); }
        sql.append(" ORDER BY title COLLATE NOCASE");
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) ps.setString(i + 1, args.get(i));
            List<Book> out = new ArrayList<>();
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) out.add(map(r));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Book map(ResultSet r) throws SQLException {
        int pages = r.getInt("pages");
        Integer p = r.wasNull() ? null : pages;
        String ia = r.getString("indexed_at");
        return new Book(r.getLong("id"), r.getString("title"), r.getString("authors"),
                Area.valueOf(r.getString("area")), BookKind.valueOf(r.getString("kind")), r.getString("pdf_path"),
                p, r.getInt("current_page"), BookStatus.valueOf(r.getString("status")), r.getString("tags"),
                r.getString("notes"), ia == null ? null : Instant.parse(ia));
    }

    private static void bind(PreparedStatement ps, Book b) throws SQLException {
        ps.setString(1, b.title());
        ps.setString(2, b.authors());
        ps.setString(3, b.area().name());
        ps.setString(4, b.kind().name());
        ps.setString(5, b.pdfPath());
        if (b.pages() == null) ps.setNull(6, Types.INTEGER); else ps.setInt(6, b.pages());
        ps.setInt(7, b.currentPage());
        ps.setString(8, b.status().name());
        ps.setString(9, b.tags());
        ps.setString(10, b.notes());
        ps.setString(11, b.indexedAt() == null ? null : b.indexedAt().toString());
    }

    @Override
    public void logReading(long bookId, int pages, java.time.Instant at) {
        try (java.sql.Connection c = db.connect(); java.sql.PreparedStatement ps = c.prepareStatement(
                "INSERT INTO reading_log(book_id,pages,at) VALUES (?,?,?)")) {
            ps.setLong(1, bookId);
            ps.setInt(2, pages);
            ps.setString(3, at.toString());
            ps.executeUpdate();
        } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
    }
}
