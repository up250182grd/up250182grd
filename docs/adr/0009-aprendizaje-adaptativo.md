# ADR-0009: Aprendizaje adaptativo — SM-2, práctica de errores y recomendador por reglas

- **Estado:** Aceptado
- **Fecha:** 2026-11-11
- **Relacionado:** RN-43, RF-SRS-1..4, RF-REC-1..2, ADR-0001 (fronteras de módulos)

## Contexto
La fase 6 debe convertir la actividad real del alumno en su siguiente paso sin depender de IA (sin costo ni
latencia) y sin inventar datos en el tablero.

## Decisiones

1. **SM-2 exacto y sin coma flotante** (`vocabulary.domain.Sm2`, versión 1). El factor de facilidad es `BigDecimal`
   con 3 decimales. Interpretación documentada de RN-43:
   - con q < 3 el factor **no cambia**, como en la descripción original de SuperMemo;
   - el intervalo n ≥ 3 usa el factor **anterior**, redondeado al día (mitades hacia arriba).

   Cada tarjeta guarda `algorithm` y `algorithm_version`. Cada repaso guarda el estado antes y después en JSON, lo
   que permite migrar a FSRS (v1.3) sin perder historial.
2. **Fechas por día local.** Una tarjeta vence al **inicio del día local** del alumno + intervalo, no a la misma hora
   del repaso.
3. **Cola diaria** (`ReviewQueue`):
   - primero las olvidadas;
   - después las vencidas, de la más atrasada a la menos;
   - por último las nuevas, hasta 10 por día (configurable).
4. **Las tarjetas salen de las lecciones.** Se crean a partir del vocabulario de las lecciones completadas al menos una
   vez. La sincronización es idempotente (`INSERT IGNORE`), así no hace falta un evento entre módulos y los alumnos
   anteriores a la fase 6 también reciben sus palabras.
5. **Repasos idempotentes.** El cliente manda un `reviewId`; un reintento devuelve el resultado original sin aplicar
   SM-2 otra vez. El cliente nunca envía fechas ni intervalos: los calcula el servidor.
6. **Errores → práctica.**
   - Las correcciones del profesor IA se vuelven ejercicios de "escríbelo bien", evaluados de forma determinista con
     el mismo normalizador que los ejercicios.
   - Un error se da por superado con 2 aciertos seguidos.
   - La cola prioriza las categorías con más errores abiertos, es decir, los recurrentes.
7. **Recomendador por reglas** (`RecommendationEngine`, versión 1).
   - Cada regla produce un factor de 0 a 1000; la puntuación es peso × factor / 1000, en aritmética entera.
   - Los pesos (0–100) se versionan en `recommendation_weights`; solo los cambia quien tiene `recommendation:write`.
     Cada cambio exige un motivo y queda auditado.
   - Al cumplir la meta diaria, todo baja a la mitad salvo los repasos vencidos.
8. **Progreso con datos reales.** Los minutos de estudio son una **estimación** (duración de cada evento, o un valor
   típico, con tope de 5 min por evento) y la interfaz lo dice.
9. **Fronteras.** Una prueba de ArchUnit exige que los módulos solo se usen entre sí por su paquete `api`. Otra impide
   nombres simples repetidos en los tipos expuestos, porque OpenAPI los sobrescribía en silencio. Este último caso ya
   había ocurrido con `StartRequest` del tutor y de la voz, y se corrigió.

## Consecuencias
- (+) Determinista, probado con tablas de casos, sin costo y explicable al alumno ("por qué te lo recomendamos").
- (+) Los pesos se ajustan sin desplegar código.
- (−) SM-2 es más simple que FSRS; la migración queda prevista por versión de algoritmo.
- (−) Los minutos son aproximados hasta tener un cronómetro de sesión.
