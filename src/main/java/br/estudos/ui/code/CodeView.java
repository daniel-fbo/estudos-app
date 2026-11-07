package br.estudos.ui.code;

import br.estudos.Services;
import br.estudos.app.code.CodeResults.*;
import br.estudos.app.code.CodeRunnerService;
import br.estudos.app.config.Config;
import br.estudos.domain.TestCase;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Tela Código: editor, casos de teste, compilar/rodar e stress test. Controller fino. */
public class CodeView {
    private final Services services;
    private final BorderPane root = new BorderPane();
    private final Label fileLabel = new Label("Nenhum arquivo");
    private final TextArea editor = new TextArea();
    private final ListView<TestCase> caseList = new ListView<>();
    private final TextArea caseIn = new TextArea();
    private final TextArea caseOut = new TextArea();
    private final TextArea output = new TextArea();
    private final Label status = new Label();
    private final AtomicBoolean stressCancel = new AtomicBoolean();
    private Path current;

    public CodeView(Services services) {
        this.services = services;
        root.setPadding(new Insets(16));
        Label title = new Label("Código");
        title.getStyleClass().add("screen-title");

        Button open = new Button("Abrir…");
        open.setOnAction(e -> choose());
        Button save = new Button("Salvar");
        save.setOnAction(e -> save());
        Button run = new Button("Compilar e rodar casos");
        run.setOnAction(e -> runAll());
        Button stress = new Button("Stress test…");
        stress.setOnAction(e -> stressDialog());
        Button cancel = new Button("Cancelar stress");
        cancel.setOnAction(e -> stressCancel.set(true));
        HBox bar = new HBox(8, open, save, run, stress, cancel, fileLabel);
        VBox top = new VBox(8, title, bar);

        editor.setStyle("-fx-font-family: monospace;");
        output.setStyle("-fx-font-family: monospace;");
        output.setEditable(false);
        output.setPrefRowCount(10);
        caseIn.setPromptText("Entrada");
        caseOut.setPromptText("Saída esperada");
        caseIn.setPrefRowCount(4);
        caseOut.setPrefRowCount(4);
        caseIn.setStyle("-fx-font-family: monospace;");
        caseOut.setStyle("-fx-font-family: monospace;");
        caseList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(TestCase t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : "#" + t.id() + " [" + t.origin() + "] "
                        + t.input().lines().findFirst().orElse(""));
            }
        });
        caseList.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> {
            if (t != null) { caseIn.setText(t.input()); caseOut.setText(t.expected()); }
        });
        Button addCase = new Button("Adicionar caso");
        addCase.setOnAction(e -> addCase());
        Button delCase = new Button("Excluir selecionado");
        delCase.setOnAction(e -> deleteCase());
        VBox cases = new VBox(8, new Label("Casos de teste"), caseList, caseIn, caseOut,
                new HBox(8, addCase, delCase));
        cases.setPrefWidth(320);
        VBox.setVgrow(caseList, Priority.ALWAYS);

        VBox center = new VBox(8, editor, new Label("Resultado"), output, status);
        VBox.setVgrow(editor, Priority.ALWAYS);
        root.setTop(top);
        root.setCenter(center);
        root.setRight(cases);
        BorderPane.setMargin(cases, new Insets(0, 0, 0, 12));
        BorderPane.setMargin(center, new Insets(8, 0, 0, 0));
    }

    public Node root() { return root; }

    private Path defaultDir() {
        Path d = Path.of(services.config().estudosRepo(), "cp", "problemas");
        return Files.isDirectory(d) ? d : Path.of(System.getProperty("user.home"));
    }

    private Path pick(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialDirectory(defaultDir().toFile());
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fontes C/C++", "*.c", "*.cpp", "*.cc"));
        File f = fc.showOpenDialog(root.getScene().getWindow());
        return f == null ? null : f.toPath();
    }

    private void choose() {
        Path p = pick("Abrir arquivo");
        if (p == null) return;
        try {
            editor.setText(Files.readString(p));
            current = p;
            fileLabel.setText(p.toString());
            refreshCases();
        } catch (Exception e) { error(e); }
    }

    private boolean save() {
        if (current == null) return false;
        try { Files.writeString(current, editor.getText()); status.setText("Salvo."); return true; }
        catch (Exception e) { error(e); return false; }
    }

    private void refreshCases() {
        if (current == null) return;
        Path p = current;
        bg(() -> services.code().cases(p), list -> caseList.getItems().setAll(list));
    }

    private void addCase() {
        if (current == null) return;
        Path p = current;
        String in = caseIn.getText(), out = caseOut.getText();
        bg(() -> { services.code().addCase(p, in, out); return null; }, r -> refreshCases());
    }

    private void deleteCase() {
        TestCase t = caseList.getSelectionModel().getSelectedItem();
        if (t == null) return;
        bg(() -> { services.code().deleteCase(t.id()); return null; }, r -> refreshCases());
    }

    private void runAll() {
        if (current == null) return;
        if (!save()) return;
        Path p = current;
        status.setText("Compilando e rodando…");
        bg(() -> services.code().runAll(p), this::showReport);
    }

    private void showReport(RunReport r) {
        StringBuilder sb = new StringBuilder();
        if (r.verdict() == Verdict.CE) sb.append("ERRO DE COMPILAÇÃO\n").append(r.compileOutput());
        else {
            if (!r.compileOutput().isBlank()) sb.append("Avisos:\n").append(r.compileOutput()).append("\n\n");
            for (CaseResult c : r.results()) {
                sb.append("#").append(c.testCase().id()).append(": ").append(c.verdict()).append('\n');
                if (!c.detail().isEmpty()) sb.append(c.detail()).append('\n');
            }
        }
        output.setText(sb.toString());
        if (r.verdict() != Verdict.CE && r.total() == 0) { status.setText("Nenhum caso de teste cadastrado."); return; }
        status.setText("Veredito: " + r.verdict() + " (" + r.passed() + "/" + r.total() + ")");
    }

    private void stressDialog() {
        if (current == null) return;
        Path sol = current;
        Path brute = pick("Força bruta");
        if (brute == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Gerador");
        fc.setInitialDirectory(defaultDir().toFile());
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Gerador", "*.py", "*.cpp", "*.c"));
        File g = fc.showOpenDialog(root.getScene().getWindow());
        if (g == null) return;
        TextInputDialog d = new TextInputDialog("1000");
        d.setHeaderText("Número de iterações");
        var n = d.showAndWait();
        if (n.isEmpty()) return;
        int iters;
        try { iters = Integer.parseInt(n.get().trim()); } catch (NumberFormatException e) { iters = 0; }
        if (iters <= 0) { status.setText("Informe um número inteiro maior que zero."); return; }
        if (!save()) return;
        stressCancel.set(false);
        status.setText("Stress test rodando…");
        int it = iters;
        bg(() -> services.code().stress(sol, brute, g.toPath(), it, stressCancel::get), r -> {
            output.setText(r.message());
            status.setText(r.diverged() ? "Divergência! Caso adicionado." : "Stress test terminou.");
            refreshCases();
        });
    }

    private interface Work<T> { T call() throws Exception; }

    private <T> void bg(Work<T> work, java.util.function.Consumer<T> done) {
        services.background().submit(() -> {
            try {
                T r = work.call();
                Platform.runLater(() -> done.accept(r));
            } catch (Throwable t) {
                Platform.runLater(() -> error(t));
            }
        });
    }

    private void error(Throwable t) {
        status.setText("Erro: " + t.getMessage());
    }
}
