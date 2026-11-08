package br.estudos.ui.anki;

import br.estudos.Services;
import br.estudos.app.anki.CardDraft;
import br.estudos.app.anki.CardService;
import br.estudos.domain.*;
import br.estudos.ui.ai.FxBudgetGate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Tela "Anki": lista de cards, novo card, importar, gerar de aula (com aprovação) e sincronizar. */
public class AnkiView {
    private final Services services;
    private final CardService cards;
    private final VBox root = new VBox(10);
    private final VBox list = new VBox(6);
    private final Label status = new Label();
    private final ComboBox<Object> areaFilter = new ComboBox<>();
    private final ComboBox<String> syncFilter = new ComboBox<>();

    public AnkiView(Services services) {
        this.services = services;
        this.cards = services.cards();
        root.setPadding(new Insets(20));
        Label title = new Label("Anki");
        title.getStyleClass().add("screen-title");

        Button add = new Button("Novo card");
        add.setOnAction(e -> edit(null));
        Button imp = new Button("Importar das notas");
        imp.setOnAction(e -> bg(() -> {
            var r = cards.importFromRepo();
            return "Importação: " + r.found() + " encontrados, " + r.added() + " novos, " + r.duplicates() + " repetidos.";
        }));
        Button gen = new Button("Gerar de uma aula…");
        gen.setOnAction(e -> pickLesson());
        Button sync = new Button("Sincronizar com o Anki");
        sync.setOnAction(e -> bg(() -> {
            var r = cards.sync();
            return "Sincronizado: " + r.ok() + " enviados" + (r.rejected() > 0 ? ", " + r.rejected() + " recusados (provável duplicata no Anki)" : "")
                    + (r.sent() == 0 ? " (nada pendente)" : "") + ".";
        }));

        areaFilter.getItems().add("Todas");
        areaFilter.getItems().addAll((Object[]) Area.values());
        areaFilter.getSelectionModel().selectFirst();
        syncFilter.getItems().addAll("Todos", "Pendentes", "Sincronizados");
        syncFilter.getSelectionModel().selectFirst();
        areaFilter.valueProperty().addListener((o, a, b) -> refresh());
        syncFilter.valueProperty().addListener((o, a, b) -> refresh());

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.getChildren().addAll(title, new HBox(8, add, imp, gen, sync), status,
                new HBox(8, areaFilter, syncFilter), scroll);
        refresh();
    }

    public Node root() { return root; }

    private void bg(Callable<String> job) {
        status.setText("Trabalhando…");
        services.background().submit(() -> {
            String msg;
            try {
                msg = job.call();
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
        Object a = areaFilter.getValue();
        Area area = a instanceof Area ar ? ar : null;
        String s = syncFilter.getValue();
        Boolean synced = "Pendentes".equals(s) ? Boolean.FALSE : "Sincronizados".equals(s) ? Boolean.TRUE : null;
        services.background().submit(() -> {
            try {
                List<Card> rows = cards.list(area, synced);
                Platform.runLater(() -> show(rows));
            } catch (Throwable e) {
                Platform.runLater(() -> status.setText("Erro: " + e.getMessage()));
            }
        });
    }

    private void show(List<Card> rows) {
        list.getChildren().clear();
        if (rows.isEmpty()) list.getChildren().add(new Label("Nenhum card."));
        for (Card c : rows) {
            Label l = new Label("[" + c.area() + " · " + c.origin() + " · " + (c.synced() ? "sincronizado" : "pendente")
                    + "]  " + c.front().replace('\n', ' '));
            l.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(l, Priority.ALWAYS);
            Button ed = new Button("Editar");
            ed.setOnAction(e -> edit(c));
            Button del = new Button("Excluir");
            del.setOnAction(e -> bg(() -> {
                cards.delete(c.id());
                return "Card excluído.";
            }));
            list.getChildren().add(new HBox(8, l, ed, del));
        }
    }

    private void edit(Card c) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(c == null ? "Novo card" : "Editar card");
        TextArea front = new TextArea(c == null ? "" : c.front());
        TextArea back = new TextArea(c == null ? "" : c.back());
        front.setPrefRowCount(3);
        back.setPrefRowCount(4);
        ComboBox<Area> area = new ComboBox<>();
        area.getItems().addAll(Area.values());
        area.setValue(c == null ? Area.CP : c.area());
        d.getDialogPane().setContent(new VBox(6, new Label("Frente"), front, new Label("Verso"), back, area));
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        String f = front.getText(), b = back.getText();
        Area ar = area.getValue();
        bg(() -> {
            if (c == null) {
                return cards.add(f, b, ar, CardOrigin.MANUAL, null).isPresent() ? "Card criado." : "Já existe card com essa frente.";
            }
            return cards.edit(c, f, b, ar)
                    ? "Card salvo, mas o Anki não respondeu: a nota será atualizada na próxima sincronização."
                    : "Card atualizado.";
        });
    }

    private void pickLesson() {
        services.background().submit(() -> {
            List<Lesson> ready = services.lessons().list(null, null).stream()
                    .filter(l -> l.status() == LessonStatus.PRONTA && l.mdPath() != null).toList();
            Platform.runLater(() -> {
                if (ready.isEmpty()) {
                    status.setText("Nenhuma aula pronta com arquivo .md.");
                    return;
                }
                ChoiceDialog<Lesson> cd = new ChoiceDialog<>(ready.get(0), ready);
                cd.setHeaderText("Aula");
                TextInputDialog nd = new TextInputDialog("10");
                nd.setHeaderText("Máximo de cards");
                var lesson = cd.showAndWait();
                if (lesson.isEmpty()) return;
                var n = nd.showAndWait();
                if (n.isEmpty()) return;
                int max;
                try {
                    max = Integer.parseInt(n.get().trim());
                    if (max <= 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    status.setText("Número inválido.");
                    return;
                }
                generate(lesson.get(), max);
            });
        });
    }

    private void generate(Lesson lesson, int max) {
        status.setText("Gerando cards (veja a tela Tarefas)…");
        try {
            cards.generate(lesson, max, new FxBudgetGate()).whenComplete((drafts, err) -> Platform.runLater(() -> {
                if (err != null) {
                    status.setText("Erro: " + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage()));
                } else {
                    approve(lesson, drafts);
                }
            }));
        } catch (Exception e) {
            status.setText("Erro: " + e.getMessage());
        }
    }

    private void approve(Lesson lesson, List<CardDraft> drafts) {
        if (drafts.isEmpty()) {
            status.setText("A IA não devolveu cards.");
            return;
        }
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Aprovar cards");
        VBox box = new VBox(8);
        List<CheckBox> checks = new ArrayList<>();
        List<TextArea> fronts = new ArrayList<>(), backs = new ArrayList<>();
        for (CardDraft cd : drafts) {
            CheckBox ck = new CheckBox("Salvar");
            ck.setSelected(true);
            TextArea f = new TextArea(cd.front()), b = new TextArea(cd.back());
            f.setPrefRowCount(2);
            b.setPrefRowCount(2);
            f.setWrapText(true);
            b.setWrapText(true);
            checks.add(ck);
            fronts.add(f);
            backs.add(b);
            box.getChildren().addAll(ck, f, b, new Separator());
        }
        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setPrefSize(600, 500);
        d.getDialogPane().setContent(sp);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            status.setText("Cards descartados.");
            return;
        }
        List<CardDraft> chosen = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++)
            if (checks.get(i).isSelected() && !fronts.get(i).getText().isBlank() && !backs.get(i).getText().isBlank())
                chosen.add(new CardDraft(fronts.get(i).getText(), backs.get(i).getText()));
        bg(() -> {
            int n = cards.saveDrafts(chosen, CardService.areaOfLesson(lesson), CardOrigin.AULA, lesson.title());
            return n + " cards salvos (" + (chosen.size() - n) + " repetidos).";
        });
    }
}
