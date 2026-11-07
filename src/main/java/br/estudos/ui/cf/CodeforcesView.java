package br.estudos.ui.cf;

import br.estudos.Services;
import br.estudos.app.cf.CfRatingPoint;
import br.estudos.app.cf.CfService;
import br.estudos.app.cf.CfStats;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/** Tela "Codeforces": sincronizar, gráfico de rating, estatísticas por tag, sugestão e dicas. */
public class CodeforcesView {
    private final Services services;
    private final VBox root = new VBox(10);
    private final Label status = new Label();
    private final Label suggestion = new Label();
    private final NumberAxis x = new NumberAxis();
    private final NumberAxis y = new NumberAxis();
    private final LineChart<Number, Number> chart = new LineChart<>(x, y);
    private final TableView<CfStats.TagStat> tags = new TableView<>();
    private final TableView<CfStats.TagStat> failed = new TableView<>();

    public CodeforcesView(Services services) {
        this.services = services;
        root.setPadding(new Insets(20));
        Label title = new Label("Codeforces");
        title.getStyleClass().add("screen-title");
        Button sync = new Button("Sincronizar");
        sync.setOnAction(e -> sync());
        x.setLabel("Contest nº");
        x.setForceZeroInRange(false);
        y.setForceZeroInRange(false);
        y.setLabel("Rating");
        chart.setLegendVisible(false);
        chart.setPrefHeight(240);
        chart.setCreateSymbols(true);
        setup(tags, true);
        setup(failed, false);
        suggestion.setWrapText(true);
        TabPane tabs = new TabPane(
                tab("Estatísticas", new VBox(8, chart, suggestion, new HBox(10, box("Taxa de AC por tag", tags),
                        box("Tags com mais WA/TLE", failed)))),
                tab("Dicas", new ScrollPane(new HintPanel(services, "").root())));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        root.getChildren().addAll(title, new HBox(8, sync, status), tabs);
        refresh();
    }

    public Node root() { return root; }

    private static Tab tab(String n, Node c) {
        Tab t = new Tab(n, c);
        t.setClosable(false);
        return t;
    }

    private static VBox box(String t, Node n) {
        VBox.setVgrow(n, Priority.ALWAYS);
        VBox b = new VBox(4, new Label(t), n);
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }

    private static void setup(TableView<CfStats.TagStat> t, boolean rate) {
        TableColumn<CfStats.TagStat, String> a = new TableColumn<>("Tag");
        a.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().tag()));
        TableColumn<CfStats.TagStat, String> b = new TableColumn<>(rate ? "AC / total" : "Falhas");
        b.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(rate
                ? c.getValue().accepted() + " / " + c.getValue().total() + " (" + Math.round(c.getValue().acRate() * 100) + "%)"
                : String.valueOf(c.getValue().failed())));
        t.getColumns().addAll(List.of(a, b));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPrefHeight(220);
    }

    private void sync() {
        status.setText("Sincronizando (1 requisição a cada 2 s)…");
        services.background().submit(() -> {
            String msg;
            try {
                var r = services.cf().sync();
                msg = "Sincronizado: " + r.fetched() + " submissões (" + r.added() + " novas), " + r.ratings() + " contests.";
            } catch (Throwable e) {
                msg = "Erro: " + e.getMessage();
            }
            String m = msg;
            Platform.runLater(() -> {
                status.setText(m);
                refresh();
            });
        });
    }

    private void refresh() {
        services.background().submit(() -> {
            try {
                CfService.Overview o = services.cf().overview();
                Platform.runLater(() -> show(o));
            } catch (Throwable e) {
                Platform.runLater(() -> status.setText("Erro: " + e.getMessage()));
            }
        });
    }

    private void show(CfService.Overview o) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        int i = 1;
        for (CfRatingPoint p : o.ratings()) s.getData().add(new XYChart.Data<>(i++, p.newRating()));
        chart.getData().setAll(List.of(s));
        tags.getItems().setAll(o.tags());
        failed.getItems().setAll(o.mostFailed());
        suggestion.setText(o.suggestion().isEmpty() ? "Sem dados suficientes para sugerir tags."
                : "Treine estas 3 tags: " + String.join(", ", o.suggestion()));
    }
}
