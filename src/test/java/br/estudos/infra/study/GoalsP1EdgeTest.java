package br.estudos.infra.study;

import br.estudos.app.ai.AiService;
import br.estudos.app.config.Config;
import br.estudos.app.library.LibraryService;
import br.estudos.app.study.Dated;
import br.estudos.app.study.StudyService;
import br.estudos.app.study.StudyStats;
import br.estudos.app.tasks.TaskQueue;
import br.estudos.domain.*;
import br.estudos.infra.ai.JdbcAiCallRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.library.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Casos de borda do P1 (revisão). */
class GoalsP1EdgeTest {
    @TempDir Path dir;
    Database db;
    StudyServiceTest.MutableClock clock;
    StudyService svc;

    @BeforeEach
    void up() throws Exception {
        db = new Database(dir.resolve("edge.db"));
        db.migrate();
        clock = new StudyServiceTest.MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
        svc = new StudyService(new JdbcStudyRepository(db), clock);
    }

    void sql(String q, Object... a) throws Exception {
        try (var c = db.connect(); var ps = c.prepareStatement(q)) {
            for (int i = 0; i < a.length; i++) ps.setObject(i + 1, a[i]);
            ps.executeUpdate();
        }
    }

    long count(String q) throws Exception {
        try (var c = db.connect(); var st = c.createStatement(); var r = st.executeQuery(q)) { r.next(); return r.getLong(1); }
    }

    @Test
    void openSessionIsExcludedFromStreakAndStats() {
        svc.start(Area.CP, null);
        var d = svc.dashboard();
        assertEquals(0, d.streak());
        assertTrue(d.lastSessions().isEmpty());
        assertTrue(d.weekSecondsByArea().isEmpty() || d.weekSecondsByArea().values().stream().allMatch(v -> v == 0));
    }

    @Test
    void endOpenWithClockBeforeStartClampsToZero() {
        svc.start(Area.CP, null);
        StudySession open = svc.openSession().orElse(null);
        // processo atual está rodando: openSession vazio; usa outro serviço
        assertNull(open);
        StudyService s2 = new StudyService(new JdbcStudyRepository(db), clock);
        StudySession o = s2.openSession().orElseThrow();
        clock.now = clock.now.minusSeconds(3600);
        assertEquals(0, s2.endOpen(o, null).durationSeconds());
    }

    @Test
    void mostRecentOpenSessionIsOffered() {
        svc.start(Area.CP, "velha");
        clock.now = clock.now.plusSeconds(3600);
        StudyService s2 = new StudyService(new JdbcStudyRepository(db), clock);
        s2.start(Area.XADREZ, "nova");
        StudyService s3 = new StudyService(new JdbcStudyRepository(db), clock);
        assertEquals("nova", s3.openSession().orElseThrow().subject());
    }

    @Test
    void resumeWhileRunningFails() {
        svc.start(Area.CP, null);
        StudyService s2 = new StudyService(new JdbcStudyRepository(db), clock);
        StudySession o = s2.openSession().orElseThrow();
        svc.stop(null);
        s2.start(Area.MUSICA, null);
        assertThrows(IllegalStateException.class, () -> s2.resumeOpen(o));
    }

    @Test
    void deletingGoalRemovesItsLogsAndIdReuseDoesNotInherit() throws Exception {
        Goal g = svc.createGoal("a", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 5);
        svc.adjustCount(g, 3);
        svc.deleteGoal(g.id());
        assertEquals(0, count("SELECT COUNT(*) FROM goal_count_log"));
        svc.createGoal("b", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 5);
        assertEquals(0, svc.dashboard().goals().get(0).current());
    }

    @Test
    void storedSourceOnHoursGoalIsIgnored() throws Exception {
        Goal h = svc.createGoal("h", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 5);
        sql("UPDATE goal SET auto_count_ac=3 WHERE id=?", h.id());
        assertEquals(GoalSource.NENHUMA, svc.goals().get(0).effectiveSource());
        assertEquals(0, svc.dashboard().goals().get(0).current());
    }

    @Test
    void unknownSourceCodeFallsBackToNone() {
        assertEquals(GoalSource.NENHUMA, GoalSource.of(99));
        assertEquals(GoalSource.NENHUMA, GoalSource.of(-1));
        for (GoalSource s : GoalSource.values()) assertEquals(s, GoalSource.of(s.code()));
    }

    @Test
    void sourceAllowedMatrix() {
        for (Area a : Area.values()) {
            assertTrue(GoalSource.NENHUMA.allowedFor(a, GoalKind.HORAS));
            assertFalse(GoalSource.PAGINAS.allowedFor(a, GoalKind.HORAS));
            assertTrue(GoalSource.PAGINAS.allowedFor(a, GoalKind.CONTAGEM));
            assertEquals(a == Area.CP, GoalSource.RUNNER.allowedFor(a, GoalKind.CONTAGEM));
            assertEquals(a == Area.CP, GoalSource.CODEFORCES.allowedFor(a, GoalKind.CONTAGEM));
        }
    }

    @Test
    void sumInPeriodBoundaries() {
        ZoneId z = ZoneId.of("UTC");
        LocalDate wed = LocalDate.of(2026, 11, 4);
        List<Dated> l = List.of(
                new Dated(Instant.parse("2026-11-01T23:59:59Z"), 100), // domingo anterior
                new Dated(Instant.parse("2026-11-02T00:00:00Z"), 1),   // segunda 00:00 conta
                new Dated(Instant.parse("2026-11-08T23:59:59Z"), 2),   // domingo conta
                new Dated(Instant.parse("2026-11-09T00:00:00Z"), 100)); // próxima segunda
        assertEquals(3, StudyStats.sumInPeriod(l, GoalPeriod.SEMANAL, wed, z));
        List<Dated> m = List.of(
                new Dated(Instant.parse("2026-10-31T23:59:59Z"), 100),
                new Dated(Instant.parse("2026-11-01T00:00:00Z"), 1),
                new Dated(Instant.parse("2026-11-30T23:59:59Z"), 2),
                new Dated(Instant.parse("2026-12-01T00:00:00Z"), 100));
        assertEquals(3, StudyStats.sumInPeriod(m, GoalPeriod.MENSAL, wed, z));
        assertEquals(0, StudyStats.sumInPeriod(List.of(), GoalPeriod.SEMANAL, wed, z));
    }

    @Test
    void adjustCountOnHoursGoalRejected() {
        Goal h = svc.createGoal("h", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 5);
        assertThrows(IllegalArgumentException.class, () -> svc.adjustCount(h, 1));
    }

    @Test
    void zeroDeltaWritesNothing() throws Exception {
        Goal g = svc.createGoal("a", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 5);
        svc.adjustCount(g, 0);
        svc.adjustCount(g, -1); // período vazio
        assertEquals(0, count("SELECT COUNT(*) FROM goal_count_log"));
    }

    @Test
    void sameAndInvalidPagesLogNothing() throws Exception {
        var books = new JdbcBookRepository(db);
        Book b0 = books.insert(new Book(0, "M", null, Area.MATEMATICA, BookKind.LIVRO, null, 100, 0, BookStatus.QUERO_LER, null, null, null));
        TaskQueue q = new TaskQueue(Clock.systemUTC());
        var index = new LucenePageIndex(dir.resolve("lucene"));
        try {
            AiService ai = new AiService(List.of(), new JdbcAiCallRepository(db), q, Clock.systemUTC(), () -> 100);
            LibraryService lib = new LibraryService(books, new PdfBoxReader(), index, ai, () -> Config.defaults(dir), clock);
            Book b = lib.updateProgress(b0, 10);
            lib.updateProgress(b, 10);   // igual
            lib.updateProgress(b, -5);   // negativo → 0
            assertEquals(1, count("SELECT COUNT(*) FROM reading_log"));
            lib.updateProgress(b, 9999); // limitado a 100 → +90
            assertEquals(100, count("SELECT SUM(pages) FROM reading_log"));
            books.delete(b.id());        // cascata
            assertEquals(0, count("SELECT COUNT(*) FROM reading_log"));
        } finally { q.close(); index.close(); }
    }

    @Test
    void cfSubmissionsWithoutContestAreNotMerged() throws Exception {
        svc.createGoal("cf", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 9, GoalSource.CODEFORCES);
        sql("INSERT INTO cf_submission(id,contest_id,problem_index,verdict,submitted_at) VALUES (1,NULL,'A','OK','2026-11-04T08:00:00Z')");
        sql("INSERT INTO cf_submission(id,contest_id,problem_index,verdict,submitted_at) VALUES (2,NULL,'B','OK','2026-11-04T08:00:00Z')");
        assertEquals(2, svc.dashboard().goals().get(0).current());
    }
}
