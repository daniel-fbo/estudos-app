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
