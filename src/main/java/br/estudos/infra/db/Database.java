package br.estudos.infra.db;

import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/** Banco SQLite em arquivo. Cada {@link #connect()} abre uma conexão nova com chaves estrangeiras ligadas. */
public class Database {

    private final Path file;
    private final SQLiteConfig sqliteConfig = new SQLiteConfig();

    public Database(Path file) {
        this.file = file.toAbsolutePath();
        sqliteConfig.enforceForeignKeys(true);
        sqliteConfig.setBusyTimeout(5_000);
    }

    public Path file() {
        return file;
    }

    public Connection connect() throws SQLException {
        return sqliteConfig.createConnection("jdbc:sqlite:" + file);
    }

    /** Cria a pasta do banco, se preciso, e aplica as migrations pendentes de {@code db/}. */
    public int migrate() throws IOException, SQLException {
        Files.createDirectories(file.getParent());
        return new Migrator(this).migrate(Migrator.loadFromClasspath("db"));
    }
}
