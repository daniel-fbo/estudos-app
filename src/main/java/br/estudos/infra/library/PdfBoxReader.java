package br.estudos.infra.library;

import br.estudos.app.library.PdfReader;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PdfBoxReader implements PdfReader {
    @Override
    public Info info(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDDocumentInformation i = doc.getDocumentInformation();
            return new Info(i == null ? null : i.getTitle(), i == null ? null : i.getAuthor(), doc.getNumberOfPages());
        }
    }

    @Override
    public List<String> pageTexts(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper s = new PDFTextStripper();
            List<String> out = new ArrayList<>();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                s.setStartPage(p);
                s.setEndPage(p);
                out.add(s.getText(doc));
            }
            return out;
        }
    }
}
