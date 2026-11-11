# EnglishOS — Instalación, ejecución y pruebas

Estado: **Fases 2, 3 y 4 implementadas.** Ver [Fase 4](#estado-de-la-fase-4), [Fase 3](#estado-de-la-fase-3) y [Fase 2](#estado-de-la-fase-2).

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
| Adaptador OpenAI (Chat Completions en streaming) | ⚠️ **Implementado pero no probado contra la API real** (no hay acceso a la API desde el entorno de desarrollo). Probado con un flujo de ejemplo. 🔍 Verificar con la documentación vigente antes de activarlo. |
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
| Exportar/eliminar mis datos (RF-IDN-7) | ⏳ Pendiente (Fase 9, cuando existan datos de aprendizaje) |
| Rate limiting por IP (RNF-16) | ⏳ Pendiente (Fase 9); hoy solo hay bloqueo por cuenta |
| PWA (manifest + service worker) | ⏳ Pendiente (Fase 8) |
| Auditoría de accesibilidad automatizada (axe) | ⏳ Pendiente (Fase 8) |

### Pendientes conocidos

- **Imágenes Docker (`--profile full`) sin verificar**: en el entorno donde se desarrolló la Fase 2, `docker build` no pudo descargar dependencias por la inspección TLS del proxy de red de ese entorno. Los Dockerfiles siguen el patrón estándar; deben probarse en una máquina normal o en CI. Los servicios `mysql` y `mailpit` sí se verificaron.
- `npm audit` reporta 10 avisos moderados de `minimatch` en dependencias **de desarrollo** (no llegan al build de producción). Revisar cuando haya versiones corregidas.
- Mailpit y las credenciales de `docker-compose.yml` son solo para desarrollo local.
