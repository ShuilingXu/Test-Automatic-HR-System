#!/usr/bin/env bash
set -euo pipefail

# Unified Ubuntu deployment entry point.
#
# From a source checkout it builds the release package first. From an extracted
# release it installs or upgrades the systemd service directly. The same file is
# copied into GitHub release archives as deploy.sh.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_PATH="$SCRIPT_DIR/$(basename "${BASH_SOURCE[0]}")"
ORIGINAL_ARGS=("$@")

INSTALL_DIR="${AUTO_HR_INSTALL_DIR:-/opt/auto-hr}"
SERVICE_NAME="auto-hr"
SERVICE_USER="autohr"
SERVICE_GROUP="autohr"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
SERVICE_ADDRESS="${AUTO_HR_SERVER_ADDRESS:-${SERVER_ADDRESS:-127.0.0.1}}"
SERVICE_PORT="${AUTO_HR_SERVER_PORT:-${SERVER_PORT:-8081}}"
SERVICE_PROFILE="${AUTO_HR_SPRING_PROFILE:-${SPRING_PROFILES_ACTIVE:-prod}}"
HEALTH_URL_OVERRIDE="${AUTO_HR_HEALTH_URL:-}"
HEALTH_URL=""

ENV_SOURCE=""
WEB_ROOT=""
INSTALL_DEPENDENCIES=true
CONFIGURE_COTURN=true
RUN_SERVICE=false
RELEASE_ROOT=""
ENV_FILE="$INSTALL_DIR/.env"
ENV_BACKUP="$INSTALL_DIR/.env.previous"
ENV_EXISTED=false
SERVICE_WAS_ACTIVE=false
SERVICE_WAS_ENABLED=false
DEPLOY_SUCCEEDED=false
FRONTEND_ACTIVATED=false
ENV_TRANSACTION_STARTED=false
DEPLOYMENT_STARTED=false
ROLLBACK_DONE=false
HAD_CURRENT_JAR=false
HAD_CURRENT_DEPLOY_SCRIPT=false
HAD_CURRENT_SERVICE=false

log() {
  printf '[auto-hr] %s\n' "$*"
}

die() {
  printf '[auto-hr] ERROR: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  bash deploy-ubuntu.sh [options]   Build from source and deploy
  sudo ./deploy.sh [options]        Deploy an extracted release package

Options:
  --install-dir DIR      Install under DIR (default: /opt/auto-hr).
  --env FILE             Install FILE as the service environment file. Without
                         this option, an existing environment is preserved; a
                         first install is initialized with random secrets.
  --web-root DIR         Publish frontend files to an existing
                         OpenResty/Nginx site root after the backend is healthy.
  --server-address ADDR  Bind the backend to ADDR (default: 127.0.0.1).
  --server-port PORT     Bind the backend to PORT (default: 8081).
  --spring-profile NAME  Spring profile for the service (default: prod).
  --skip-dependencies    Do not install missing Ubuntu runtime/build packages.
  --skip-coturn          Do not install or configure the local coturn service.
  -h, --help             Show this help.

The service listens on the configured address and port (127.0.0.1:8081 by
default). Terminate TLS and proxy /api with OpenResty/Nginx for public
deployments. Re-running the command performs an in-place upgrade and rolls
back the application files if health checks fail.
EOF
}

run_service() {
  local -a java_options=()
  if [ -n "${JAVA_OPTS:-}" ]; then
    read -r -a java_options <<<"$JAVA_OPTS"
  fi

  cd "$INSTALL_DIR"
  mkdir -p uploads logs
  exec java "${java_options[@]}" -jar "$INSTALL_DIR/backend/auto-hr.jar" \
    --spring.profiles.active="${SPRING_PROFILES_ACTIVE:-prod}" \
    --server.address="${SERVER_ADDRESS:-127.0.0.1}" \
    --server.port="${SERVER_PORT:-8081}"
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --install-dir)
      [ "$#" -ge 2 ] || die "--install-dir requires a directory path."
      INSTALL_DIR="$2"
      shift 2
      ;;
    --install-dir=*)
      INSTALL_DIR="${1#*=}"
      shift
      ;;
    --env)
      [ "$#" -ge 2 ] || die "--env requires a file path."
      ENV_SOURCE="$2"
      shift 2
      ;;
    --env=*)
      ENV_SOURCE="${1#*=}"
      shift
      ;;
    --web-root)
      [ "$#" -ge 2 ] || die "--web-root requires a directory path."
      WEB_ROOT="$2"
      shift 2
      ;;
    --web-root=*)
      WEB_ROOT="${1#*=}"
      shift
      ;;
    --server-address)
      [ "$#" -ge 2 ] || die "--server-address requires an address."
      SERVICE_ADDRESS="$2"
      shift 2
      ;;
    --server-address=*)
      SERVICE_ADDRESS="${1#*=}"
      shift
      ;;
    --server-port)
      [ "$#" -ge 2 ] || die "--server-port requires a port."
      SERVICE_PORT="$2"
      shift 2
      ;;
    --server-port=*)
      SERVICE_PORT="${1#*=}"
      shift
      ;;
    --spring-profile)
      [ "$#" -ge 2 ] || die "--spring-profile requires a profile name."
      SERVICE_PROFILE="$2"
      shift 2
      ;;
    --spring-profile=*)
      SERVICE_PROFILE="${1#*=}"
      shift
      ;;
    --skip-dependencies)
      INSTALL_DEPENDENCIES=false
      shift
      ;;
    --skip-coturn)
      CONFIGURE_COTURN=false
      shift
      ;;
    --run-service)
      RUN_SERVICE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1"
      ;;
  esac
done

if [ "$RUN_SERVICE" = true ]; then
  export AUTO_HR_INSTALL_DIR="$INSTALL_DIR"
  export SERVER_ADDRESS="${SERVER_ADDRESS:-$SERVICE_ADDRESS}"
  export SERVER_PORT="${SERVER_PORT:-$SERVICE_PORT}"
  export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-$SERVICE_PROFILE}"
  run_service
fi

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

run_privileged() {
  if [ "${EUID:-$(id -u)}" -eq 0 ]; then
    "$@"
  else
    command_exists sudo || die "sudo is required to install missing dependencies."
    sudo "$@"
  fi
}

install_apt_packages() {
  [ "$INSTALL_DEPENDENCIES" = true ] || return 0
  command_exists apt-get || die "This deployment script supports Ubuntu/Debian apt systems only."

  local -a missing=()
  local package_name
  for package_name in "$@"; do
    if ! dpkg-query -W -f='${Status}' "$package_name" 2>/dev/null | grep -Fq 'install ok installed'; then
      missing+=("$package_name")
    fi
  done

  if [ "${#missing[@]}" -gt 0 ]; then
    log "Installing packages: ${missing[*]}"
    run_privileged apt-get update
    run_privileged env DEBIAN_FRONTEND=noninteractive apt-get install -y "${missing[@]}"
  fi
}

ensure_source_dependencies() {
  install_apt_packages ca-certificates curl openjdk-17-jdk maven nodejs npm python3 zip

  for command_name in java mvn node npm python3; do
    command_exists "$command_name" || die "Required build command is missing: $command_name"
  done

  local node_major
  node_major="$(node --version | sed 's/^v//' | cut -d. -f1)"
  if [ "$node_major" -lt 18 ]; then
    [ "$INSTALL_DEPENDENCIES" = true ] || die "Node.js 18 or newer is required."
    log "Installing Node.js 20 because the current Node.js version is too old."
    run_privileged env DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl gnupg
    curl -fsSL https://deb.nodesource.com/setup_20.x | run_privileged bash -
    run_privileged env DEBIAN_FRONTEND=noninteractive apt-get install -y nodejs
  fi
}

is_source_tree() {
  [ -f "$SCRIPT_DIR/backend/pom.xml" ] && [ -f "$SCRIPT_DIR/frontend/package.json" ] \
    && [ -f "$SCRIPT_DIR/scripts/package-release.sh" ]
}

is_release_tree() {
  [ -s "$SCRIPT_DIR/backend/auto-hr.jar" ] && [ -f "$SCRIPT_DIR/frontend/index.html" ] \
    && [ -f "$SCRIPT_DIR/.env.example" ]
}

if is_source_tree; then
  ensure_source_dependencies
  log "Building the tested release package from source."
  bash "$SCRIPT_DIR/scripts/package-release.sh"
  packaged_script="$SCRIPT_DIR/release/auto-hr-release/deploy.sh"
  [ -s "$packaged_script" ] || die "The release build did not create $packaged_script"
  exec bash "$packaged_script" "${ORIGINAL_ARGS[@]}"
elif is_release_tree; then
  RELEASE_ROOT="$SCRIPT_DIR"
else
  die "Run this script from the repository root or an extracted auto-hr-release directory."
fi

if [ -n "$ENV_SOURCE" ]; then
  [ -s "$ENV_SOURCE" ] || die "Environment file is missing or empty: $ENV_SOURCE"
  ENV_SOURCE="$(readlink -f "$ENV_SOURCE")"
fi

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  command_exists sudo || die "Run this release deployment as root."
  log "Elevating privileges for the system installation."
  exec sudo env "AUTO_HR_INSTALL_DIR=$INSTALL_DIR" "AUTO_HR_SERVER_ADDRESS=$SERVICE_ADDRESS" \
    "AUTO_HR_SERVER_PORT=$SERVICE_PORT" "AUTO_HR_SPRING_PROFILE=$SERVICE_PROFILE" \
    "AUTO_HR_HEALTH_URL=$HEALTH_URL_OVERRIDE" \
    bash "$SCRIPT_PATH" "${ORIGINAL_ARGS[@]}"
fi

validate_service_settings() {
  [[ "$SERVICE_ADDRESS" =~ ^[A-Za-z0-9._:-]+$ ]] \
    || die "--server-address must be a hostname or IP address without whitespace."
  [[ "$SERVICE_PORT" =~ ^[0-9]+$ ]] \
    || die "--server-port must be a number between 1 and 65535."
  ((10#$SERVICE_PORT >= 1 && 10#$SERVICE_PORT <= 65535)) \
    || die "--server-port must be a number between 1 and 65535."
  [[ "$SERVICE_PROFILE" =~ ^[A-Za-z0-9][A-Za-z0-9._,-]*$ ]] \
    || die "--spring-profile contains unsupported characters."

  if [ -n "$HEALTH_URL_OVERRIDE" ]; then
    [[ "$HEALTH_URL_OVERRIDE" != *$'\r'* && "$HEALTH_URL_OVERRIDE" != *$'\n'* ]] \
      || die "AUTO_HR_HEALTH_URL cannot contain CR or LF."
    HEALTH_URL="$HEALTH_URL_OVERRIDE"
    return 0
  fi

  local health_host="$SERVICE_ADDRESS"
  case "$health_host" in
    0.0.0.0) health_host="127.0.0.1" ;;
    ::) health_host="::1" ;;
  esac
  if [[ "$health_host" == *:* ]]; then
    health_host="[$health_host]"
  fi
  HEALTH_URL="http://${health_host}:${SERVICE_PORT}/api/auth/captcha"
}

validate_install_dir() {
  INSTALL_DIR="$(readlink -m "$INSTALL_DIR")"
  [[ "$INSTALL_DIR" =~ ^/opt/[A-Za-z0-9._/-]+$ ]] \
    || die "--install-dir must be below /opt and contain only letters, numbers, '.', '_', '-', and '/'."
  case "$INSTALL_DIR" in
    /opt/*)
      ;;
    *)
      die "AUTO_HR_INSTALL_DIR must be an absolute directory below /opt."
      ;;
  esac
  [ "$INSTALL_DIR" != "/opt" ] || die "AUTO_HR_INSTALL_DIR cannot be /opt."
  ENV_FILE="$INSTALL_DIR/.env"
  ENV_BACKUP="$INSTALL_DIR/.env.previous"
}

validate_web_root() {
  [ -n "$WEB_ROOT" ] || return 0
  case "$WEB_ROOT" in
    /*)
      ;;
    *)
      die "--web-root must be an absolute path."
      ;;
  esac

  WEB_ROOT="$(readlink -m "$WEB_ROOT")"
  case "$WEB_ROOT" in
    /var/www/*)
      ;;
    /|/bin|/bin/*|/boot|/boot/*|/dev|/dev/*|/etc|/etc/*|/lib|/lib/*|/lib64|/lib64/*|/proc|/proc/*|/root|/root/*|/run|/run/*|/sbin|/sbin/*|/sys|/sys/*|/tmp|/tmp/*|/usr|/usr/*|/var|/var/*)
      die "--web-root must point to a dedicated site directory, not a system directory: $WEB_ROOT."
      ;;
  esac
  case "$WEB_ROOT" in
    "$INSTALL_DIR"|"$INSTALL_DIR"/*)
      die "--web-root must be outside $INSTALL_DIR."
      ;;
  esac
}

ensure_runtime_dependencies() {
  local -a packages=(
    ca-certificates curl openjdk-17-jre-headless openssl python3
    ffmpeg redis-tools postgresql-client
    tesseract-ocr tesseract-ocr-eng tesseract-ocr-chi-sim
  )
  if [ "$CONFIGURE_COTURN" = true ]; then
    packages+=(coturn)
  fi
  install_apt_packages "${packages[@]}"

  for command_name in curl java openssl python3 systemctl; do
    command_exists "$command_name" || die "Required runtime command is missing: $command_name"
  done

  local java_major
  java_major="$(java -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
  [ -n "$java_major" ] && [ "$java_major" -ge 17 ] \
    || die "Java 17 or newer is required."
}

random_secret() {
  local value
  value="$(openssl rand -hex 32 2>/dev/null || true)"
  [ -n "$value" ] || die "OpenSSL could not generate a random secret."
  printf '%s\n' "$value"
}

get_env_value() {
  local key="$1"
  local raw_value
  raw_value="$(grep -m 1 "^${key}=" "$ENV_FILE" 2>/dev/null | cut -d= -f2- || true)"
  decode_properties_value "$raw_value"
}

decode_properties_value() {
  PROPERTIES_VALUE="$1" python3 - <<'PY'
import os

value = os.environ["PROPERTIES_VALUE"]
decoded = []
index = 0
escapes = {"t": "\t", "n": "\n", "r": "\r", "f": "\f"}
while index < len(value):
    character = value[index]
    if character != "\\" or index + 1 >= len(value):
        decoded.append(character)
        index += 1
        continue
    index += 1
    character = value[index]
    if character == "u" and index + 4 < len(value):
        codepoint = value[index + 1:index + 5]
        try:
            decoded.append(chr(int(codepoint, 16)))
            index += 5
            continue
        except ValueError:
            pass
    decoded.append(escapes.get(character, character))
    index += 1
print("".join(decoded))
PY
}

encode_properties_value() {
  PROPERTIES_VALUE="$1" python3 - <<'PY'
import os

value = os.environ["PROPERTIES_VALUE"]
encoded = []
for character in value:
    if character in "\\ =:#!":
        encoded.append("\\")
    encoded.append(character)
print("".join(encoded))
PY
}

set_env_value() {
  local key="$1"
  local value="$2"
  local encoded_value

  [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "Invalid environment variable name: $key"
  [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] \
    || die "Environment variable $key cannot contain a line break."
  encoded_value="$(encode_properties_value "$value")"

  ENV_FILE="$ENV_FILE" ENV_KEY="$key" ENV_VALUE="$encoded_value" python3 - <<'PY'
import os
from pathlib import Path
import tempfile

path = Path(os.environ["ENV_FILE"])
key = os.environ["ENV_KEY"]
value = os.environ["ENV_VALUE"]
lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
result = []
found = False
for line in lines:
    if line.startswith(f"{key}="):
        if not found:
            result.append(f"{key}={value}")
            found = True
        continue
    result.append(line)
if not found:
    result.append(f"{key}={value}")

fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
try:
    with os.fdopen(fd, "w", encoding="utf-8") as output:
        output.write("\n".join(result) + "\n")
        output.flush()
        os.fsync(output.fileno())
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)
finally:
    if os.path.exists(temporary):
        os.unlink(temporary)
PY
  chown "$SERVICE_USER:$SERVICE_GROUP" "$ENV_FILE"
  chmod 0600 "$ENV_FILE"
}

ensure_env_value() {
  local key="$1"
  local default_value="$2"
  if [ -z "$(get_env_value "$key")" ]; then
    set_env_value "$key" "$default_value"
  fi
}

create_service_account() {
  if ! getent group "$SERVICE_GROUP" >/dev/null 2>&1; then
    groupadd --system "$SERVICE_GROUP"
  fi
  if ! id -u "$SERVICE_USER" >/dev/null 2>&1; then
    useradd --system --gid "$SERVICE_GROUP" --home-dir "$INSTALL_DIR" \
      --shell /usr/sbin/nologin "$SERVICE_USER"
  fi

  install -d -o "$SERVICE_USER" -g "$SERVICE_GROUP" -m 0750 "$INSTALL_DIR"
  install -d -o root -g "$SERVICE_GROUP" -m 0750 "$INSTALL_DIR/backend"
  install -d -o "$SERVICE_USER" -g "$SERVICE_GROUP" -m 0700 \
    "$INSTALL_DIR/uploads" "$INSTALL_DIR/logs"
}

prepare_environment() {
  if [ -s "$ENV_FILE" ]; then
    ENV_EXISTED=true
    cp -a "$ENV_FILE" "$ENV_BACKUP"
  else
    ENV_EXISTED=false
    rm -f "$ENV_BACKUP"
  fi
  ENV_TRANSACTION_STARTED=true

  if [ -n "$ENV_SOURCE" ]; then
    install -o "$SERVICE_USER" -g "$SERVICE_GROUP" -m 0600 "$ENV_SOURCE" "$ENV_FILE.next"
    mv -f "$ENV_FILE.next" "$ENV_FILE"
  elif [ "$ENV_EXISTED" = false ]; then
    install -o "$SERVICE_USER" -g "$SERVICE_GROUP" -m 0600 \
      "$RELEASE_ROOT/.env.example" "$ENV_FILE"
    log "Created $ENV_FILE from .env.example."
  fi

  chown "$SERVICE_USER:$SERVICE_GROUP" "$ENV_FILE"
  chmod 0600 "$ENV_FILE"

  ensure_env_value JWT_SECRET "$(random_secret)"
  ensure_env_value DB_TYPE sqlite
  ensure_env_value REDIS_HOST 127.0.0.1
  ensure_env_value REDIS_PORT 6379
  ensure_env_value REDIS_SSL_ENABLED false
  case "$(get_env_value REDIS_HOST)" in
    127.0.0.1|localhost|::1)
      ensure_env_value REDIS_PASSWORD "$(random_secret)"
      ;;
  esac
  ensure_env_value SITE_SETTINGS_PATH "$INSTALL_DIR/.site-settings.json"
  ensure_env_value SITE_CONTENT_PATH "$INSTALL_DIR/.site-content.json"
}

detect_public_ip() {
  local address=""
  address="$(curl -fsS --max-time 3 https://api.ipify.org 2>/dev/null || true)"
  if [ -z "$address" ]; then
    address="$(curl -fsS --max-time 3 https://ifconfig.me 2>/dev/null || true)"
  fi
  if [ -z "$address" ]; then
    address="$(hostname -I 2>/dev/null | awk '{print $1}')"
  fi
  printf '%s\n' "$address"
}

detect_private_ip() {
  hostname -I 2>/dev/null | tr ' ' '\n' \
    | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' \
    | grep -v '^127\.' | head -n 1 || true
}

configure_redis() {
  local redis_host redis_port redis_password redis_ssl redis_response
  redis_host="$(get_env_value REDIS_HOST)"
  redis_port="$(get_env_value REDIS_PORT)"
  redis_password="$(get_env_value REDIS_PASSWORD)"
  redis_ssl="$(get_env_value REDIS_SSL_ENABLED)"
  redis_host="${redis_host:-127.0.0.1}"
  redis_port="${redis_port:-6379}"

  local -a tls_arguments=()
  if [ "$redis_ssl" = "true" ]; then
    tls_arguments+=(--tls)
  fi

  if [[ "$redis_host" =~ ^(127\.0\.0\.1|localhost|::1)$ ]]; then
    [ -n "$redis_password" ] || die "REDIS_PASSWORD cannot be empty for the local Redis service."
  fi

  redis_response="$(REDISCLI_AUTH="$redis_password" redis-cli -h "$redis_host" -p "$redis_port" \
    "${tls_arguments[@]}" ping 2>/dev/null || true)"
  if [ "$redis_response" = "PONG" ]; then
    log "Redis is already reachable at ${redis_host}:${redis_port}; preserving its existing service configuration."
    return 0
  fi
  case "$redis_response" in
    *NOAUTH*|*WRONGPASS*|*"AUTH failed"*|*"no password is set"*)
      die "Redis is reachable at ${redis_host}:${redis_port}, but REDIS_PASSWORD is invalid."
      ;;
    "")
      ;;
    *)
      die "A service is reachable at ${redis_host}:${redis_port}, but it did not return a Redis PONG."
      ;;
  esac

  case "$redis_host" in
    127.0.0.1|localhost|::1)
      install_apt_packages redis-server
      [ -f /etc/redis/redis.conf ] || die "Redis configuration was not found at /etc/redis/redis.conf"
      REDIS_PASSWORD="$redis_password" python3 - <<'PY'
import json
import os
from pathlib import Path

path = Path("/etc/redis/redis.conf")
lines = path.read_text(encoding="utf-8").splitlines()
replacements = {
    "bind": "bind 127.0.0.1 ::1",
    "protected-mode": "protected-mode yes",
    "requirepass": "requirepass " + json.dumps(os.environ["REDIS_PASSWORD"]),
}
seen = set()
result = []
for line in lines:
    stripped = line.lstrip("# ").strip()
    key = stripped.split(maxsplit=1)[0] if stripped else ""
    if key in replacements:
        if key not in seen:
            result.append(replacements[key])
            seen.add(key)
        continue
    result.append(line)
for key, replacement in replacements.items():
    if key not in seen:
        result.append(replacement)
path.write_text("\n".join(result) + "\n", encoding="utf-8")
PY
      systemctl enable redis-server
      systemctl restart redis-server
      ;;
  esac

  redis_response="$(REDISCLI_AUTH="$redis_password" redis-cli -h "$redis_host" -p "$redis_port" \
    "${tls_arguments[@]}" ping 2>/dev/null || true)"
  [ "$redis_response" = "PONG" ] \
    || die "Redis is unavailable at ${redis_host}:${redis_port}."
}

validate_database() {
  local db_type db_url db_username db_password postgres_url
  db_type="$(get_env_value DB_TYPE)"
  db_type="${db_type:-sqlite}"
  case "$db_type" in
    sqlite|mysql)
      return 0
      ;;
    pgsql)
      db_url="$(get_env_value DB_URL)"
      db_username="$(get_env_value DB_USERNAME)"
      db_password="$(get_env_value DB_PASSWORD)"
      [ -n "$db_url" ] && [ -n "$db_username" ] && [ -n "$db_password" ] \
        || die "DB_TYPE=pgsql requires DB_URL, DB_USERNAME, and DB_PASSWORD."
      case "$db_password" in
        change-this-password|your_password)
          die "DB_PASSWORD still contains a placeholder value."
          ;;
      esac
      postgres_url="${db_url#jdbc:}"
      PGPASSWORD="$db_password" psql "$postgres_url" -U "$db_username" \
        -v ON_ERROR_STOP=1 -qAtc 'SELECT 1' >/dev/null 2>&1 \
        || die "PostgreSQL is unavailable or its credentials are invalid: $db_url"
      ;;
    *)
      die "Unsupported DB_TYPE: $db_type"
      ;;
  esac
}

list_ffmpeg_encoders() {
  "$1" -hide_banner -encoders 2>/dev/null | awk '$1 ~ /^[VAS]/ { print $2 }'
}

has_ffmpeg_encoder() {
  [ -n "$2" ] && list_ffmpeg_encoders "$1" | grep -Fxq "$2"
}

configure_media_tools() {
  local ffmpeg_bin video_codec audio_codec tesseract_bin ocr_language
  ffmpeg_bin="$(get_env_value INTERVIEW_VIDEO_FFMPEG_PATH)"
  ffmpeg_bin="${ffmpeg_bin:-ffmpeg}"
  command_exists "$ffmpeg_bin" || die "ffmpeg was not found: $ffmpeg_bin"

  video_codec="$(get_env_value INTERVIEW_VIDEO_VIDEO_CODEC)"
  audio_codec="$(get_env_value INTERVIEW_VIDEO_AUDIO_CODEC)"
  if [ -z "$video_codec" ]; then
    for candidate in libvpx-vp9 vp9 libvpx; do
      if has_ffmpeg_encoder "$ffmpeg_bin" "$candidate"; then
        video_codec="$candidate"
        break
      fi
    done
  fi
  if [ -z "$audio_codec" ]; then
    for candidate in libopus opus; do
      if has_ffmpeg_encoder "$ffmpeg_bin" "$candidate"; then
        audio_codec="$candidate"
        break
      fi
    done
  fi
  has_ffmpeg_encoder "$ffmpeg_bin" "$video_codec" \
    || die "ffmpeg does not provide the configured WebM video encoder: $video_codec"
  has_ffmpeg_encoder "$ffmpeg_bin" "$audio_codec" \
    || die "ffmpeg does not provide the configured WebM audio encoder: $audio_codec"

  set_env_value INTERVIEW_VIDEO_FFMPEG_PATH "$ffmpeg_bin"
  set_env_value INTERVIEW_VIDEO_VIDEO_CODEC "$video_codec"
  set_env_value INTERVIEW_VIDEO_AUDIO_CODEC "$audio_codec"

  tesseract_bin="$(get_env_value RESUME_OCR_TESSERACT_PATH)"
  tesseract_bin="${tesseract_bin:-tesseract}"
  command_exists "$tesseract_bin" || die "Tesseract was not found: $tesseract_bin"
  ocr_language="$(get_env_value RESUME_OCR_LANGUAGE)"
  if [ -z "$ocr_language" ]; then
    if "$tesseract_bin" --list-langs 2>/dev/null | grep -Fxq chi_sim; then
      ocr_language="chi_sim+eng"
    else
      ocr_language="eng"
    fi
  fi
  ensure_env_value RESUME_OCR_ENABLED true
  set_env_value RESUME_OCR_TESSERACT_PATH "$tesseract_bin"
  set_env_value RESUME_OCR_LANGUAGE "$ocr_language"
  ensure_env_value RESUME_OCR_DPI 200
  ensure_env_value RESUME_OCR_MAX_PAGES 5
}

configure_coturn() {
  [ "$CONFIGURE_COTURN" = true ] || return 0

  local turn_host external_ip private_ip shared_secret realm min_port max_port
  local turn_service="coturn"
  turn_host="$(get_env_value TURN_HOST)"
  external_ip="$(get_env_value TURN_EXTERNAL_IP)"
  private_ip="$(get_env_value TURN_PRIVATE_IP)"
  shared_secret="$(get_env_value INTERVIEW_TURN_SHARED_SECRET)"
  realm="$(get_env_value TURN_REALM)"
  min_port="$(get_env_value TURN_MIN_PORT)"
  max_port="$(get_env_value TURN_MAX_PORT)"

  external_ip="${external_ip:-$(detect_public_ip)}"
  turn_host="${turn_host:-$external_ip}"
  private_ip="${private_ip:-$(detect_private_ip)}"
  shared_secret="${shared_secret:-$(random_secret)}"
  realm="${realm:-autohr.local}"
  min_port="${min_port:-49160}"
  max_port="${max_port:-49200}"
  [ -n "$external_ip" ] && [ -n "$turn_host" ] \
    || die "Could not detect TURN_HOST/TURN_EXTERNAL_IP; set them in .env or use --skip-coturn."
  [[ "$min_port" =~ ^[0-9]+$ && "$max_port" =~ ^[0-9]+$ ]] \
    || die "TURN_MIN_PORT and TURN_MAX_PORT must be numeric."
  ((10#$min_port >= 1 && 10#$max_port <= 65535 && 10#$min_port <= 10#$max_port)) \
    || die "TURN port range must be within 1-65535 and ordered correctly."

  set_env_value TURN_HOST "$turn_host"
  set_env_value TURN_EXTERNAL_IP "$external_ip"
  set_env_value TURN_PRIVATE_IP "$private_ip"
  set_env_value TURN_REALM "$realm"
  set_env_value TURN_MIN_PORT "$min_port"
  set_env_value TURN_MAX_PORT "$max_port"
  set_env_value INTERVIEW_STUN_URLS \
    "stun:stun.l.google.com:19302,stun:stun.cloudflare.com:3478"
  set_env_value INTERVIEW_TURN_URLS \
    "turn:${turn_host}:3478?transport=udp,turn:${turn_host}:3478?transport=tcp"
  set_env_value INTERVIEW_TURN_SHARED_SECRET "$shared_secret"

  local external_mapping="external-ip=${external_ip}"
  if [ -n "$private_ip" ] && [ "$private_ip" != "$external_ip" ]; then
    external_mapping="external-ip=${external_ip}/${private_ip}"
  fi

  cat >/etc/turnserver.conf <<EOF
listening-port=3478
listening-ip=0.0.0.0
${external_mapping}
fingerprint
use-auth-secret
static-auth-secret=${shared_secret}
realm=${realm}
server-name=${realm}
no-multicast-peers
no-cli
min-port=${min_port}
max-port=${max_port}
syslog
EOF
  chown root:root /etc/turnserver.conf
  chmod 0600 /etc/turnserver.conf

  if [ -f /etc/default/coturn ]; then
    if grep -q '^TURNSERVER_ENABLED=' /etc/default/coturn; then
      sed -i 's/^TURNSERVER_ENABLED=.*/TURNSERVER_ENABLED=1/' /etc/default/coturn
    else
      printf '%s\n' 'TURNSERVER_ENABLED=1' >>/etc/default/coturn
    fi
  fi
  if ! systemctl list-unit-files coturn.service >/dev/null 2>&1 \
    && systemctl list-unit-files turnserver.service >/dev/null 2>&1; then
    turn_service="turnserver"
  fi
  systemctl enable "$turn_service"
  systemctl restart "$turn_service"

  if command_exists ufw && ufw status 2>/dev/null | grep -Fq 'Status: active'; then
    ufw allow 3478/tcp
    ufw allow 3478/udp
    ufw allow "${min_port}:${max_port}/udp"
    log "UFW allows coturn on TCP/UDP 3478 and UDP ${min_port}:${max_port}."
  fi
}

write_service_unit() {
  local destination="$1"
  cat >"$destination" <<EOF
[Unit]
Description=Auto HR System
After=network-online.target redis-server.service
Wants=network-online.target
ConditionPathExists=${INSTALL_DIR}/.env

[Service]
Type=simple
User=${SERVICE_USER}
Group=${SERVICE_GROUP}
UMask=0077
WorkingDirectory=${INSTALL_DIR}
EnvironmentFile=${INSTALL_DIR}/.env
Environment=AUTO_HR_INSTALL_DIR=${INSTALL_DIR}
Environment=SPRING_PROFILES_ACTIVE=${SERVICE_PROFILE}
Environment=SERVER_ADDRESS=${SERVICE_ADDRESS}
Environment=SERVER_PORT=${SERVICE_PORT}
ExecStart=${INSTALL_DIR}/deploy.sh --run-service
Restart=always
RestartSec=5
SuccessExitStatus=143
TimeoutStopSec=30
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=full
ReadWritePaths=${INSTALL_DIR}

[Install]
WantedBy=multi-user.target
EOF
  chown root:root "$destination"
  chmod 0644 "$destination"
}

safe_remove_tree() {
  local target="$1"
  case "$target" in
    "$INSTALL_DIR/frontend.next"|"$INSTALL_DIR/frontend.previous"|"$INSTALL_DIR/web-root.previous")
      rm -rf -- "$target"
      ;;
    *)
      die "Refusing to remove unexpected directory: $target"
      ;;
  esac
}

sync_frontend_tree() {
  local source_root="$1"
  local destination_root="$2"
  SOURCE_ROOT="$source_root" DESTINATION_ROOT="$destination_root" INSTALL_ROOT="$INSTALL_DIR" python3 - <<'PY'
import os
from pathlib import Path
import shutil

source = Path(os.environ["SOURCE_ROOT"]).resolve(strict=True)
destination = Path(os.environ["DESTINATION_ROOT"])
destination.mkdir(parents=True, exist_ok=True)
destination = destination.resolve(strict=True)
install_root = Path(os.environ["INSTALL_ROOT"]).resolve(strict=True)
blocked_roots = {
    Path("/"), Path("/bin"), Path("/boot"), Path("/dev"), Path("/etc"),
    Path("/home"), Path("/lib"), Path("/lib64"), Path("/media"), Path("/mnt"),
    Path("/opt"), Path("/proc"), Path("/root"), Path("/run"), Path("/sbin"),
    Path("/srv"), Path("/sys"), Path("/tmp"), Path("/usr"), Path("/var"),
}
if destination in blocked_roots or destination == install_root or install_root in destination.parents:
    raise SystemExit(f"Refusing unsafe web root: {destination}")
for entry in destination.iterdir():
    if entry.is_symlink() or entry.is_file():
        entry.unlink()
    else:
        shutil.rmtree(entry)
for entry in source.iterdir():
    target = destination / entry.name
    if entry.is_dir():
        shutil.copytree(entry, target)
    else:
        shutil.copy2(entry, target)
PY
}

stage_release() {
  log "Staging application files."
  install -o root -g "$SERVICE_GROUP" -m 0640 \
    "$RELEASE_ROOT/backend/auto-hr.jar" "$INSTALL_DIR/backend/auto-hr.jar.next"
  install -o root -g "$SERVICE_GROUP" -m 0750 \
    "$RELEASE_ROOT/deploy.sh" "$INSTALL_DIR/deploy.sh.next"
  install -o root -g root -m 0644 "$RELEASE_ROOT/.env.example" "$INSTALL_DIR/.env.example"
  write_service_unit "$INSTALL_DIR/auto-hr.service.next"

  safe_remove_tree "$INSTALL_DIR/frontend.next"
  install -d -o root -g root -m 0755 "$INSTALL_DIR/frontend.next"
  cp -a "$RELEASE_ROOT/frontend/." "$INSTALL_DIR/frontend.next/"
}

backup_current_release() {
  rm -f "$INSTALL_DIR/backend/auto-hr.jar.previous" \
    "$INSTALL_DIR/deploy.sh.previous" "$INSTALL_DIR/auto-hr.service.previous"
  safe_remove_tree "$INSTALL_DIR/frontend.previous"
  safe_remove_tree "$INSTALL_DIR/web-root.previous"

  if [ -f "$INSTALL_DIR/backend/auto-hr.jar" ]; then
    HAD_CURRENT_JAR=true
    cp -a "$INSTALL_DIR/backend/auto-hr.jar" "$INSTALL_DIR/backend/auto-hr.jar.previous"
  fi
  if [ -f "$INSTALL_DIR/deploy.sh" ]; then
    HAD_CURRENT_DEPLOY_SCRIPT=true
    cp -a "$INSTALL_DIR/deploy.sh" "$INSTALL_DIR/deploy.sh.previous"
  fi
  if [ -f "$INSTALL_DIR/auto-hr.service" ]; then
    HAD_CURRENT_SERVICE=true
    cp -a "$INSTALL_DIR/auto-hr.service" "$INSTALL_DIR/auto-hr.service.previous"
  elif [ -f "$SERVICE_FILE" ]; then
    HAD_CURRENT_SERVICE=true
    cp -a "$SERVICE_FILE" "$INSTALL_DIR/auto-hr.service.previous"
  fi
  if systemctl is-active --quiet "$SERVICE_NAME"; then
    SERVICE_WAS_ACTIVE=true
  fi
  if systemctl is-enabled --quiet "$SERVICE_NAME"; then
    SERVICE_WAS_ENABLED=true
  fi
}

restore_environment() {
  if [ "$ENV_EXISTED" = true ] && [ -f "$ENV_BACKUP" ]; then
    cp -a "$ENV_BACKUP" "$ENV_FILE"
    chown "$SERVICE_USER:$SERVICE_GROUP" "$ENV_FILE"
    chmod 0600 "$ENV_FILE"
  elif [ "$ENV_EXISTED" = false ]; then
    rm -f "$ENV_FILE"
  fi
}

restore_runtime_configuration() {
  [ "$ENV_EXISTED" = true ] || return 0
  (configure_redis) || log "Warning: Redis configuration could not be restored automatically."
  (configure_coturn) || log "Warning: coturn configuration could not be restored automatically."
  restore_environment
}

rollback_release() {
  [ "$ROLLBACK_DONE" = false ] || return 0
  ROLLBACK_DONE=true
  log "Restoring the previous release."
  systemctl stop "$SERVICE_NAME" 2>/dev/null || true

  if [ "$HAD_CURRENT_JAR" = true ] && [ -f "$INSTALL_DIR/backend/auto-hr.jar.previous" ]; then
    mv -f "$INSTALL_DIR/backend/auto-hr.jar.previous" "$INSTALL_DIR/backend/auto-hr.jar"
  else
    rm -f "$INSTALL_DIR/backend/auto-hr.jar"
  fi
  if [ "$HAD_CURRENT_DEPLOY_SCRIPT" = true ] && [ -f "$INSTALL_DIR/deploy.sh.previous" ]; then
    mv -f "$INSTALL_DIR/deploy.sh.previous" "$INSTALL_DIR/deploy.sh"
  else
    rm -f "$INSTALL_DIR/deploy.sh"
  fi
  if [ "$HAD_CURRENT_SERVICE" = true ] && [ -f "$INSTALL_DIR/auto-hr.service.previous" ]; then
    mv -f "$INSTALL_DIR/auto-hr.service.previous" "$INSTALL_DIR/auto-hr.service"
    install -o root -g root -m 0644 "$INSTALL_DIR/auto-hr.service" "$SERVICE_FILE"
  else
    rm -f "$INSTALL_DIR/auto-hr.service"
    rm -f "$SERVICE_FILE"
  fi
  if [ -d "$INSTALL_DIR/frontend.previous" ]; then
    rm -rf -- "$INSTALL_DIR/frontend"
    mv "$INSTALL_DIR/frontend.previous" "$INSTALL_DIR/frontend"
  elif [ "$FRONTEND_ACTIVATED" = true ]; then
    rm -rf -- "$INSTALL_DIR/frontend"
  fi
  if [ -n "$WEB_ROOT" ] && [ -d "$INSTALL_DIR/web-root.previous" ]; then
    sync_frontend_tree "$INSTALL_DIR/web-root.previous" "$WEB_ROOT" || true
  fi
  restore_environment
  restore_runtime_configuration
  systemctl daemon-reload || true
  if [ "$SERVICE_WAS_ACTIVE" = true ] && [ -f "$INSTALL_DIR/backend/auto-hr.jar" ]; then
    systemctl start "$SERVICE_NAME" || true
  fi
  if [ "$SERVICE_WAS_ENABLED" = false ]; then
    systemctl disable "$SERVICE_NAME" 2>/dev/null || true
  fi
}

cleanup_deployment() {
  rm -f "$INSTALL_DIR/backend/auto-hr.jar.next" "$INSTALL_DIR/deploy.sh.next" \
    "$INSTALL_DIR/auto-hr.service.next"
  safe_remove_tree "$INSTALL_DIR/frontend.next"
  if [ "$DEPLOY_SUCCEEDED" = true ]; then
    rm -f "$INSTALL_DIR/backend/auto-hr.jar.previous" \
      "$INSTALL_DIR/deploy.sh.previous" "$INSTALL_DIR/auto-hr.service.previous"
    safe_remove_tree "$INSTALL_DIR/frontend.previous"
    safe_remove_tree "$INSTALL_DIR/web-root.previous"
    rm -f "$ENV_BACKUP"
  fi
}

deployment_exit() {
  local status="$?"
  trap - EXIT INT TERM
  set +e
  if [ "$status" -ne 0 ]; then
    if [ "$DEPLOYMENT_STARTED" = true ]; then
      rollback_release
    elif [ "$ENV_TRANSACTION_STARTED" = true ]; then
      log "Restoring the previous environment."
      restore_environment
      restore_runtime_configuration
    fi
  fi
  cleanup_deployment
  exit "$status"
}

wait_for_health() {
  local attempt
  for attempt in $(seq 1 30); do
    if systemctl is-active --quiet "$SERVICE_NAME" \
      && curl --fail --silent --show-error "$HEALTH_URL" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  return 1
}

activate_backend() {
  mv -f "$INSTALL_DIR/deploy.sh.next" "$INSTALL_DIR/deploy.sh" || return 1
  mv -f "$INSTALL_DIR/auto-hr.service.next" "$INSTALL_DIR/auto-hr.service" || return 1
  install -o root -g root -m 0644 "$INSTALL_DIR/auto-hr.service" "$SERVICE_FILE" || return 1
  systemctl daemon-reload || return 1
  systemctl enable "$SERVICE_NAME" || return 1
  systemctl stop "$SERVICE_NAME" 2>/dev/null || true
  mv -f "$INSTALL_DIR/backend/auto-hr.jar.next" "$INSTALL_DIR/backend/auto-hr.jar" || return 1
  systemctl start "$SERVICE_NAME" || return 1
  wait_for_health
}

activate_frontend() {
  if [ -d "$INSTALL_DIR/frontend" ]; then
    mv "$INSTALL_DIR/frontend" "$INSTALL_DIR/frontend.previous" || return 1
  fi
  mv "$INSTALL_DIR/frontend.next" "$INSTALL_DIR/frontend" || return 1
  FRONTEND_ACTIVATED=true

  if [ -n "$WEB_ROOT" ]; then
    install -d -o root -g root -m 0755 "$WEB_ROOT" || return 1
    install -d -o root -g root -m 0755 "$INSTALL_DIR/web-root.previous" || return 1
    cp -a "$WEB_ROOT/." "$INSTALL_DIR/web-root.previous/" || return 1
    sync_frontend_tree "$INSTALL_DIR/frontend" "$WEB_ROOT" || return 1
  fi
  return 0
}

show_failure_diagnostics() {
  systemctl status "$SERVICE_NAME" --no-pager || true
  journalctl -u "$SERVICE_NAME" -n 100 --no-pager || true
}

main() {
  validate_install_dir
  validate_web_root
  validate_service_settings
  ensure_runtime_dependencies
  create_service_account
  trap deployment_exit EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  prepare_environment
  validate_database
  configure_redis
  configure_media_tools
  configure_coturn
  stage_release
  backup_current_release
  DEPLOYMENT_STARTED=true

  if ! activate_backend; then
    show_failure_diagnostics
    die "The new backend did not become healthy within 60 seconds."
  fi
  if ! activate_frontend; then
    die "Frontend activation failed."
  fi

  DEPLOY_SUCCEEDED=true
  log "Deployment completed successfully."
  log "Backend health: $HEALTH_URL"
  if [ -n "$WEB_ROOT" ]; then
    log "Frontend files: $WEB_ROOT"
  else
    log "Frontend is embedded in the backend and stored at $INSTALL_DIR/frontend"
  fi
  log "Environment: $ENV_FILE"
  log "Service logs: journalctl -u $SERVICE_NAME -f"
}

main
