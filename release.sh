#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Read current values from gradle.properties
PROPS="$ROOT/gradle.properties"
MC_VERSION=$(grep '^minecraft_version=' "$PROPS" | cut -d= -f2)
CURRENT_MOD_VERSION=$(grep '^mod_version=' "$PROPS" | cut -d= -f2)

echo "Minecraft version : $MC_VERSION"
echo "Current mod version: $CURRENT_MOD_VERSION"
echo ""
read -rp "New mod version: " NEW_MOD_VERSION

if [[ -z "$NEW_MOD_VERSION" ]]; then
	echo "Aborted: no version entered."
	exit 1
fi

TAG="v${NEW_MOD_VERSION}+${MC_VERSION}"
echo ""
echo "Will create tag: $TAG"
read -rp "Confirm? [y/N] " CONFIRM
[[ "$CONFIRM" =~ ^[Yy]$ ]] || {
	echo "Aborted."
	exit 1
}

# Update gradle.properties
sed -i "s/^mod_version=.*/mod_version=${NEW_MOD_VERSION}/" "$PROPS"

# Update version() in CarpetBotsExtension.java
EXT_FILE="$ROOT/src/main/java/matejstastny/bettercarpetbots/CarpetBotsExtension.java"
sed -i "s/return \"${CURRENT_MOD_VERSION}\";/return \"${NEW_MOD_VERSION}\";/" "$EXT_FILE"

echo ""
echo "Updated gradle.properties and CarpetBotsExtension.java"

# Commit and tag
cd "$ROOT"
git add "$PROPS" "$EXT_FILE"
git commit -m "chore: bump version to ${NEW_MOD_VERSION}"
git tag "$TAG"
git push origin HEAD
git push origin "$TAG"

echo ""
echo "Pushed tag $TAG - release CI is now running."
