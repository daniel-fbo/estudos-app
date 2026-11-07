package br.estudos.app.tools;

import br.estudos.app.ports.ProcessRunner;
import br.estudos.app.ports.ProcessRunner.ProcessRequest;
import br.estudos.app.ports.ProcessRunner.ProcessResult;

import java.io.IOException;
import java.time.Duration;

/** Confere se um executável configurado responde a {@code --version}. */
public class ExecutableChecker {

    public record CheckResult(boolean ok, String message) {}

    static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ProcessRunner runner;

    public ExecutableChecker(ProcessRunner runner) {
        this.runner = runner;
    }

    public CheckResult check(String executable) {
        if (executable == null || executable.isBlank()) {
            return new CheckResult(false, "Caminho vazio.");
        }
        ProcessResult r;
        try {
            r = runner.run(ProcessRequest.of(TIMEOUT, executable.strip(), "--version"));
        } catch (IOException e) {
            return new CheckResult(false, "Não foi possível executar: " + e.getMessage());
        }
        if (r.timedOut()) {
            return new CheckResult(false, "Tempo esgotado (" + TIMEOUT.toSeconds() + " s).");
        }
        String text = firstLine(r.stdout());
        if (text.isEmpty()) text = firstLine(r.stderr());
        if (r.exitCode() != 0) {
            return new CheckResult(false, "Falhou (código " + r.exitCode() + "): " + text);
        }
        return new CheckResult(true, text.isEmpty() ? "OK (sem saída)" : text);
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        return s.lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
    }
}
