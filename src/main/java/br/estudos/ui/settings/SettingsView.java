package br.estudos.ui.settings;

import br.estudos.Services;
import br.estudos.app.config.Config;
import br.estudos.app.tools.ExecutableChecker.CheckResult;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tela Configurações: edita e salva o config; testa cada executável com {@code --version}. */
public class SettingsView {

    private static final Logger log = LoggerFactory.getLogger(SettingsView.class);

    private final Services services;
    private final GridPane grid = new GridPane();
    private int row = 0;

    private final TextField estudosRepo = new TextField();
    private final TextField dbPath = new TextField();
    private final TextField claudePath = new TextField();
    private final TextField agyPath = new TextField();
    private final TextField gccPath = new TextField();
    private final TextField gppPath = new TextField();
    private final TextField cfHandle = new TextField();
    private final TextField modelLessons = new TextField();
    private final TextField modelDefault = new TextField();
    private final TextField modelCheap = new TextField();
    private final Spinner<Integer> budget = new Spinner<>(0, 10_000, 30);
    private final Label status = new Label();
    private final VBox root;

    public SettingsView(Services services) {
        this.services = services;
        grid.setHgap(10);
        grid.setVgap(8);

        Button chooseRepo = new Button("Escolher…");
        chooseRepo.setOnAction(e -> chooseDirectory());
        addRow("Repositório estudos", estudosRepo, chooseRepo);
        addRow("Banco de dados", dbPath, new Label("(vale após reiniciar)"));
        addExecutable("claude", claudePath);
        addExecutable("agy", agyPath);
        addExecutable("gcc", gccPath);
        addExecutable("g++", gppPath);
        addRow("Handle Codeforces", cfHandle, null);
        addRow("Modelo para aulas", modelLessons, null);
        addRow("Modelo padrão", modelDefault, null);
        addRow("Modelo barato", modelCheap, null);
        budget.setEditable(true);
        addRow("Chamadas de IA por dia", budget, null);

        Button save = new Button("Salvar");
        save.setDefaultButton(true);
        save.setOnAction(e -> save());

        Label title = new Label("Configurações");
        title.getStyleClass().add("screen-title");
        root = new VBox(16, title, grid, new HBox(10, save, status));
        root.setPadding(new Insets(24));
        fill(services.config());
    }

    public Parent root() {
        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        return sp;
    }

    private void fill(Config c) {
        estudosRepo.setText(c.estudosRepo());
        dbPath.setText(c.dbPath());
        claudePath.setText(c.claudePath());
        agyPath.setText(c.agyPath());
        gccPath.setText(c.gccPath());
        gppPath.setText(c.gppPath());
        cfHandle.setText(c.codeforcesHandle());
        modelLessons.setText(c.models().getOrDefault(Config.MODEL_LESSONS, ""));
        modelDefault.setText(c.models().getOrDefault(Config.MODEL_DEFAULT, ""));
        modelCheap.setText(c.models().getOrDefault(Config.MODEL_CHEAP, ""));
        budget.getValueFactory().setValue(c.dailyAiBudget());
    }

    private Config read() {
        Map<String, String> models = new LinkedHashMap<>(services.config().models());
        models.put(Config.MODEL_LESSONS, modelLessons.getText().strip());
        models.put(Config.MODEL_DEFAULT, modelDefault.getText().strip());
        models.put(Config.MODEL_CHEAP, modelCheap.getText().strip());
        return new Config(
                estudosRepo.getText().strip(), dbPath.getText().strip(),
                claudePath.getText().strip(), agyPath.getText().strip(),
                gccPath.getText().strip(), gppPath.getText().strip(),
                cfHandle.getText().strip(), models, budget.getValue());
    }

    private void save() {
        try {
            budget.increment(0); // confirma o texto digitado no spinner
            services.saveConfig(read());
            status.setText("Salvo.");
        } catch (Exception ex) {
            log.error("Erro ao salvar config", ex);
            status.setText("Erro ao salvar: " + ex.getMessage());
        }
    }

    private void chooseDirectory() {
        DirectoryChooser dc = new DirectoryChooser();
        File current = new File(estudosRepo.getText());
        if (current.isDirectory()) dc.setInitialDirectory(current);
        File f = dc.showDialog(root.getScene().getWindow());
        if (f != null) estudosRepo.setText(f.getAbsolutePath());
    }

    private void addExecutable(String name, TextField field) {
        Label result = new Label();
        result.setWrapText(true);
        result.setMaxWidth(380);
        Button test = new Button("Testar");
        test.setOnAction(e -> {
            String path = field.getText();
            test.setDisable(true);
            result.setText("Testando…");
            Task<CheckResult> task = new Task<>() {
                @Override
                protected CheckResult call() {
                    return services.executableChecker().check(path);
                }
            };
            task.setOnSucceeded(ev -> {
                CheckResult r = task.getValue();
                result.setText((r.ok() ? "✔ " : "✘ ") + r.message());
                test.setDisable(false);
            });
            task.setOnFailed(ev -> {
                log.error("Teste de {} falhou", name, task.getException());
                result.setText("✘ " + task.getException().getMessage());
                test.setDisable(false);
            });
            services.background().submit(task);
        });
        addRow("Executável " + name, field, new HBox(8, test, result));
    }

    private void addRow(String label, javafx.scene.Node field, javafx.scene.Node extra) {
        grid.add(new Label(label), 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
        if (field instanceof TextField tf) tf.setPrefColumnCount(32);
        if (extra != null) grid.add(extra, 2, row);
        row++;
    }
}
