# Progresso

Cada módulo concluído adiciona um bloco de no máximo 12 linhas. A próxima sessão lê este arquivo em vez de explorar o código.

Formato:

```
## M<n> — <nome> — <data> — <status: concluído | parcial | bloqueado>
- feito: ...
- interfaces públicas novas: ...
- pendências / bloqueios: ...
- notas para o próximo módulo: ...
```

---

## M0 — Esqueleto — 2026-11-06 — concluído
- feito: pom (Java 21, JavaFX 21.0.5, sqlite-jdbc, Jackson, Lucene, PDFBox, Logback, JUnit 5; `mvn javafx:run`); janela com barra lateral (10 telas, só Configurações implementada, demais "Em breve"); `Config` + `JsonConfigStore` (cria padrões, preenche campos ausentes); `Database` + `Migrator` (V1__base = docs/schema.sql, `schema_version`, FKs ligadas); log em `~/.estudos-app/logs/`; tela Configurações com "Testar" (`--version`). 15 testes.
- interfaces públicas novas: `app.ports.ConfigStore`, `app.ports.ProcessRunner` (+ `ProcessRequest`/`ProcessResult`; impl. `infra.process.SystemProcessRunner` com timeout e kill da árvore), `app.tools.ExecutableChecker`, `infra.db.Database.connect()/migrate()`, `br.estudos.Services` (injeção manual; `background()` = executor de 2 threads).
- flags confirmadas: claude 2.1.319 → `-p`, `--dangerously-skip-permissions`, `--output-format json`, `--model` (aliases `fable|opus|sonnet|haiku` ou nome completo). agy 1.5.1 → `agy --dangerously-skip-permissions --print "<prompt>"` (`--model <id>` opcional; ids em `agy models`). Ambos testados com "responda só OK". `REVISOR="agy"`.
- pendências: `TaskQueue` da ARQUITETURA ainda não existe (só `Services.background()`); M3 deve criá-la. Separador de SQL do Migrator não aceita `;`/`--` em strings nem triggers.
- notas: pasta base pode ser trocada com `-Destudos.home=...` (vale também para o log). Mudança de `dbPath` só vale ao reiniciar.

## M1 — Metas e progresso — 2026-11-06 — concluído
- feito: cronômetro (iniciar/pausar/retomar/encerrar, com área, assunto, nota) gravando em `study_session`; CRUD de metas HORAS/CONTAGEM (semanal/mensal, ativar/desativar, +/−); Painel com gráfico de barras de horas/área na semana, barras de progresso das metas ativas, sequência e últimas 5 sessões; telas Painel e Metas ligadas. 26 testes.
- interfaces públicas novas: `app.study.StudyService` (via `Services.study()`; relógio injetável), `StudyStats` (puro: `streak`, `periodStart/End`, `secondsByArea`, `progress`), `StudyRepository` (impl. `infra.study.JdbcStudyRepository`), domínio `StudySession`, `Goal`, `GoalKind`, `GoalPeriod`.
- regras: semana começa na segunda; sequência não zera se só hoje ainda não teve estudo; progresso de CONTAGEM = `manual_count + auto_count_ac` (não zera a cada período); só sessões encerradas contam.
- pendências: sem edição de título/alvo de meta existente (só criar/ativar/excluir); sessão em andamento se perde se o app fechar; sem fake de repositório (testes usam SQLite temporário).
- notas: `auto_count_ac` fica para M5/M6 (Codeforces) preencher; `StudyService.dashboard()` roda fora da thread JavaFX.

### M1 — correções da revisão
- bloqueante: GoalsView — criar/ajustar/ativar/excluir meta agora rodam em `services.background()` (`mutate`) e atualizam a UI com `Platform.runLater`; erros aparecem na tela.
- bloqueante: metas inativas mostravam 0 — novo `StudyService.allGoalsProgress()` (todas as metas) usado por GoalsView.
- bloqueante: `started_at` gravado truncado em segundos e `sessionsSince` compara com prefixo sem "Z" (cobre dados com fração).
- importante: `Timeline` do Painel para sozinho quando a tela sai da cena.
- importante: `createGoal` rejeita NaN/infinito; `createGoal`/`start` rejeitam enums nulos (IllegalArgumentException).
- importante: `runBg` do Painel captura Throwable e mostra Alert; `start` também mostra erro.
- itens "menor" ignorados (exceto `adjustCount` só para CONTAGEM, feito junto). Testes do revisor passaram sem ajuste.

## M2 — Runner de código — 2026-11-06 — concluído
- feito: tela Código (abrir/salvar `.c`/`.cpp`, editor monoespaçado, casos de teste em `code_test_case`, compilar+rodar todos, stress test com cancelar). Compila com g++/gcc + sanitizers (30 s); 2 s/caso; vereditos AC/WA(diff)/TLE/RE(stderr)/CE; comparação ignora espaço no fim de linha e linhas vazias finais. Cada execução grava em `code_run`. 35 testes (9 novos, compilam programas reais).
- interfaces públicas novas: `app.code.CodeRunnerService` (via `Services.code()`: `compile`, `runAll`, `stress`, `cases/addCase/deleteCase`), `CodeRepository` (impl. `infra.code.JdbcCodeRepository`), `CodeResults`, `OutputComparator`, domínio `TestCase`.
- stress: gerador recebe o nº da iteração em `argv[1]` (`.py` via `python3`, ou `.cpp`/`.c`); para na 1ª divergência e salva o caso (origin STRESS, esperado = saída da força bruta).
- decisão: `auto_count_ac` é contador (M1); um AC (todos os casos passam, ≥1 caso) soma +1 em toda meta ativa CONTAGEM/CP, em vez do "= 1" da SPEC.
- pendências: o mesmo AC repetido soma de novo; caminhos dos compiladores vêm de `Config.gccPath/gppPath`; sem teste de UI nem `mvn javafx:run` verificado (ambiente sem display).

### M2 — correções da revisão
- importante: CodeView `save()` agora retorna boolean; `runAll`/stress abortam se o salvamento falhar.
- importante: sem casos de teste, `runAll` não grava `code_run` e a UI mostra "Nenhum caso de teste cadastrado."
- importante: `stress` lança IllegalArgumentException se `iterations <= 0`; a UI valida inteiro > 0.
- importante: `runCase` executa o binário com `workDir = binary.getParent()` (temp isolado).
- itens "menor" ignorados. Testes do revisor passaram sem ajuste.

### M1/M2 — correção da contagem de AC — 2026-11-07
- `auto_count_ac` volta a ser marca (SPEC): `Goal.countAc` (boolean); caixa "Contar ACs do runner" em Metas (só CONTAGEM + CP).
- progresso = `manual_count` + arquivos cujo **primeiro** AC caiu no período da meta (`StudyRepository.firstAcTimes()` lê `code_run`). Repetir AC não soma; zera por semana/mês.
- removido `CodeRepository.creditAcToCpGoals()`; runner só grava `code_run`. Novos: `StudyService.createGoal(..., countAc)`, `Goal.withActive/withCountAc/countsAc`. 66 testes.
- pendência: `manual_count` ainda não zera por período.

## M3 — Núcleo de IA — 2026-11-07 — concluído
- feito: `AiService` (cache por `sha256(runner+modelo+tarefa+prompt+hash dos anexos)`, "Gerar de novo" = `refresh`, orçamento diário de chamadas com confirmação, registro OK/ERRO/CANCELADO em `ai_call`); `TaskQueue` (2 threads IA + 2 gerais, histórico de 50, cancelar); telas **Tarefas** e **Uso de IA** (hoje x/limite + tokens, taxa de cache, tabelas por dia/tarefa/modelo, teste rápido Enviar/Gerar de novo). 95 testes.
- interfaces públicas novas: `app.ports.AiRunner` (impl. `infra.ai.ClaudeCliRunner`/`AgyCliRunner`, parser `CliOutputParser`), `app.ai.AiService` (`submit(req, refresh, gate)` → `TaskHandle<AiOutcome>`, `execute` síncrono, `budget()`, `usage(dias)`, `supported(kind)`), `AiCallRepository` (impl. `JdbcAiCallRepository`), `BudgetGate` (UI: `ui.ai.FxBudgetGate`), `app.tasks.TaskQueue/TaskHandle/CancellationToken`; domínio `AiRequest`, `AiResult`, `AiCall`, `AiRunnerKind`, `AiTask`, `AiCallStatus`. `Services.tasks()`, `Services.ai()`; `Services.background()` agora é o executor geral da `TaskQueue`.
- migration `V2__ai_call_cache_hit`: coluna `cache_hit`; acertos de cache são gravados (taxa) mas não contam no orçamento nem servem de fonte de cache.
- CLIs: claude `-p <prompt> --output-format json [--model m] --dangerously-skip-permissions` (tokens de entrada = input + cache_creation + cache_read; custo de `total_cost_usd`); agy tem modo não interativo: `--dangerously-skip-permissions --output-format json [--model m] --print <prompt>` (sem custo). Rodam no `estudosRepo`; se a pasta não existe → erro claro (hoje `~/estudos` não existe nesta máquina).
- teste manual (`AI_REAL=1 mvn test -Dtest=RealCliManualTest`), 2026-11-07: claude haiku → "OK", 32825/39 tokens, US$ 0,0049; agy padrão → "OK", 6914/125 tokens. `mvn javafx:run` abriu sem exceção (as telas novas não foram clicadas).
- pendências: orçamento só por nº de chamadas (tokens só exibidos); prompt vai como argumento (limite de ~128 KB por argumento no Linux); timeout padrão 10 min por `AiRequest`, sem campo no config.

### M3 — correções da revisão
- bloqueante: `AiUsage.Row.add` sem NPE quando `costUsd` é nulo (sem ternário misto Double/double).
- importante: `CliAiRunner` só interrompe a thread enquanto `run()` está ativo (flag `running`, desligada no `finally`).
- importante: `TaskQueue` canceller ignora tarefa já finalizada.
- importante: `FxBudgetGate` abre o diálogo direto se já estiver na thread JavaFX (sem deadlock).
- importante: `CacheKeys` calcula o SHA-256 dos anexos em blocos de 64 KB.
- importante: `CancellationToken.onCancel/cancel` sincronizados; callback roda uma só vez.
- itens "menor" ignorados. Testes do revisor passaram sem ajuste.

## M4 — Aulas — 2026-11-07 — concluído
- feito: tela **Aulas** (tipo, conteúdo, tópicos → "Gerar"; lista com busca por título e filtro de área; Abrir PDF/pasta; status gerando/pronta/falhou, "Ver log"). Aviso de aula parecida (Jaccard de palavras ≥ 0,5) antes de gerar. Envia `aula_matematica|aula_programacao <conteúdo>: <tópicos>` ao Claude (modelo `aulas`, timeout 60 min, sempre `refresh` = sem cache) pela fila de IA.
- ao terminar: procura `.pdf`/`.md` modificados desde o início sob `estudosRepo` (ignora pastas ocultas e `aulas/`), move para `<estudosRepo>/aulas/<matematica|programacao>/<slug>/` e atualiza `lesson`. Sem arquivo novo → FALHOU com log.
- interfaces públicas novas: `app.lessons.LessonService` (`generate`, `similar`, `list`, `command`; via `Services.lessons()`), `LessonRepository` (impl. `infra.lessons.JdbcLessonRepository`), domínio `Lesson`, `LessonStatus`. Migration `V3__lesson_log` (coluna `log`).
- testes: `LessonServiceTest` com runner falso que grava PDF/.md (fluxo, falha, título parecido). 101 testes.
- pendências: aulas "GERANDO" ficam assim se o app fechar no meio; duas gerações simultâneas podem trocar arquivos entre si (detecção por data); `mvn javafx:run` não verificado (sem display).

### M4 — correções da revisão
- importante: `LessonFiles.findNew` só aceita `.md` com o mesmo nome do PDF (sem PDF, só se houver um único `.md`); teste `naoMoveMdSemRelacaoComOPdf` reativado (era `@Disabled`, estava certo).
- importante: destino da aula usa `LessonFiles.freeDir` (sufixo `-2`, `-3`) em vez de sobrescrever aula anterior de mesmo slug.
- importante: faltando PDF ou `.md`, a aula fica FALHOU com log dos arquivos encontrados (nada é movido).
- itens "menor" ignorados.

## M5 — Biblioteca — 2026-11-07 — concluído
- feito: tela **Biblioteca** (cadastro/edição/exclusão de livros, filtros, importar pasta e indexar em segundo plano pela fila Tarefas, busca no texto com trecho em negrito e abrir PDF, atualizar página atual → status LENDO/LIDO automático, "Perguntar"). Índice Lucene em `~/.estudos-app/lucene/`, 1 documento por página.
- pergunta: `askAgy` envia o caminho do PDF (anexo entra na chave de cache; resposta em cache); se o agy falha/indisponível a UI oferece `askClaude` com as 8 melhores páginas do Lucene.
- interfaces públicas novas: `app.library.LibraryService` (`Services.library()`), `BookRepository` (impl. `JdbcBookRepository`), `PdfReader` (`PdfBoxReader`), `PageIndex` (`LucenePageIndex`); domínio `Book`, `BookKind`, `BookStatus`.
- testes: `LibraryServiceTest` com 2 PDFs gerados via PDFBox (importar, buscar, reimportar, progresso, excluir, perguntas com runners falsos). Todos passam.
- pendências: "alimenta metas" não implementado (a página atual só fica em `book.current_page`; metas não leem isso ainda); texto de PDFs escaneados não é extraído (sem OCR); `mvn javafx:run` não verificado (sem display).

### M5 — correções da revisão
- importante: `snippet` compara com `fold()` (minúsculas/sem acento que preserva o comprimento), sem `StringIndexOutOfBounds` em textos como `İ`.
- importante: analyzer próprio (StandardTokenizer + LowerCase + `FoldFilter` sem acentos) no índice e na consulta; "minimos" acha "mínimos". Livros já indexados precisam ser reindexados.
- importante: `textQuery` passa `AND`/`OR`/`NOT` soltos para minúsculas, então "Dijkstra AND" não zera a busca.
- itens "menor" ignorados. Testes do revisor passaram sem ajuste.

## M6 — Anki — 2026-11-07 — concluído
- feito: tela **Anki** (lista com filtros área/sincronização, novo/editar/excluir card, importar `.md` do repositório, gerar de uma aula com diálogo para aprovar/editar antes de salvar, sincronizar). Tabela `card` já existia no V1 (sem migration nova); "status de sincronização" = `anki_note_id`/`synced_at` nulos ou não.
- importação: blocos `> **Card.** …` / `> ---` / `> …` (vários por citação); área inferida do caminho (matematica, programacao→CP, xadrez, musica, senão OUTRO); dedup por SHA-256 da frente normalizada (`front_hash` UNIQUE).
- geração: `CardService.generate` usa modelo `barato`, tarefa CARDS, aula inline (corte em 60k chars); aceita JSON `[{frente, verso}]` mesmo cercado de texto/```; limita a N; nada é salvo sem `saveDrafts`.
- sync: `createDeck` + `addNotes` (modelo "Basic", `allowDuplicate=false`), baralho `Estudos::<Área>`; recusados (null) continuam pendentes; Anki fechado → `AnkiException.unavailable()` com mensagem pedindo para abrir o Anki.
- interfaces públicas novas: `app.anki.CardService` (`Services.cards()`), `CardRepository` (`JdbcCardRepository`), `CardMarkdownParser`, `app.ports.AnkiClient` (`infra.http.AnkiConnectClient`), domínio `Card`, `CardOrigin`. 
- testes: `CardServiceTest` (parser, hash, importação/dedup, geração com runner falso, sync com fake, cliente HTTP com servidor falso e sem servidor).
- pendências: URL do AnkiConnect fixa em localhost:8765; editar card já sincronizado não atualiza a nota no Anki; `mvn javafx:run` não verificado (sem display).

### M6 — correções da revisão
- importante: `importFromRepo` pula (com aviso no log) arquivos `.md` não-UTF-8/ilegíveis em vez de abortar a importação inteira. Itens "menor" ignorados; testes do revisor passaram sem ajuste.

## M7 — Codeforces e dicas — 2026-11-07 — concluído
- feito: tela **Codeforces** (Sincronizar; gráfico de rating; AC por tag; tags com mais WA/TLE; "Treine estas 3 tags" = pior taxa de AC com ≥3 tentativas e ≥1 falha; aba **Dicas**). Botão "Dicas…" no Código abre o mesmo painel com o nome do arquivo como problema.
- sync: `user.status` + `user.rating` do `codeforcesHandle` (config) → `cf_submission` (upsert por id) e `cf_rating` (migration `V4__cf_rating`, substituída a cada sync). CE/TESTING não contam como falha.
- dicas: 3 níveis, modelo `barato`, tarefa DICA, prompt proíbe código; nível N só após N-1 (`HintLevels`); texto salvo em `hint` por problema (cache; não chama IA de novo).
- interfaces públicas novas: `app.cf.CfService` (`Services.cf()`: `sync`, `overview`, `hint`, `maxHintLevel`), `CfStats` (puro), `HintLevels`, `CfRepository` (`infra.cf.JdbcCfRepository`), `app.ports.CodeforcesClient` (`infra.http.CodeforcesHttpClient`, 1 req/2 s).
- testes: `CfTest` (agregação por tag, sync idempotente, níveis/cache de dicas, cliente HTTP com servidor falso e intervalo).
- pendências: enunciado por URL só é repassado ao Claude (o app não baixa a página); `mvn javafx:run` não verificado (sem display); "auto_count_ac" não usa `cf_submission`.

## M8 — Agenda — 2026-11-07 — concluído
- feito: tela **Agenda** (instruções para criar credenciais OAuth, "Autorizar Google", grade semanal 6h–23h de horários livres, "Propor blocos", "Aprovar e criar eventos", lista de blocos da semana com "Marcar como feito"). Eventos vão para o calendário "Estudos" (criado se faltar).
- planejador guloso sem IA (`BlockPlanner`): metas de HORAS ativas; falta = alvo − progresso (mensais ÷ semanas restantes); sempre a meta com mais minutos faltando → primeiro horário livre, preferindo dia sem bloco dessa meta; blocos de 30–90 min; ignora horários passados.
- marcar como feito: `StudyService.logSession` cria a `study_session` (idempotente); usa a tabela `calendar_block` do V1 (sem migration nova).
- interfaces públicas novas: `app.calendar.CalendarService` (`Services.calendar()`), `BlockPlanner`, `CalendarRepository` (`JdbcCalendarRepository`), `app.ports.CalendarClient` (`infra.google.GoogleCalendarClient`: cliente oficial; `~/.estudos-app/google/credentials.json` + `tokens/`), domínio `CalendarBlock`. Deps novas no pom: google-api-services-calendar, google-api-client, google-oauth-client-jetty.
- testes: `CalendarTest` (distribuição, dias distintos, passado, fim de horários, aprovar/marcar feito com cliente falso).
- pendências: integração real com o Google NÃO testada (sem credenciais/display) — testar à mão: salvar credentials.json, Autorizar, propor, aprovar e conferir no Google Calendar; grade de horários livres não é salva; metas de CONTAGEM não entram no plano; `mvn javafx:run` não verificado.
