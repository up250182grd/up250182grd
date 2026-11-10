# 10. Plan de desarrollo

Principio: cada incremento termina con **pruebas ejecutadas en verde** y algo demostrable localmente. Los proveedores externos se integran primero con adaptadores **simulados** (claramente etiquetados) y después con los reales.

Orden ajustado respecto a las fases del documento maestro: la **medición y el ledger** se adelantan (Fase 4a) porque el tutor de texto y la voz no pueden registrar consumo sin ellos. Pagos reales y reportes completos se quedan en la Fase 7.

| Fase | Entregables | Depende de | Criterios de aceptación |
|---|---|---|---|
| **1 — Análisis y arquitectura** ✅ | Este documento, ADRs. | — | Revisado por el propietario; decisiones D-1…D-6 respondidas o con valor por defecto aceptado. |
| **2 — Base del proyecto** | Monorepo, Docker Compose (MySQL 8.4 + Mailpit), Spring Boot con módulos vacíos y prueba de arquitectura, Flyway V1 (identity), registro/verificación/login/logout/recuperación, roles, bootstrap de admin, perfil, React + Vite + Tailwind con tokens claro/oscuro, AppShell responsive, páginas de auth, cliente tipado OpenAPI, CI. | 1 | `docker compose up` levanta todo; pruebas de integración de auth en verde; alumno recibe 403 en `/admin`; E2E: registrar → verificar (Mailpit) → login → dashboard vacío; CI en verde. |
| **3 — Sistema educativo** | Cursos/módulos/lecciones/ejercicios (Flyway V2), evaluadores deterministas, intentos, progreso y dominio, contenido semilla A1–A2, diagnóstico adaptativo (ítems deterministas), plan, editor admin básico. | 2 | Pruebas unitarias de cada evaluador y de la escalera; DTOs sin soluciones; E2E: diagnóstico → plan → lección → ejercicios → progreso registrado. |
| **4a — Medición y ledger** | `metering` (tarifas versionadas, fx, `ChargeCalculator`, liquidaciones), `wallet` (cuentas, asientos, reservas, captura, liberación, idempotencia), políticas 1.00/1.30, saldo y movimientos en UI, recarga **simulada**. | 2 | Pruebas unitarias del §5.2 (incluido ejemplo $100 → $100/$130); prueba de concurrencia de reservas; invariante Σ=0; idempotencia; factor guardado por cargo. |
| **4b — Profesor IA por texto** | Puerto `LlmClient` + adaptador real y simulado, plantillas de prompt pedagógicas, chat con streaming, correcciones estructuradas, memoria resumida, evaluación de escritura con rúbrica, consumo medido y liquidado por conversación. | 3, 4a | Integración con proveedor simulado: uso registrado e idempotente; liquidación correcta por rol; prueba manual con proveedor real y comparación con panel del proveedor. |
| **5 — Voz en tiempo real** | 🔍 Verificación de la API Realtime vigente; `RealtimeVoiceProvider`, SDP vía backend, canal lateral, watchdog, límites, SSE de medición, pantalla de llamada (máquina de estados), interrupciones, transcripción, resumen académico. | 4a, 4b | E2E con micrófono falso + proveedor simulado (inicio, mute, corte por reserva, desconexión); checklist manual Chrome/Android/iOS; 10 llamadas reales del admin conciliadas. |
| **6 — Aprendizaje adaptativo** | Motor de recomendaciones por reglas con pesos configurables, SM-2 y cola diaria, errores recurrentes → práctica, dashboard con gráficas. | 3, 4b | Pruebas unitarias del recomendador con perfiles; pruebas SM-2 con tabla de casos; dashboard muestra datos reales. |
| **7 — Administración y monetización** | Mercado Pago en modo prueba (🔍 verificación de documentación), webhooks firmados e idempotentes, panel financiero, "Mi consumo", política comercial versionada, conciliación, incidencias, auditoría consultable, exportación CSV, devoluciones manuales. | 4a | Pruebas: firma inválida, evento repetido, monto manipulado, retorno sin webhook → sin crédito; cambio de factor no afecta cargos previos; reportes cuadran con ledger. |
| **8 — Gamificación y UX** | XP, rachas con descanso, insignias, PWA (manifest + SW), pulido responsive, auditoría de accesibilidad. | 3, 6 | axe sin violaciones serias; Lighthouse PWA instalable; pruebas de racha. |
| **9 — Seguridad, pruebas y despliegue** | Pruebas de seguridad (roles, IDOR, importes, replay, consumo del cliente, secretos), rate limiting, cabeceras, backups y restauración, despliegue (contenedores + reverse proxy + HTTPS), runbooks, documentos legales en borrador para revisión. | todas | Checklist OWASP ASVS L2 revisado; restauración de backup probada; los 20 criterios de éxito demostrados. |

## Primer incremento propuesto (Fase 2, en este orden)

1. Esqueleto del monorepo + `docker-compose.yml` + `.env.example` + CI mínima.
2. Backend Spring Boot: `shared`, `config`, prueba de arquitectura de módulos, `ProblemDetail` para errores.
3. Flyway `V1__identity.sql` + entidades + repositorios.
4. Registro, verificación de correo, login/logout, CSRF, recuperación, bootstrap admin + pruebas de integración (Testcontainers).
5. Frontend: Vite + React + TS + Tailwind, tokens, AppShell, páginas de auth, cliente OpenAPI, guardas de ruta.
6. E2E Playwright del flujo de cuenta.

## Riesgos del plan y respuesta

| Riesgo | Respuesta |
|---|---|
| La API Realtime no permite canal lateral de servidor | Plan B (§1.1 T-1): proyecto/clave dedicada + conciliación por API de uso; control en vivo solo estimado; cobro final tras conciliación. Se decide al inicio de la Fase 5. |
| El contenido educativo no alcanza | Priorizar A1–B1 núcleo; herramienta de borradores asistidos por IA con revisión obligatoria. |
| Costos reales de voz más altos de lo esperado | Calibrar con sesiones del admin; modelos más económicos; límites de duración. |
| Bloqueo legal/fiscal para cobrar | Operar solo como uso personal (admin) hasta tener revisión; el sistema funciona completo en modo prueba. |
