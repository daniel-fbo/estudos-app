# Estudos App

App desktop pessoal que junta num lugar só o estudo de matemática, programação competitiva, xadrez e música: metas e tempo de estudo, prática de código com casos de teste, biblioteca de PDFs, aulas geradas por IA, cards do Anki, Codeforces e agenda no Google Calendar.

O app só coordena. O trabalho de IA é feito pelas CLIs `claude` e `agy`, chamadas sob demanda e sempre com cache: a mesma pergunta nunca é paga duas vezes.

Java 21 · JavaFX 21 · Maven · SQLite · Lucene · PDFBox · JUnit 5

## Telas

| Tela | O que faz |
|---|---|
| **Painel** | Cronômetro de estudo por área, horas da semana, sequência de dias, progresso das metas e últimas sessões. |
| **Metas** | Metas semanais ou mensais de horas ou de contagem. A contagem pode ser manual (+/−) ou automática: ACs do runner, ACs do Codeforces ou páginas lidas. Tudo zera na virada do período. |
| **Código** | Editor de `.c`/`.cpp`, casos de teste, compilação com sanitizers, vereditos AC/WA/TLE/RE/CE e stress test contra uma força bruta. Botão **Dicas…** para pedir dicas em 3 níveis, sem código. |
| **Aulas** | Gera aulas com as skills `aula_matematica` e `aula_programacao` (uma por vez) e guarda PDF e Markdown em `~/estudos/aulas/<área>/<slug>/`. |
| **Biblioteca** | Importa uma pasta de PDFs, busca no texto (sem diferenciar acentos), registra a página atual e permite perguntar ao livro. |
| **Anki** | Cards criados à mão, importados dos `.md` do repositório `estudos` ou gerados de uma aula, com sincronização pelo AnkiConnect. |
| **Codeforces** | Sincroniza submissões e rating, mostra AC por tag e as tags com mais WA/TLE, e sugere 3 tags para treinar. |
| **Agenda** | Você marca os horários livres numa grade, o app propõe blocos de estudo a partir das metas e cria os eventos num calendário "Estudos" do Google. |
| **Tarefas / Uso de IA** | Fila de tarefas em segundo plano (com cancelar), chamadas e tokens por dia, tarefa e modelo, custo estimado e taxa de acerto do cache. |
| **Configurações** | Caminhos, modelos, orçamento diário de IA (chamadas e tokens), tema claro/escuro e backup `.zip` do banco e do config. |

Atalhos: **Ctrl+1…0** trocam de tela.

## Requisitos

- Java 21+ e Maven 3.9+
- `gcc` e `g++` (tela Código)
- `python3` (geradores `.py` do stress test)
- `claude` (Claude Code), logado com `claude auth login`. Opcional: `agy` (Antigravity)
- Opcional: Anki com o complemento AnkiConnect (`2055492159`)

## Rodar

```bash
mvn javafx:run
```

Na primeira execução o app cria `~/.estudos-app/` com:

| Caminho | Conteúdo |
|---|---|
| `config.json` | Configurações; edite pela tela Configurações |
| `estudos.db` | Banco SQLite; as migrations são aplicadas sozinhas ao abrir |
| `logs/` | Log diário |
| `lucene/` | Índice de busca da Biblioteca |
| `google/` | Credenciais e token do Google Calendar (nunca vão para o repositório) |

A pasta base pode ser trocada com `-Destudos.home=/outro/caminho`.

## Primeira configuração

1. **Repositório `estudos`.** As CLIs de IA rodam nele e as aulas são guardadas nele. O padrão é `~/estudos`.
2. **Codeforces.** Preencha o handle em Configurações e clique em **Sincronizar** na tela Codeforces.
3. **Anki.** Abra o Anki com o AnkiConnect instalado antes de sincronizar. O endereço padrão é `http://localhost:8765`.
4. **Google Calendar.** No Google Cloud Console, crie um cliente OAuth do tipo *App para computador* com a API do Calendar ativada. Salve o JSON em `~/.estudos-app/google/credentials.json` e clique em **Autorizar Google** na tela Agenda.
5. **Modelos de IA.** O padrão é `opus` para aulas, `sonnet` para o resto e `haiku` para tarefas baratas (cards, dicas).

## Pacote e atalho no menu

```bash
scripts/empacotar.sh        # gera target/jpackage/Estudos/bin/Estudos (JVM embutida)
scripts/instalar-atalho.sh  # cria o "Estudos" no menu de aplicativos
```

O pacote usa o mesmo `~/.estudos-app` do `mvn javafx:run`. Ele fica em `target/`: depois de um `mvn clean` ou de mudar o código, rode `scripts/empacotar.sh` de novo.

## Testes

```bash
mvn test
```

Os testes usam SQLite em arquivo temporário e *fakes* para tudo o que é externo (CLIs de IA, AnkiConnect, Codeforces, Google). Os da tela Código compilam e rodam programas reais com `gcc`/`g++`.

Teste manual com as CLIs reais (gasta uma chamada curta de cada):

```bash
AI_REAL=1 mvn test -Dtest=RealCliManualTest
```

## Arquitetura

```
br.estudos
├── App, Launcher, Services   # entrada e injeção manual de dependências
├── ui/       # telas JavaFX (controllers finos)
├── app/      # serviços e portas (interfaces): ai, code, study, lessons, library, anki, cf, calendar, backup
├── domain/   # records imutáveis
└── infra/    # implementações: db (JDBC + migrations), process, ai (CLIs), http, search (Lucene), google
```

- Toda chamada externa passa por uma interface em `app` com implementação em `infra`.
- Nada de operação longa na thread do JavaFX.
- Migrations em `src/main/resources/db/V<n>__<nome>.sql`. Nunca edite uma já commitada; crie a próxima.

Detalhes em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) e no esquema [`docs/schema.sql`](docs/schema.sql).

## Como o app foi construído

O código foi escrito por agentes de IA, um módulo por vez, com revisão cruzada:

```bash
scripts/construir.sh M1 [M2 ...]   # implementa (Claude) → testes → revisa (Antigravity ou Claude) → corrige → merge
scripts/construir.sh --uso         # tokens e custo estimado de cada etapa
```

| Arquivo | Para quê |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Regras comuns aos agentes: stack, camadas, economia de tokens, definição de pronto |
| [`CLAUDE.md`](CLAUDE.md) / [`GEMINI.md`](GEMINI.md) | Papel de cada agente: o Claude implementa, o Antigravity revisa |
| [`docs/SPEC.md`](docs/SPEC.md) | O que cada módulo faz (M0–M9, P1–P3) e os critérios de aceite |
| [`docs/PROGRESSO.md`](docs/PROGRESSO.md) | Registro de cada módulo: interfaces novas, decisões e pendências |
| [`scripts/agentes.env`](scripts/agentes.env) | Flags das CLIs, modelo por módulo e qual revisor usar |
| [`LEIA-ME.md`](LEIA-ME.md) | Instruções originais do kit de construção |

As limitações conhecidas e o que ficou de fora de propósito estão no fim de `docs/SPEC.md` e nas linhas de pendências de `docs/PROGRESSO.md`.
