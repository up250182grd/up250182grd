# ADR-0010: Recargas con pasarela (Mercado Pago, modo prueba)

- **Estado:** Aceptado. El contrato del proveedor está pendiente de verificar (🔍).
- **Fecha:** 2026-11-11
- **Relacionado:** ADR-0003 (libro de doble partida), RN-19..RN-26

## Contexto
Las recargas reales mueven dinero. Los riesgos clásicos son:
- acreditar con el regreso del navegador;
- aceptar notificaciones falsificadas;
- acreditar dos veces un evento repetido;
- aceptar montos manipulados.

No fue posible leer la documentación vigente de Mercado Pago desde el entorno de desarrollo, porque sus dominios
están bloqueados en la red de la sesión.

## Decisión
1. **Intención de recarga.**
   - El servidor fija el monto y la moneda (MXN) y crea la preferencia de Checkout Pro con
     `external_reference = id de la intención`.
   - El navegador solo es redirigido a la pasarela; jamás ve el token de acceso.
2. **El regreso no acredita.** `/app/wallet/topups/{id}` solo consulta el estado.
3. **Notificación = aviso, no fuente de verdad.**
   - La firma `x-signature` (HMAC-SHA256 de `id:…;request-id:…;ts:…;`) autentica al remitente.
   - Con firma inválida, la notificación se registra y se responde 401, sin consultar ni acreditar.
   - Con firma válida, el servidor **consulta el pago en la API** con su token y acredita solo si:
     - el pago está aprobado;
     - el monto es **exactamente** el de la intención;
     - la moneda es MXN.
   - Si no coinciden, la intención queda en revisión con una incidencia y **sin crédito**.
4. **Idempotencia en tres niveles.** Evita el crédito doble aunque los eventos lleguen repetidos o en paralelo.
   - Bloqueo de la fila de la intención.
   - Clave del libro `topup:gw:MERCADOPAGO:{paymentId}` (única).
   - `UNIQUE(provider, provider_payment_id)`.
5. **Conciliación.** Una tarea cada 15 minutos (y un botón para el administrador) busca los pagos por referencia
   cuando la notificación se perdió.
6. **Devoluciones manuales.**
   - Primero se retira el saldo **no usado**; el saldo nunca queda negativo.
   - Después se pide la devolución a la pasarela. Si la rechaza, el saldo se repone y se abre una incidencia.
   - Si la pasarela confirma pero falla el registro local, no se repone (sería pagar dos veces) y se abre una
     incidencia `REFUND_STATE_UNKNOWN`.
7. **Contabilidad.** Las recargas cobradas se registran contra la cuenta `GATEWAY_CLEARING` (dinero por recibir de la
   pasarela). Las comisiones de la pasarela todavía no se registran.
8. **Reportes CSV** (liquidaciones, libro mayor, recargas) con BOM UTF-8 y protección contra inyección de fórmulas. La
   prueba de integración comprueba que cada asiento del CSV suma cero y que los totales de liquidaciones cuadran con
   el resumen.

## Consecuencias
- (+) Ni el navegador ni el cuerpo de la notificación pueden fijar el monto acreditado.
- (+) El adaptador real se prueba por HTTP contra un servidor que imita la API (`FakeMercadoPago`).
- (−) **Pendiente de verificar contra la documentación oficial antes de usar credenciales de prueba:**
  - el formato exacto de la firma (incluido el uso de minúsculas en `data.id`);
  - los nombres de los campos (`sandbox_init_point`, `transaction_amount`, `currency_id`);
  - la ruta de búsqueda;
  - la cabecera `X-Idempotency-Key`.
- (−) Sin ventana de tiempo para `ts`: una notificación reenviada no causa daño, porque el procesamiento es
  idempotente y siempre consulta la API.
