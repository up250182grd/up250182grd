# ADR-0006: Aislamiento READ COMMITTED en MySQL

- **Estado:** Aceptado
- **Fecha:** 2026-11-10

## Contexto
Las pruebas E2E en paralelo produjeron un *deadlock* de InnoDB al registrar dos usuarios a la vez: bajo REPEATABLE READ (valor por defecto de MySQL), el `UPDATE ... WHERE user_id = ?` que invalida tokens pendientes toma *gap locks* sobre el mismo hueco del índice; después, ambos `INSERT` esperan al otro. El usuario recibía un error 500. Lo reproduce `ConcurrentRegistrationIT`.

## Decisión
- Todas las conexiones usan `READ COMMITTED` (`spring.datasource.hikari.transaction-isolation`). En este nivel InnoDB no toma *gap locks* en búsquedas normales.
- La consistencia no depende del nivel de aislamiento, sino de mecanismos explícitos: restricciones `UNIQUE` (idempotencia), `SELECT ... FOR UPDATE` en filas críticas (tokens, y en la Fase 4 cuentas del ledger y reservas) y versiones optimistas (`@Version`).
- Un usuario recién creado no ejecuta la invalidación de tokens (no tiene ninguno).

## Consecuencias
- (+) Menos bloqueos y *deadlocks* con concurrencia real.
- (−) Las lecturas repetidas dentro de una transacción pueden ver datos confirmados por otras. Por eso el ledger (Fase 4) bloqueará explícitamente las cuentas antes de calcular saldos.
