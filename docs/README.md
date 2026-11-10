# EnglishOS — Documentación de diseño (Fase 1)

Plataforma de aprendizaje de inglés con profesor virtual de IA, conversación de voz en tiempo real, billetera en MXN y facturación por consumo real.

> **Estado:** Fase 1 — Análisis y arquitectura. **Todavía no hay código de la aplicación.**
> Todo lo que aparece aquí es diseño; nada se declara "implementado".

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
