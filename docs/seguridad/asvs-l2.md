# Revisión de seguridad — OWASP ASVS nivel 2

Revisión **por capítulos** del estándar OWASP ASVS 5.0, con la evidencia de cada control. No es una auditoría
requisito por requisito (son cientos): antes del lanzamiento comercial conviene una revisión externa o una prueba de
penetración. 🔍 La numeración sigue ASVS 5.0 (mayo de 2025); verifica contra el documento oficial si se cita en un
contrato.

Leyenda: ✅ cubierto y probado · 🟡 parcial o con condición · ⏳ pendiente · — no aplica.

| Capítulo | Estado | Evidencia y notas |
|---|---|---|
| V1 Codificación y saneamiento | ✅ | React escapa todo el texto. Las explicaciones con formato usan `RichText`, que construye elementos sin `innerHTML`. SQL siempre parametrizado (JPA/JdbcTemplate). `SecuritySweepIT.injectionAttemptsAreTreatedAsData`. CSV con neutralización de fórmulas (`CsvTest`). |
| V2 Validación y lógica de negocio | ✅ | Bean Validation en todas las peticiones. Los montos se validan en el servidor (`SecuritySweepIT.manipulatedAmountsAreRejected`). El servidor calcula el consumo y el precio, nunca el cliente (`theClientCannotReportConsumptionOrPrices`). Idempotencia en operaciones financieras, con pruebas de concurrencia (WalletAndTutorIT, PaymentsIT, AdaptiveLearningIT). |
| V3 Seguridad del frontend web | ✅ | CSP estricta `script-src 'self'`; `frame-ancestors 'none'`; `X-Content-Type-Options`; `Referrer-Policy`; `Permissions-Policy` (micrófono solo propio). Cabeceras presentes en **todas** las rutas de nginx (corregido en la Fase 8). HSTS en Caddy. Cookies `HttpOnly`, `Secure` y `SameSite=Lax`. Verificado en la cadena real (despliegue.md). |
| V4 API y servicios web | ✅ | Barrido de **todas** las rutas del contrato OpenAPI: sin sesión → 401/403; alumno en rutas de administración → 403; sin token CSRF → 403 (`SecuritySweepIT`). Errores RFC 9457 sin detalles internos. OpenAPI desactivado en producción. |
| V5 Manejo de archivos | — | No hay subida de archivos. Las descargas CSV/JSON se generan en el servidor con `Content-Disposition: attachment` y `no-store`. |
| V6 Autenticación | ✅ | Argon2id; política de contraseñas (longitud ≥ 12, sin el correo); bloqueo por cuenta tras intentos fallidos; límites por IP en inicio de sesión, registro y recuperación (`RateLimitIT`); verificación de correo; tokens de un solo uso guardados como hash. ⏳ Segundo factor (MFA) para administradores: recomendado antes del lanzamiento. |
| V7 Gestión de sesiones | ✅ | Sesiones de servidor (Spring Session JDBC). Se revocan al cambiar la contraseña, al suspender la cuenta o al cerrarla (`PrivacyIT`: otro dispositivo pierde acceso). La cookie no es legible desde JavaScript. |
| V8 Autorización | ✅ | Verificada en el backend (`@PreAuthorize`, rutas `/api/v1/admin/**` con rol ADMIN). Propiedad de cada recurso: los recursos ajenos responden 404 (pruebas por módulo: conversaciones, llamadas, tarjetas, errores, recargas). Asignación masiva ignorada (`massAssignmentIsIgnoredAndRolesCannotBeSelfAssigned`). |
| V9 Tokens autocontenidos | — | No se usan JWT; sesiones de servidor. |
| V10 OAuth/OIDC | — | No aplica. |
| V11 Criptografía | ✅ | Argon2id para contraseñas; SHA-256 para tokens de un solo uso; HMAC-SHA256 con comparación en tiempo constante para webhooks (vector verificado con `openssl`). TLS lo termina Caddy (certificados automáticos). Respaldos cifrables con `age`. |
| V12 Comunicaciones seguras | ✅ | Solo HTTPS (HSTS de 1 año). Proveedores externos por HTTPS. MySQL sin puertos expuestos en producción. |
| V13 Configuración | ✅ | Secretos solo por variables de entorno (`.env` fuera del repositorio; gitleaks en CI). Actuator solo expone `health` sin detalles. Perfil `prod` sin recargas simuladas ni OpenAPI. Dependabot y `npm audit` en CI. 🟡 Las dependencias de desarrollo tienen avisos moderados (ESLint/minimatch) que no llegan al navegador. |
| V14 Protección de datos | ✅ | Minimización (el audio nunca se guarda; transcripción opcional). Retención de 180 días con borrado automático (`PrivacyIT`). Exportación y cierre de cuenta del titular. Ni contraseñas ni tokens en las respuestas ni en la exportación. 🟡 Cifrado en reposo del disco: depende del proveedor del servidor (actívalo). |
| V15 Código y arquitectura seguros | ✅ | Fronteras de módulos verificadas (ArchUnit). Nombres únicos en el contrato de la API. Dinero sin coma flotante. Doble partida con invariantes verificadas. Riesgo de prompt injection acotado: el texto del alumno va como mensaje de usuario, nunca como instrucción del sistema; el tema de voz se entrecomilla y se escapa; el modelo no tiene herramientas ni acceso a datos. |
| V16 Registros y manejo de errores | ✅ | Registros JSON (ECS) en producción. Sin contraseñas, tokens, IP ni correos en los mensajes propios. Bitácora de auditoría consultable para acciones sensibles. Errores sin trazas (`errorsDoNotLeakInternalsAndActuatorIsClosed`). |
| V17 WebRTC | 🟡 | La negociación SDP pasa por el backend con la clave del servidor (sin claves efímeras en el navegador). El consumo se mide por el canal lateral del servidor. ⏳ La conexión real con el proveedor no está verificada (ADR-0004/0008). |

## Riesgos aceptados o pendientes

- **Límites de peticiones en memoria:** válidos para una sola instancia; con varias, hay que moverlos.
- **MFA para administradores:** pendiente; recomendado antes de abrir el servicio a terceros.
- **Prueba de penetración externa:** pendiente antes del lanzamiento comercial.
- **Documentos legales:** borradores; requieren revisión de un abogado.
