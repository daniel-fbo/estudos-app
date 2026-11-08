#!/usr/bin/env bash
# Cria ~/.local/share/applications/estudos.desktop apontando para o app empacotado
# (rode scripts/empacotar.sh antes).
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
EXEC="$RAIZ/target/jpackage/Estudos/bin/Estudos"
ICONE="$RAIZ/src/main/resources/icon.png"
DEST="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
ARQ="$DEST/estudos.desktop"

[ -x "$EXEC" ] || { echo "!! App não encontrado em $EXEC. Rode scripts/empacotar.sh primeiro." >&2; exit 1; }

mkdir -p "$DEST"
cat > "$ARQ" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Estudos
Comment=Matemática, programação competitiva, xadrez e música
Exec="$EXEC"
Icon=$ICONE
Terminal=false
Categories=Education;
DESKTOP
chmod +x "$ARQ"

if command -v desktop-file-validate >/dev/null 2>&1; then
    desktop-file-validate "$ARQ" && echo "== .desktop válido"
fi
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$DEST" || true
echo "== Atalho criado: $ARQ"
