# EnglishOS — Documentación de diseño (Fase 1)

Plataforma de aprendizaje de inglés con profesor virtual de IA, conversación de voz en tiempo real, billetera en MXN y facturación por consumo real.

> **Estado:** Fases 1 a 9 completas: el MVP está implementado y probado con proveedores simulados.
> Qué falta para darlo por terminado con proveedores reales: [criterios-de-exito.md](criterios-de-exito.md).
> Operación: [despliegue](operacion/despliegue.md) · [respaldos](operacion/respaldos.md) · [incidentes](operacion/incidentes.md) · Seguridad: [ASVS](seguridad/asvs-l2.md).
> Instalación, ejecución, pruebas y estado detallado: [DESARROLLO.md](DESARROLLO.md).
> Lo que no figure como implementado en DESARROLLO.md es diseño, no funcionalidad existente.

## Convenciones de este documento

| Marca | Significado |
|---|---|
| ✅ **Confirmado** | Requisito explícito del propietario o decisión tomada en este documento. |
| 🔶 **Supuesto** | Decisión razonable tomada por el equipo; puede cambiarse sin romper la arquitectura. |
| 🔍 **Verificar** | Depende de documentación de terceros, tarifas, leyes o condiciones comerciales que deben confirmarse con fuentes oficiales **en el momento de implementar**. |
| ⚖️ **Revisión legal/fiscal** | Requiere opinión de abogado o contador en México antes del lanzamiento comercial. |

## Índice

1. [Análisis de viabilidad](architecture/01-viabilidad.md)
2. [Arquitectura recomendada](architecture/02-arquitectura.md)
3. [Alcance del MVP](architecture/03-alcance-mvp.md)
4. [Requerimientos funcionales y no funcionales](architecture/04-requerimientos.md)
5. [Reglas de negocio](architecture/05-reglas-negocio.md)
6. [Modelo de base de datos](architecture/06-modelo-datos.md)
7. [Flujos críticos](architecture/07-flujos-criticos.md)
8. [Diseño inicial de pantallas y UX](architecture/08-pantallas-ux.md)
9. [Estructura del repositorio](architecture/09-estructura-repositorio.md)
10. [Plan de desarrollo](architecture/10-plan-desarrollo.md)

Registro de decisiones de arquitectura (ADR): [`adr/`](adr/).

Contrato de la API (generado por el backend): [`api/openapi.json`](api/openapi.json).

### Versiones fijadas en la Fase 2 (verificadas en los repositorios oficiales el 2026-11-10)

| Componente | Versión | Nota |
|---|---|---|
| Spring Boot | 4.1.2 | El diseño decía "4.0.x"; se usa la última estable. |
| Java | 21 | |
| MySQL | 8.4 | |
| Flyway / Hibernate | 12.4 / 7.4 | Gestionadas por Spring Boot. |
| React / Vite / Tailwind | 19.3 / 8.3 / 4.3 | |
| TypeScript | 5.9.3 | La 7.x aún no es compatible con typescript-eslint ni con openapi-typescript. |
| React Router | 7.18 | Se mantiene la línea 7 (la 8 es muy reciente). |
| TanStack Query / Zod | 5.107 / 4.6 | |

## Decisiones pendientes del propietario

Estas decisiones **no se pueden inferir** y afectan dinero o cumplimiento legal. El diseño soporta cualquiera de las opciones, pero se debe elegir antes de la Fase 7:

| # | Decisión | Opción por defecto propuesta |
|---|---|---|
| D-1 | ¿El IVA se cobra al **recargar** saldo o al **consumir**? ¿Los precios mostrados incluyen IVA? | Precios mostrados con IVA incluido (PROFECO exige precio total); tratamiento fiscal exacto ⚖️ con contador. |
| D-2 | ¿Quién absorbe la comisión de la pasarela? | La absorbe el negocio (reduce la utilidad neta, no se traslada como cargo oculto). |
| D-3 | ¿Se permiten devoluciones de saldo no consumido? | Sí, por política escrita, al mismo medio de pago, descontando nada que no se haya informado previamente. ⚖️ |
| D-4 | Proveedor de pagos | Mercado Pago (Checkout Pro) como primero; Stripe como adaptador alterno. 🔍 |
| D-5 | Edad mínima de usuarios | 18+ en el lanzamiento comercial (evita consentimiento parental y requisitos del proveedor de IA para menores). 🔍⚖️ |
| D-6 | Repositorio | Este repositorio es el **repositorio de perfil de GitHub** (`usuario/usuario`); su `README.md` aparece en tu perfil. Se recomienda crear un repositorio dedicado `englishos` antes de la Fase 2. |
