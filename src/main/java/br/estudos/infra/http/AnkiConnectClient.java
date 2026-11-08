package br.estudos.infra.http;

import br.estudos.app.anki.AnkiException;
import br.estudos.app.ports.AnkiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Cliente do AnkiConnect (API versão 6). */
public class AnkiConnectClient implements AnkiClient {
    public static final String DEFAULT_URL = br.estudos.app.config.Config.DEFAULT_ANKI_URL;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final java.util.function.Supplier<String> url;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public AnkiConnectClient(String url) { this(() -> url); }

    /** O endereço é lido a cada chamada, para valer logo após salvar as Configurações. */
    public AnkiConnectClient(java.util.function.Supplier<String> url) { this.url = url; }

    @Override
    public void updateNoteFields(long noteId, String front, String back) throws AnkiException {
        ObjectNode params = JSON.createObjectNode();
        ObjectNode note = params.putObject("note").put("id", noteId);
        note.putObject("fields").put("Front", front).put("Back", back);
        call("updateNoteFields", params);
    }

    @Override
    public void createDeck(String deck) throws AnkiException {
        ObjectNode params = JSON.createObjectNode().put("deck", deck);
        call("createDeck", params);
    }

    @Override
    public List<Long> addNotes(List<Note> notes) throws AnkiException {
        ObjectNode params = JSON.createObjectNode();
        ArrayNode arr = params.putArray("notes");
        for (Note n : notes) {
            ObjectNode o = arr.addObject();
            o.put("deckName", n.deck()).put("modelName", "Basic");
            o.putObject("fields").put("Front", n.front()).put("Back", n.back());
            o.putObject("options").put("allowDuplicate", false);
            o.putArray("tags").add("estudos-app");
        }
        JsonNode res = call("addNotes", params);
        if (!res.isArray() || res.size() != notes.size())
            throw new AnkiException("Resposta inesperada do AnkiConnect em addNotes.");
        List<Long> ids = new ArrayList<>();
        for (JsonNode n : res) ids.add(n.isNull() ? null : n.asLong());
        return ids;
    }

    private JsonNode call(String action, ObjectNode params) throws AnkiException {
        ObjectNode body = JSON.createObjectNode().put("action", action).put("version", 6);
        body.set("params", params);
        try {
            URI target;
            try {
                target = URI.create(url.get().strip());
            } catch (RuntimeException e) {
                throw new AnkiException("Endereço do AnkiConnect inválido: " + url.get(), false, e);
            }
            HttpRequest req;
            try {
                req = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build();
            } catch (IllegalArgumentException e) {
                throw new AnkiException("Endereço do AnkiConnect inválido: " + url.get(), false, e);
            }
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new AnkiException("AnkiConnect respondeu HTTP " + resp.statusCode());
            JsonNode root = JSON.readTree(resp.body());
            JsonNode err = root.get("error");
            if (err != null && !err.isNull()) throw new AnkiException("AnkiConnect: " + err.asText());
            JsonNode result = root.get("result");
            return result == null ? JSON.nullNode() : result;
        } catch (java.net.ConnectException | java.net.http.HttpTimeoutException e) {
            throw unavailable(e);
        } catch (IOException e) {
            if (e.getClass().getName().contains("Connect")) throw unavailable(e);
            throw new AnkiException("Falha ao falar com o AnkiConnect: " + e.getMessage(), false, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AnkiException("Interrompido.", false, e);
        }
    }

    private static AnkiException unavailable(Exception e) {
        return new AnkiException("Não consegui falar com o Anki. Abra o Anki (com o add-on AnkiConnect) e tente de novo.", true, e);
    }
}
