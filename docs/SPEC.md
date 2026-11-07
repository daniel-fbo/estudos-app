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
