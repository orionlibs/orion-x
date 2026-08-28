#!/usr/bin/env bash
#
# Starts the digital twin webapp (Node.js), restarting it if it's
# already running.
#
# Usage: ./startwebapp.sh

set -euo pipefail

SCRIPT_DIR="/Users/dimiefthymiou/workspaces/misc/orion-x/engineering/digitaltwin/webapp"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

WEBAPP_PORT=8081
BACKEND_HOST=localhost
BACKEND_PORT=8103

kill_port() {
    local port="$1"
    local name="$2"
    local pids
    pids="$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null || true)"

    if [ -n "$pids" ]; then
        echo "Stopping existing ${name} on port ${port} (pid(s): ${pids})"
        kill -TERM $pids 2>/dev/null || true

        for _ in 1 2 3 4 5; do
            pids="$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null || true)"
            [ -z "$pids" ] && break
            sleep 1
        done

        pids="$(lsof -ti "tcp:${port}" -sTCP:LISTEN 2>/dev/null || true)"
        if [ -n "$pids" ]; then
            echo "${name} did not stop gracefully, force-killing (pid(s): ${pids})"
            kill -KILL $pids 2>/dev/null || true
        fi
    fi
}

echo "==> Stopping any running webapp"
kill_port "$WEBAPP_PORT" "webapp"

echo "==> Starting webapp"
(
    cd "$SCRIPT_DIR"
    PORT="$WEBAPP_PORT" BACKEND_HOST="$BACKEND_HOST" BACKEND_PORT="$BACKEND_PORT" \
        nohup node app/server.js > "$LOG_DIR/webapp.log" 2>&1 &
    echo $! > "$LOG_DIR/webapp.pid"
)

echo "==> Webapp is starting in the background."
echo "    webapp: http://localhost:${WEBAPP_PORT} (log: $LOG_DIR/webapp.log)"
