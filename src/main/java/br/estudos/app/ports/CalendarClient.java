package br.estudos.app.ports;

import java.time.Instant;

/** Porta para o Google Calendar. */
public interface CalendarClient {
    /** Há credenciais do cliente OAuth e token salvo? */
    boolean authorized();

    /** Abre o fluxo OAuth (navegador) se preciso. Bloqueante. */
    void authorize() throws CalendarException;

    /** Cria o evento no calendário "Estudos" (criado se não existir) e devolve o id do evento. */
    String createEvent(String title, String description, Instant start, Instant end) throws CalendarException;

    class CalendarException extends Exception {
        public CalendarException(String m) { super(m); }
        public CalendarException(String m, Throwable c) { super(m, c); }
    }
}
