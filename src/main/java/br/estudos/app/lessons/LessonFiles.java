package br.estudos.app.lessons;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utilitários puros: slug, similaridade de títulos e localização/movimentação dos arquivos gerados. */
final class LessonFiles {
    private LessonFiles() {}

    static String slug(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase();
        n = n.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (n.length() > 60) n = n.substring(0, 60).replaceAll("-+$", "");
        return n.isEmpty() ? "aula" : n;
    }

    static Set<String> words(String s) {
        return Arrays.stream(slug(s).split("-")).filter(w -> w.length() > 2).collect(Collectors.toSet());
    }

    /** Jaccard das palavras (0..1). */
    static double similarity(String a, String b) {
        Set<String> x = words(a), y = words(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(x);
        inter.retainAll(y);
        Set<String> uni = new HashSet<>(x);
        uni.addAll(y);
        return (double) inter.size() / uni.size();
    }

    record Found(Path pdf, Path md) {}

    /** Procura .pdf/.md modificados desde {@code since} sob {@code root}, ignorando {@code skip} e pastas ocultas. */
    static Found findNew(Path root, Path skip, Instant since) {
        if (!Files.isDirectory(root)) return new Found(null, null);
        FileTime limit = FileTime.from(since);
        List<Path> pdfs = new ArrayList<>(), mds = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile).forEach(p -> {
                Path rel = root.relativize(p);
                for (Path part : rel) if (part.toString().startsWith(".") || part.toString().equals("node_modules")) return;
                if (skip != null && p.startsWith(skip)) return;
                String name = p.getFileName().toString().toLowerCase();
                try {
                    if (Files.getLastModifiedTime(p).compareTo(limit) < 0) return;
                } catch (IOException e) {
                    return;
                }
                if (name.endsWith(".pdf")) pdfs.add(p);
                else if (name.endsWith(".md")) mds.add(p);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Comparator<Path> byTime = Comparator.comparing(p -> {
            try { return Files.getLastModifiedTime(p); } catch (IOException e) { return FileTime.fromMillis(0); }
        });
        Path pdf = pdfs.stream().max(byTime).orElse(null);
        Path md = null;
        if (pdf != null) {
            String stem = stem(pdf);
            md = mds.stream().filter(m -> stem(m).equals(stem)).findFirst().orElse(null);
        }
        if (md == null) md = mds.stream().max(byTime).orElse(null);
        return new Found(pdf, md);
    }

    private static String stem(Path p) {
        String n = p.getFileName().toString();
        int i = n.lastIndexOf('.');
        return i < 0 ? n : n.substring(0, i);
    }

    static Path move(Path file, Path dir) throws IOException {
        if (file == null) return null;
        Files.createDirectories(dir);
        Path dest = dir.resolve(file.getFileName());
        if (dest.equals(file)) return dest;
        return Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);
    }
}
