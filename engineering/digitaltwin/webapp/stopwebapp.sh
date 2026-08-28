#!/usr/bin/env bash
#
# Stops the digital twin webapp (Node.js).
#
# Usage: ./stopwebapp.sh

set -euo pipefail

WEBAPP_PORT=8081

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
