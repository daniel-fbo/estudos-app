package br.estudos.app.lessons;

import br.estudos.domain.Lesson;

import java.util.List;

public interface LessonRepository {
    Lesson insert(Lesson l);

    void update(Lesson l);

    /** Filtra por trecho do título (sem diferenciar maiúsculas) e área; {@code null}/vazio = sem filtro. */
    List<Lesson> search(String titleQuery, String area);

    /** Aulas {@code GERANDO} viram {@code FALHOU} com o log dado. Devolve quantas. */
    int failGenerating(String log);
}
