package br.estudos.domain;

/** Resposta de uma CLI de IA. Tokens e custo são {@code null} quando a CLI não informa. */
public record AiResult(String response, Long inputTokens, Long outputTokens, Double costUsd) {}
