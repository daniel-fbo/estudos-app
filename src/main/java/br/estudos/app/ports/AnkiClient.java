package br.estudos.app.ports;

import br.estudos.app.anki.AnkiException;

import java.util.List;

/** Acesso ao AnkiConnect. */
public interface AnkiClient {
    record Note(String deck, String front, String back) {}

    void createDeck(String deck) throws AnkiException;

    /** Um id por nota, na mesma ordem; {@code null} onde o Anki recusou (ex.: duplicada). */
    List<Long> addNotes(List<Note> notes) throws AnkiException;
}
