package br.estudos.infra.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Aplica migrations {@code V<n>__<nome>.sql} em ordem, cada uma numa transação,
 * registrando em {@code schema_version}.
 *
 * <p>Limitação do separador: comandos são divididos em {@code ;} depois de remover comentários
 * {@code --}. Não use {@code ;} ou {@code --} dentro de strings nem triggers com corpo.
 */
public class Migrator {

    private static final Logger log = LoggerFactory.getLogger(Migrator.class);
    private static final Pattern NAME = Pattern.compile("V(\\d+)__(\\w+)\\.sql");

    public record Migration(int version, String name, String sql) {}

    private final Database db;

    public Migrator(Database db) {
        this.db = db;
    }

    /** @return quantas migrations foram aplicadas agora */
    public int migrate(List<Migration> migrations) throws SQLException {
        List<Migration> sorted = migrations.stream()
                .sorted(Comparator.comparingInt(Migration::version)).toList();
        try (Connection c = db.connect()) {
            try (Statement st = c.createStatement()) {
                st.execute("""
                        CREATE TABLE IF NOT EXISTS schema_version (
                          version INTEGER PRIMARY KEY,
                          name TEXT NOT NULL,
                          applied_at TEXT NOT NULL)""");
            }
            Set<Integer> applied = appliedVersions(c);
            int count = 0;
            for (Migration m : sorted) {
                if (applied.contains(m.version())) continue;
                apply(c, m);
                count++;
            }
            return count;
        }
    }

    private static Set<Integer> appliedVersions(Connection c) throws SQLException {
        Set<Integer> s = new HashSet<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version")) {
            while (rs.next()) s.add(rs.getInt(1));
        }
        return s;
    }

    private static void apply(Connection c, Migration m) throws SQLException {
        log.info("Aplicando migration V{}__{}", m.version(), m.name());
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            for (String sql : splitStatements(m.sql())) st.execute(sql);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO schema_version(version, name, applied_at) VALUES (?, ?, ?)")) {
                ps.setInt(1, m.version());
                ps.setString(2, m.name());
                ps.setString(3, Instant.now().toString());
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw new SQLException("Falha na migration V" + m.version() + "__" + m.name() + ": " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(true);
        }
    }

    static List<String> splitStatements(String sql) {
        String noComments = sql.lines()
                .map(l -> {
                    int i = l.indexOf("--");
                    return i >= 0 ? l.substring(0, i) : l;
                })
                .reduce("", (a, b) -> a + b + "\n");
        List<String> out = new ArrayList<>();
        for (String s : noComments.split(";")) {
            if (!s.isBlank()) out.add(s.strip());
        }
        return out;
    }

    /** Lê as migrations de uma pasta do classpath (funciona em diretório e em jar). */
    public static List<Migration> loadFromClasspath(String dir) throws IOException {
        URL url = Migrator.class.getClassLoader().getResource(dir);
        if (url == null) throw new IOException("Pasta de migrations não encontrada no classpath: " + dir);
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        if ("jar".equals(uri.getScheme())) {
            FileSystem fs;
            boolean created = false;
            try {
                fs = FileSystems.newFileSystem(uri, Map.of());
                created = true;
            } catch (FileSystemAlreadyExistsException e) {
                fs = FileSystems.getFileSystem(uri);
            }
            try {
                return loadFromDir(fs.getPath(dir));
            } finally {
                if (created) fs.close();
            }
        }
        return loadFromDir(Path.of(uri));
    }

    static List<Migration> loadFromDir(Path dir) throws IOException {
        List<Migration> list = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.toList()) {
                Matcher m = NAME.matcher(p.getFileName().toString());
                if (!m.matches()) continue;
                int v = Integer.parseInt(m.group(1));
                if (!seen.add(v)) throw new IOException("Versão de migration duplicada: V" + v);
                list.add(new Migration(v, m.group(2), Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
        list.sort(Comparator.comparingInt(Migration::version));
        return list;
    }
}
