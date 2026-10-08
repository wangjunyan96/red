#!/usr/bin/env bash
set -euo pipefail

APP_NAME="red"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_NAME="cloud-phone-task-server-1.0.0.jar"
JAR_PATH="$APP_DIR/$JAR_NAME"
LOG_FILE="$APP_DIR/app.log"
PID_FILE="$APP_DIR/app.pid"
PORT="8889"

cd "$APP_DIR"

if [ ! -f "$JAR_PATH" ]; then
  echo "ERROR: jar not found: $JAR_PATH"
  exit 1
fi

echo "Stopping $APP_NAME..."

if [ -f "$PID_FILE" ]; then
  OLD_PID="$(cat "$PID_FILE" || true)"
  if [ -n "${OLD_PID:-}" ] && kill -0 "$OLD_PID" 2>/dev/null; then
    echo "Killing pid from $PID_FILE: $OLD_PID"
    kill "$OLD_PID" || true
    sleep 3
    if kill -0 "$OLD_PID" 2>/dev/null; then
      echo "Force killing pid: $OLD_PID"
      kill -9 "$OLD_PID" || true
    fi
  fi
  rm -f "$PID_FILE"
fi

JAR_PIDS="$(pgrep -f "$JAR_NAME" || true)"
if [ -n "$JAR_PIDS" ]; then
  echo "Killing existing jar processes: $JAR_PIDS"
  kill $JAR_PIDS || true
  sleep 3
  JAR_PIDS="$(pgrep -f "$JAR_NAME" || true)"
  if [ -n "$JAR_PIDS" ]; then
    echo "Force killing existing jar processes: $JAR_PIDS"
    kill -9 $JAR_PIDS || true
  fi
fi

PORT_PIDS="$(ss -tlnp 2>/dev/null | awk -v port=":$PORT" '$4 ~ port {print $0}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u || true)"
if [ -n "$PORT_PIDS" ]; then
  echo "Killing processes listening on port $PORT: $PORT_PIDS"
  kill $PORT_PIDS || true
  sleep 3
  PORT_PIDS="$(ss -tlnp 2>/dev/null | awk -v port=":$PORT" '$4 ~ port {print $0}' | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u || true)"
  if [ -n "$PORT_PIDS" ]; then
    echo "Force killing processes listening on port $PORT: $PORT_PIDS"
    kill -9 $PORT_PIDS || true
  fi
fi

echo "Starting $APP_NAME..."
nohup java -jar "$JAR_PATH" > "$LOG_FILE" 2>&1 &
NEW_PID="$!"
echo "$NEW_PID" > "$PID_FILE"

echo "Started $APP_NAME, pid=$NEW_PID"
echo "Log file: $LOG_FILE"

sleep 5
if kill -0 "$NEW_PID" 2>/dev/null; then
  echo "Process is running. Recent logs:"
  tail -n 40 "$LOG_FILE"
else
  echo "ERROR: process exited. Recent logs:"
  tail -n 80 "$LOG_FILE" || true
  exit 1
fi
