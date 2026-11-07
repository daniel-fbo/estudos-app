package br.estudos.ui.study;

import br.estudos.Services;
import br.estudos.app.study.GoalProgress;
import br.estudos.app.study.StudyService;
import br.estudos.domain.Area;
import br.estudos.domain.StudySession;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Painel: cronômetro de estudo + horas da semana, metas, sequência e últimas sessões. */
public class DashboardView {
    private final Services services;
    private final StudyService study;
    private final VBox root = new VBox(14);
    private final VBox dashBox = new VBox(14);
    private final Label clock = new Label("00:00:00");
    private final ComboBox<Area> area = new ComboBox<>();
    private final TextField subject = new TextField();
    private final TextField note = new TextField();
    private final Button start = new Button("Iniciar");
    private final Button pause = new Button("Pausar");
    private final Button stop = new Button("Encerrar");

    public DashboardView(Services services) {
        this.services = services;
        this.study = services.study();
        root.setPadding(new Insets(20));
        Label title = new Label("Painel");
        title.getStyleClass().add("screen-title");

        area.getItems().addAll(Area.values());
        area.getSelectionModel().select(Area.CP);
        subject.setPromptText("Assunto");
        note.setPromptText("Nota (opcional)");
        clock.setStyle("-fx-font-size: 28px; -fx-font-family: monospace;");
        start.setOnAction(e -> {
            try { study.start(area.getValue(), subject.getText()); } catch (RuntimeException ex) { showError(ex); }
            sync();
        });
        pause.setOnAction(e -> { if (study.paused()) study.resume(); else study.pause(); sync(); });
        stop.setOnAction(e -> { runBg(() -> study.stop(note.getText()), () -> { note.clear(); sync(); refresh(); }); });
        HBox timer = new HBox(10, clock, area, subject, note, start, pause, stop);
        timer.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Timeline[] ref = new Timeline[1];
        Timeline tick = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (root.getScene() == null) { ref[0].stop(); return; } // tela trocada: para o relógio
            updateClock();
        }));
        ref[0] = tick;
        tick.setCycleCount(Animation.INDEFINITE);
        tick.play();

        root.getChildren().addAll(title, timer, dashBox);
        sync();
        refresh();
        Platform.runLater(this::offerOpenSession);
    }

    /** Sessão que ficou aberta (app fechado no meio): retomar, encerrar agora ou descartar. */
    private void offerOpenSession() {
        if (root.getScene() == null) return;
        study.openSession().ifPresent(open -> {
            ButtonType resume = new ButtonType("Retomar");
            ButtonType end = new ButtonType("Encerrar agora");
            ButtonType drop = new ButtonType("Descartar");
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Há uma sessão de " + open.area()
                    + (open.subject() == null ? "" : " (" + open.subject() + ")") + " iniciada em "
                    + java.time.LocalDateTime.ofInstant(open.startedAt(), java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")) + " que não foi encerrada.",
                    resume, end, drop);
            a.setHeaderText("Sessão em andamento");
            var r = a.showAndWait();
            if (r.isEmpty()) return;
            if (r.get() == resume) { study.resumeOpen(open); sync(); }
            else if (r.get() == end) runBg(() -> study.endOpen(open, null), this::refresh);
            else runBg(() -> study.discardOpen(open), this::refresh);
        });
    }

    public Node root() { return new ScrollPane(root) {{ setFitToWidth(true); }}; }

    private void sync() {
        boolean run = study.running();
        start.setDisable(run);
        pause.setDisable(!run);
        stop.setDisable(!run);
        area.setDisable(run);
        subject.setDisable(run);
        pause.setText(study.paused() ? "Retomar" : "Pausar");
        updateClock();
    }

    private void updateClock() {
        long s = study.elapsedSeconds();
        clock.setText(String.format("%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60));
    }

    private void runBg(Runnable work, Runnable then) {
        services.background().submit(() -> {
            try { work.run(); }
            catch (Throwable t) { Platform.runLater(() -> showError(t)); }
            finally { Platform.runLater(then); }
        });
    }

    private void showError(Throwable t) {
        new Alert(Alert.AlertType.ERROR, "Erro: " + t.getMessage(), ButtonType.OK).show();
    }

    private void refresh() {
        services.background().submit(() -> {
            StudyService.Dashboard d = study.dashboard();
            Platform.runLater(() -> render(d));
        });
    }

    private void render(StudyService.Dashboard d) {
        dashBox.getChildren().clear();

        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("horas");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle("Horas por área nesta semana");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(260);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Area a : Area.values()) {
            series.getData().add(new XYChart.Data<>(a.name(), d.weekSecondsByArea().getOrDefault(a, 0L) / 3600.0));
        }
        chart.getData().add(series);

        Label streak = new Label("Sequência: " + d.streak() + (d.streak() == 1 ? " dia" : " dias") + " seguidos");
        streak.setStyle("-fx-font-size: 16px;");

        VBox goals = new VBox(8, new Label("Metas ativas"));
        if (d.goals().isEmpty()) goals.getChildren().add(new Label("Nenhuma meta ativa. Crie na tela Metas."));
        for (GoalProgress g : d.goals()) goals.getChildren().add(GoalsView.progressRow(g));

        VBox last = new VBox(4, new Label("Últimas sessões"));
        if (d.lastSessions().isEmpty()) last.getChildren().add(new Label("Nenhuma sessão ainda."));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());
        for (StudySession s : d.lastSessions()) {
            String sub = s.subject() == null ? "" : " · " + s.subject();
            last.getChildren().add(new Label(f.format(s.startedAt()) + " — " + s.area() + sub + " — "
                    + s.durationSeconds() / 60 + " min"));
        }
        dashBox.getChildren().addAll(streak, chart, goals, last);
    }
}
