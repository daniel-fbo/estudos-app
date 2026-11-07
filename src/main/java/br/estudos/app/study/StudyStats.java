package br.estudos.app.study;

import br.estudos.domain.*;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Cálculos puros (sem I/O) de sequência, períodos e progresso de metas. */
public final class StudyStats {
    private StudyStats() {}

    /** Início do período (semana começa na segunda) que contém {@code today}. */
    public static LocalDate periodStart(GoalPeriod p, LocalDate today) {
        return p == GoalPeriod.SEMANAL
                ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : today.withDayOfMonth(1);
    }

    /** Fim exclusivo do período. */
    public static LocalDate periodEnd(GoalPeriod p, LocalDate today) {
        LocalDate s = periodStart(p, today);
        return p == GoalPeriod.SEMANAL ? s.plusWeeks(1) : s.plusMonths(1);
    }

    /**
     * Dias seguidos com estudo terminando hoje. Se hoje ainda não teve estudo, a sequência
     * continua contando a partir de ontem (o dia não acabou); só zera se ontem também falhou.
     */
    public static int streak(Collection<LocalDate> studyDays, LocalDate today) {
        Set<LocalDate> days = new HashSet<>(studyDays);
        LocalDate d = days.contains(today) ? today : today.minusDays(1);
        int n = 0;
        while (days.contains(d)) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    /** Segundos por área no intervalo [from, to) de datas locais. */
    public static Map<Area, Long> secondsByArea(List<StudySession> sessions, LocalDate from, LocalDate to, ZoneId zone) {
        Map<Area, Long> m = new EnumMap<>(Area.class);
        for (StudySession s : sessions) {
            LocalDate d = s.startedAt().atZone(zone).toLocalDate();
            if (!d.isBefore(from) && d.isBefore(to)) m.merge(s.area(), s.durationSeconds(), Long::sum);
        }
        return m;
    }

    public static GoalProgress progress(Goal g, List<StudySession> sessions, LocalDate today, ZoneId zone) {
        if (g.kind() == GoalKind.CONTAGEM) {
            return new GoalProgress(g, g.manualCount() + g.autoCountAc(), g.target());
        }
        long secs = secondsByArea(sessions, periodStart(g.period(), today), periodEnd(g.period(), today), zone)
                .getOrDefault(g.area(), 0L);
        return new GoalProgress(g, secs / 3600.0, g.target());
    }
}
