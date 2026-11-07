package br.estudos.domain;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Pedido a uma CLI de IA.
 *
 * @param model       alias ou nome do modelo; {@code null} usa o padrão da CLI
 * @param attachments arquivos citados no prompt; o conteúdo entra na chave de cache
 */
public record AiRequest(AiRunnerKind runner, String model, AiTask task, String prompt,
                        List<Path> attachments, Duration timeout) {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    public AiRequest {
        Objects.requireNonNull(runner, "runner");
        Objects.requireNonNull(task, "task");
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt vazio");
        if (model != null && model.isBlank()) model = null;
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        if (timeout == null) timeout = DEFAULT_TIMEOUT;
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout deve ser positivo");
    }

    public static AiRequest of(AiRunnerKind runner, String model, AiTask task, String prompt) {
        return new AiRequest(runner, model, task, prompt, List.of(), null);
    }

    public AiRequest withTimeout(Duration t) {
        return new AiRequest(runner, model, task, prompt, attachments, t);
    }

    public AiRequest withAttachments(List<Path> files) {
        return new AiRequest(runner, model, task, prompt, files, timeout);
    }
}
