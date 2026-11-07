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
        Button add = new Button("Criar meta");
        add.setOnAction(e -> {
            try {
                double t = Double.parseDouble(target.getText().replace(',', '.').trim());
                study.createGoal(name.getText(), area.getValue(), kind.getValue(), period.getValue(), t);
                name.clear(); target.clear(); error.setText("");
                refresh();
            } catch (NumberFormatException ex) {
                error.setText("Alvo inválido.");
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
            }
        });
        error.setStyle("-fx-text-fill: #c0392b;");
        HBox form = new HBox(8, name, area, kind, period, target, add);
        HBox.setHgrow(name, Priority.ALWAYS);
        root.getChildren().addAll(title, form, error, list);
        refresh();
    }

    public Node root() { return new ScrollPane(root) {{ setFitToWidth(true); }}; }

    private void refresh() {
        services.background().submit(() -> {
            List<Goal> goals = study.goals();
            // reaproveita o cálculo do painel para obter o progresso de todas (inclui inativas)
            var dash = study.dashboard();
            Platform.runLater(() -> render(goals, dash.goals()));
        });
    }

    private void render(List<Goal> goals, List<GoalProgress> active) {
        list.getChildren().clear();
        if (goals.isEmpty()) list.getChildren().add(new Label("Nenhuma meta cadastrada."));
        for (Goal g : goals) {
            GoalProgress p = active.stream().filter(x -> x.goal().id() == g.id()).findFirst()
                    .orElse(new GoalProgress(g, 0, g.target()));
            VBox row = new VBox(4);
            HBox actions = new HBox(6);
            if (g.kind() == GoalKind.CONTAGEM) {
                Button minus = new Button("−");
                Button plus = new Button("+");
                minus.setOnAction(e -> { study.adjustCount(g, -1); refresh(); });
                plus.setOnAction(e -> { study.adjustCount(g, 1); refresh(); });
                actions.getChildren().addAll(minus, plus);
            }
            CheckBox on = new CheckBox("Ativa");
            on.setSelected(g.active());
            on.setOnAction(e -> {
                study.updateGoal(new Goal(g.id(), g.title(), g.area(), g.kind(), g.period(), g.target(),
                        g.manualCount(), g.autoCountAc(), on.isSelected(), g.createdAt()));
                refresh();
            });
            Button del = new Button("Excluir");
            del.setOnAction(e -> { study.deleteGoal(g.id()); refresh(); });
            actions.getChildren().addAll(on, del);
            row.getChildren().addAll(progressRow(p), actions);
            list.getChildren().add(row);
        }
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
