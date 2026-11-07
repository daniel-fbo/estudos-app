package br.estudos.app.ai;

import br.estudos.domain.AiCall;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiCallRepository {

    /** Grava e devolve com o id gerado. */
    AiCall insert(AiCall call);

    /** Última chamada real (não cache) com status OK e essa chave. */
    Optional<AiCall> findOk(String cacheKey);

    /** Chamadas reais (não cache) desde {@code from}: quantidade e soma de tokens. */
    BudgetStatus usageSince(Instant from, int dailyLimit);

    /** Todas as linhas desde {@code from}, sem prompt e resposta (para estatística). */
    List<AiCall> statsSince(Instant from);
}
