#!/usr/bin/env bash
# call this like:
# ./build-and-run.sh 'log.error -m "hello"'
# ./build-and-run.sh 'log.error -m "hello"' 'math.random.from.0.to.1'
# ./build-and-run.sh --output-file /tmp/result.json 'log.error -m "hello"'
set -euo pipefail
INTERACTIVE="false"

OUTPUT_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --output-file) OUTPUT_ARGS+=("-Dorion.x.ai.agent.cli.output-file=$2"); shift 2 ;;
        *) break ;;
    esac
    case "$1" in
        --interactive) INTERACTIVE=$2; shift 2 ;;
        *) break ;;
    esac
done

mvn clean install -DskipTests=true -f ../../utils/pom.xml
mvn clean package -DskipTests=true
JAR="target/agentcli.jar"
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
printf '%s\n' "$@" > "$TMP"
java --enable-native-access=ALL-UNNAMED "${OUTPUT_ARGS[@]+"${OUTPUT_ARGS[@]}"}" -Dspring.shell.interactive.enabled=$INTERACTIVE -jar "$JAR" "@$TMP"
