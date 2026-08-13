#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
RELEASE_DIR="$ROOT_DIR/release"
PACKAGE_NAME="auto-hr-release"
PACKAGE_DIR="$RELEASE_DIR/$PACKAGE_NAME"

if [ -d "$PACKAGE_DIR" ]; then
  rm -rf "$PACKAGE_DIR"
fi
mkdir -p "$PACKAGE_DIR/backend" "$PACKAGE_DIR/uploads" "$PACKAGE_DIR/logs"

echo "Building backend tests..."
mvn -q -f "$BACKEND_DIR/pom.xml" test

echo "Building frontend..."
npm --prefix "$FRONTEND_DIR" ci
npm --prefix "$FRONTEND_DIR" run build

echo "Embedding frontend in the executable backend..."
rm -rf "$BACKEND_DIR/target/classes/static"
mkdir -p "$BACKEND_DIR/target/classes/static"
cp -R "$FRONTEND_DIR/dist/." "$BACKEND_DIR/target/classes/static/"
mvn -q -f "$BACKEND_DIR/pom.xml" -DskipTests package

JAR_FILE="$(find "$BACKEND_DIR/target" -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n 1)"
test -n "$JAR_FILE"
cp "$JAR_FILE" "$PACKAGE_DIR/backend/auto-hr.jar"
cp "$ROOT_DIR/.env.example" "$PACKAGE_DIR/.env.example"
cp "$ROOT_DIR/scripts/start-release.sh" "$PACKAGE_DIR/start.sh"
cp "$ROOT_DIR/scripts/auto-hr.service" "$PACKAGE_DIR/auto-hr.service"
chmod +x "$PACKAGE_DIR/start.sh"

(
  cd "$RELEASE_DIR"
  rm -f "$PACKAGE_NAME.zip"
  zip -qr "$PACKAGE_NAME.zip" "$PACKAGE_NAME"
)

echo "Release package created: $RELEASE_DIR/$PACKAGE_NAME.zip"
