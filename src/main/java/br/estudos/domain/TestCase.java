package br.estudos.domain;

/** Caso de teste de um arquivo-fonte. origin: MANUAL ou STRESS. */
public record TestCase(long id, String sourcePath, String input, String expected, String origin) {}
