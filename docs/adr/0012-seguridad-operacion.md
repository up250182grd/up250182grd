# ADR-0012: Seguridad de la operación (límites, derechos del titular, proxies y respaldos)

- **Estado:** Aceptado
- **Fecha:** 2026-11-11
- **Relacionado:** RNF-4, RNF-11, RNF-12, RNF-15, RNF-16; sección 18 del documento maestro

## Decisiones

1. **Límites de peticiones en el backend** (`RateLimitFilter`, ventana fija).
   - Por IP en las rutas públicas (inicio de sesión, registro, recuperación, webhook). Por usuario en IA, recargas y
     práctica.
   - Se aplican **antes** de CSRF y de la autorización, así que cuentan también los intentos rechazados.
   - Responden 429 con `Retry-After`. Las reglas viven en configuración.
   - En memoria: válido para una instancia (el MVP). Con varias, se mueve al proxy o a Redis.
2. **IP real solo desde proxies de confianza.** `server.forward-headers-strategy=native` (RemoteIpValve de Tomcat)
   acepta `X-Forwarded-*` solo de redes internas. Con `framework`, cualquier cliente podía falsear su IP para esquivar
   los límites. Verificado en la cadena real con `X-Forwarded-For` falsificado.
3. **Derechos del titular.**
   - Cada módulo implementa `PersonalDataContributor` (núcleo compartido) para exportar y borrar sus datos, así que
     identidad no conoce las tablas ajenas.
   - El cierre de cuenta:
     - borra el contenido personal;
     - **conserva anonimizados** los movimientos contables y cobros (obligación legal);
     - revoca todas las sesiones;
     - deja libre el correo.
   - Lo bloquean una sesión abierta, ser el último administrador, o tener saldo sin renuncia expresa.
4. **Retención** (`RetentionPolicy` + tarea diaria). Mensajes de conversaciones cerradas y transcripciones de voz se
   borran a los 180 días (configurable).
5. **Producción.**
   - Caddy (HTTPS automático, HSTS) → nginx (cabeceras de seguridad en cada `location`, SSE sin búfer, reenvío del
     esquema original) → backend con perfil `prod` (logs JSON, sin OpenAPI ni recargas simuladas) → MySQL sin puertos
     publicados.
   - La salud no incluye el SMTP: una caída del correo no debe marcar el servicio como caído.
6. **Respaldos.** `mysqldump --single-transaction`, cifrado opcional con `age`, suma SHA-256 y rotación. La prueba de
   restauración compara todas las tablas, el historial de migraciones y las invariantes del libro.
7. **Barrido de seguridad sobre el contrato vivo.** Las pruebas recorren todas las rutas de OpenAPI: una ruta nueva
   queda cubierta automáticamente (sesión, rol de administrador, CSRF, campos de consumo prohibidos).

## Consecuencias
- (+) Las protecciones se comprueban en cada compilación, no solo en una revisión puntual.
- (−) Los límites en memoria se reinician al reiniciar el backend. Es aceptable para frenar abusos, pero no es una
  cuota contable.
- (−) Un saldo al que se renuncia al cerrar la cuenta queda en la cuenta anonimizada; la decisión contable (D-1) está
  pendiente.
