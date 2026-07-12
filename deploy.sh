#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

LOCAL_MODS="$HOME/prism/Starlight/minecraft/mods"
REMOTE_HOST="trickfire@tfserver"
REMOTE_MODS="/home/trickfire/minecraft/mods"

echo "==> Building..."
cd "$ROOT"
./gradlew build --no-daemon -q

JAR=$(find build/libs -name "better-carpet-bots-*.jar" ! -name "*-dev.jar" ! -name "*-sources.jar" | head -1)
if [[ -z "$JAR" ]]; then
    echo "No jar found in build/libs."
    exit 1
fi
JAR_NAME=$(basename "$JAR")
echo "    $JAR_NAME"

echo ""
echo "==> Deploying locally (Prism: Starlight)..."
rm -f "$LOCAL_MODS"/better-carpet-bots-*.jar
cp "$JAR" "$LOCAL_MODS/$JAR_NAME"
echo "    $LOCAL_MODS/$JAR_NAME"

echo ""
echo "==> Deploying to $REMOTE_HOST..."
ssh "$REMOTE_HOST" "rm -f ${REMOTE_MODS}/better-carpet-bots-*.jar"
scp -q "$JAR" "$REMOTE_HOST:${REMOTE_MODS}/${JAR_NAME}"
echo "    $REMOTE_HOST:${REMOTE_MODS}/${JAR_NAME}"

echo ""
echo "Done."
