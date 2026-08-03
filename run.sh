#!/usr/bin/env bash
#
# Starts the whole Northgate Toll Plaza stack: both Spring Boot services and the
# Angular dev server. Ctrl-C stops everything.
#
set -euo pipefail
cd "$(dirname "$0")"

LOGS="$(pwd)/.logs"
mkdir -p "$LOGS"

# --- configuration ---------------------------------------------------------
[ -f .env ] && { set -a; . ./.env; set +a; }

if [ -z "${NORTHGATE_JWT_SECRET:-}" ]; then
  echo "NORTHGATE_JWT_SECRET is not set. Copy .env.example to .env and fill it in:"
  echo "  cp .env.example .env && openssl rand -base64 32"
  exit 1
fi

# Homebrew PostgreSQL names the role after the OS user rather than 'postgres'.
export NORTHGATE_DB_USER="${NORTHGATE_DB_USER:-$(whoami)}"

# --- preflight -------------------------------------------------------------
pg_isready -q || { echo "PostgreSQL is not running: brew services start postgresql@18"; exit 1; }
nc -z localhost 27017 2>/dev/null || { echo "MongoDB is not running: brew services start mongodb-community"; exit 1; }
psql -lqt | cut -d'|' -f1 | grep -qw northgate_toll || { echo "Database missing: createdb northgate_toll"; exit 1; }

# --- build -----------------------------------------------------------------
echo "Building the backend..."
(cd northgate-backend && ./mvnw -q package -DskipTests)

[ -d northgate-frontend/node_modules ] || (cd northgate-frontend && npm install)

# --- run -------------------------------------------------------------------
pids=()
cleanup() {
  echo
  echo "Stopping..."
  for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup INT TERM EXIT

java -jar northgate-backend/audit-service/target/audit-service-0.0.1-SNAPSHOT.jar > "$LOGS/audit.log" 2>&1 &
pids+=($!)
java -jar northgate-backend/toll-service/target/toll-service-0.0.1-SNAPSHOT.jar   > "$LOGS/toll.log"  2>&1 &
pids+=($!)

printf 'Waiting for the services'
for _ in $(seq 1 90); do
  if grep -q "Started TollServiceApplication"  "$LOGS/toll.log"  2>/dev/null &&
     grep -q "Started AuditServiceApplication" "$LOGS/audit.log" 2>/dev/null; then
    echo " up."
    break
  fi
  if grep -qE "APPLICATION FAILED TO START" "$LOGS/toll.log" "$LOGS/audit.log" 2>/dev/null; then
    echo " failed. See $LOGS/*.log"
    exit 1
  fi
  printf '.'; sleep 1
done

(cd northgate-frontend && npx ng serve) &
pids+=($!)

cat <<'BANNER'

  Northgate Toll Plaza is running
  --------------------------------------------
  UI              http://localhost:4200
  toll-service    http://localhost:8080
  audit-service   http://localhost:8081

  op-14 / 1234    booth operator  -> lane console
  mg-02 / 1234    plaza manager   -> plaza overview

  Logs in .logs/ · Ctrl-C to stop
BANNER

wait
