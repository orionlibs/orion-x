#!/usr/bin/env bash
#
# Starts PostgreSQL and the digital twin backend Spring Boot services
# (thing-service, then sensor-simulator), restarting them if they're
# already running.
#
# Usage: ./runbackend.sh

set -euo pipefail

SCRIPT_DIR="/Users/dimiefthymiou/workspaces/misc/orion-x/engineering/digitaltwin"
LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

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

start_service() {
    local dir="$1"
    local name="$2"
    export JAVA_HOME="$HOME/.sdkman/candidates/java/26-oracle"
    export PATH="$JAVA_HOME/bin:$PATH"

    echo "==> Starting ${name}"
    (
        cd "$dir"
        nohup mvn spring-boot:run > "$LOG_DIR/${name}.log" 2>&1 &
        echo $! > "$LOG_DIR/${name}.pid"
    )
    echo "    logs: $LOG_DIR/${name}.log"
}

echo "==> Stopping any running digital twin backend services"
kill_port "$THING_SERVICE_PORT" "thing-service"
kill_port "$SENSOR_SIMULATOR_PORT" "sensor-simulator"

echo "==> Restarting PostgreSQL"
brew services stop postgresql@16
brew services start postgresql@16

echo "==> Waiting for 12 seconds for PostgreSQL to be ready"
sleep 12

start_service "$THING_SERVICE_DIR" "thing-service"
start_service "$SENSOR_SIMULATOR_DIR" "sensor-simulator"

echo "==> Backend services are starting in the background."
echo "    thing-service:     http://localhost:${THING_SERVICE_PORT} (log: $LOG_DIR/thing-service.log)"
echo "    sensor-simulator:   http://localhost:${SENSOR_SIMULATOR_PORT} (log: $LOG_DIR/sensor-simulator.log)"
