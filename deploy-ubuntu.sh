#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
LOG_DIR="$ROOT_DIR/logs"
ENV_FILE="$ROOT_DIR/.env"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
TURN_HOST="${TURN_HOST:-}"
TURN_USERNAME="${TURN_USERNAME:-}"
TURN_CREDENTIAL="${TURN_CREDENTIAL:-}"
TURN_REALM="${TURN_REALM:-}"
TURN_MIN_PORT="${TURN_MIN_PORT:-}"
TURN_MAX_PORT="${TURN_MAX_PORT:-}"

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
  install_package coturn

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

random_secret() {
  openssl rand -hex 18 2>/dev/null || date +%s%N
}

set_env_value() {
  local key="$1"
  local value="$2"

  if grep -q "^${key}=" "$ENV_FILE"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    echo "${key}=${value}" >> "$ENV_FILE"
  fi
}

get_env_value() {
  local key="$1"
  grep -m 1 "^${key}=" "$ENV_FILE" | cut -d= -f2- || true
}

prepare_env() {
  if [ ! -f "$ENV_FILE" ]; then
    cp "$ROOT_DIR/.env.example" "$ENV_FILE"
  fi

  if ! grep -q '^JWT_SECRET=' "$ENV_FILE"; then
    echo "JWT_SECRET=$(openssl rand -hex 32 2>/dev/null || date +%s%N)" >> "$ENV_FILE"
  fi

  TURN_HOST="${TURN_HOST:-$(get_env_value TURN_HOST)}"
  TURN_HOST="${TURN_HOST:-$(hostname -I 2>/dev/null | awk '{print $1}')}"
  TURN_USERNAME="${TURN_USERNAME:-$(get_env_value TURN_USERNAME)}"
  TURN_USERNAME="${TURN_USERNAME:-autohr}"
  TURN_CREDENTIAL="${TURN_CREDENTIAL:-$(get_env_value TURN_CREDENTIAL)}"
  TURN_REALM="${TURN_REALM:-$(get_env_value TURN_REALM)}"
  TURN_REALM="${TURN_REALM:-autohr.local}"
  TURN_MIN_PORT="${TURN_MIN_PORT:-$(get_env_value TURN_MIN_PORT)}"
  TURN_MIN_PORT="${TURN_MIN_PORT:-49160}"
  TURN_MAX_PORT="${TURN_MAX_PORT:-$(get_env_value TURN_MAX_PORT)}"
  TURN_MAX_PORT="${TURN_MAX_PORT:-49200}"

  if [ -z "$TURN_HOST" ]; then
    echo "TURN_HOST is empty. Set TURN_HOST to this server's public IP or domain."
    exit 1
  fi

  if [ -z "$TURN_CREDENTIAL" ]; then
    TURN_CREDENTIAL="$(random_secret)"
  fi

  set_env_value INTERVIEW_STUN_URLS "stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478"
  set_env_value INTERVIEW_TURN_URLS "turn:${TURN_HOST}:3478?transport=udp,turn:${TURN_HOST}:3478?transport=tcp"
  set_env_value INTERVIEW_TURN_USERNAME "$TURN_USERNAME"
  set_env_value INTERVIEW_TURN_CREDENTIAL "$TURN_CREDENTIAL"
  set_env_value TURN_HOST "$TURN_HOST"
  set_env_value TURN_USERNAME "$TURN_USERNAME"
  set_env_value TURN_CREDENTIAL "$TURN_CREDENTIAL"
  set_env_value TURN_REALM "$TURN_REALM"
  set_env_value TURN_MIN_PORT "$TURN_MIN_PORT"
  set_env_value TURN_MAX_PORT "$TURN_MAX_PORT"
}

configure_coturn() {
  local turn_service="coturn"

  echo "Configuring coturn on ${TURN_HOST}:3478 ..."
  sudo tee /etc/turnserver.conf >/dev/null <<EOF
listening-port=3478
fingerprint
lt-cred-mech
realm=${TURN_REALM}
server-name=${TURN_REALM}
user=${TURN_USERNAME}:${TURN_CREDENTIAL}
no-multicast-peers
no-cli
min-port=${TURN_MIN_PORT}
max-port=${TURN_MAX_PORT}
syslog
EOF

  if [ -f /etc/default/coturn ]; then
    if grep -q '^TURNSERVER_ENABLED=' /etc/default/coturn; then
      sudo sed -i 's/^TURNSERVER_ENABLED=.*/TURNSERVER_ENABLED=1/' /etc/default/coturn
    else
      echo 'TURNSERVER_ENABLED=1' | sudo tee -a /etc/default/coturn >/dev/null
    fi
  fi

  if ! systemctl list-unit-files coturn.service >/dev/null 2>&1 && systemctl list-unit-files turnserver.service >/dev/null 2>&1; then
    turn_service="turnserver"
  fi

  sudo systemctl enable "$turn_service"
  sudo systemctl restart "$turn_service"
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
  configure_coturn
  stop_existing_processes
  start_backend
  start_frontend

  echo "Auto HR System is starting."
  echo "Backend:  http://localhost:$BACKEND_PORT"
  echo "Frontend: http://localhost:$FRONTEND_PORT"
  echo "TURN:     turn:$TURN_HOST:3478 udp/tcp"
  echo "Firewall: allow tcp/udp 3478 and udp $TURN_MIN_PORT:$TURN_MAX_PORT"
  echo "Logs:     $LOG_DIR"
  echo "Stop:     kill \$(cat logs/backend.pid) \$(cat logs/frontend.pid)"
}

main "$@"
