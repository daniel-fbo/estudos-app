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
    private long runId;
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
        Instant now = clock.instant();
        // grava já com ended_at nulo: se o app fechar, a sessão pode ser recuperada
        StudySession open = repo.saveSession(new StudySession(0, area, blankToNull(subject), now, null, 0, null));
        runId = open.id(); runArea = area; runSubject = subject; runStart = open.startedAt(); pausedAt = null; pausedTotal = 0;
    }

    /** Sessão que ficou aberta (app fechado no meio); vazio se há uma em andamento neste processo. */
    public synchronized Optional<StudySession> openSession() {
        return runStart != null ? Optional.empty() : repo.openSession();
    }

    /** Retoma a contagem a partir de {@code started_at}. */
    public synchronized void resumeOpen(StudySession open) {
        if (runStart != null) throw new IllegalStateException("Já há uma sessão em andamento");
        runId = open.id(); runArea = open.area(); runSubject = open.subject(); runStart = open.startedAt();
        pausedAt = null; pausedTotal = 0;
    }

    /** Encerra a sessão aberta agora, contando de {@code started_at} até o instante atual. */
    public synchronized StudySession endOpen(StudySession open, String note) {
        Instant end = clock.instant();
        long dur = Math.max(0, Duration.between(open.startedAt(), end).getSeconds());
        StudySession done = new StudySession(open.id(), open.area(), open.subject(), open.startedAt(), end, dur,
                blankToNull(note));
        repo.updateSession(done);
        return done;
    }

    public synchronized void discardOpen(StudySession open) { repo.deleteSession(open.id()); }

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
        StudySession s = new StudySession(runId, runArea, blankToNull(runSubject), runStart,
                clock.instant(), dur, blankToNull(note));
        repo.updateSession(s);
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

    public synchronized void discard() {
        if (runStart != null) repo.deleteSession(runId);
        runStart = null; pausedAt = null; pausedTotal = 0; }

    // ---- metas ----
    public Goal createGoal(String title, Area area, GoalKind kind, GoalPeriod period, double target) {
        return createGoal(title, area, kind, period, target, GoalSource.NENHUMA);
    }

    public Goal createGoal(String title, Area area, GoalKind kind, GoalPeriod period, double target,
                           GoalSource source) {
        if (area == null || kind == null || period == null)
            throw new IllegalArgumentException("Área, tipo e período são obrigatórios");
        validate(title, target, source, area, kind);
        return repo.saveGoal(new Goal(0, title.trim(), area, kind, period, target, source, true, clock.instant()));
    }

    /** Edita título, alvo e fonte automática (mesmas validações da criação). */
    public Goal editGoal(Goal g, String title, double target, GoalSource source) {
        validate(title, target, source, g.area(), g.kind());
        return repo.saveGoal(g.withTitleTarget(title.trim(), target).withSource(source));
    }

    private static void validate(String title, double target, GoalSource source, Area area, GoalKind kind) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Título obrigatório");
        if (Double.isNaN(target) || Double.isInfinite(target) || target <= 0)
            throw new IllegalArgumentException("Alvo deve ser um número maior que zero");
        if (source == null || !source.allowedFor(area, kind))
            throw new IllegalArgumentException("Fonte automática inválida para esta meta");
    }

    public Goal updateGoal(Goal g) { return repo.saveGoal(g); }
    public void deleteGoal(long id) { repo.deleteGoal(id); }
    public List<Goal> goals() { return repo.goals(false); }

    /** Ajusta o contador manual do período atual; nunca deixa o total do período abaixo de zero. */
    public synchronized void adjustCount(Goal g, int delta) {
        if (g.kind() != GoalKind.CONTAGEM) throw new IllegalArgumentException("Só metas de contagem têm contador");
        long now = StudyStats.sumInPeriod(repo.countDeltas(g.id()), g.period(), LocalDate.now(clock), clock.getZone());
        long d = Math.max(delta, -Math.max(0, now));
        if (d != 0) repo.logCountDelta(g.id(), (int) d, clock.instant());
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
        List<Dated> runnerAcs = null, cfAcs = null;
        Map<Area, List<Dated>> pages = new EnumMap<>(Area.class);
        List<GoalProgress> gp = new ArrayList<>();
        for (Goal g : goals) {
            List<Dated> manual = List.of(), auto = List.of();
            if (g.kind() == GoalKind.CONTAGEM) {
                manual = repo.countDeltas(g.id());
                switch (g.effectiveSource()) {
                    case RUNNER -> {
                        if (runnerAcs == null) runnerAcs = repo.firstAcTimes().stream().map(i -> new Dated(i, 1)).toList();
                        auto = runnerAcs;
                    }
                    case CODEFORCES -> {
                        if (cfAcs == null) cfAcs = repo.cfFirstAcTimes().stream().map(i -> new Dated(i, 1)).toList();
                        auto = cfAcs;
                    }
                    case PAGINAS -> auto = pages.computeIfAbsent(g.area(), repo::pagesRead);
                    case NENHUMA -> { }
                }
            }
            gp.add(StudyStats.progress(g, recent, manual, auto, today, zone));
        }
        return new Dashboard(StudyStats.secondsByArea(recent, weekStart, weekStart.plusWeeks(1), zone), gp,
                StudyStats.streak(days, today), repo.lastSessions(5));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
