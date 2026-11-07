package br.estudos.ui.lessons;

import br.estudos.Services;
import br.estudos.app.lessons.LessonService;
import br.estudos.domain.Lesson;
import br.estudos.domain.LessonStatus;
import br.estudos.ui.ai.FxBudgetGate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

/** Tela "Aulas": formulário de geração e lista de aulas (busca por título e área). */
public class LessonsView {
    private final Services services;
    private final LessonService lessons;
    private final VBox root = new VBox(12);
    private final ComboBox<String> kind = new ComboBox<>();
    private final TextField content = new TextField();
    private final TextField topics = new TextField();
    private final Label status = new Label();
    private final TextField search = new TextField();
    private final ComboBox<String> areaFilter = new ComboBox<>();
    private final VBox list = new VBox(6);

    public LessonsView(Services services) {
        this.services = services;
        this.lessons = services.lessons();
        root.setPadding(new Insets(20));
        Label title = new Label("Aulas");
        title.getStyleClass().add("screen-title");

        kind.getItems().addAll(LessonService.KIND_MATH, LessonService.KIND_PROG);
        kind.getSelectionModel().selectFirst();
        content.setPromptText("Conteúdo (ex.: Teoria dos grupos)");
        topics.setPromptText("Tópicos (ex.: subgrupos, Lagrange)");
        HBox.setHgrow(content, Priority.ALWAYS);
        HBox.setHgrow(topics, Priority.ALWAYS);
        Button gen = new Button("Gerar");
        gen.setOnAction(e -> generate());
        HBox form = new HBox(8, kind, content, topics, gen);

        search.setPromptText("Buscar por título");
        search.textProperty().addListener((o, a, b) -> refresh());
        areaFilter.getItems().addAll("Todas", "matematica", "programacao");
        areaFilter.getSelectionModel().selectFirst();
        areaFilter.valueProperty().addListener((o, a, b) -> refresh());
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox filters = new HBox(8, search, areaFilter);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(title, form, status, filters, scroll);
        refresh();
    }

    public Node root() { return root; }

    private void generate() {
        String k = kind.getValue(), c = content.getText(), t = topics.getText();
        if (c == null || c.isBlank()) {
            status.setText("Informe o conteúdo.");
            return;
        }
        status.setText("Verificando aulas parecidas…");
        services.background().submit(() -> {
            try {
                List<Lesson> sim = lessons.similar(c.trim());
                Platform.runLater(() -> confirmAndStart(k, c, t, sim));
            } catch (Throwable e) {
                Platform.runLater(() -> status.setText("Erro: " + e.getMessage()));
            }
        });
    }

    private void confirmAndStart(String k, String c, String t, List<Lesson> similar) {
        if (!similar.isEmpty()) {
            StringBuilder sb = new StringBuilder("Já existe aula com título parecido:\n");
            similar.stream().limit(5).forEach(l -> sb.append("• ").append(l.title()).append('\n'));
            sb.append("\nGerar mesmo assim?");
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, sb.toString(), ButtonType.YES, ButtonType.NO);
            a.setHeaderText("Aula parecida");
            if (a.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
                status.setText("");
                return;
            }
        }
        status.setText("Aula enviada à fila (veja a tela Tarefas).");
        try {
            lessons.generate(k, c, t, new FxBudgetGate()).whenComplete((l, err) -> Platform.runLater(() -> {
                if (err != null) status.setText("Erro: " + err.getMessage());
                refresh();
            }));
        } catch (RuntimeException e) {
            status.setText("Erro: " + e.getMessage());
        }
        refresh();
    }

    private void refresh() {
        String q = search.getText(), a = areaFilter.getValue();
        String area = a == null || a.equals("Todas") ? null : a;
        services.background().submit(() -> {
            try {
                List<Lesson> rows = lessons.list(q, area);
                Platform.runLater(() -> show(rows));
            } catch (Throwable e) {
                Platform.runLater(() -> status.setText("Erro: " + e.getMessage()));
            }
        });
    }

    private void show(List<Lesson> rows) {
        list.getChildren().clear();
        if (rows.isEmpty()) list.getChildren().add(new Label("Nenhuma aula."));
        for (Lesson l : rows) list.getChildren().add(row(l));
    }

    private Node row(Lesson l) {
        Label name = new Label(l.title() + "  [" + l.area() + "]  — " + switch (l.status()) {
            case GERANDO -> "gerando";
            case PRONTA -> "pronta";
            case FALHOU -> "falhou";
        });
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox r = new HBox(8, name);
        if (l.status() == LessonStatus.PRONTA) {
            if (l.pdfPath() != null) {
                Button pdf = new Button("Abrir PDF");
                pdf.setOnAction(e -> open(new File(l.pdfPath())));
                r.getChildren().add(pdf);
            }
            File dir = new File(l.pdfPath() != null ? l.pdfPath() : l.mdPath()).getParentFile();
            Button folder = new Button("Abrir pasta");
            folder.setOnAction(e -> open(dir));
            r.getChildren().add(folder);
        } else if (l.status() == LessonStatus.FALHOU) {
            Button log = new Button("Ver log");
            log.setOnAction(e -> {
                TextArea ta = new TextArea(l.log() == null ? "(sem log)" : l.log());
                ta.setEditable(false);
                ta.setWrapText(true);
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setHeaderText("Falha: " + l.title());
                a.getDialogPane().setContent(ta);
                a.show();
            });
            r.getChildren().add(log);
        }
        return r;
    }

    private void open(File f) {
        services.background().submit(() -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(f);
                } else {
                    new ProcessBuilder("xdg-open", f.getAbsolutePath()).start();
                }
            } catch (Exception e) {
                Platform.runLater(() -> status.setText("Não abriu " + f + ": " + e.getMessage()));
            }
        });
    }
}
