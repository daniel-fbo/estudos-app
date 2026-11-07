package br.estudos.infra.study;

import br.estudos.app.study.StudyService;
import br.estudos.domain.*;
import br.estudos.infra.db.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class StudyServiceTest {
    @TempDir Path dir;

    /** Relógio mutável. */
    static class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant i) { now = i; }
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    @Test
    void timerGoalsAndDashboardRoundTrip() throws Exception {
        Database db = new Database(dir.resolve("e.db"));
        db.migrate();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-06T10:00:00Z"));
        StudyService svc = new StudyService(new JdbcStudyRepository(db), clock);

        svc.start(Area.CP, "dp");
        clock.now = clock.now.plusSeconds(1800);
        svc.pause();
        clock.now = clock.now.plusSeconds(600); // pausa não conta
        assertEquals(1800, svc.elapsedSeconds());
        svc.resume();
        clock.now = clock.now.plusSeconds(1800);
        StudySession s = svc.stop("ok");
        assertEquals(3600, s.durationSeconds());
        assertFalse(svc.running());

        Goal h = svc.createGoal("2h CP", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 2);
        Goal c = svc.createGoal("5 problemas", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 5);
        svc.adjustCount(c, 1);
        svc.adjustCount(svc.goals().get(1), 1);
        svc.adjustCount(svc.goals().get(1), -5); // não fica negativo

        var d = svc.dashboard();
        assertEquals(1, d.streak());
        assertEquals(3600L, d.weekSecondsByArea().get(Area.CP));
        assertEquals(1, d.lastSessions().size());
        assertEquals(0.5, d.goals().get(0).fraction(), 1e-9);
        assertEquals(0, d.goals().get(1).current());

        svc.deleteGoal(h.id());
        assertEquals(1, svc.goals().size());
        assertThrows(IllegalArgumentException.class, () -> svc.createGoal(" ", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 1));
    }

    @Test
    void timerLifecycleEdgeCases() throws Exception {
        Database db = new Database(dir.resolve("timer.db"));
        db.migrate();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-06T08:00:00Z"));
        StudyService svc = new StudyService(new JdbcStudyRepository(db), clock);

        // Não permite parar sem sessão
        assertThrows(IllegalStateException.class, () -> svc.stop(null));

        // Inicia sessão
        svc.start(Area.MATEMATICA, "  álgebra linear  ");
        assertTrue(svc.running());
        assertFalse(svc.paused());

        // Não permite iniciar outra em paralelo
        assertThrows(IllegalStateException.class, () -> svc.start(Area.CP, "outro"));

        // Ciclos de pausa e retomada
        clock.now = clock.now.plusSeconds(600); // 10 min
        svc.pause();
        assertTrue(svc.paused());
        svc.pause(); // pausa repetida não altera estado
        clock.now = clock.now.plusSeconds(300); // 5 min de pausa
        assertEquals(600, svc.elapsedSeconds());
        svc.resume();
        assertFalse(svc.paused());
        svc.resume(); // retomada repetida é no-op

        clock.now = clock.now.plusSeconds(600); // mais 10 min (total 20 min estudados)
        assertEquals(1200, svc.elapsedSeconds());

        // Pausa e encerra enquanto pausado
        svc.pause();
        clock.now = clock.now.plusSeconds(600); // 10 min pausado
        assertEquals(1200, svc.elapsedSeconds());
        StudySession s = svc.stop("   "); // nota só com espaços deve virar null

        assertEquals(1200, s.durationSeconds());
        assertEquals("álgebra linear", s.subject());
        assertNull(s.note());
        assertFalse(svc.running());
        assertFalse(svc.paused());
    }

    @Test
    void discardSessionCancelsWithoutSaving() throws Exception {
        Database db = new Database(dir.resolve("discard.db"));
        db.migrate();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-06T08:00:00Z"));
        StudyService svc = new StudyService(new JdbcStudyRepository(db), clock);

        svc.start(Area.XADREZ, "táticas");
        clock.now = clock.now.plusSeconds(900);
        assertEquals(900, svc.elapsedSeconds());

        svc.discard();
        assertFalse(svc.running());
        assertEquals(0, svc.elapsedSeconds());

        // Nada foi salvo
        assertTrue(svc.dashboard().lastSessions().isEmpty());

        // Pode iniciar nova sessão normalmente
        svc.start(Area.XADREZ, "final");
        assertTrue(svc.running());
    }

    @Test
    void goalValidationEdgeCases() throws Exception {
        Database db = new Database(dir.resolve("goals.db"));
        db.migrate();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-06T08:00:00Z"));
        StudyService svc = new StudyService(new JdbcStudyRepository(db), clock);

        assertThrows(IllegalArgumentException.class, () -> svc.createGoal(null, Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 1));
        assertThrows(IllegalArgumentException.class, () -> svc.createGoal("", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 1));
        assertThrows(IllegalArgumentException.class, () -> svc.createGoal("   ", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 1));
        assertThrows(IllegalArgumentException.class, () -> svc.createGoal("ok", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 0));
        assertThrows(IllegalArgumentException.class, () -> svc.createGoal("ok", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, -5));
    }

    @Test
    void dashboardEmptyDatabase() throws Exception {
        Database db = new Database(dir.resolve("empty.db"));
        db.migrate();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-06T08:00:00Z"));
        StudyService svc = new StudyService(new JdbcStudyRepository(db), clock);

        var d = svc.dashboard();
        assertEquals(0, d.streak());
        assertTrue(d.weekSecondsByArea().isEmpty());
        assertTrue(d.goals().isEmpty());
        assertTrue(d.lastSessions().isEmpty());
    }

    @Test
    void rejectsNanInfiniteAndNullEnums() throws Exception {
        Database db = new Database(dir.resolve("val.db"));
        db.migrate();
        StudyService svc = new StudyService(new JdbcStudyRepository(db), new MutableClock(Instant.parse("2026-11-06T08:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> svc.createGoal("x", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> svc.createGoal("x", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> svc.createGoal("x", null, GoalKind.HORAS, GoalPeriod.SEMANAL, 1));
        assertThrows(IllegalArgumentException.class, () -> svc.start(null, "x"));
    }
}
