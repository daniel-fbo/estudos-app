package br.estudos.app.calendar;

import br.estudos.domain.Area;

/** Minutos que ainda faltam para uma meta de horas. */
public record Need(long goalId, String title, Area area, long minutes) {
}
