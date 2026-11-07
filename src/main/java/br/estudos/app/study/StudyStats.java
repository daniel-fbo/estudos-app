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
        return progress(g, sessions, List.of(), List.of(), today, zone);
    }

    /**
     * @param manual deltas do contador manual (CONTAGEM); só os do período atual somam
     * @param auto   eventos da fonte automática da meta (já escolhida pelo chamador), cada um com seu valor
     */
    public static GoalProgress progress(Goal g, List<StudySession> sessions, List<Dated> manual, List<Dated> auto,
                                        LocalDate today, ZoneId zone) {
        if (g.kind() == GoalKind.CONTAGEM) {
            long m = Math.max(0, sumInPeriod(manual, g.period(), today, zone));
            long a = g.effectiveSource() == GoalSource.NENHUMA ? 0 : sumInPeriod(auto, g.period(), today, zone);
            return new GoalProgress(g, m + a, g.target());
        }
        long secs = secondsByArea(sessions, periodStart(g.period(), today), periodEnd(g.period(), today), zone)
                .getOrDefault(g.area(), 0L);
        return new GoalProgress(g, secs / 3600.0, g.target());
    }

    public static long sumInPeriod(List<Dated> items, GoalPeriod p, LocalDate today, ZoneId zone) {
        LocalDate from = periodStart(p, today), to = periodEnd(p, today);
        long sum = 0;
        for (Dated d : items) {
            LocalDate day = d.at().atZone(zone).toLocalDate();
            if (!day.isBefore(from) && day.isBefore(to)) sum += d.value();
        }
        return sum;
    }
}
