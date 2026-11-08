package br.estudos.domain;

/** Card de estudo. {@code ankiNoteId == null} = ainda não sincronizado. */
public record Card(long id, String front, String back, Area area, CardOrigin origin, String sourceRef,
                   String frontHash, Long ankiNoteId, String syncedAt) {
    /** Sincronizado = tem nota no Anki e nenhuma edição pendente ({@code syncedAt} nulo marca pendência). */
    public boolean synced() { return ankiNoteId != null && syncedAt != null; }
}
