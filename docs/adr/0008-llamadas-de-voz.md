# ADR-0008: Llamadas de voz — conexión, medición y cortes

- **Estado:** Aceptado (el adaptador real queda pendiente de verificar contra la API)
- **Fecha:** 2026-11-11
- **Relacionado:** ADR-0004 (medición del lado del servidor), ADR-0007 (cobro del tutor)

## Contexto
La voz en tiempo real es la función más cara y la más fácil de manipular desde el cliente: el audio fluye directo
entre el navegador y el proveedor. Hay que cobrar exactamente lo consumido, cortar a tiempo si no hay saldo y no
exponer la clave del proveedor.

## Decisión
1. **Conexión unificada (sin claves efímeras).** El navegador genera la oferta SDP y la envía al backend; el backend
   la manda al proveedor (`POST /v1/realtime/calls`, multipart `sdp` + `session`) con su clave y devuelve la respuesta
   SDP. Se evitan las claves efímeras porque hay reportes de que el canal lateral responde 404 con llamadas creadas así.
2. **Canal lateral del servidor** (`wss://…/v1/realtime?call_id=…`): el backend recibe `response.done` con el uso y lo
   registra (`ai_usage_records`, idempotente por id de respuesta). El navegador nunca informa consumo.
3. **Reserva por minutos.** Al iniciar se reservan `min(15, minutos que alcanzan)` × costo estimado por minuto × 1.20.
   Mínimo 2 minutos o no se inicia. Al medir ≥ 90 % de la reserva se intenta ampliar; si no hay saldo o el límite
   mensual no lo permite, la llamada se corta (`RESERVE_LIMIT`).
4. **Vigilancia** cada 2 s: duración máxima, aviso 60 s antes, y corte si el navegador deja de mandar latidos (30 s).
5. **Fin en dos pasos:** `ENDING` (colgar en el proveedor) → espera breve para recibir los últimos eventos de uso →
   liquidación única (`ENDED`). Uso que llegue después de liquidar abre una incidencia de conciliación.
6. **Una llamada activa por usuario** garantizada por una columna `active_user_id` con índice único.
7. **Privacidad:** el audio nunca se guarda; la transcripción solo si el alumno lo permite (`store_transcripts`).

## Consecuencias
- (+) El cliente no puede alterar el consumo ni ver la clave.
- (+) El proveedor simulado ejercita todo el flujo (reserva, medidor, cortes, liquidación) sin costo.
- (−) Pueden llegar eventos de uso tras el corte; el exceso queda cubierto por el sobregiro técnico (RN-24) y se audita.
- (−) Depende de capacidades del proveedor 🔍 aún no verificadas desde este entorno (formato de `Location`, nombres de
  eventos); aisladas en `OpenAiRealtimeVoiceProvider` y `OpenAiRealtimeEvents`.
