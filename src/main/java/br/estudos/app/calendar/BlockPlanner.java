package br.estudos.app.calendar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Distribuição gulosa de blocos (sem IA): repete "meta com mais minutos faltando → primeiro horário
 * livre", preferindo um dia em que essa meta ainda não tem bloco. Blocos de {@code minBlock} a
 * {@code maxBlock} minutos; nada é proposto antes de {@code now}.
 */
public final class BlockPlanner {
    private BlockPlanner() {}

    public static List<PlannedBlock> plan(List<Need> needs, List<FreeSlot> slots, LocalDateTime now,
                                          int minBlock, int maxBlock) {
        if (minBlock <= 0 || maxBlock < minBlock) throw new IllegalArgumentException("Tamanhos de bloco inválidos");
        List<FreeSlot> free = new ArrayList<>();
        for (FreeSlot s : slots) {
            LocalDateTime st = s.start().isBefore(now) ? now : s.start();
            if (st.isBefore(s.end())
                    && java.time.Duration.between(st, s.end()).toMinutes() >= minBlock)
                free.add(new FreeSlot(st, s.end()));
        }
        free.sort(Comparator.comparing(FreeSlot::start));

        Map<Long, Long> left = new LinkedHashMap<>();
        Map<Long, Need> byId = new HashMap<>();
        for (Need n : needs) {
            if (n.minutes() <= 0) continue;
            left.merge(n.goalId(), n.minutes(), Long::sum);
            byId.putIfAbsent(n.goalId(), n);
        }
        Map<Long, Set<LocalDate>> usedDays = new HashMap<>();
        List<PlannedBlock> out = new ArrayList<>();

        while (!free.isEmpty()) {
            Long gid = null;
            for (var e : left.entrySet())
                if (e.getValue() > 0 && (gid == null || e.getValue() > left.get(gid))) gid = e.getKey();
            if (gid == null) break;
            Set<LocalDate> days = usedDays.computeIfAbsent(gid, k -> new HashSet<>());
            int idx = -1;
            for (int i = 0; i < free.size(); i++)
                if (!days.contains(free.get(i).start().toLocalDate())) { idx = i; break; }
            if (idx < 0) idx = 0;
            FreeSlot s = free.get(idx);
            long len = Math.min(Math.min(maxBlock, s.minutes()), Math.max(left.get(gid), minBlock));
            LocalDateTime end = s.start().plusMinutes(len);
            Need n = byId.get(gid);
            out.add(new PlannedBlock(gid, n.title(), n.area(), s.start(), end));
            days.add(s.start().toLocalDate());
            left.put(gid, left.get(gid) - len);
            free.remove(idx);
            if (s.minutes() - len >= minBlock) free.add(idx, new FreeSlot(end, s.end()));
        }
        out.sort(Comparator.comparing(PlannedBlock::start));
        return out;
    }
}
