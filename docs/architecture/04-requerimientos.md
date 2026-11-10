# 4. Requerimientos funcionales y no funcionales

Formato: `RF-<MÓDULO>-<n>` / `RNF-<n>`. **Prioridad:** M = MVP, P = posterior. Cada requisito incluye un **criterio de verificación**.

## 4.1 Requerimientos funcionales

### Identidad y cuentas (IDN)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-IDN-1 | Registro con correo, contraseña (≥ 12 caracteres, verificada contra lista de contraseñas comunes), nombre visible y aceptación de términos/aviso de privacidad (versión registrada). | M | Prueba de integración: registro válido crea `users` + `user_profiles` + `consents`; inválidos devuelven 400 con errores por campo. |
| RF-IDN-2 | Verificación de correo por enlace de un solo uso con caducidad (24 h). Sin verificar no se puede recargar saldo ni usar IA de pago. | M | Token usado/caducado → 410; endpoints de IA devuelven 403 `EMAIL_NOT_VERIFIED`. |
| RF-IDN-3 | Login/logout con sesión de servidor; bloqueo progresivo tras intentos fallidos. | M | 5 fallos → retraso/429; logout invalida la sesión en BD. |
| RF-IDN-4 | Recuperación de contraseña sin revelar si el correo existe. | M | Respuesta idéntica para correo existente e inexistente. |
| RF-IDN-5 | Roles `ADMIN`, `STUDENT` con permisos; el rol se evalúa en backend en cada solicitud. | M | Prueba de seguridad: alumno → 403 en `/api/v1/admin/**` aunque manipule el cliente. |
| RF-IDN-6 | Perfil: idioma de explicación (es/en), objetivos, minutos diarios, zona horaria. | M | CRUD con validación. |
| RF-IDN-7 | Exportar mis datos (JSON) y solicitar eliminación de cuenta (anonimización, conservando registros financieros exigidos por ley). | M | El export contiene perfil, progreso, conversaciones; el borrado anonimiza PII y conserva ledger. |
| RF-IDN-8 | Suspensión de cuenta por admin invalida sesiones activas y llamadas en curso. | M | Prueba: suspender → siguiente request 401, llamada se finaliza y liquida. |

### Diagnóstico (ASM)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-ASM-1 | Al primer ingreso, ofrecer diagnóstico (se puede posponer). | M | Flag `onboarding_completed`. |
| RF-ASM-2 | Selección adaptativa por escalera: empieza en A2, sube tras aciertos y baja tras errores por habilidad; termina por número máximo de ítems o convergencia. | M | Prueba unitaria del algoritmo con secuencias simuladas. |
| RF-ASM-3 | Resultado por habilidad (vocabulario, gramática, lectura, escritura) con nivel, confianza y evidencias (ítems). | M | Respuesta API incluye `skill`, `level`, `confidence`, `evidenceCount`. |
| RF-ASM-4 | Leyenda visible: "Nivel estimado, no es una certificación oficial del MCER". | M | Prueba de componente. |
| RF-ASM-5 | Generar plan personalizado (secuencia de módulos) a partir del resultado y objetivos. | M | Plan persistido y consultable. |
| RF-ASM-6 | Diagnóstico de comprensión auditiva y expresión oral. | P | — |

### Cursos y lecciones (CRS)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-CRS-1 | Jerarquía nivel → curso → módulo → lección → ejercicios, con orden. | M | Migraciones + API de lectura. |
| RF-CRS-2 | Lección con objetivo, prerrequisitos, explicación (Markdown saneado), ejemplos, vocabulario, ejercicios, criterio de dominio y regla de repaso. | M | Validación de esquema al publicar. |
| RF-CRS-3 | Estados de contenido `DRAFT`, `REVIEWED`, `PUBLISHED`, `ARCHIVED`; el alumno solo ve `PUBLISHED`. | M | Prueba de integración. |
| RF-CRS-4 | Admin crea/edita/ordena/desactiva contenido; cada cambio se audita. | M | Registro en `audit_logs`. |
| RF-CRS-5 | El alumno ve lo aprendido, lo en curso y lo pendiente. | M | Endpoint `/me/progress`. |

### Ejercicios (EXR)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-EXR-1 | Tipos MVP: `MULTIPLE_CHOICE`, `TRUE_FALSE`, `FILL_BLANK`, `WORD_ORDER`, `MATCHING`, `ERROR_SPOTTING`, `FREE_WRITING`. | M | Un evaluador por tipo con pruebas. |
| RF-EXR-2 | Evaluación determinista en servidor para tipos cerrados (normalización: mayúsculas, espacios, apóstrofos tipográficos, contracciones equivalentes listadas). | M | Pruebas unitarias con casos límite. |
| RF-EXR-3 | La respuesta correcta **no** se envía al cliente antes de responder. | M | Prueba: DTO de ejercicio no contiene `answer`. |
| RF-EXR-4 | Escritura libre evaluada por IA con rúbrica JSON (gramática, vocabulario, coherencia, tarea) y consumo medido. | M | Esquema JSON validado; si la IA falla, intento queda `PENDING_REVIEW`. |
| RF-EXR-5 | Registrar cada intento: respuesta, resultado, pistas usadas, tiempo. | M | Fila en `exercise_attempts`. |
| RF-EXR-6 | Retroalimentación explicativa y reintentos. | M | — |
| RF-EXR-7 | Escucha-y-responde, traducción contextual, diálogos, construcción de preguntas. | P | — |

### Tutor de texto (TUT)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-TUT-1 | Chat con modos libre, guiado (tema gramatical), roleplay (escenario). | M | — |
| RF-TUT-2 | Prompt de sistema versionado con metodología pedagógica, nivel del alumno, idioma de explicación y errores recurrentes. | M | `prompt_templates.version` guardado en cada conversación. |
| RF-TUT-3 | Respuesta en streaming (SSE). | M | — |
| RF-TUT-4 | Cada turno registra uso de tokens informado por el proveedor y se liquida (ver flujo). | M | Prueba de integración con proveedor simulado. |
| RF-TUT-5 | Memoria entre sesiones mediante resumen del alumno (no historial completo). | M | — |
| RF-TUT-6 | Correcciones estructuradas (original, corrección, explicación, categoría de error) almacenadas como `learner_errors`. | M | — |

### Voz en tiempo real (VOC)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-VOC-1 | Mostrar antes de llamar: modelo, costo estimado por minuto (rango), límite de sesión, saldo disponible, gasto del mes. | M | Endpoint `/voice/quote`. |
| RF-VOC-2 | Iniciar llamada: el backend valida saldo, crea reserva, negocia la sesión con el proveedor y devuelve SDP answer. | M | Integración con proveedor simulado. |
| RF-VOC-3 | Controles: llamar, colgar, mute, indicador de conexión, escuchando, profesor hablando, visualizador de audio, temporizador, consumo estimado. | M | E2E con micrófono falso. |
| RF-VOC-4 | VAD del proveedor; interrumpir al profesor cancela su respuesta y corta audio local. | M | Manual + E2E parcial. |
| RF-VOC-5 | Límite de duración y corte por reserva (90 %) con aviso a 1 min. | M | Prueba con eventos de uso simulados. |
| RF-VOC-6 | Desconexión: no se factura consumo inexistente; se liquida lo medido; oferta de nueva llamada. | M | Prueba de integración. |
| RF-VOC-7 | Transcripción en vivo (si el proveedor la entrega) y resumen académico posterior (temas, errores, vocabulario nuevo, siguientes pasos). | M | — |
| RF-VOC-8 | Una llamada activa por usuario. | M | Segundo intento → 409. |
| RF-VOC-9 | Cambiar idioma de explicación y modo de conversación antes de la llamada. | M | — |
| RF-VOC-10 | Modos académico, tecnológico y pronunciación. | P | — |

### Vocabulario y SRS (VOC-SRS → `SRS`)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-SRS-1 | Tarjeta: término, traducción, significado contextual, IPA, ejemplos, categoría, nivel. | M | — |
| RF-SRS-2 | Algoritmo SM-2 documentado; calificación 0–5; próxima fecha calculada en servidor. | M | Pruebas unitarias con tabla de casos. |
| RF-SRS-3 | Cola diaria priorizando vencidas y olvidadas. | M | — |
| RF-SRS-4 | `algorithm_version` por tarjeta para migrar sin perder historial. | M | — |

### Recomendaciones (REC)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-REC-1 | Motor de reglas (sin IA) que puntúa: repasos vencidos, lección siguiente en plan, habilidad más débil, error recurrente, tiempo disponible. | M | Pruebas unitarias con perfiles de alumno. |
| RF-REC-2 | Pesos configurables desde admin. | M | — |

### Billetera (WAL)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-WAL-1 | Saldo disponible, reservado e historial paginado. | M | — |
| RF-WAL-2 | Todo movimiento es un asiento de doble partida inmutable con clave de idempotencia única. | M | Prueba: misma clave dos veces → un solo asiento. |
| RF-WAL-3 | Reserva, captura parcial, liberación. | M | Pruebas unitarias e integración concurrente. |
| RF-WAL-4 | Sin saldo negativo salvo sobregiro técnico con tope configurable. | M | Prueba de concurrencia: 2 llamadas paralelas. |
| RF-WAL-5 | Límite de gasto mensual definido por el usuario y alerta de saldo bajo. | M | — |
| RF-WAL-6 | Solicitud de devolución (flujo manual con aprobación admin). | M | — |

### Pagos (PAY)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-PAY-1 | Crear orden de recarga (monto mínimo/máximo) y redirigir a checkout. | M | — |
| RF-PAY-2 | Acreditar solo tras webhook con firma válida **y** consulta del estado al proveedor = aprobado. | M | Pruebas: firma inválida → 401 sin crédito; retorno a "éxito" sin webhook → sin crédito. |
| RF-PAY-3 | Webhooks idempotentes por `provider_event_id` y por `payment_id`. | M | Webhook repetido → un solo crédito. |
| RF-PAY-4 | Modo prueba/producción por configuración. | M | — |
| RF-PAY-5 | Contracargos y reembolsos reflejados en ledger. | P | — |

### Medición (MTR)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-MTR-1 | Cada interacción con IA crea un `ai_usage_record` con proveedor, modelo, versión de tarifa, unidades por tipo, origen del dato (`PROVIDER_EVENT`, `PROVIDER_USAGE_API`, `ESTIMATE`). | M | — |
| RF-MTR-2 | Costo calculado en USD con `BigDecimal`, convertido a MXN con tipo de cambio registrado. | M | Pruebas unitarias. |
| RF-MTR-3 | Factor por rol al momento de liquidar (1.00 admin, 1.30 alumno), guardado en el cargo. | M | Pruebas unitarias e integración. |
| RF-MTR-4 | Estados `ESTIMATED`, `MEASURED`, `RECONCILED`, `PENDING_RECONCILIATION`. | M | — |
| RF-MTR-5 | Conciliación con datos del proveedor y ajustes vía asientos compensatorios (nunca editar). | M | — |

### Administración (ADM)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-ADM-1 | Usuarios: listar, buscar, filtrar, ver actividad, suspender, cambiar rol (con confirmación y auditoría). | M | — |
| RF-ADM-2 | Contenido académico (ver RF-CRS-4). | M | — |
| RF-ADM-3 | IA: proveedores, modelos habilitados, tarifas versionadas, límites de sesión, funciones costosas on/off. | M | — |
| RF-ADM-4 | Finanzas: ingresos, costos de proveedor, recargos, comisiones, IVA, utilidad neta estimada, pagos fallidos, reservas abiertas, conciliaciones pendientes; exportar CSV. | M | — |
| RF-ADM-5 | "Mi consumo": sesiones del admin al costo directo. | M | — |
| RF-ADM-6 | Factor comercial editable solo con permiso `pricing:write`, con vigencia futura y auditoría; no afecta operaciones liquidadas. | M | Prueba: cambio de factor no altera cargos previos. |
| RF-ADM-7 | Auditoría consultable y no editable. | M | — |

### Gamificación (GAM)
| ID | Requisito | P | Verificación |
|---|---|---|---|
| RF-GAM-1 | XP por actividades completadas (no por clics). | M | — |
| RF-GAM-2 | Racha con 1 día de descanso por semana sin perderla. | M | Prueba unitaria. |
| RF-GAM-3 | Insignias por hitos de aprendizaje real. | M | — |
| RF-GAM-4 | Misiones diarias, objetivos semanales, retos. | P | — |

## 4.2 Requerimientos no funcionales

| ID | Categoría | Requisito | Verificación |
|---|---|---|---|
| RNF-1 | Exactitud financiera | Cero uso de `float/double` para dinero; `DECIMAL` en BD. | Regla ArchUnit/Checkstyle + revisión. |
| RNF-2 | Integridad | Suma de líneas de cada asiento = 0; saldo cacheado = suma del ledger. | Prueba de invariantes + job nocturno de verificación. |
| RNF-3 | Idempotencia | Toda operación financiera acepta clave de idempotencia única. | Pruebas de reintento. |
| RNF-4 | Seguridad | OWASP ASVS nivel 2 como guía; dependencias sin CVE críticos. | OWASP Dependency-Check / Dependabot en CI. |
| RNF-5 | Latencia voz | Establecer llamada < 3 s p95 (sin contar permisos); el audio no pasa por el backend. | Métrica `voice.setup.duration`. |
| RNF-6 | Latencia API | Endpoints de lectura < 300 ms p95 en carga nominal. | Métricas Micrometer. |
| RNF-7 | Disponibilidad | Objetivo 99.5 % mensual tras lanzamiento comercial. | Monitoreo externo. |
| RNF-8 | Accesibilidad | WCAG 2.2 AA como objetivo: contraste, foco visible, navegación con teclado, `aria-live` en estados de llamada. | axe-core en Playwright + revisión manual. |
| RNF-9 | Responsive | Funcional desde 360 px de ancho. | Playwright en viewports móvil/escritorio. |
| RNF-10 | Compatibilidad | Últimas 2 versiones de Chrome, Edge, Firefox, Safari (macOS/iOS 17+). | Matriz de pruebas manuales de voz. |
| RNF-11 | Privacidad | Sin audio almacenado por defecto; retención de transcripciones configurable (🔶 180 días). | Prueba de job de retención. |
| RNF-12 | Observabilidad | Logs JSON con `traceId`, sin PII ni secretos; métricas de costo por proveedor. | Revisión + prueba de enmascarado. |
| RNF-13 | Mantenibilidad | Fronteras de módulos verificadas automáticamente; cobertura ≥ 90 % en `metering`, `wallet`, evaluadores; ≥ 70 % global. | JaCoCo en CI. |
| RNF-14 | Reproducibilidad | `docker compose up` levanta el entorno local; README con pasos. | Probado en CI. |
| RNF-15 | Backups | Respaldo diario de MySQL con restauración probada mensual. | Runbook + prueba documentada. |
| RNF-16 | Rate limiting | Login, registro, IA y webhooks con límites por IP/usuario. | Pruebas de 429. |
