#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVER_DIR="$ROOT/.paper-smoke"
MC_VERSION="26.1.2"
USER_AGENT="ArcaneAlchemy-CI/1.0 (https://github.com/charixmaz/ArcaneAlchemy)"

# Never accept a new EULA implicitly. CI may use an explicitly configured
# repository variable; local runs may reuse an already accepted test directory.
if [[ "${PAPER_EULA_ACCEPTED:-}" != "true" ]] && ! grep -Eq '^eula=true\s*$' "$SERVER_DIR/eula.txt" 2>/dev/null; then
  echo "Paper smoke test NOT RUN: no existing EULA acceptance; set it only after the server owner agrees." >&2
  exit 2
fi
mkdir -p "$SERVER_DIR/plugins"
if [[ "${PAPER_EULA_ACCEPTED:-}" == "true" ]]; then printf 'eula=true\n' > "$SERVER_DIR/eula.txt"; fi

BUILDS_JSON="$(curl -fsSL -H "User-Agent: $USER_AGENT" "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds")"
PAPER_URL="$(printf '%s' "$BUILDS_JSON" | jq -r 'first(.[] | select(.channel == "STABLE") | .downloads."server:default".url) // empty')"

if [[ -z "$PAPER_URL" ]]; then
  echo "No stable Paper build found for $MC_VERSION" >&2
  exit 1
fi

curl -fsSL -H "User-Agent: $USER_AGENT" "$PAPER_URL" -o "$SERVER_DIR/paper.jar"
cp "$ROOT"/target/ArcaneAlchemy-*.jar "$SERVER_DIR/plugins/ArcaneAlchemy.jar"

cd "$SERVER_DIR"
if [[ -e console.in && ! -p console.in ]]; then
  echo "Refusing to replace an unexpected console.in file." >&2
  exit 1
fi
if [[ -p console.in ]]; then rm -f -- console.in; fi
mkfifo console.in
exec 3<>console.in

java -Dpaper.disableStartupVersionCheck \
  -Xms512M -Xmx1024M -jar paper.jar --nogui <console.in >server.out 2>&1 &
SERVER_PID=$!

cleanup() {
  if kill -0 "$SERVER_PID" 2>/dev/null; then
    printf 'stop\n' >&3 || true
    sleep 2
    kill "$SERVER_PID" 2>/dev/null || true
  fi
  exec 3>&-
}
trap cleanup EXIT

ready=0
for _ in $(seq 1 120); do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "Paper exited before startup completed." >&2
    cat server.out >&2
    exit 1
  fi

  if grep -Fq "ArcaneAlchemy enabled." server.out && grep -Eq 'Done \([0-9.]+s\)!' server.out; then
    ready=1
    break
  fi

  if grep -Eq 'Could not load|Error occurred while enabling ArcaneAlchemy|NoClassDefFoundError|ClassNotFoundException' server.out; then
    echo "ArcaneAlchemy load failure detected." >&2
    cat server.out >&2
    exit 1
  fi
  sleep 1
done

if [[ "$ready" -ne 1 ]]; then
  echo "Timed out waiting for Paper + ArcaneAlchemy startup." >&2
  cat server.out >&2
  exit 1
fi

printf 'plugins\n' >&3
sleep 2
printf 'stop\n' >&3

for _ in $(seq 1 30); do
  if ! kill -0 "$SERVER_PID" 2>/dev/null; then
    wait "$SERVER_PID"
    trap - EXIT
    exec 3>&-
    echo "Paper smoke test passed: standalone ArcaneAlchemy loaded."
    exit 0
  fi
  sleep 1
done

echo "Paper did not shut down cleanly." >&2
cat server.out >&2
exit 1
