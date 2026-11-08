#!/usr/bin/env bash
# Gera o app nativo com jpackage. Esta máquina (Arch) não tem rpmbuild/dpkg-deb,
# então o tipo é app-image: uma pasta com executável em target/jpackage/Estudos/bin/Estudos.
# O app usa ~/.estudos-app (mesmo banco e config do `mvn javafx:run`).
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
cd "$RAIZ"

APP_VERSION="0.1.0"
TARGET="$RAIZ/target"
STAGE="$TARGET/jpackage-input"
OUTPUT="$TARGET/jpackage"

echo "== Compilando (mvn package, sem testes)"
mvn -q package -DskipTests

MAIN_JAR="$(ls "$TARGET"/estudos-app-*.jar | head -n1)"
[ -f "$MAIN_JAR" ] || { echo "!! jar principal não encontrado em $TARGET" >&2; exit 1; }

# jpackage exige o jar principal dentro do --input, junto com as dependências.
rm -rf "$STAGE" "$OUTPUT"
mkdir -p "$STAGE"
# JavaFX vai para uma pasta própria (module-path); o resto fica no classpath.
mkdir -p "$STAGE/javafx"
for j in "$TARGET"/libs/*.jar; do
    case "$(basename "$j")" in
        javafx-*-linux.jar) cp "$j" "$STAGE/javafx/" ;;
        *) cp "$j" "$STAGE/" ;;
    esac
done
cp "$MAIN_JAR" "$STAGE"/
ls "$STAGE"/javafx/*.jar >/dev/null 2>&1 || { echo "!! jars javafx-*-linux.jar não encontrados em target/libs" >&2; exit 1; }

ICON_OPT=()
[ -f src/main/resources/icon.png ] && ICON_OPT=(--icon src/main/resources/icon.png)

echo "== Gerando app-image com jpackage"
jpackage \
    --type app-image \
    --name Estudos \
    --app-version "$APP_VERSION" \
    --input "$STAGE" \
    --main-jar "$(basename "$MAIN_JAR")" \
    --main-class br.estudos.Launcher \
    --dest "$OUTPUT" \
    --java-options "--module-path=\$APPDIR/javafx" \
    --java-options "--add-modules=javafx.controls" \
    --java-options "--enable-native-access=ALL-UNNAMED" \
    "${ICON_OPT[@]}"

echo "== Pronto: $OUTPUT/Estudos/bin/Estudos"
