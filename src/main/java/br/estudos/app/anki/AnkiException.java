package br.estudos.app.anki;

public class AnkiException extends Exception {
    private final boolean unavailable;
    public AnkiException(String msg, boolean unavailable, Throwable cause) { super(msg, cause); this.unavailable = unavailable; }
    public AnkiException(String msg) { this(msg, false, null); }
    /** {@code true} se o Anki/AnkiConnect não respondeu (provavelmente fechado). */
    public boolean unavailable() { return unavailable; }
}
