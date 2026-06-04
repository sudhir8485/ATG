#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# ATG Linux installer builder → produces a .deb package
# Requirements: JDK 17+, fakeroot, dpkg (standard on Ubuntu/Debian)
# Usage: ./build-linux.sh
# ─────────────────────────────────────────────────────────────────────────────
set -e

APP_NAME="ATG"
APP_VERSION="1.0.0"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_NAME="AutomatedTimetable-${APP_VERSION}.jar"
JAR_PATH="$SCRIPT_DIR/target/$JAR_NAME"
ICON="$SCRIPT_DIR/src/main/jpackage/atg-icon.png"
OUTPUT="$SCRIPT_DIR/installer-output"

# ── Check prerequisites ──────────────────────────────────────────────────────
if ! command -v jpackage &>/dev/null; then
    echo "ERROR: jpackage not found. Make sure JDK 17+ bin is on your PATH."
    echo "       Try: export PATH=\$JAVA_HOME/bin:\$PATH"
    exit 1
fi
if ! command -v dpkg &>/dev/null; then
    echo "ERROR: dpkg not found. Install with: sudo apt-get install dpkg"
    exit 1
fi

echo "══════════════════════════════════════════════════"
echo " ATG Installer Build  —  Linux (.deb)"
echo "══════════════════════════════════════════════════"

# ── Step 1: Build fat JAR ────────────────────────────────────────────────────
echo ""
echo "Step 1/2 — Building Spring Boot fat JAR..."
cd "$SCRIPT_DIR"
./mvnw clean package -DskipTests -q
echo "         JAR: $JAR_PATH"

if [ ! -f "$JAR_PATH" ]; then
    echo "ERROR: JAR not found. Check Maven output above."
    exit 1
fi

# ── Step 2: Package with jpackage ────────────────────────────────────────────
echo ""
echo "Step 2/2 — Running jpackage..."
mkdir -p "$OUTPUT"

jpackage \
  --type deb \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --input "$SCRIPT_DIR/target" \
  --main-jar "$JAR_NAME" \
  --icon "$ICON" \
  --dest "$OUTPUT" \
  --vendor "APCOER IT Dept" \
  --description "Automated Timetable Generator — APCOER Information Technology Department" \
  --copyright "2025 APCOER" \
  --linux-deb-maintainer "sudhirdhawade05@gmail.com" \
  --linux-app-category "Education" \
  --linux-shortcut \
  --java-options "-Dspring.profiles.active=prod" \
  --java-options "-Xverify:none" \
  --java-options "-XX:TieredStopAtLevel=1" \
  --java-options "-Xshare:off" \
  --java-options "-Xmx512m" \
  --java-options "-Djava.awt.headless=false"

echo ""
echo "══════════════════════════════════════════════════"
echo " Build complete!"
echo " Package location:"
ls -lh "$OUTPUT/"*.deb 2>/dev/null || ls "$OUTPUT/"
echo "══════════════════════════════════════════════════"
echo ""
echo "Install on target machine:"
echo "  sudo dpkg -i $OUTPUT/atg_${APP_VERSION}_amd64.deb"
echo "  ATG                    (launch from terminal or desktop shortcut)"
