#!/usr/bin/env bash
# Prueba de restauración (RNF-15, mensual): respalda, restaura en una base aparte y compara.
#   - el número de filas de cada tabla coincide con el origen;
#   - el historial de migraciones (Flyway) es idéntico;
#   - el libro contable restaurado cuadra: Σ de todas las líneas = 0 y cada saldo = Σ de sus líneas.
# Al final borra la base de prueba. Sale con código ≠ 0 si algo no coincide.
set -euo pipefail
cd "$(dirname "$0")/../.."
SERVICE="${MYSQL_SERVICE:-mysql}"
TMP_DIR="$(mktemp -d)"
TARGET="englishos_restore_check"
trap 'rm -rf "$TMP_DIR"; docker compose exec -T "$SERVICE" sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -e \"DROP DATABASE IF EXISTS $TARGET\"" >/dev/null 2>&1 || true' EXIT

sql() { # $1 = base, $2 = consulta
  docker compose exec -T "$SERVICE" sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -N -B $1" <<< "$2" 2>/dev/null
}

BACKUP_DIR="$TMP_DIR" AGE_RECIPIENT="" infra/backup/backup.sh
FILE="$(ls "$TMP_DIR"/englishos-*.sql.gz)"
infra/backup/restore.sh "$FILE" "$TARGET"

COUNTS="select table_name from information_schema.tables where table_schema = DATABASE() and table_type = 'BASE TABLE' order by table_name"
fail=0
tables=$(sql englishos "$COUNTS")
for t in $tables; do
  a=$(sql englishos "select count(*) from \`$t\`")
  b=$(sql "$TARGET" "select count(*) from \`$t\`")
  if [[ "$a" != "$b" ]]; then
    echo "DIFERENCIA en $t: origen=$a restaurado=$b"
    fail=1
  fi
done
echo "Tablas comparadas: $(echo "$tables" | wc -w)"

h1=$(sql englishos "select group_concat(concat(version,':',checksum) order by installed_rank) from flyway_schema_history")
h2=$(sql "$TARGET" "select group_concat(concat(version,':',checksum) order by installed_rank) from flyway_schema_history")
[[ "$h1" == "$h2" ]] && echo "Historial de migraciones idéntico" || { echo "Historial de migraciones DISTINTO"; fail=1; }

sum=$(sql "$TARGET" "select coalesce(sum(amount),0) from ledger_entries")
bad=$(sql "$TARGET" "select count(*) from ledger_accounts a where a.balance <> (select coalesce(sum(e.amount),0) from ledger_entries e where e.account_id = a.id)")
echo "Libro restaurado: Σ líneas = $sum; cuentas con saldo inconsistente = $bad"
if [[ "$sum" != "0.00" && "$sum" != "0" ]] || [[ "$bad" != "0" ]]; then
  fail=1
fi

if [[ $fail -ne 0 ]]; then
  echo "RESULTADO: la restauración NO coincide" >&2
  exit 1
fi
echo "RESULTADO: restauración verificada ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
