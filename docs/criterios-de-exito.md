# Los 20 criterios de éxito (sección 25 del documento maestro)

Estado al 2026-11-11 con la evidencia automatizada de cada uno. "E2E" son pruebas de Playwright en el navegador;
"IT" son pruebas de integración con MySQL real.

| # | Criterio | Estado | Evidencia |
|---|---|---|---|
| 1 | Un estudiante puede registrarse y acceder | ✅ | E2E `account.spec` (registro, confirmación por correo, inicio de sesión) · `AuthFlowIT` |
| 2 | Puede realizar una evaluación diagnóstica | ✅ | E2E `learning.spec` (diagnóstico adaptativo) · `AssessmentFlowIT` |
| 3 | Puede consultar su plan de aprendizaje | ✅ | E2E `learning.spec`, `practice.spec` |
| 4 | Puede estudiar una lección | ✅ | E2E `learning.spec` (explicación bilingüe) |
| 5 | Puede resolver ejercicios | ✅ | E2E `learning.spec` (6 tipos de ejercicio) · `ExerciseEvaluatorTest` |
| 6 | Puede recibir correcciones | ✅ | Ejercicios con corrección; correcciones del profesor IA (`tutor.spec`); práctica de errores (`AdaptiveLearningIT`) |
| 7 | Puede conversar por texto con la IA | ✅ con profesor simulado · 🟡 con proveedor real | `tutor.spec`, `WalletAndTutorIT`. El adaptador de OpenAI está implementado pero **no verificado** contra la API real. |
| 8 | Puede iniciar una conversación real por voz | 🟡 | Flujo completo con proveedor **simulado** (`voice.spec`, `VoiceIT`). La llamada **real** requiere clave de OpenAI y verificar la API (ADR-0004/0008). |
| 9 | Puede interrumpir y escuchar al profesor | ⏳ | La sesión real se configura con detección de turnos que permite interrumpir (`interrupt_response`), pero solo se puede comprobar con audio real. |
| 10 | Puede finalizar la conversación | ✅ | Colgar, cortes por reserva, duración y desconexión (`VoiceIT`, `voice.spec`) |
| 11 | Puede consultar los resultados educativos de la sesión | ✅ | Resumen de conversación (correcciones por categoría) y de llamada (transcripción y desglose) — `tutor.spec`, `voice.spec` |
| 12 | Su progreso queda registrado | ✅ | Progreso de lecciones, repasos SM-2, XP y racha (`AdaptiveLearningIT`, `GamificationIT`, `achievements.spec`) |
| 13 | Su consumo de IA queda registrado | ✅ | Medición del lado del servidor por evento del proveedor (`WalletAndTutorIT`, `VoiceIT`) |
| 14 | El administrador tiene factor 1.00 | ✅ | `adminConversationIsRecordedAtCostAsInternalExpense`, `adminCallIsRecordedAtCost…` |
| 15 | Los usuarios normales tienen factor 1.30 | ✅ | `studentConversationIs…ChargedAtCostPlus30Percent`, `studentCallIs…`, `ChargeCalculatorTest` |
| 16 | Las operaciones financieras son auditables | ✅ | Doble partida con invariantes, idempotencia, bitácora consultable y CSV que cuadran con el libro (`PaymentsIT`) |
| 17 | El administrador puede consultar costos e ingresos | ✅ | *Finanzas* (resumen, Mi consumo, recargas, reportes) — `tutor.spec`, `admin-finance.spec` |
| 18 | La plataforma funciona en móvil y computadora | ✅ | `responsive.spec` (sin desbordes en móvil); PWA instalable (verificado con Chrome); accesibilidad con axe |
| 19 | El sistema cuenta con controles de seguridad | ✅ | `SecuritySweepIT`, `RateLimitIT`, `PrivacyIT`, `ArchitectureTest`, [revisión ASVS](seguridad/asvs-l2.md) |
| 20 | Instrucciones reproducibles de instalación y ejecución | ✅ | [DESARROLLO.md](DESARROLLO.md) (local) y [operacion/despliegue.md](operacion/despliegue.md) (producción) |

## Lo que falta para declarar el MVP terminado (alcance §3.3)

- **Criterios 7 (con proveedor real), 8 y 9:**
  1. Activar OpenAI con una clave real.
  2. Verificar la API Realtime contra la documentación vigente.
  3. Firmar la **lista de verificación manual de voz** en Chrome de escritorio, Chrome para Android y Safari para iOS.
- **Conciliación real:** al menos 10 sesiones del administrador contra el panel de uso del proveedor, con la diferencia
  documentada.
- **Mercado Pago:** verificar la integración con credenciales de prueba (ADR-0010).
- **Revisión legal** de los documentos en `/legal` y **decisión fiscal D-1** (IVA, comisiones, CFDI).
