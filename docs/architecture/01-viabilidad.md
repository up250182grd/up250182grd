# 1. Análisis de viabilidad

Conclusión general: **el proyecto es viable** como monolito modular desarrollado por una persona con asistencia de IA, siempre que el MVP se limite al flujo académico principal + una conversación de voz real con medición comprobable. Los mayores riesgos no son de programación sino de **contenido educativo**, **atribución exacta del costo de voz** y **cumplimiento fiscal/legal del saldo prepagado**.

---

## 1.1 Dificultades técnicas

| ID | Dificultad | Impacto | Mitigación |
|---|---|---|---|
| T-1 | **El consumo de la sesión Realtime llega al navegador**, no al servidor. En WebRTC, los eventos (incluido `response.done` con `usage`) viajan por el *data channel* del cliente. Un cliente malicioso puede alterar o no enviar esos datos. | Crítico (facturación) | El backend **nunca** acepta consumo enviado por el frontend. Opciones (🔍 verificar con la documentación vigente de OpenAI Realtime): **(a)** el backend negocia el SDP contra el endpoint de llamadas del proveedor con la clave permanente, obtiene el `call_id`, y abre una **conexión lateral ("sideband") WebSocket** del servidor a la misma sesión para recibir los eventos de uso; **(b)** si (a) no existe, conciliar con la **API de Uso/Costos** del proveedor por proyecto/clave dedicada y día. El diseño implementa (a) y deja (b) como conciliación de respaldo. |
| T-2 | Los minutos de llamada ≠ costo. El proveedor factura por **tokens de audio/texto de entrada y salida, y tokens en caché**; el contexto crece durante la llamada, por lo que el costo por minuto **aumenta** con la duración. | Alto | Medición por tokens reportados; estimación previa basada en un perfil de consumo calibrado con sesiones reales del administrador; límite de duración y truncado de contexto configurables. |
| T-3 | Audio en navegadores móviles, sobre todo **iOS Safari**: requiere gesto del usuario para reproducir audio, la sesión de audio se interrumpe al bloquear pantalla o al recibir llamadas, y las PWA en modo standalone tienen particularidades de permisos. | Medio | Iniciar `AudioContext` y `getUserMedia` dentro del click de "Llamar"; manejar `visibilitychange`/`pagehide`; pruebas manuales en iOS reales antes de declarar terminado. |
| T-4 | Reconexión de WebRTC: una sesión Realtime perdida no se "reanuda" exactamente; se crea otra. | Medio | Política: tras desconexión, el backend cierra la sesión del proveedor, liquida lo consumido y ofrece **nueva llamada** con resumen del contexto previo (no se cobra tiempo inexistente). |
| T-5 | Concurrencia en la billetera (dos llamadas simultáneas, reintentos, webhooks duplicados). | Alto | Libro mayor de doble partida, claves de idempotencia únicas en BD, bloqueo optimista/pesimista por billetera, una sola llamada de voz activa por usuario. |
| T-6 | Precisión monetaria (USD → MXN, tokens de fracciones de centavo). | Alto | `BigDecimal` en Java, `DECIMAL` en MySQL, reglas de redondeo explícitas (ver [Reglas de negocio](05-reglas-negocio.md)). |
| T-7 | Prompt injection: el alumno puede intentar que el tutor ignore instrucciones, revele el *system prompt* o genere contenido fuera de propósito; también texto de lecciones o transcripciones podría contener instrucciones. | Medio | Las decisiones de negocio (cobro, permisos, nivel oficial) **nunca** las toma el modelo; el modelo solo produce texto/audio. Delimitadores de contenido no confiable, límites de tokens, moderación y registros. |
| T-8 | Evaluación de pronunciación real requiere análisis acústico (p. ej. un servicio de *pronunciation assessment*). La transcripción de un modelo no mide fonética. | Medio | MVP: retroalimentación **basada en transcripción** y etiquetada como tal. Evaluación acústica en versión posterior. |

## 1.2 Dificultades económicas

| ID | Dificultad | Mitigación |
|---|---|---|
| E-1 | Las tarifas de voz en tiempo real son significativamente más altas que las de texto. Una llamada larga puede costar varias veces lo que una sesión de texto equivalente. 🔍 Las tarifas vigentes **deben consultarse en la página oficial de precios del proveedor** al implementar; no se codifican en el código, se cargan como `provider_pricing_versions`. | Estimador visible antes de llamar, límites por sesión y por mes, modelos "mini" para práctica cotidiana, truncado de contexto. |
| E-2 | Tipo de cambio USD/MXN variable; el proveedor cobra en USD. | Registrar tipo de cambio usado, su fuente (🔶 FIX publicado por Banxico, 🔍 verificar API SIE y sus términos) y fecha en cada liquidación. |
| E-3 | Comisiones de pasarela (porcentaje + fijo + IVA) en recargas pequeñas pueden comerse el 30 % de recargo. | Monto mínimo de recarga configurable (🔶 $100 MXN); reporte de utilidad neta que descuenta comisiones reales. |
| E-4 | El 30 % es **markup** sobre costo, no margen neto: tras IVA, comisión e infraestructura la utilidad neta puede ser baja o negativa con poco volumen. | Panel de rentabilidad que separa ingreso bruto y utilidad neta estimada. |
| E-5 | Uso del administrador al costo genera gasto real sin ingreso. | Se registra como **gasto interno**, separado de clientes; tope mensual configurable para el propio administrador. |

## 1.3 Dificultades educativas

| ID | Dificultad | Mitigación |
|---|---|---|
| P-1 | **Contenido**: un curso A1–C2 serio requiere cientos de lecciones y miles de ítems revisados. Es el mayor costo oculto del proyecto. | MVP con un currículo acotado (A1–B1, ~3 módulos por nivel) y banco de ítems del diagnóstico revisado manualmente. La IA puede **proponer borradores**, pero se publican solo tras revisión (`status = DRAFT → REVIEWED → PUBLISHED`). |
| P-2 | Diagnóstico confiable: un test corto produce un nivel **estimado**, no certificado. | Algoritmo adaptativo por escalera con evidencias por habilidad; mensaje explícito "no es una certificación oficial del MCER". |
| P-3 | Medir dominio sin depender del juicio del LLM. | Ejercicios deterministas con reglas; dominio = combinación de exactitud, número de intentos espaciados en el tiempo y retención (ver [reglas](05-reglas-negocio.md#53-reglas-académicas)). |
| P-4 | La gamificación puede incentivar "farmear" XP en lugar de aprender. | XP desacoplado del nivel lingüístico; rachas con días de descanso permitidos. |

## 1.4 Dificultades de seguridad, privacidad y legales

| ID | Riesgo | Mitigación |
|---|---|---|
| S-1 | Robo/abuso de credenciales de IA que genera gasto ilimitado. | Claves solo en servidor, credenciales efímeras o SDP negociado por backend, límites duros por usuario/día/mes y por plataforma, alertas. |
| S-2 | Fraude en pagos (webhooks falsos, retorno a página de éxito sin pagar). | Webhooks firmados + consulta servidor-a-servidor del estado del pago antes de acreditar. |
| S-3 | Datos personales y conversaciones. ⚖️ Ley Federal de Protección de Datos Personales en Posesión de los Particulares (nueva ley publicada en 2025; 🔍 verificar texto y reglamento vigentes). | Aviso de privacidad, derechos ARCO (acceso/eliminación), minimización, **sin grabación de audio por defecto**, retención configurable de transcripciones. |
| S-4 | ⚖️ **Saldo prepagado**: en México, almacenar fondos de clientes puede rozar la regulación de fondos de pago electrónico (Ley Fintech). | Diseñar el saldo como **créditos de servicio no transferibles y no retirables en efectivo**, solo consumibles en EnglishOS. Confirmar con abogado antes de cobrar a terceros. |
| S-5 | ⚖️ Facturación electrónica (CFDI 4.0, SAT) para ingresos de clientes y obligaciones de protección al consumidor (PROFECO: precio total, términos claros, devoluciones). | Integración CFDI fuera del MVP; registrar datos fiscales opcionales; revisión de contador. |
| S-6 | 🔍 Términos del proveedor de IA (edad mínima, usos permitidos, retención de datos, divulgación de que la voz es sintética). | Revisar políticas vigentes; indicar en la UI que el profesor es una IA. |

## 1.5 Contradicciones o tensiones detectadas en los requisitos

1. **"Control de consumo en tiempo real" vs. "no confiar en el frontend"**: en WebRTC el uso llega al cliente. → Resuelto con conexión lateral de servidor (T-1). Si el proveedor no lo permitiera, el control en vivo sería solo **estimado** (temporizador) y el cobro final se concilia.
2. **"Reservar saldo" vs. "no generar saldo negativo"**: el consumo real puede exceder la reserva si el corte no es instantáneo. → El backend corta la sesión al alcanzar el 90 % (🔶 configurable) de la reserva; cualquier excedente pequeño se registra como **sobregiro técnico autorizado** con tope, y se notifica. Nunca se deja de registrar consumo real.
3. **"Administrador no paga" vs. "billetera única para todos"**: → El administrador usa el mismo medidor pero su liquidación va a una cuenta contable de **gasto interno**, no a su billetera; no requiere recargar.
4. **"Costo real" vs. "costo confirmado tarde"**: el proveedor puede no dar el costo final al instante. → Estados `ESTIMATED → MEASURED → RECONCILED`; nunca se presenta una estimación como definitiva.
5. **"Precio = costo × 1.30" vs. impuestos**: el IVA no forma parte del costo ni del recargo; se calcula aparte (D-1).
6. **"Pruebas de reproducción de audio y permisos de micrófono"**: no son totalmente automatizables; se usará Playwright con dispositivos falsos de Chromium + **lista de verificación manual** en iOS/Android reales.
