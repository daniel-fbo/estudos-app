package br.estudos.infra.study;

import br.estudos.app.ai.AiService;
import br.estudos.app.cf.CfSubmission;
import br.estudos.app.config.Config;
import br.estudos.app.library.LibraryService;
import br.estudos.app.study.StudyService;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.*;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.cf.JdbcCfRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.library.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GoalsP1Test {
    @TempDir Path dir;
    Database db;
    StudyServiceTest.MutableClock clock;
    StudyService svc;

    @BeforeEach
    void up() throws Exception {
        db = new Database(dir.resolve("p1.db"));
        db.migrate();
        clock = new StudyServiceTest.MutableClock(Instant.parse("2026-11-04T10:00:00Z")); // quarta
        svc = new StudyService(new JdbcStudyRepository(db), clock);
    }

    double cur(int i) { return svc.dashboard().goals().get(i).current(); }

    void sql(String q, Object... a) throws Exception {
        try (var c = db.connect(); var ps = c.prepareStatement(q)) {
            for (int i = 0; i < a.length; i++) ps.setObject(i + 1, a[i]);
            ps.executeUpdate();
        }
    }

    @Test
    void editValidatesAndPersists() {
        Goal g = svc.createGoal("a", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 3);
        Goal e = svc.editGoal(g, " novo ", 7.5, GoalSource.CODEFORCES);
        assertEquals("novo", svc.goals().get(0).title());
        assertEquals(7.5, svc.goals().get(0).target());
        assertEquals(GoalSource.CODEFORCES, svc.goals().get(0).source());
        assertThrows(IllegalArgumentException.class, () -> svc.editGoal(e, " ", 1, GoalSource.NENHUMA));
        assertThrows(IllegalArgumentException.class, () -> svc.editGoal(e, "x", 0, GoalSource.NENHUMA));
        assertThrows(IllegalArgumentException.class, () -> svc.editGoal(e, "x", Double.NaN, GoalSource.NENHUMA));
        assertThrows(IllegalArgumentException.class, () -> svc.editGoal(e, "x", 1, null));
        Goal m = svc.createGoal("m", Area.MATEMATICA, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 3);
        assertThrows(IllegalArgumentException.class, () -> svc.editGoal(m, "m", 3, GoalSource.RUNNER));
        Goal h = svc.createGoal("h", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 3);
        assertThrows(IllegalArgumentException.class, () -> svc.editGoal(h, "h", 3, GoalSource.PAGINAS));
        assertThrows(IllegalArgumentException.class,
                () -> svc.createGoal("m", Area.MATEMATICA, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 3, GoalSource.CODEFORCES));
    }

    @Test
    void openSessionRecovery() {
        svc.start(Area.CP, "dp");
        assertTrue(svc.dashboard().lastSessions().isEmpty()); // aberta não conta
        assertEquals(0, svc.dashboard().weekSecondsByArea().getOrDefault(Area.CP, 0L));
        // "reabre o app": novo serviço sobre o mesmo banco
        StudyService s2 = new StudyService(new JdbcStudyRepository(db), clock);
        StudySession open = s2.openSession().orElseThrow();
        assertEquals(Area.CP, open.area());
        assertNull(open.endedAt());

        clock.now = clock.now.plusSeconds(1200);
        s2.resumeOpen(open);
        assertTrue(s2.running());
        assertEquals(1200, s2.elapsedSeconds());
        clock.now = clock.now.plusSeconds(600);
        assertEquals(1800, s2.stop(null).durationSeconds());
        assertTrue(s2.openSession().isEmpty());
        assertEquals(1, s2.dashboard().lastSessions().size());

        // encerrar agora
        s2.start(Area.XADREZ, null);
        StudyService s3 = new StudyService(new JdbcStudyRepository(db), clock);
        clock.now = clock.now.plusSeconds(300);
        StudySession o3 = s3.openSession().orElseThrow();
        assertEquals(300, s3.endOpen(o3, "fim").durationSeconds());
        assertTrue(s3.openSession().isEmpty());
        assertEquals(2, s3.dashboard().lastSessions().size());

        // descartar
        s3.start(Area.MUSICA, null);
        StudyService s4 = new StudyService(new JdbcStudyRepository(db), clock);
        s4.discardOpen(s4.openSession().orElseThrow());
        assertTrue(s4.openSession().isEmpty());
        assertEquals(2, s4.dashboard().lastSessions().size());
        // discard() em memória também apaga a linha
        s4.start(Area.MUSICA, null);
        s4.discard();
        assertTrue(s4.openSession().isEmpty());
    }

    @Test
    void manualCounterResetsEachWeekAndMonth() {
        Goal w = svc.createGoal("w", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 5);
        svc.createGoal("m", Area.CP, GoalKind.CONTAGEM, GoalPeriod.MENSAL, 5);
        svc.adjustCount(w, 1); svc.adjustCount(w, 1); svc.adjustCount(w, 1);
        svc.adjustCount(svc.goals().get(1), 1);
        assertEquals(3, cur(0));
        assertEquals(1, cur(1));
        svc.adjustCount(w, -10); // limitado a zerar o período
        assertEquals(0, cur(0));
        svc.adjustCount(w, 2);
        clock.now = Instant.parse("2026-11-08T23:00:00Z"); // domingo, mesma semana
        assertEquals(2, cur(0));
        clock.now = Instant.parse("2026-11-09T00:30:00Z"); // segunda: semana nova, mês igual
        assertEquals(0, cur(0));
        assertEquals(1, cur(1));
        svc.adjustCount(w, -1); // nada a descontar no período novo
        assertEquals(0, cur(0));
        clock.now = Instant.parse("2026-12-01T00:30:00Z");
        assertEquals(0, cur(1));
    }

    @Test
    void sourcesCountOnlyInPeriodAndRightGoals() throws Exception {
        svc.createGoal("run", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 9, GoalSource.RUNNER);
        svc.createGoal("cf", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 9, GoalSource.CODEFORCES);
        svc.createGoal("pg", Area.MATEMATICA, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 9, GoalSource.PAGINAS);
        svc.createGoal("none", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 9);

        // runner: a.c primeiro AC na semana passada (não conta, mesmo repetido hoje); b.c esta semana
        for (String[] r : new String[][]{{"a.c", "2026-10-30T10:00:00Z"}, {"a.c", "2026-11-04T09:00:00Z"},
                {"b.c", "2026-11-03T09:00:00Z"}, {"c.c", "2026-11-03T09:00:00Z", "WA"}})
            sql("INSERT INTO code_run(source_path,ran_at,verdict,passed,total) VALUES (?,?,?,1,1)",
                    r[0], r[1], r.length > 2 ? r[2] : "AC");

        // Codeforces: 1/A primeiro OK na semana passada; 1/B OK 2x esta semana (1); 2/A só WA; 3/A OK hoje
        var cf = new JdbcCfRepository(db);
        cf.upsertSubmissions(List.of(
                new CfSubmission(1, 1, "A", "p", null, null, "OK", "2026-10-30T10:00:00Z"),
                new CfSubmission(2, 1, "A", "p", null, null, "OK", "2026-11-04T10:00:00Z"),
                new CfSubmission(3, 1, "B", "p", null, null, "OK", "2026-11-02T10:00:00Z"),
                new CfSubmission(4, 1, "B", "p", null, null, "OK", "2026-11-04T10:00:00Z"),
                new CfSubmission(5, 2, "A", "p", null, null, "WRONG_ANSWER", "2026-11-04T10:00:00Z"),
                new CfSubmission(6, 3, "A", "p", null, null, "OK", "2026-11-04T08:00:00Z")));

        assertEquals(1, cur(0));
        assertEquals(2, cur(1));
        assertEquals(0, cur(2));
        assertEquals(0, cur(3));
    }

    @Test
    void pageAdvanceIsLoggedAndRegressionIgnored() throws Exception {
        var books = new JdbcBookRepository(db);
        Book mat = books.insert(new Book(0, "M", null, Area.MATEMATICA, BookKind.LIVRO, null, 500, 0, BookStatus.QUERO_LER, null, null, null));
        Book cp = books.insert(new Book(0, "C", null, Area.CP, BookKind.LIVRO, null, 500, 0, BookStatus.QUERO_LER, null, null, null));
        TaskQueue q = new TaskQueue(Clock.systemUTC());
        var index = new LucenePageIndex(dir.resolve("lucene"));
        try {
            AiService ai = new AiService(List.of(), new JdbcAiCallRepository(db), q, Clock.systemUTC(), () -> 100);
            LibraryService lib = new LibraryService(books, new PdfBoxReader(), index, ai, () -> Config.defaults(dir), clock);
            svc.createGoal("pg", Area.MATEMATICA, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 100, GoalSource.PAGINAS);
            Book b = lib.updateProgress(mat, 30);
            b = lib.updateProgress(b, 20);  // retrocesso: não desconta
            b = lib.updateProgress(b, 50);  // +30
            lib.updateProgress(cp, 40);     // outra área: não conta
            assertEquals(60, cur(0));
            clock.now = Instant.parse("2026-11-09T10:00:00Z"); // semana nova
            assertEquals(0, cur(0));
            lib.updateProgress(b, 55);
            assertEquals(5, cur(0));
        } finally { q.close(); index.close(); }
    }
}
