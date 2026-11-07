package br.estudos.infra.config;

import br.estudos.app.config.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonConfigStoreTest {

    @TempDir Path dir;

    @Test
    void createsDefaultsWhenMissing() throws Exception {
        Path file = dir.resolve("sub/config.json");
        JsonConfigStore store = new JsonConfigStore(file, Config.defaults(dir));

        Config c = store.load();

        assertTrue(Files.exists(file));
        assertEquals(dir.resolve("estudos.db").toString(), c.dbPath());
        assertEquals("opus", c.model(Config.MODEL_LESSONS));
        assertEquals("sonnet", c.model("desconhecida"));
        assertEquals(30, c.dailyAiBudget());
    }

    @Test
    void savesAndReloads() throws Exception {
        Path file = dir.resolve("config.json");
        JsonConfigStore store = new JsonConfigStore(file, Config.defaults(dir));
        Config edited = new Config("/repo", "/db.sqlite", "/bin/claude", "agy", "gcc-14", "g++-14",
                "tourist", Map.of("aulas", "fable", "padrao", "sonnet", "barato", "haiku"), 7, "claro");

        store.save(edited);
        Config reloaded = new JsonConfigStore(file, Config.defaults(dir)).load();

        assertEquals(edited, reloaded);
    }

    @Test
    void fillsMissingFieldsAndIgnoresUnknownOnes() throws Exception {
        Path file = dir.resolve("config.json");
        Files.writeString(file, """
                {"codeforcesHandle":"filipe","models":{"aulas":"opus"},"campoVelho":1}""");

        Config c = new JsonConfigStore(file, Config.defaults(dir)).load();

        assertEquals("filipe", c.codeforcesHandle());
        assertEquals("claude", c.claudePath());
        assertEquals("haiku", c.model(Config.MODEL_CHEAP));
        assertEquals(30, c.dailyAiBudget());
    }
}
