# ADR-0011: Gamificación por eventos, PWA y accesibilidad verificada

- **Estado:** Aceptado
- **Fecha:** 2026-11-11
- **Relacionado:** RN-44, RN-45, RF-GAM-1..3, RNF-8, P-4 (riesgo de "farmear" XP)

## Decisiones

1. **Eventos de dominio en lugar de dependencias directas.**
   - Aprendizaje, diagnóstico, vocabulario, tutor y voz publican eventos en su paquete `api`: lección terminada,
     repaso, error superado, mensaje, conversación cerrada, llamada terminada y diagnóstico completado.
   - `gamification` los escucha con `@TransactionalEventListener`: **después del commit** y en una transacción
     nueva (`REQUIRES_NEW`).
   - Los errores se registran y no se propagan. Un fallo de gamificación nunca deshace una lección, un repaso o un
     cobro.
2. **Idempotencia.** Cada hecho se guarda en `game_events` con `UNIQUE(user, tipo, id de origen)`. Solo si el hecho
   es nuevo se suman XP, actividad del día e insignias, así que una entrega repetida no suma dos veces.
3. **XP** (`XpRules`, versión 1). Por actividades terminadas, nunca por clics ni por intentos sueltos.

   | Actividad | XP |
   |---|---|
   | Lección terminada | 20, decreciente al repetir la misma lección el mismo día: 20, 10, 5, 2… |
   | Lección dominada (bono) | 30 |
   | Repaso de vocabulario | 2 |
   | Error superado | 10 |
   | Diagnóstico | 30 |
   | Conversación (≥ 3 respuestas del profesor) o llamada (≥ 1 min) | 15 |

   El XP no modifica el nivel MCER.
4. **Racha** (`Streak`, versión 1).
   - Un día cuenta si se cumple una de estas condiciones: ≥ 5 min de actividad, 1 lección o 10 repasos.
   - Se permite 1 día de descanso por semana (lunes a domingo). El descanso no suma, y un segundo hueco en la misma
     semana rompe la racha.
   - Hoy, mientras no se cumpla, está "pendiente" y no rompe la racha.
   - El calendario de 4 semanas usa los mismos estados que el cálculo.
5. **Insignias.** Hay 9, todas por hitos de aprendizaje real: diagnóstico, lecciones terminadas y dominadas,
   palabras en repaso largo, errores superados, conversación y rachas de 7 y 30 días. Una vez ganadas no se pierden.
6. **PWA.**
   - Manifiesto con iconos normales y "maskable", y `start_url=/app`.
   - Service worker básico:
     - nunca guarda en caché `/api/`;
     - usa caché primero para `/assets/` (archivos con hash);
     - usa red primero para la navegación, con la última versión de la app como respaldo sin conexión.
   - Solo se registra en producción.
   - La instalabilidad se verificó con el criterio del propio Chrome (`Page.getInstallabilityErrors` por CDP, sin
     errores), porque Lighthouse 12 ya no tiene auditoría PWA.
7. **Accesibilidad.**
   - axe-core en Playwright sobre las páginas públicas, del alumno y de administración, en tema claro y oscuro, y
     sobre pantallas con contenido (lección, ejercicio, chat, llamada, práctica de errores).
   - La prueba falla con cualquier violación "serious" o "critical".
   - Las correcciones de contraste se hicieron en los tokens de diseño (`--color-*-ink`, `--color-on-danger`), no
     página por página.
8. **nginx.**
   - Las cabeceras de seguridad viven en un fragmento que se incluye en cada `location`. Un `add_header` dentro de un
     `location` anula los del `server`, y antes `index.html` salía sin CSP.
   - `proxy_buffering off` para que las respuestas en streaming (SSE) del tutor y de la voz lleguen al momento.

## Consecuencias
- (+) Un módulo nuevo puede sumar XP publicando un evento, sin tocar `gamification` por dentro.
- (+) El riesgo P-4 se mitiga con XP decreciente, XP separado del nivel y descanso sin castigo.
- (−) La actividad anterior a la Fase 8 no genera XP retroactivo.
- (−) Si el servidor se cae entre el commit y el oyente, se puede perder el XP de ese hecho. Es aceptable para
  gamificación, y la racha usa la misma tabla.
- (−) axe no sustituye la revisión manual con lector de pantalla y teclado, que sigue pendiente.
