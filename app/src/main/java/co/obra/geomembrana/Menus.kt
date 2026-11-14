package co.obra.geomembrana

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Botón grande de menú: ícono, título y explicación en lenguaje sencillo. */
@Composable
fun OpcionMenu(icono: String, titulo: String, detalle: String, destacada: Boolean = false, alTocar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = alTocar),
        colors = CardDefaults.cardColors(
            containerColor = if (destacada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icono, fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = if (destacada) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                if (detalle.isNotBlank()) Text(detalle, fontSize = 14.sp,
                    color = if (destacada) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 28.sp, color = if (destacada) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun Subtitulo(texto: String) {
    Text(texto, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
}

/** Pestaña "Informes": todo lo que se puede sacar de la app, con botones que dicen lo que hacen. */
@Composable
fun ContenidoInformes(
    p: Proyecto, registros: List<Registro>, pad: PaddingValues,
    alReporteDia: (String) -> Unit, alInformeGeneral: () -> Unit, alExcel: () -> Unit, alEstadisticas: () -> Unit
) {
    val ctx = LocalContext.current
    val dias = Calc.porFecha(registros).reversed().take(5)
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Subtitulo("REPORTE DIARIO (PDF con fotos)")
        if (dias.isEmpty()) Text("Aún no hay días registrados.", fontSize = 16.sp)
        dias.forEach { d ->
            val resumen = Calc.metricas(d.registros, p).joinToString("  ·  ") { "${it.nombre} ${Fmt.num(it.total.area, 2)} m²" }
            OpcionMenu("📄", if (d.fecha == T.hoy()) "Hoy" else T.fechaLarga(d.fecha), resumen, destacada = d.fecha == T.hoy()) { alReporteDia(d.fecha) }
        }
        OpcionMenu("📅", "Otro día…", "Elegir la fecha en el calendario") {
            val hoy = LocalDate.now()
            DatePickerDialog(ctx, { _, y, m, dd -> alReporteDia(LocalDate.of(y, m + 1, dd).toString()) }, hoy.year, hoy.monthValue - 1, hoy.dayOfMonth).show()
        }

        Subtitulo("AVANCE GENERAL")
        OpcionMenu("📊", "Informe de avance (PDF)", "Bóveda y bota por separado, curva S, avance por fecha", alTocar = alInformeGeneral)
        OpcionMenu("📗", "Tabla en Excel", "Todos los registros, resumen y datos de la curva S", alTocar = alExcel)
        OpcionMenu("📈", "Ver estadísticas en pantalla", "Curva S, proyección y rendimientos", alTocar = alEstadisticas)
        Spacer(Modifier.height(30.dp))
    }
}

/** Pestaña "Más": copias de seguridad, importar y configuración. */
@Composable
fun ContenidoMas(
    pad: PaddingValues, ultimaCopia: String, version: String,
    alCopiaAhora: () -> Unit, alCopiaOtroLugar: () -> Unit, alRestaurar: () -> Unit,
    alImportar: () -> Unit, alConfiguracion: () -> Unit, alEstadisticas: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Subtitulo("COPIAS DE SEGURIDAD")
        Text("La app guarda sola una copia cada día en la carpeta Descargas/ControlGeomembrana del teléfono." +
            if (ultimaCopia.isNotBlank()) " Última: ${T.fechaCorta(ultimaCopia)}." else "",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OpcionMenu("💾", "Guardar copia ahora", "En Descargas/ControlGeomembrana", alTocar = alCopiaAhora)
        OpcionMenu("📤", "Guardar copia en otro lugar", "Drive, correo, memoria…", alTocar = alCopiaOtroLugar)
        OpcionMenu("♻️", "Recuperar una copia", "Reemplaza los datos del teléfono por los de la copia", alTocar = alRestaurar)

        Subtitulo("DATOS")
        OpcionMenu("📥", "Importar avance desde archivo", "CSV o texto con Sección, Fecha, PK inicial, PK final…", alTocar = alImportar)
        OpcionMenu("📈", "Estadísticas y curva S", "", alTocar = alEstadisticas)

        Subtitulo("AJUSTES")
        OpcionMenu("⚙️", "Configuración del proyecto", "Túnel, secciones, turnos, colores y logo", alTocar = alConfiguracion)
        Text("Control Geomembrana · versión $version", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp))
        Spacer(Modifier.height(30.dp))
    }
}
