package br.estudos.app.calendar;

import br.estudos.domain.CalendarBlock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CalendarRepository {
    CalendarBlock save(CalendarBlock b);
    List<CalendarBlock> between(Instant from, Instant to);
    Optional<CalendarBlock> find(long id);
    void markDone(long id);
}
