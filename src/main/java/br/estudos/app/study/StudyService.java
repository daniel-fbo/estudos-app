package br.estudos.app.study;

import br.estudos.domain.*;

import java.time.*;
import java.util.*;

/** Casos de uso de M1: cronômetro, metas e painel. O relógio é injetável para testes. */
public class StudyService {
    private final StudyRepository repo;
    private final Clock clock;

    private Area runArea;
    private String runSubject;
    private Instant runStart;
    private Instant pausedAt;
    private long pausedTotal; // segundos

    public StudyService(StudyRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    // ---- cronômetro ----
    public synchronized void start(Area area, String subject) {
        if (area == null) throw new IllegalArgumentException("Área obrigatória");
        if (runStart != null) throw new IllegalStateException("Já há uma sessão em andamento");
        runArea = area; runSubject = subject; runStart = clock.instant(); pausedAt = null; pausedTotal = 0;
    }

    public synchronized void pause() {
        if (runStart != null && pausedAt == null) pausedAt = clock.instant();
    }

    public synchronized void resume() {
        if (pausedAt != null) {
            pausedTotal += Duration.between(pausedAt, clock.instant()).getSeconds();
            pausedAt = null;
        }
    }

    public synchronized boolean running() { return runStart != null; }
    public synchronized boolean paused() { return pausedAt != null; }

    public synchronized long elapsedSeconds() {
        if (runStart == null) return 0;
        Instant end = pausedAt != null ? pausedAt : clock.instant();
        return Math.max(0, Duration.between(runStart, end).getSeconds() - pausedTotal);
    }

    public synchronized StudySession stop(String note) {
        if (runStart == null) throw new IllegalStateException("Nenhuma sessão em andamento");
        long dur = elapsedSeconds();
        StudySession s = repo.saveSession(new StudySession(0, runArea, blankToNull(runSubject), runStart,
                clock.instant(), dur, blankToNull(note)));
        runStart = null; pausedAt = null; pausedTotal = 0;
        return s;
    }

    /** Registra uma sessão já encerrada (ex.: bloco da agenda marcado como feito). */
    public StudySession logSession(Area area, String subject, Instant start, Instant end, String note) {
        if (area == null || start == null || end == null || end.isBefore(start))
            throw new IllegalArgumentException("Sessão inválida");
        return repo.saveSession(new StudySession(0, area, blankToNull(subject), start, end,
                Duration.between(start, end).getSeconds(), blankToNull(note)));
    }

    public synchronized void discard() { runStart = null; pausedAt = null; pausedTotal = 0; }

    // ---- metas ----
    public Goal createGoal(String title, Area area, GoalKind kind, GoalPeriod period, double target) {
        return createGoal(title, area, kind, period, target, false);
    }

    /** @param countAc contar os ACs do runner (só vale para CONTAGEM em CP) */
    public Goal createGoal(String title, Area area, GoalKind kind, GoalPeriod period, double target,
                           boolean countAc) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Título obrigatório");
        if (area == null || kind == null || period == null)
            throw new IllegalArgumentException("Área, tipo e período são obrigatórios");
        if (Double.isNaN(target) || Double.isInfinite(target) || target <= 0)
            throw new IllegalArgumentException("Alvo deve ser um número maior que zero");
        boolean ac = countAc && kind == GoalKind.CONTAGEM && area == Area.CP;
        return repo.saveGoal(new Goal(0, title.trim(), area, kind, period, target, 0, ac, true, clock.instant()));
    }

    public Goal updateGoal(Goal g) { return repo.saveGoal(g); }
    public void deleteGoal(long id) { repo.deleteGoal(id); }
    public List<Goal> goals() { return repo.goals(false); }

    public Goal adjustCount(Goal g, int delta) {
        if (g.kind() != GoalKind.CONTAGEM) throw new IllegalArgumentException("Só metas de contagem têm contador");
        int c = Math.max(0, g.manualCount() + delta);
        repo.setManualCount(g.id(), c);
        return g.withManualCount(c);
    }

    // ---- painel ----
    public record Dashboard(Map<Area, Long> weekSecondsByArea, List<GoalProgress> goals, int streak,
                            List<StudySession> lastSessions) {}

    public Dashboard dashboard() {
        return buildDashboard(true);
    }

    /** Progresso de todas as metas (ativas e inativas), na ordem de {@link #goals()}. */
    public List<GoalProgress> allGoalsProgress() {
        return buildDashboard(false).goals();
    }

    private Dashboard buildDashboard(boolean onlyActive) {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = StudyStats.periodStart(GoalPeriod.SEMANAL, today);
        LocalDate monthStart = StudyStats.periodStart(GoalPeriod.MENSAL, today);
        LocalDate from = weekStart.isBefore(monthStart) ? weekStart : monthStart;
        List<StudySession> recent = repo.sessionsSince(from.atStartOfDay(zone).toInstant());

        Set<LocalDate> days = new HashSet<>();
        for (Instant i : repo.allSessionStarts()) days.add(i.atZone(zone).toLocalDate());

        List<Goal> goals = repo.goals(onlyActive);
        List<Instant> firstAcs = goals.stream().anyMatch(Goal::countsAc) ? repo.firstAcTimes() : List.of();
        List<GoalProgress> gp = new ArrayList<>();
        for (Goal g : goals) gp.add(StudyStats.progress(g, recent, firstAcs, today, zone));
        return new Dashboard(StudyStats.secondsByArea(recent, weekStart, weekStart.plusWeeks(1), zone), gp,
                StudyStats.streak(days, today), repo.lastSessions(5));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
