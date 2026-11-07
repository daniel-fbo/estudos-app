package br.estudos.ui.calendar;

import br.estudos.Services;
import br.estudos.app.calendar.FreeSlot;
import br.estudos.app.calendar.PlannedBlock;
import br.estudos.domain.CalendarBlock;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tela "Agenda": grade de horários livres, proposta de blocos, aprovação e marcação de feitos. */
public class AgendaView {
    private static final int FIRST_HOUR = 6, LAST_HOUR = 23;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("EEE dd/MM HH:mm", Locale.forLanguageTag("pt-BR"));

    private final Services services;
    private final VBox root = new VBox(10);
    private final Label status = new Label();
    private final ToggleButton[][] cells = new ToggleButton[7][LAST_HOUR - FIRST_HOUR + 1];
    private final ListView<PlannedBlock> proposed = new ListView<>();
    private final ListView<CalendarBlock> scheduled = new ListView<>();
    private LocalDate weekStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    public AgendaView(Services services) {
        this.services = services;
        root.setPadding(new Insets(20));
        Label title = new Label("Agenda");
        title.getStyleClass().add("screen-title");

        Label help = new Label("""
                Para usar o Google Calendar: no Google Cloud Console crie um projeto, ative a "Google Calendar API", \
                configure a tela de consentimento (usuário de teste = você) e crie um "ID do cliente OAuth" do tipo \
                "App para computador". Baixe o JSON e salve em %s. Depois clique em "Autorizar" (abre o navegador). \
                Os eventos vão para um calendário chamado "Estudos"."""
                .formatted(services.home().resolve("google").resolve("credentials.json")));
        help.setWrapText(true);
        Button auth = new Button("Autorizar Google");
        auth.setOnAction(e -> run("Autorizando…", () -> { services.calendar().authorize(); return "Autorizado."; }));

        Button prev = new Button("◀"), next = new Button("▶");
        Label week = new Label();
        prev.setOnAction(e -> { weekStart = weekStart.minusWeeks(1); refresh(week); });
        next.setOnAction(e -> { weekStart = weekStart.plusWeeks(1); refresh(week); });

        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);
        for (int d = 0; d < 7; d++) {
            grid.add(new Label(DayOfWeek.of(d + 1).getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("pt-BR"))), d + 1, 0);
            for (int h = 0; h < cells[d].length; h++) {
                ToggleButton b = new ToggleButton();
                b.setPrefSize(46, 20);
                cells[d][h] = b;
                grid.add(b, d + 1, h + 1);
            }
        }
        for (int h = 0; h < LAST_HOUR - FIRST_HOUR + 1; h++) grid.add(new Label((FIRST_HOUR + h) + "h"), 0, h + 1);
        ScrollPane gridScroll = new ScrollPane(grid);
        gridScroll.setPrefViewportHeight(260);

        Button plan = new Button("Propor blocos");
        plan.setOnAction(e -> plan());
        Button approve = new Button("Aprovar e criar eventos");
        approve.setOnAction(e -> approve());
        Button done = new Button("Marcar como feito");
        done.setOnAction(e -> markDone());

        proposed.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(PlannedBlock b, boolean empty) {
                super.updateItem(b, empty);
                setText(empty || b == null ? null : b.title() + " — " + b.start().format(FMT) + " a " + b.end().toLocalTime());
            }
        });
        scheduled.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(CalendarBlock b, boolean empty) {
                super.updateItem(b, empty);
                setText(empty || b == null ? null : (b.done() ? "✔ " : "• ") + b.title() + " — "
                        + b.startsAt().atZone(ZoneId.systemDefault()).format(FMT));
            }
        });
        proposed.setPrefHeight(150);
        scheduled.setPrefHeight(150);

        VBox left = new VBox(6, new HBox(8, new Label("Horários livres (clique nas células)"), prev, week, next), gridScroll);
        VBox right = new VBox(6, new Label("Proposta"), proposed, new HBox(8, plan, approve),
                new Label("Blocos da semana"), scheduled, done);
        HBox.setHgrow(left, Priority.ALWAYS);
        root.getChildren().addAll(title, help, new HBox(8, auth, status), new HBox(16, left, right));
        refresh(week);
    }

    public Node root() { return new ScrollPane(root) {{ setFitToWidth(true); }}; }

    private List<FreeSlot> slots() {
        List<FreeSlot> out = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < cells[d].length; h++) {
                if (!cells[d][h].isSelected()) continue;
                LocalDateTime st = weekStart.plusDays(d).atTime(FIRST_HOUR + h, 0);
                if (!out.isEmpty() && out.get(out.size() - 1).end().equals(st)) {
                    FreeSlot last = out.remove(out.size() - 1);
                    out.add(new FreeSlot(last.start(), st.plusHours(1)));
                } else {
                    out.add(new FreeSlot(st, st.plusHours(1)));
                }
            }
        }
        return out;
    }

    private void plan() {
        List<FreeSlot> s = slots();
        run("Planejando…", () -> {
            List<PlannedBlock> p = services.calendar().plan(s);
            Platform.runLater(() -> proposed.getItems().setAll(p));
            return p.isEmpty() ? "Nada a propor (sem metas de horas pendentes ou sem horários livres)."
                    : p.size() + " blocos propostos.";
        });
    }

    private void approve() {
        List<PlannedBlock> p = new ArrayList<>(proposed.getItems());
        if (p.isEmpty()) { status.setText("Nenhum bloco proposto."); return; }
        run("Criando eventos…", () -> {
            var saved = services.calendar().approve(p);
            Platform.runLater(() -> { proposed.getItems().clear(); load(); });
            return saved.size() + " eventos criados no calendário Estudos.";
        });
    }

    private void markDone() {
        CalendarBlock b = scheduled.getSelectionModel().getSelectedItem();
        if (b == null) { status.setText("Selecione um bloco."); return; }
        run("Registrando…", () -> {
            services.calendar().markDone(b.id());
            Platform.runLater(this::load);
            return "Sessão registrada.";
        });
    }

    private void refresh(Label week) {
        week.setText(weekStart.format(DateTimeFormatter.ofPattern("dd/MM")) + " – " + weekStart.plusDays(6).format(DateTimeFormatter.ofPattern("dd/MM")));
        load();
    }

    private void load() {
        LocalDate ws = weekStart;
        services.background().submit(() -> {
            try {
                var l = services.calendar().week(ws);
                Platform.runLater(() -> scheduled.getItems().setAll(l));
            } catch (Throwable e) {
                Platform.runLater(() -> status.setText("Erro: " + e.getMessage()));
            }
        });
    }

    private interface Job { String call() throws Exception; }

    private void run(String start, Job job) {
        status.setText(start);
        services.background().submit(() -> {
            String msg;
            try { msg = job.call(); } catch (Throwable e) { msg = "Erro: " + e.getMessage(); }
            String m = msg;
            Platform.runLater(() -> status.setText(m));
        });
    }
}
