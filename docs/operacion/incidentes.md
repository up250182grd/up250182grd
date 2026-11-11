# Manual de incidentes

Cada procedimiento indica qué revisar, qué hacer y qué **no** hacer. Ante la duda, protege primero el dinero de los
alumnos y los datos personales.

## "El libro contable no cuadra" (aviso rojo en Finanzas)

1. **No hagas ajustes manuales en la base de datos.** El aviso viene de `verifyLedger`: la suma global de las líneas
   es distinta de 0 o el saldo guardado de una cuenta difiere de la suma de sus líneas.
2. Descarga el CSV del libro mayor del mes: *Finanzas → Reportes → Libro mayor*. Busca un asiento cuya suma no sea 0.
3. Si el problema es solo el saldo guardado de una cuenta, recalcúlalo desde las líneas, que son la fuente de verdad,
   con una migración o un script revisado y auditado.
4. Si falta o sobra una línea, restaura en una base aparte el respaldo del día anterior y compara.

## Incidencias de cobro (*Finanzas → Incidencias abiertas*)

| Tipo | Qué significa | Qué hacer |
|---|---|---|
| `AMOUNT_MISMATCH` | Un pago aprobado no coincide en monto o moneda con la recarga; no se acreditó. | Revisa el pago en Mercado Pago. Si es legítimo, devuelve el pago en la pasarela y pide al alumno que recargue de nuevo. **No acredites a mano.** |
| `DUPLICATE_PAYMENT` | Llegó un segundo pago para una recarga ya acreditada. | Devuelve el segundo pago desde el panel de Mercado Pago. |
| `UNKNOWN_PAYMENT` | Pago sin recarga asociada en EnglishOS. | Busca la referencia en Mercado Pago; si no corresponde a ningún alumno, devuélvelo. |
| `GATEWAY_REVERSAL` | La pasarela informa una devolución o un contracargo de un pago acreditado. | Contacta al alumno y decide si se ajusta su saldo (un ajuste es un asiento nuevo, auditado). |
| `REFUND_FAILED` | La pasarela rechazó una devolución; el saldo ya se repuso. | Revisa el motivo en Mercado Pago y reintenta. |
| `REFUND_STATE_UNKNOWN` | La pasarela confirmó la devolución pero no se pudo registrar aquí. | Verifica en Mercado Pago. **No vuelvas a devolver.** Registra el resultado al resolver la incidencia. |
| `OVERDRAFT_ABOVE_LIMIT` | Un cobro superó el sobregiro técnico permitido. | Revisa la sesión (consumo medido contra reserva). |
| `USAGE_AFTER_SETTLEMENT` | Llegó consumo de una llamada después de liquidarla. | Concilia contra el panel del proveedor de IA. |

Resuelve cada incidencia con un motivo claro: queda en *Auditoría*.

## El proveedor de IA no responde o rechaza las llamadas

- Lo que ve el alumno: "No se pudo conectar con el profesor". Los fallos de inicio no cobran nada y la reserva se
  libera.
- Revisa la página de estado del proveedor y los registros (`docker compose logs backend | grep -i openai`).
- Si la caída es prolongada, desactiva temporalmente con `AI_PROVIDER=fake` y `AI_VOICE_PROVIDER=fake`, y avisa a los
  alumnos. El profesor simulado no cobra.

## Webhooks de Mercado Pago que no llegan

- La conciliación automática (cada 15 minutos) recupera los pagos de las recargas abiertas. También puedes usar
  *Recargas → Conciliar con la pasarela*.
- Revisa los registros del webhook. Una firma inválida responde 401: ¿cambió la clave secreta en el panel de Mercado
  Pago?

## Sospecha de acceso indebido o filtración de un secreto

1. **Rota el secreto afectado de inmediato**:
   - `DB_PASSWORD` y `MYSQL_ROOT_PASSWORD`;
   - `OPENAI_API_KEY` en el panel del proveedor;
   - `MP_ACCESS_TOKEN` y `MP_WEBHOOK_SECRET` en Mercado Pago;
   - credenciales de SMTP.

   Después reinicia: `docker compose -f docker-compose.prod.yml up -d`.
2. Para cerrar todas las sesiones, reinicia el backend. Las sesiones viven en la base de datos; para invalidarlas todas:
   `TRUNCATE SPRING_SESSION_ATTRIBUTES; DELETE FROM SPRING_SESSION;`.
3. Revisa *Auditoría*: cambios de rol, de precios, devoluciones y cierres de cuenta.
4. Si hubo acceso a datos personales, sigue el procedimiento de notificación que indique el aviso de privacidad y la
   ley aplicable (🔍 revisión legal).

## Solicitudes de los titulares de datos

- **Acceso:** el alumno descarga sus datos en *Ajustes → Privacidad*. Si lo pide por correo, verifica su identidad
  antes de entregar nada.
- **Cancelación:** el alumno elimina su cuenta en *Ajustes → Privacidad*. Si tiene saldo, ofrécele antes la devolución
  (*Recargas → Devolución*).
