# ADR-0007: Profesor por texto, proveedor simulado y cobro por conversación

- **Estado:** Aceptado
- **Fecha:** 2026-11-11

## Decisiones

1. **Puerto `LlmClient` con dos adaptadores.** `FakeLlmClient` (simulado, sin red, tarifas ficticias en MXN, etiquetado en la interfaz) y `OpenAiChatClient` (Chat Completions con streaming e `include_usage`). El simulado es el valor por defecto para poder desarrollar y probar todo el flujo de cobro sin gastar dinero ni depender de la red.
2. **Turno en tres fases.** (a) Transacción: validar, reservar el **peor caso** del turno (lo ya medido + entrada estimada + `max-output-tokens`) y guardar el mensaje. (b) Llamada al proveedor **sin transacción abierta**, reenviando el texto por SSE. (c) Transacción: registrar el uso informado por el proveedor (idempotente por id de respuesta), la respuesta y las correcciones.
3. **Correcciones estructuradas** tras un marcador (`<<<CORRECTIONS>>>` + JSON). El servidor filtra el flujo para que el alumno nunca vea el marcador ni el JSON, y valida el JSON como datos (categorías cerradas, longitudes máximas).
4. **Cobro por conversación** (RN-12), al cerrarla o tras 30 min de inactividad, con la tarifa y la política fijadas al iniciarla. La reserva inicial es de $5.00 y se amplía por turno si hace falta.
5. **Si el proveedor falla**, no se registra uso ni se cobra ese turno (RN-36). Si el uso llega después de liquidar, se registra igualmente y se abre una incidencia de conciliación.
6. **Un turno a la vez por conversación**, controlado en memoria. Es suficiente con una instancia del backend; con varias instancias se necesitará un candado en la base de datos.

## Consecuencias

- (+) El cliente no puede alterar el consumo facturado; el cobro es reproducible a partir de `ai_usage_records`.
- (+) Se puede usar y probar toda la plataforma sin clave de API.
- (−) El adaptador real aún no se ha probado contra la API de OpenAI; hay que verificarlo antes de abrirlo a alumnos.
