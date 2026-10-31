#!/usr/bin/env bash
#
# Run Flyway migrations against the PostgreSQL database selected by DB_URL.
#
# DB_URL uses the same host/path form as application.conf, e.g.
#   DB_URL=//localhost:5432/drugcalc
# DB_USER and DB_PASSWORD are forwarded to Flyway when set.

set -euo pipefail

if ! command -v flyway >/dev/null 2>&1; then
    echo "flyway cannot be found in PATH" >&2
    exit 1
fi

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
migration_dir="$script_dir/../src/main/resources/db/migration"

if [[ ! -d "$migration_dir" ]]; then
    echo "migration directory not found: $migration_dir" >&2
    exit 2
fi
migration_dir=$(cd -- "$migration_dir" && pwd)

: "${DB_URL:?DB_URL must be set, e.g. //localhost:5432/drugcalc}"

url=${DB_URL#//}
host_port=${url%%/*}
if [[ "$host_port" == "$url" ]]; then
    echo "DB_URL must include a database path, e.g. //localhost:5432/drugcalc" >&2
    exit 2
fi
database=${url#*/}
if [[ -z "$database" || "$database" == */* ]]; then
    echo "DB_URL must contain exactly one database path component: $DB_URL" >&2
    exit 2
fi

if [[ "$host_port" == *:* ]]; then
    host=${host_port%:*}
    port=${host_port##*:}
else
    host=$host_port
    port=5432
fi

if [[ -z "$host" || -z "$port" ]]; then
    echo "could not parse host/port from DB_URL: $DB_URL" >&2
    exit 2
fi
case "$port" in
    ''|*[!0-9]*)
        echo "invalid port in DB_URL: $port" >&2
        exit 2
        ;;
esac

FLYWAY_URL="jdbc:postgresql://${host}:${port}/${database}"
FLYWAY_LOCATIONS="filesystem:${migration_dir}"
export FLYWAY_URL FLYWAY_LOCATIONS

if [[ -n "${DB_USER:-}" ]]; then
    export FLYWAY_USER="$DB_USER"
fi
if [[ -n "${DB_PASSWORD:-}" ]]; then
    export FLYWAY_PASSWORD="$DB_PASSWORD"
fi

echo "running Flyway migrations on postgresql://$host:$port/$database"
exec flyway migrate
