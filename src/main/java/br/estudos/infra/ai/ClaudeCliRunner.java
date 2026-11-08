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

/** {@code claude -p --output-format json [--model m] --dangerously-skip-permissions}, prompt no stdin (flags conferidas no M0). */
public class ClaudeCliRunner extends CliAiRunner {

    public ClaudeCliRunner(ProcessRunner processes, Supplier<String> executable, Supplier<Path> workDir) {
        super(processes, executable, workDir);
    }

    @Override
    public AiRunnerKind kind() { return AiRunnerKind.CLAUDE; }

    @Override
    List<String> arguments(AiRequest req) {
        List<String> a = new ArrayList<>(List.of("-p", "--output-format", "json"));
        if (req.model() != null) a.addAll(List.of("--model", req.model()));
        a.add("--dangerously-skip-permissions");
        return a;
    }

    /** O prompt vai pelo stdin ({@code claude -p} sem argumento lê o stdin): sem limite de tamanho de argv. */
    @Override
    String stdin(AiRequest req) { return req.prompt(); }

    @Override
    AiResult parse(String stdout) throws AiException { return CliOutputParser.claude(stdout); }
}
