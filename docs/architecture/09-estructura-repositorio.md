# 9. Estructura del repositorio

Monorepo con dos aplicaciones y la infraestructura local. (Se creará en la Fase 2; **ningún archivo de esta lista existe todavía** salvo `docs/`.)

```
englishos/
├── README.md                     ← instalación y ejecución local
├── .env.example                  ← todas las variables documentadas, sin valores secretos
├── docker-compose.yml            ← mysql, mailpit, backend, frontend (perfil dev)
├── .github/
│   ├── workflows/ci.yml          ← build + pruebas backend/frontend + gitleaks + dependencias
│   └── dependabot.yml
├── docs/
│   ├── README.md                 ← índice (este diseño)
│   ├── architecture/             ← documentos 01–10
│   ├── adr/                      ← decisiones de arquitectura numeradas
│   ├── runbooks/                 ← backups, conciliación, incidentes
│   └── legal/                    ← borradores de aviso de privacidad, términos, política de cobros (⚖️)
├── backend/
│   ├── pom.xml                   ← Maven (wrapper incluido)
│   ├── src/main/java/com/englishos/
│   │   ├── EnglishOsApplication.java
│   │   ├── shared/               ← Money, ids ULID, errores RFC 9457 (ProblemDetail), reloj, idempotencia
│   │   ├── config/               ← seguridad, CORS, OpenAPI, Jackson, rate limiting
│   │   ├── identity/{api,web,application,domain,infrastructure}
│   │   ├── learning/…            ← cursos, lecciones, ejercicios, evaluadores, progreso
│   │   ├── assessment/…
│   │   ├── vocabulary/…          ← SM-2
│   │   ├── recommendation/…
│   │   ├── ai/                   ← puertos LlmClient, RealtimeVoiceProvider + adaptadores openai/, fake/
│   │   ├── tutor/…
│   │   ├── voice/…               ← sesiones, canal lateral, watchdog
│   │   ├── metering/…            ← tarifas, fx, calculadora de cargos, liquidaciones, conciliación
│   │   ├── wallet/…              ← ledger, reservas
│   │   ├── payments/…            ← PaymentGateway + adaptadores mercadopago/, fake/
│   │   ├── gamification/…
│   │   ├── admin/…
│   │   └── audit/…
│   ├── src/main/resources/
│   │   ├── application.yml, application-dev.yml, application-test.yml, application-prod.yml
│   │   ├── db/migration/V1__identity.sql, V2__learning.sql, …   ← Flyway
│   │   ├── db/seed/              ← contenido semilla (solo perfil dev / comando de importación)
│   │   └── prompts/              ← plantillas de prompt versionadas (importadas a prompt_templates)
│   └── src/test/java/com/englishos/…   ← unitarias, integración (Testcontainers), arquitectura
├── frontend/
│   ├── package.json, vite.config.ts, tsconfig.json, index.html
│   ├── public/                   ← iconos PWA, manifest
│   └── src/
│       ├── main.tsx, App.tsx, routes.tsx
│       ├── api/                  ← cliente tipado generado (openapi-typescript) + wrapper con CSRF
│       ├── components/ui/        ← componentes accesibles base (Button, Dialog, Card…)
│       ├── components/layout/    ← AppShell, Sidebar, BottomNav, AdminShell
│       ├── features/
│       │   ├── auth/  onboarding/  dashboard/  lessons/  exercises/  practice/
│       │   ├── vocabulary/  tutor-chat/  voice-call/  progress/  achievements/
│       │   ├── wallet/  settings/
│       │   └── admin/{users,content,ai,finance,my-usage,pricing,incidents,audit}/
│       ├── hooks/, lib/ (formatMoney MXN con Intl, fechas), stores/ (Zustand)
│       ├── styles/tokens.css     ← tokens claro/oscuro
│       └── test/                 ← utilidades de prueba
│   └── e2e/                      ← Playwright (flujo principal, voz con micrófono falso, responsive, axe)
└── infra/
    ├── docker/backend.Dockerfile, frontend.Dockerfile (nginx con CSP)
    └── prod/                     ← ejemplo de despliegue (reverse proxy, mismo origen)
```

## Responsabilidades clave

| Ruta | Responsabilidad |
|---|---|
| `backend/.../ai/fake` | Proveedor simulado determinista para pruebas y desarrollo sin gastar: emite eventos de uso conocidos. **Siempre etiquetado como simulado en la UI.** |
| `backend/.../payments/fake` | Pasarela simulada con webhooks firmados con clave de prueba. |
| `backend/.../metering/domain/ChargeCalculator` | Implementación pura del algoritmo §5.2. |
| `backend/.../wallet/domain/LedgerPosting` | Construcción de asientos balanceados (rechaza si Σ ≠ 0). |
| `frontend/src/features/voice-call` | Máquina de estados de la llamada (idle → requesting-mic → connecting → active → ending → summary/error). |

## Variables de entorno (primer borrador)

| Variable | Descripción | Ejemplo dev |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` / `test` / `prod` | `dev` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión MySQL | `jdbc:mysql://localhost:3306/englishos` |
| `APP_BASE_URL` | Origen público del frontend | `http://localhost:5173` |
| `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD` | Crea el primer admin si no existe | — |
| `AI_PROVIDER` | `fake` / `openai` | `fake` |
| `OPENAI_API_KEY` | Clave del proveedor (**solo servidor**) | — |
| `PAYMENTS_GATEWAY` | `fake` / `mercadopago` | `fake` |
| `MP_ACCESS_TOKEN`, `MP_WEBHOOK_SECRET` | Credenciales de Mercado Pago (prueba primero) | — |
| `FX_PROVIDER`, `BANXICO_TOKEN` | Tipo de cambio | `manual` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` | SMTP (Mailpit en dev) | `localhost`, `1025` |
