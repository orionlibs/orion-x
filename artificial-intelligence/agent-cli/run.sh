#!/usr/bin/env bash
# call this like:
# ./run.sh log.error -m hello
# ./run.sh log.error -m 'hello world'
# ./run.sh --output-file /tmp/result.json log.error -m hello
# ./run.sh --interactive true
set -euo pipefail
INTERACTIVE="true"
JAR="target/agentcli.jar"

OUTPUT_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --output-file) OUTPUT_ARGS+=("-Dorion.x.ai.agent.cli.output-file=$2"); shift 2 ;;
        --interactive) INTERACTIVE=$2; shift 2 ;;
        *) break ;;
    esac
done

NONINTERACTIVE=$([ "$INTERACTIVE" = "true" ] && echo "false" || echo "true")
java --enable-native-access=ALL-UNNAMED "${OUTPUT_ARGS[@]+"${OUTPUT_ARGS[@]}"}" \
    -Dspring.shell.interactive.enabled=$INTERACTIVE \
    -Dspring.shell.noninteractive.enabled=$NONINTERACTIVE \
    -jar "$JAR" "$@"
