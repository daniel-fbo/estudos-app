package br.estudos.domain;

import java.time.Instant;

/** Aula gerada pela skill de IA. {@code kind}: aula_matematica | aula_programacao. */
public record Lesson(long id, String kind, String area, String title, String topics, LessonStatus status,
                     String pdfPath, String mdPath, String log, Instant createdAt) {

    public Lesson withResult(LessonStatus s, String pdf, String md, String log) {
        return new Lesson(id, kind, area, title, topics, s, pdf, md, log, createdAt);
    }
}
