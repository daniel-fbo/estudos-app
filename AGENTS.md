# AGENTS.md — Estudos App

Regras para qualquer agente (Claude Code, Antigravity) que trabalhe neste repositório. Leia este arquivo inteiro; é curto de propósito.

## O projeto

App desktop pessoal (um único usuário: Filipe, estudante de Engenharia de Software) que unifica o estudo de matemática, programação competitiva, xadrez e música. Ele coordena; o trabalho de IA é feito chamando as CLIs `claude` e `agy`. Detalhes em `docs/SPEC.md`. Arquitetura em `docs/ARQUITETURA.md`.

## Stack (não troque sem pedir)

Java 21 · JavaFX 21 · Maven · SQLite (`org.xerial:sqlite-jdbc`) · Jackson · Apache Lucene · Apache PDFBox · JUnit 5 · SLF4J + Logback. Sem Spring, sem ORM: JDBC simples com repositórios.

## Como trabalhar (economia de tokens é requisito)

1. **Comece lendo só:** este arquivo, `docs/PROGRESSO.md` e a seção do seu módulo em `docs/SPEC.md`. Não explore o repositório inteiro. Para achar algo, use busca (`grep`/glob) e abra só o trecho necessário.
2. **Um módulo por sessão.** Faça o que o prompt pede e pare. Não adiante módulos futuros nem refatore o que não pertence ao módulo.
3. **Deixe a máquina verificar.** Rode `mvn -q -o test` (ou sem `-o` na primeira vez). Leia só as falhas. Não releia arquivos que você acabou de escrever.
4. **Saída curta.** Sem explicações longas no terminal; o registro fica em `docs/PROGRESSO.md`.
5. **Ao terminar o módulo:**
   - todos os testes passam;
   - `docs/PROGRESSO.md` ganha no máximo 12 linhas: o que foi feito, interfaces públicas novas, pendências;
   - commit `M<n>: <resumo>` na branch do módulo.
6. **Se a sessão ficar longa** ou você travar duas vezes no mesmo erro: commit do que funciona, escreva o bloqueio em `PROGRESSO.md` e pare. Uma sessão nova continua melhor do que uma sessão inchada.
7. **Flags de CLI externas** (`claude`, `agy`, `g++`, `anki`): confirme com `--help` antes de usar. Nunca chute uma flag.

## Código

- Pacotes por camada: `ui`, `app` (serviços/casos de uso), `domain` (modelos puros), `infra` (banco, processos, HTTP, arquivos). `ui` depende de `app`; `app` de `domain` e de interfaces; `infra` implementa as interfaces.
- Toda chamada externa (processo, HTTP, IA) passa por uma interface em `app` com implementação em `infra` e um *fake* para teste.
- Nada de operação longa na thread do JavaFX: use `Task`/`ExecutorService` e atualize a UI com `Platform.runLater`.
- Migrations SQL numeradas em `src/main/resources/db/V<n>__<nome>.sql`, aplicadas na inicialização. Nunca edite uma migration já commitada; crie outra.
- Testes: unidade para serviços e repositórios (SQLite em arquivo temporário). UI sem testes automatizados; mantenha os controllers finos.
- Textos da interface em português do Brasil. Código, nomes e commits podem ser em inglês ou português, mas seja consistente dentro do arquivo.
- Segredos (tokens OAuth etc.) só em `~/.estudos-app/`, nunca no repositório.

## Definição de pronto de um módulo

Compila, testes passam, critérios de aceitação da SPEC atendidos, `PROGRESSO.md` atualizado, commit feito. O app abre (`mvn javafx:run`) sem exceção no log.
