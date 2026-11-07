package br.estudos.ui.study;

import br.estudos.Services;
import br.estudos.app.study.GoalProgress;
import br.estudos.app.study.StudyService;
import br.estudos.domain.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** CRUD de metas, com contador manual (+/−) para as de CONTAGEM. */
public class GoalsView {
    private final Services services;
    private final StudyService study;
    private final VBox root = new VBox(12);
    private final VBox list = new VBox(8);
    private final Label error = new Label();

    public GoalsView(Services services) {
        this.services = services;
        this.study = services.study();
        root.setPadding(new Insets(20));
        Label title = new Label("Metas");
        title.getStyleClass().add("screen-title");

        TextField name = new TextField();
        name.setPromptText("Título (ex.: 5 problemas)");
        ComboBox<Area> area = new ComboBox<>();
        area.getItems().addAll(Area.values());
        area.getSelectionModel().select(Area.CP);
        ComboBox<GoalKind> kind = new ComboBox<>();
        kind.getItems().addAll(GoalKind.values());
        kind.getSelectionModel().selectFirst();
        ComboBox<GoalPeriod> period = new ComboBox<>();
        period.getItems().addAll(GoalPeriod.values());
        period.getSelectionModel().selectFirst();
        TextField target = new TextField();
        target.setPromptText("Alvo (horas ou quantidade)");
        ComboBox<GoalSource> source = new ComboBox<>();
        source.setTooltip(new Tooltip("Fonte automática somada ao contador manual (só metas de CONTAGEM)"));
        Runnable syncSource = () -> fillSources(source, area.getValue(), kind.getValue());
        area.setOnAction(e -> syncSource.run());
        kind.setOnAction(e -> syncSource.run());
        syncSource.run();
        Button add = new Button("Criar meta");
        add.setOnAction(e -> {
            try {
                double t = Double.parseDouble(target.getText().replace(',', '.').trim());
                String n = name.getText();
                Area a = area.getValue();
                GoalKind k = kind.getValue();
                GoalPeriod p = period.getValue();
                GoalSource src = source.getValue();
                mutate(() -> study.createGoal(n, a, k, p, t, src), () -> { name.clear(); target.clear(); });
            } catch (NumberFormatException ex) {
                error.setText("Alvo inválido.");
            }
        });
        error.setStyle("-fx-text-fill: #c0392b;");
        HBox form = new HBox(8, name, area, kind, period, target, source, add);
        HBox.setHgrow(name, Priority.ALWAYS);
        root.getChildren().addAll(title, form, error, list);
        refresh();
    }

    public Node root() { return new ScrollPane(root) {{ setFitToWidth(true); }}; }

    private void refresh() {
        services.background().submit(() -> {
            try {
                List<GoalProgress> all = study.allGoalsProgress(); // inclui inativas
                Platform.runLater(() -> render(all));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> error.setText("Erro: " + ex.getMessage()));
            }
        });
    }

    /** Executa a mutação fora da thread do JavaFX; depois atualiza a tela (ou mostra o erro). */
    private void mutate(Runnable work, Runnable onOk) {
        services.background().submit(() -> {
            try {
                work.run();
                Platform.runLater(() -> { error.setText(""); onOk.run(); });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> error.setText(ex.getMessage()));
            }
            refresh();
        });
    }

    private void render(List<GoalProgress> all) {
        list.getChildren().clear();
        if (all.isEmpty()) list.getChildren().add(new Label("Nenhuma meta cadastrada."));
        for (GoalProgress p : all) {
            Goal g = p.goal();
            VBox row = new VBox(4);
            HBox actions = new HBox(6);
            if (g.kind() == GoalKind.CONTAGEM) {
                Button minus = new Button("−");
                Button plus = new Button("+");
                minus.setOnAction(e -> mutate(() -> study.adjustCount(g, -1), () -> {}));
                plus.setOnAction(e -> mutate(() -> study.adjustCount(g, 1), () -> {}));
                actions.getChildren().addAll(minus, plus);
            }
            CheckBox on = new CheckBox("Ativa");
            on.setSelected(g.active());
            on.setOnAction(e -> {
                boolean sel = on.isSelected();
                mutate(() -> study.updateGoal(g.withActive(sel)), () -> {});
            });
            Button del = new Button("Excluir");
            del.setOnAction(e -> mutate(() -> study.deleteGoal(g.id()), () -> {}));
            Button edit = new Button("Editar");
            edit.setOnAction(e -> edit(g));
            actions.getChildren().addAll(edit, on, del);
            row.getChildren().addAll(progressRow(p), actions);
            list.getChildren().add(row);
        }
    }

    private static void fillSources(ComboBox<GoalSource> box, Area area, GoalKind kind) {
        GoalSource cur = box.getValue();
        box.getItems().clear();
        for (GoalSource s : GoalSource.values()) if (s.allowedFor(area, kind)) box.getItems().add(s);
        box.setValue(cur != null && box.getItems().contains(cur) ? cur : GoalSource.NENHUMA);
        box.setDisable(box.getItems().size() < 2);
    }

    private void edit(Goal g) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Editar meta");
        TextField name = new TextField(g.title());
        TextField target = new TextField(String.valueOf(g.target()));
        ComboBox<GoalSource> source = new ComboBox<>();
        fillSources(source, g.area(), g.kind());
        source.setValue(g.effectiveSource());
        VBox box = new VBox(8, new Label("Título"), name, new Label("Alvo"), target, new Label("Fonte automática"), source);
        d.getDialogPane().setContent(box);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            try {
                double t = Double.parseDouble(target.getText().replace(',', '.').trim());
                String n = name.getText();
                GoalSource src = source.getValue();
                mutate(() -> study.editGoal(g, n, t, src), () -> {});
            } catch (NumberFormatException ex) {
                error.setText("Alvo inválido.");
            }
        });
    }

    /** Linha com título, barra e valor; reutilizada pelo Painel. */
    static Node progressRow(GoalProgress p) {
        Goal g = p.goal();
        boolean hours = g.kind() == GoalKind.HORAS;
        String cur = hours ? String.format("%.1f h", p.current()) : String.valueOf((int) p.current());
        String tgt = hours ? String.format("%.1f h", p.target()) : String.valueOf((int) p.target());
        Label l = new Label(g.title() + " [" + g.area() + ", " + g.period().name().toLowerCase() + "] — "
                + cur + " / " + tgt);
        ProgressBar bar = new ProgressBar(p.fraction());
        bar.setMaxWidth(Double.MAX_VALUE);
        return new VBox(2, l, bar);
    }
}
