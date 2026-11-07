package br.estudos.app.anki;

import br.estudos.domain.Area;
import br.estudos.domain.Card;

import java.util.List;
import java.util.Optional;

public interface CardRepository {
    /** Insere se a frente (hash) ainda não existe; vazio se duplicada. */
    Optional<Card> insertIfNew(Card c);
    /** Atualiza frente/verso/área/hash. Lança IllegalArgumentException se o novo hash já existe em outro card. */
    void update(Card c);
    void delete(long id);
    void markSynced(long id, long noteId, String at);
    /** Filtros nulos são ignorados. */
    List<Card> list(Area area, Boolean synced);
}
