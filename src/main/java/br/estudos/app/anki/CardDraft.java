package br.estudos.app.anki;

/** Card ainda não salvo (vindo do parser ou da IA). */
public record CardDraft(String front, String back) {}
