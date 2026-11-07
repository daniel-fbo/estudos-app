package br.estudos.app.calendar;

import java.time.LocalDateTime;

/** Intervalo livre marcado pelo usuário. */
public record FreeSlot(LocalDateTime start, LocalDateTime end) {
    public long minutes() { return java.time.Duration.between(start, end).toMinutes(); }
}
