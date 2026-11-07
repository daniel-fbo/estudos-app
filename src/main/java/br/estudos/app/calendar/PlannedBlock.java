package br.estudos.app.calendar;

import br.estudos.domain.Area;

import java.time.LocalDateTime;

/** Bloco proposto pelo planejador (ainda não aprovado). */
public record PlannedBlock(long goalId, String title, Area area, LocalDateTime start, LocalDateTime end) {
}
