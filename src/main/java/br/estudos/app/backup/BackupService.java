package br.estudos.app.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Exporta e importa backup do banco SQLite e config num .zip.
 * <p>
 * O banco é copiado com VACUUM INTO para garantir consistência
 * sem depender do estado de conexões abertas.
 */
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private static final String DB_ENTRY = "estudos.db";
    private static final String CONFIG_ENTRY = "config.json";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final Path dbFile;
    private final Path configFile;

    public BackupService(Path dbFile, Path configFile) {
        this.dbFile = dbFile.toAbsolutePath();
        this.configFile = configFile.toAbsolutePath();
    }

    /** Nome sugerido para o arquivo de backup. */
    public String suggestedName() {
        return "estudos-backup-" + LocalDateTime.now().format(FMT) + ".zip";
    }

    /**
     * Exporta banco + config para {@code dest}.
     * Usa VACUUM INTO para copiar o banco de forma atômica e segura.
     */
    public void export(Path dest) throws IOException {
        Path tempDb = Files.createTempFile("estudos-backup-", ".db");
        try {
            // VACUUM INTO cria uma cópia consistente do banco sem precisar fechar conexões
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("VACUUM INTO '" + tempDb.toAbsolutePath().toString().replace("'", "''") + "'");
            } catch (SQLException e) {
                throw new IOException("Falha ao criar cópia segura do banco: " + e.getMessage(), e);
            }

            try (OutputStream os = Files.newOutputStream(dest);
                 ZipOutputStream zos = new ZipOutputStream(os)) {

                addEntry(zos, DB_ENTRY, tempDb);

                if (Files.exists(configFile)) {
                    addEntry(zos, CONFIG_ENTRY, configFile);
                }

                zos.finish();
            }
            log.info("Backup exportado para {}", dest);
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    /**
     * Importa backup de {@code src}. Sobrescreve o banco e o config existentes.
     * O app deve ser reiniciado após a importação.
     */
    public void importBackup(Path src) throws IOException {
        Files.createDirectories(dbFile.getParent());
        Files.createDirectories(configFile.getParent());
        Path tmpDb = dbFile.resolveSibling(dbFile.getFileName() + ".import-tmp");
        Path tmpCfg = configFile.resolveSibling(configFile.getFileName() + ".import-tmp");
        boolean foundDb = false;
        boolean foundCfg = false;
        try {
            try (InputStream is = Files.newInputStream(src);
                 ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    switch (entry.getName()) {
                        case DB_ENTRY -> {
                            Files.copy(zis, tmpDb, StandardCopyOption.REPLACE_EXISTING);
                            foundDb = true;
                        }
                        case CONFIG_ENTRY -> {
                            Files.copy(zis, tmpCfg, StandardCopyOption.REPLACE_EXISTING);
                            foundCfg = true;
                        }
                        default -> log.warn("Entrada desconhecida no backup: {}", entry.getName());
                    }
                    zis.closeEntry();
                }
            }
            if (!foundDb) {
                throw new IOException("Backup inválido: não contém " + DB_ENTRY);
            }
            validateDb(tmpDb);
            Files.move(tmpDb, dbFile, StandardCopyOption.REPLACE_EXISTING);
            if (foundCfg) {
                Files.move(tmpCfg, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Backup restaurado");
        } finally {
            Files.deleteIfExists(tmpDb);
            Files.deleteIfExists(tmpCfg);
        }
    }

    private static void validateDb(Path db) throws IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
            if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                throw new IOException("Backup inválido: banco corrompido");
            }
        } catch (SQLException e) {
            throw new IOException("Backup inválido: banco ilegível (" + e.getMessage() + ")", e);
        }
    }

    private static void addEntry(ZipOutputStream zos, String name, Path file) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        Files.copy(file, zos);
        zos.closeEntry();
    }
}
