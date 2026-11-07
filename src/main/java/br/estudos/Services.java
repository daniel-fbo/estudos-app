package br.estudos;

import br.estudos.app.config.Config;
import br.estudos.app.ports.ConfigStore;
import br.estudos.app.ports.ProcessRunner;
import br.estudos.app.code.CodeRunnerService;
import br.estudos.infra.code.JdbcCodeRepository;
import br.estudos.app.study.StudyService;
import br.estudos.infra.study.JdbcStudyRepository;
import br.estudos.app.tools.ExecutableChecker;
import br.estudos.infra.config.AppPaths;
import br.estudos.infra.config.JsonConfigStore;
import br.estudos.infra.db.Database;
import br.estudos.infra.process.SystemProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Todas as dependências do app, montadas à mão (sem framework de injeção). */
public final class Services implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Services.class);

    private final Path home;
    private final ConfigStore configStore;
    private volatile Config config;
    private final Database database;
    private final ProcessRunner processRunner;
    private final ExecutableChecker executableChecker;
    private final ExecutorService background;
    private final StudyService studyService;
    private final JdbcCodeRepository codeRepository;

    private Services(Path home, ConfigStore configStore, Config config, Database database,
                     ProcessRunner processRunner) {
        this.home = home;
        this.configStore = configStore;
        this.config = config;
        this.database = database;
        this.processRunner = processRunner;
        this.studyService = new StudyService(new JdbcStudyRepository(database), java.time.Clock.systemDefaultZone());
        this.codeRepository = new JdbcCodeRepository(database);
        this.executableChecker = new ExecutableChecker(processRunner);
        this.background = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "estudos-bg");
            t.setDaemon(true);
            return t;
        });
    }

    public static Services create(Path home) throws IOException, SQLException {
        Files.createDirectories(home);
        ConfigStore store = new JsonConfigStore(home.resolve("config.json"), Config.defaults(home));
        Config config = store.load();
        Database db = new Database(AppPaths.expand(config.dbPath()));
        int applied = db.migrate();
        log.info("Banco {} pronto ({} migrations aplicadas agora)", db.file(), applied);
        return new Services(home, store, config, db, new SystemProcessRunner());
    }

    public Path home() { return home; }
    public Config config() { return config; }
    public Database database() { return database; }
    public ProcessRunner processRunner() { return processRunner; }
    public ExecutableChecker executableChecker() { return executableChecker; }
    public StudyService study() { return studyService; }
    /** Cria com os compiladores da configuração atual. */
    public CodeRunnerService code() {
        return new CodeRunnerService(processRunner, codeRepository, config.gccPath(), config.gppPath());
    }
    public ExecutorService background() { return background; }

    public void saveConfig(Config newConfig) throws IOException {
        configStore.save(newConfig);
        this.config = newConfig;
    }

    @Override
    public void close() {
        background.shutdownNow();
        try {
            background.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
