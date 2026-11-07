package br.estudos.app.lessons;

import br.estudos.app.ai.AiException;
import br.estudos.app.ai.AiService;
import br.estudos.app.config.Config;
import br.estudos.app.ports.AiRunner;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.*;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.lessons.JdbcLessonRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LessonServiceTest {
    @TempDir Path tmp;
    Path repoDir;
    TaskQueue queue;
    LessonService svc;
    volatile boolean fail;

    /** Runner falso que, como a skill, grava um PDF e um .md no diretório de trabalho. */
    class FilesRunner implements AiRunner {
        public AiRunnerKind kind() { return AiRunnerKind.CLAUDE; }
        public AiResult run(AiRequest req, CancellationToken t) throws AiException {
            if (fail) throw new AiException("boom");
            try {
                Files.createDirectories(repoDir.resolve("saida"));
                Files.writeString(repoDir.resolve("saida/grupos.pdf"), "%PDF");
                Files.writeString(repoDir.resolve("saida/grupos.md"), "# grupos");
            } catch (Exception e) { throw new AiException(e.getMessage()); }
            return new AiResult("ok " + req.prompt(), 1L, 1L, 0.0);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        repoDir = tmp.resolve("estudos");
        Files.createDirectories(repoDir);
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        queue = new TaskQueue(Clock.systemUTC());
        AiService ai = new AiService(List.of(new FilesRunner()), new JdbcAiCallRepository(db), queue,
                Clock.systemUTC(), () -> 100);
        Config cfg = Config.defaults(tmp).withDefaultsFrom(Config.defaults(tmp));
        cfg = new Config(repoDir.toString(), cfg.dbPath(), "claude", "agy", "gcc", "g++", "", cfg.models(), 100);
        Config c = cfg;
        svc = new LessonService(ai, new JdbcLessonRepository(db), () -> c, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() { queue.close(); }

    @Test
    void geraEMoveArquivos() {
        Lesson l = svc.generate(LessonService.KIND_MATH, "Teoria dos grupos", "subgrupos, Lagrange", s -> true).join();
        assertEquals(LessonStatus.PRONTA, l.status());
        Path dir = repoDir.resolve("aulas/matematica/teoria-dos-grupos");
        assertEquals(dir.resolve("grupos.pdf").toString(), l.pdfPath());
        assertEquals(dir.resolve("grupos.md").toString(), l.mdPath());
        assertTrue(Files.exists(dir.resolve("grupos.pdf")));
        assertFalse(Files.exists(repoDir.resolve("saida/grupos.pdf")));
        var listed = svc.list("grupos", "matematica");
        assertEquals(1, listed.size());
        assertEquals(l.pdfPath(), listed.get(0).pdfPath());
        assertEquals(LessonStatus.PRONTA, listed.get(0).status());
        assertTrue(svc.list("xadrez", null).isEmpty());
    }

    @Test
    void falhaFicaRegistradaComLog() {
        fail = true;
        Lesson l = svc.generate(LessonService.KIND_PROG, "Dijkstra", "", s -> true).join();
        assertEquals(LessonStatus.FALHOU, l.status());
        assertTrue(l.log().contains("boom"));
        assertEquals(LessonStatus.FALHOU, svc.list(null, "programacao").get(0).status());
    }

    @Test
    void avisaTituloParecido() {
        svc.generate(LessonService.KIND_MATH, "Teoria dos grupos", null, s -> true).join();
        assertEquals(1, svc.similar("Grupos: teoria").size());
        assertTrue(svc.similar("Árvores binárias").isEmpty());
    }

    @Test
    void comandoDaSkill() {
        assertEquals("aula_matematica Teoria dos grupos: subgrupos, Lagrange",
                LessonService.command("aula_matematica", "Teoria dos grupos", "subgrupos, Lagrange"));
    }
}
