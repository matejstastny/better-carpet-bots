#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PROPS="$ROOT/gradle.properties"
CHANGELOG="$ROOT/CHANGELOG.md"
EXT_FILE="$ROOT/src/main/java/matejstastny/bettercarpetbots/CarpetBotsExtension.java"

MC_VERSION=$(grep '^minecraft_version=' "$PROPS" | cut -d= -f2)
CURRENT_MOD_VERSION=$(grep '^mod_version=' "$PROPS" | cut -d= -f2)

echo "Minecraft version  : $MC_VERSION"
echo "Current mod version: $CURRENT_MOD_VERSION"
echo ""
read -rp "New mod version: " NEW_MOD_VERSION

if [[ -z "$NEW_MOD_VERSION" ]]; then
    echo "Aborted: no version entered."
    exit 1
fi

TAG="v${NEW_MOD_VERSION}+${MC_VERSION}"

# Extract the changelog section for a given version
extract_changelog() {
    local ver="$1"
    awk -v ver="$ver" '
        /^## \[/ { if (flag) exit; if (index($0, "## [" ver "]") == 1) flag=1; next }
        flag { print }
    ' "$CHANGELOG"
}

CHANGELOG_CONTENT=$(extract_changelog "$NEW_MOD_VERSION")

if [[ -z "$CHANGELOG_CONTENT" ]]; then
    echo ""
    echo "No ## [${NEW_MOD_VERSION}] section found in CHANGELOG.md."
    read -rp "Open CHANGELOG.md in \$EDITOR to add it now? [Y/n] " OPEN_EDITOR
    if [[ ! "$OPEN_EDITOR" =~ ^[Nn]$ ]]; then
        ${EDITOR:-nano} "$CHANGELOG"
        CHANGELOG_CONTENT=$(extract_changelog "$NEW_MOD_VERSION")
    fi
    if [[ -z "$CHANGELOG_CONTENT" ]]; then
        echo "No changelog section found. Aborting."
        exit 1
    fi
fi

echo ""
echo "Changelog for $TAG:"
echo "---"
echo "$CHANGELOG_CONTENT"
echo "---"
echo ""
echo "Will create tag: $TAG"
read -rp "Confirm? [y/N] " CONFIRM
[[ "$CONFIRM" =~ ^[Yy]$ ]] || { echo "Aborted."; exit 1; }

# Update gradle.properties
sed -i "s/^mod_version=.*/mod_version=${NEW_MOD_VERSION}/" "$PROPS"

# Update version() in CarpetBotsExtension.java
sed -i "s/return \"${CURRENT_MOD_VERSION}\";/return \"${NEW_MOD_VERSION}\";/" "$EXT_FILE"

echo ""
echo "Updated gradle.properties and CarpetBotsExtension.java"

cd "$ROOT"
git add "$PROPS" "$EXT_FILE" "$CHANGELOG"
git commit -m "chore: bump version to ${NEW_MOD_VERSION}"
git tag "$TAG"
git push origin HEAD
git push origin "$TAG"

echo ""
echo "Pushed tag $TAG — release CI is now running."
