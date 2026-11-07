package br.estudos.app.study;

import java.time.Instant;

/** Valor (delta, página, contagem 1) com o instante em que ocorreu. */
public record Dated(Instant at, long value) {}
