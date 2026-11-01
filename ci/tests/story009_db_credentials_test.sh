#!/usr/bin/env bash
# STORY-009 (ADR-003): the database password is supplied at runtime and never committed.
#
# Repository-level acceptance checks that the backend's Maven build cannot make (it only sees backend/):
#   1. no tracked file outside quality-gates/ assigns a literal to a database password setting
#   2. .env is git-ignored and untracked; the committed .env.example names DB_PASSWORD without a value
#   3. `docker compose config` fails without DB_PASSWORD and passes the value to the db and backend services
#   4. the Jenkinsfile injects the "Secret text" credential ivirma-db-password with withCredentials,
#      and its stages and their order are unchanged
#
# Usage (needs git and the docker compose CLI; nothing is started):
#   bash ci/tests/story009_db_credentials_test.sh
# Exit code 0 = all checks passed. No password value is ever printed.
set -uo pipefail
export MSYS_NO_PATHCONV=1
ROOT="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)" || exit 2
cd "$ROOT" || exit 2

FAILED=0
pass() { echo "PASS  STORY-009 $1"; }
fail() { echo "FAIL  STORY-009 $1"; FAILED=1; }

# --- 1. no database password literal in any tracked file --------------------------------------------------
# A setting is fine when its value is empty, starts with "$" (an environment reference such as ${DB_PASSWORD},
# "$(docker exec ...)" or $env:...) or is an obvious placeholder (<...>, ..., ***). A default inside the reference
# (Spring `${NAME:default}`, shell/compose `${NAME:-default}` or `${NAME-default}`) counts as a literal; compose's
# required-variable form `${NAME:?message}` does not (a key right after `{` is a reference, not a setting).
# Matches print file:line only.
DB_KEYS='(^|[^{A-Za-z0-9_])(spring\.datasource\.password|spring\.flyway\.password|POSTGRES_PASSWORD|[A-Z_]*DB_PASSWORD)'
LITERAL="${DB_KEYS}[\"']?[[:space:]]*[=:][[:space:]]*[\"']?[^\$\"'[:space:]<.*]"
WITH_DEFAULT="${DB_KEYS}[\"']?[[:space:]]*[=:][[:space:]]*[\"']?\\$\\{[A-Za-z0-9_]+(-|:[^?+])"
hits="$(git ls-files -z -- . ':(exclude)quality-gates/**' \
        | xargs -0 grep -I -n -E -e "$LITERAL" -e "$WITH_DEFAULT" -- 2>/dev/null | cut -d: -f1,2)"
if [ -z "$hits" ]; then
  pass "1 no tracked file assigns a literal to a database password setting"
else
  fail "1 database password literal in tracked files (file:line, values not shown):"
  printf '        %s\n' $hits
fi

# --- 2. .env is local only, .env.example documents the variable -------------------------------------------
if git check-ignore -q .env && ! git ls-files --error-unmatch .env >/dev/null 2>&1; then
  pass "2a .env is git-ignored and not tracked"
else
  fail "2a .env must be git-ignored and must not be tracked"
fi
if git ls-files --error-unmatch .env.example >/dev/null 2>&1 && grep -qE '^DB_PASSWORD=$' .env.example; then
  pass "2b .env.example is tracked and declares DB_PASSWORD with no value"
else
  fail "2b .env.example must be tracked and contain the line 'DB_PASSWORD=' (no value)"
fi

# --- 3. docker compose takes the password from the environment only ---------------------------------------
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
: > "$TMP/empty.env"                                           # --env-file: ignore a developer's own .env
ENV_FILE="$TMP/empty.env"
command -v cygpath >/dev/null 2>&1 && ENV_FILE="$(cygpath -m "$ENV_FILE")"   # Git Bash: docker.exe needs C:/...
compose() { docker compose -p story009-check --env-file "$ENV_FILE" -f "$ROOT/docker-compose.yml" "$@"; }

if (unset DB_PASSWORD; compose config -q) >"$TMP/missing.out" 2>&1; then
  fail "3a docker compose config must fail when DB_PASSWORD is not set"
elif grep -q 'DB_PASSWORD' "$TMP/missing.out"; then
  pass "3a docker compose config fails without DB_PASSWORD and names the variable"
else
  fail "3a docker compose config failed without DB_PASSWORD but did not name the variable"
fi

if (DB_PASSWORD='' compose config -q) >/dev/null 2>&1; then
  fail "3b docker compose config must fail when DB_PASSWORD is empty"
else
  pass "3b docker compose config fails when DB_PASSWORD is empty"
fi

VALUE="s9$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"           # random, letters+digits, never printed
if (DB_PASSWORD="${VALUE}" compose config --format json) >"$TMP/set.json" 2>"$TMP/set.err" \
   && grep -qF "\"POSTGRES_PASSWORD\": \"$VALUE\"" "$TMP/set.json" \
   && grep -qF "\"DB_PASSWORD\": \"$VALUE\"" "$TMP/set.json" \
   && [ "$(grep -cF "$VALUE" "$TMP/set.json")" -eq 2 ]; then
  pass "3c with DB_PASSWORD set, db gets it as POSTGRES_PASSWORD and backend as DB_PASSWORD (and nowhere else)"
else
  fail "3c with DB_PASSWORD set, db and backend must both receive it (compose stderr follows, no values)"
  grep -v -F "$VALUE" "$TMP/set.err" | sed 's/^/        /'
fi

# --- 4. Jenkins: credential binding, stages unchanged ----------------------------------------------------
BINDING="string(credentialsId: 'ivirma-db-password', variable: 'DB_PASSWORD')"
STAGES="$(grep -oE "stage\('[^']+'\)" Jenkinsfile | sed -E "s/stage\('([^']+)'\)/\1/" | paste -sd '|' -)"
if [ "$STAGES" = "Build|Start app|Backend tests|E2E tests|Quality Gates|Deliver" ]; then
  pass "4a Jenkinsfile stages and their order are unchanged"
else
  fail "4a Jenkinsfile stages changed: $STAGES"
fi
if grep -qF "withCredentials([$BINDING])" Jenkinsfile; then
  pass "4b Jenkinsfile injects the Secret text credential ivirma-db-password with withCredentials"
else
  fail "4b Jenkinsfile must use withCredentials([$BINDING])"
fi
if grep -qF 'docker exec ivirma-db printenv POSTGRES_PASSWORD' Jenkinsfile; then
  pass "4c Backend tests still reads the password from the running db container"
else
  fail "4c Backend tests must read the password from the db container (docker exec ivirma-db printenv POSTGRES_PASSWORD)"
fi

exit "$FAILED"
