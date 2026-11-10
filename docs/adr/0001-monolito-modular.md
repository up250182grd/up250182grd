# ADR-0001: Monolito modular con Spring Boot

- **Estado:** Aceptado
- **Fecha:** 2026-11-10

## Contexto
Proyecto de una persona, cuyo primer usuario es el propietario, con operaciones financieras que requieren consistencia transaccional (billetera, reservas, cargos).

## Decisión
Un único backend Spring Boot organizado por módulos de dominio (`identity`, `learning`, `assessment`, `vocabulary`, `recommendation`, `ai`, `tutor`, `voice`, `metering`, `wallet`, `payments`, `gamification`, `admin`, `audit`) con fronteras verificadas por pruebas de arquitectura. Una sola base MySQL.

## Consecuencias
- (+) Transacciones ACID entre medición y ledger; despliegue y depuración simples.
- (+) Un módulo puede extraerse más tarde si hay una razón medible.
- (−) Requiere disciplina para no acoplar módulos; se mitiga con pruebas ArchUnit/Modulith.
