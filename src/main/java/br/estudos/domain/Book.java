package br.estudos.domain;

import java.time.Instant;

/** Livro ou material de estudo (tabela {@code book}). */
public record Book(long id, String title, String authors, Area area, BookKind kind, String pdfPath,
                   Integer pages, int currentPage, BookStatus status, String tags, String notes,
                   Instant indexedAt) {

    public Book withProgress(int page, BookStatus s) {
        return new Book(id, title, authors, area, kind, pdfPath, pages, page, s, tags, notes, indexedAt);
    }

    public Book withIndexedAt(Instant t) {
        return new Book(id, title, authors, area, kind, pdfPath, pages, currentPage, status, tags, notes, t);
    }

    public Book withPages(Integer p) {
        return new Book(id, title, authors, area, kind, pdfPath, p, currentPage, status, tags, notes, indexedAt);
    }
}
