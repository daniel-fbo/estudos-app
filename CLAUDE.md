@AGENTS.md

## Papel do Claude Code

Você **implementa**. O Antigravity (`agy`) revisa seu trabalho depois; não se revise exaustivamente, deixe isso para ele.

- Siga o prompt da sessão (vem de `prompts/`). Escopo = um módulo.
- Use subagentes só para buscas amplas; o resultado deles deve voltar resumido.
- Ao receber um arquivo `REVISAO.md`: corrija só os itens marcados como **bloqueante** ou **importante**. Para cada um, uma linha em `PROGRESSO.md`. Depois apague `REVISAO.md` e faça commit.
