package br.estudos.app.calendar;

import br.estudos.app.ports.CalendarClient;
import br.estudos.app.study.GoalProgress;
import br.estudos.app.study.StudyService;
import br.estudos.domain.*;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

/** Casos de uso de M8: planejar a semana, aprovar (cria eventos) e marcar blocos como feitos. */
public class CalendarService {
    public static final int MIN_BLOCK = 30, MAX_BLOCK = 90;

    private final CalendarRepository repo;
    private final CalendarClient client;
    private final StudyService study;
    private final Clock clock;

    public CalendarService(CalendarRepository repo, CalendarClient client, StudyService study, Clock clock) {
        this.repo = repo;
        this.client = client;
        this.study = study;
        this.clock = clock;
    }

    public boolean authorized() { return client.authorized(); }
    public void authorize() throws CalendarClient.CalendarException { client.authorize(); }

    /** Minutos que faltam nas metas de horas ativas; as mensais são divididas pelas semanas restantes. */
    public List<Need> needs() {
        LocalDate today = LocalDate.now(clock);
        List<Need> out = new ArrayList<>();
        for (GoalProgress p : study.dashboard().goals()) {
            Goal g = p.goal();
            if (g.kind() != GoalKind.HORAS || !g.active()) continue;
            double remaining = Math.max(0, p.target() - p.current());
            if (g.period() == GoalPeriod.MENSAL) {
                long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(today,
                        br.estudos.app.study.StudyStats.periodEnd(GoalPeriod.MENSAL, today)));
                remaining = remaining / Math.max(1, Math.ceil(days / 7.0));
            }
            long min = Math.round(remaining * 60);
            if (min > 0) out.add(new Need(g.id(), g.title(), g.area(), min));
        }
        return out;
    }

    public List<PlannedBlock> plan(List<FreeSlot> slots) {
        return BlockPlanner.plan(needs(), slots, LocalDateTime.now(clock), MIN_BLOCK, MAX_BLOCK);
    }

    /** Cria os eventos no Google e grava os blocos. Se o Google falha, nada é gravado a partir dali. */
    public List<CalendarBlock> approve(List<PlannedBlock> blocks) throws CalendarClient.CalendarException {
        ZoneId zone = clock.getZone();
        List<CalendarBlock> saved = new ArrayList<>();
        for (PlannedBlock b : blocks) {
            Instant s = b.start().atZone(zone).toInstant(), e = b.end().atZone(zone).toInstant();
            String id = client.createEvent("Estudo: " + b.title(), "Área: " + b.area() + " (Estudos App)", s, e);
            saved.add(repo.save(new CalendarBlock(0, b.goalId(), b.title(), b.area(), id, s, e, false)));
        }
        return saved;
    }

    public List<CalendarBlock> week(LocalDate weekStart) {
        ZoneId zone = clock.getZone();
        return repo.between(weekStart.atStartOfDay(zone).toInstant(), weekStart.plusWeeks(1).atStartOfDay(zone).toInstant());
    }

    /** Marca como feito e registra a sessão de estudo correspondente. Idempotente. */
    public void markDone(long blockId) {
        CalendarBlock b = repo.find(blockId).orElseThrow(() -> new IllegalArgumentException("Bloco inexistente"));
        if (b.done()) return;
        study.logSession(b.area(), b.title(), b.startsAt(), b.endsAt(), "Bloco da agenda");
        repo.markDone(blockId);
    }
}
