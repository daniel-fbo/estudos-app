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

import static br.estudos.app.calendar.CalendarTest.NOW;
import static br.estudos.app.calendar.CalendarTest.slot;
import static br.estudos.app.calendar.CalendarTest.total;
import static org.junit.jupiter.api.Assertions.*;

/** Casos de borda do M8 (revisão). */
class CalendarBordaTest {

    @Test void argumentosInvalidosLancam() {
        var n = List.of(new Need(1, "a", Area.CP, 60));
        assertThrows(IllegalArgumentException.class, () -> BlockPlanner.plan(n, List.of(), NOW, 0, 60));
        assertThrows(IllegalArgumentException.class, () -> BlockPlanner.plan(n, List.of(), NOW, 60, 30));
    }

    @Test void horarioMenorQueBlocoMinimoEhIgnorado() {
        var out = BlockPlanner.plan(List.of(new Need(1, "a", Area.CP, 120)),
                List.of(new FreeSlot(NOW.plusHours(1), NOW.plusHours(1).plusMinutes(20))), NOW, 30, 90);
        assertTrue(out.isEmpty());
    }

    @Test void horarioParcialmenteNoPassadoEhCortadoEmNow() {
        var out = BlockPlanner.plan(List.of(new Need(1, "a", Area.CP, 600)),
                List.of(new FreeSlot(NOW.minusHours(1), NOW.plusHours(1))), NOW, 30, 90);
        assertEquals(1, out.size());
        assertEquals(NOW, out.get(0).start());
        assertEquals(60, total(out, 1));
    }

    @Test void horarioLongoEhQuebradoEmBlocosDeNoMaximoMax() {
        var out = BlockPlanner.plan(List.of(new Need(1, "a", Area.CP, 600)),
                List.of(slot(0, 9, 14)), NOW, 30, 90); // 300 min
        assertEquals(300, total(out, 1));
        assertTrue(out.stream().allMatch(b -> Duration.between(b.start(), b.end()).toMinutes() <= 90));
        for (int i = 1; i < out.size(); i++) assertFalse(out.get(i).start().isBefore(out.get(i - 1).end()));
    }

    @Test void restoMenorQueMinimoNaoViraBloco() {
        // 100 min: 90 + sobra de 10 (< 30) descartada
        var out = BlockPlanner.plan(List.of(new Need(1, "a", Area.CP, 600)),
                List.of(new FreeSlot(NOW.plusHours(1), NOW.plusHours(1).plusMinutes(100))), NOW, 30, 90);
        assertEquals(90, total(out, 1));
    }

    @Test void necessidadesNaoPositivasSaoIgnoradasEDuplicadasSomam() {
        var out = BlockPlanner.plan(List.of(new Need(1, "a", Area.CP, 0), new Need(2, "b", Area.CP, -5),
                        new Need(3, "c", Area.CP, 30), new Need(3, "c", Area.CP, 30)),
                List.of(slot(0, 9, 12)), NOW, 30, 90);
        assertEquals(60, total(out, 3));
        assertEquals(0, total(out, 1) + total(out, 2));
    }

    @Test void saidaOrdenadaPorInicio() {
        var out = BlockPlanner.plan(List.of(new Need(1, "a", Area.CP, 120), new Need(2, "b", Area.OUTRO, 60)),
                List.of(slot(1, 9, 11), slot(0, 9, 11), slot(2, 9, 11)), NOW, 30, 60);
        for (int i = 1; i < out.size(); i++) assertFalse(out.get(i).start().isBefore(out.get(i - 1).start()));
    }

    static class Fake implements CalendarClient {
        final List<String> created = new ArrayList<>();
        int failAt = -1;
        public boolean authorized() { return true; }
        public void authorize() {}
        public String createEvent(String t, String d, Instant s, Instant e) throws CalendarException {
            if (created.size() == failAt) throw new CalendarException("falhou");
            created.add(t);
            return "ev" + created.size();
        }
    }

    record Env(CalendarService svc, StudyService study, JdbcCalendarRepository repo, Fake fake, JdbcStudyRepository srepo) {}

    Env env(Path tmp, Clock clock) throws Exception {
        Database db = new Database(tmp.resolve("t.db"));
        db.migrate();
        var srepo = new JdbcStudyRepository(db);
        var study = new StudyService(srepo, clock);
        var repo = new JdbcCalendarRepository(db);
        var fake = new Fake();
        return new Env(new CalendarService(repo, fake, study, clock), study, repo, fake, srepo);
    }

    Clock clock() { return Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()); }

    @Test void falhaNoGoogleMantemSoOsJaCriados(@TempDir Path tmp) throws Exception {
        var e = env(tmp, clock());
        Goal g = e.study().createGoal("CP", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 3);
        var plan = e.svc().plan(List.of(slot(0, 9, 12)));
        assertTrue(plan.size() >= 2);
        e.fake().failAt = 1;
        assertThrows(CalendarClient.CalendarException.class, () -> e.svc().approve(plan));
        assertEquals(1, e.svc().week(NOW.toLocalDate()).size());
    }

    @Test void marcarBlocoInexistenteLanca(@TempDir Path tmp) throws Exception {
        var e = env(tmp, clock());
        assertThrows(IllegalArgumentException.class, () -> e.svc().markDone(999));
    }

    @Test void semMetasDeHorasNaoHaNecessidade(@TempDir Path tmp) throws Exception {
        var e = env(tmp, clock());
        e.study().createGoal("Problemas", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 5);
        assertTrue(e.svc().needs().isEmpty());
        assertTrue(e.svc().plan(List.of(slot(0, 9, 12))).isEmpty());
    }

    @Test void metaMensalEhDivididaPelasSemanasRestantes(@TempDir Path tmp) throws Exception {
        var e = env(tmp, clock()); // 2 nov: restam 29 dias até 1 dez => 5 semanas
        e.study().createGoal("Mat", Area.MATEMATICA, GoalKind.HORAS, GoalPeriod.MENSAL, 10);
        assertEquals(120, e.svc().needs().get(0).minutes()); // 10h / 5 = 2h
    }

    @Test void metaJaCumpridaNaoGeraNecessidade(@TempDir Path tmp) throws Exception {
        var e = env(tmp, clock());
        e.study().createGoal("CP", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 1);
        Instant s = NOW.atZone(ZoneId.systemDefault()).toInstant().minusSeconds(7200);
        e.study().logSession(Area.CP, "x", s, s.plusSeconds(7200), null);
        assertTrue(e.svc().needs().isEmpty());
    }

    @Test void semanaUsaIntervaloSemiAberto(@TempDir Path tmp) throws Exception {
        var e = env(tmp, clock());
        long gid = e.study().createGoal("CP", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 20).id();
        LocalDate seg = NOW.toLocalDate();
        e.svc().approve(List.of(
                new PlannedBlock(gid, "x", Area.CP, seg.atTime(9, 0), seg.atTime(10, 0)),
                new PlannedBlock(gid, "y", Area.CP, seg.plusWeeks(1).atStartOfDay(), seg.plusWeeks(1).atTime(1, 0))));
        assertEquals(1, e.svc().week(seg).size());
        assertEquals(1, e.svc().week(seg.plusWeeks(1)).size());
    }

    @Test void logSessionRejeitaIntervaloInvertido(@TempDir Path tmp) throws Exception {
        var e = env(tmp, clock());
        Instant t = Instant.parse("2026-11-02T10:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> e.study().logSession(Area.CP, "x", t, t.minusSeconds(1), null));
        assertThrows(IllegalArgumentException.class, () -> e.study().logSession(null, "x", t, t, null));
    }

    @Test void blocoSobrevivePerdaDaMeta(@TempDir Path tmp) throws Exception {
        var e = env(tmp, clock());
        Goal g = e.study().createGoal("CP", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 3);
        var saved = e.svc().approve(e.svc().plan(List.of(slot(0, 9, 10))));
        assertFalse(saved.isEmpty());
        // Ao ler, o bloco continua listável mesmo com meta sem título/área (LEFT JOIN).
        assertEquals(saved.size(), e.svc().week(NOW.toLocalDate()).size());
    }

    /** Falha hoje: planejar de novo não desconta blocos já aprovados e ainda não feitos (eventos duplicados). */
    @Test void replanejarNaoDuplicaBlocosAprovados(@TempDir Path tmp) throws Exception {
        var e = env(tmp, clock());
        Goal g = e.study().createGoal("CP", Area.CP, GoalKind.HORAS, GoalPeriod.SEMANAL, 3);
        e.svc().approve(e.svc().plan(List.of(slot(0, 9, 12))));
        var second = e.svc().plan(List.of(slot(1, 9, 12)));
        assertTrue(second.isEmpty());
    }
}
