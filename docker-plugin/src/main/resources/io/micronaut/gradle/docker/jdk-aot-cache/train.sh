#!/bin/bash
#
# Trains the JDK AOT cache of a Micronaut application while its Docker image is built.
# The Micronaut Gradle plugin writes this script into the Docker context when
# micronaut.docker.jdkAotCache is enabled, and the generated Dockerfile runs it:
#
#   train.sh --cache <file> [--port <port>] [--path <path>]... [--timeout <seconds>]
#            [--training-run] [--compatible-oop-compression] [--strict-probes <count>]
#            -- java [jvm options] -jar <application jar>
#
# The command after "--" is the ENTRYPOINT without -XX:AOTCache. The script runs it once with
# -XX:AOTCacheOutput=<file>:
# - with --training-run, the application's Micronaut core has the training run switch, which the
#   command turns on: the application warms itself up and must exit with status 0;
# - otherwise the application runs in the background until its HTTP server answers on port <port>.
#   Each <path> is then requested with GET and must answer with a status below 400. SIGTERM
#   stops the application, and the JVM writes the cache as it exits.
# The build fails unless the cache is written. Then <count> strict launches check the cache.
# Only bash builtins are used to talk to the application, because JRE images have no curl.

set -euo pipefail

log() {
  echo "[jdk-aot-cache] $*"
}

fail() {
  echo "[jdk-aot-cache] ERROR: $*" >&2
  exit 1
}

cache=""
port=""
paths=()
timeout=120
training_run=false
compatible_oop_compression=false
strict_probes=0
while (($# > 0)); do
  case "$1" in
    --cache) cache="$2"; shift 2 ;;
    --port) port="$2"; shift 2 ;;
    --path) paths+=("$2"); shift 2 ;;
    --timeout) timeout="$2"; shift 2 ;;
    --training-run) training_run=true; shift ;;
    --compatible-oop-compression) compatible_oop_compression=true; shift ;;
    --strict-probes) strict_probes="$2"; shift 2 ;;
    --) shift; break ;;
    *) fail "unknown option $1" ;;
  esac
done
(($# > 0)) || fail "no command to train with"
[[ -n $cache ]] || fail "no cache file"
java="$1"
shift
jvm_command=("$@")

# One run of the JVM tells whether it can write a cache and whether it has the JDK 27 creation flag
flags=$("$java" -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal -version 2>/dev/null) || fail "$java -version failed"
[[ $flags == *" AOTCacheOutput "* ]] || fail "$java has no -XX:AOTCacheOutput: the JDK AOT cache needs JDK 25 or later in the base image"
if $compatible_oop_compression && [[ $flags == *" AOTCompatibleOopCompression "* ]]; then
  # Only the JVM that creates the cache reads JDK_AOT_VM_OPTIONS
  export JDK_AOT_VM_OPTIONS="${JDK_AOT_VM_OPTIONS:+$JDK_AOT_VM_OPTIONS }-XX:+UnlockDiagnosticVMOptions -XX:+AOTCompatibleOopCompression"
  log "JDK_AOT_VM_OPTIONS=$JDK_AOT_VM_OPTIONS"
fi

rm -f "$cache"
training_command=("$java" "-XX:AOTCacheOutput=$cache" "${jvm_command[@]}")
log "Training: ${training_command[*]}"
"${training_command[@]}" &
pid=$!
# Never leave the application running if the training fails
trap 'kill -KILL "$pid" 2>/dev/null || true' EXIT

# Waits up to $1 seconds for the application to exit and stores its exit status in $status
status=""
wait_for_exit() {
  local deadline=$((SECONDS + $1))
  while kill -0 "$pid" 2>/dev/null; do
    ((SECONDS < deadline)) || return 1
    sleep 0.5
  done
  status=0
  wait "$pid" || status=$?
}

# Prints the status code of a GET request to the application, and fails if it does not answer
http_status() {
  local line
  line=$({ printf 'GET %s HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' "$1" >&3 &&
    IFS= read -r -t 30 line <&3 &&
    printf '%s' "$line"; } 2>/dev/null 3<>"/dev/tcp/127.0.0.1/$port") || return 1
  [[ $line =~ ^HTTP/[0-9.]+\ ([0-9][0-9][0-9]) ]] || return 1
  printf '%s' "${BASH_REMATCH[1]}"
}

if $training_run; then
  log "Waiting up to ${timeout}s for the training run to exit"
  wait_for_exit "$timeout" || fail "the training run did not exit within ${timeout}s"
  ((status == 0)) || fail "the training run exited with status $status"
else
  [[ -n $port ]] || fail "no HTTP port to send the training requests to"
  log "Waiting up to ${timeout}s for the application to answer on port $port"
  deadline=$((SECONDS + timeout))
  until http_status / >/dev/null; do
    if ! kill -0 "$pid" 2>/dev/null; then
      status=0
      wait "$pid" || status=$?
      fail "the application exited with status $status before it answered on port $port"
    fi
    ((SECONDS < deadline)) || fail "the application did not answer on port $port within ${timeout}s"
    sleep 0.5
  done
  for path in ${paths[@]+"${paths[@]}"}; do
    code=$(http_status "$path") || fail "GET $path got no HTTP response"
    ((code < 400)) || fail "GET $path answered with status $code"
    log "GET $path: $code"
  done
  log "Stopping the application with SIGTERM"
  kill -TERM "$pid"
  wait_for_exit "$timeout" || fail "the application did not exit within ${timeout}s of SIGTERM"
  # 143 is 128 + SIGTERM
  ((status == 0 || status == 143)) || fail "the application exited with status $status"
fi
trap - EXIT
[[ -s $cache ]] || fail "the JVM wrote no cache to $cache"
log "Wrote $cache ($(wc -c <"$cache") bytes)"

if ((strict_probes > 0)); then
  # A strict launch of the same JVM options and class path, which stops after printing the version
  probe=("$java" -XX:AOTMode=on "-XX:AOTCache=$cache")
  jar=""
  for ((i = 0; i < ${#jvm_command[@]}; i++)); do
    if [[ ${jvm_command[i]} == -jar ]]; then
      jar="${jvm_command[i + 1]}"
      break
    fi
    probe+=("${jvm_command[i]}")
  done
  [[ -n $jar ]] || fail "strict probes need a command that runs a JAR with -jar"
  probe+=(-cp "$jar" -version)
  for ((i = 1; i <= strict_probes; i++)); do
    output=$("${probe[@]}" 2>&1) || { echo "$output" >&2; fail "strict probe $i of $strict_probes failed: ${probe[*]}"; }
  done
  log "$strict_probes strict probes passed"
fi
