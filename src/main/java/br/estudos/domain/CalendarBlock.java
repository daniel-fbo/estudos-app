package br.estudos.domain;

import java.time.Instant;

/** Bloco de estudo agendado ({@code googleEventId} nulo = só local). */
public record CalendarBlock(long id, Long goalId, String title, Area area, String googleEventId,
                            Instant startsAt, Instant endsAt, boolean done) {
}
