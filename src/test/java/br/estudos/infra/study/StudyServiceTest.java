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
}
