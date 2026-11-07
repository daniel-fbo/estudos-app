# SPEC — Estudos App

App desktop de uso individual. Concentra metas, progresso, prática de código, biblioteca, aulas geradas por IA, cards do Anki e agenda. **Princípio de custo:** tudo que pode ser código comum é código comum (zero tokens). IA só sob demanda, com o modelo mais barato que resolve, e todo resultado de IA fica guardado para nunca ser pedido duas vezes.

Áreas de estudo (enum `Area`): `CP`, `MATEMATICA`, `XADREZ`, `MUSICA`, `FACULDADE`, `OUTRO`.

Configuração em `~/.estudos-app/config.json` (criado com padrões na primeira execução): caminho do repositório `estudos`, caminho do banco (`~/.estudos-app/estudos.db`), caminhos de `claude`, `agy`, `gcc`, `g++`, handle do Codeforces, modelo padrão por tipo de tarefa, orçamento diário de chamadas de IA.

Cada seção abaixo é um módulo. Um módulo = uma sessão de implementação.

---

## M0 — Esqueleto

- Projeto Maven com `javafx-maven-plugin` (`mvn javafx:run`), Java 21, dependências da stack.
- Janela principal com barra lateral de navegação: Painel, Metas, Código, Aulas, Biblioteca, Anki, Codeforces, Agenda, Uso de IA, Configurações. Telas ainda não implementadas mostram "Em breve".
- `Config` (carrega/salva JSON, cria padrões), `Database` (conexão SQLite, aplica migrations de `db/` em ordem, tabela `schema_version`), migration `V1__base.sql` com o esquema de `docs/schema.sql`.
- Logback gravando em `~/.estudos-app/logs/`.
- Tela Configurações funcional: editar e salvar os campos do config, com botão "Testar" para cada executável (roda `--version` e mostra o resultado).
- **Aceite:** `mvn test` passa; `mvn javafx:run` abre a janela; o banco é criado com todas as tabelas; o config é salvo e recarregado.

## M1 — Metas e progresso

- **Sessão de estudo:** cronômetro (iniciar, pausar, encerrar) com área, assunto e nota opcional. Grava em `study_session`.
- **Metas:** CRUD de metas semanais ou mensais por área, de dois tipos: `HORAS` (alvo em horas) e `CONTAGEM` (ex.: "5 problemas", com contador manual + / −).
- **Painel:** horas por área na semana (gráfico de barras JavaFX), progresso de cada meta ativa (barra), sequência de dias seguidos com estudo, últimas 5 sessões.
- **Aceite:** testes do cálculo da sequência (incluindo virada de semana e dia sem estudo) e do progresso das metas.

## M2 — Runner de código

- Tela Código: escolher um arquivo `.c`/`.cpp` (padrão: `estudos/cp/problemas/`), editor simples (`TextArea` monoespaçado) e painel de casos de teste (entrada / saída esperada), salvos em `code_test_case`.
- **Compilar:** `g++ -std=c++17 -O2 -Wall -Wextra -fsanitize=address,undefined -g` (ou `gcc -std=c17` para `.c`), com timeout de 30 s. Mostra os erros do compilador.
- **Rodar todos os casos:** timeout de 2 s por caso. Veredito por caso: `AC`, `WA` (com diff linha a linha), `TLE`, `RE` (com stderr). A comparação ignora espaço no fim de linha e linhas vazias no final.
- **Stress test:** escolher força bruta + gerador (`.py` ou `.cpp`) e um número de iterações; para no primeiro caso divergente e o adiciona aos casos de teste.
- Cada execução grava em `code_run`; um AC conta automaticamente para metas `CONTAGEM` de área CP que tenham `auto_count_ac = 1`.
- **Aceite:** testes com programas pequenos de exemplo (AC, WA, TLE, RE, erro de compilação). Processos sempre mortos no timeout.

## M3 — Núcleo de IA

- Interface `AiRunner` com implementações `ClaudeCliRunner` e `AgyCliRunner`. Ambas executam a CLI em modo não interativo no diretório do repositório `estudos`, com modelo escolhido, timeout configurável e cancelamento.
- **Claude:** `claude -p <prompt> --output-format json --model <modelo>`, mais as flags de permissão necessárias (confirmar com `claude --help`). Ler do JSON a resposta, custo e tokens, se presentes.
- **Agy:** descobrir o modo não interativo com `agy --help`. Se não existir, a implementação lança `UnsupportedOperationException` com mensagem clara e a UI desabilita os botões que dependem dela.
- **Cache:** antes de chamar, calcular `sha256(runner + modelo + tarefa + prompt + hash dos arquivos anexados)`. Se existir em `ai_call` com sucesso, devolver o resultado guardado. O botão "Gerar de novo" ignora o cache explicitamente.
- **Orçamento:** contador diário de chamadas e tokens. Acima do orçamento configurado, pede confirmação antes de chamar.
- **Fila:** chamadas longas rodam em segundo plano. Um painel "Tarefas" mostra as que estão rodando e as concluídas, com cancelar.
- **Tela Uso de IA:** chamadas por dia, por tarefa e por modelo; tokens e custo estimado quando disponíveis; taxa de acerto do cache.
- **Aceite:** testes com um `FakeAiRunner` cobrindo cache, orçamento e cancelamento; teste manual documentado em `PROGRESSO.md` com uma chamada real curta a cada CLI.

## M4 — Aulas

- Tela Aulas com formulário: tipo (`aula_matematica` ou `aula_programacao`), conteúdo e tópicos. O botão "Gerar" monta o comando da skill (ex.: `aula_matematica Teoria dos grupos: subgrupos, Lagrange`) e envia ao `ClaudeCliRunner` com o modelo `aulas` do config, pela fila.
- Ao terminar, localizar o PDF e o `.md` gerados (procurar arquivos novos no diretório de trabalho pela data de modificação), movê-los para `estudos/aulas/<area>/<slug>/` e registrar em `lesson`.
- Lista de aulas com busca por título e área; abrir PDF (`Desktop.open`) e pasta. Status: gerando, pronta, falhou (com log).
- Antes de gerar, avisar se já existe aula com título parecido (similaridade simples por palavras).
- **Aceite:** teste do fluxo com `FakeAiRunner` que cria arquivos falsos; a aula aparece na lista com os caminhos certos.

## M5 — Biblioteca

- Cadastro de livros e materiais (`book`): título, autores, área, tipo (`LIVRO`, `NOTAS`, `ARTIGO`, `SLIDES`, `OUTRO`), caminho do PDF, total de páginas, página atual, status (`QUERO_LER`, `LENDO`, `LIDO`), tags e notas.
- **Importar pasta:** varre PDFs, preenche título e páginas pelos metadados do PDFBox e indexa o texto no Lucene (índice em `~/.estudos-app/lucene/`), página por página, em segundo plano.
- **Busca:** texto livre em todos os livros, com resultados por livro e página e um trecho destacado. Clique abre o PDF.
- **Progresso de leitura:** atualizar a página atual; isso alimenta metas.
- **Perguntar a este livro:** envia o PDF e a pergunta ao `AgyCliRunner` (contexto longo), com a resposta em cache. Se o agy estiver indisponível, oferece Claude com os trechos relevantes do Lucene (top 8 páginas) em vez do livro inteiro.
- **Aceite:** testes de indexação e busca com 2 PDFs pequenos gerados no teste.

## M6 — Anki

- Tabela `card`: frente, verso, área, origem (`MANUAL`, `AULA`, `NOTA`, `IA`), id da nota no Anki, status de sincronização.
- **Importar cards** dos `.md` do repositório `estudos`, procurando blocos `> **Card.** pergunta` / `> ---` / `> resposta`. Deduplicação por hash da frente.
- **Gerar cards de uma aula:** envia o `.md` da aula ao Claude com o modelo `barato` do config e o pedido de no máximo N cards em JSON (`[{frente, verso}]`). Mostra os cards para aprovar ou editar antes de salvar.
- **Sincronizar** com o Anki via AnkiConnect (`http://localhost:8765`, `addNotes`), um baralho por área (`Estudos::CP` etc.). Se o Anki estiver fechado, a mensagem diz isso.
- **Aceite:** testes do parser de Markdown e da deduplicação; teste do cliente AnkiConnect com servidor HTTP falso.

## M7 — Codeforces e dicas

- Sincronizar submissões via API pública (`user.status`, `user.rating`) com o handle do config; guardar em `cf_submission`. Respeitar o limite da API (1 requisição a cada 2 s).
- **Tela:** gráfico do rating, taxa de AC por tag, tags com mais WA/TLE e a sugestão "treine estas 3 tags" (código comum, sem IA).
- **Dica em níveis** num problema (ligado ao arquivo do Runner): botões "Dica 1 (tema)", "Dica 2 (observação-chave)" e "Dica 3 (esboço)". Cada nível chama o Claude com o modelo `barato`, o enunciado colado ou a URL, e a instrução de **não** dar código. Cada nível só é liberado depois do anterior. As dicas ficam em cache por problema.
- **Aceite:** testes de agregação por tag com JSON de exemplo e do controle de níveis.

## M8 — Agenda

- Integração com o Google Calendar (cliente Java oficial, fluxo OAuth para app instalado). Token em `~/.estudos-app/google/`. Credenciais do cliente OAuth fornecidas pelo usuário; a tela explica como criá-las.
- **Planejar semana:** a partir das metas ativas e de horários livres que o usuário marca numa grade semanal, propor blocos de estudo (algoritmo guloso simples, sem IA). O usuário aprova e os eventos são criados num calendário "Estudos".
- Marcar um bloco como feito cria a `study_session` correspondente.
- **Aceite:** testes do algoritmo de distribuição de blocos. A integração real é testada à mão e documentada.

## M9 — Polimento e pacote

- Tema visual consistente (CSS JavaFX, modo escuro e claro), atalhos de teclado, ícone.
- Backup: exportar e importar o banco e o config num `.zip`.
- Pacote nativo com `jpackage` (`mvn package` + script `scripts/empacotar.sh`).
- **Aceite:** instalador gerado; o app instalado abre e usa o mesmo banco.

---

# Pendências (P1–P3)

Correções das pendências registradas em `docs/PROGRESSO.md` depois do M9. Mesmo processo dos módulos: uma seção = uma sessão. O banco do usuário ainda não tem metas, livros nem cards, então mudanças de significado de colunas não precisam migrar dados.

## P1 — Metas: edição, período e fontes automáticas

- **Editar meta existente:** na tela Metas, editar título e alvo (validações iguais às da criação).
- **Sessão em andamento não se perde:** ao iniciar o cronômetro, gravar a `study_session` com `ended_at` nulo. Ao abrir o app, se houver sessão aberta, perguntar se quer retomar (continua contando a partir de `started_at`, descontando pausas não é necessário) ou encerrar agora / descartar. Sessões abertas não contam nas estatísticas.
- **Contador manual por período:** os +/− passam a gravar em uma tabela nova `goal_count_log(goal_id, delta, at)` (migration `V5__...`). O progresso de CONTAGEM soma só os deltas dentro do período atual da meta (semana começa na segunda), então zera a cada semana/mês. `goal.manual_count` deixa de ser usado para o progresso.
- **Fonte automática da meta:** a coluna `goal.auto_count_ac` passa a ser um código de fonte (documentar no domínio como enum `GoalSource`): `0` nenhuma, `1` ACs do runner (arquivos com primeiro AC no período, como hoje), `2` ACs do Codeforces (problemas distintos `contest_id+problem_index` cuja primeira submissão `OK` caiu no período, de `cf_submission`), `3` páginas lidas. Fontes 1 e 2 só para CONTAGEM em CP; fonte 3 para CONTAGEM em qualquer área. Uma meta tem uma fonte só (sem somar runner + Codeforces, para não contar o mesmo problema duas vezes). A UI troca a caixa "Contar ACs" por uma escolha da fonte, na criação e na edição.
- **Páginas lidas:** ao atualizar a página atual de um livro, gravar o avanço positivo em uma tabela nova `reading_log(book_id, pages, at)` (na mesma migration `V5`). A fonte 3 soma as páginas dos livros da mesma área da meta dentro do período. Voltar páginas não desconta.
- **Aceite:** testes de: edição com validação; recuperação de sessão aberta; contador manual zerando na virada da semana/mês (relógio injetável); cada fonte contando só dentro do período e só para as metas certas; avanço de página gravado e retrocesso ignorado.

## P2 — IA, aulas e Anki

- **Orçamento por tokens:** novo campo do config `dailyTokenBudget` (0 = sem limite). O orçamento estoura se passar do limite de chamadas **ou** de tokens (entrada + saída de hoje); a confirmação diz qual dos dois. Campo editável em Configurações e exibido em Uso de IA.
- **Timeout de IA configurável:** campo `aiTimeoutMinutes` no config (padrão 10; aulas continuam com o próprio timeout de 60 min, a menos que o config seja maior). Editável em Configurações.
- **Prompt grande:** o `ClaudeCliRunner` envia o prompt pelo stdin (`claude -p` sem o prompt como argumento lê o stdin; confirmado em `claude --help`). O `agy --print` exige o prompt como argumento: se o prompt passar de 100 KB, o `AgyCliRunner` falha antes de executar com mensagem clara sugerindo o Claude.
- **Aulas presas em GERANDO:** ao iniciar o app, aulas com status `GERANDO` viram `FALHOU` com log "app fechado durante a geração".
- **Uma aula por vez:** a geração de aulas é serializada (fila própria de 1 thread ou trava no `LessonService`); enquanto uma gera, a próxima espera e a UI mostra "na fila". Assim a detecção de arquivos por data não mistura duas aulas.
- **AnkiConnect configurável:** campo `ankiUrl` no config (padrão `http://localhost:8765`), editável em Configurações.
- **Editar card sincronizado:** salvar um card com `anki_note_id` chama `updateNoteFields` no AnkiConnect (atualiza frente/verso da nota). Se o Anki estiver fechado, o card é salvo localmente e marcado como pendente de sincronização (`synced_at` nulo); a próxima sincronização atualiza a nota existente em vez de criar outra.
- **Aceite:** testes de orçamento por tokens (os dois limites), stdin do Claude (runner com processo falso), limite do agy, recuperação de aulas `GERANDO`, serialização de duas gerações, e `updateNoteFields` com servidor HTTP falso (Anki aberto e fechado).

## P3 — Agenda e pacote

- **Grade de horários salva:** os horários livres marcados na grade semanal da Agenda são gravados (tabela nova via migration `V6__...` ou JSON em `~/.estudos-app/`) e recarregados ao abrir a tela.
- **JavaFX no module-path no pacote:** `scripts/empacotar.sh` separa os jars do JavaFX (`javafx-*-linux.jar`) e os passa por `--module-path`/`--add-modules javafx.controls` (via `--java-options` com `$APPDIR`), para sumir o aviso "Unsupported JavaFX configuration". O resto continua no classpath.
- **Atalho no menu:** script `scripts/instalar-atalho.sh` que cria `~/.local/share/applications/estudos.desktop` apontando para o app empacotado, com o ícone (substitui o instalador .deb/.rpm, que exige ferramentas ausentes no Arch).
- **Aceite:** teste de salvar/carregar a grade; o app empacotado abre sem o aviso do JavaFX e usa o mesmo banco; o `.desktop` gerado passa em `desktop-file-validate` se a ferramenta existir.

## Pendências que não serão feitas

- OCR de PDFs escaneados (fora do escopo; exigiria Tesseract).
- Baixar o enunciado do Codeforces pela URL (o site bloqueia acesso automatizado; a URL continua sendo repassada à IA).
- Metas de CONTAGEM no planejador da Agenda (não têm duração).
- Atalhos de teclado configuráveis e instalador .deb/.rpm.
- Separador de SQL do Migrator: fica a convenção (sem `;`/`--` em strings nem triggers nas migrations).
- Teste real do Google Calendar: depende das credenciais do usuário (passo a passo na tela Agenda).
