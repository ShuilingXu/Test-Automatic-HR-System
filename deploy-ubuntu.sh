#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOG_DIR="$ROOT_DIR/logs"
ENV_FILE="$ROOT_DIR/.env"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"

install_package() {
  local package_name="$1"
  if ! dpkg -s "$package_name" >/dev/null 2>&1; then
    sudo apt-get install -y "$package_name"
  fi
}

ensure_dependencies() {
  if ! command -v sudo >/dev/null 2>&1; then
    echo "sudo is required to install missing dependencies."
    exit 1
  fi

  sudo apt-get update
  install_package openjdk-17-jdk
  install_package maven

  local node_major=0
  if command -v node >/dev/null 2>&1; then
    node_major="$(node -v | sed 's/^v//' | cut -d. -f1)"
  fi

  if [ "$node_major" -lt 18 ]; then
    sudo apt-get install -y ca-certificates curl gnupg
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y nodejs
  fi
}

prepare_env() {
  if [ ! -f "$ENV_FILE" ]; then
    cp "$ROOT_DIR/.env.example" "$ENV_FILE"
  fi

  if ! grep -q '^JWT_SECRET=' "$ENV_FILE"; then
    echo "JWT_SECRET=$(openssl rand -hex 32 2>/dev/null || date +%s%N)" >> "$ENV_FILE"
  fi
}

stop_existing_processes() {
  if command -v lsof >/dev/null 2>&1; then
    lsof -ti tcp:"$BACKEND_PORT" | xargs -r kill || true
    lsof -ti tcp:"$FRONTEND_PORT" | xargs -r kill || true
  fi
}

start_backend() {
  echo "Building backend..."
  (cd "$BACKEND_DIR" && mvn -q -DskipTests package)

  local jar_file
  jar_file="$(find "$BACKEND_DIR/target" -maxdepth 1 -name '*.jar' ! -name '*.original' | head -n 1)"
  if [ -z "$jar_file" ]; then
    echo "Backend jar was not found."
    exit 1
  fi

  echo "Starting backend on http://0.0.0.0:$BACKEND_PORT ..."
  nohup java -jar "$jar_file" --server.port="$BACKEND_PORT" > "$LOG_DIR/backend.log" 2>&1 &
  echo $! > "$LOG_DIR/backend.pid"
}

start_frontend() {
  echo "Installing frontend dependencies..."
  (cd "$FRONTEND_DIR" && npm install)

  echo "Starting frontend on http://0.0.0.0:$FRONTEND_PORT ..."
  nohup npm --prefix "$FRONTEND_DIR" run dev -- --host 0.0.0.0 --port "$FRONTEND_PORT" > "$LOG_DIR/frontend.log" 2>&1 &
  echo $! > "$LOG_DIR/frontend.pid"
}

main() {
  mkdir -p "$LOG_DIR"
  ensure_dependencies
  prepare_env
  stop_existing_processes
  start_backend
  start_frontend

  echo "Auto HR System is starting."
  echo "Backend:  http://localhost:$BACKEND_PORT"
  echo "Frontend: http://localhost:$FRONTEND_PORT"
  echo "Logs:     $LOG_DIR"
  echo "Stop:     kill \$(cat logs/backend.pid) \$(cat logs/frontend.pid)"
}

main "$@"
