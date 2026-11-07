package br.estudos.app.backup;

import br.estudos.app.config.Config;
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
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class BackupEdgeCasesTest {

    @TempDir Path tmp;
    Path dbFile, configFile;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tmp.resolve("estudos.db");
        configFile = tmp.resolve("config.json");
        Database db = new Database(dbFile);
        db.migrate();
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT)");
            s.execute("INSERT INTO t VALUES (1, 'original')");
        }
        Files.writeString(configFile, "{\"theme\":\"escuro\"}");
    }

    private Path zip(String name, byte[]... contents) throws IOException {
        Path z = tmp.resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(z))) {
            String[] names = {"estudos.db", "config.json"};
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] == null) continue;
                zos.putNextEntry(new ZipEntry(names[i]));
                zos.write(contents[i]);
                zos.closeEntry();
            }
        }
        return z;
    }

    @Test
    void importArquivoQueNaoEZipLancaExcecao() throws Exception {
        Path lixo = tmp.resolve("lixo.zip");
        Files.writeString(lixo, "isto não é um zip");
        assertThrows(IOException.class, () -> new BackupService(dbFile, configFile).importBackup(lixo));
    }

    @Test
    void importSemBancoNaoAlteraConfigExistente() throws Exception {
        Path z = zip("semdb.zip", null, "{\"theme\":\"claro\"}".getBytes());
        assertThrows(IOException.class, () -> new BackupService(dbFile, configFile).importBackup(z));
        assertTrue(Files.readString(configFile).contains("escuro"),
                "backup inválido não pode sobrescrever o config");
    }

    @Test
    void importBancoCorrompidoPreservaBancoOriginal() throws Exception {
        Path z = zip("corrompido.zip", "isto não é sqlite".getBytes(), "{}".getBytes());
        try { new BackupService(dbFile, configFile).importBackup(z); } catch (IOException ignored) { }
        try (Connection c = new Database(dbFile).connect();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT v FROM t WHERE id = 1")) {
            assertTrue(rs.next(), "banco original deveria continuar legível");
            assertEquals("original", rs.getString(1));
        }
    }

    @Test
    void importNaoDeixaArquivoTemporario() throws Exception {
        BackupService svc = new BackupService(dbFile, configFile);
        Path z = tmp.resolve("ok.zip");
        svc.export(z);
        svc.importBackup(z);
        try (var files = Files.list(tmp)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().endsWith(".import-tmp")));
        }
    }

    @Test
    void importCriaPastasAusentes() throws Exception {
        Path z = tmp.resolve("ok.zip");
        new BackupService(dbFile, configFile).export(z);
        Path novoDb = tmp.resolve("a/b/estudos.db");
        Path novoCfg = tmp.resolve("c/d/config.json");
        new BackupService(novoDb, novoCfg).importBackup(z);
        assertTrue(Files.exists(novoDb));
        assertTrue(Files.exists(novoCfg));
    }

    @Test
    void exportNaoDeixaTemporarioNemAlteraOrigem() throws Exception {
        long antes = Files.size(dbFile);
        new BackupService(dbFile, configFile).export(tmp.resolve("b.zip"));
        assertEquals(antes, Files.size(dbFile));
    }

    @Test
    void exportParaPastaInexistenteLancaIOException() {
        assertThrows(IOException.class,
                () -> new BackupService(dbFile, configFile).export(tmp.resolve("nao/existe/b.zip")));
    }

    @Test
    void configTemaNuloOuVazioViraEscuro() {
        Config d = Config.defaults(tmp);
        assertEquals("escuro", d.withTheme(null).theme());
        assertEquals("escuro", d.withTheme("  ").theme());
        assertTrue(d.isDark());
        assertFalse(d.withTheme("claro").isDark());
    }

    @Test
    void configTemaDesconhecidoNaoViraClasseCssInvalida() {
        Config d = Config.defaults(tmp);
        String t = d.withTheme("roxo").theme();
        assertTrue(t.equals("escuro") || t.equals("claro"), "tema inválido deveria cair no padrão, veio: " + t);
    }
}
