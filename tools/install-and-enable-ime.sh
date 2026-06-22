#!/usr/bin/env bash
#
# install-and-enable-ime.sh
#
# Installs the Simple Keyboard APK on a connected device/emulator,
# enables it as an input method, and sets it as the active IME.
#
# Usage:
#   ./tools/install-and-enable-ime.sh          # debug APK
#   ./tools/install-and-enable-ime.sh release   # release APK (if built)
#
# Prerequisites:
#   - adb in PATH
#   - A device/emulator connected (adb devices)
#   - Root access (su) available on the device

set -euo pipefail

APP_ID="io.cuetime.cuetimekeyboard.inputmethod"
IME_COMPONENT="${APP_ID}/.latin.LatinIME"

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="${SCRIPT_DIR}/app/build/outputs/apk"

# Determine which APK to install
BUILD_TYPE="${1:-debug}"
if [[ "$BUILD_TYPE" == "release" ]]; then
    APK_PATH="${BUILD_DIR}/release/app-release.apk"
else
    APK_PATH="${BUILD_DIR}/debug/app-debug.apk"
fi

if [[ ! -f "$APK_PATH" ]]; then
    echo "❌ APK not found at: ${APK_PATH}"
    echo ""
    echo "   Build it first with:"
    echo "     cd ${SCRIPT_DIR} && ./gradlew assemble${BUILD_TYPE^}"
    exit 1
fi

echo "📱 Simple Keyboard — Install & Enable IME"
echo "═══════════════════════════════════════════"
echo "  APK:        ${APK_PATH}"
echo "  Package:    ${APP_ID}"
echo "  IME:        ${IME_COMPONENT}"
echo ""

# ── 1. Wait for device ──────────────────────────────────────────────
echo "⏳ Waiting for device..."
adb wait-for-device

# ── 2. Install APK ──────────────────────────────────────────────────
echo "📦 Installing APK..."
adb install -r -d "$APK_PATH" 2>&1 | tail -2 || {
    echo "⚠️  adb install failed — trying with root..."
    adb push "$APK_PATH" /data/local/tmp/tmp.apk
    adb shell su -c "pm install -r -t /data/local/tmp/tmp.apk && rm /data/local/tmp/tmp.apk" 2>&1 | tail -2
}
echo ""

# ── 3. Enable the IME ──────────────────────────────────────────────
echo "🔓 Enabling IME..."
adb shell ime enable "$IME_COMPONENT" 2>&1 || {
    # Fallback via settings put (requires root)
    echo "⚠️  'ime enable' failed — trying via root settings..."
    adb shell su -c "settings put secure enabled_input_methods \"\$(settings get secure enabled_input_methods):${IME_COMPONENT}\"" 2>&1
}

echo ""

# ── 4. Set as active IME ────────────────────────────────────────────
echo "🎯 Setting ${IME_COMPONENT} as active IME..."
adb shell ime set "$IME_COMPONENT" 2>&1 || {
    # Fallback via settings put (requires root)
    echo "⚠️  'ime set' failed — trying via root settings..."
    adb shell su -c "settings put secure default_input_method \"${IME_COMPONENT}\"" 2>&1
}

echo ""

# ── 5. Verify ───────────────────────────────────────────────────────
echo "🔍 Verifying..."
CURRENT_IME="$(adb shell settings get secure default_input_method 2>/dev/null || true)"
if [[ "$CURRENT_IME" == "$IME_COMPONENT" ]]; then
    echo "✅ Success! Simple Keyboard is now the active IME."
else
    echo "⚠️  Current default IME is: ${CURRENT_IME:-<unknown>}"
    echo "   You may need to select it manually in Settings → System → Languages & Input → Virtual Keyboard"
fi

echo ""
echo "📋 Enabled IMEs:"
adb shell ime list -s 2>/dev/null || adb shell su -c "ime list -s"
