# ADR-0002: Sesiones de servidor con cookie HttpOnly en lugar de JWT

- **Estado:** Aceptado
- **Fecha:** 2026-11-10

## Contexto
La SPA y la API se sirven desde el mismo origen en producción. Se necesita revocar el acceso al suspender cuentas o cambiar roles, y proteger el gasto de IA.

## Decisión
Spring Session JDBC + cookie `HttpOnly; Secure; SameSite=Lax` + protección CSRF de Spring Security para SPA. Contraseñas con Argon2id.

## Consecuencias
- (+) Revocación inmediata; sin tokens en `localStorage` (menor impacto de un XSS).
- (−) Requiere CSRF y estado en BD (aceptable a esta escala).
