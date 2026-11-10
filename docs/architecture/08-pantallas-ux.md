# 8. Diseño inicial de pantallas y UX

## 8.1 Identidad visual (propuesta)

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `--color-bg` | `#F7F8FC` | `#0B0E1A` | Fondo |
| `--color-surface` | `#FFFFFF` | `#131729` | Tarjetas |
| `--color-surface-2` | `#EEF0F8` | `#1B2036` | Superficies secundarias |
| `--color-text` | `#141831` | `#E7E9F5` | Texto principal |
| `--color-text-muted` | `#5A6080` | `#9AA0BF` | Texto secundario |
| `--color-primary` | `#3B3FD8` (índigo) | `#7C80FF` | Acciones principales |
| `--color-accent` | `#7A3BE0` (violeta) | `#A98BFF` | Progreso, logros |
| `--color-deep` | `#1A2B6D` (azul profundo) | `#2D3F8F` | Encabezados, sidebar |
| `--color-success` / `warning` / `danger` | `#15803D` / `#B45309` / `#B91C1C` | `#4ADE80` / `#FBBF24` / `#F87171` | Estados |

- Tipografía: **Inter** (UI) y **JetBrains Mono** (cifras de consumo y código). Escala 14/16/18/20/24/30/36.
- Radios 12 px en tarjetas, 8 px en controles; sombras suaves; animaciones ≤ 200 ms con `prefers-reduced-motion` respetado.
- Contraste mínimo 4.5:1 en texto (se verificará cada par de tokens con herramienta de contraste antes de congelarlos).
- Iconografía: Lucide (una sola familia).
- El color comunica acción y progreso; nunca es el único indicador (siempre texto/icono).

## 8.2 Mapa de navegación

```
Público
 ├─ /                       Landing (propuesta, precios transparentes, CTA registro)
 ├─ /login, /register, /verify-email, /forgot-password
 └─ /legal/{terminos,privacidad,cobros}

Alumno (sidebar en escritorio / barra inferior en móvil)
 ├─ /app                    Dashboard
 ├─ /app/onboarding         Bienvenida + diagnóstico
 ├─ /app/plan               Mi plan (módulos y lecciones: aprendido / en curso / pendiente)
 ├─ /app/lessons/:id        Lección (explicación → ejemplos → ejercicios → resumen)
 ├─ /app/practice           Repaso del día (SRS + errores recurrentes)
 ├─ /app/vocabulary         Mis palabras
 ├─ /app/tutor              Profesor IA: elegir Texto | Voz, modo, tema
 ├─ /app/tutor/chat/:id     Chat
 ├─ /app/tutor/call         Llamada de voz
 ├─ /app/tutor/sessions/:id Resumen de sesión
 ├─ /app/progress           Estadísticas
 ├─ /app/achievements       Logros
 ├─ /app/wallet             Billetera: saldo, recargar, movimientos, consumo, límites
 └─ /app/settings           Perfil, idioma, privacidad (exportar / borrar datos), tema

Administrador (/admin, layout separado; además puede usar /app como alumno)
 ├─ /admin                  Resumen: usuarios activos, consumo, ingresos brutos, utilidad neta est.
 ├─ /admin/users            Usuarios
 ├─ /admin/content          Cursos → módulos → lecciones → ejercicios; banco de diagnóstico
 ├─ /admin/ai               Proveedores, modelos, tarifas versionadas, límites, funciones
 ├─ /admin/finance          Ingresos, costos, recargos, comisiones, IVA, pagos, reservas, conciliación, export
 ├─ /admin/my-usage         Mi consumo (al costo)
 ├─ /admin/pricing          Política comercial (factor por rol, versión, vigencia)
 ├─ /admin/incidents        Incidencias de facturación / integraciones
 └─ /admin/audit            Bitácora
```

## 8.3 Pantallas clave (wireframes)

### Dashboard del alumno (escritorio)
```
┌────────────┬─────────────────────────────────────────────────────────────┐
│ EnglishOS  │ Good evening, Ana 👋            Nivel estimado: B1 (Gramática A2) │
│            ├──────────────────────────────┬──────────────────────────────┤
│ ▸ Inicio   │ ▶ Continúa: "Present perfect │ 📞 Habla con tu profesor     │
│   Plan     │   vs past simple" · 12 min   │   [ Llamar ]  [ Chatear ]    │
│   Práctica │   [ Empezar lección ]        │   ~$1.10–1.90 MXN/min (est.) │
│   Vocab.   ├──────────────┬───────────────┼──────────────┬───────────────┤
│   Profesor │ 🔥 Racha 6   │ ⏱ 3 h 20 m   │ 📚 214 pal.  │ 💳 $146.20     │
│   Progreso │ días         │ esta semana   │ 18 por repasar│ gastado mes $53.80│
│   Logros   ├──────────────┴───────────────┴──────────────┴───────────────┤
│   Billetera│ Habilidades (radar)      │ Errores recurrentes             │
│   Ajustes  │ Voc ▇▇▇▇▇ Gram ▇▇▇ ...   │ • 3rd person -s (7)  [Practicar]│
│            │                          │ • since/for (4)                 │
└────────────┴──────────────────────────┴─────────────────────────────────┘
```
(Los importes del wireframe son ilustrativos, no tarifas.)

### Llamada de voz (móvil)
```
┌───────────────────────────┐
│ ← Free conversation   ●Conectado │
│                           │
│        ◯◯◯◯◯◯◯           │  visualizador de audio
│      Teacher is speaking… │  (aria-live="polite")
│                           │
│  00:07:42 / 15:00         │
│  Consumo medido: $9.84    │  "medido" | "estimado"
│  Reserva: $30.00          │
│ ───────────────────────── │
│  Transcripción ▾          │
│  You: I have went to…     │
│  T: Nice! We say "I have  │
│     gone"…                │
│ ───────────────────────── │
│  [🎤 Mute]   [ ⏹ Colgar ] │  botones ≥ 48 px
└───────────────────────────┘
```

### Pre-llamada (cotización)
Modelo, rango de costo por minuto (**estimado**), duración máxima con tu saldo, límite mensual restante, modo, tema, idioma de explicación, aviso "el profesor es una IA; no se graba audio" y botón **Llamar**.

### Resumen de sesión
Duración conectada, consumo **medido** desglosado (costo proveedor solo visible al admin; al alumno: precio e IVA), temas practicados, errores con corrección, vocabulario nuevo (botón "agregar a mis tarjetas"), recomendación siguiente.

### Billetera
Saldo disponible / reservado, botón Recargar, movimientos (filtrables), consumo por sesión, límite mensual y umbral de alerta editables, enlace a política de cobros y devoluciones.

### Admin — Finanzas
Tarjetas: ingresos (recargas confirmadas), costo de IA de clientes, recargo cobrado (ingreso bruto adicional), comisiones, IVA, gasto interno admin, **utilidad neta estimada** (con fórmula visible), conciliaciones pendientes. Tabla por periodo y exportación CSV.

## 8.4 Estados y patrones

- **Carga:** skeletons, no spinners de pantalla completa.
- **Vacío:** cada lista vacía explica qué hacer ("Aún no tienes palabras. Termina una lección para añadirlas").
- **Errores:** mensaje humano + acción (reintentar / recargar saldo / contactar); código de error para soporte.
- **Confirmaciones:** acciones con dinero o irreversibles usan diálogo con resumen (monto, efecto).
- **Micrófono:** pantalla previa explicando el permiso; si se deniega, instrucciones por navegador (incluido iOS).
- **Móvil:** barra inferior con 5 destinos (Inicio, Práctica, Profesor, Progreso, Más); áreas táctiles ≥ 44–48 px.
- **Accesibilidad:** foco visible, orden lógico, `aria-live` para estado de llamada y resultados de ejercicios, alternativa textual a la visualización de audio, sin depender solo de color.
