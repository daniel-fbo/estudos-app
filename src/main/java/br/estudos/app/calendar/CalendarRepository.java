package br.estudos.app.calendar;

import br.estudos.domain.CalendarBlock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CalendarRepository {
    CalendarBlock save(CalendarBlock b);
    List<CalendarBlock> between(Instant from, Instant to);
    Optional<CalendarBlock> find(long id);
    void markDone(long id);
    /** Células marcadas da grade semanal, codificadas como (diaDaSemana 1..7) * 100 + hora. */
    Set<Integer> loadAvailability();
    void saveAvailability(Set<Integer> cells);
}
