@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.obra.geomembrana

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tarjeta con título para agrupar campos. */
@Composable
fun Bloque(titulo: String, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            contenido()
        }
    }
}

@Composable
fun Campo(
    etiqueta: String, valor: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier,
    numerico: Boolean = false, ayuda: String? = null, error: Boolean = false, lineas: Int = 1, sufijo: String? = null
) {
    OutlinedTextField(
        value = valor, onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = lineas == 1, minLines = lineas,
        isError = error,
        supportingText = ayuda?.let { { Text(it) } },
        suffix = sufijo?.let { { Text(it) } },
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)
    )
}

/** Hora HH:mm con botón grande "Ahora" y reloj para escoger. */
@Composable
fun CampoHora(etiqueta: String, valor: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(modifier) {
        Text(etiqueta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val m = Horas.aMin(valor) ?: Horas.aMin(T.ahora()) ?: 0
                    TimePickerDialog(ctx, { _, h, mm -> alCambiar(Horas.formato(h * 60 + mm)) }, m / 60, m % 60, true).show()
                },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
            ) { Text(valor.ifBlank { "--:--" }, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            BotonGrande("Ahora", { alCambiar(T.ahora()) }, Modifier.width(96.dp))
        }
    }
}

@Composable
fun BotonGrande(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    androidx.compose.material3.Button(onClick = onClick, enabled = habilitado, modifier = modifier.heightIn(min = 56.dp)) {
        Text(texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/** Opciones en fichas grandes (una sola seleccionada). */
@Composable
fun <O> Opciones(opciones: List<O>, seleccion: O, etiqueta: (O) -> String, alElegir: (O) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        opciones.forEach { o ->
            FilterChip(
                selected = o == seleccion, onClick = { alElegir(o) },
                label = { Text(etiqueta(o), fontSize = 16.sp, modifier = Modifier.padding(vertical = 10.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
fun Kpi(etiqueta: String, valor: String, sub: String = "", modifier: Modifier = Modifier, destacado: Boolean = false) {
    val fondo = if (destacado) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val tinta = if (destacado) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(fondo).padding(10.dp)
    ) {
        Text(etiqueta.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tinta.copy(alpha = 0.75f), lineHeight = 13.sp)
        Text(valor, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = tinta, lineHeight = 26.sp)
        if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = tinta.copy(alpha = 0.8f), lineHeight = 14.sp)
    }
}

/** Fila de dos indicadores del mismo ancho. */
@Composable
fun KpiFila(vararg items: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { it(Modifier.weight(1f)) }
    }
}

/** Franja del túnel: instalado (amarillo), resaltado (azul) y pendiente (gris). */
@Composable
fun FranjaTunel(p: Proyecto, registros: List<Registro>, resaltar: List<Registro> = emptyList(), alto: Int = 26) {
    val inst = Calc.cobertura(registros, p).tramos
    val res = Calc.cobertura(resaltar, p).tramos
    val pend = MaterialTheme.colorScheme.surfaceVariant
    val borde = MaterialTheme.colorScheme.outline
    val azul = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(alto.dp)) {
            fun x(a: Double) = ((a - p.absIni) / p.longitudTotal * size.width).toFloat().coerceIn(0f, size.width)
            drawRect(pend)
            inst.forEach { t -> drawRect(Amarillo, Offset(x(t.ini), 0f), Size(maxOf(1.5f, x(t.fin) - x(t.ini)), size.height)) }
            res.forEach { t -> drawRect(azul, Offset(x(t.ini), 0f), Size(maxOf(1.5f, x(t.fin) - x(t.ini)), size.height)) }
            drawRect(borde, style = Stroke(1.5f))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Abscisa.texto(p.absIni), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(Abscisa.texto(p.absFin), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Aviso de color (ok / aviso / mal). */
@Composable
fun Etiqueta(texto: String, tipo: Int) {   // 0 ok, 1 aviso, 2 mal
    val e = LocalEstado.current
    val (fg, bg) = when (tipo) { 0 -> e.ok to e.okFondo; 1 -> e.aviso to e.avisoFondo; else -> e.mal to e.malFondo }
    Text(texto, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
fun Miniatura(foto: Foto?, lado: Int = 480): ImageBitmap? {
    val ctx = LocalContext.current
    val b by produceState<ImageBitmap?>(null, foto?.archivo) {
        value = if (foto == null) null else withContext(Dispatchers.IO) {
            runCatching { Fotos.cargar(ctx, foto, lado)?.asImageBitmap() }.getOrNull()
        }
    }
    return b
}

/** Casilla de foto de un momento de la jornada. */
@Composable
fun CasillaFoto(momento: Momento, foto: Foto?, alTocar: () -> Unit, modifier: Modifier = Modifier) {
    val img = Miniatura(foto)
    val e = LocalEstado.current
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, if (foto != null) e.ok else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .clickable(onClick = alTocar),
            contentAlignment = Alignment.Center
        ) {
            if (img != null) {
                Image(img, contentDescription = momento.etiqueta, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(R.drawable.ic_camara), contentDescription = null, modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tomar foto", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(momento.corto, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        if (foto != null) Text(T.marcaCorta(foto.tomada), fontSize = 11.sp, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }
}

/** Barra de valores (para gráficos sencillos por fecha, cuadrilla, etc.). */
@Composable
fun Barra(fraccion: Float, color: Color = Amarillo) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraccion.coerceIn(0f, 1f)).height(10.dp).background(color))
    }
}

/**
 * Las 4 fotos de una jornada (fecha): cámara o galería, ver, compartir y borrar.
 * Se usa dentro del formulario y desde la tarjeta de cada día.
 */
@Composable
fun FotosJornada(db: Db, jornada: String, aviso: (String) -> Unit, alCambiar: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var version by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val fotos = androidx.compose.runtime.remember(version, jornada) { db.fotosDe(jornada) }
    var pendiente by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var ver by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Momento?>(null) }

    fun procesar(trabajo: () -> Foto?) {
        scope.launch {
            val f = withContext(Dispatchers.IO) { runCatching { trabajo() }.getOrNull() }
            if (f != null) { version++; alCambiar(); aviso("Foto guardada: ${f.momento.corto}") } else aviso("No se pudo leer la foto")
        }
    }
    val tomar = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        val m = pendiente?.let { runCatching { Momento.valueOf(it) }.getOrNull() }
        if (ok && m != null) procesar { Fotos.desdeCaptura(ctx, jornada, m, db) }
    }
    val galeria = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        val m = pendiente?.let { runCatching { Momento.valueOf(it) }.getOrNull() }
        if (uri != null && m != null) procesar { Fotos.desdeUri(ctx, uri, jornada, m, db) }
    }
    fun camara(m: Momento) {
        pendiente = m.name; ver = null
        val f = Fotos.captura(ctx); f.delete()
        try { tomar.launch(uriDe(ctx, f)) } catch (e: android.content.ActivityNotFoundException) { aviso("No se encontró una app de cámara") }
    }

    Column {
        Text("Una al inicio, dos en momentos intermedios y una al final de la jornada del ${T.fechaCorta(jornada)}.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        Momento.entries.chunked(2).forEach { par ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                par.forEach { m -> CasillaFoto(m, fotos[m], { if (fotos[m] == null) camara(m) else ver = m }, Modifier.weight(1f)) }
            }
        }
    }

    ver?.let { m ->
        val f = fotos[m]
        val img = Miniatura(f, 1400)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ver = null },
            title = { Text(m.etiqueta) },
            text = {
                Column {
                    if (img != null) Image(img, m.etiqueta, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp))
                    f?.let { Text("Tomada: ${T.marcaCorta(it.tomada)}", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { camara(m) }) { Text("Cámara") }
                        OutlinedButton(onClick = { pendiente = m.name; ver = null; galeria.launch("image/*") }) { Text("Galería") }
                        OutlinedButton(onClick = { f?.let { compartir(ctx, Fotos.archivo(ctx, it), "image/jpeg") } }) { Text("Compartir") }
                        OutlinedButton(onClick = { f?.let { Fotos.quitar(ctx, it, db) }; ver = null; version++; alCambiar() }) {
                            Text("Borrar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { ver = null }) { Text("Cerrar") } }
        )
    }
}

/** Curva S: avance real (línea continua) y proyectado según rendimientos (líneas punteadas), en % de la longitud. */
@Composable
fun GraficoCurvaS(c: CurvaS, alto: Int = 220) {
    val real = c.real
    if (real.isEmpty()) { Text("Sin registros para la curva S.", fontSize = 14.sp); return }
    val todos = real + c.proyReciente + c.proyPromedio
    val x0 = todos.minOf { it.fecha.toEpochDay() }.toFloat()
    val x1 = maxOf(todos.maxOf { it.fecha.toEpochDay() }.toFloat(), x0 + 1f)
    val ejes = MaterialTheme.colorScheme.outline
    val azul = MaterialTheme.colorScheme.primary
    val gris = MaterialTheme.colorScheme.onSurfaceVariant
    val rejilla = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(alto.dp)) {
            val izq = 8f; val w = size.width - izq; val h = size.height - 4f
            fun px(d: java.time.LocalDate) = izq + (d.toEpochDay() - x0) / (x1 - x0) * w
            fun py(m: Double) = (h - (m / c.total).toFloat().coerceIn(0f, 1f) * h)
            for (k in 0..4) { val y = h - h * k / 4f; drawLine(rejilla, Offset(izq, y), Offset(size.width, y), 1f) }
            drawLine(ejes, Offset(izq, 0f), Offset(izq, h), 2f)
            drawLine(ejes, Offset(izq, h), Offset(size.width, h), 2f)
            fun linea(pts: List<PuntoCurva>, color: Color, ancho: Float, punteada: Boolean) {
                if (pts.size < 2) return
                val path = androidx.compose.ui.graphics.Path()
                pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.fecha), py(p.metros)) else path.lineTo(px(p.fecha), py(p.metros)) }
                drawPath(path, color, style = Stroke(ancho,
                    pathEffect = if (punteada) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null))
            }
            linea(c.proyPromedio, gris, 3f, true)
            linea(c.proyReciente, Amarillo, 4f, true)
            linea(real, azul, 5f, false)
            real.lastOrNull()?.let { drawCircle(azul, 7f, Offset(px(it.fecha), py(it.metros))) }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(T.fechaCorta(java.time.LocalDate.ofEpochDay(x0.toLong()).toString()), fontSize = 11.sp, color = gris)
            Spacer(Modifier.weight(1f))
            Text(T.fechaCorta(java.time.LocalDate.ofEpochDay(x1.toLong()).toString()), fontSize = 11.sp, color = gris)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Leyenda(azul, "Real: ${Fmt.pct(real.last().metros / c.total)}")
            if (c.proyReciente.isNotEmpty()) Leyenda(Amarillo, "Proyectado (últimos turnos, ${Fmt.num(c.mPorDiaReciente)} m/día) → ${T.fechaCorta(c.proyReciente.last().fecha.toString())}")
            if (c.proyPromedio.isNotEmpty()) Leyenda(gris, "Proyectado (promedio, ${Fmt.num(c.mPorDiaPromedio)} m/día) → ${T.fechaCorta(c.proyPromedio.last().fecha.toString())}")
        }
    }
}

@Composable
private fun Leyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 18.dp, height = 4.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(texto, fontSize = 12.sp)
    }
}

/** Métricas por grupo (Bota, Bóveda) con el detalle de cada sección. */
@Composable
fun TablaMetricas(m: List<MetricasGrupo>) {
    if (m.isEmpty()) { Text("Sin registros.", fontSize = 14.sp); return }
    m.forEach { g ->
        Text(g.nombre, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
        KpiFila(
            { Kpi("Longitud", "${Fmt.num(g.total.longitud)} m", "${g.total.registros} registro(s)", it) },
            { Kpi("Área cubierta", "${Fmt.num(g.total.area, 2)} m²", if (g.total.minutos > 0) "${Fmt.num(g.total.m2PorHora, 1)} m²/h" else "", it, destacado = true) }
        )
        if (g.porSeccion.size > 1 || g.porSeccion.first().first.nombre != g.nombre) g.porSeccion.forEach { (s, r) ->
            Text("• ${s.nombre}: ${Fmt.num(r.longitud)} m × ${Fmt.num(s.perimetro)} m → ${Fmt.num(r.area, 2)} m²" +
                (if (r.minutos > 0) " · ${Fmt.duracion(r.minutos.toDouble())} · ${Fmt.num(r.mPorHora)} m/h" else ""), fontSize = 14.sp)
        }
    }
}
