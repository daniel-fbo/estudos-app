package br.estudos.app.cf;

import br.estudos.app.ai.AiService;
import br.estudos.app.ai.BudgetGate;
import br.estudos.app.config.Config;
import br.estudos.app.ports.CodeforcesClient;
import br.estudos.domain.AiRequest;
import br.estudos.domain.AiRunnerKind;
import br.estudos.domain.AiTask;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

public class CfService {
    public record SyncResult(int fetched, int added, int ratings) {}
    public record Overview(List<CfRatingPoint> ratings, List<CfStats.TagStat> tags,
                           List<CfStats.TagStat> mostFailed, List<String> suggestion) {}

    private final CfRepository repo;
    private final CodeforcesClient client;
    private final AiService ai;
    private final Supplier<Config> config;
    private final Clock clock;

    public CfService(CfRepository repo, CodeforcesClient client, AiService ai, Supplier<Config> config, Clock clock) {
        this.repo = repo;
        this.client = client;
        this.ai = ai;
        this.config = config;
        this.clock = clock;
    }

    public SyncResult sync() throws CfException {
        String handle = config.get().codeforcesHandle();
        if (handle == null || handle.isBlank())
            throw new CfException("Defina o handle do Codeforces em Configurações.");
        List<CfSubmission> subs = client.userStatus(handle.strip());
        List<CfRatingPoint> rt = client.userRating(handle.strip());
        int added = repo.upsertSubmissions(subs);
        repo.replaceRatings(rt);
        return new SyncResult(subs.size(), added, rt.size());
    }

    public Overview overview() {
        List<CfSubmission> subs = repo.submissions();
        return new Overview(repo.ratings(), CfStats.byTag(subs), CfStats.mostFailed(subs), CfStats.suggest(subs, 3, 3));
    }

    public int maxHintLevel(String problemRef) { return repo.maxHintLevel(norm(problemRef)); }

    public java.util.Optional<String> savedHint(String problemRef, int level) {
        return repo.hint(norm(problemRef), level);
    }

    /**
     * Devolve a dica do nível (do cache se já existir). Lança {@link IllegalStateException} se o nível anterior
     * ainda não foi liberado. {@code statement} é o enunciado colado ou uma URL.
     */
    public String hint(String problemRef, int level, String statement, BudgetGate gate) throws Exception {
        String ref = norm(problemRef);
        if (ref.isEmpty()) throw new IllegalArgumentException("Informe o problema.");
        var cached = repo.hint(ref, level);
        if (cached.isPresent()) return cached.get();
        if (!HintLevels.allowed(level, repo.maxHintLevel(ref)))
            throw new IllegalStateException("Libere a dica " + (level - 1) + " antes.");
        if (statement == null || statement.isBlank()) throw new IllegalArgumentException("Cole o enunciado ou a URL do problema.");
        AiRequest req = AiRequest.of(AiRunnerKind.CLAUDE, config.get().model(Config.MODEL_CHEAP), AiTask.DICA,
                prompt(statement.strip(), level));
        String text = ai.submit(req, false, gate).result().get().response().strip();
        repo.saveHint(ref, level, text, clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());
        return text;
    }

    static String prompt(String statement, int level) {
        return "Você ajuda um estudante de programação competitiva. NÃO escreva código nem pseudocódigo e NÃO entregue a "
                + "solução completa. Responda em português do Brasil.\n" + HintLevels.instruction(level)
                + "\n\n--- PROBLEMA (enunciado ou URL) ---\n" + statement;
    }

    static String norm(String ref) { return ref == null ? "" : ref.strip(); }
}
