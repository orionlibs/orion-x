#!/usr/bin/env bash
# call this like:
# ./run.sh 'log.error -m "hello"'
# ./run.sh 'log.error -m "hello"' 'math.random.from.0.to.1'
# ./run.sh --output-dir /tmp --output-file result.json 'log.error -m "hello"'
set -euo pipefail
INTERACTIVE="false"
JAR="target/agentcli.jar"

OUTPUT_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --output-file) OUTPUT_ARGS+=("-Dorion.x.ai.agent.cli.output-file=$2"); shift 2 ;;
        --interactive) INTERACTIVE=$2; shift 2 ;;
        *) break ;;
    esac
done

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
printf '%s\n' "$@" > "$TMP"
java --enable-native-access=ALL-UNNAMED "${OUTPUT_ARGS[@]+"${OUTPUT_ARGS[@]}"}" -Dspring.shell.interactive.enabled=$INTERACTIVE -jar "$JAR" "@$TMP"
