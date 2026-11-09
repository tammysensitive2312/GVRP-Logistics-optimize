#!/usr/bin/env sh
# Export one stored solution as JSON for gvrp_eval (read-only, agent_reader).
# Usage: GVRP_DB_PASSWORD=... sh sql/export_solution.sh <solution_id> > export.json
# Needs Docker; the MySQL client runs in a throwaway container. The password is passed through the
# environment, never on the command line.
set -eu
id="${1:?solution id}"
case "$id" in *[!0-9]*) echo "solution id must be numeric" >&2; exit 2 ;; esac
dir="$(cd "$(dirname "$0")" && pwd)"
{ echo "SET @solution_id = $id;"; cat "$dir/export_solution.sql"; } |
  MYSQL_PWD="${GVRP_DB_PASSWORD:?set GVRP_DB_PASSWORD}" docker run --rm -i -e MYSQL_PWD mysql:8.0 \
    mysql -h"${GVRP_DB_HOST:-host.docker.internal}" -u"${GVRP_DB_USER:-agent_reader}" \
          --default-character-set=utf8mb4 -N -B -r gvrp_db
