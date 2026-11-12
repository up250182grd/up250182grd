# EnglishOS — Instalación, ejecución y pruebas

Estado: **Fases 2 a 9 implementadas.** Ver [Fase 9](#estado-de-la-fase-9), [Fase 8](#estado-de-la-fase-8), [Fase 7](#estado-de-la-fase-7), [Fase 6](#estado-de-la-fase-6), [Fase 5](#estado-de-la-fase-5), [Fase 4](#estado-de-la-fase-4), [Fase 3](#estado-de-la-fase-3) y [Fase 2](#estado-de-la-fase-2).

## Estado de la Fase 9

| Funcionalidad | Estado |
|---|---|
| Límites de peticiones (RNF-16): inicio de sesión, registro, recuperación, IA, recargas, webhook; 429 con `Retry-After` | ✅ `RateLimitIT`; además verificado en la cadena real con `X-Forwarded-For` falsificado |
| IP real solo desde proxies de confianza (`forward-headers-strategy: native`) | ✅ |
| Barrido de seguridad sobre **todas** las rutas del contrato: sesión, rol de administrador, CSRF, campos de consumo prohibidos, cabeceras, errores sin detalles, actuator cerrado, asignación masiva, inyección, montos manipulados | ✅ `SecuritySweepIT` (9 pruebas) |
| Derechos del titular: descargar mis datos (JSON) y eliminar mi cuenta (borra el contenido, conserva la contabilidad anonimizada y revoca sesiones) | ✅ `PrivacyIT`, E2E `privacy.spec` |
| Retención de transcripciones: 180 días, borrado diario automático (RNF-11) | ✅ `PrivacyIT` |
| Documentos legales visibles antes de aceptarlos (`/legal/privacidad`, `/legal/terminos`, `/legal/comercial`) | ✅ **Borradores sujetos a revisión legal** |
| Respaldos: script diario cifrable, restauración y **prueba de restauración verificada** | ✅ [operacion/respaldos.md](operacion/respaldos.md) |
| Despliegue de producción: Caddy (HTTPS automático) + nginx + backend `prod` + MySQL interno | ✅ Configuración validada y cadena probada en local · ⚠️ construcción de imágenes no verificada aquí (proxy de red) |
| Registros JSON en producción sin datos personales; salud sin depender del SMTP | ✅ |
| Dependabot (Maven, npm, Actions, Docker) y `npm audit` de producción en CI | ✅ |
| Manuales: [despliegue](operacion/despliegue.md), [respaldos](operacion/respaldos.md), [incidentes](operacion/incidentes.md) | ✅ |
| Revisión OWASP ASVS nivel 2 | ✅ [seguridad/asvs-l2.md](seguridad/asvs-l2.md) (por capítulos; pendientes: MFA de administradores y una prueba de penetración externa) |
| Los 20 criterios de éxito | 17 ✅ · 2 🟡 · 1 ⏳ — ver [criterios-de-exito.md](criterios-de-exito.md) |

## Estado de la Fase 8

| Funcionalidad | Estado |
|---|---|
| XP por actividades terminadas (decreciente al repetir la misma lección el mismo día), separado del nivel MCER | ✅ Implementado y probado (ADR-0011) |
| Racha con 1 día de descanso por semana; calendario de 4 semanas; meta del día (5 min, 1 lección o 10 repasos) | ✅ Tabla de casos en `StreakTest` |
| 9 insignias por hitos reales, con progreso visible | ✅ |
| Gamificación por eventos de dominio, después del commit e idempotente (un fallo no afecta lecciones ni cobros) | ✅ |
| PWA instalable: manifiesto, iconos (incluido maskable), service worker que nunca guarda la API en caché, funciona sin conexión | ✅ Chrome no reporta errores de instalabilidad (CDP) con el build real servido por nginx |
| Accesibilidad: axe-core sin violaciones serias o críticas en 18 rutas (claro y oscuro) y en pantallas con contenido | ✅ |
| Pulido responsive: ninguna pantalla del alumno ni de administración se desborda en móvil | ✅ |
| nginx: cabeceras de seguridad en todas las rutas y streaming sin búfer | ✅ Corregido; configuración validada con `nginx -t` y `curl` |
| Revisión manual con lector de pantalla (NVDA/VoiceOver) y en iOS/Android reales | ⏳ Pendiente (no automatizable aquí) |
| Misiones diarias y retos (RF-GAM-4, prioridad P) | ⏳ Fuera del MVP |

## Estado de la Fase 7

| Funcionalidad | Estado |
|---|---|
| Recargas con Mercado Pago Checkout Pro (modo prueba): intención con monto fijado por el servidor, redirección a la pasarela, página de regreso que **solo consulta** | ✅ Implementado (ADR-0010) |
| Webhook público con firma HMAC; registro de todas las notificaciones; crédito solo tras consultar el pago en la API (aprobado, mismo monto y moneda) | ✅ Probado: firma inválida, evento repetido y en paralelo, monto/moneda manipulados, regreso sin webhook → sin crédito |
| Conciliación automática (cada 15 min) y manual de recargas sin notificación | ✅ |
| Devoluciones manuales de saldo no usado, con reposición si la pasarela rechaza | ✅ |
| Incidencias resolubles con motivo; bitácora de auditoría consultable con filtros | ✅ |
| Reportes CSV (liquidaciones, libro mayor, recargas) que cuadran con el libro y el resumen; protección contra inyección de fórmulas | ✅ |
| Política comercial versionada y no retroactiva, "Mi consumo" | ✅ (desde la Fase 4) |
| Adaptador HTTP de Mercado Pago | ⚠️ **Probado solo contra un servidor simulado local.** 🔍 Falta verificar el contrato con la documentación vigente y con credenciales de prueba reales. |
| Comisiones de la pasarela e IVA en la contabilidad (decisión fiscal D-1) | ⏳ Pendiente |

### Probar Mercado Pago en modo prueba (cuando lo decidas)

1. En el panel de desarrolladores de Mercado Pago crea una aplicación y copia las **credenciales de prueba**:
   - el *Access token* de prueba;
   - la *clave secreta* de webhooks.
2. En el backend define:
   - `PAYMENTS_PROVIDER=mercadopago`;
   - `MP_ACCESS_TOKEN`;
   - `MP_WEBHOOK_SECRET`;
   - `MP_TEST_MODE=true`.

   Solo en el servidor: nunca en el frontend ni en el repositorio.
3. La pasarela debe poder llegar al webhook por HTTPS: `PAYMENTS_NOTIFICATION_URL=https://<tu-dominio>/api/v1/webhooks/mercadopago`.
   En local se necesita un túnel.
4. Verifica con la documentación vigente lo marcado con 🔍 en `MercadoPagoGateway` y `WebhookSignature`. Después haz
   una recarga con una tarjeta de prueba y comprueba el crédito, el registro en *Auditoría* y el CSV de recargas.

## Estado de la Fase 6

| Funcionalidad | Estado |
|---|---|
| SM-2 (RN-43) con aritmética decimal, versión de algoritmo por tarjeta e historial antes/después (ADR-0009) | ✅ Implementado y probado con tabla de casos |
| Tarjetas creadas desde el vocabulario de las lecciones practicadas; "Mis palabras" con búsqueda y filtros | ✅ |
| Cola diaria: olvidadas → vencidas → nuevas (10/día); vencimiento al inicio del día local; repasos idempotentes; atajos de teclado | ✅ |
| Errores del profesor IA → práctica "escríbelo bien" (determinista, sin costo); superado con 2 aciertos seguidos | ✅ |
| Recomendador por reglas (6 reglas, sin IA) con pesos versionados, auditados y editables en *Administración → Recomendaciones* | ✅ Pruebas unitarias con perfiles de alumno |
| Tablero: "Para hoy", meta diaria, minutos por día (14 días, con tabla equivalente), vocabulario y errores por categoría | ✅ Datos reales; los minutos son **estimados** y así se indica |
| Regla de arquitectura: los módulos solo se comunican por su `api`; nombres únicos en los tipos de la API | ✅ |
| FSRS (algoritmo más moderno) | ⏳ v1.3 (prevista la migración por `algorithm_version`) |
| Vocabulario nuevo detectado en conversaciones con el profesor | ⏳ Pendiente (hoy el vocabulario proviene de las lecciones) |

## Estado de la Fase 5

| Funcionalidad | Estado |
|---|---|
| Cotización previa: costo estimado por minuto (marcado como estimación), minutos que alcanza el saldo/límite, motivo si no se puede llamar | ✅ Implementado y probado |
| Inicio de llamada: correo verificado, **una llamada activa por usuario**, reserva mínima de 2 minutos (alumno), tope mensual (admin) | ✅ |
| Medición en el servidor por el canal lateral (`response.done` → uso); tarifa y factor fijados al iniciar; liquidación única ×1.30 / ×1.00 | ✅ |
| Cortes automáticos: al 90 % de la reserva sin saldo/límite para ampliarla (`RESERVE_LIMIT`), duración máxima (aviso 60 s antes), cliente sin latido (`CLIENT_LOST`), cierre del proveedor | ✅ |
| Recuperación al reiniciar el servidor: las llamadas abiertas se cuelgan y se liquidan con lo registrado | ✅ (sin prueba automatizada) |
| Transcripción en vivo (SSE) y guardada solo si el alumno lo permite; **el audio nunca se guarda** | ✅ |
| Interfaz: cotización, modos, contador, consumo medido, nivel de micrófono, silenciar, colgar, transcripción, resumen con desglose, historial | ✅ |
| **Llamada simulada** (sin audio, diálogo y consumo ficticios) — proveedor por defecto, etiquetada "Llamada simulada (sin audio)" | ✅ |
| Adaptador OpenAI Realtime (WebRTC: SDP del navegador → servidor → `/v1/realtime/calls`; canal lateral por WebSocket con `call_id`; colgar) | ⚠️ **Implementado pero no probado contra la API real.** El modelo de transcripción se configura con `AI_REALTIME_TRANSCRIPTION_MODEL` (por omisión `gpt-live-transcribe`, verificado en la referencia oficial el 2026-11-11; `gpt-4o-mini-transcribe` se retira el 2027-02-26). 🔍 Falta verificar que el `call_id` llegue en el encabezado `Location` (la referencia no lo menciona). |
| Cobro de la transcripción del alumno (el proveedor la factura aparte, con la tarifa del modelo de transcripción) | ✅ Se mide por el canal lateral (por duración o por tokens), con modelo y tarifa propios fijados al iniciar, y entra en la misma liquidación; el estimado por minuto y las reservas la incluyen. Sin tarifa de transcripción vigente no se inicia la llamada. Probado con el proveedor simulado (`VoiceIT`) y con eventos de ejemplo de la referencia oficial (ADR-0013). |
| Ruta WebRTC del navegador (micrófono, oferta SDP, audio remoto, permisos, iOS) | ⚠️ Implementada; **solo se probó el modo simulado** (no hay proveedor real en el entorno de pruebas). |
| Grabación/almacenamiento de audio | ❌ Deliberadamente no se hace |

### Activar la voz real (cuando lo decidas)

1. Registra el modelo realtime en *Finanzas → Modelos y tarifas* (tipo *Voz en tiempo real*) con sus tarifas por millón de tokens de texto y audio (entrada, entrada en caché, salida) **copiadas de la página oficial de precios**, con URL y fecha.
2. Registra también el modelo de transcripción (tipo *Transcripción de voz*, código igual a `AI_REALTIME_TRANSCRIPTION_MODEL`, por omisión `gpt-live-transcribe`). Si se cobra por duración, captura su **precio por minuto**; si se cobra por tokens, sus tarifas por millón. Debe estar **en la misma moneda** que el modelo realtime. Sin esta tarifa la voz real no inicia llamadas.
3. En el backend: `AI_VOICE_PROVIDER=openai`, `AI_REALTIME_MODEL=<código del modelo>`, opcional `AI_REALTIME_VOICE` y `AI_REALTIME_TRANSCRIPTION_MODEL`, y `OPENAI_API_KEY` (solo en el servidor).
4. Haz llamadas cortas como administrador y compara el consumo registrado (incluida la transcripción) con el panel de uso del proveedor antes de abrirlo a alumnos.
5. El sitio debe servirse por HTTPS (el navegador solo permite el micrófono en contextos seguros).

## Estado de la Fase 4

| Funcionalidad | Estado |
|---|---|
| Calculadora normativa de cargos (§5.2), factor ×1.30 alumno / ×1.00 administrador | ✅ Implementado y probado |
| Libro mayor de doble partida: recargas, reservas, cargos, liberaciones, gasto interno del admin; idempotencia y verificación de invariantes | ✅ |
| Medición por uso informado por el proveedor (nunca por el navegador); tarifa y factor fijados al iniciar la sesión | ✅ |
| Tarifas versionadas con URL de fuente y fecha de verificación; política comercial versionada, no retroactiva y auditada | ✅ |
| Tipo de cambio USD/MXN (captura manual); sin tipo de cambio → liquidación pendiente de conciliación (no se inventa) | ✅ |
| Billetera: saldo, reservado, gastado del mes, movimientos, límite mensual, alerta de saldo bajo | ✅ |
| Profesor IA por texto: modos libre/guiado/roleplay, streaming, correcciones estructuradas, memoria de errores recientes, cierre por inactividad | ✅ |
| Panel de finanzas: resumen del mes, cuentas del negocio, incidencias, "Mi consumo", modelos/tarifas, política, tipo de cambio | ✅ |
| **Proveedor simulado** (sin red y sin costo real, tarifas ficticias en MXN) | ✅ — es el proveedor activo por defecto |
| Adaptador OpenAI (Responses API `POST /v1/responses` en streaming, `store: false`) | ⚠️ **Implementado pero no probado contra la API real** (sin clave). Petición y eventos (`response.output_text.delta`, `response.completed`/`incomplete`/`failed`, `error`, `usage`) verificados en la referencia oficial el 2026-11-11 y probados con un flujo de ejemplo. |
| Recargas con pago real (Mercado Pago), comisiones, IVA (decisión D-1) | ⏳ Fase 7. Hoy solo hay **recargas simuladas** (activas en `dev`/`test`, desactivadas por defecto en producción). |
| Evaluación de escritura libre con IA | ⏳ Pendiente (usará esta misma medición) |
| Tipo de cambio automático de Banxico | ⏳ Pendiente (hoy se captura manualmente) |

### Activar el proveedor real (cuando lo decidas)

1. En *Finanzas → Modelos y tarifas*, registra el modelo de OpenAI y captura su tarifa desde la página oficial de precios (URL y fecha).
2. Si la tarifa está en USD, registra el tipo de cambio USD/MXN del día en *Finanzas → Tipo de cambio*.
3. Define en el entorno del backend `AI_PROVIDER=openai`, `AI_TEXT_MODEL=<código del modelo>` y `OPENAI_API_KEY` (solo en el servidor; nunca en el frontend ni en el repositorio).
4. Haz unas conversaciones como administrador (al costo) y compara el consumo con el panel de uso de OpenAI antes de abrirlo a alumnos.

## Estado de la Fase 3

| Funcionalidad | Estado |
|---|---|
| Cursos → módulos → lecciones con textos bilingües (es/en) y cambio de idioma instantáneo (botón ES/EN) | ✅ Implementado y probado |
| Ejercicios con corrección determinista en el servidor: opción múltiple, verdadero/falso, completar, ordenar palabras, relacionar, identificar error (RF-EXR-1/2) | ✅ |
| La solución nunca llega al navegador antes de responder; intentos idempotentes (RF-EXR-3, RF-EXR-5) | ✅ |
| Retroalimentación, respuesta correcta, pistas y reintentos (RF-EXR-6) | ✅ |
| Progreso y regla de dominio en días distintos (RN-40); "Mi plan" con aprendido / en curso / pendiente (RF-CRS-5) | ✅ |
| Diagnóstico adaptativo de vocabulario, gramática y lectura con nivel estimado por habilidad y confianza (RF-ASM-1 a 4) | ✅ |
| Plan personalizado y lección recomendada (RF-ASM-5) | ✅ |
| Panel de contenido: crear, editar, ordenar, publicar, archivar y marcar como revisado, con auditoría (RF-CRS-4) | ✅ |
| Contenido semilla: 6 lecciones A1–A2 (32 ejercicios) y banco de 36 ítems de diagnóstico A1–B2 | ✅ cargado — ⚠️ **sin revisar**: revísalo desde *Administración → Contenido* |
| Escritura libre evaluada por IA | ⏳ Fase 4 (requiere medición de consumo) |
| Comprensión auditiva y expresión oral en el diagnóstico | ⏳ Posterior |
| Contenido B1 en adelante | ⏳ Pendiente de redactar y revisar (el diagnóstico ya estima hasta B2; si no hay curso de tu nivel, el plan asigna el más avanzado disponible) |

## Requisitos

| Herramienta | Versión probada |
|---|---|
| Java (JDK) | 21 |
| Maven | 3.9 |
| Node.js | 22 |
| Docker + Docker Compose | Docker 29 |

## 1. Configurar variables de entorno

```bash
cp .env.example .env
```

Edita `.env` y define al menos `ADMIN_BOOTSTRAP_EMAIL` y `ADMIN_BOOTSTRAP_PASSWORD` (mínimo 12 caracteres, sin contener la parte local de tu correo). Con eso se crea tu cuenta de administrador en el primer arranque. Después del primer arranque, borra la contraseña de `.env`.

## 2. Levantar MySQL y Mailpit

```bash
docker compose up -d mysql mailpit
```

- MySQL 8.4 en `localhost:3306` (base `englishos`).
- Mailpit (bandeja de correos de desarrollo) en <http://localhost:8025>.

## 3. Backend (Spring Boot)

```bash
cd backend
set -a; source ../.env; set +a
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- API en <http://localhost:8080>. Swagger UI (solo dev): <http://localhost:8080/swagger-ui.html>.
- Flyway crea las tablas automáticamente.

## 4. Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

Abre <http://localhost:5173>. Vite redirige `/api` al backend, así que navegador, cookies y CSRF funcionan igual que en producción (mismo origen).

## Todo en contenedores (opcional)

```bash
docker compose --profile full up -d --build
```

La app queda en <http://localhost:8088> (nginx sirve el frontend y redirige `/api` al backend).

## Pruebas

| Comando | Qué ejecuta |
|---|---|
| `cd backend && mvn verify` | Unitarias, arquitectura (ArchUnit) e integración por HTTP real contra MySQL 8.4 (Testcontainers; requiere Docker). |
| `cd frontend && npm test` | Pruebas de componentes y del cliente HTTP (Vitest). |
| `cd frontend && npm run typecheck && npm run lint` | Tipos estrictos y lint. |
| `cd frontend && npm run e2e` | E2E con Playwright contra el sistema completo (pasos 2–4 activos). Usa `E2E_ADMIN_EMAIL`/`E2E_ADMIN_PASSWORD` iguales a los del bootstrap. |

### Contrato API tipado

El backend exporta su OpenAPI a `docs/api/openapi.json` (prueba `OpenApiExportIT`). El frontend genera sus tipos con:

```bash
cd frontend
npm run gen:api
```

La CI falla si el contrato cambió y no se regeneró el cliente.

## Estado de la Fase 2

| Funcionalidad | Estado |
|---|---|
| Registro con consentimientos versionados (RF-IDN-1) | ✅ Implementado y probado |
| Verificación de correo de un solo uso, reenvío (RF-IDN-2) | ✅ |
| Login/logout con sesión de servidor, bloqueo tras 5 fallos (RF-IDN-3) | ✅ |
| Recuperación de contraseña sin revelar cuentas; revoca sesiones (RF-IDN-4) | ✅ |
| Roles y permisos verificados en backend (RF-IDN-5) | ✅ |
| Perfil: nombre, idioma de explicación, meta diaria, transcripciones (RF-IDN-6) | ✅ |
| Suspensión/reactivación y cambio de roles con auditoría y revocación de sesiones (RF-IDN-8, RF-ADM-1 parcial) | ✅ |
| Administrador inicial desde variables de entorno | ✅ |
| UI: landing, acceso, dashboard, ajustes, usuarios (admin), claro/oscuro, responsive | ✅ |
| Exportar/eliminar mis datos (RF-IDN-7) | ✅ Fase 9 |
| Rate limiting por IP (RNF-16) | ✅ Fase 9 |
| PWA (manifest + service worker) | ✅ Fase 8 |
| Auditoría de accesibilidad automatizada (axe) | ✅ Fase 8 |

### Pendientes conocidos

- **Imágenes Docker (`--profile full`) sin verificar**: en el entorno donde se desarrolló la Fase 2, `docker build` no pudo descargar dependencias por la inspección TLS del proxy de red de ese entorno. Los Dockerfiles siguen el patrón estándar; deben probarse en una máquina normal o en CI. Los servicios `mysql` y `mailpit` sí se verificaron.
- `npm audit` reporta 10 avisos moderados de `minimatch` en dependencias **de desarrollo** (no llegan al build de producción). Revisar cuando haya versiones corregidas.
- Mailpit y las credenciales de `docker-compose.yml` son solo para desarrollo local.
