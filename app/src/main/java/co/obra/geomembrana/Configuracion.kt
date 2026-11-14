package co.obra.geomembrana

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun t(d: Double?, dec: Int = 3) = d?.let { Fmt.num(it, dec).replace(".", "") } ?: ""

@Composable
fun ContenidoConfiguracion(
    p: Proyecto, tema: ModoTema, hayLogo: Boolean, pad: PaddingValues,
    alGuardar: (Proyecto) -> Unit, alTema: (ModoTema) -> Unit, alElegirLogo: () -> Unit, alQuitarLogo: () -> Unit
) {
    var nombre by rememberSaveable { mutableStateOf(p.nombre) }
    var empresa by rememberSaveable { mutableStateOf(p.empresa) }
    var titulo by rememberSaveable { mutableStateOf(p.titulo) }
    var codigo by rememberSaveable { mutableStateOf(p.codigoFormato) }
    var absIni by rememberSaveable { mutableStateOf(Abscisa.texto(p.absIni)) }
    var absFin by rememberSaveable { mutableStateOf(Abscisa.texto(p.absFin)) }
    // Secciones codificadas como texto para sobrevivir a la rotación: nombre|perímetro|terminada|grupo por línea
    var secciones by rememberSaveable {
        mutableStateOf(p.secciones.joinToString("\n") { "${it.nombre}|${t(it.perimetro)}|${it.terminada}|${it.grupo}" })
    }
    val filas = secciones.split("\n").filter { it.isNotEmpty() }.map { it.split("|") + List(4) { "" } }
    fun cambiar(i: Int, k: Int, v: String) {
        secciones = filas.mapIndexed { j, f -> (if (j == i) f.take(4).toMutableList().also { it[k] = v.replace("|", "/").replace("\n", " ") } else f.take(4)).joinToString("|") }
            .joinToString("\n")
    }
    var turnosDia by rememberSaveable { mutableStateOf(p.turnosPorDia) }
    var hDia by rememberSaveable { mutableStateOf(p.horaInicioDia) }
    var hNoche by rememberSaveable { mutableStateOf(p.horaInicioNoche) }
    var nEta by rememberSaveable { mutableStateOf(p.turnosParaEta.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val ini = Abscisa.leer(absIni); val fin = Abscisa.leer(absFin)

    Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Bloque("Proyecto") {
            Campo("Nombre del túnel / proyecto", nombre, { nombre = it })
            Campo("Empresa", empresa, { empresa = it })
            Campo("Título del informe", titulo, { titulo = it })
            Campo("Código del formato", codigo, { codigo = it })
        }
        Bloque("Geometría") {
            Campo("Abscisa inicial del túnel", absIni, { absIni = it }, ayuda = ini?.let { "= ${Fmt.num(it)} m" } ?: "Ej. K6+176.57", error = ini == null)
            Campo("Abscisa final del túnel", absFin, { absFin = it }, ayuda = fin?.let { "= ${Fmt.num(it)} m" } ?: "Ej. K7+173.00", error = fin == null)
            if (ini != null && fin != null && fin > ini) Text("Longitud total: ${Fmt.fijo(fin - ini)} m", fontSize = 16.sp)
        }
        Bloque("Secciones de geomembrana") {
            Text("Cada sección avanza por separado. Las del mismo grupo se reportan juntas (ej. Bota = HD + HI). " +
                "Marca “Terminada” cuando la sección se entrega completa.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            filas.forEachIndexed { i, f ->
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Campo("Nombre", f[0], { cambiar(i, 0, it) }, Modifier.weight(1.4f))
                    Campo("Perímetro", f[1], { cambiar(i, 1, it) }, Modifier.weight(1f), numerico = true, sufijo = "m")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Campo("Grupo", f[3], { cambiar(i, 3, it) }, Modifier.weight(1f))
                    Text("Terminada", fontSize = 14.sp)
                    androidx.compose.material3.Switch(checked = f[2] == "true", onCheckedChange = { cambiar(i, 2, it.toString()) })
                    androidx.compose.material3.TextButton(onClick = {
                        secciones = filas.filterIndexed { j, _ -> j != i }.joinToString("\n") { it.take(4).joinToString("|") }
                    }) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
                }
            }
            OutlinedButton(onClick = { secciones = (if (secciones.isBlank()) "" else secciones + "\n") + "Nueva sección||false|" },
                modifier = Modifier.padding(top = 8.dp)) { Text("Agregar sección") }
        }
        Bloque("Turnos y proyección") {
            Text("Turnos por día", style = MaterialTheme.typography.labelLarge)
            Opciones(listOf(1, 2), turnosDia, { if (it == 1) "1 (solo día)" else "2 (día y noche)" }) { turnosDia = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoHora("Inicio turno día", hDia, { hDia = it }, Modifier.weight(1f))
                if (turnosDia >= 2) CampoHora("Inicio turno noche", hNoche, { hNoche = it }, Modifier.weight(1f))
            }
            Campo("Turnos que se promedian para la proyección", nEta, { nEta = it.filter { c -> c.isDigit() } }, numerico = true)
        }
        error?.let { Etiqueta(it, 2) }
        BotonGrande("Guardar configuración", {
            val lista = filas.map { f -> Seccion(f[0].trim(), Fmt.leer(f[1]) ?: 0.0, f[2] == "true", f[3].trim()) }
            when {
                ini == null || fin == null -> error = "Revisa las abscisas del túnel."
                lista.isEmpty() -> error = "Debe haber al menos una sección."
                lista.any { it.nombre.isBlank() || it.perimetro <= 0.0 } -> error = "Cada sección necesita nombre y perímetro mayor que cero."
                lista.map { Texto.clave(it.nombre) }.distinct().size < lista.size -> error = "Hay secciones con el mismo nombre."
                fin <= ini -> error = "La abscisa final del túnel debe ser mayor que la inicial."
                else -> {
                    error = null
                    alGuardar(Proyecto(
                        nombre = nombre.trim().ifBlank { "Túnel" }, empresa = empresa.trim(),
                        titulo = titulo.trim().ifBlank { Proyecto().titulo }, codigoFormato = codigo.trim(),
                        absIni = ini, absFin = fin, secciones = lista, turnosPorDia = turnosDia,
                        horaInicioDia = hDia.ifBlank { "06:00" }, horaInicioNoche = hNoche.ifBlank { "18:00" },
                        turnosParaEta = (nEta.toIntOrNull() ?: 6).coerceIn(1, 60),
                        tempMin = p.tempMin, tempMax = p.tempMax, velMin = p.velMin, velMax = p.velMax
                    ))
                }
            }
        }, Modifier.fillMaxWidth().padding(vertical = 10.dp))

        Bloque("Pantalla") {
            Text("Modo de color", style = MaterialTheme.typography.labelLarge)
            Opciones(ModoTema.entries, tema, { it.etiqueta }) { alTema(it) }
        }
        Bloque("Logo de la empresa (sale en los PDF)") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = alElegirLogo) { Text(if (hayLogo) "Cambiar logo" else "Elegir logo") }
                if (hayLogo) OutlinedButton(onClick = alQuitarLogo) { Text("Quitar logo") }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
