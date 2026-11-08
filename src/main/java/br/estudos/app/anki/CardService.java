package br.estudos.app.anki;

import br.estudos.app.ai.AiOutcome;
import br.estudos.app.ai.AiService;
import br.estudos.app.ai.BudgetGate;
import br.estudos.app.config.Config;
import br.estudos.app.ports.AnkiClient;
import br.estudos.domain.*;
import br.estudos.infra.config.AppPaths;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class CardService {
    public static final int MAX_PROMPT_CHARS = 60_000;
    private static final ObjectMapper JSON = new ObjectMapper();

    public record ImportResult(int found, int added, int duplicates) {}
    public record SyncResult(int sent, int ok, int rejected) {}

    private final CardRepository repo;
    private final AnkiClient anki;
    private final AiService ai;
    private final Supplier<Config> config;
    private final Clock clock;

    public CardService(CardRepository repo, AnkiClient anki, AiService ai, Supplier<Config> config, Clock clock) {
        this.repo = repo;
        this.anki = anki;
        this.ai = ai;
        this.config = config;
        this.clock = clock;
    }

    public List<Card> list(Area area, Boolean synced) { return repo.list(area, synced); }

    public Optional<Card> add(String front, String back, Area area, CardOrigin origin, String sourceRef) {
        if (front == null || front.isBlank() || back == null || back.isBlank())
            throw new IllegalArgumentException("frente e verso são obrigatórios");
        String f = front.strip();
        return repo.insertIfNew(new Card(0, f, back.strip(), area, origin, sourceRef,
                CardMarkdownParser.hash(f), null, null));
    }

    /**
     * Salva o card. Se já tem nota no Anki, atualiza a nota; com o Anki fechado salva só local e deixa
     * {@code synced_at} nulo (pendente — a próxima sincronização atualiza a nota). Devolve {@code true}
     * se ficou pendente de sincronização.
     */
    public boolean edit(Card c, String front, String back, Area area) {
        if (front == null || front.isBlank() || back == null || back.isBlank())
            throw new IllegalArgumentException("frente e verso são obrigatórios");
        String f = front.strip(), b = back.strip();
        String hash = CardMarkdownParser.hash(f);
        boolean hasNote = c.ankiNoteId() != null;
        // grava local primeiro (pode lançar por frente duplicada) sem tocar no Anki
        repo.update(new Card(c.id(), f, b, area, c.origin(), c.sourceRef(),
                hash, c.ankiNoteId(), hasNote ? null : c.syncedAt()));
        if (!hasNote) return false;
        try {
            anki.updateNoteFields(c.ankiNoteId(), f, b);
        } catch (AnkiException e) {
            return true;
        }
        repo.update(new Card(c.id(), f, b, area, c.origin(), c.sourceRef(),
                hash, c.ankiNoteId(), now()));
        return false;
    }

    private String now() { return clock.instant().truncatedTo(ChronoUnit.SECONDS).toString(); }

    public void delete(long id) { repo.delete(id); }

    /** Importa cards de todos os {@code .md} sob {@code estudosRepo} (pastas ocultas ignoradas). */
    public ImportResult importFromRepo() throws IOException {
        Path root = AppPaths.expand(config.get().estudosRepo());
        if (!Files.isDirectory(root)) throw new IOException("Pasta do repositório não existe: " + root);
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".md"))
                    .filter(p -> !hidden(root.relativize(p))).sorted().toList();
        }
        int found = 0, added = 0;
        for (Path p : files) {
            Area area = areaOf(root.relativize(p));
            String text;
            try {
                text = Files.readString(p);
            } catch (IOException e) { // não-UTF-8 ou ilegível: pula só este arquivo
                org.slf4j.LoggerFactory.getLogger(CardService.class).warn("Arquivo ignorado na importação: {} ({})", p, e.toString());
                continue;
            }
            for (CardDraft d : CardMarkdownParser.parse(text)) {
                found++;
                if (add(d.front(), d.back(), area, CardOrigin.NOTA, root.relativize(p).toString()).isPresent()) added++;
            }
        }
        return new ImportResult(found, added, found - added);
    }

    private static boolean hidden(Path rel) {
        for (Path part : rel) if (part.toString().startsWith(".")) return true;
        return false;
    }

    static Area areaOf(Path rel) {
        String s = rel.toString().toLowerCase();
        if (s.contains("matematica") || s.contains("matemática")) return Area.MATEMATICA;
        if (s.contains("programacao") || s.contains("programação") || s.contains("/cp/") || s.startsWith("cp/")) return Area.CP;
        if (s.contains("xadrez")) return Area.XADREZ;
        if (s.contains("musica") || s.contains("música")) return Area.MUSICA;
        return Area.OUTRO;
    }

    public static Area areaOfLesson(Lesson l) {
        return "matematica".equals(l.area()) ? Area.MATEMATICA : "programacao".equals(l.area()) ? Area.CP : Area.OUTRO;
    }

    /** Pede à IA (modelo barato) até {@code max} cards da aula; nada é salvo — a UI aprova antes ({@link #saveDrafts}). */
    public CompletableFuture<List<CardDraft>> generate(Lesson lesson, int max, BudgetGate gate) throws IOException {
        if (max <= 0) throw new IllegalArgumentException("max deve ser > 0");
        if (lesson.mdPath() == null) throw new IllegalStateException("A aula não tem arquivo .md.");
        String md = Files.readString(Path.of(lesson.mdPath()));
        if (md.length() > MAX_PROMPT_CHARS) md = md.substring(0, MAX_PROMPT_CHARS);
        AiRequest req = AiRequest.of(AiRunnerKind.CLAUDE, config.get().model(Config.MODEL_CHEAP), AiTask.CARDS,
                prompt(md, max));
        return ai.submit(req, false, gate).result().thenApply(o -> parseDrafts(o.response(), max));
    }

    static String prompt(String md, int max) {
        return "A partir da aula em Markdown abaixo, crie no máximo " + max + " cards de estudo (pergunta e resposta curtas, "
                + "em português do Brasil). Responda SOMENTE com um array JSON no formato "
                + "[{\"frente\": \"...\", \"verso\": \"...\"}], sem texto extra nem bloco de código.\n\n--- AULA ---\n" + md;
    }

    /** Aceita JSON puro ou cercado por texto/``` ; descarta itens incompletos e limita a {@code max}. */
    public static List<CardDraft> parseDrafts(String response, int max) {
        if (response == null) throw new IllegalStateException("Resposta vazia da IA.");
        int a = response.indexOf('['), b = response.lastIndexOf(']');
        if (a < 0 || b <= a) throw new IllegalStateException("A IA não devolveu um array JSON.");
        try {
            JsonNode arr = JSON.readTree(response.substring(a, b + 1));
            List<CardDraft> out = new ArrayList<>();
            for (JsonNode n : arr) {
                String f = text(n, "frente", "front"), v = text(n, "verso", "back");
                if (f != null && v != null) out.add(new CardDraft(f, v));
                if (out.size() >= max) break;
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("JSON inválido da IA: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && v.isTextual() && !v.asText().isBlank()) return v.asText().strip();
        }
        return null;
    }

    /** Salva os rascunhos aprovados. Devolve quantos eram novos. */
    public int saveDrafts(List<CardDraft> drafts, Area area, CardOrigin origin, String sourceRef) {
        int n = 0;
        for (CardDraft d : drafts) if (add(d.front(), d.back(), area, origin, sourceRef).isPresent()) n++;
        return n;
    }

    public static String deckOf(Area a) {
        return "Estudos::" + switch (a) {
            case CP -> "CP";
            case MATEMATICA -> "Matemática";
            case XADREZ -> "Xadrez";
            case MUSICA -> "Música";
            case FACULDADE -> "Faculdade";
            case OUTRO -> "Outro";
        };
    }

    /** Envia ao Anki os cards ainda não sincronizados, um baralho por área. */
    public SyncResult sync() throws AnkiException {
        List<Card> pending = repo.list(null, false);
        Map<Area, List<Card>> byArea = new EnumMap<>(Area.class);
        for (Card c : pending) byArea.computeIfAbsent(c.area(), k -> new ArrayList<>()).add(c);
        int ok = 0, rejected = 0;
        String now = now();
        for (var e : byArea.entrySet()) {
            String deck = deckOf(e.getKey());
            anki.createDeck(deck);
            List<Card> cards = new ArrayList<>();
            for (Card c : e.getValue()) {
                if (c.ankiNoteId() == null) { cards.add(c); continue; }
                try { // edição pendente: atualiza a nota existente em vez de criar outra
                    anki.updateNoteFields(c.ankiNoteId(), c.front(), c.back());
                    repo.markSynced(c.id(), c.ankiNoteId(), now);
                    ok++;
                } catch (AnkiException ex) {
                    if (ex.unavailable()) throw ex;
                    rejected++;
                }
            }
            if (cards.isEmpty()) continue;
            List<Long> ids = anki.addNotes(cards.stream().map(c -> new AnkiClient.Note(deck, c.front(), c.back())).toList());
            for (int i = 0; i < cards.size(); i++) {
                Long id = ids.get(i);
                if (id == null) rejected++;
                else {
                    repo.markSynced(cards.get(i).id(), id, now);
                    ok++;
                }
            }
        }
        return new SyncResult(pending.size(), ok, rejected);
    }
}
