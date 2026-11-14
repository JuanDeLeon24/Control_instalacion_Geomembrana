@file:OptIn(ExperimentalMaterial3Api::class)

package co.obra.geomembrana

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Db(applicationContext)
        setContent {
            var tema by remember { mutableStateOf(Ajustes.tema(applicationContext)) }
            TemaObra(tema) { App(db, tema) { tema = it; Ajustes.guardarTema(applicationContext, it) } }
        }
    }
}

internal fun uriDe(ctx: Context, f: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".archivos", f)

internal fun compartir(ctx: Context, f: File, mime: String) {
    val i = Intent(Intent.ACTION_SEND).setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uriDe(ctx, f))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "Compartir archivo"))
}

internal fun abrir(ctx: Context, f: File, mime: String): Boolean = try {
    ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uriDe(ctx, f), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    true
} catch (e: ActivityNotFoundException) { false }

private const val MIME_PDF = "application/pdf"
private const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Archivo generado, esperando que el usuario lo abra, comparta o guarde. */
private data class Listo(val archivo: File, val mime: String)

@Composable
fun App(db: Db, tema: ModoTema, alCambiarTema: (ModoTema) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    fun aviso(msg: String) { scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(msg) } }

    var version by remember { mutableIntStateOf(0) }
    val registros = remember(version) { db.registros() }
    val fotos = remember(version) { db.fotos() }
    var proyecto by remember { mutableStateOf(Ajustes.proyecto(ctx)) }
    var hayLogo by remember { mutableStateOf(Logo.cargar(ctx) != null) }

    // Rutas: "inicio", "estadisticas", "config", "nuevo:<id>", "editar:<id>"
    var ruta by rememberSaveable { mutableStateOf("inicio") }
    var menu by remember { mutableStateOf(false) }
    var trabajando by remember { mutableStateOf<String?>(null) }
    var listo by remember { mutableStateOf<Listo?>(null) }
    var preguntarFotos by remember { mutableStateOf(false) }
    var confirmarRestaurar by remember { mutableStateOf(false) }

    var fotosDia by remember { mutableStateOf<String?>(null) }
    var resultadoImport by remember { mutableStateOf<Importacion.Resultado?>(null) }

    /** Agrega registros importados, sin repetir los que ya existen (misma sección, fecha y abscisas). */
    fun agregarImportados(r: Importacion.Resultado): Int {
        val existentes = db.registros().map { Importacion.clave(it) }.toSet()
        val nuevos = r.registros.filter { Importacion.clave(it) !in existentes }
        if (r.seccionesNuevas.isNotEmpty()) {
            proyecto = proyecto.copy(secciones = proyecto.secciones + r.seccionesNuevas)
            Ajustes.guardarProyecto(ctx, proyecto)
        }
        db.agregar(nuevos)
        return nuevos.size
    }

    // Primera apertura: carga el avance ya ejecutado que viene con la app (assets/avance_inicial.csv)
    LaunchedEffect(Unit) {
        if (!Ajustes.avanceInicialCargado(ctx)) {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    val txt = ctx.assets.open("avance_inicial.csv").use { it.readBytes().toString(Charsets.UTF_8) }
                    agregarImportados(Importacion.leer(txt, proyecto))
                }.getOrDefault(-1)
            }
            Ajustes.marcarAvanceInicial(ctx)
            if (n > 0) { version++; aviso("Se cargaron $n registros de avance ya ejecutados") }
        }
        // Copia automática del día en Descargas/ControlGeomembrana (sobrevive a una desinstalación)
        withContext(Dispatchers.IO) { runCatching { Autocopia.siToca(ctx, db) } }
    }

    fun generar(etiqueta: String, mime: String, trabajo: () -> File) {
        trabajando = etiqueta
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { trabajo() } }
            trabajando = null
            r.onSuccess { listo = Listo(it, mime) }.onFailure { aviso("No se pudo generar el archivo: ${it.message ?: "error"}") }
        }
    }
    fun reporteDia(dia: String) = generar("Generando reporte del ${T.fechaCorta(dia)}…", MIME_PDF) {
        Pdf.generar(ctx, db.registros(), db.fotos(), proyecto, Logo.cargar(ctx), dia, conFotos = true)
    }
    fun informe(conFotos: Boolean) = generar("Generando informe de avance…", MIME_PDF) {
        Pdf.generar(ctx, db.registros(), db.fotos(), proyecto, Logo.cargar(ctx), null, conFotos)
    }
    fun excel() = generar("Generando Excel…", MIME_XLSX) { Excel.avance(ctx, db.registros(), db.fotos(), proyecto) }

    // ---------- Selectores de archivos del sistema ----------
    fun copiarA(uri: android.net.Uri?, f: File?) {
        if (uri == null || f == null) return
        runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { o -> f.inputStream().use { it.copyTo(o) } } }
            .onSuccess { aviso("Archivo guardado") }.onFailure { aviso("No se pudo guardar el archivo") }
    }
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_PDF)) { copiarA(it, listo?.archivo) }
    val guardarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_XLSX)) { copiarA(it, listo?.archivo) }
    val exportarCopia = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            trabajando = "Guardando copia de seguridad…"
            scope.launch {
                val r = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { Respaldo.exportar(ctx, db, it) } } }
                trabajando = null
                aviso(if (r.isSuccess) "Copia de seguridad guardada (datos y fotos)" else "No se pudo guardar la copia")
            }
        }
    }
    val importarCopia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            trabajando = "Restaurando copia…"
            scope.launch {
                val r = runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { Respaldo.importar(ctx, db, it) } } }
                trabajando = null
                r.onSuccess { n -> proyecto = Ajustes.proyecto(ctx); version++; aviso("Se restauraron $n registros") }
                    .onFailure { e -> aviso((e as? IllegalArgumentException)?.message ?: "Ese archivo no es una copia válida de esta app") }
            }
        }
    }
    val importarCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val txt = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    Importacion.leer(txt, proyecto)
                }
            }
            r.onSuccess { resultadoImport = it }.onFailure { aviso("No se pudo leer el archivo") }
        }
    }
    val elegirLogo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { if (Logo.guardar(ctx, uri)) { hayLogo = true; aviso("Logo actualizado") } else aviso("No se pudo leer esa imagen") }
    }

    // ---------- Formulario ----------
    if (ruta.startsWith("nuevo:") || ruta.startsWith("editar:")) {
        val id = ruta.substringAfter(':')
        val original = registros.firstOrNull { it.id == id }
        PantallaFormulario(
            db = db, p = proyecto, registros = registros, original = original, registroId = id,
            alGuardar = { r ->
                db.guardar(r); Ajustes.recordar(ctx, r); version++; ruta = "inicio"
                // Actualiza la copia del día en Descargas con lo recién guardado
                scope.launch(Dispatchers.IO) { runCatching { Autocopia.hacer(ctx, db) } }
                aviso("Registro guardado: ${r.seccion} ${Fmt.num(r.longitud)} m · ${Fmt.num(r.area, 2)} m²")
            },
            alEliminar = { db.eliminar(id); version++; ruta = "inicio"; aviso("Registro eliminado") },
            alCancelar = { version++; ruta = "inicio" },
            aviso = ::aviso
        )
        Overlays(trabajando, null, {}, {}, {}, {}, snack)
        return
    }

    // Pestañas de la barra inferior y pantallas internas (con flecha para volver)
    val pestana = when (ruta) { "informes" -> "informes"; "mas", "config" -> "mas"; "estadisticas" -> "informes"; else -> "inicio" }
    val interna = ruta == "estadisticas" || ruta == "config"
    BackHandler(enabled = ruta != "inicio") { ruta = if (interna) pestana else "inicio" }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when (ruta) {
                        "estadisticas" -> "Estadísticas y curva S"; "config" -> "Configuración"
                        "informes" -> "Informes"; "mas" -> "Más opciones"; else -> "Control Geomembrana"
                    }, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (interna) IconButton(onClick = { ruta = pestana }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple("inicio", Icons.Filled.Home, "Inicio"),
                    Triple("registrar", Icons.Filled.AddCircle, "Registrar"),
                    Triple("informes", Icons.Filled.Share, "Informes"),
                    Triple("mas", Icons.Filled.Menu, "Más")
                ).forEach { (clave, icono, texto) ->
                    NavigationBarItem(
                        selected = pestana == clave,
                        onClick = { ruta = if (clave == "registrar") "nuevo:" + T.uid() else clave },
                        icon = { Icon(icono, null, Modifier.size(30.dp)) },
                        label = { Text(texto, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        when (ruta) {
            "estadisticas" -> ContenidoEstadisticas(proyecto, registros, pad)
            "config" -> ContenidoConfiguracion(proyecto, tema, hayLogo, pad,
                alGuardar = { p -> proyecto = p; Ajustes.guardarProyecto(ctx, p); ruta = "mas"; aviso("Configuración guardada") },
                alTema = alCambiarTema,
                alElegirLogo = { elegirLogo.launch("image/*") },
                alQuitarLogo = { Logo.quitar(ctx); hayLogo = Logo.cargar(ctx) != null; aviso("Logo quitado") })
            "informes" -> ContenidoInformes(proyecto, registros, pad,
                alReporteDia = { reporteDia(it) }, alInformeGeneral = { preguntarFotos = true }, alExcel = { excel() },
                alEstadisticas = { ruta = "estadisticas" })
            "mas" -> ContenidoMas(pad, Autocopia.ultima(ctx), version,
                alCopiaAhora = {
                    trabajando = "Guardando copia en Descargas…"
                    scope.launch {
                        val r = runCatching { withContext(Dispatchers.IO) { Autocopia.hacer(ctx, db) } }
                        trabajando = null
                        aviso(if (r.isSuccess) "Copia guardada en Descargas/ControlGeomembrana" else "No se pudo guardar la copia")
                    }
                },
                alCopiaOtroLugar = { exportarCopia.launch("Geomembrana_copia_${T.hoy()}.zip") },
                alRestaurar = { confirmarRestaurar = true },
                alImportar = { importarCsv.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                alConfiguracion = { ruta = "config" },
                alEstadisticas = { ruta = "estadisticas" })
            else -> ContenidoInicio(
                proyecto, registros, fotos.groupBy { it.jornada }.mapValues { it.value.size }, pad,
                alAbrir = { ruta = "editar:" + it.id }, alReporteDia = { reporteDia(it) }, alFotosDia = { fotosDia = it },
                alEstadisticas = { ruta = "estadisticas" }, alRegistrar = { ruta = "nuevo:" + T.uid() }
            )
        }
    }

    Overlays(trabajando, listo, { l -> if (!abrir(ctx, l.archivo, l.mime)) aviso("No hay una app para abrir este archivo") },
        { l -> compartir(ctx, l.archivo, l.mime) },
        { l -> if (l.mime == MIME_PDF) guardarPdf.launch(l.archivo.name) else guardarXlsx.launch(l.archivo.name) },
        { listo = null }, null)

    fotosDia?.let { dia ->
        AlertDialog(
            onDismissRequest = { fotosDia = null; version++ },
            title = { Text("Fotos de la jornada") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { FotosJornada(db, dia, ::aviso) { version++ } } },
            confirmButton = { TextButton(onClick = { fotosDia = null; version++ }) { Text("Listo") } }
        )
    }
    resultadoImport?.let { r ->
        val existentes = registros.map { Importacion.clave(it) }.toSet()
        val nuevos = r.registros.count { Importacion.clave(it) !in existentes }
        AlertDialog(
            onDismissRequest = { resultadoImport = null },
            title = { Text("Importar avance") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("${r.registros.size} registros leídos · $nuevos nuevos · ${r.registros.size - nuevos} ya estaban.", fontSize = 15.sp)
                    r.registros.groupBy { it.seccion }.forEach { (sec, l) ->
                        Text("• $sec: ${l.size} registros, ${Fmt.num(l.sumOf { it.longitud })} m, ${Fmt.num(l.sumOf { it.area ?: 0.0 }, 2)} m²", fontSize = 14.sp)
                    }
                    if (r.seccionesNuevas.isNotEmpty()) Text("Secciones nuevas: " + r.seccionesNuevas.joinToString { "${it.nombre} (${Fmt.num(it.perimetro)} m)" },
                        fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                    r.errores.take(15).forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                    if (r.errores.size > 15) Text("… y ${r.errores.size - 15} errores más", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = nuevos > 0, onClick = {
                    resultadoImport = null
                    val n = agregarImportados(r); version++; aviso("Se importaron $n registros")
                }) { Text("Importar $nuevos") }
            },
            dismissButton = { TextButton(onClick = { resultadoImport = null }) { Text("Cancelar") } }
        )
    }
    if (preguntarFotos) AlertDialog(
        onDismissRequest = { preguntarFotos = false },
        title = { Text("Informe de avance") },
        text = { Text("Incluye el avance global y por grupo (Bóveda / Bota), curva S y desgloses. ¿Agregar el anexo con las fotos de todas las jornadas? El archivo puede quedar pesado.") },
        confirmButton = { TextButton(onClick = { preguntarFotos = false; informe(true) }) { Text("Con fotos") } },
        dismissButton = { TextButton(onClick = { preguntarFotos = false; informe(false) }) { Text("Sin fotos") } }
    )
    if (confirmarRestaurar) AlertDialog(
        onDismissRequest = { confirmarRestaurar = false },
        title = { Text("Restaurar copia de seguridad") },
        text = { Text("Los registros y fotos actuales se reemplazan por los de la copia (.zip). ¿Continuar?") },
        confirmButton = {
            TextButton(onClick = {
                confirmarRestaurar = false
                importarCopia.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
            }) { Text("Elegir archivo") }
        },
        dismissButton = { TextButton(onClick = { confirmarRestaurar = false }) { Text("Cancelar") } }
    )
}

/** Indicador de trabajo en curso y diálogo de archivo listo. */
@Composable
private fun Overlays(
    trabajando: String?, listo: Listo?, alAbrir: (Listo) -> Unit, alCompartir: (Listo) -> Unit,
    alGuardar: (Listo) -> Unit, alCerrar: () -> Unit, snack: SnackbarHostState?
) {
    if (snack != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SnackbarHost(snack) }
    if (trabajando != null) AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text(trabajando, fontSize = 16.sp)
            }
        }
    )
    if (listo != null) AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Archivo listo") },
        text = {
            Column {
                Text(listo.archivo.name, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                Button(onClick = { alAbrir(listo) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir") }
                OutlinedButton(onClick = { alCompartir(listo) }, modifier = Modifier.fillMaxWidth()) { Text("Compartir (WhatsApp, correo…)") }
                OutlinedButton(onClick = { alGuardar(listo) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar en el teléfono") }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } }
    )
}
