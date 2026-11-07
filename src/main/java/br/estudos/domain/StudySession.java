package br.estudos.domain;

import java.time.Instant;

/** Sessão de estudo. {@code endedAt} nulo = ainda em andamento. */
public record StudySession(long id, Area area, String subject, Instant startedAt, Instant endedAt,
                           long durationSeconds, String note) {
}
