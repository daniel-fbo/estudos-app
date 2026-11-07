package br.estudos.infra.config;

import br.estudos.app.config.Config;
import br.estudos.app.ports.ConfigStore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Config em JSON. Grava num arquivo temporário e renomeia, para não corromper o original. */
public class JsonConfigStore implements ConfigStore {

    private static final Logger log = LoggerFactory.getLogger(JsonConfigStore.class);

    private final Path file;
    private final Config defaults;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public JsonConfigStore(Path file, Config defaults) {
        this.file = file;
        this.defaults = defaults;
    }

    @Override
    public Config load() throws IOException {
        if (Files.notExists(file)) {
            log.info("Config não encontrado; criando padrões em {}", file);
            save(defaults);
            return defaults;
        }
        return mapper.readValue(file.toFile(), Config.class).withDefaultsFrom(defaults);
    }

    @Override
    public void save(Config config) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(tmp.toFile(), config);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.info("Config salvo em {}", file);
    }
}
