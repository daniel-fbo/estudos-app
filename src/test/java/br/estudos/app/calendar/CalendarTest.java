package br.estudos.app.calendar;

import br.estudos.app.ports.CalendarClient;
import br.estudos.app.study.StudyService;
import br.estudos.domain.*;
import br.estudos.infra.calendar.JdbcCalendarRepository;
import br.estudos.infra.db.Database;
import br.estudos.infra.study.JdbcStudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalendarTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 8, 0); // segunda
    static FreeSlot slot(int day, int h1, int h2) {
        LocalDate d = NOW.toLocalDate().plusDays(day);
        return new FreeSlot(d.atTime(h1, 0), d.atTime(h2, 0));
    }
    static long total(List<PlannedBlock> l, long gid) {
        return l.stream().filter(b -> b.goalId() == gid).mapToLong(b -> Duration.between(b.start(), b.end()).toMinutes()).sum();
    }

    @Test void distribuiSemExcederNecessidadeNemSobrepor() {
        var needs = List.of(new Need(1, "CP", Area.CP, 180), new Need(2, "Mat", Area.MATEMATICA, 60));
        var out = BlockPlanner.plan(needs, List.of(slot(0, 9, 12), slot(1, 9, 12), slot(2, 9, 12)), NOW, 30, 90);
        assertEquals(180, total(out, 1));
        assertEquals(60, total(out, 2));
        for (var b : out) assertTrue(Duration.between(b.start(), b.end()).toMinutes() <= 90);
        for (int i = 1; i < out.size(); i++) assertFalse(out.get(i).start().isBefore(out.get(i - 1).end()));
    }

    @Test void prefereDiasDiferentesParaAMesmaMeta() {
        var out = BlockPlanner.plan(List.of(new Need(1, "CP", Area.CP, 120)),
                List.of(slot(0, 9, 11), slot(1, 9, 11)), NOW, 30, 60);
        assertEquals(2, out.size());
        assertNotEquals(out.get(0).start().toLocalDate(), out.get(1).start().toLocalDate());
    }

    @Test void paraQuandoOsHorariosAcabamEIgnoraPassado() {
        var out = BlockPlanner.plan(List.of(new Need(1, "CP", Area.CP, 600)),
                List.of(slot(0, 6, 8), slot(0, 9, 10)), NOW, 30, 90);
        assertEquals(60, total(out, 1)); // o de 6h–8h já passou
        assertTrue(out.stream().allMatch(b -> !b.start().isBefore(NOW)));
    }

    @Test void semNecessidadeOuSemHorariosNaoPropoe() {
        assertTrue(BlockPlanner.plan(List.of(), List.of(slot(0, 9, 10)), NOW, 30, 90).isEmpty());
        assertTrue(BlockPlanner.plan(List.of(new Need(1, "x", Area.CP, 60)), List.of(), NOW, 30, 90).isEmpty());
    }

    @Test void metaMaiorPrimeiro() {
        var out = BlockPlanner.plan(List.of(new Need(1, "a", Area.CP, 30), new Need(2, "b", Area.OUTRO, 300)),
                List.of(slot(0, 9, 10)), NOW, 30, 60);
        assertEquals(2, out.get(0).goalId());
    }

    @Test void aprovarEMarcarFeito(@TempDir Path tmp) throws Exception {
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        var study = new StudyService(new JdbcStudyRepository(db), clock);
        Goal g = study.createGoal("Estudar CP", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 3);
        List<String> created = new ArrayList<>();
        CalendarClient fake = new CalendarClient() {
            public boolean authorized() { return true; }
            public void authorize() {}
            public String createEvent(String t, String d, Instant s, Instant e) { created.add(t); return "ev" + created.size(); }
        };
        var svc = new CalendarService(new JdbcCalendarRepository(db), fake, study, clock);

        assertEquals(180, svc.needs().get(0).minutes());
        var plan = svc.plan(List.of(slot(0, 9, 12)));
        assertEquals(180, total(plan, g.id()));
        var saved = svc.approve(plan);
        assertEquals(plan.size(), created.size());
        assertEquals("ev1", saved.get(0).googleEventId());
        assertEquals(plan.size(), svc.week(NOW.toLocalDate()).size());

        svc.markDone(saved.get(0).id());
        svc.markDone(saved.get(0).id()); // idempotente
        var sessions = new JdbcStudyRepository(db).lastSessions(10);
        assertEquals(1, sessions.size());
        assertEquals(Area.CP, sessions.get(0).area());
        assertEquals(Duration.between(plan.get(0).start(), plan.get(0).end()).getSeconds(), sessions.get(0).durationSeconds());
        assertTrue(svc.week(NOW.toLocalDate()).get(0).done());
        assertEquals(180 - Duration.between(plan.get(0).start(), plan.get(0).end()).toMinutes(), svc.needs().isEmpty() ? 0 : svc.needs().get(0).minutes());
    }

    @Test void salvaECarregaGrade(@TempDir Path dir) throws Exception {
        var db = new Database(dir.resolve("g.db"));
        db.migrate();
        var repo = new JdbcCalendarRepository(db);
        assertTrue(repo.loadAvailability().isEmpty());
        repo.saveAvailability(java.util.Set.of(109, 110, 720));
        assertEquals(java.util.Set.of(109, 110, 720), repo.loadAvailability());
        repo.saveAvailability(java.util.Set.of(215));
        assertEquals(java.util.Set.of(215), repo.loadAvailability());
        repo.saveAvailability(java.util.Set.of());
        assertTrue(repo.loadAvailability().isEmpty());
    }
}
