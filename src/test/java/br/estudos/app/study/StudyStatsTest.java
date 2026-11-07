package br.estudos.app.study;

import br.estudos.domain.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StudyStatsTest {
    static final ZoneId Z = ZoneId.of("UTC");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 6); // sexta

    static StudySession ses(Area a, LocalDate d, long secs) {
        return new StudySession(1, a, null, d.atTime(10, 0).atZone(Z).toInstant(),
                d.atTime(11, 0).atZone(Z).toInstant(), secs, null);
    }

    static Goal goal(GoalKind k, GoalPeriod p, double t, Area a, int manual) {
        return new Goal(1, "g", a, k, p, t, manual, 0, true, Instant.EPOCH);
    }

    @Test
    void streakEmpty() {
        assertEquals(0, StudyStats.streak(List.of(), TODAY));
    }

    @Test
    void streakIncludingToday() {
        assertEquals(3, StudyStats.streak(List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2)), TODAY));
    }

    @Test
    void streakKeepsIfOnlyTodayMissing() {
        assertEquals(2, StudyStats.streak(List.of(TODAY.minusDays(1), TODAY.minusDays(2)), TODAY));
    }

    @Test
    void streakBrokenByGapDay() {
        assertEquals(0, StudyStats.streak(List.of(TODAY.minusDays(2), TODAY.minusDays(3)), TODAY));
        assertEquals(1, StudyStats.streak(List.of(TODAY, TODAY.minusDays(2)), TODAY));
    }

    @Test
    void streakCrossesWeekAndMonthBoundary() {
        LocalDate mon = LocalDate.of(2026, 11, 2);
        List<LocalDate> days = List.of(mon, mon.minusDays(1), mon.minusDays(2), mon.minusDays(3)); // dom, sáb, sex (mês anterior)
        assertEquals(4, StudyStats.streak(days, mon));
    }

    @Test
    void streakIgnoresDuplicates() {
        assertEquals(1, StudyStats.streak(List.of(TODAY, TODAY, TODAY), TODAY));
    }

    @Test
    void weekStartsOnMonday() {
        assertEquals(LocalDate.of(2026, 11, 2), StudyStats.periodStart(GoalPeriod.SEMANAL, TODAY));
        assertEquals(LocalDate.of(2026, 11, 2), StudyStats.periodStart(GoalPeriod.SEMANAL, LocalDate.of(2026, 11, 2)));
        assertEquals(LocalDate.of(2026, 11, 2), StudyStats.periodStart(GoalPeriod.SEMANAL, LocalDate.of(2026, 11, 8))); // domingo
        assertEquals(LocalDate.of(2026, 11, 1), StudyStats.periodStart(GoalPeriod.MENSAL, TODAY));
        assertEquals(LocalDate.of(2026, 12, 1), StudyStats.periodEnd(GoalPeriod.MENSAL, TODAY));
    }

    @Test
    void secondsByAreaExcludesOtherWeeks() {
        var list = List.of(ses(Area.CP, LocalDate.of(2026, 11, 2), 3600),
                ses(Area.CP, LocalDate.of(2026, 11, 8), 1800),
                ses(Area.CP, LocalDate.of(2026, 11, 1), 9999),  // domingo anterior
                ses(Area.CP, LocalDate.of(2026, 11, 9), 9999),  // segunda seguinte
                ses(Area.XADREZ, TODAY, 600));
        Map<Area, Long> m = StudyStats.secondsByArea(list, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), Z);
        assertEquals(5400L, m.get(Area.CP));
        assertEquals(600L, m.get(Area.XADREZ));
        assertNull(m.get(Area.MATEMATICA));
    }

    @Test
    void hoursGoalProgressUsesOnlyAreaAndPeriod() {
        var list = List.of(ses(Area.CP, TODAY, 7200), ses(Area.MATEMATICA, TODAY, 3600),
                ses(Area.CP, LocalDate.of(2026, 10, 30), 3600)); // semana passada, mas...
        GoalProgress week = StudyStats.progress(goal(GoalKind.HORAS, GoalPeriod.SEMANAL, 4, Area.CP, 0), list, TODAY, Z);
        assertEquals(2.0, week.current(), 1e-9);
        assertEquals(0.5, week.fraction(), 1e-9);
        assertFalse(week.done());
        GoalProgress month = StudyStats.progress(goal(GoalKind.HORAS, GoalPeriod.MENSAL, 2, Area.CP, 0), list, TODAY, Z);
        assertEquals(2.0, month.current(), 1e-9); // 30/out fica fora do mês
        assertTrue(month.done());
    }

    @Test
    void countGoalUsesCountersAndCapsFraction() {
        GoalProgress p = StudyStats.progress(goal(GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 5, Area.CP, 7), List.of(), TODAY, Z);
        assertEquals(7, p.current());
        assertEquals(1.0, p.fraction());
        assertTrue(p.done());
        assertEquals(0.4, StudyStats.progress(goal(GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 5, Area.CP, 2), List.of(), TODAY, Z).fraction(), 1e-9);
    }
}
