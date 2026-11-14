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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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

    // Al abrir la app: borra fotos que quedaron de registros nuevos que no se guardaron
    // (no si la actividad se recreó con un registro nuevo abierto, p. ej. al volver de la cámara)
    LaunchedEffect(Unit) {
        if (!ruta.startsWith("nuevo:")) withContext(Dispatchers.IO) {
            val ids = db.registros().map { it.id }.toSet()
            db.fotos().filter { it.registroId !in ids }.forEach { Fotos.quitar(ctx, it, db) }
        }
        version++
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
                aviso("Registro guardado: ${Fmt.num(r.longitud)} m · ${Fmt.num(r.area, 1)} m²")
            },
            alEliminar = { Fotos.quitarTodas(ctx, id, db); db.eliminar(id); version++; ruta = "inicio"; aviso("Registro eliminado") },
            alCancelar = {
                if (original == null) { db.fotosDe(id).values.forEach { Fotos.quitar(ctx, it, db) } }
                version++; ruta = "inicio"
            },
            aviso = ::aviso
        )
        Overlays(trabajando, null, {}, {}, {}, {}, snack)
        return
    }

    BackHandler(enabled = ruta != "inicio") { ruta = "inicio" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when (ruta) { "estadisticas" -> "Estadísticas"; "config" -> "Configuración"; else -> "Control Geomembrana" },
                        fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (ruta != "inicio") IconButton(onClick = { ruta = "inicio" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menú") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Informe de avance (PDF)") }, onClick = { menu = false; preguntarFotos = true })
                        DropdownMenuItem(text = { Text("Exportar a Excel") }, onClick = { menu = false; excel() })
                        DropdownMenuItem(text = { Text("Estadísticas y proyección") }, onClick = { menu = false; ruta = "estadisticas" })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Guardar copia de seguridad") }, onClick = {
                            menu = false; exportarCopia.launch("Geomembrana_copia_${T.hoy()}.zip")
                        })
                        DropdownMenuItem(text = { Text("Restaurar copia de seguridad") }, onClick = { menu = false; confirmarRestaurar = true })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Configuración") }, onClick = { menu = false; ruta = "config" })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            if (ruta == "inicio") ExtendedFloatingActionButton(
                onClick = { ruta = "nuevo:" + T.uid() },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Nuevo registro", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        when (ruta) {
            "estadisticas" -> ContenidoEstadisticas(proyecto, registros, pad)
            "config" -> ContenidoConfiguracion(proyecto, tema, hayLogo, pad,
                alGuardar = { p -> proyecto = p; Ajustes.guardarProyecto(ctx, p); ruta = "inicio"; aviso("Configuración guardada") },
                alTema = alCambiarTema,
                alElegirLogo = { elegirLogo.launch("image/*") },
                alQuitarLogo = { Logo.quitar(ctx); hayLogo = Logo.cargar(ctx) != null; aviso("Logo quitado") })
            else -> ContenidoInicio(
                proyecto, registros, fotos.groupBy { it.registroId }.mapValues { it.value.size }, pad,
                alAbrir = { ruta = "editar:" + it.id }, alReporteDia = { reporteDia(it) }, alEstadisticas = { ruta = "estadisticas" }
            )
        }
    }

    Overlays(trabajando, listo, { l -> if (!abrir(ctx, l.archivo, l.mime)) aviso("No hay una app para abrir este archivo") },
        { l -> compartir(ctx, l.archivo, l.mime) },
        { l -> if (l.mime == MIME_PDF) guardarPdf.launch(l.archivo.name) else guardarXlsx.launch(l.archivo.name) },
        { listo = null }, null)

    if (preguntarFotos) AlertDialog(
        onDismissRequest = { preguntarFotos = false },
        title = { Text("Informe de avance") },
        text = { Text("¿Incluir el anexo con las fotos de todos los registros? El archivo puede quedar pesado.") },
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
