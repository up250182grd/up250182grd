# Publicar EnglishOS gratis en Oracle Cloud (modo demo)

Guía para tener la app en internet, con un enlace `https://…` que se abre desde cualquier navegador o celular, **sin pagar**.
Usa una máquina gratuita de Oracle Cloud ("Always Free") y la app en **modo demo**.

## Qué es el modo demo

- **Todo simulado, sin costo real:** profesor IA, voz y pagos son simulados. El backend se niega a arrancar si se configura IA o pagos reales (`DemoModeGuard`).
- **Recargas simuladas activas:** los alumnos de prueba pueden ponerse saldo ficticio y usar todo.
- **Correos en una bandeja de pruebas:** los de verificación y recuperación no se envían de verdad. Llegan a `https://TU-DOMINIO/correo/`, protegida con usuario y contraseña. Desde ahí confirmas los registros.
- **No es para alumnos reales que paguen.** Para eso hace falta el despliegue normal ([despliegue.md](despliegue.md)) con correo, IA y pagos reales.

> Condiciones de Oracle verificadas por última vez el 2026-11-13, en fuentes no oficiales. El límite gratuito de las máquinas Ampere A1 se redujo en 2026 (se reporta 2 núcleos y 12 GB en total). A menudo falta capacidad en algunas regiones. Oracle puede pedir una tarjeta para verificar la identidad. Revisa las condiciones vigentes al registrarte.

## Lo que haces tú (unos 30 minutos)

### 1. Crear la cuenta

1. Entra a **oracle.com/cloud/free** y regístrate.
2. Elige con cuidado la **región principal** (*home region*), porque no se puede cambiar después. Elige una cercana; si luego no hay capacidad, prueba otra zona de disponibilidad dentro de la misma región.

### 2. Crear la máquina

En la consola: **Compute → Instances → Create instance**.

| Opción | Valor |
|---|---|
| Image | **Canonical Ubuntu 24.04** |
| Shape | **Ampere → VM.Standard.A1.Flex**, con **1 OCPU y 6 GB** de memoria. Basta para la app y deja margen dentro del límite gratis. |
| Networking | Crear red nueva (VCN) con subred **pública** y **asignar IPv4 pública** |
| Add SSH keys | **Generate a key pair for me** → **Save private key** (guarda el archivo `.key`) |

Pulsa **Create**. Cuando esté en *Running*, copia la **Public IP address**.

> Si aparece *Out of capacity*, cambia de *Availability domain* o inténtalo más tarde.

### 3. Abrir los puertos 80 y 443 en Oracle

1. En la instancia, entra a **Subnet** y luego a la **Security List** (*Default Security List…*).
2. **Add Ingress Rules** dos veces, una por cada puerto:
   - Source CIDR: `0.0.0.0/0`
   - IP Protocol: TCP
   - Destination Port: `80`; en la segunda regla, `443`

### 4. Conseguir un nombre gratis (DuckDNS)

1. Entra a **duckdns.org** e inicia sesión (con GitHub o Google).
2. Crea un subdominio, por ejemplo `miapp-ingles`, que queda como `miapp-ingles.duckdns.org`.
3. En **current ip** pon la IP pública de tu máquina y pulsa **update ip**.

### 5. Conectarte a la máquina (Windows, PowerShell)

1. Mueve la llave a tu carpeta `.ssh` y protégela; ajusta la ruta de descarga si es otra:

   ```powershell
   mkdir $env:USERPROFILE\.ssh -Force; Move-Item $env:USERPROFILE\Downloads\*.key $env:USERPROFILE\.ssh\oracle.key
   ```

   ```powershell
   icacls "$env:USERPROFILE\.ssh\oracle.key" /inheritance:r /grant:r "$($env:USERNAME):(R)"
   ```

2. Conéctate, cambiando la IP por la tuya:

   ```powershell
   ssh -i $env:USERPROFILE\.ssh\oracle.key ubuntu@TU.IP.PUBLICA
   ```

   A la pregunta *Are you sure you want to continue connecting*, responde `yes`.

### 6. Instalar la app (un solo comando)

Ya dentro de la máquina, cambia el dominio y el correo por los tuyos:

```bash
curl -fsSL https://raw.githubusercontent.com/up250182grd/up250182grd/claude/sharp-pascal-hhynf8/infra/oracle/instalar.sh -o instalar.sh
```

```bash
sudo bash instalar.sh miapp-ingles.duckdns.org tu@correo.com
```

El instalador hace esto:

1. Instala Docker.
2. Abre los puertos en el firewall interno de Ubuntu.
3. Descarga el código.
4. Genera contraseñas aleatorias.
5. Construye y arranca la app con HTTPS automático.

La primera vez tarda entre 10 y 20 minutos. Al final muestra:

```
LISTO. Abre https://miapp-ingles.duckdns.org en tu navegador.
Administrador:      tu@correo.com / Demo-xxxxxxxxxxxxxxxxxxxx
Correos de prueba:  https://miapp-ingles.duckdns.org/correo/  (usuario: correo / contraseña: xxxxxxxxxxxxxxxx)
```

**Guarda esos datos.** También quedan en el servidor, en `/root/englishos-credenciales.txt`; para verlos: `sudo cat /root/englishos-credenciales.txt`.

## Uso diario

- **Entrar:** abre tu enlace y entra con el correo y la contraseña del administrador.
- **Probar como alumno:**
  1. Regístrate con otro correo, aunque sea inventado.
  2. Abre `/correo/` y pulsa el enlace de verificación.
  3. Ya en la app, haz una recarga simulada en *Billetera*.
- **Actualizar a la última versión del código:** vuelve a ejecutar `sudo bash instalar.sh miapp-ingles.duckdns.org tu@correo.com`. Conserva datos y contraseñas.
- **Apagar:**

  ```bash
  cd /opt/englishos && sudo docker compose -f docker-compose.prod.yml -f docker-compose.demo.yml down
  ```

## Si algo falla

| Síntoma | Qué revisar |
|---|---|
| El instalador dice que la app no respondió por HTTPS | Que DuckDNS tenga la IP correcta y que existan las reglas de los puertos 80 y 443 (paso 3) |
| *Out of capacity* al crear la máquina | Otra zona de disponibilidad u otro momento del día |
| Error durante la construcción | Registros: `cd /opt/englishos && sudo docker compose -f docker-compose.prod.yml -f docker-compose.demo.yml logs --tail 50` |

> Oracle puede recuperar máquinas gratuitas que pasan mucho tiempo sin uso. Si la app es importante, haz respaldos con `infra/backup/backup.sh` (ver [respaldos.md](respaldos.md)).
