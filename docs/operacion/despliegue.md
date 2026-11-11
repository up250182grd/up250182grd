# Despliegue en producción

Topología: **Internet → Caddy (HTTPS) → nginx (app web y `/api`) → backend (Spring Boot) → MySQL.** Solo Caddy publica
puertos (80 y 443). MySQL y el backend quedan en la red interna de Docker.

## Requisitos

- Un servidor Linux con Docker Engine y el plugin Compose v2 (2 vCPU y 4 GB de RAM son suficientes para empezar).
- Un dominio con un registro DNS **A/AAAA** que apunte al servidor, y los puertos 80 y 443 abiertos (Let's Encrypt
  valida por el puerto 80).
- Un servicio SMTP para los correos (verificación y restablecimiento de contraseña).

## Primera instalación

1. Clona el repositorio en `/opt/englishos` y entra a la carpeta.
2. Copia la plantilla de variables y complétala. **Nunca subas el `.env` al repositorio.**

   ```bash
   cp .env.prod.example .env
   ```

   - Genera las contraseñas con `openssl rand -base64 32`.
   - Define `DOMAIN`, `ACME_EMAIL`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`, el SMTP y `ADMIN_BOOTSTRAP_*`.
   - Empieza con `AI_PROVIDER=fake` y `PAYMENTS_PROVIDER=none`; activa los proveedores reales después (pasos más
     abajo).
3. Arranca:

   ```bash
   docker compose -f docker-compose.prod.yml up -d --build
   ```

4. Comprueba:
   - `docker compose -f docker-compose.prod.yml ps`: todos los servicios aparecen *running* y el backend *healthy*.
   - `curl -I https://TU_DOMINIO/` responde `200` con `strict-transport-security` y `content-security-policy`.
   - Entra con el administrador inicial y cambia la contraseña. Después vacía `ADMIN_BOOTSTRAP_PASSWORD` en `.env`.
5. Programa los respaldos diarios (ver [respaldos.md](respaldos.md)).

## Actualizar a una versión nueva

```bash
cd /opt/englishos
infra/backup/backup.sh
git pull
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f backend
```

1. Respalda **antes** de actualizar.
2. Al arrancar, Flyway aplica solo las migraciones nuevas. Las migraciones son incrementales y nunca se editan una vez
   publicadas.
3. Si algo falla: vuelve a la versión anterior con `git checkout <versión>` y `up -d --build`. Si una migración nueva
   ya se aplicó, restaura el respaldo previo (ver respaldos.md).

## Activar proveedores reales

- **IA:**
  1. Registra el modelo y su tarifa oficial en *Administración → Finanzas*.
  2. Define `AI_PROVIDER=openai`, `AI_TEXT_MODEL`, y para voz `AI_VOICE_PROVIDER` y `AI_REALTIME_MODEL`.
  3. Pon `OPENAI_API_KEY` en `.env`.
  4. Haz pruebas como administrador (al costo) y concilia el consumo con el panel del proveedor.
- **Pagos:**
  1. Empieza con credenciales **de prueba** de Mercado Pago: `PAYMENTS_PROVIDER=mercadopago`, `MP_ACCESS_TOKEN`,
     `MP_WEBHOOK_SECRET` y `MP_TEST_MODE=true`.
  2. En el panel de Mercado Pago, configura el webhook en `https://TU_DOMINIO/api/v1/webhooks/mercadopago`.
  3. Verifica antes lo marcado con 🔍 en `MercadoPagoGateway` (ADR-0010).

## Seguridad de la operación

- El `.env` debe tener permisos `600` y ser propiedad de la cuenta que ejecuta Docker.
- El acceso SSH es solo con llave, y el firewall solo deja pasar los puertos 22, 80 y 443.
- Actualiza el sistema operativo y Docker con regularidad.
- Revisa los avisos de Dependabot.
- **Límites de peticiones:** viven en memoria y son válidos para **una** instancia del backend. Para escalar a varias,
  mueve los límites al proxy o a un almacén compartido (ADR-0012).
- **Registros:** salen en JSON por la salida estándar y se consultan con `docker compose logs`. No contienen
  contraseñas, tokens, IP ni correos.

## Verificación realizada

Probado localmente el 2026-11-11, con la configuración de esta carpeta:
- Caddy 2.10 con `TLS_MODE=internal`, la configuración real de nginx y el backend con el perfil `prod` sobre MySQL 8.4.

Resultados:
- **HTTPS y cabeceras:** el sitio responde por HTTPS con HSTS, CSP y `X-Frame-Options`, y sin cabecera `Server`.
- **Cookies:** salen como `Secure`, `HttpOnly` y `SameSite=Lax`.
- **Esquema real:** el `https` original atraviesa los dos proxies.
- **IP real:** el límite de inicio de sesión se aplica a la IP real aunque se falsee `X-Forwarded-For`.
- **Rutas internas:** `/actuator/*` y `/v3/api-docs` no llegan al backend.
- **Registros:** salen en JSON (ECS).
- **Configuración:** `nginx -t` y `caddy validate` son correctos.

**No verificado aquí:** la construcción de las imágenes con `docker build`. El proxy de red del entorno de desarrollo
bloquea `npm ci` y Maven dentro de la construcción. En GitHub Actions o en un servidor normal no aplica.
