#!/usr/bin/env bash
# Constrói módulos do Estudos App com dois agentes:
#   Claude implementa -> testes -> Antigravity revisa -> Claude corrige -> testes -> merge.
#
# Uso:  scripts/construir.sh M1 [M2 M3 ...]
#       scripts/construir.sh --uso          # resumo de tokens/custo das execuções
#
# Para no primeiro problema (teste falhando, módulo bloqueado, agente com erro),
# deixando tudo na branch do módulo para você olhar.
set -euo pipefail

RAIZ="$(git rev-parse --show-toplevel)"
cd "$RAIZ"
# shellcheck source=/dev/null
source scripts/agentes.env
LOGS=".revisao/logs"
mkdir -p "$LOGS"

msg()  { printf '\n\033[1;35m== %s\033[0m\n' "$*"; }
erro() { printf '\n\033[1;31m!! %s\033[0m\n' "$*" >&2; exit 1; }

prompt() {  # prompt <arquivo> <modulo>
  sed "s/{{M}}/$2/g" "prompts/$1.md"
}

modelo_de() {
  local var="MODELO_$1"
  echo "${!var:-$MODELO_PADRAO}"
}

claude_run() {  # claude_run <modelo> <log> <prompt>
  local modelo="$1" log="$2" texto="$3"
  # shellcheck disable=SC2086
  if ! "$CLAUDE_BIN" -p "$texto" --model "$modelo" $CLAUDE_FLAGS > "$log" 2> "$log.err"; then
    erro "Claude falhou. Veja $log e $log.err"
  fi
  python3 - "$log" <<'PY' || true
import json, sys
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    sys.exit(0)
u = d.get("usage", {}) or {}
print(f"   turnos={d.get('num_turns','?')} in={u.get('input_tokens','?')} "
      f"out={u.get('output_tokens','?')} cache_read={u.get('cache_read_input_tokens','?')} "
      f"custo_est=${d.get('total_cost_usd','?')}")
if d.get("is_error"):
    sys.exit(1)
PY
}

revisar() {  # revisar <modulo>
  local m="$1" texto
  texto="$(prompt revisar "$m")"
  case "$REVISOR" in
    agy)
      [ -n "$AGY_CMD" ] || erro "AGY_CMD vazio em scripts/agentes.env. Preencha ou use REVISOR=claude."
      # shellcheck disable=SC2086
      $AGY_CMD "$texto" > "$LOGS/$m-revisar.log" 2>&1 || erro "agy falhou. Veja $LOGS/$m-revisar.log"
      ;;
    claude)
      claude_run "$MODELO_REVISOR_CLAUDE" "$LOGS/$m-revisar.json" \
        "Siga GEMINI.md (você assume o papel de revisor). $texto"
      ;;
    nenhum) echo "APROVADO" > REVISAO.md ;;
    *) erro "REVISOR desconhecido: $REVISOR" ;;
  esac
  [ -f REVISAO.md ] || erro "O revisor não gerou REVISAO.md"
}

testes() {
  msg "Testes"
  $TESTE_CMD || erro "Testes falharam na branch $(git branch --show-current)."
}

if [ "${1:-}" = "--uso" ]; then
  python3 - "$LOGS" <<'PY'
import json, glob, os, sys
tot_in = tot_out = 0; custo = 0.0
for f in sorted(glob.glob(os.path.join(sys.argv[1], "*.json"))):
    try: d = json.load(open(f))
    except Exception: continue
    u = d.get("usage", {}) or {}
    i, o, c = u.get("input_tokens", 0) or 0, u.get("output_tokens", 0) or 0, d.get("total_cost_usd", 0) or 0
    tot_in += i; tot_out += o; custo += c
    print(f"{os.path.basename(f):28s} in={i:>9} out={o:>8} ${c:.2f}")
print(f"{'TOTAL':28s} in={tot_in:>9} out={tot_out:>8} ${custo:.2f}  (custo estimado; na assinatura conta como uso do limite)")
PY
  exit 0
fi

[ $# -ge 1 ] || erro "Uso: scripts/construir.sh M1 [M2 ...]"
[ -z "$(git status --porcelain)" ] || erro "Há mudanças não commitadas. Faça commit ou stash antes."

for M in "$@"; do
  msg "Módulo $M"
  git checkout -q main
  git checkout -q -B "modulo/$M"

  msg "$M: implementação (Claude, $(modelo_de "$M"))"
  claude_run "$(modelo_de "$M")" "$LOGS/$M-implementar.json" "$(prompt implementar "$M")"
  grep -qE "^## $M .*bloqueado" docs/PROGRESSO.md && erro "$M ficou bloqueado. Leia docs/PROGRESSO.md."
  testes

  msg "$M: revisão ($REVISOR)"
  git diff main...HEAD > ".revisao/$M.diff"
  revisar "$M"

  if head -n1 REVISAO.md | grep -q "^APROVADO"; then
    rm -f REVISAO.md
    msg "$M: aprovado na primeira revisão"
  else
    msg "$M: correções (Claude, $MODELO_CORRECAO)"
    claude_run "$MODELO_CORRECAO" "$LOGS/$M-corrigir.json" "$(prompt corrigir "$M")"
    rm -f REVISAO.md
  fi
  testes
  git add -A && git commit -q -m "$M: testes da revisão" >/dev/null 2>&1 || true

  msg "$M: merge na main"
  git checkout -q main
  git merge -q --no-ff "modulo/$M" -m "Merge $M"
  git push -q 2>/dev/null || echo "   (push não feito; sem remoto ou sem login)"
done

msg "Concluído: $*"
"$0" --uso
