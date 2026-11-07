package br.estudos.ui.tasks;

import br.estudos.Services;
import br.estudos.app.tasks.TaskInfo;
import br.estudos.app.tasks.TaskQueue;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Painel "Tarefas": o que está na fila/rodando e o histórico recente, com cancelar. */
public class TasksView {
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final TaskQueue queue;
    private final VBox root = new VBox(12);
    private final VBox list = new VBox(6);
    private final Runnable listener = () -> Platform.runLater(this::refresh);

    public TasksView(Services services) {
        this.queue = services.tasks();
        root.setPadding(new Insets(20));
        Label title = new Label("Tarefas");
        title.getStyleClass().add("screen-title");
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(title, scroll);
        root.sceneProperty().addListener((o, old, scene) -> {
            if (scene == null) queue.removeListener(listener);
            else queue.addListener(listener);
        });
        refresh();
    }

    public Node root() {
        return root;
    }

    private void refresh() {
        list.getChildren().clear();
        var tasks = queue.snapshot();
        if (tasks.isEmpty()) list.getChildren().add(new Label("Nenhuma tarefa nesta sessão."));
        for (TaskInfo t : tasks) list.getChildren().add(row(t));
    }

    private Node row(TaskInfo t) {
        String when = HOUR.format(t.submittedAt())
                + (t.finishedAt() == null ? "" : " → " + HOUR.format(t.finishedAt()));
        Label name = new Label((t.ai() ? "[IA] " : "") + t.title());
        Label state = new Label(t.state().label() + "  " + when);
        VBox text = new VBox(2, name, state);
        if (t.message() != null) {
            Label msg = new Label(t.message());
            msg.setWrapText(true);
            msg.setStyle("-fx-text-fill: #c0392b;");
            text.getChildren().add(msg);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(10, text, spacer);
        box.setAlignment(Pos.CENTER_LEFT);
        if (!t.state().finished()) {
            Button cancel = new Button("Cancelar");
            cancel.setOnAction(e -> queue.cancel(t.id()));
            box.getChildren().add(cancel);
        }
        return box;
    }
}
