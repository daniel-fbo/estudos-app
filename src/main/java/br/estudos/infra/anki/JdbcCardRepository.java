package br.estudos.infra.anki;

import br.estudos.app.anki.CardRepository;
import br.estudos.domain.*;
import br.estudos.infra.db.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcCardRepository implements CardRepository {
    private final Database db;

    public JdbcCardRepository(Database db) { this.db = db; }

    @Override
    public Optional<Card> insertIfNew(Card c) {
        String sql = "INSERT OR IGNORE INTO card(front,back,area,origin,source_ref,front_hash,anki_note_id,synced_at) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection con = db.connect(); PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.front());
            ps.setString(2, c.back());
            ps.setString(3, c.area().name());
            ps.setString(4, c.origin().name());
            ps.setString(5, c.sourceRef());
            ps.setString(6, c.frontHash());
            if (c.ankiNoteId() == null) ps.setNull(7, Types.INTEGER); else ps.setLong(7, c.ankiNoteId());
            ps.setString(8, c.syncedAt());
            if (ps.executeUpdate() == 0) return Optional.empty();
            try (ResultSet k = ps.getGeneratedKeys()) {
                k.next();
                return Optional.of(new Card(k.getLong(1), c.front(), c.back(), c.area(), c.origin(), c.sourceRef(),
                        c.frontHash(), c.ankiNoteId(), c.syncedAt()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void update(Card c) {
        String sql = "UPDATE card SET front=?,back=?,area=?,front_hash=?,synced_at=? WHERE id=?";
        try (Connection con = db.connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, c.front());
            ps.setString(2, c.back());
            ps.setString(3, c.area().name());
            ps.setString(4, c.frontHash());
            ps.setString(5, c.syncedAt());
            ps.setLong(6, c.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("UNIQUE"))
                throw new IllegalArgumentException("Já existe um card com essa frente.");
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void delete(long id) {
        try (Connection con = db.connect(); PreparedStatement ps = con.prepareStatement("DELETE FROM card WHERE id=?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void markSynced(long id, long noteId, String at) {
        try (Connection con = db.connect();
             PreparedStatement ps = con.prepareStatement("UPDATE card SET anki_note_id=?, synced_at=? WHERE id=?")) {
            ps.setLong(1, noteId);
            ps.setString(2, at);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<Card> list(Area area, Boolean synced) {
        StringBuilder sql = new StringBuilder("SELECT * FROM card WHERE 1=1");
        if (area != null) sql.append(" AND area=?");
        if (synced != null) sql.append(synced ? " AND anki_note_id IS NOT NULL AND synced_at IS NOT NULL"
                : " AND (anki_note_id IS NULL OR synced_at IS NULL)");
        sql.append(" ORDER BY id DESC");
        try (Connection con = db.connect(); PreparedStatement ps = con.prepareStatement(sql.toString())) {
            if (area != null) ps.setString(1, area.name());
            List<Card> out = new ArrayList<>();
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    long note = r.getLong("anki_note_id");
                    boolean noNote = r.wasNull(); // antes de ler outras colunas (wasNull vale só para a última)
                    out.add(new Card(r.getLong("id"), r.getString("front"), r.getString("back"),
                            Area.valueOf(r.getString("area")), CardOrigin.valueOf(r.getString("origin")),
                            r.getString("source_ref"), r.getString("front_hash"),
                            noNote ? null : note, r.getString("synced_at")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
