# ADR-0004: La medición de voz Realtime se obtiene del lado del servidor

- **Estado:** Aceptado (sujeto a verificar la API del proveedor al inicio de la Fase 5)
- **Fecha:** 2026-11-10

## Contexto
Con WebRTC, el audio y los eventos de la sesión llegan al navegador. El cobro no puede depender de datos del cliente.

## Decisión
El backend negocia el SDP con el proveedor usando la clave permanente, obtiene el identificador de la llamada y abre un canal lateral de servidor para recibir los eventos de uso. Los datos de uso del cliente solo se usan como estimación visual.

Plan B si el proveedor no ofrece canal lateral: clave/proyecto dedicado y conciliación con la API de uso; el cobro final queda `PENDING_RECONCILIATION` hasta tener datos del proveedor.

## Consecuencias
- (+) El cliente no puede manipular el consumo facturado.
- (−) Dependencia de una capacidad concreta del proveedor, aislada detrás de `RealtimeVoiceProvider`.
