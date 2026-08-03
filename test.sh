#!/usr/bin/env bash
#
# Runs every test in the project: 61 backend (JUnit) + 26 frontend (Vitest).
# The backend suite supplies its own signing secret, so no .env is needed.
#
set -uo pipefail
cd "$(dirname "$0")"

failed=0

# Homebrew PostgreSQL names the role after the OS user rather than 'postgres'.
export NORTHGATE_DB_USER="${NORTHGATE_DB_USER:-$(whoami)}"

echo "==> Preflight"
pg_isready -q || { echo "    PostgreSQL is not running: brew services start postgresql@18"; exit 1; }
nc -z localhost 27017 2>/dev/null || { echo "    MongoDB is not running: brew services start mongodb-community"; exit 1; }
if ! psql -lqt | cut -d'|' -f1 | grep -qw northgate_toll_test; then
  echo "    Creating northgate_toll_test (needed by DashboardAggregationTest)"
  createdb northgate_toll_test
fi
echo "    ok"

echo
echo "==> Backend (JUnit)"
(cd northgate-backend && ./mvnw -q test) || failed=1

echo
echo "==> Frontend (Vitest)"
(cd northgate-frontend && npx ng test --watch=false) || failed=1

echo
if [ "$failed" -eq 0 ]; then
  echo "All tests passed."
else
  echo "TESTS FAILED — see the output above."
fi
exit "$failed"
