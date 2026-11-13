#!/usr/bin/env bash
# Instala EnglishOS en MODO DEMO (todo simulado, sin costo real) en un servidor Ubuntu limpio.
# Pensado para una máquina gratuita de Oracle Cloud (Always Free, Ampere A1), pero sirve en cualquier Ubuntu 22.04/24.04.
#
# Uso (en el servidor):
#   curl -fsSL https://raw.githubusercontent.com/up250182grd/up250182grd/claude/sharp-pascal-hhynf8/infra/oracle/instalar.sh -o instalar.sh
#   sudo bash instalar.sh tuapp.duckdns.org tu@correo.com
#
# Volver a ejecutarlo actualiza el código y reinicia la app; conserva el .env, las contraseñas y los datos.
set -euo pipefail

DOMINIO="${1:-}"
CORREO="${2:-}"
REPO="https://github.com/up250182grd/up250182grd.git"
RAMA="${RAMA:-claude/sharp-pascal-hhynf8}"
DIR="/opt/englishos"
CREDENCIALES="/root/englishos-credenciales.txt"
COMPOSE=(docker compose -f docker-compose.prod.yml -f docker-compose.demo.yml)

if [ "$(id -u)" -ne 0 ]; then
  echo "Ejecuta el instalador con sudo: sudo bash instalar.sh <dominio> <correo>" >&2
  exit 1
fi
if [ -z "$DOMINIO" ] || [ -z "$CORREO" ]; then
  echo "Uso: sudo bash instalar.sh tuapp.duckdns.org tu@correo.com" >&2
  exit 1
fi

aleatorio() { openssl rand -hex "$1"; }

echo "== 1/5 Docker"
if ! docker compose version >/dev/null 2>&1; then
  apt-get update -y
  apt-get install -y ca-certificates curl git openssl
  curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
  sh /tmp/get-docker.sh
fi
systemctl enable --now docker >/dev/null

echo "== 2/5 Firewall del sistema (puertos 80 y 443)"
# Las imágenes Ubuntu de Oracle rechazan por defecto todo lo que no sea SSH, aunque la consola permita el tráfico.
for puerto in 80 443; do
  if ! iptables -C INPUT -p tcp --dport "$puerto" -j ACCEPT 2>/dev/null; then
    iptables -I INPUT 1 -p tcp --dport "$puerto" -j ACCEPT
  fi
done
if command -v netfilter-persistent >/dev/null 2>&1; then
  netfilter-persistent save >/dev/null 2>&1 || true
fi

echo "== 3/5 Código ($RAMA)"
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" fetch --quiet origin "$RAMA"
  git -C "$DIR" checkout --quiet "$RAMA"
  git -C "$DIR" reset --quiet --hard "origin/$RAMA"
else
  git clone --quiet --branch "$RAMA" "$REPO" "$DIR"
fi
cd "$DIR"

echo "== 4/5 Configuración"
if [ -f .env ]; then
  echo "Ya existe $DIR/.env: se conserva (contraseñas y datos sin cambios)."
else
  ADMIN_PASSWORD="Demo-$(aleatorio 10)"
  CORREO_PASSWORD="$(aleatorio 8)"
  CORREO_HASH="$(docker run --rm caddy:2.10-alpine caddy hash-password --plaintext "$CORREO_PASSWORD")"
  umask 077
  cat > .env <<EOF
# Generado por infra/oracle/instalar.sh (MODO DEMO). No lo subas al repositorio.
DOMAIN=$DOMINIO
ACME_EMAIL=$CORREO
TLS_MODE=acme
DB_USER=englishos
DB_PASSWORD=$(aleatorio 24)
MYSQL_ROOT_PASSWORD=$(aleatorio 24)
MAIL_HOST=mailpit
MAIL_PORT=1025
MAIL_FROM='EnglishOS <no-reply@$DOMINIO>'
ADMIN_BOOTSTRAP_EMAIL=$CORREO
ADMIN_BOOTSTRAP_PASSWORD=$ADMIN_PASSWORD
ADMIN_MONTHLY_CAP_MXN=1000.00
LEGAL_TERMS_VERSION=2026-11-draft
LEGAL_PRIVACY_VERSION=2026-11-draft
MAILPIT_USER=correo
MAILPIT_HASH='$CORREO_HASH'
EOF
  cat > "$CREDENCIALES" <<EOF
EnglishOS (modo demo) — https://$DOMINIO
Administrador:      $CORREO / $ADMIN_PASSWORD
Correos de prueba:  https://$DOMINIO/correo/  (usuario: correo / contraseña: $CORREO_PASSWORD)
EOF
  chmod 600 "$CREDENCIALES"
fi

echo "== 5/5 Construcción y arranque (la primera vez tarda varios minutos)"
"${COMPOSE[@]}" up -d --build

echo "Esperando a que la app responda..."
for _ in $(seq 1 90); do
  if curl -fsS -o /dev/null "https://$DOMINIO/" 2>/dev/null; then
    echo
    echo "LISTO. Abre https://$DOMINIO en tu navegador."
    echo
    cat "$CREDENCIALES" 2>/dev/null || true
    echo
    echo "Guarda estos datos; también quedan en $CREDENCIALES (solo los puede leer root)."
    exit 0
  fi
  sleep 10
done

echo "La app no respondió por HTTPS en 15 minutos. Revisa:" >&2
echo "  - que $DOMINIO apunte a la IP pública de este servidor (DuckDNS);" >&2
echo "  - que los puertos 80 y 443 estén abiertos en la consola de Oracle (lista de seguridad);" >&2
echo "  - los registros: cd $DIR && sudo ${COMPOSE[*]} logs --tail 50" >&2
exit 1
