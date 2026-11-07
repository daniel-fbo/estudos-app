package br.estudos.app.code;

import br.estudos.domain.TestCase;

import java.util.List;

/** Tipos de resultado do runner de código. */
public final class CodeResults {
    private CodeResults() {}

    public enum Verdict { AC, WA, TLE, RE, CE }

    public record CompileResult(boolean ok, String output, java.nio.file.Path binary) {}

    public record CaseResult(TestCase testCase, Verdict verdict, String actual, String detail) {}

    public record RunReport(Verdict verdict, int passed, int total, List<CaseResult> results, String compileOutput) {}

    public record StressResult(boolean diverged, int iterations, TestCase newCase, String message) {}
}
