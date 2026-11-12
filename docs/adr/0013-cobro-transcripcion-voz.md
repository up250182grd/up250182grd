# ADR-0013: Cobro de la transcripción en las llamadas de voz

- **Estado:** Aceptado
- **Fecha:** 2026-11-12
- **Relacionado:** ADR-0004, ADR-0005, ADR-0008; RN-4, RN-23, RN-34

## Contexto

En una llamada de voz, el proveedor transcribe lo que dice el alumno con un modelo aparte. Según la referencia
oficial de OpenAI Realtime (verificada el 2026-11-11), el evento
`conversation.item.input_audio_transcription.completed` trae un `usage` que "se factura con la tarifa del modelo de
transcripción, no con la del modelo realtime". Ese uso puede llegar de dos formas:

- `{"type":"duration","seconds":…}`, para modelos que se cobran por minuto (p. ej. `gpt-live-transcribe`, listado
  a $0.017/min en la página de precios);
- `{"type":"tokens","input_tokens":…,"input_token_details":{"audio_tokens","text_tokens"},"output_tokens":…}`.

Hasta ahora ese costo no se medía, así que cada llamada real costaba más de lo que se cobraba.

## Decisiones

1. **El modelo de transcripción es un modelo más del catálogo**, con la modalidad `TRANSCRIPTION` y su propia tarifa
   versionada (ADR-0005). El proveedor de voz declara qué modelo usa (`RealtimeVoiceProvider.transcriptionModel()`).
2. **Se fija al iniciar la llamada**, junto con la tarifa del modelo realtime (RN-4). Sin tarifa vigente de
   transcripción, la llamada no se inicia (`AI_NOT_CONFIGURED`): no se cobra nada sin precio.
3. **Misma moneda.** La llamada se liquida en una sola moneda, así que la tarifa de transcripción debe estar en la
   misma que la del modelo realtime; si no, la llamada no se inicia.
4. **Registro aparte, liquidación única.** Cada transcripción se guarda como registro de uso propio, con el modelo y la
   tarifa de transcripción. Es idempotente por `transcription:<item_id>`. La liquidación ya suma todos los registros
   de la llamada y aplica el factor una sola vez (×1.30 alumno, ×1.00 administrador).
5. **Unidad por minuto exacta.** Las tarifas se aplican "por millón de unidades"; el audio por duración se mide en
   millonésimas de minuto (`audio_microminutes`). Así la tarifa capturada es el precio por minuto publicado y el
   cálculo es exacto, salvo el redondeo a la millonésima de minuto (60 µs por evento).
6. **El estimado por minuto y las reservas incluyen la transcripción.** Se asume que el alumno habla todo el minuto
   (`app.voice.estimate.transcribed-seconds-per-minute: 60`), en línea con `audio-in-per-minute`. El estimado no se
   queda corto; el cobro usa siempre lo medido.
7. **Uso sin tarifa fijada** (llamadas iniciadas antes de este cambio): no se inventa un precio; se abre la incidencia
   `TRANSCRIPTION_USAGE_UNPRICED` para conciliación.
8. **Unidades permitidas por modalidad** en el panel y en el servidor:
   - `TEXT`: texto;
   - `REALTIME`: texto y audio;
   - `TRANSCRIPTION`: `audio_microminutes` o tokens.

## Consecuencias

- (+) El costo real de una llamada queda cubierto y el alumno ve un estimado más cercano a lo que pagará.
- (+) El panel de tarifas permite registrar modelos de voz y de transcripción, antes solo de texto.
- (−) El estimado por minuto sube (con las tarifas simuladas, de $2.17 a $2.56 MXN), así que con el mismo saldo
  caben algo menos de minutos.
- (−) 🔍 La forma del `usage` de la transcripción está tomada de la referencia oficial, pero no se ha probado contra
  la API real. Verificar con la factura durante las primeras llamadas reales.
