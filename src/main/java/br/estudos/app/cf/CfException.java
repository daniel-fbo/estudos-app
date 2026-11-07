package br.estudos.app.cf;

public class CfException extends Exception {
    public CfException(String msg) { super(msg); }
    public CfException(String msg, Throwable cause) { super(msg, cause); }
}
