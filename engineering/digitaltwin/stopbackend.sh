#!/usr/bin/env bash
#
# Stops PostgreSQL and the digital twin backend Spring Boot services
# (thing-service, then sensor-simulator).
#
# Usage: ./stopbackend.sh

set -euo pipefail

SCRIPT_DIR="/Users/dimiefthymiou/workspaces/misc/orion-x/engineering/digitaltwin"

THING_SERVICE_DIR="$SCRIPT_DIR/thing"
SENSOR_SIMULATOR_DIR="$SCRIPT_DIR/sensor-simulator"

THING_SERVICE_PORT=8103
SENSOR_SIMULATOR_PORT=8102

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

echo "==> Stopping any running digital twin backend services"
kill_port "$THING_SERVICE_PORT" "thing-service"
kill_port "$SENSOR_SIMULATOR_PORT" "sensor-simulator"

echo "==> Stopping PostgreSQL"
brew services stop postgresql@16

