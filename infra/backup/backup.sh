#!/usr/bin/env bash
# Respaldo lógico de MySQL (RNF-15). Consistente sin bloquear (InnoDB + --single-transaction).
#
# Uso (en el servidor, desde la raíz del repositorio):
#   COMPOSE_FILE=docker-compose.prod.yml infra/backup/backup.sh
# Variables:
#   BACKUP_DIR        carpeta destino (por defecto ./backups)
#   BACKUP_KEEP_DAYS  días que se conservan los respaldos locales (por defecto 14)
#   AGE_RECIPIENT     si se define, el archivo se cifra con `age` para ese destinatario (recomendado
#                     antes de copiarlo fuera del servidor)
#   MYSQL_SERVICE     servicio de compose con MySQL (por defecto mysql)
# Programar a diario con cron, p. ej.:  15 3 * * *  cd /opt/englishos && infra/backup/backup.sh >> backups/backup.log 2>&1
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
SERVICE="${MYSQL_SERVICE:-mysql}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"
umask 077
OUT="$BACKUP_DIR/englishos-$STAMP.sql.gz"

# La contraseña de root se lee DENTRO del contenedor (no aparece en la línea de comandos del host).
docker compose exec -T "$SERVICE" sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --quick --routines --triggers \
     --events --hex-blob --set-gtid-purged=OFF --no-tablespaces --databases "$MYSQL_DATABASE"' \
  | gzip -9 > "$OUT"

# Un volcado válido termina con la marca de mysqldump; si no, el respaldo está incompleto.
if ! gzip -dc "$OUT" | tail -n 1 | grep -q "Dump completed"; then
  echo "ERROR: el respaldo $OUT está incompleto" >&2
  rm -f "$OUT"
  exit 1
fi

if [[ -n "${AGE_RECIPIENT:-}" ]]; then
  age -r "$AGE_RECIPIENT" -o "$OUT.age" "$OUT"
  rm -f "$OUT"
  OUT="$OUT.age"
fi
sha256sum "$OUT" > "$OUT.sha256"

find "$BACKUP_DIR" -maxdepth 1 -name 'englishos-*.sql.gz*' -mtime +"$KEEP_DAYS" -delete
echo "Respaldo listo: $OUT ($(du -h "$OUT" | cut -f1))"
