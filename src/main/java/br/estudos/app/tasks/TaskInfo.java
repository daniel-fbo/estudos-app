package br.estudos.app.tasks;

import java.time.Instant;

/** Foto imutável de uma tarefa da fila, para a UI. */
public record TaskInfo(long id, String title, boolean ai, TaskState state,
                       Instant submittedAt, Instant finishedAt, String message) {}
