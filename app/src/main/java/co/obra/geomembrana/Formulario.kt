@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.obra.geomembrana

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private fun txt(d: Double?, dec: Int = 3): String = d?.let { Fmt.num(it, dec).replace(".", "") } ?: ""

/**
 * Nuevo registro o edición. [registroId] ya existe aunque el registro aún no esté guardado,
 * para poder asociar las fotos desde el primer momento.
 */
@Composable
fun PantallaFormulario(
    db: Db, p: Proyecto, registros: List<Registro>, original: Registro?, registroId: String,
    alGuardar: (Registro) -> Unit, alEliminar: () -> Unit, alCancelar: () -> Unit, aviso: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ult = remember { Ajustes.ultimos(ctx) }
    val cob = remember(registros) { Calc.cobertura(registros, p) }
    val siguiente = cob.pendientes.firstOrNull()?.ini

    var fecha by rememberSaveable(registroId) { mutableStateOf(original?.fecha ?: T.hoy()) }
    var turno by rememberSaveable(registroId) { mutableStateOf(original?.turno ?: T.turnoActual()) }
    var responsable by rememberSaveable(registroId) { mutableStateOf(original?.responsable ?: ult.responsable) }
    var cuadrilla by rememberSaveable(registroId) { mutableStateOf(original?.cuadrilla ?: ult.cuadrilla) }
    var absIni by rememberSaveable(registroId) {
        mutableStateOf(original?.let { Abscisa.texto(it.absIni) } ?: siguiente?.let { Abscisa.texto(it) } ?: "")
    }
    var absFin by rememberSaveable(registroId) { mutableStateOf(original?.let { Abscisa.texto(it.absFin) } ?: "") }
    var horaIni by rememberSaveable(registroId) { mutableStateOf(original?.horaIni ?: "") }
    var horaFin by rememberSaveable(registroId) { mutableStateOf(original?.horaFin ?: "") }
    var ajustar by rememberSaveable(registroId) { mutableStateOf(original?.longitudManual != null) }
    var longManual by rememberSaveable(registroId) { mutableStateOf(txt(original?.longitudManual)) }
    var perimetro by rememberSaveable(registroId) {
        mutableStateOf(original?.let { txt(it.perimetro) } ?: ult.perimetro.ifBlank { txt(p.perimetroDefecto) })
    }
    var rollos by rememberSaveable(registroId) { mutableStateOf(original?.rollos?.joinToString("\n") ?: "") }
    var rolloNuevo by rememberSaveable(registroId) { mutableStateOf("") }
    var temp by rememberSaveable(registroId) { mutableStateOf(txt(original?.tempCuna, 1)) }
    var vel by rememberSaveable(registroId) { mutableStateOf(txt(original?.velSoldadura)) }
    var prueba by rememberSaveable(registroId) { mutableStateOf((original?.prueba ?: Prueba.APROBADO).name) }
    var geotextil by rememberSaveable(registroId) { mutableStateOf((original?.geotextil ?: Geotextil.OK).name) }
    var notas by rememberSaveable(registroId) { mutableStateOf(original?.notas ?: "") }
    var intentoGuardar by rememberSaveable(registroId) { mutableStateOf(false) }
    var confirmarBorrar by remember { mutableStateOf(false) }
    var confirmarSalir by remember { mutableStateOf(false) }

    // ---------- Fotos ----------
    var versionFotos by remember { mutableIntStateOf(0) }
    val fotos = remember(versionFotos, registroId) { db.fotosDe(registroId) }
    var momentoPendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var verMomento by remember { mutableStateOf<Momento?>(null) }

    fun procesar(trabajo: () -> Foto?) {
        scope.launch {
            val f = withContext(Dispatchers.IO) { runCatching { trabajo() }.getOrNull() }
            if (f != null) { versionFotos++; aviso("Foto guardada: ${f.momento.corto}") } else aviso("No se pudo leer la foto")
        }
    }
    val tomarFoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val m = momentoPendiente?.let { runCatching { Momento.valueOf(it) }.getOrNull() }
        if (ok && m != null) procesar { Fotos.desdeCaptura(ctx, registroId, m, db) }
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val m = momentoPendiente?.let { runCatching { Momento.valueOf(it) }.getOrNull() }
        if (uri != null && m != null) procesar { Fotos.desdeUri(ctx, uri, registroId, m, db) }
    }
    fun camara(m: Momento) {
        momentoPendiente = m.name; verMomento = null
        val f = Fotos.captura(ctx); f.delete()
        try { tomarFoto.launch(uriDe(ctx, f)) } catch (e: ActivityNotFoundException) { aviso("No se encontró una app de cámara") }
    }
    fun desdeGaleria(m: Momento) { momentoPendiente = m.name; verMomento = null; galeria.launch("image/*") }

    // ---------- Cálculos en vivo ----------
    val ini = Abscisa.leer(absIni)
    val fin = Abscisa.leer(absFin)
    val errores = Calc.errores(ini, fin, p)
    val longAbs = if (ini != null && fin != null && fin > ini) fin - ini else null
    val longitud = if (ajustar) Fmt.leer(longManual) else longAbs
    val perim = Fmt.leer(perimetro)
    val area = if (longitud != null && perim != null) longitud * perim else null
    val minutos = Horas.diff(horaIni, horaFin)
    val listaRollos = rollos.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val traslapos = if (ini != null && fin != null && fin > ini) Calc.traslapos(ini, fin, registros, registroId) else emptyList()

    fun construir(): Registro? {
        if (errores.isNotEmpty() || ini == null || fin == null) return null
        if (ajustar && (Fmt.leer(longManual) ?: -1.0) <= 0.0) return null
        return Registro(
            id = registroId, fecha = fecha, turno = turno, responsable = responsable.trim(), cuadrilla = cuadrilla.trim(),
            absIni = ini, absFin = fin, horaIni = horaIni, horaFin = horaFin,
            longitudManual = if (ajustar) Fmt.leer(longManual) else null, perimetro = perim,
            rollos = listaRollos, tempCuna = Fmt.leer(temp), velSoldadura = Fmt.leer(vel),
            prueba = Prueba.valueOf(prueba), geotextil = Geotextil.valueOf(geotextil), notas = notas.trim(),
            creado = original?.creado ?: T.marca()
        )
    }

    fun salir() { if (original == null && fotos.isEmpty() && absFin.isBlank()) alCancelar() else confirmarSalir = true }
    BackHandler { salir() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "Nuevo registro de avance" else "Editar registro") },
                navigationIcon = { IconButton(onClick = { salir() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    if (original != null) IconButton(onClick = { confirmarBorrar = true }) { Icon(Icons.Filled.Delete, "Eliminar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)
        ) {
            Seccion("Fecha y turno") {
                OutlinedButton(
                    onClick = {
                        val d = runCatching { LocalDate.parse(fecha) }.getOrDefault(LocalDate.now())
                        DatePickerDialog(ctx, { _, y, m, dd -> fecha = LocalDate.of(y, m + 1, dd).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text(T.fechaLarga(fecha), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(8.dp))
                Opciones(listOf(Turnos.DIA, Turnos.NOCHE), turno, { it }) { turno = it }
                Campo("Responsable (topógrafo / residente / inspector)", responsable, { responsable = it })
                Campo("Cuadrilla (opcional)", cuadrilla, { cuadrilla = it })
            }

            Seccion("Tramo instalado") {
                Campo("Abscisa inicial", absIni, { absIni = it }, ayuda = ini?.let { "= ${Fmt.num(it)} m" } ?: "Ej. K6+176.57",
                    error = intentoGuardar && (ini == null || errores.any { it.contains("inicial") }))
                if (siguiente != null && original == null && Abscisa.leer(absIni) != siguiente) {
                    TextButton(onClick = { absIni = Abscisa.texto(siguiente) }) { Text("Usar siguiente pendiente: ${Abscisa.texto(siguiente)}") }
                }
                Campo("Abscisa final", absFin, { absFin = it }, ayuda = fin?.let { "= ${Fmt.num(it)} m" } ?: "Ej. K6+190.00",
                    error = intentoGuardar && (fin == null || errores.any { it.contains("final") }))
                Text("Túnel: ${Abscisa.texto(p.absIni)} a ${Abscisa.texto(p.absFin)} (${Fmt.num(p.longitudTotal)} m)",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ini != null && fin != null) errores.forEach { Etiqueta(it, 2); Spacer(Modifier.height(4.dp)) }
                traslapos.forEach { (r, t) ->
                    Etiqueta("Se traslapa ${Fmt.num(t.longitud)} m con el registro del ${T.fechaCorta(r.fecha)} ${r.turno} " +
                        "(${Abscisa.texto(r.absIni)} – ${Abscisa.texto(r.absFin)})", 1)
                    Spacer(Modifier.height(4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Longitud por abscisas", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Fmt.num(longAbs)} m", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Ajustar (traslapo)", fontSize = 14.sp)
                    Spacer(Modifier.padding(4.dp))
                    Switch(checked = ajustar, onCheckedChange = {
                        ajustar = it
                        if (it && longManual.isBlank()) longManual = txt(longAbs)
                    })
                }
                if (ajustar) Campo("Longitud instalada (m)", longManual, { longManual = it }, numerico = true, sufijo = "m",
                    error = intentoGuardar && (Fmt.leer(longManual) ?: -1.0) <= 0.0)
                Campo("Perímetro instalado (desarrollo de la sección)", perimetro, { perimetro = it }, numerico = true, sufijo = "m")
                Kpi("Área cubierta en este registro", if (area != null) "${Fmt.fijo(area)} m²" else "–",
                    if (longitud != null && perim != null) "${Fmt.num(longitud)} m × ${Fmt.num(perim)} m" else "Longitud × perímetro",
                    Modifier.fillMaxWidth().padding(top = 6.dp), destacado = true)
            }

            Seccion("Horario de la actividad") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoHora("Hora de inicio", horaIni, { horaIni = it }, Modifier.weight(1f))
                    CampoHora("Hora de fin", horaFin, { horaFin = it }, Modifier.weight(1f))
                }
                if (minutos != null) {
                    val h = minutos / 60.0
                    Text("Duración: ${Fmt.duracion(minutos.toDouble())}" +
                        (if (h > 0 && longitud != null) " · ${Fmt.num(longitud / h)} m/h" else "") +
                        (if (h > 0 && area != null) " · ${Fmt.num(area / h, 1)} m²/h" else ""),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }
            }

            Seccion("Rollos utilizados") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Campo("Código del rollo", rolloNuevo, { rolloNuevo = it.uppercase() }, Modifier.weight(1f))
                    Spacer(Modifier.padding(4.dp))
                    BotonGrande("Agregar", {
                        val c = rolloNuevo.trim()
                        if (c.isNotEmpty() && c !in listaRollos) rollos = (listaRollos + c).joinToString("\n")
                        rolloNuevo = ""
                    })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listaRollos.forEach { c ->
                        InputChip(selected = false, onClick = { rollos = (listaRollos - c).joinToString("\n") },
                            label = { Text(c, fontSize = 15.sp) },
                            trailingIcon = { Icon(Icons.Filled.Close, "Quitar", Modifier.padding(2.dp)) })
                    }
                }
                Text("${listaRollos.size} rollo(s)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Seccion("Parámetros de calidad") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Campo("Temp. cuña / extrusora", temp, { temp = it }, Modifier.weight(1f), numerico = true, sufijo = "°C",
                        ayuda = rangoTexto(p.tempMin, p.tempMax))
                    Campo("Velocidad soldadura", vel, { vel = it }, Modifier.weight(1f), numerico = true, sufijo = "m/min",
                        ayuda = rangoTexto(p.velMin, p.velMax))
                }
                Text("Prueba de presión del canal de aire", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                Opciones(Prueba.entries, Prueba.valueOf(prueba), { it.etiqueta }) { prueba = it.name }
                Text("Geotextil de soporte", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                Opciones(Geotextil.entries, Geotextil.valueOf(geotextil), { it.etiqueta }) { geotextil = it.name }
                construir()?.let { r -> Calc.alertas(r, p).filter { !it.startsWith("Longitud") }.forEach { Etiqueta(it, 2); Spacer(Modifier.height(4.dp)) } }
            }

            Seccion("Fotos de la jornada (${fotos.size}/4)") {
                Text("Una al inicio, dos en momentos intermedios y una al final.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                Momento.entries.chunked(2).forEach { par ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        par.forEach { m ->
                            CasillaFoto(m, fotos[m], { if (fotos[m] == null) camara(m) else verMomento = m }, Modifier.weight(1f))
                        }
                    }
                }
            }

            Seccion("Notas / observaciones") {
                Campo("Interferencias, demoras por sostenimiento, filtraciones…", notas, { notas = it }, lineas = 3)
            }

            if (intentoGuardar && construir() == null) {
                Etiqueta("Revisa los campos marcados antes de guardar.", 2)
            }
            BotonGrande(if (original == null) "Guardar registro" else "Guardar cambios", {
                intentoGuardar = true
                construir()?.let { alGuardar(it) }
            }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
            Spacer(Modifier.height(40.dp))
        }
    }

    // ---------- Diálogos ----------
    verMomento?.let { m ->
        val f = fotos[m]
        val img = Miniatura(f, 1400)
        AlertDialog(
            onDismissRequest = { verMomento = null },
            title = { Text(m.etiqueta) },
            text = {
                Column {
                    if (img != null) Image(img, m.etiqueta, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp))
                    f?.let { Text("Tomada: ${T.marcaCorta(it.tomada)}", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(onClick = { camara(m) }) { Text("Cámara") }
                        OutlinedButton(onClick = { desdeGaleria(m) }) { Text("Galería") }
                        OutlinedButton(onClick = { f?.let { compartir(ctx, Fotos.archivo(ctx, it), "image/jpeg") } }) { Text("Compartir") }
                        OutlinedButton(onClick = {
                            f?.let { Fotos.quitar(ctx, it, db) }; verMomento = null; versionFotos++
                        }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { verMomento = null }) { Text("Cerrar") } }
        )
    }
    if (confirmarBorrar) AlertDialog(
        onDismissRequest = { confirmarBorrar = false },
        title = { Text("¿Eliminar este registro?") },
        text = { Text("Se borran también sus fotos. Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(onClick = { confirmarBorrar = false; alEliminar() }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmarBorrar = false }) { Text("Cancelar") } }
    )
    if (confirmarSalir) AlertDialog(
        onDismissRequest = { confirmarSalir = false },
        title = { Text("¿Salir sin guardar?") },
        text = { Text(if (original == null) "Se perderán los datos y las fotos de este registro nuevo." else "Se perderán los cambios (las fotos ya quedaron guardadas).") },
        confirmButton = { TextButton(onClick = { confirmarSalir = false; alCancelar() }) { Text("Salir", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmarSalir = false }) { Text("Seguir editando") } }
    )
}

private fun rangoTexto(a: Double?, b: Double?): String? = when {
    a != null && b != null -> "Especificación: ${Fmt.num(a)} a ${Fmt.num(b)}"
    a != null -> "Mínimo: ${Fmt.num(a)}"
    b != null -> "Máximo: ${Fmt.num(b)}"
    else -> null
}
