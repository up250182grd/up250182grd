# ADR-0005: Tarifas y factores comerciales como datos versionados

- **Estado:** Aceptado
- **Fecha:** 2026-11-10

## Decisión
Las tarifas del proveedor (`provider_pricing_versions`) y los factores comerciales (`commercial_pricing_policies`) se guardan versionados, con vigencia, fuente y autor. Cada liquidación referencia las versiones usadas. Ningún precio se codifica en el código. Los cálculos usan `BigDecimal`, con redondeo HALF_UP a 2 decimales solo en los puntos financieros definidos en `docs/architecture/05-reglas-negocio.md` §5.2.

## Consecuencias
- (+) Los precios cambian sin despliegue y sin efectos retroactivos.
- (−) Hay que verificar y capturar manualmente las tarifas oficiales cuando cambien.
