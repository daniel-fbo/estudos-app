package br.estudos.app.backup;

import br.estudos.infra.db.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class BackupServiceTest {

    @TempDir Path tmp;
    private Path dbFile;
    private Path configFile;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tmp.resolve("estudos.db");
        configFile = tmp.resolve("config.json");

        // Cria um banco real com uma tabela
        Database db = new Database(dbFile);
        db.migrate();
        try (Connection conn = db.connect(); Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS backup_test (id INTEGER PRIMARY KEY, name TEXT)");
            s.execute("INSERT INTO backup_test VALUES (1, 'teste')");
        }

        // Cria config
        Files.writeString(configFile, "{\"theme\":\"escuro\"}");
    }

    @Test
    void exportCriaZipComBancoEConfig() throws Exception {
        BackupService svc = new BackupService(dbFile, configFile);
        Path dest = tmp.resolve("backup.zip");
        svc.export(dest);

        assertTrue(Files.exists(dest));
        try (ZipFile zf = new ZipFile(dest.toFile())) {
            assertNotNull(zf.getEntry("estudos.db"));
            assertNotNull(zf.getEntry("config.json"));
            assertEquals(2, zf.size());
        }
    }

    @Test
    void exportSemConfigIncluiSoBanco() throws Exception {
        Files.delete(configFile);
        BackupService svc = new BackupService(dbFile, configFile);
        Path dest = tmp.resolve("backup.zip");
        svc.export(dest);

        try (ZipFile zf = new ZipFile(dest.toFile())) {
            assertNotNull(zf.getEntry("estudos.db"));
            assertNull(zf.getEntry("config.json"));
            assertEquals(1, zf.size());
        }
    }

    @Test
    void importRestauraBancoEConfig() throws Exception {
        BackupService svc = new BackupService(dbFile, configFile);
        Path backupFile = tmp.resolve("backup.zip");
        svc.export(backupFile);

        // Altera o banco e config locais
        try (Connection conn = new Database(dbFile).connect(); Statement s = conn.createStatement()) {
            s.execute("DELETE FROM backup_test");
        }
        Files.writeString(configFile, "{\"tema\":\"claro\"}");

        // Importa o backup original
        Path destDir = tmp.resolve("restored");
        Files.createDirectories(destDir);
        Path restDb = destDir.resolve("estudos.db");
        Path restCfg = destDir.resolve("config.json");
        new BackupService(restDb, restCfg).importBackup(backupFile);

        // Verifica banco restaurado
        assertTrue(Files.exists(restDb));
        try (Connection conn = new Database(restDb).connect();
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT name FROM backup_test WHERE id = 1")) {
            assertTrue(rs.next());
            assertEquals("teste", rs.getString("name"));
        }

        // Verifica config restaurado
        String cfg = Files.readString(restCfg);
        assertTrue(cfg.contains("escuro"));
    }

    @Test
    void importSemBancoLancaExcecao() throws Exception {
        // Cria um zip só com config
        Path badZip = tmp.resolve("bad.zip");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(badZip))) {
            zos.putNextEntry(new ZipEntry("config.json"));
            zos.write("{}".getBytes());
            zos.closeEntry();
        }

        BackupService svc = new BackupService(dbFile, configFile);
        IOException ex = assertThrows(IOException.class, () -> svc.importBackup(badZip));
        assertTrue(ex.getMessage().contains("estudos.db"));
    }

    @Test
    void suggestedNameContemData() {
        BackupService svc = new BackupService(dbFile, configFile);
        String name = svc.suggestedName();
        assertTrue(name.startsWith("estudos-backup-"));
        assertTrue(name.endsWith(".zip"));
    }

    @Test
    void vacuumIntoCriaCopiaSemConexaoAberta() throws Exception {
        // Confirma que o VACUUM INTO funciona mesmo com o banco em uso
        BackupService svc = new BackupService(dbFile, configFile);
        // Abre uma conexão para simular o app rodando
        try (Connection conn = new Database(dbFile).connect()) {
            Path dest = tmp.resolve("backup2.zip");
            // Deve funcionar — VACUUM INTO não precisa de exclusive lock
            assertDoesNotThrow(() -> svc.export(dest));
            assertTrue(Files.exists(dest));
        }
    }
}
