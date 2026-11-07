Este é o primeiro passo do Estudos App, numa sessão interativa comigo. Leia AGENTS.md, CLAUDE.md, docs/SPEC.md (só a seção M0 e o topo), docs/ARQUITETURA.md e docs/schema.sql.

1. **Ferramentas.** Confira `java -version` (precisa ser 21+), `mvn -v`, `git`, `gh auth status`, `gcc`/`g++`, `python3`, `claude --version` e `agy --version`. Liste o que falta, com o comando de instalação para o meu sistema, e espere eu instalar.
2. **Flags dos agentes.** Leia `claude --help` e `agy --help`.
   - Confirme que `--dangerously-skip-permissions`, `--output-format json` e `--model` existem no Claude, e os aliases de modelo aceitos.
   - Ache o modo não interativo do agy (um prompt, executa, sai) e a flag que aprova ações sem perguntar.
   - Atualize `scripts/agentes.env` (CLAUDE_FLAGS, AGY_CMD, nomes de modelo). Se o agy não tiver modo não interativo, ponha REVISOR="claude" e me avise.
   - Teste os dois com um prompt trivial ("responda só OK") e mostre o resultado.
3. **Implemente o M0** conforme a SPEC: projeto Maven, janela com navegação, Config, Database com migrations (V1 = docs/schema.sql), logging, tela Configurações.
4. Crie o `.gitignore` (target/, .revisao/, *.log, .idea/, .vscode/, REVISAO.md). Rode os testes e abra o app uma vez para conferir.
5. Registre o M0 em docs/PROGRESSO.md (inclua as flags confirmadas dos dois agentes), faça commit na `main` e publique com `gh repo create estudos-app --private --source=. --remote=origin --push`. Se o nome já existir, pergunte qual usar.
6. Termine com um resumo de 5 linhas e o comando para o próximo passo: `scripts/construir.sh M1`.
