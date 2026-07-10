#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP_HOME="${APP_HOME:-$(cd -- "${SCRIPT_DIR}/../.." && pwd)}"
JAR_PATH="${JAR_PATH:-${APP_HOME}/target/Qintelipass-0.0.1-SNAPSHOT.jar}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "Required environment variable ${name} is not set." >&2
    exit 1
  fi
}

for required_name in DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD REDIS_HOST JWT_SECRET CORS_ALLOWED_ORIGINS; do
  require_env "${required_name}"
done

if (( ${#JWT_SECRET} < 32 )); then
  echo "JWT_SECRET must contain at least 32 characters." >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java was not found in PATH." >&2
  exit 1
fi

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Application JAR was not found at ${JAR_PATH}." >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE
read -r -a JAVA_OPTS_ARRAY <<< "${JAVA_OPTS:-}"
exec java "${JAVA_OPTS_ARRAY[@]}" -jar "${JAR_PATH}"
