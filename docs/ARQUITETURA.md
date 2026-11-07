# Arquitetura

```
br.estudos
├── App.java                 # main JavaFX; monta dependências (injeção manual)
├── ui/                      # controllers + views (FXML ou código), um subpacote por tela
├── app/                     # serviços e interfaces de porta
│   ├── ports/               # AiRunner, ProcessRunner, Clock, AnkiClient, CodeforcesClient,
│   │                        # CalendarClient, PdfIndexer, FileSystemPort
│   ├── goals/  code/  ai/  lessons/  library/  anki/  cf/  calendar/
├── domain/                  # records imutáveis: StudySession, Goal, Verdict, AiRequest...
└── infra/
    ├── db/                  # Database, Migrator, repositórios JDBC
    ├── process/             # ProcessRunner real (timeout, kill da árvore, captura de stdout/stderr)
    ├── ai/                  # ClaudeCliRunner, AgyCliRunner, parser do JSON de saída
    ├── http/                # AnkiConnect, Codeforces (java.net.http.HttpClient)
    ├── search/              # Lucene + PDFBox
    ├── google/              # Calendar
    └── config/              # Config JSON
```

## Decisões

- **Injeção manual** em `App.java` (um `Services` com tudo). Sem framework.
- **`ProcessRunner`** é a base de compilação, execução de código e CLIs de IA. Recebe comando, diretório, stdin, timeout e variáveis de ambiente. Devolve exit code, stdout, stderr, duração e flag de timeout. No timeout mata a árvore de processos (`ProcessHandle.descendants()`).
- **`AiRunner`**: `AiResult run(AiRequest req, CancellationToken tok)`. O `AiService` em `app/ai` aplica cache, orçamento e registro em `ai_call`, e é o único lugar que chama um `AiRunner`.
- **Fila de tarefas**: `TaskQueue` com `ExecutorService` de 2 threads para IA e 2 para o resto. Observável pela UI.
- **Relógio injetável** (`Clock`) para testar sequência de dias, metas e agenda.
- **Modelos por tarefa** no config: `{"aulas":"opus","padrao":"sonnet","barato":"haiku"}`. Os nomes são aliases repassados à CLI; confirmar os aceitos com `claude --help`.

## Fluxo de uma chamada de IA

UI → `AiService.submit(req)` → calcula `cache_key` → se houver resultado OK em cache, devolve → confere o orçamento (se estourou, pede confirmação) → `TaskQueue` → `AiRunner.run` → grava em `ai_call` → callback na UI.
