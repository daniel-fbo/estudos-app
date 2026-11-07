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
