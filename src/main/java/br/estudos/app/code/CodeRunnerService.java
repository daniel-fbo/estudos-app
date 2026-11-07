package br.estudos.app.code;

import br.estudos.app.code.CodeResults.*;
import br.estudos.app.ports.ProcessRunner;
import br.estudos.app.ports.ProcessRunner.ProcessRequest;
import br.estudos.app.ports.ProcessRunner.ProcessResult;
import br.estudos.domain.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Compila, roda casos de teste e faz stress test. Chamar fora da thread JavaFX. */
public class CodeRunnerService {
    public static final Duration COMPILE_TIMEOUT = Duration.ofSeconds(30);

    private final ProcessRunner runner;
    private final CodeRepository repo;
    private final String gcc, gpp, python;
    private final Duration caseTimeout;

    public CodeRunnerService(ProcessRunner runner, CodeRepository repo, String gcc, String gpp) {
        this(runner, repo, gcc, gpp, Duration.ofSeconds(2));
    }

    public CodeRunnerService(ProcessRunner runner, CodeRepository repo, String gcc, String gpp, Duration caseTimeout) {
        this.runner = runner;
        this.repo = repo;
        this.gcc = gcc;
        this.gpp = gpp;
        this.python = "python3";
        this.caseTimeout = caseTimeout;
    }

    public List<TestCase> cases(Path source) { return repo.cases(key(source)); }
    public TestCase addCase(Path source, String in, String out) { return repo.saveCase(key(source), in, out, "MANUAL"); }
    public void deleteCase(long id) { repo.deleteCase(id); }

    private static String key(Path p) { return p.toAbsolutePath().normalize().toString(); }

    private static boolean isC(Path p) { return p.getFileName().toString().endsWith(".c"); }

    /** Compila para um binário temporário (apague com {@link #cleanup}). */
    public CompileResult compile(Path source) throws IOException {
        Path dir = Files.createTempDirectory("estudos-run");
        Path bin = dir.resolve("prog");
        List<String> cmd = new ArrayList<>();
        if (isC(source)) cmd.addAll(List.of(gcc, "-std=c17"));
        else cmd.addAll(List.of(gpp, "-std=c++17"));
        cmd.addAll(List.of("-O2", "-Wall", "-Wextra", "-fsanitize=address,undefined", "-g",
                source.toAbsolutePath().toString(), "-o", bin.toString()));
        ProcessResult r = runner.run(new ProcessRequest(cmd, null, null, COMPILE_TIMEOUT, null));
        String out = (r.stdout() + r.stderr()).strip();
        if (r.timedOut()) return new CompileResult(false, "Tempo de compilação excedido (30 s).\n" + out, bin);
        return new CompileResult(r.exitCode() == 0, out, bin);
    }

    public static void cleanup(Path binary) {
        try {
            Files.deleteIfExists(binary);
            Files.deleteIfExists(binary.getParent());
        } catch (IOException ignored) { }
    }

    /** Compila e roda todos os casos salvos do arquivo; grava em code_run. */
    public RunReport runAll(Path source) throws IOException {
        CompileResult c = compile(source);
        String k = key(source);
        try {
            if (!c.ok()) {
                repo.saveRun(k, "CE", 0, 0, c.output());
                return new RunReport(Verdict.CE, 0, 0, List.of(), c.output());
            }
            List<TestCase> cases = repo.cases(k);
            List<CaseResult> results = new ArrayList<>();
            int passed = 0;
            Verdict worst = Verdict.AC;
            for (TestCase tc : cases) {
                CaseResult cr = runCase(c.binary(), tc);
                results.add(cr);
                if (cr.verdict() == Verdict.AC) passed++;
                else if (worst == Verdict.AC) worst = cr.verdict();
            }
            StringBuilder detail = new StringBuilder();
            for (CaseResult cr : results)
                if (cr.verdict() != Verdict.AC)
                    detail.append("caso ").append(cr.testCase().id()).append(' ').append(cr.verdict())
                          .append(": ").append(cr.detail()).append('\n');
            if (!cases.isEmpty()) repo.saveRun(k, worst.name(), passed, cases.size(), detail.toString());
            return new RunReport(worst, passed, cases.size(), results, c.output());
        } finally {
            cleanup(c.binary());
        }
    }

    private CaseResult runCase(Path binary, TestCase tc) throws IOException {
        ProcessResult r = runner.run(new ProcessRequest(List.of(binary.toString()), binary.getParent(), tc.input(), caseTimeout, null));
        if (r.timedOut()) return new CaseResult(tc, Verdict.TLE, r.stdout(), "excedeu " + caseTimeout.toMillis() + " ms");
        if (r.exitCode() != 0) return new CaseResult(tc, Verdict.RE, r.stdout(),
                "código de saída " + r.exitCode() + "\n" + r.stderr());
        if (!OutputComparator.equal(tc.expected(), r.stdout()))
            return new CaseResult(tc, Verdict.WA, r.stdout(), OutputComparator.diff(tc.expected(), r.stdout()));
        return new CaseResult(tc, Verdict.AC, r.stdout(), "");
    }

    /**
     * Stress test: gerador recebe o número da iteração como argumento (argv[1]).
     * Para na primeira divergência (WA/RE/TLE da solução frente à força bruta) e salva o caso.
     */
    public StressResult stress(Path solution, Path brute, Path generator, int iterations, BooleanSupplier cancelled)
            throws IOException {
        if (iterations <= 0) throw new IllegalArgumentException("iterations deve ser > 0");
        CompileResult sol = compile(solution), br = null, gen = null;
        try {
            if (!sol.ok()) return new StressResult(false, 0, null, "Erro ao compilar a solução:\n" + sol.output());
            br = compile(brute);
            if (!br.ok()) return new StressResult(false, 0, null, "Erro ao compilar a força bruta:\n" + br.output());
            List<String> genCmd;
            if (generator.getFileName().toString().endsWith(".py")) {
                genCmd = List.of(python, generator.toAbsolutePath().toString());
            } else {
                gen = compile(generator);
                if (!gen.ok()) return new StressResult(false, 0, null, "Erro ao compilar o gerador:\n" + gen.output());
                genCmd = List.of(gen.binary().toString());
            }
            for (int i = 1; i <= iterations; i++) {
                if (cancelled != null && cancelled.getAsBoolean())
                    return new StressResult(false, i - 1, null, "Cancelado após " + (i - 1) + " iterações.");
                List<String> cmd = new ArrayList<>(genCmd);
                cmd.add(Integer.toString(i));
                ProcessResult g = runner.run(new ProcessRequest(cmd, null, null, Duration.ofSeconds(10), null));
                if (!g.ok()) return new StressResult(false, i - 1, null, "Gerador falhou na iteração " + i + ":\n" + g.stderr());
                String input = g.stdout();
                ProcessResult b = runner.run(new ProcessRequest(List.of(br.binary().toString()), null, input,
                        Duration.ofSeconds(10), null));
                if (!b.ok()) return new StressResult(false, i - 1, null, "Força bruta falhou na iteração " + i + ":\n" + b.stderr());
                TestCase probe = new TestCase(0, key(solution), input, b.stdout(), "STRESS");
                CaseResult cr = runCase(sol.binary(), probe);
                if (cr.verdict() != Verdict.AC) {
                    TestCase saved = repo.saveCase(key(solution), input, b.stdout(), "STRESS");
                    return new StressResult(true, i, saved,
                            "Divergência na iteração " + i + " (" + cr.verdict() + "):\n" + cr.detail());
                }
            }
            return new StressResult(false, iterations, null, iterations + " iterações sem divergência.");
        } finally {
            cleanup(sol.binary());
            if (br != null) cleanup(br.binary());
            if (gen != null) cleanup(gen.binary());
        }
    }
}
