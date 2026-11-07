package br.estudos.app.ai;

import br.estudos.domain.AiCall;

/** Resultado entregue a quem pediu: a linha de {@code ai_call} e se veio do cache. */
public record AiOutcome(AiCall call, boolean fromCache) {
    public String response() {
        return call.response();
    }
}
