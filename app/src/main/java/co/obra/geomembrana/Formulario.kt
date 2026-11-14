@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.obra.geomembrana

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private fun txt(d: Double?, dec: Int = 3): String = d?.let { Fmt.num(it, dec).replace(".", "") } ?: ""

private val PASOS = listOf("Qué y cuándo", "Dónde", "Horario y personal", "Fotos del día", "Revisar y guardar")

/** Explicación corta debajo del título de cada paso. */
@Composable
private fun Ayuda(texto: String) {
    Text(texto, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun Pregunta(texto: String) {
    Text(texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
private fun FilaResumen(etq: String, valor: String, alCambiar: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(etq, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = alCambiar) { Text("Cambiar", fontSize = 15.sp) }
    }
}

/**
 * Registro de avance como asistente paso a paso: una pregunta por pantalla, botones grandes "Atrás" y "Siguiente",
 * y un resumen final antes de guardar. Las fotos son de la jornada (la fecha elegida), no del registro.
 */
@Composable
fun PantallaFormulario(
    db: Db, p: Proyecto, registros: List<Registro>, original: Registro?, registroId: String,
    alGuardar: (Registro) -> Unit, alEliminar: () -> Unit, alCancelar: () -> Unit, aviso: (String) -> Unit
) {
    val ctx = LocalContext.current
    val ult = remember { Ajustes.ultimos(ctx) }
    val scroll = rememberScrollState()

    var paso by rememberSaveable(registroId) { mutableIntStateOf(if (original == null) 0 else PASOS.lastIndex) }
    var fecha by rememberSaveable(registroId) { mutableStateOf(original?.fecha ?: T.hoy()) }
    var turno by rememberSaveable(registroId) { mutableStateOf(original?.turno ?: T.turnoActual()) }
    val activas = p.secciones.filter { !it.terminada }.ifEmpty { p.secciones }
    var seccion by rememberSaveable(registroId) {
        mutableStateOf(original?.seccion ?: (p.seccion(ult.seccion)?.takeIf { !it.terminada } ?: activas.first()).nombre)
    }
    var responsable by rememberSaveable(registroId) { mutableStateOf(original?.responsable ?: ult.responsable) }
    var cuadrilla by rememberSaveable(registroId) { mutableStateOf(original?.cuadrilla ?: ult.cuadrilla) }
    val deLaSeccion = remember(registros, seccion) { Calc.deSeccion(registros, seccion) }
    val siguiente = remember(deLaSeccion) { Calc.siguiente(deLaSeccion) }
    var absIni by rememberSaveable(registroId) {
        mutableStateOf(original?.let { Abscisa.texto(it.absIni) } ?: siguiente?.let { Abscisa.texto(it) } ?: "")
    }
    var absFin by rememberSaveable(registroId) { mutableStateOf(original?.let { Abscisa.texto(it.absFin) } ?: "") }
    var horaIni by rememberSaveable(registroId) { mutableStateOf(original?.horaIni ?: "") }
    var horaFin by rememberSaveable(registroId) { mutableStateOf(original?.horaFin ?: "") }
    var avanzado by rememberSaveable(registroId) { mutableStateOf(original?.longitudManual != null || original?.areaManual != null) }
    var ajustar by rememberSaveable(registroId) { mutableStateOf(original?.longitudManual != null) }
    var longManual by rememberSaveable(registroId) { mutableStateOf(txt(original?.longitudManual)) }
    var perimetro by rememberSaveable(registroId) {
        mutableStateOf(original?.let { txt(it.perimetro) } ?: txt(p.seccion(seccion)?.perimetro))
    }
    var ajustarArea by rememberSaveable(registroId) { mutableStateOf(original?.areaManual != null) }
    var areaManual by rememberSaveable(registroId) { mutableStateOf(txt(original?.areaManual, 2)) }
    var rollos by rememberSaveable(registroId) { mutableStateOf(original?.rollos?.joinToString("\n") ?: "") }
    var rolloNuevo by rememberSaveable(registroId) { mutableStateOf("") }
    var notas by rememberSaveable(registroId) { mutableStateOf(original?.notas ?: "") }
    var intento by rememberSaveable(registroId) { mutableStateOf(false) }
    var confirmarBorrar by remember { mutableStateOf(false) }
    var confirmarSalir by remember { mutableStateOf(false) }

    // ---------- Cálculos en vivo ----------
    val ini = Abscisa.leer(absIni)
    val fin = Abscisa.leer(absFin)
    val errores = Calc.errores(ini, fin)
    val fueraRango = Calc.fueraDeRango(ini, fin, p)
    val longAbs = if (ini != null && fin != null) kotlin.math.abs(fin - ini) else null
    val longitud = if (ajustar) Fmt.leer(longManual) else longAbs
    val perim = Fmt.leer(perimetro)
    val areaCalc = if (longitud != null && perim != null) longitud * perim else null
    val area = if (ajustarArea) Fmt.leer(areaManual) else areaCalc
    val minutos = Horas.diff(horaIni, horaFin)
    val listaRollos = rollos.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val traslapos = if (ini != null && fin != null && errores.isEmpty()) Calc.traslapos(ini, fin, deLaSeccion, registroId) else emptyList()
    val sec = p.seccion(seccion)

    fun construir(): Registro? {
        if (errores.isNotEmpty() || ini == null || fin == null) return null
        if (ajustar && (Fmt.leer(longManual) ?: -1.0) <= 0.0) return null
        if (ajustarArea && (Fmt.leer(areaManual) ?: -1.0) <= 0.0) return null
        return Registro(
            id = registroId, fecha = fecha, turno = turno, seccion = seccion,
            responsable = responsable.trim(), cuadrilla = cuadrilla.trim(),
            absIni = ini, absFin = fin, horaIni = horaIni, horaFin = horaFin,
            longitudManual = if (ajustar) Fmt.leer(longManual) else null, perimetro = perim,
            areaManual = if (ajustarArea) Fmt.leer(areaManual) else null,
            rollos = listaRollos,
            // Control de calidad retirado de la app: se conservan los valores que ya tuviera el registro
            tempCuna = original?.tempCuna, velSoldadura = original?.velSoldadura,
            prueba = original?.prueba ?: Prueba.NA, geotextil = original?.geotextil ?: Geotextil.OK,
            notas = notas.trim(), creado = original?.creado ?: T.marca()
        )
    }

    /** ¿Se puede pasar al siguiente paso? Solo el paso "Dónde" es obligatorio. */
    fun pasoValido(i: Int): Boolean = i != 1 || construir() != null

    fun salir() { if (original == null && absFin.isBlank()) alCancelar() else confirmarSalir = true }
    BackHandler { if (paso > 0 && original == null) paso-- else salir() }
    LaunchedEffect(paso) { scroll.scrollTo(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (original == null) "Registrar avance" else "Corregir registro", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { salir() }) { Icon(Icons.Filled.Close, "Salir sin guardar") } },
                    actions = {
                        if (original != null) IconButton(onClick = { confirmarBorrar = true }) { Icon(Icons.Filled.Delete, "Eliminar registro") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                // Pasos: números tocables para ir directo a uno
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PASOS.indices.forEach { i ->
                                val activo = i == paso
                                val hecho = i < paso
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(if (activo || hecho) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                        .clickable { if (i <= paso || (paso..i - 1).all { pasoValido(it) }) paso = i },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (hecho) "✓" else "${i + 1}", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                        color = if (activo || hecho) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        Text("Paso ${paso + 1} de ${PASOS.size}: ${PASOS[paso]}", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 6.dp))
                        LinearProgressIndicator(progress = { (paso + 1f) / PASOS.size }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (paso > 0) OutlinedButton(onClick = { paso-- }, modifier = Modifier.weight(1f).heightIn(min = 60.dp)) {
                        Text("← Atrás", fontSize = 18.sp)
                    }
                    if (paso < PASOS.lastIndex) {
                        BotonGrande("Siguiente →", {
                            intento = true
                            if (pasoValido(paso)) { intento = false; paso++ }
                        }, Modifier.weight(1f).heightIn(min = 60.dp))
                    } else {
                        BotonGrande("✓  Guardar", {
                            intento = true
                            val r = construir()
                            if (r != null) alGuardar(r) else paso = 1
                        }, Modifier.weight(1f).heightIn(min = 60.dp))
                    }
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (paso) {
                // ---------------- 1. Qué y cuándo ----------------
                0 -> {
                    Pregunta("¿Qué sección se instaló?")
                    p.secciones.forEach { s ->
                        val elegida = s.nombre == seccion
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable {
                                seccion = s.nombre
                                perimetro = txt(s.perimetro)
                                if (original == null) Calc.siguiente(Calc.deSeccion(registros, s.nombre))?.let { absIni = Abscisa.texto(it) }
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(2.dp, if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.nombre, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                        color = if (elegida) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    Text("Perímetro ${Fmt.num(s.perimetro)} m" + if (s.terminada) " · terminada" else "", fontSize = 14.sp,
                                        color = if (elegida) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (elegida) Text("✓", fontSize = 26.sp, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    if (sec?.terminada == true) Etiqueta("Esta sección está marcada como terminada. ¿Seguro que es la correcta?", 1)

                    Pregunta("¿Qué día?")
                    OutlinedButton(
                        onClick = {
                            val d = runCatching { LocalDate.parse(fecha) }.getOrDefault(LocalDate.now())
                            DatePickerDialog(ctx, { _, y, m, dd -> fecha = LocalDate.of(y, m + 1, dd).toString() }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    ) { Text("📅  " + T.fechaLarga(fecha), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    if (fecha != T.hoy()) TextButton(onClick = { fecha = T.hoy() }) { Text("Volver a hoy", fontSize = 16.sp) }

                    Pregunta("¿Qué turno?")
                    Opciones(listOf(Turnos.DIA, Turnos.NOCHE), turno, { if (it == Turnos.DIA) "☀  Día" else "☾  Noche" }) { turno = it }
                }

                // ---------------- 2. Dónde ----------------
                1 -> {
                    Ayuda("Escribe las abscisas donde empezó y donde terminó la instalación, en el sentido en que se avanzó. " +
                        "Ejemplo: K6+512.39")
                    Pregunta("¿Dónde EMPEZÓ?")
                    Campo("Abscisa inicial", absIni, { absIni = it }, ayuda = ini?.let { "✓ ${Abscisa.texto(it)}" } ?: "Ejemplo: K6+512.39",
                        error = intento && ini == null)
                    if (siguiente != null && original == null && ini != siguiente) {
                        OutlinedButton(onClick = { absIni = Abscisa.texto(siguiente) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Usar donde terminó el último: ${Abscisa.texto(siguiente)}", fontSize = 15.sp)
                        }
                    }
                    Pregunta("¿Dónde TERMINÓ?")
                    Campo("Abscisa final", absFin, { absFin = it }, ayuda = fin?.let { "✓ ${Abscisa.texto(it)}" } ?: "Ejemplo: K6+507.00",
                        error = intento && (fin == null || errores.isNotEmpty()))

                    Spacer(Modifier.height(10.dp))
                    // Resultado grande: lo que se instaló
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ÁREA CUBIERTA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(if (area != null) "${Fmt.fijo(area)} m²" else "–", fontSize = 36.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(
                                if (longitud != null && perim != null) "${Fmt.num(longitud)} m de largo × ${Fmt.num(perim)} m de perímetro"
                                else "Escribe las dos abscisas", fontSize = 15.sp, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (ajustarArea) Text("(cantidad oficial)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (intento && ini != null && fin != null) errores.forEach { Etiqueta(it, 2); Spacer(Modifier.height(4.dp)) }
                    if (intento && (ini == null || fin == null)) Etiqueta("Falta escribir bien una abscisa (ejemplo: K6+507.00)", 2)
                    fueraRango?.let { Etiqueta(it, 1); Spacer(Modifier.height(4.dp)) }
                    traslapos.forEach { (r, t) ->
                        Etiqueta("Ojo: se cruza ${Fmt.num(t.longitud)} m con lo registrado el ${T.fechaCorta(r.fecha)} " +
                            "(${Abscisa.texto(r.absIni)} → ${Abscisa.texto(r.absFin)})", 1)
                        Spacer(Modifier.height(4.dp))
                    }

                    TextButton(onClick = { avanzado = !avanzado }, modifier = Modifier.padding(top = 6.dp)) {
                        Text(if (avanzado) "▲ Ocultar ajustes avanzados" else "▼ Ajustes avanzados (perímetro, traslapo, cantidad oficial)", fontSize = 15.sp)
                    }
                    if (avanzado) Column {
                        Campo("Perímetro instalado", perimetro, { perimetro = it }, numerico = true, sufijo = "m")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Corregir la longitud (traslapo)", fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Switch(checked = ajustar, onCheckedChange = { ajustar = it; if (it && longManual.isBlank()) longManual = txt(longAbs) })
                        }
                        if (ajustar) Campo("Longitud instalada", longManual, { longManual = it }, numerico = true, sufijo = "m",
                            error = intento && (Fmt.leer(longManual) ?: -1.0) <= 0.0)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Usar la cantidad oficial del acta (m²)", fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Switch(checked = ajustarArea, onCheckedChange = { ajustarArea = it; if (it && areaManual.isBlank()) areaManual = txt(areaCalc, 2) })
                        }
                        if (ajustarArea) Campo("Cantidad oficial", areaManual, { areaManual = it }, numerico = true, sufijo = "m²",
                            error = intento && (Fmt.leer(areaManual) ?: -1.0) <= 0.0)
                    }
                }

                // ---------------- 3. Horario y personal ----------------
                2 -> {
                    Ayuda("Toca \"Ahora\" cuando empiece y cuando termine el trabajo. Si no lo sabes, puedes dejarlo vacío.")
                    CampoHora("Hora de inicio", horaIni, { horaIni = it })
                    Spacer(Modifier.height(12.dp))
                    CampoHora("Hora de fin", horaFin, { horaFin = it })
                    if (minutos != null) {
                        val h = minutos / 60.0
                        Kpi("Tiempo trabajado", Fmt.duracion(minutos.toDouble()),
                            if (h > 0 && area != null) "Rendimiento: ${Fmt.num(longitud?.let { it / h })} m/h · ${Fmt.num(area / h, 1)} m²/h" else "",
                            Modifier.fillMaxWidth().padding(top = 12.dp), destacado = true)
                    }
                    Pregunta("¿Quién reporta?")
                    Campo("Responsable (nombre)", responsable, { responsable = it })
                    Campo("Cuadrilla (opcional)", cuadrilla, { cuadrilla = it })
                    Pregunta("Rollos usados (opcional)")
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
                                label = { Text(c, fontSize = 16.sp) }, trailingIcon = { Icon(Icons.Filled.Close, "Quitar") })
                        }
                    }
                }

                // ---------------- 4. Fotos ----------------
                3 -> {
                    Ayuda("Toca cada cuadro para tomar la foto. Son 4 por día: al inicio, dos durante el trabajo y una al final. " +
                        "Si aún no las tienes, puedes seguir y tomarlas después desde Inicio.")
                    FotosJornada(db, fecha, aviso)
                }

                // ---------------- 5. Revisar y guardar ----------------
                else -> {
                    Ayuda("Revisa que todo esté bien. Toca \"Cambiar\" para corregir algo.")
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            FilaResumen("Sección", seccion) { paso = 0 }
                            FilaResumen("Fecha y turno", "${T.fechaLarga(fecha)} · $turno") { paso = 0 }
                            FilaResumen("Tramo", if (ini != null && fin != null) "${Abscisa.texto(ini)} → ${Abscisa.texto(fin)}" else "⚠ Falta") { paso = 1 }
                            FilaResumen("Longitud × perímetro", "${Fmt.num(longitud)} m × ${Fmt.num(perim)} m") { paso = 1 }
                            FilaResumen("Horario", if (minutos != null) "$horaIni a $horaFin (${Fmt.duracion(minutos.toDouble())})" else "Sin horario") { paso = 2 }
                            FilaResumen("Responsable", responsable.ifBlank { "Sin nombre" }) { paso = 2 }
                            FilaResumen("Fotos del día", "${db.fotosDe(fecha).size} de 4") { paso = 3 }
                        }
                    }
                    Kpi("Área cubierta", if (area != null) "${Fmt.fijo(area)} m²" else "–", "", Modifier.fillMaxWidth().padding(top = 10.dp), destacado = true)
                    Pregunta("Observaciones (opcional)")
                    Campo("Demoras, interferencias, filtraciones…", notas, { notas = it }, lineas = 3)
                    if (construir() == null) Etiqueta("Falta corregir el tramo (paso 2) antes de guardar.", 2)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmarBorrar) AlertDialog(
        onDismissRequest = { confirmarBorrar = false },
        title = { Text("¿Eliminar este registro?") },
        text = { Text("No se puede deshacer. Las fotos del día se conservan.", fontSize = 16.sp) },
        confirmButton = { TextButton(onClick = { confirmarBorrar = false; alEliminar() }) { Text("Sí, eliminar", color = MaterialTheme.colorScheme.error, fontSize = 16.sp) } },
        dismissButton = { TextButton(onClick = { confirmarBorrar = false }) { Text("No", fontSize = 16.sp) } }
    )
    if (confirmarSalir) AlertDialog(
        onDismissRequest = { confirmarSalir = false },
        title = { Text("¿Salir sin guardar?") },
        text = { Text("Se pierde lo que escribiste en este registro. Las fotos del día sí quedan guardadas.", fontSize = 16.sp) },
        confirmButton = { TextButton(onClick = { confirmarSalir = false; alCancelar() }) { Text("Salir sin guardar", color = MaterialTheme.colorScheme.error, fontSize = 16.sp) } },
        dismissButton = { TextButton(onClick = { confirmarSalir = false }) { Text("Seguir aquí", fontSize = 16.sp) } }
    )
}
