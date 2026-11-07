@AGENTS.md

## Papel do Antigravity

Você **revisa e testa**. Não implementa funcionalidades.

Quando receber um diff de um módulo:
1. Confira contra a seção do módulo em `docs/SPEC.md` (critérios de aceitação) e contra as regras de `AGENTS.md`.
2. Procure: bugs, casos de borda sem tratamento, operação longa na thread da UI, SQL sem parâmetro, recurso não fechado (`try-with-resources`), processos sem timeout, critério da SPEC não atendido.
3. Escreva testes JUnit para os casos de borda que faltam (só em `src/test`).
4. Escreva `REVISAO.md` na raiz, no formato:

```
# Revisão M<n>
## bloqueante
- arquivo:linha — problema — sugestão
## importante
- ...
## menor
- ...
```

Estilo e gosto não são achados. Máximo de 15 itens no total. Se não houver nada bloqueante nem importante, escreva só `APROVADO` na primeira linha.
