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

    /** Grade semanal salva: (diaDaSemana 1..7) * 100 + hora. */
    public java.util.Set<Integer> loadAvailability() { return repo.loadAvailability(); }
    public void saveAvailability(java.util.Set<Integer> cells) { repo.saveAvailability(cells); }

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
        LocalDateTime now = LocalDateTime.now(clock);
        List<CalendarBlock> pending = pendingFor(slots, now);
        List<Need> needs = new ArrayList<>();
        for (Need n : needs()) {
            long used = pending.stream().filter(b -> b.goalId() != null && b.goalId() == n.goalId())
                    .mapToLong(b -> Duration.between(b.startsAt(), b.endsAt()).toMinutes()).sum();
            if (n.minutes() > used) needs.add(new Need(n.goalId(), n.title(), n.area(), n.minutes() - used));
        }
        return BlockPlanner.plan(needs, subtractBusy(slots, pending), now, MIN_BLOCK, MAX_BLOCK);
    }

    /** Blocos aprovados, ainda não feitos e futuros, nas semanas cobertas pelos horários. */
    private List<CalendarBlock> pendingFor(List<FreeSlot> slots, LocalDateTime now) {
        if (slots.isEmpty()) return List.of();
        ZoneId zone = clock.getZone();
        LocalDate first = slots.stream().map(s -> s.start().toLocalDate()).min(LocalDate::compareTo).get();
        LocalDate last = slots.stream().map(s -> s.end().toLocalDate()).max(LocalDate::compareTo).get();
        Instant from = first.with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant();
        Instant to = last.with(java.time.DayOfWeek.MONDAY).plusWeeks(1).atStartOfDay(zone).toInstant();
        Instant nowI = now.atZone(zone).toInstant();
        return repo.between(from, to).stream().filter(b -> !b.done() && b.endsAt().isAfter(nowI)).toList();
    }

    private List<FreeSlot> subtractBusy(List<FreeSlot> slots, List<CalendarBlock> busy) {
        ZoneId zone = clock.getZone();
        List<FreeSlot> cur = new ArrayList<>(slots);
        for (CalendarBlock b : busy) {
            LocalDateTime bs = LocalDateTime.ofInstant(b.startsAt(), zone), be = LocalDateTime.ofInstant(b.endsAt(), zone);
            List<FreeSlot> next = new ArrayList<>();
            for (FreeSlot s : cur) {
                if (!bs.isBefore(s.end()) || !be.isAfter(s.start())) { next.add(s); continue; }
                if (bs.isAfter(s.start())) next.add(new FreeSlot(s.start(), bs));
                if (be.isBefore(s.end())) next.add(new FreeSlot(be, s.end()));
            }
            cur = next;
        }
        return cur;
    }

    /** Cria os eventos no Google e grava os blocos. Se o Google falha, nada é gravado a partir dali. */
    public List<CalendarBlock> approve(List<PlannedBlock> blocks) throws CalendarClient.CalendarException {
        ZoneId zone = clock.getZone();
        List<CalendarBlock> saved = new ArrayList<>();
        for (PlannedBlock b : blocks) {
            Instant s = b.start().atZone(zone).toInstant(), e = b.end().atZone(zone).toInstant();
            String id;
            try {
                id = client.createEvent("Estudo: " + b.title(), "Área: " + b.area() + " (Estudos App)", s, e);
            } catch (CalendarClient.CalendarException ex) {
                ex.saved = List.copyOf(saved);
                throw ex;
            }
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
