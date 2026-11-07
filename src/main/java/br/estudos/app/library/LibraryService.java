package br.estudos.app.library;

import br.estudos.app.ai.AiOutcome;
import br.estudos.app.ai.AiService;
import br.estudos.app.ai.BudgetGate;
import br.estudos.app.config.Config;
import br.estudos.app.tasks.CancellationToken;
import br.estudos.app.tasks.TaskHandle;
import br.estudos.domain.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Biblioteca: cadastro de livros, importação/indexação de PDFs, busca, progresso e perguntas à IA. */
public class LibraryService {
    public static final int TOP_PAGES = 8;
    private static final int MAX_PAGE_CHARS = 6000;

    public record ImportResult(int imported, int skipped, List<String> failures) {}
    public record SearchHit(Book book, int page, String snippet) {}

    private final BookRepository repo;
    private final PdfReader pdf;
    private final PageIndex index;
    private final AiService ai;
    private final Supplier<Config> config;
    private final Clock clock;

    public LibraryService(BookRepository repo, PdfReader pdf, PageIndex index, AiService ai,
                          Supplier<Config> config, Clock clock) {
        this.repo = repo;
        this.pdf = pdf;
        this.index = index;
        this.ai = ai;
        this.config = config;
        this.clock = clock;
    }

    // ---- cadastro ----

    public List<Book> list(String q, Area area, BookStatus status) { return repo.list(q, area, status); }

    public Book add(Book b) {
        if (b.title() == null || b.title().isBlank()) throw new IllegalArgumentException("título vazio");
        return repo.insert(b);
    }

    public void update(Book b) { repo.update(b); }

    public void delete(long id) throws IOException {
        index.deleteBook(id);
        repo.delete(id);
    }

    /** Atualiza a página atual (limitada a 0..total) e ajusta o status: LENDO ao começar, LIDO na última página. */
    public Book updateProgress(Book b, int page) {
        int p = Math.max(0, page);
        if (b.pages() != null) p = Math.min(p, b.pages());
        BookStatus s = b.status();
        if (b.pages() != null && b.pages() > 0 && p >= b.pages()) s = BookStatus.LIDO;
        else if (p > 0 && s != BookStatus.LENDO) s = BookStatus.LENDO;
        Book nb = b.withProgress(p, s);
        repo.update(nb);
        return nb;
    }

    // ---- importação / indexação ----

    /** Varre a pasta atrás de PDFs; cadastra os novos e indexa o texto. Pode ser cancelada entre arquivos. */
    public ImportResult importFolder(Path dir, Area area, BookKind kind, Consumer<String> progress,
                                     CancellationToken token) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.walk(dir)) {
            files = s.filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                    .sorted().toList();
        }
        int imported = 0, skipped = 0;
        List<String> failures = new ArrayList<>();
        int n = 0;
        for (Path f : files) {
            token.throwIfCancelled();
            progress.accept("(" + (++n) + "/" + files.size() + ") " + f.getFileName());
            String path = f.toAbsolutePath().toString();
            try {
                var existing = repo.findByPath(path);
                if (existing.isPresent() && existing.get().indexedAt() != null) {
                    skipped++;
                    continue;
                }
                PdfReader.Info info = pdf.info(f);
                Book b = existing.orElseGet(() -> repo.insert(new Book(0, titleOf(info, f), clean(info.author()), area, kind,
                        path, info.pages(), 0, BookStatus.QUERO_LER, null, null, null)));
                indexBook(b);
                imported++;
            } catch (IOException | RuntimeException e) {
                failures.add(f.getFileName() + ": " + e.getMessage());
            }
        }
        return new ImportResult(imported, skipped, failures);
    }

    /** (Re)indexa o PDF de um livro e grava {@code indexed_at} e o total de páginas. */
    public Book indexBook(Book b) throws IOException {
        if (b.pdfPath() == null) throw new IOException("livro sem PDF");
        List<String> texts = pdf.pageTexts(Path.of(b.pdfPath()));
        index.replaceBook(b.id(), texts);
        Book nb = b.withPages(texts.size()).withIndexedAt(clock.instant());
        repo.update(nb);
        return nb;
    }

    private static String titleOf(PdfReader.Info info, Path f) {
        String t = clean(info.title());
        if (t != null) return t;
        String name = f.getFileName().toString();
        return name.substring(0, name.length() - 4);
    }

    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    // ---- busca ----

    public List<SearchHit> search(String query, int limit) throws IOException {
        List<SearchHit> out = new ArrayList<>();
        for (PageIndex.Hit h : index.search(query, limit)) {
            repo.find(h.bookId()).ifPresent(b -> out.add(new SearchHit(b, h.page(), h.snippet())));
        }
        return out;
    }

    // ---- perguntar a este livro ----

    public boolean agyAvailable() { return ai.supported(AiRunnerKind.AGY); }

    /** Envia o PDF inteiro ao agy (contexto longo). Resposta em cache (mesma pergunta + mesmo PDF). */
    public TaskHandle<AiOutcome> askAgy(Book b, String question, BudgetGate gate) {
        requirePdf(b);
        String prompt = "Leia o PDF \"" + b.pdfPath() + "\" e responda em português do Brasil, citando páginas "
                + "quando possível.\n\nPergunta: " + question.trim();
        return ai.submit(new AiRequest(AiRunnerKind.AGY, null, AiTask.LIVRO, prompt, List.of(Path.of(b.pdfPath())), null),
                false, gate);
    }

    /** Alternativa: Claude com as {@value #TOP_PAGES} páginas mais relevantes do índice. */
    public TaskHandle<AiOutcome> askClaude(Book b, String question, BudgetGate gate) throws IOException {
        requirePdf(b);
        List<PageIndex.Hit> hits = index.topPages(b.id(), question, TOP_PAGES);
        if (hits.isEmpty())
            throw new IllegalStateException("Nenhum trecho relevante no índice (o livro foi indexado?).");
        StringBuilder sb = new StringBuilder("Responda em português do Brasil usando apenas os trechos do livro \"")
                .append(b.title()).append("\" abaixo e cite as páginas.\n\nPergunta: ").append(question.trim()).append("\n");
        for (PageIndex.Hit h : hits) {
            String t = index.pageText(b.id(), h.page());
            if (t == null) continue;
            if (t.length() > MAX_PAGE_CHARS) t = t.substring(0, MAX_PAGE_CHARS);
            sb.append("\n--- Página ").append(h.page()).append(" ---\n").append(t.trim()).append('\n');
        }
        return ai.submit(AiRequest.of(AiRunnerKind.CLAUDE, config.get().model(Config.MODEL_DEFAULT), AiTask.LIVRO,
                sb.toString()), false, gate);
    }

    private static void requirePdf(Book b) {
        if (b.pdfPath() == null || !Files.isRegularFile(Path.of(b.pdfPath())))
            throw new IllegalStateException("PDF não encontrado: " + b.pdfPath());
    }
}
