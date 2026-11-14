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

1. **Todas las versiones se firman con la misma llave del proyecto.** La llave no está en el repositorio (es público):
   se guarda en dos secretos de GitHub, *Settings → Secrets and variables → Actions → New repository secret*:

   | Secreto | Contenido |
   |---|---|
   | `FIRMA_JKS_BASE64` | La llave (.jks) en base64 |
   | `FIRMA_CLAVE` | La contraseña de la llave |

   Si faltan, la compilación **se detiene con un error** en vez de producir un APK que obligaría a desinstalar. Después
   de compilar, el flujo verifica que el APK quedó firmado con la llave del proyecto (huella SHA-256
   `26:BB:97:78:73:4B:C6:8D:AC:3D:2D:0F:35:98:E1:E4:D8:E8:A7:21:E2:C6:89:B4:CA:5C:7C:A7:E2:5F:B5:61`).
   Guarda una copia de la llave y su contraseña fuera de GitHub: sin ellas no se pueden publicar actualizaciones.
2. **El número de versión sube en cada compilación** (`versionCode` = número de ejecución + 10; el APK se llama
   `ControlGeomembrana-v1.1.N.apk`).
3. **La base de datos se migra sin borrar nada**: los cambios de versión solo agregan tablas o columnas.
4. **Copia automática diaria** (datos + fotos + configuración) en `Descargas/ControlGeomembrana/` del teléfono, que se
   actualiza cada vez que se guarda un registro y conserva las últimas 10. Esa carpeta no se borra al desinstalar: si
   algún día hubiera que reinstalar, menú ⋮ → "Restaurar copia de seguridad" y se elige el .zip más reciente.

Para actualizar: descarga el APK nuevo, ábrelo y toca **Actualizar**. No desinstales la app.

## Uso

### Nuevo registro (un tramo de una sección instalado en un turno)
- **Fecha y turno** (Día / Noche), **responsable** y **cuadrilla**. Recuerda los últimos datos.
- **Sección** (Bóveda, Bota HD, Bota HI…): propone su perímetro.
- **Abscisa inicial y final**: se escriben como `K6+512.39`, `6+512,39` o `6512.39`, **en el sentido en que se avanza**
  (la bóveda va de K7+173 hacia K6+176). La app propone continuar donde terminó el último registro de esa sección, avisa
  si el tramo se **traslapa** con otro de la misma sección y si se sale del túnel configurado.
- **Longitud** = abscisa final − inicial. Con "Ajustar (traslapo)" se puede corregir a mano.
- **Perímetro** instalado (desarrollo de la sección; por defecto 12,5 m, configurable).
- **Área cubierta = longitud × perímetro**, visible en grande mientras se llena el formulario, en cada registro, en el total
  de cada día y en el acumulado. Con "Usar cantidad oficial" se puede escribir el m² del acta si difiere por redondeo.
- **Hora de inicio y fin** con botón **Ahora** (calcula duración, m/h y m²/h; admite turnos que pasan la medianoche).
- **Rollos** utilizados (códigos).
- **Calidad**: temperatura de cuña / extrusora, velocidad de soldadura, prueba de presión del canal de aire
  (Aprobado / No aprobado / N.A.) y estado del geotextil. Marca en rojo lo que esté fuera de especificación.
- **Fotos de la jornada**: cuatro casillas por día, **Inicio**, **Intermedia 1**, **Intermedia 2** y **Final**, con la cámara o
  desde la galería; se guardan reducidas en el teléfono, con la fecha y hora en que se tomaron. También se cargan desde el
  botón "Fotos" de cada día en la pantalla principal.
- **Notas**: interferencias geológicas, demoras por sostenimiento, filtraciones, etc.

Toca cualquier registro para corregirlo o eliminarlo.

### Pantalla principal
Una tarjeta por grupo, **Bóveda** y **Bota** por separado: avance (% por área), m² instalados frente al total, franja del
túnel de cada sección, tramo pendiente y **fecha estimada de terminación**. Debajo, cada día con los metros y **m² de la
jornada por grupo**, el botón de fotos (x/4) y el reporte diario.

### Estadísticas, curva S y proyección
Se eligen **Bóveda**, **Bota** o **Todo el proyecto**:
- **Curva S** de cada sección en curso (la bóveda): avance real acumulado frente al avance **proyectado según los
  rendimientos**: con el promedio de los últimos turnos (amarillo) y con el promedio de todos los turnos (gris).
- Avance cubierto **sin contar dos veces los traslapos**, longitud registrada, tramos pendientes y traslapos detectados.
- Rendimiento global, por fecha (gráfico de barras) y **por cuadrilla**.
- **Desglose por rollo** (si un registro usa varios rollos, su longitud y área se reparten entre ellos).
- **Proyección (ETA)**: promedio de metros por turno de los últimos N turnos (6 por defecto); desde el último turno
  registrado se cuentan los turnos que faltan (1 o 2 por día) y en el último turno se suma la fracción de las horas que se
  trabajan en promedio por turno.
- Resumen de calidad y alertas.

### Informes
- **Reporte diario (PDF con fotos)**: botón en cada día. **Métricas de la jornada por grupo** (Bóveda / Bota: longitud,
  área cubierta, horas, rendimiento, con el detalle HD / HI), avance global acumulado a esa fecha por grupo y sección,
  franjas del túnel con lo instalado ese día, **curva S de la bóveda**, tabla de registros, calidad, firmas y el **anexo con
  las 4 fotos** de la jornada.
- **Informe de avance (PDF)**: avance global por grupo y sección, curva S, avance por fecha separado por grupo, rollos,
  cuadrillas y tramos pendientes (con o sin fotos).
- **Excel**: hojas Resumen (por grupo y sección), Registros, Por fecha (m y m² de cada grupo), Curva S (datos real y
  proyectados), Cuadrillas y Rollos.

### Importar avance
Menú ⋮ → "Importar avance (CSV / texto)". Primera fila con títulos: `Sección | Fecha | PK inicial | PK final | Longitud (m) |
Cantidad (m²)` (opcionales: Turno, Responsable, Cuadrilla, Rollos, Notas). Separado por `;`, `,` o tabulador. Fechas
`2026-07-23`, `23/07/2026` o `7/23/2026` (se detecta el formato). No repite registros que ya existen (misma sección, fecha
y abscisas). Muestra un resumen antes de importar.

Al terminar, cada archivo se puede **abrir, compartir** (WhatsApp, correo) o **guardar** en el teléfono.

### Copias de seguridad
Menú ⋮ → "Guardar copia de seguridad": un .zip con todos los registros, las fotos y la configuración. Sirve también para
pasar todo a otro teléfono ("Restaurar copia de seguridad", que reemplaza lo que haya). La app no usa la copia automática de
Android, así que los datos solo salen del teléfono por esta copia: haz una con frecuencia.

### Configuración
Nombre del proyecto, empresa, título y código del formato, abscisas del túnel, perímetro de referencia, turnos por día y su
hora de inicio, turnos que se promedian para la proyección, especificaciones de temperatura y velocidad de soldadura (según
la ficha técnica del fabricante), modo de color (claro para sol directo, oscuro para el túnel) y logo para los PDF.

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
| `MainActivity.kt`, `Inicio.kt`, `Formulario.kt`, `Estadisticas.kt`, `Configuracion.kt`, `Componentes.kt`, `Tema.kt` | Interfaz (Jetpack Compose, Material 3) |

Las pruebas de los cálculos están en `app/src/test` y se ejecutan en cada compilación.
