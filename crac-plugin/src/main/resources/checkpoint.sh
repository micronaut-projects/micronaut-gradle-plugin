#!/bin/bash

# Set a trap to close the app once the script finishes
trap 'echo "Killing $PROCESS" && kill -0 $PROCESS 2>/dev/null && kill $PROCESS' EXIT

echo "Starting application"
# Run the app in the background
/azul-crac-jdk/bin/java \
  -XX:CRaCCheckpointTo=cr \
  -XX:+UnlockDiagnosticVMOptions \
  -XX:+CRTraceStartupTime \
  -Djdk.crac.trace-startup-time=true \
  -jar application.jar &
PROCESS=$!
echo "Started application as process $PROCESS"


# Wait for the app to be started
echo "Waiting 10s for application to start"
retries=5
until $(curl --output /dev/null --silent --head http://localhost:8080); do
  if [ $retries -le 0 ]; then
    echo "failed"
    exit 1
  fi
  echo -n '.'
  sleep 2
  retries=$((retries - 1))
done

# Warm up the app.
echo "Warming up application"
./warmup.sh

# Take a snapshot
echo "Sending checkpoint signal to process $PROCESS"
/azul-crac-jdk/bin/jcmd $PROCESS JDK.checkpoint

echo "Wait up to 60s for snapshot to be complete"
retries=12
while [ $retries -gt 0 ]; do
  kill -0 $PROCESS 2>/dev/null
  OK=$?
  if [ $OK -eq 1 ] ; then
    echo ".done"
    break
  fi
  echo -n "$OK."
  sleep 5
  retries=$((retries - 1))
done

kill -0 $PROCESS 2>/dev/null
OK=$?
if [ $OK -eq 1 ]; then
  wait $PROCESS
  echo "EXITED WITH $?"
  echo "Snapshotting complete"
  # Make the snapshot files readable to the host
  chmod 666 cr/*
else
  echo "Snapshotting failed"
  exit 1
fi
