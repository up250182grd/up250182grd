# ADR-0003: Billetera como libro mayor de doble partida inmutable

- **Estado:** Aceptado
- **Fecha:** 2026-11-10

## Contexto
El saldo debe ser auditable, resistente a reintentos y a concurrencia, y separar el ingreso por costo recuperado, el recargo, los impuestos y el gasto interno del administrador.

## Decisión
Tablas `ledger_accounts`, `ledger_transactions`, `ledger_entries`. Cada asiento tiene líneas que suman 0 y una `idempotency_key` única, y nunca se edita: las correcciones son asientos compensatorios. `ledger_accounts.balance` es una caché que se actualiza en la misma transacción con bloqueo y que un job verifica.

## Consecuencias
- (+) Trazabilidad completa; los reportes financieros se derivan del libro.
- (−) Más complejo que un campo `saldo`; lo justifican los requisitos.
