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
        return new Goal(1, "g", a, k, p, t, manual, false, true, Instant.EPOCH);
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

    @Test
    void streakLeapYear() {
        LocalDate feb28 = LocalDate.of(2024, 2, 28);
        LocalDate feb29 = LocalDate.of(2024, 2, 29);
        LocalDate mar1 = LocalDate.of(2024, 3, 1);
        assertEquals(3, StudyStats.streak(List.of(feb28, feb29, mar1), mar1));
    }

    @Test
    void streakWithFutureDays() {
        LocalDate tomorrow = TODAY.plusDays(1);
        assertEquals(1, StudyStats.streak(List.of(TODAY, tomorrow), TODAY));
    }

    @Test
    void streakSingleDayYesterday() {
        assertEquals(1, StudyStats.streak(List.of(TODAY.minusDays(1)), TODAY));
    }

    @Test
    void periodStartAndEndYearTransition() {
        LocalDate dec31 = LocalDate.of(2026, 12, 31); // quinta
        assertEquals(LocalDate.of(2026, 12, 28), StudyStats.periodStart(GoalPeriod.SEMANAL, dec31));
        assertEquals(LocalDate.of(2027, 1, 4), StudyStats.periodEnd(GoalPeriod.SEMANAL, dec31));
        assertEquals(LocalDate.of(2026, 12, 1), StudyStats.periodStart(GoalPeriod.MENSAL, dec31));
        assertEquals(LocalDate.of(2027, 1, 1), StudyStats.periodEnd(GoalPeriod.MENSAL, dec31));
    }

    @Test
    void secondsByAreaBoundaryAndEmpty() {
        assertTrue(StudyStats.secondsByArea(List.of(), TODAY, TODAY.plusDays(7), Z).isEmpty());

        LocalDate from = LocalDate.of(2026, 11, 2);
        LocalDate to = LocalDate.of(2026, 11, 9);
        var sessions = List.of(
                ses(Area.MATEMATICA, from, 1200), // exatamente no limite inferior (inclusivo)
                ses(Area.MATEMATICA, to, 3600)    // exatamente no limite superior (exclusivo)
        );
        Map<Area, Long> m = StudyStats.secondsByArea(sessions, from, to, Z);
        assertEquals(1200L, m.get(Area.MATEMATICA));
    }

    @Test
    void goalProgressZeroTargetAndAutoCount() {
        Goal zeroTarget = new Goal(1, "zero", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 0, 0, false, true, Instant.EPOCH);
        GoalProgress gpZero = new GoalProgress(zeroTarget, 0, 0);
        assertEquals(0.0, gpZero.fraction());
        assertTrue(gpZero.done());

        Goal countWithAuto = new Goal(2, "ac", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 10, 4, true, true, Instant.EPOCH);
        List<Instant> sixAcs = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> TODAY.atTime(i, 0).atZone(Z).toInstant()).toList();
        GoalProgress gpAuto = StudyStats.progress(countWithAuto, List.of(), sixAcs, TODAY, Z);
        assertEquals(10.0, gpAuto.current());
        assertEquals(1.0, gpAuto.fraction());
        assertTrue(gpAuto.done());
    }

    @Test
    void acsCountOnlyInsidePeriodAndOnlyForFlaggedCpCountGoals() {
        List<Instant> acs = List.of(
                TODAY.atTime(9, 0).atZone(Z).toInstant(),                 // esta semana
                TODAY.minusDays(4).atTime(9, 0).atZone(Z).toInstant(),    // segunda desta semana
                TODAY.minusDays(5).atTime(23, 0).atZone(Z).toInstant());  // domingo anterior
        Goal flagged = new Goal(1, "cp", Area.CP, GoalKind.CONTAGEM, GoalPeriod.SEMANAL, 5, 1, true, true, Instant.EPOCH);
        assertEquals(3.0, StudyStats.progress(flagged, List.of(), acs, TODAY, Z).current()); // 1 manual + 2 ACs
        assertEquals(2.0, StudyStats.progress(flagged.withManualCount(0).withCountAc(true), List.of(), acs, TODAY, Z).current());

        assertEquals(1.0, StudyStats.progress(flagged.withCountAc(false), List.of(), acs, TODAY, Z).current());
        Goal math = new Goal(2, "m", Area.MATEMATICA, GoalKind.CONTAGEM, GoalPeriod.MENSAL, 5, 0, true, true, Instant.EPOCH);
        assertEquals(0.0, StudyStats.progress(math, List.of(), acs, TODAY, Z).current());
        Goal monthly = new Goal(3, "mes", Area.CP, GoalKind.CONTAGEM, GoalPeriod.MENSAL, 5, 0, true, true, Instant.EPOCH);
        assertEquals(3.0, StudyStats.progress(monthly, List.of(), acs, TODAY, Z).current());
    }
}
