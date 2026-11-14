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
    var perim by rememberSaveable { mutableStateOf(t(p.perimetroDefecto)) }
    var turnosDia by rememberSaveable { mutableStateOf(p.turnosPorDia) }
    var hDia by rememberSaveable { mutableStateOf(p.horaInicioDia) }
    var hNoche by rememberSaveable { mutableStateOf(p.horaInicioNoche) }
    var nEta by rememberSaveable { mutableStateOf(p.turnosParaEta.toString()) }
    var tMin by rememberSaveable { mutableStateOf(t(p.tempMin, 1)) }
    var tMax by rememberSaveable { mutableStateOf(t(p.tempMax, 1)) }
    var vMin by rememberSaveable { mutableStateOf(t(p.velMin)) }
    var vMax by rememberSaveable { mutableStateOf(t(p.velMax)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val ini = Abscisa.leer(absIni); val fin = Abscisa.leer(absFin)

    Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Seccion("Proyecto") {
            Campo("Nombre del túnel / proyecto", nombre, { nombre = it })
            Campo("Empresa", empresa, { empresa = it })
            Campo("Título del informe", titulo, { titulo = it })
            Campo("Código del formato", codigo, { codigo = it })
        }
        Seccion("Geometría") {
            Campo("Abscisa inicial del túnel", absIni, { absIni = it }, ayuda = ini?.let { "= ${Fmt.num(it)} m" } ?: "Ej. K6+176.57", error = ini == null)
            Campo("Abscisa final del túnel", absFin, { absFin = it }, ayuda = fin?.let { "= ${Fmt.num(it)} m" } ?: "Ej. K7+173.00", error = fin == null)
            if (ini != null && fin != null && fin > ini) Text("Longitud total: ${Fmt.fijo(fin - ini)} m", fontSize = 16.sp)
            Campo("Perímetro de referencia (se propone en cada registro)", perim, { perim = it }, numerico = true, sufijo = "m")
        }
        Seccion("Turnos y proyección") {
            Text("Turnos por día", style = MaterialTheme.typography.labelLarge)
            Opciones(listOf(1, 2), turnosDia, { if (it == 1) "1 (solo día)" else "2 (día y noche)" }) { turnosDia = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CampoHora("Inicio turno día", hDia, { hDia = it }, Modifier.weight(1f))
                if (turnosDia >= 2) CampoHora("Inicio turno noche", hNoche, { hNoche = it }, Modifier.weight(1f))
            }
            Campo("Turnos que se promedian para la proyección", nEta, { nEta = it.filter { c -> c.isDigit() } }, numerico = true)
        }
        Seccion("Especificaciones de soldadura") {
            Text("Ajustar según la ficha técnica del fabricante de la geomembrana. Vacío = sin control.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Campo("Temp. mínima", tMin, { tMin = it }, Modifier.weight(1f), numerico = true, sufijo = "°C")
                Campo("Temp. máxima", tMax, { tMax = it }, Modifier.weight(1f), numerico = true, sufijo = "°C")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Campo("Vel. mínima", vMin, { vMin = it }, Modifier.weight(1f), numerico = true, sufijo = "m/min")
                Campo("Vel. máxima", vMax, { vMax = it }, Modifier.weight(1f), numerico = true, sufijo = "m/min")
            }
        }
        error?.let { Etiqueta(it, 2) }
        BotonGrande("Guardar configuración", {
            when {
                ini == null || fin == null -> error = "Revisa las abscisas del túnel."
                fin <= ini -> error = "La abscisa final del túnel debe ser mayor que la inicial."
                else -> {
                    error = null
                    alGuardar(Proyecto(
                        nombre = nombre.trim().ifBlank { "Túnel" }, empresa = empresa.trim(),
                        titulo = titulo.trim().ifBlank { Proyecto().titulo }, codigoFormato = codigo.trim(),
                        absIni = ini, absFin = fin, perimetroDefecto = Fmt.leer(perim), turnosPorDia = turnosDia,
                        horaInicioDia = hDia.ifBlank { "06:00" }, horaInicioNoche = hNoche.ifBlank { "18:00" },
                        turnosParaEta = (nEta.toIntOrNull() ?: 6).coerceIn(1, 60),
                        tempMin = Fmt.leer(tMin), tempMax = Fmt.leer(tMax), velMin = Fmt.leer(vMin), velMax = Fmt.leer(vMax)
                    ))
                }
            }
        }, Modifier.fillMaxWidth().padding(vertical = 10.dp))

        Seccion("Pantalla") {
            Text("Modo de color", style = MaterialTheme.typography.labelLarge)
            Opciones(ModoTema.entries, tema, { it.etiqueta }) { alTema(it) }
        }
        Seccion("Logo de la empresa (sale en los PDF)") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = alElegirLogo) { Text(if (hayLogo) "Cambiar logo" else "Elegir logo") }
                if (hayLogo) OutlinedButton(onClick = alQuitarLogo) { Text("Quitar logo") }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
