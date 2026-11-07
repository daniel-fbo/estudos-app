package br.estudos.app.library;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Leitura de PDFs (impl.: PDFBox). */
public interface PdfReader {
    record Info(String title, String author, int pages) {}

    Info info(Path pdf) throws IOException;

    /** Texto de cada página (índice 0 = página 1). */
    List<String> pageTexts(Path pdf) throws IOException;
}
