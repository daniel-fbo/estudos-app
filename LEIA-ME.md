# Kit do Estudos App: como usar

Kit para o Claude Code e o Antigravity construírem o app inteiro, um módulo por vez, com revisão cruzada e economia de tokens embutida.

## O que tem aqui

| Arquivo | Para quê |
|---|---|
| `AGENTS.md` | Regras comuns aos dois agentes: stack, arquitetura, economia de tokens, definição de pronto |
| `CLAUDE.md` / `GEMINI.md` | Papéis: Claude implementa, Antigravity revisa e escreve testes |
| `docs/SPEC.md` | O que o app faz, módulo por módulo (M0–M9), com critérios de aceite |
| `docs/ARQUITETURA.md` | Pacotes, interfaces e o fluxo de uma chamada de IA |
| `docs/schema.sql` | Esquema do banco SQLite |
| `docs/PROGRESSO.md` | Memória entre sessões. Cada sessão lê só isso em vez de explorar o código |
| `prompts/` | Prompts de implementar, revisar e corrigir, e o prompt da sessão inicial |
| `scripts/construir.sh` | O pipeline: implementar → testar → revisar → corrigir → testar → merge |
| `scripts/agentes.env` | Flags e modelo de cada agente e de cada módulo |

## Passo a passo

1. **Prepare a pasta.**
   ```bash
   cd ~ && unzip ~/Downloads/estudos-app-kit.zip   # ajuste o caminho do zip
   mv estudos-app-kit estudos-app && cd estudos-app
   chmod +x scripts/*.sh
   git init -b main && git add -A && git commit -m "kit inicial"
   ```
2. **Skills no Claude Code.** O módulo de Aulas chama `aula_matematica` e `aula_programacao` pela CLI. Descompacte os dois .zip das skills em `~/.claude/skills/`.
3. **Sessão inicial (interativa, com você olhando).**
   ```bash
   claude --model opus
   ```
   Cole o conteúdo de `prompts/m0_inicial.md`. Ele confere as ferramentas, descobre as flags certas do `agy`, preenche `scripts/agentes.env`, constrói o esqueleto e publica no GitHub. É a única etapa em que você precisa acompanhar.
4. **Módulos, no automático.**
   ```bash
   scripts/construir.sh M1            # um por vez no começo
   scripts/construir.sh M2 M3 M4      # depois, vários em sequência
   scripts/construir.sh --uso         # quanto cada etapa gastou
   ```
   O script para sozinho se um teste falhar ou se o agente registrar bloqueio. Tudo fica na branch `modulo/Mx`. Para retomar depois de corrigir, rode o mesmo módulo de novo.
5. **Teste você mesmo** cada módulo com `mvn javafx:run` antes de seguir. Se algo estiver errado, abra `claude` e descreva o problema numa sessão curta.

## Ordem sugerida e custo relativo

| Módulo | Modelo | Peso | Observação |
|---|---|---|---|
| M0 Esqueleto | Opus | médio | interativo |
| M1 Metas | Sonnet | baixo | já fica útil todo dia |
| M2 Runner de código | Sonnet | médio | |
| M3 Núcleo de IA | Opus | médio | cache, orçamento, fila: a base da economia |
| M4 Aulas | Sonnet | baixo | depende do M3 |
| M5 Biblioteca | Sonnet | alto | Lucene + PDFBox |
| M6 Anki | Sonnet | baixo | |
| M7 Codeforces + dicas | Sonnet | baixo | |
| M8 Agenda | Sonnet | alto | OAuth do Google dá trabalho; deixe por último |
| M9 Polimento | Sonnet | médio | |

## Para economizar tokens

- **Um módulo por dia ou por janela de uso.** Assim você não bate no limite no meio de um módulo. Se bater, o script para e você roda de novo depois.
- **A revisão vai para o Antigravity** (cota do Google). Use `REVISOR="claude"` só se o `agy` não tiver modo não interativo.
- **`docs/PROGRESSO.md` curto** é o que impede cada sessão de reler o projeto inteiro. Se ele crescer demais, peça para resumir os módulos antigos.
- **Mudou de ideia sobre algo?** Edite a seção do módulo em `docs/SPEC.md` antes de rodar. Mudar a especificação custa muito menos do que corrigir código pronto.
- **Pode cortar módulos sem medo.** M8 e M9 são opcionais.

## Riscos (e o que segura cada um)

- **Modo autônomo** (`--dangerously-skip-permissions`): o agente roda comandos sem perguntar. Mitigação: só rode dentro de `~/estudos-app`, com tudo em branch e commitado; `git reset --hard main` desfaz um módulo ruim.
- **Agente "concluir" sem concluir:** os testes e os critérios de aceite da SPEC barram, e o revisor confere a SPEC.
- **Flags erradas:** o M0 confirma as flags com `--help` e grava em `agentes.env`. Se uma CLI mudar, ajuste só ali.
