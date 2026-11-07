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
