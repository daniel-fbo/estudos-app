package br.estudos.app.library;

import java.io.IOException;
import java.util.List;

/** Índice de texto por página (impl.: Lucene). */
public interface PageIndex {
    /** Um resultado; {@code snippet} traz os termos achados entre «» . */
    record Hit(long bookId, int page, String snippet, float score) {}

    /** Substitui tudo que havia do livro; {@code pages.get(0)} é a página 1. */
    void replaceBook(long bookId, List<String> pages) throws IOException;

    void deleteBook(long bookId) throws IOException;

    List<Hit> search(String query, int limit) throws IOException;

    /** Melhores páginas de um livro para a consulta. */
    List<Hit> topPages(long bookId, String query, int limit) throws IOException;

    /** Texto indexado de uma página, ou {@code null}. */
    String pageText(long bookId, int page) throws IOException;
}
