#!/usr/bin/env bash
set -euo pipefail

VIEWER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${VIEWER_DIR}/data"

PKG="com.ambientmemory.timeline"
DB_NAME="ambient_memory.db"

PORT="${PORT:-8008}"
OPEN_BROWSER="${OPEN_BROWSER:-true}"

SERIAL="${DEVICE_SERIAL:-}"

ADB=(adb)
if [[ -n "${SERIAL}" ]]; then
  ADB+=( -s "${SERIAL}" )
fi

echo "== AmbientMemory: pulling DB from device =="

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found in PATH."
  exit 1
fi

mkdir -p "${DATA_DIR}"

echo "-- Clearing old export: ${DATA_DIR}"
rm -rf "${DATA_DIR}"
mkdir -p "${DATA_DIR}"

echo "-- Pulling SQLite DB (${PKG}:${DB_NAME})"
# Pull DB via run-as. Requires debuggable build.
"${ADB[@]}" exec-out "run-as ${PKG} cat databases/${DB_NAME}" > "${DATA_DIR}/${DB_NAME}"

echo "-- Pulling captures (files/captures/*.jpg)"
# Extract into DATA_DIR so we end up with DATA_DIR/captures/...
"${ADB[@]}" exec-out "run-as ${PKG} sh -c 'cd files && tar -c captures'" | tar -x -C "${DATA_DIR}"

echo "== Export complete. Serving viewer =="

# Free the port: stale PID file, crashed python, or another process may still hold PORT.
free_listen_port() {
  local p="$1"
  if command -v lsof >/dev/null 2>&1; then
    local pids
    pids="$(lsof -tiTCP:"${p}" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "${pids}" ]]; then
      echo "-- Port ${p} in use; stopping listener(s): ${pids}"
      # shellcheck disable=SC2086
      kill ${pids} 2>/dev/null || true
      sleep 0.2
      # shellcheck disable=SC2086
      kill -9 ${pids} 2>/dev/null || true
    fi
  fi
}

if [[ -f "${VIEWER_DIR}/.server_pid" ]]; then
  OLD_PID="$(cat "${VIEWER_DIR}/.server_pid" || true)"
  if [[ -n "${OLD_PID}" ]] && kill -0 "${OLD_PID}" >/dev/null 2>&1; then
    echo "Stopping old server PID=${OLD_PID}"
    kill "${OLD_PID}" 2>/dev/null || true
    sleep 0.2
    kill -9 "${OLD_PID}" 2>/dev/null || true
  fi
fi

free_listen_port "${PORT}"

# If still busy, try a few alternate ports (avoids "Address already in use").
ACTUAL_PORT=""
for try in 0 1 2 3 4 5; do
  candidate=$((PORT + try))
  if command -v python3 >/dev/null 2>&1; then
    if python3 -c "import socket; s=socket.socket(); s.bind(('127.0.0.1',${candidate})); s.close()" 2>/dev/null; then
      ACTUAL_PORT="${candidate}"
      break
    fi
  fi
done
if [[ -z "${ACTUAL_PORT}" ]]; then
  echo "No free TCP port in range ${PORT}..$((PORT + 5)). Try: PORT=8015 \"${BASH_SOURCE[0]}\"" >&2
  exit 1
fi
if [[ "${ACTUAL_PORT}" != "${PORT}" ]]; then
  echo "-- Default port ${PORT} busy; using ${ACTUAL_PORT}"
fi

cd "${VIEWER_DIR}"
python3 -m http.server "${ACTUAL_PORT}" --bind 127.0.0.1 &
SERVER_PID=$!
echo "${SERVER_PID}" > "${VIEWER_DIR}/.server_pid"
PORT="${ACTUAL_PORT}"

if [[ "${OPEN_BROWSER}" == "true" ]]; then
  echo "Opening browser: http://127.0.0.1:${PORT}/"
  open "http://127.0.0.1:${PORT}/" || true
fi

echo "Done."

