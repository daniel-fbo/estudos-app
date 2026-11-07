package br.estudos.app.ports;

import br.estudos.app.ai.AiException;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.domain.AiRequest;
import br.estudos.domain.AiResult;
import br.estudos.domain.AiRunnerKind;

/** Uma CLI de IA. Só o {@code AiService} chama; ele cuida de cache, orçamento e registro. */
public interface AiRunner {

    AiRunnerKind kind();

    /** {@code false} se a CLI não tem modo não interativo; a UI desabilita o que depende dela. */
    default boolean supported() {
        return true;
    }

    /**
     * Executa o pedido e bloqueia até terminar.
     *
     * @throws java.util.concurrent.CancellationException se {@code token} foi cancelado
     * @throws AiException em erro da CLI (saída não zero, timeout, JSON inválido)
     */
    AiResult run(AiRequest request, CancellationToken token) throws AiException;
}
