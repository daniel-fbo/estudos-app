package br.estudos.ui.library;

import br.estudos.Services;
import br.estudos.app.ai.AiOutcome;
import br.estudos.app.library.LibraryService;
import br.estudos.app.tasks.TaskHandle;
import br.estudos.domain.*;
import br.estudos.ui.ai.FxBudgetGate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/** Tela "Biblioteca": livros, importar pasta, busca no texto, progresso e perguntas ao livro. */
public class LibraryView {
    private final Services services;
    private final LibraryService lib;
    private final VBox root = new VBox(10);
    private final Label status = new Label();
    private final TextField filter = new TextField();
    private final ComboBox<Object> areaFilter = new ComboBox<>();
    private final ComboBox<Object> statusFilter = new ComboBox<>();
    private final VBox books = new VBox(6);
    private final TextField fullText = new TextField();
    private final VBox results = new VBox(4);

    public LibraryView(Services services) {
        this.services = services;
        this.lib = services.library();
        root.setPadding(new Insets(20));
        Label title = new Label("Biblioteca");
        title.getStyleClass().add("screen-title");

        Button add = new Button("Adicionar livro");
        add.setOnAction(e -> edit(null));
        Button imp = new Button("Importar pasta");
        imp.setOnAction(e -> importFolder());
        HBox actions = new HBox(8, add, imp);

        fullText.setPromptText("Buscar no texto de todos os livros");
        fullText.setOnAction(e -> search());
        Button go = new Button("Buscar");
        go.setOnAction(e -> search());
        HBox.setHgrow(fullText, Priority.ALWAYS);
        HBox searchRow = new HBox(8, fullText, go);

        filter.setPromptText("Filtrar por título, autor ou tag");
        filter.textProperty().addListener((o, a, b) -> refresh());
        areaFilter.getItems().add("Todas as áreas");
        areaFilter.getItems().addAll((Object[]) Area.values());
        areaFilter.getSelectionModel().selectFirst();
        areaFilter.valueProperty().addListener((o, a, b) -> refresh());
        statusFilter.getItems().add("Todos os status");
        statusFilter.getItems().addAll((Object[]) BookStatus.values());
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.valueProperty().addListener((o, a, b) -> refresh());
        HBox.setHgrow(filter, Priority.ALWAYS);
        HBox filters = new HBox(8, filter, areaFilter, statusFilter);

        VBox content = new VBox(10, new Label("Resultados da busca"), results, new Separator(),
                new Label("Livros"), books);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(title, actions, searchRow, status, filters, scroll);
        refresh();
    }

    public Node root() { return root; }

    private <T> void bg(Supplier<T> work, java.util.function.Consumer<T> ui) {
        services.background().submit(() -> {
            try {
                T r = work.get();
                Platform.runLater(() -> ui.accept(r));
            } catch (Throwable e) {
                Platform.runLater(() -> status.setText("Erro: " + e.getMessage()));
            }
        });
    }

    private void refresh() {
        String q = filter.getText();
        Area a = areaFilter.getValue() instanceof Area x ? x : null;
        BookStatus s = statusFilter.getValue() instanceof BookStatus x ? x : null;
        bg(() -> lib.list(q, a, s), this::show);
    }

    private void show(List<Book> rows) {
        books.getChildren().clear();
        if (rows.isEmpty()) books.getChildren().add(new Label("Nenhum livro."));
        for (Book b : rows) books.getChildren().add(row(b));
    }

    private Node row(Book b) {
        String pg = b.pages() == null ? "?" : String.valueOf(b.pages());
        Label name = new Label(b.title() + "  [" + b.area() + " · " + b.kind() + " · " + b.status() + "]  p. "
                + b.currentPage() + "/" + pg + (b.indexedAt() == null ? "  (não indexado)" : ""));
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Button open = new Button("Abrir PDF");
        open.setDisable(b.pdfPath() == null);
        open.setOnAction(e -> open(b.pdfPath()));
        Button page = new Button("Página…");
        page.setOnAction(e -> {
            TextInputDialog d = new TextInputDialog(String.valueOf(b.currentPage()));
            d.setHeaderText("Página atual de " + b.title());
            d.showAndWait().ifPresent(t -> {
                try {
                    int p = Integer.parseInt(t.trim());
                    bg(() -> lib.updateProgress(b, p), x -> refresh());
                } catch (NumberFormatException ex) {
                    status.setText("Página inválida.");
                }
            });
        });
        Button ask = new Button("Perguntar");
        ask.setDisable(b.pdfPath() == null);
        ask.setOnAction(e -> ask(b));
        Button ed = new Button("Editar");
        ed.setOnAction(e -> edit(b));
        Button del = new Button("Excluir");
        del.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Excluir \"" + b.title() + "\" da biblioteca? (o PDF não é apagado)",
                    ButtonType.YES, ButtonType.NO);
            if (a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES)
                bg(() -> { try { lib.delete(b.id()); } catch (java.io.IOException ex) { throw new IllegalStateException(ex); } return 0; },
                        x -> refresh());
        });
        return new HBox(6, name, open, page, ask, ed, del);
    }

    // ---- cadastro ----

    private void edit(Book b) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(b == null ? "Novo livro" : "Editar livro");
        TextField title = new TextField(b == null ? "" : b.title());
        TextField authors = new TextField(b == null || b.authors() == null ? "" : b.authors());
        ComboBox<Area> area = new ComboBox<>();
        area.getItems().addAll(Area.values());
        area.setValue(b == null ? Area.MATEMATICA : b.area());
        ComboBox<BookKind> kind = new ComboBox<>();
        kind.getItems().addAll(BookKind.values());
        kind.setValue(b == null ? BookKind.LIVRO : b.kind());
        ComboBox<BookStatus> st = new ComboBox<>();
        st.getItems().addAll(BookStatus.values());
        st.setValue(b == null ? BookStatus.QUERO_LER : b.status());
        TextField pdf = new TextField(b == null || b.pdfPath() == null ? "" : b.pdfPath());
        Button pick = new Button("Escolher…");
        pick.setOnAction(e -> {
            File f = new FileChooser().showOpenDialog(root.getScene().getWindow());
            if (f != null) pdf.setText(f.getAbsolutePath());
        });
        TextField pages = new TextField(b == null || b.pages() == null ? "" : String.valueOf(b.pages()));
        TextField tags = new TextField(b == null || b.tags() == null ? "" : b.tags());
        TextArea notes = new TextArea(b == null || b.notes() == null ? "" : b.notes());
        notes.setPrefRowCount(3);
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        Object[][] rows = {{"Título", title}, {"Autores", authors}, {"Área", area}, {"Tipo", kind}, {"Status", st},
                {"PDF", new HBox(6, pdf, pick)}, {"Total de páginas", pages}, {"Tags", tags}, {"Notas", notes}};
        for (int i = 0; i < rows.length; i++) {
            g.add(new Label((String) rows[i][0]), 0, i);
            g.add((Node) rows[i][1], 1, i);
        }
        d.getDialogPane().setContent(g);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        Integer total;
        try {
            total = pages.getText().isBlank() ? null : Integer.valueOf(pages.getText().trim());
        } catch (NumberFormatException ex) {
            status.setText("Total de páginas inválido.");
            return;
        }
        String path = pdf.getText().isBlank() ? null : pdf.getText().trim();
        Book nb = new Book(b == null ? 0 : b.id(), title.getText().trim(), nullIfBlank(authors.getText()), area.getValue(),
                kind.getValue(), path, total, b == null ? 0 : b.currentPage(), st.getValue(), nullIfBlank(tags.getText()),
                nullIfBlank(notes.getText()), b == null ? null : b.indexedAt());
        bg(() -> {
            if (b == null) {
                Book saved = lib.add(nb);
                return saved;
            }
            lib.update(nb);
            return nb;
        }, saved -> {
            refresh();
            if (saved.pdfPath() != null && saved.indexedAt() == null) index(saved);
        });
    }

    private static String nullIfBlank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private void index(Book b) {
        status.setText("Indexando " + b.title() + "…");
        services.tasks().submit("Indexar " + b.title(), tok -> {
            lib.indexBook(b);
            return null;
        }).result().whenComplete((r, err) -> Platform.runLater(() -> {
            status.setText(err == null ? "Indexado: " + b.title() : "Erro ao indexar: " + err.getMessage());
            refresh();
        }));
    }

    private void importFolder() {
        File dir = new DirectoryChooser().showDialog(root.getScene().getWindow());
        if (dir == null) return;
        ChoiceDialog<Area> ad = new ChoiceDialog<>(Area.MATEMATICA, Area.values());
        ad.setHeaderText("Área dos PDFs importados");
        Area area = ad.showAndWait().orElse(null);
        if (area == null) return;
        status.setText("Importação enviada à fila (veja a tela Tarefas).");
        TaskHandle<LibraryService.ImportResult> h = services.tasks().submit("Importar " + dir.getName(),
                tok -> lib.importFolder(dir.toPath(), area, BookKind.LIVRO, m -> {}, tok));
        h.result().whenComplete((r, err) -> Platform.runLater(() -> {
            if (err != null) status.setText("Importação: " + (h.result().isCancelled() ? "cancelada" : err.getMessage()));
            else status.setText("Importados " + r.imported() + ", já existentes " + r.skipped()
                    + (r.failures().isEmpty() ? "" : ", falhas: " + String.join("; ", r.failures())));
            refresh();
        }));
    }

    // ---- busca ----

    private void search() {
        String q = fullText.getText();
        if (q == null || q.isBlank()) return;
        bg(() -> {
            try {
                return lib.search(q, 30);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }, hits -> {
            results.getChildren().clear();
            if (hits.isEmpty()) results.getChildren().add(new Label("Nada encontrado."));
            for (var h : hits) {
                Button b = new Button(h.book().title() + " — p. " + h.page());
                b.setOnAction(e -> open(h.book().pdfPath()));
                results.getChildren().addAll(b, snippet(h.snippet()));
            }
        });
    }

    private static TextFlow snippet(String s) {
        TextFlow f = new TextFlow();
        boolean bold = false;
        StringBuilder cur = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '«' || c == '»') {
                flush(f, cur, bold);
                bold = c == '«';
            } else cur.append(c);
        }
        flush(f, cur, bold);
        return f;
    }

    private static void flush(TextFlow f, StringBuilder cur, boolean bold) {
        if (cur.isEmpty()) return;
        Text t = new Text(cur.toString());
        if (bold) t.setFont(Font.font(null, FontWeight.BOLD, -1));
        f.getChildren().add(t);
        cur.setLength(0);
    }

    // ---- perguntar ----

    private void ask(Book b) {
        TextInputDialog d = new TextInputDialog();
        d.setHeaderText("Pergunta sobre \"" + b.title() + "\"");
        String q = d.showAndWait().orElse("").trim();
        if (q.isEmpty()) return;
        if (lib.agyAvailable()) {
            status.setText("Pergunta enviada ao agy (veja a tela Tarefas).");
            try {
                lib.askAgy(b, q, new FxBudgetGate()).result().whenComplete((o, err) -> Platform.runLater(() -> {
                    if (err == null) showAnswer(b, o);
                    else offerClaude(b, q, err.getMessage());
                }));
            } catch (RuntimeException e) {
                status.setText("Erro: " + e.getMessage());
            }
        } else {
            offerClaude(b, q, "o agy não está disponível");
        }
    }

    private void offerClaude(Book b, String q, String why) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "O agy falhou (" + why + ").\nPerguntar ao Claude com as "
                + LibraryService.TOP_PAGES + " páginas mais relevantes do índice?", ButtonType.YES, ButtonType.NO);
        if (a.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            status.setText("Pergunta cancelada.");
            return;
        }
        status.setText("Pergunta enviada ao Claude (veja a tela Tarefas).");
        try {
            lib.askClaude(b, q, new FxBudgetGate()).result().whenComplete((o, err) -> Platform.runLater(() -> {
                if (err == null) showAnswer(b, o);
                else status.setText("Erro: " + err.getMessage());
            }));
        } catch (Exception e) {
            status.setText("Erro: " + e.getMessage());
        }
    }

    private void showAnswer(Book b, AiOutcome o) {
        status.setText("");
        TextArea ta = new TextArea(o.response());
        ta.setEditable(false);
        ta.setWrapText(true);
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(b.title() + (o.fromCache() ? " (resposta em cache)" : ""));
        a.getDialogPane().setContent(ta);
        a.show();
    }

    private void open(String path) {
        if (path == null) return;
        services.background().submit(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                    Desktop.getDesktop().open(new File(path));
                else new ProcessBuilder("xdg-open", path).start();
            } catch (Exception e) {
                Platform.runLater(() -> status.setText("Não abriu " + path + ": " + e.getMessage()));
            }
        });
    }
}
