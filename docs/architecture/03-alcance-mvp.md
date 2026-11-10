# 3. Alcance del MVP

Objetivo del MVP: **demostrar los 20 criterios de éxito** con el menor sistema posible, priorizando el flujo académico principal y **una conversación de voz real con medición comprobable del consumo**.

## 3.1 Dentro del MVP (v0.1 → v1.0)

| Área | Incluido |
|---|---|
| Cuentas | Registro con correo + contraseña, verificación de correo, login/logout, recuperación de contraseña, roles `ADMIN` y `STUDENT`, perfil (idioma de explicación, objetivo, minutos/día). |
| Diagnóstico | Test adaptativo de **vocabulario, gramática y lectura** (deterministas) + **una muestra de escritura** evaluada por IA con rúbrica. Resultado por habilidad A1–C2 con nivel de confianza. |
| Currículo | Niveles A1, A2, B1 con contenido semilla revisado (≥ 3 módulos por nivel, ≥ 4 lecciones por módulo en el nivel inicial del administrador). Editor admin básico (CRUD, ordenar, publicar/despublicar). |
| Ejercicios | Opción múltiple, verdadero/falso, completar, ordenar palabras, relacionar, identificación de errores, escritura libre (IA). |
| Progreso | Intentos, dominio por lección y habilidad, lección recomendada. |
| Vocabulario | Tarjetas con SM-2 (repetición espaciada), cola diaria de repaso. |
| Tutor de texto | Chat con modos libre/guiado/roleplay; correcciones; memoria resumida entre sesiones; medición de tokens. |
| Voz Realtime | Llamada WebRTC con VAD, interrupciones, silencio/mute, temporizador, consumo estimado en vivo, límite de duración, corte por reserva, transcripción, resumen académico posterior. Modos: libre, guiado, roleplay. |
| Medición | Uso por sesión, versiones de precios, tipo de cambio, factor por rol (1.00/1.30), estados estimado/medido/conciliado. |
| Billetera | Ledger de doble partida, recarga, reserva, cargo, liberación, historial, límite de gasto mensual, alerta de saldo bajo. |
| Pagos | Una pasarela (Mercado Pago) en **modo de prueba**; webhooks firmados e idempotentes. Producción solo tras revisión legal/fiscal. |
| Admin | Usuarios (buscar, suspender, rol), contenido, precios/modelos/límites, consumo y costos, ingresos brutos vs. utilidad neta estimada, "Mi consumo", auditoría, exportación CSV. |
| Gamificación mínima | XP, racha con "día de descanso", 5–10 insignias. |
| UX | Responsive mobile-first, modo claro/oscuro, PWA instalable (manifest + SW básico), objetivo WCAG 2.2 AA. |
| Calidad | Pruebas unitarias e integración de dinero/medición/ejercicios; E2E del flujo principal; CI. |

## 3.2 Fuera del MVP (versiones posteriores)

| Versión | Funcionalidad |
|---|---|
| v1.1 | Comprensión auditiva con audio TTS pregenerado; modos "preparación académica" e "inglés tecnológico" con currículo propio; misiones diarias/semanales. |
| v1.2 | Evaluación acústica de pronunciación (servicio especializado 🔍); diagnóstico oral. |
| v1.3 | Algoritmo FSRS para repetición espaciada (migración de datos SM-2 → FSRS). |
| v1.4 | Segunda pasarela (Stripe), reembolsos automáticos, facturación CFDI. |
| v2 | Motor adaptativo estadístico (p. ej. modelos de conocimiento bayesianos/IRT), notificaciones push, app instalable en tiendas, multi-moneda, planes de suscripción, equipos/escuelas. |

## 3.3 Criterio para "MVP terminado"

Los 20 criterios de éxito de la sección 25 del documento maestro se demuestran con:
- pruebas automatizadas en verde en CI (unitarias + integración + E2E del flujo principal), **y**
- una lista de verificación manual firmada para voz en Chrome escritorio, Android Chrome e iOS Safari, **y**
- una conciliación real de al menos 10 sesiones del administrador contra el panel de uso del proveedor con diferencia documentada.
