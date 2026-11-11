#!/usr/bin/env bash
# Restaura un respaldo en una base de datos DESTINO (RNF-15).
#
# Uso:
#   infra/backup/restore.sh backups/englishos-20261111T031500Z.sql.gz englishos_restore
# Para restaurar sobre la base de producción, primero detén el backend y usa como destino "englishos"
# (ver docs/operacion/respaldos.md). Archivos .age se descifran con AGE_IDENTITY (archivo de clave privada).
set -euo pipefail

FILE="${1:?Falta el archivo de respaldo}"
TARGET="${2:?Falta la base de datos destino}"
SERVICE="${MYSQL_SERVICE:-mysql}"

if [[ ! "$TARGET" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "Nombre de base de datos no válido: $TARGET" >&2
  exit 1
fi
if [[ -f "$FILE.sha256" ]]; then
  sha256sum -c "$FILE.sha256"
fi

decompress() {
  if [[ "$FILE" == *.age ]]; then
    age -d -i "${AGE_IDENTITY:?Falta AGE_IDENTITY para descifrar}" "$FILE" | gzip -dc
  else
    gzip -dc "$FILE"
  fi
}

docker compose exec -T "$SERVICE" sh -c \
  "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" -e 'DROP DATABASE IF EXISTS \`$TARGET\`; CREATE DATABASE \`$TARGET\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;'"

# El volcado incluye "CREATE DATABASE/USE englishos": se reescribe hacia el destino elegido.
decompress \
  | sed -E "s/^(CREATE DATABASE[^\`]*\`)englishos(\`)/\1$TARGET\2/; s/^USE \`englishos\`;/USE \`$TARGET\`;/" \
  | docker compose exec -T "$SERVICE" sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'

echo "Restaurado $FILE en la base $TARGET"
