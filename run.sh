#!/usr/bin/env bash
# Starts the backend, waits until it is ready, then starts the frontend.
# Press Ctrl+C once to stop both.
#
#   ./run.sh             start backend + frontend
#   ./run.sh backend     start only the backend
#   ./run.sh frontend    start only the frontend (backend must already be running)

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
LOG_DIR="$ROOT/logs"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
READY_TIMEOUT="${READY_TIMEOUT:-180}"
MODE="${1:-all}"

PIDS=()
NAMES=()

info() { printf '\033[1;36m[run]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[run]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[run]\033[0m %s\n' "$*" >&2; exit 1; }

need() { command -v "$1" >/dev/null 2>&1 || die "'$1' is not installed or not on PATH. $2"; }

port_in_use() {
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]$1\$"
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
  fi
}

backend_up() {
  if command -v curl >/dev/null 2>&1; then
    curl -fsS -o /dev/null --max-time 2 "http://127.0.0.1:$BACKEND_PORT/api/status" 2>/dev/null
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$BACKEND_PORT") 2>/dev/null
  fi
}

# Each process runs in its own process group so Ctrl+C stops it and everything it spawned.
start_bg() {
  local name="$1" dir="$2"; shift 2
  ( cd "$dir" && exec setsid "$@" >"$LOG_DIR/$name.log" 2>&1 ) &
  PIDS+=("$!")
  NAMES+=("$name")
}

cleanup() {
  trap - EXIT
  [ "${#PIDS[@]}" -gt 0 ] || return 0
  echo
  info "Stopping..."
  local pid
  for pid in "${PIDS[@]}"; do kill -TERM -- "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null; done
  sleep 2
  for pid in "${PIDS[@]}"; do kill -KILL -- "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null; done
  info "Stopped."
}
trap cleanup EXIT
trap 'exit 130' INT TERM

start_backend() {
  need java "Install Java 17+ (e.g. sudo apt install openjdk-21-jdk)."
  need mvn  "Install Maven (e.g. sudo apt install maven)."
  port_in_use "$BACKEND_PORT" && die "Port $BACKEND_PORT is already in use (an old backend still running?). Find it with: ss -ltnp | grep $BACKEND_PORT"

  info "Starting backend (the first run can take a few minutes)..."
  start_bg backend "$BACKEND_DIR" mvn -B spring-boot:run
  local pid="${PIDS[$((${#PIDS[@]} - 1))]}" waited=0

  until backend_up; do
    if ! kill -0 "$pid" 2>/dev/null; then
      warn "Backend stopped unexpectedly. Last log lines:"
      tail -n 40 "$LOG_DIR/backend.log" >&2
      exit 1
    fi
    if [ "$waited" -ge "$READY_TIMEOUT" ]; then
      warn "Backend not ready after ${READY_TIMEOUT}s. See $LOG_DIR/backend.log"
      exit 1
    fi
    sleep 1
    waited=$((waited + 1))
    [ $((waited % 15)) -eq 0 ] && info "Still waiting for the backend... (${waited}s)"
  done
  info "Backend is up on port $BACKEND_PORT."
}

start_frontend() {
  need node "Install Node.js 18+ inside WSL (e.g. via nvm)."
  need npm  "Install Node.js 18+ inside WSL (e.g. via nvm)."
  case "$(command -v node)" in
    /mnt/c/*) warn "node is the Windows install ($(command -v node)). It works, but Node inside WSL is more reliable." ;;
  esac
  port_in_use "$FRONTEND_PORT" && die "Port $FRONTEND_PORT is already in use (an old frontend still running?)."

  if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    info "Installing frontend dependencies (first run only)..."
    ( cd "$FRONTEND_DIR" && npm install ) || die "npm install failed."
  fi

  info "Starting frontend..."
  start_bg frontend "$FRONTEND_DIR" npm run dev
}

[ -d "$BACKEND_DIR" ]  || die "Cannot find $BACKEND_DIR. Keep run.sh in the project root."
[ -d "$FRONTEND_DIR" ] || die "Cannot find $FRONTEND_DIR. Keep run.sh in the project root."
need setsid "It is part of util-linux (sudo apt install util-linux)."
mkdir -p "$LOG_DIR"

case "$MODE" in
  all)      start_backend; start_frontend ;;
  backend)  start_backend ;;
  frontend) start_frontend ;;
  *)        die "Unknown option '$MODE'. Use: all | backend | frontend" ;;
esac

echo
info "Running. Open  http://127.0.0.1:$FRONTEND_PORT/  (API on port $BACKEND_PORT)"
info "Logs: $LOG_DIR/backend.log and $LOG_DIR/frontend.log   |   Ctrl+C stops everything."

# If either process dies, report it and shut the other one down too.
while true; do
  for i in "${!PIDS[@]}"; do
    if ! kill -0 "${PIDS[$i]}" 2>/dev/null; then
      warn "${NAMES[$i]} exited. Last log lines:"
      tail -n 25 "$LOG_DIR/${NAMES[$i]}.log" >&2
      exit 1
    fi
  done
  sleep 2
done
