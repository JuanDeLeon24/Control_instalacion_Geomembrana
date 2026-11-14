# Control Geomembrana – Túnel

App Android para registrar el **avance de instalación de geomembrana** en el túnel: tramo instalado por abscisas,
perímetro, **área cubierta**, rollos, parámetros de soldadura, fotos de la jornada, rendimiento y proyección de terminación.
Funciona **100 % sin internet**: no tiene permiso de red, guarda todo en una base de datos interna del teléfono y genera
los PDF y Excel en el mismo equipo.

Proyecto configurado por defecto: **Túnel 0**, de **K6+176.57** a **K7+173.00** (**996,43 m**), con tres secciones de
geomembrana que avanzan por separado:

| Sección | Perímetro | Grupo | Estado |
|---|---|---|---|
| Bota HD | 2,0 m | Bota | Terminada |
| Bota HI | 2,0 m | Bota | Terminada |
| Bóveda | 19,0 m | Bóveda | En curso |

Todo se puede cambiar en Configuración (nombre, perímetro, grupo y "terminada" de cada sección).

**Avance ya ejecutado incluido**: la app trae 100 registros de bota y bóveda del 23/07/2026 al 14/10/2026 (la jornada de
bóveda del 13/11/2026, K6+512.39 → K6+507.00, se dejó por fuera para ingresarla a mano como prueba)
(`app/src/main/assets/avance_inicial.csv`) y los carga sola la primera vez que se abre. Las cantidades en m² son las
oficiales del registro, aunque difieran unas centésimas de longitud × perímetro.

## Cómo obtener el APK
1. En este repositorio, pestaña **Actions** → "Compilar APK Control Geomembrana" (se ejecuta con cada cambio; también con
   "Run workflow"). Tarda unos 5 minutos.
2. Abre la ejecución terminada y descarga **ControlGeomembrana-APK** (.zip; adentro está el .apk).
3. Pasa el .apk al celular, ábrelo y acepta "Instalar apps de origen desconocido".

### Actualizaciones sin perder datos
Cada APK nuevo se instala **encima** del anterior y conserva registros, fotos y configuración, porque:

1. **Todas las versiones se firman con la misma llave del proyecto**: `app/firma/geomembrana.jks` (por decisión del dueño
   del proyecto está en el repositorio, para que la compilación sea automática). **No la borres ni la cambies**: con otra
   llave el APK nuevo no se instalaría encima. El flujo verifica que cada APK quede firmado con ella (huella SHA-256
   `26:BB:97:78:73:4B:C6:8D:AC:3D:2D:0F:35:98:E1:E4:D8:E8:A7:21:E2:C6:89:B4:CA:5C:7C:A7:E2:5F:B5:61`).
   Como el repositorio es público, cualquiera podría firmar un APK con esa llave: instala solo APK descargados de la
   pestaña Actions de este repositorio. Para más seguridad, pasa el repositorio a privado o mueve la llave a los
   secretos `FIRMA_JKS_BASE64` / `FIRMA_CLAVE` (si existen, se usan en lugar del archivo).
2. **El número de versión sube en cada compilación** (`versionCode` = número de ejecución + 10; el APK se llama
   `ControlGeomembrana-v1.1.N.apk`).
3. **La base de datos se migra sin borrar nada**: los cambios de versión solo agregan tablas o columnas.
4. **Copia automática diaria** (datos + fotos + configuración) en `Descargas/ControlGeomembrana/` del teléfono, que se
   actualiza cada vez que se guarda un registro y conserva las últimas 10. Esa carpeta no se borra al desinstalar: si
   algún día hubiera que reinstalar, Más → "Recuperar una copia" y se elige el .zip más reciente.

Para actualizar: descarga el APK nuevo, ábrelo y toca **Actualizar**. No desinstales la app.

## Uso
La app tiene una barra abajo con 4 botones grandes:

| Botón | Para qué |
|---|---|
| **Inicio** | Avance de Bóveda y Bota (por separado), lo registrado hoy y las jornadas anteriores |
| **Registrar** | Anotar un tramo instalado, paso a paso |
| **Informes** | Reporte diario en PDF, informe de avance, Excel y estadísticas |
| **Más** | Copias de seguridad, importar avance y configuración |

### Registrar avance (asistente de 5 pasos)
Una pregunta por pantalla, con botones grandes **Atrás / Siguiente**. Los números de arriba permiten saltar a un paso.

1. **Qué y cuándo**: sección (tarjetas grandes: Bóveda, Bota HD, Bota HI), día y turno.
2. **Dónde**: abscisa donde empezó (la app propone donde terminó el último registro) y donde terminó, en el sentido en
   que se avanzó. Muestra en grande el **área cubierta** (longitud × perímetro). Avisa si se cruza con otro registro o se
   sale del túnel. En "Ajustes avanzados": perímetro, corregir longitud (traslapo) y cantidad oficial del acta.
3. **Horario y personal**: hora de inicio y fin con botón **Ahora** (calcula tiempo y rendimiento), responsable,
   cuadrilla y rollos (opcionales).
4. **Fotos del día**: 4 cuadros (inicio, dos intermedias y final). También se cargan después desde Inicio.
5. **Revisar y guardar**: resumen con botones "Cambiar", observaciones y **Guardar**.

Para corregir o eliminar un registro: tócalo en Inicio.

### Inicio
Botón grande **Registrar avance de hoy**, una tarjeta por grupo (**Bóveda** y **Bota** por separado: % por área, m²
instalados frente al total, franja del túnel de cada sección, pendiente y fecha estimada de terminación) y la lista de
jornadas con los m² de cada grupo, el botón **Fotos x/4** y el **Reporte PDF** del día.

### Estadísticas, curva S y proyección
Se eligen **Bóveda**, **Bota** o **Todo el proyecto**:
- **Curva S** de cada sección en curso (la bóveda): avance real acumulado frente al avance **proyectado según los
  rendimientos**: con el promedio de los últimos turnos (amarillo) y con el promedio de todos los turnos (gris).
- Avance cubierto **sin contar dos veces los traslapos**, tramos pendientes, traslapos detectados.
- Rendimiento por fecha (gráfico de barras), por cuadrilla y por rollo.

### Informes
- **Reporte diario (PDF con fotos)**: **métricas de la jornada por grupo** (Bóveda / Bota: longitud, área cubierta,
  horas, rendimiento, con el detalle HD / HI), avance global acumulado a esa fecha por grupo y sección, franjas del túnel
  con lo instalado ese día, **curva S de la bóveda**, tabla de registros, firmas y el **anexo con las 4 fotos**.
- **Informe de avance (PDF)**: avance global por grupo y sección, curva S, avance por fecha separado por grupo, rollos,
  cuadrillas y tramos pendientes (con o sin fotos).
- **Excel**: hojas Resumen (por grupo y sección), Registros, Por fecha (m y m² de cada grupo), Curva S, Cuadrillas y Rollos.

Cada archivo se puede **abrir, compartir** (WhatsApp, correo) o **guardar** en el teléfono.

### Importar avance
Más → "Importar avance desde archivo". Primera fila con títulos: `Sección | Fecha | PK inicial | PK final | Longitud (m) |
Cantidad (m²)` (opcionales: Turno, Responsable, Cuadrilla, Rollos, Notas). Separado por `;`, `,` o tabulador. Fechas
`2026-07-23`, `23/07/2026` o `7/23/2026` (se detecta el formato). No repite registros que ya existen. Muestra un resumen
antes de importar.

### Copias de seguridad
Más → "Guardar copia en otro lugar": un .zip con todos los registros, las fotos y la configuración. Sirve también para
pasar todo a otro teléfono (Más → "Recuperar una copia", que reemplaza lo que haya). La app no usa la copia automática de
Android, así que los datos solo salen del teléfono por esta copia: haz una con frecuencia.

### Configuración
Nombre del proyecto, empresa, título y código del formato, abscisas del túnel, secciones (nombre, perímetro, grupo y
"terminada"), turnos por día y su
hora de inicio, turnos que se promedian para la proyección, modo de color (claro para sol directo, oscuro para el túnel) y logo para los PDF.

## Estructura del código
| Archivo | Contenido |
|---|---|
| `Modelo.kt` | Registro, Proyecto, enumeraciones (sin dependencias de Android) |
| `Calculos.kt` | Abscisas, cobertura por sección, grupos, rendimiento, proyección, curva S, desgloses y calidad (sin Android, con pruebas) |
| `Importacion.kt` | Lectura de avances en CSV / texto (sin Android, con pruebas) |
| `Db.kt` | Base SQLite (`registros`, `fotos_jornada`) |
| `Fotos.kt` | Cámara / galería, orientación EXIF y reducción |
| `Pdf.kt`, `Excel.kt` | Informes generados sin librerías externas |
| `Respaldo.kt` | Copia de seguridad .zip |
| `MainActivity.kt`, `Inicio.kt`, `Formulario.kt` (asistente), `Menus.kt`, `Estadisticas.kt`, `Configuracion.kt`, `Componentes.kt`, `Tema.kt` | Interfaz (Jetpack Compose, Material 3) |

Las pruebas de los cálculos están en `app/src/test` y se ejecutan en cada compilación.
