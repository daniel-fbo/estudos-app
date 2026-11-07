package br.estudos.domain;

import java.time.Instant;

/** Linha de {@code ai_call}. {@code cacheHit} marca respostas servidas do cache (não contam no orçamento). */
public record AiCall(long id, String cacheKey, AiRunnerKind runner, String model, AiTask task,
                     String prompt, String response, AiCallStatus status,
                     Long inputTokens, Long outputTokens, Double costUsd, long durationMs,
                     Instant createdAt, boolean cacheHit) {

    public long totalTokens() {
        return (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
    }
}
