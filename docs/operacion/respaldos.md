# Respaldos y restauración (RNF-15)

## Qué se respalda

La base de datos MySQL completa: cuentas, aprendizaje, libro contable, cobros y auditoría. Es la única fuente de
estado. El código está en Git y la configuración en `.env`, que se guarda aparte en un gestor de secretos.

## Respaldo diario

```bash
# crontab -e  (en el servidor)
15 3 * * *  cd /opt/englishos && COMPOSE_FILE=docker-compose.prod.yml infra/backup/backup.sh >> /opt/englishos/backups/backup.log 2>&1
```

`infra/backup/backup.sh`:
- Usa `mysqldump --single-transaction`: es consistente y no detiene el servicio.
- Comprime el volcado y verifica que esté completo.
- Si se define `AGE_RECIPIENT`, lo **cifra** con `age`.
- Genera la suma SHA-256.
- Borra los respaldos con más de `BACKUP_KEEP_DAYS` días.

**Copia fuera del servidor:** copia cada día los archivos `.age` a un almacenamiento externo, por ejemplo un bucket con
versionado y retención, en otra región o con otro proveedor. Un respaldo que solo vive en el mismo servidor no protege
contra la pérdida del servidor.

## Restaurar

1. Detén el backend para que nadie escriba durante la restauración:

   ```bash
   docker compose -f docker-compose.prod.yml stop backend
   ```

2. Restaura sobre la base de producción. Con un archivo `.age`, define `AGE_IDENTITY` con la ruta de la clave privada.

   ```bash
   COMPOSE_FILE=docker-compose.prod.yml infra/backup/restore.sh backups/englishos-AAAAMMDDTHHMMSSZ.sql.gz englishos
   ```

3. Arranca el backend:

   ```bash
   docker compose -f docker-compose.prod.yml start backend
   ```

4. Comprueba en *Administración → Finanzas* que no haya avisos de "El libro contable no cuadra".
5. Si hubo pagos de Mercado Pago después del respaldo, usa *Recargas → Conciliar con la pasarela* para recuperarlos:
   la pasarela es la fuente de verdad de los pagos.

## Prueba de restauración (mensual)

```bash
infra/backup/verify-restore.sh
```

El script:
1. Respalda la base.
2. La restaura en una base aparte (`englishos_restore_check`).
3. Compara el número de filas de **todas** las tablas y el historial de migraciones.
4. Comprueba que el libro restaurado cuadra: Σ = 0 y cada saldo es igual a la suma de sus líneas.
5. Borra la base de prueba.

Anota el resultado en la tabla de abajo.

| Fecha | Entorno | Tablas | Migraciones | Libro restaurado | Resultado |
|---|---|---|---|---|---|
| 2026-11-11 | Local (MySQL 8.4, datos de pruebas E2E) | 52, todas iguales | idénticas | Σ = 0.00; 0 cuentas inconsistentes | ✅ Verificada |

## Objetivos

- **Pérdida máxima de datos (RPO):** 24 horas con el respaldo diario. Para reducirla, activa los binlogs de MySQL y
  cópialos cada hora.
- **Tiempo de recuperación (RTO):** menos de 1 hora con la base actual (un volcado de cientos de KB se restaura en
  segundos). Revísalo cuando la base crezca.
