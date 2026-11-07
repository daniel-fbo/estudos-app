package br.estudos.infra.library;

import br.estudos.app.library.PageIndex;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Um documento Lucene por página: {@code book} (id), {@code page}, {@code text}. */
public class LucenePageIndex implements PageIndex, AutoCloseable {
    private final Directory dir;
    private final Analyzer analyzer = new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String field) {
            StandardTokenizer src = new StandardTokenizer();
            TokenStream ts = new FoldFilter(new LowerCaseFilter(src));
            return new TokenStreamComponents(src, ts);
        }
    };

    /** Remove acentos de cada token (mínimos → minimos). */
    private static final class FoldFilter extends TokenFilter {
        private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
        FoldFilter(TokenStream in) { super(in); }
        @Override
        public boolean incrementToken() throws IOException {
            if (!input.incrementToken()) return false;
            String f = fold(term.toString());
            term.setEmpty().append(f);
            return true;
        }
    }

    /** Minúsculas sem acento, preservando o comprimento (1 char → 1 char). */
    static String fold(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String n = java.text.Normalizer.normalize(String.valueOf(c), java.text.Normalizer.Form.NFD);
            char b = n.isEmpty() ? c : n.charAt(0);
            char l = Character.toLowerCase(b);
            sb.append(Character.isLetterOrDigit(l) || !Character.isLetterOrDigit(c) ? l : c);
        }
        return sb.toString();
    }
    private final IndexWriter writer;

    public LucenePageIndex(Path folder) throws IOException {
        this.dir = FSDirectory.open(folder);
        this.writer = new IndexWriter(dir, new IndexWriterConfig(analyzer));
        writer.commit();
    }

    @Override
    public void replaceBook(long bookId, List<String> pages) throws IOException {
        writer.deleteDocuments(LongPoint.newExactQuery("book", bookId));
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            Document d = new Document();
            d.add(new LongPoint("book", bookId));
            d.add(new StoredField("bookId", bookId));
            d.add(new IntPoint("pageNo", i + 1));
            d.add(new StoredField("page", i + 1));
            d.add(new TextField("text", pages.get(i) == null ? "" : pages.get(i), Field.Store.YES));
            docs.add(d);
        }
        writer.addDocuments(docs);
        writer.commit();
    }

    @Override
    public void deleteBook(long bookId) throws IOException {
        writer.deleteDocuments(LongPoint.newExactQuery("book", bookId));
        writer.commit();
    }

    @Override
    public List<Hit> search(String query, int limit) throws IOException {
        return run(textQuery(query), query, limit);
    }

    @Override
    public List<Hit> topPages(long bookId, String query, int limit) throws IOException {
        Query t = textQuery(query);
        if (t == null) return List.of();
        Query q = new BooleanQuery.Builder().add(t, BooleanClause.Occur.MUST)
                .add(LongPoint.newExactQuery("book", bookId), BooleanClause.Occur.FILTER).build();
        return run(q, query, limit);
    }

    @Override
    public String pageText(long bookId, int page) throws IOException {
        Query q = new BooleanQuery.Builder().add(LongPoint.newExactQuery("book", bookId), BooleanClause.Occur.MUST)
                .add(IntPoint.newExactQuery("pageNo", page), BooleanClause.Occur.MUST).build();
        try (DirectoryReader r = DirectoryReader.open(writer)) {
            TopDocs td = new IndexSearcher(r).search(q, 1);
            if (td.scoreDocs.length == 0) return null;
            return new IndexSearcher(r).storedFields().document(td.scoreDocs[0].doc).get("text");
        }
    }

    private Query textQuery(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            String safe = QueryParser.escape(query.trim());
            safe = java.util.Arrays.stream(safe.split("\\s+"))
                    .map(w -> w.equals("AND") || w.equals("OR") || w.equals("NOT") ? w.toLowerCase(Locale.ROOT) : w)
                    .collect(java.util.stream.Collectors.joining(" "));
            return new QueryParser("text", analyzer).parse(safe);
        } catch (ParseException e) {
            return null;
        }
    }

    private List<Hit> run(Query q, String raw, int limit) throws IOException {
        if (q == null) return List.of();
        try (DirectoryReader r = DirectoryReader.open(writer)) {
            IndexSearcher s = new IndexSearcher(r);
            List<Hit> out = new ArrayList<>();
            for (ScoreDoc sd : s.search(q, limit).scoreDocs) {
                Document d = s.storedFields().document(sd.doc);
                out.add(new Hit(d.getField("bookId").numericValue().longValue(),
                        d.getField("page").numericValue().intValue(), snippet(d.get("text"), raw), sd.score));
            }
            return out;
        }
    }

    /** Trecho de ~160 caracteres em volta do primeiro termo, com os termos entre «». */
    static String snippet(String text, String query) {
        String flat = text.replaceAll("\\s+", " ").trim();
        List<String> terms = new ArrayList<>();
        for (String t : fold(query).split("[^\\p{L}\\p{N}]+")) if (t.length() > 1) terms.add(t);
        String low = fold(flat);
        int first = -1;
        for (String t : terms) {
            int i = low.indexOf(t);
            if (i >= 0 && (first < 0 || i < first)) first = i;
        }
        int from = Math.max(0, first < 0 ? 0 : first - 60);
        int to = Math.min(flat.length(), from + 160);
        String win = flat.substring(from, to);
        String wlow = low.substring(from, to);
        StringBuilder sb = new StringBuilder(from > 0 ? "…" : "");
        int i = 0;
        while (i < win.length()) {
            int hit = 0;
            for (String t : terms) if (wlow.startsWith(t, i) && t.length() > hit) hit = t.length();
            if (hit > 0) {
                sb.append('«').append(win, i, i + hit).append('»');
                i += hit;
            } else {
                sb.append(win.charAt(i++));
            }
        }
        return sb.append(to < flat.length() ? "…" : "").toString();
    }

    @Override
    public void close() throws IOException {
        writer.close();
        dir.close();
    }
}
