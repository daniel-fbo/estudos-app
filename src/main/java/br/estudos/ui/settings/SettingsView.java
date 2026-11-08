package br.estudos.ui.settings;

import br.estudos.App;
import br.estudos.Services;
import br.estudos.app.backup.BackupService;
import br.estudos.app.config.Config;
import br.estudos.app.tools.ExecutableChecker.CheckResult;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
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
    private final TextField tokenBudget = new TextField();
    private final Spinner<Integer> aiTimeout = new Spinner<>(1, 600, Config.DEFAULT_AI_TIMEOUT_MIN);
    private final TextField ankiUrl = new TextField();
    private final ComboBox<String> themeCombo = new ComboBox<>();
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
        addRow("Tokens de IA por dia", tokenBudget, new Label("(0 = sem limite)"));
        aiTimeout.setEditable(true);
        addRow("Timeout de IA (min)", aiTimeout, new Label("(aulas: no mínimo 60)"));
        addRow("URL do AnkiConnect", ankiUrl, null);

        // Tema
        themeCombo.getItems().addAll(Config.THEME_DARK, Config.THEME_LIGHT);
        themeCombo.setValue(services.config().theme());
        addRow("Tema", themeCombo, new Label("(aplica ao salvar)"));

        Button save = new Button("Salvar");
        save.setDefaultButton(true);
        save.setOnAction(e -> save());

        // Backup
        Label backupTitle = new Label("Backup");
        backupTitle.getStyleClass().add("section-title");

        Button exportBtn = new Button("Exportar backup…");
        Button importBtn = new Button("Importar backup…");
        Label backupStatus = new Label();

        exportBtn.setOnAction(e -> exportBackup(backupStatus));
        importBtn.setOnAction(e -> importBackup(backupStatus));

        Label title = new Label("Configurações");
        title.getStyleClass().add("screen-title");

        Label shortcutsLabel = new Label("Atalhos: Ctrl+1…0 troca de tela");
        shortcutsLabel.getStyleClass().add("shortcut-hint");

        root = new VBox(16, title, shortcutsLabel, grid, new HBox(10, save, status),
                new Separator(),
                backupTitle, new HBox(10, exportBtn, importBtn, backupStatus));
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
        tokenBudget.setText(Long.toString(c.dailyTokenBudget()));
        aiTimeout.getValueFactory().setValue(c.aiTimeoutMinutes());
        ankiUrl.setText(c.ankiUrl());
        themeCombo.setValue(c.theme());
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
                cfHandle.getText().strip(), models, budget.getValue(),
                themeCombo.getValue(), parseTokens(), aiTimeout.getValue(), ankiUrl.getText().strip());
    }

    private long parseTokens() {
        try {
            return Math.max(0, Long.parseLong(tokenBudget.getText().strip().replace(".", "").replace("_", "")));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tokens por dia deve ser um número inteiro (0 = sem limite).");
        }
    }

    private void save() {
        try {
            budget.increment(0); // confirma o texto digitado no spinner
            aiTimeout.increment(0);
            Config newConfig = read();
            services.saveConfig(newConfig);
            status.setText("Salvo.");
            // Aplica tema ao vivo
            if (root.getScene() != null) {
                App.applyTheme(root.getScene(), newConfig);
            }
        } catch (Exception ex) {
            log.error("Erro ao salvar config", ex);
            status.setText("Erro ao salvar: " + ex.getMessage());
        }
    }

    private void exportBackup(Label backupStatus) {
        BackupService backup = createBackupService();
        FileChooser fc = new FileChooser();
        fc.setTitle("Exportar backup");
        fc.setInitialFileName(backup.suggestedName());
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivo ZIP", "*.zip"));
        File dest = fc.showSaveDialog(root.getScene().getWindow());
        if (dest == null) return;

        backupStatus.setText("Exportando…");
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                backup.export(dest.toPath());
                return null;
            }
        };
        task.setOnSucceeded(e -> backupStatus.setText("Backup exportado com sucesso."));
        task.setOnFailed(e -> {
            log.error("Erro ao exportar backup", task.getException());
            backupStatus.setText("Erro: " + task.getException().getMessage());
        });
        services.background().submit(task);
    }

    private void importBackup(Label backupStatus) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Importar backup");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivo ZIP", "*.zip"));
        File src = fc.showOpenDialog(root.getScene().getWindow());
        if (src == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "O banco e o config atuais serão substituídos pelos do backup. "
                        + "Exporte um backup antes se quiser guardar os dados de agora. Continuar?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Importar backup");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        backupStatus.setText("Importando…");
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                createBackupService().importBackup(src.toPath());
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            backupStatus.setText("Backup importado. Fechando o app…");
            Alert done = new Alert(Alert.AlertType.INFORMATION,
                    "Backup importado. O app será fechado; abra-o de novo para usar os dados restaurados.",
                    ButtonType.OK);
            done.showAndWait();
            javafx.application.Platform.exit();
        });
        task.setOnFailed(e -> {
            log.error("Erro ao importar backup", task.getException());
            backupStatus.setText("Erro: " + task.getException().getMessage());
        });
        services.background().submit(task);
    }

    private BackupService createBackupService() {
        Path dbFile = services.database().file();
        Path configFile = services.home().resolve("config.json");
        return new BackupService(dbFile, configFile);
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
