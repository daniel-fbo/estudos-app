package br.estudos.infra.ai;

import br.estudos.app.ai.AiException;
import br.estudos.app.ports.ProcessRunner;
import br.estudos.domain.AiRequest;
import br.estudos.domain.AiResult;
import br.estudos.domain.AiRunnerKind;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@code agy --dangerously-skip-permissions --output-format json [--model m] --print <prompt>}
 * (agy 1.5.1 tem modo não interativo; flags conferidas com {@code agy --help}).
 */
public class AgyCliRunner extends CliAiRunner {

    public AgyCliRunner(ProcessRunner processes, Supplier<String> executable, Supplier<Path> workDir) {
        super(processes, executable, workDir);
    }

    @Override
    public AiRunnerKind kind() { return AiRunnerKind.AGY; }

    /** Limite do prompt (bytes UTF-8): o agy só aceita o prompt como argumento. */
    public static final int MAX_PROMPT_BYTES = 100 * 1024;

    @Override
    void validate(AiRequest req) throws AiException {
        int n = req.prompt().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (n > MAX_PROMPT_BYTES)
            throw new AiException("Prompt grande demais para o agy (" + n / 1024 + " KB; máximo "
                    + MAX_PROMPT_BYTES / 1024 + " KB), pois ele só recebe o prompt como argumento. Use o Claude.");
    }

    @Override
    List<String> arguments(AiRequest req) {
        List<String> a = new ArrayList<>(List.of("--dangerously-skip-permissions", "--output-format", "json"));
        if (req.model() != null) a.addAll(List.of("--model", req.model()));
        a.addAll(List.of("--print", req.prompt()));
        return a;
    }

    @Override
    AiResult parse(String stdout) throws AiException { return CliOutputParser.agy(stdout); }
}
