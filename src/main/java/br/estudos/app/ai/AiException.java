package br.estudos.app.ai;

/** Falha numa chamada de IA (CLI ausente, saída não zero, timeout, resposta inválida). */
public class AiException extends Exception {
    public AiException(String message) { super(message); }
    public AiException(String message, Throwable cause) { super(message, cause); }
}
