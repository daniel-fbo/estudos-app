package br.estudos.app.library;

import br.estudos.domain.Area;
import br.estudos.domain.Book;
import br.estudos.domain.BookStatus;

import java.util.List;
import java.util.Optional;

public interface BookRepository {
    Book insert(Book b);
    void update(Book b);
    void delete(long id);
    Optional<Book> find(long id);
    Optional<Book> findByPath(String pdfPath);
    /** Filtros nulos/vazios são ignorados; {@code q} busca em título, autores e tags. */
    List<Book> list(String q, Area area, BookStatus status);
}
