# Control Geomembrana – Túnel

App Android para registrar el **avance de instalación de geomembrana** en el túnel: tramo instalado por abscisas,
perímetro, **área cubierta**, rollos, parámetros de soldadura, fotos de la jornada, rendimiento y proyección de terminación.
Funciona **100 % sin internet**: no tiene permiso de red, guarda todo en una base de datos interna del teléfono y genera
los PDF y Excel en el mismo equipo.

Proyecto configurado por defecto: **Túnel 0**, de **K6+176.57** a **K7+173.00** (**996,43 m**). Todo se puede cambiar en
Configuración.

## Cómo obtener el APK
1. En este repositorio, pestaña **Actions** → "Compilar APK Control Geomembrana" (se ejecuta con cada cambio; también con
   "Run workflow"). Tarda unos 5 minutos.
2. Abre la ejecución terminada y descarga **ControlGeomembrana-APK** (.zip; adentro está el .apk).
3. Pasa el .apk al celular, ábrelo y acepta "Instalar apps de origen desconocido".

### Firma (para poder actualizar sin perder datos)
El repositorio es público, por eso **la llave de firma no está en el código**. Se guarda como secreto de GitHub:
*Settings → Secrets and variables → Actions → New repository secret*:

| Secreto | Contenido |
|---|---|
| `FIRMA_JKS_BASE64` | El archivo de llave (.jks) convertido a base64 |
| `FIRMA_CLAVE` | La contraseña de la llave |

La llave se crea una sola vez (en un computador con Java):

```bash
keytool -genkeypair -v -keystore geomembrana.jks -alias geomembrana -keyalg RSA -keysize 2048 -validity 10000
```

```bash
base64 -w0 geomembrana.jks
```

Guarda el .jks y la contraseña en un lugar seguro: si se pierden, las versiones nuevas no se instalan encima de las
anteriores. **Sin estos secretos** el APK se firma con una llave temporal distinta en cada compilación, y para instalar
una versión nueva hay que desinstalar la anterior (haz antes una copia de seguridad).

## Uso

### Nuevo registro (un tramo instalado en un turno)
- **Fecha y turno** (Día / Noche), **responsable** y **cuadrilla**. Recuerda los últimos datos.
- **Abscisa inicial y final**: se escriben como `K6+176.57`, `6+176,57` o `6176.57`. La app verifica que estén dentro del
  túnel y que la final sea mayor. Propone la **siguiente abscisa pendiente** y avisa si el tramo se **traslapa** con otro
  registro.
- **Longitud** = abscisa final − inicial. Con "Ajustar (traslapo)" se puede corregir a mano.
- **Perímetro** instalado (desarrollo de la sección; por defecto 12,5 m, configurable).
- **Área cubierta = longitud × perímetro**, visible en grande mientras se llena el formulario, en cada registro, en el total
  de cada día y en el acumulado.
- **Hora de inicio y fin** con botón **Ahora** (calcula duración, m/h y m²/h; admite turnos que pasan la medianoche).
- **Rollos** utilizados (códigos).
- **Calidad**: temperatura de cuña / extrusora, velocidad de soldadura, prueba de presión del canal de aire
  (Aprobado / No aprobado / N.A.) y estado del geotextil. Marca en rojo lo que esté fuera de especificación.
- **Fotos de la jornada**: cuatro casillas, **Inicio**, **Intermedia 1**, **Intermedia 2** y **Final**. Cada una con la cámara o
  desde la galería; se guardan reducidas en el teléfono, con la fecha y hora en que se tomaron.
- **Notas**: interferencias geológicas, demoras por sostenimiento, filtraciones, etc.

Toca cualquier registro para corregirlo o eliminarlo.

### Pantalla principal
Avance acumulado (% y metros), franja del túnel con lo instalado y lo pendiente, área instalada frente al área total,
rendimiento (m/h y m²/h) y **fecha y hora estimadas de terminación**. Debajo, los registros agrupados por día con los
metros, los **m² cubiertos** y las horas de cada jornada.

### Estadísticas y proyección
- Avance cubierto **sin contar dos veces los traslapos**, longitud registrada, tramos pendientes y traslapos detectados.
- Rendimiento global, por fecha (gráfico de barras) y **por cuadrilla**.
- **Desglose por rollo** (si un registro usa varios rollos, su longitud y área se reparten entre ellos).
- **Proyección (ETA)**: promedio de metros por turno de los últimos N turnos (6 por defecto); desde el último turno
  registrado se cuentan los turnos que faltan (1 o 2 por día) y en el último turno se suma la fracción de las horas que se
  trabajan en promedio por turno.
- Resumen de calidad y alertas.

### Informes
- **Reporte diario (PDF con fotos)**: botón en cada día. Incluye la longitud, el **área cubierta en la jornada**, las horas,
  el rendimiento, el avance acumulado a esa fecha, la franja del túnel con lo instalado ese día, la tabla de registros, la
  calidad, las firmas y el **anexo con las 4 fotos** de cada registro.
- **Informe de avance (PDF)**: todo el proyecto, con avance por fecha, rollos, cuadrillas y tramos pendientes (con o sin fotos).
- **Excel**: hojas Resumen, Registros (con área como fórmula), Por fecha, Rollos, Cuadrillas y Tramos pendientes.

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
| `Calculos.kt` | Abscisas, cobertura y traslapos, rendimiento, proyección, desgloses y calidad (sin Android, con pruebas) |
| `Db.kt` | Base SQLite (`registros`, `fotos`) |
| `Fotos.kt` | Cámara / galería, orientación EXIF y reducción |
| `Pdf.kt`, `Excel.kt` | Informes generados sin librerías externas |
| `Respaldo.kt` | Copia de seguridad .zip |
| `MainActivity.kt`, `Inicio.kt`, `Formulario.kt`, `Estadisticas.kt`, `Configuracion.kt`, `Componentes.kt`, `Tema.kt` | Interfaz (Jetpack Compose, Material 3) |

Las pruebas de los cálculos están en `app/src/test` y se ejecutan en cada compilación.
