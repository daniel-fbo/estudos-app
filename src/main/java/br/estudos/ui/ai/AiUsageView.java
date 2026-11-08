package br.estudos.ui.ai;

import br.estudos.Services;
import br.estudos.app.ai.AiOutcome;
import br.estudos.app.ai.AiService;
import br.estudos.app.ai.AiUsage;
import br.estudos.app.ai.BudgetStatus;
import br.estudos.domain.AiRequest;
import br.estudos.domain.AiRunnerKind;
import br.estudos.domain.AiTask;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/** Tela "Uso de IA": orçamento de hoje, taxa de cache, agregados por dia/tarefa/modelo e um teste rápido. */
public class AiUsageView {
    private static final int DAYS = 30;

    private final Services services;
    private final AiService ai;
    private final VBox root = new VBox(12);
    private final Label today = new Label();
    private final Label cache = new Label();
    private final TableView<Map.Entry<String, AiUsage.Row>> byDay = table("Dia");
    private final TableView<Map.Entry<String, AiUsage.Row>> byTask = table("Tarefa");
    private final TableView<Map.Entry<String, AiUsage.Row>> byModel = table("Modelo");
    private final TextArea answer = new TextArea();

    public AiUsageView(Services services) {
        this.services = services;
        this.ai = services.ai();
        root.setPadding(new Insets(20));
        Label title = new Label("Uso de IA");
        title.getStyleClass().add("screen-title");
        Button reload = new Button("Atualizar");
        reload.setOnAction(e -> load());
        TabPane tabs = new TabPane(tab("Por dia (" + DAYS + " dias)", byDay), tab("Por tarefa", byTask),
                tab("Por modelo", byModel));
        tabs.setPrefHeight(260);
        root.getChildren().addAll(title, new HBox(12, today, reload), cache, tabs, quickTest());
        load();
    }

    public Node root() {
        return root;
    }

    private void load() {
        services.background().submit(() -> {
            try {
                BudgetStatus b = ai.budget();
                AiUsage u = ai.usage(DAYS);
                Platform.runLater(() -> {
                    today.setText("Hoje: " + b.callsToday() + "/" + b.dailyLimit() + " chamadas · "
                            + b.tokensToday() + (b.tokenLimit() > 0 ? "/" + b.tokenLimit() : "") + " tokens"
                            + (b.tokenLimit() > 0 ? "" : " (sem limite de tokens)"));
                    cache.setText(String.format(Locale.ROOT, "Cache: %d de %d pedidos (%.0f%%)",
                            u.cacheHits(), u.cacheHits() + u.realCalls(), 100 * u.cacheHitRate()));
                    byDay.getItems().setAll(u.byDay().entrySet().stream().sorted(Map.Entry.<String, AiUsage.Row>comparingByKey().reversed()).toList());
                    byTask.getItems().setAll(u.byTask().entrySet());
                    byModel.getItems().setAll(u.byModel().entrySet());
                });
            } catch (Throwable t) {
                Platform.runLater(() -> today.setText("Erro ao carregar: " + t.getMessage()));
            }
        });
    }

    private Node quickTest() {
        ComboBox<AiRunnerKind> runner = new ComboBox<>();
        for (AiRunnerKind k : AiRunnerKind.values()) if (ai.supported(k)) runner.getItems().add(k);
        runner.getSelectionModel().selectFirst();
        TextField model = new TextField(services.config().model(br.estudos.app.config.Config.MODEL_CHEAP));
        model.setPromptText("modelo (vazio = padrão da CLI)");
        model.setPrefColumnCount(10);
        TextField prompt = new TextField("Responda só OK");
        prompt.setPrefColumnCount(30);
        Button send = new Button("Enviar");
        Button again = new Button("Gerar de novo");
        boolean none = runner.getItems().isEmpty();
        send.setDisable(none);
        again.setDisable(none);
        send.setOnAction(e -> call(runner.getValue(), model.getText(), prompt.getText(), false));
        again.setOnAction(e -> call(runner.getValue(), model.getText(), prompt.getText(), true));
        answer.setEditable(false);
        answer.setWrapText(true);
        answer.setPrefRowCount(4);
        return new VBox(6, new Label("Teste rápido"), new HBox(8, runner, model, prompt, send, again), answer);
    }

    private void call(AiRunnerKind runner, String model, String prompt, boolean refresh) {
        if (runner == null || prompt.isBlank()) return;
        answer.setText("Enviado (acompanhe em Tarefas)…");
        ai.submit(AiRequest.of(runner, model, AiTask.OUTRO, prompt), refresh, new FxBudgetGate())
                .result().whenComplete((AiOutcome o, Throwable t) -> Platform.runLater(() -> {
                    if (t == null) answer.setText((o.fromCache() ? "[cache] " : "") + o.response());
                    else answer.setText(message(t));
                    load();
                }));
    }

    private static String message(Throwable t) {
        if (t instanceof CompletionException && t.getCause() != null) t = t.getCause();
        if (t instanceof CancellationException) return "Cancelado.";
        return "Erro: " + t.getMessage();
    }

    private static Tab tab(String name, Node content) {
        Tab t = new Tab(name, content);
        t.setClosable(false);
        return t;
    }

    private static TableView<Map.Entry<String, AiUsage.Row>> table(String keyName) {
        TableView<Map.Entry<String, AiUsage.Row>> t = new TableView<>();
        t.getColumns().add(col(keyName, Map.Entry::getKey));
        t.getColumns().add(col("Chamadas", e -> String.valueOf(e.getValue().calls())));
        t.getColumns().add(col("Cache", e -> String.valueOf(e.getValue().cacheHits())));
        t.getColumns().add(col("Tokens entrada", e -> String.valueOf(e.getValue().inputTokens())));
        t.getColumns().add(col("Tokens saída", e -> String.valueOf(e.getValue().outputTokens())));
        t.getColumns().add(col("Custo (US$)", e -> e.getValue().costUsd() == null ? "—"
                : String.format(Locale.ROOT, "%.4f", e.getValue().costUsd())));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        t.setPlaceholder(new Label("Sem chamadas"));
        return t;
    }

    private static TableColumn<Map.Entry<String, AiUsage.Row>, String> col(
            String name, java.util.function.Function<Map.Entry<String, AiUsage.Row>, String> f) {
        TableColumn<Map.Entry<String, AiUsage.Row>, String> c = new TableColumn<>(name);
        c.setCellValueFactory(cd -> new SimpleStringProperty(f.apply(cd.getValue())));
        return c;
    }
}
