@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package co.obra.geomembrana

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Contenido de la pantalla principal: panel de avance + registros agrupados por fecha. */
@Composable
fun ContenidoInicio(
    p: Proyecto, registros: List<Registro>, fotosPorRegistro: Map<String, Int>, pad: PaddingValues,
    alAbrir: (Registro) -> Unit, alReporteDia: (String) -> Unit, alEstadisticas: () -> Unit
) {
    val cob = Calc.cobertura(registros, p)
    val rend = Calc.rendimiento(registros)
    val eta = Calc.eta(registros, p, cob)
    val dias = Calc.porFecha(registros).reversed()
    val areaTot = Calc.areaTotal(p)

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 110.dp)) {
        item {
            Seccion(p.nombre) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Fmt.pct(cob.porcentaje), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.padding(6.dp))
                    Text("${Fmt.fijo(cob.cubierto)} de ${Fmt.fijo(cob.total)} m", fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                LinearProgressIndicator(progress = { cob.porcentaje.toFloat() }, modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = Amarillo, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(10.dp))
                FranjaTunel(p, registros)
                KpiFila(
                    { Kpi("Área instalada", "${Fmt.num(rend.area, 1)} m²", areaTot?.let { "de ${Fmt.num(it, 0)} m² (${Fmt.pct(rend.area / it)})" } ?: "", it) },
                    { Kpi("Pendiente", "${Fmt.num(cob.restante)} m", "${cob.pendientes.size} tramo(s)", it) }
                )
                KpiFila(
                    { Kpi("Rendimiento", "${Fmt.num(rend.mPorHora)} m/h", "${Fmt.num(rend.m2PorHora, 1)} m²/h", it) },
                    { Kpi("Terminación estimada",
                        if (eta.completado) "Completado" else eta.fechaHora?.let { d -> T.fechaHora(d) } ?: "–",
                        eta.mPorTurno?.let { m -> "${Fmt.num(m)} m/turno · ${Fmt.num(eta.turnosRestantes, 1)} turnos" } ?: "Registra avances para calcularla",
                        it, destacado = true) }
                )
                OutlinedButton(onClick = alEstadisticas, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("Ver estadísticas y proyección", fontSize = 16.sp)
                }
            }
        }
        if (dias.isEmpty()) item {
            Text("Aún no hay registros. Toca “Nuevo registro” para anotar el primer tramo instalado.",
                fontSize = 16.sp, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        dias.forEach { d ->
            item(key = "dia-" + d.fecha) {
                val r = d.rend
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(T.fechaLarga(d.fecha), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("${Fmt.num(r.longitud)} m  ·  ${Fmt.num(r.area, 1)} m² cubiertos  ·  ${Fmt.duracion(r.minutos.toDouble())}",
                            fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        OutlinedButton(onClick = { alReporteDia(d.fecha) }, modifier = Modifier.padding(top = 6.dp)) {
                            Text("Reporte diario (PDF con fotos)")
                        }
                    }
                }
            }
            items(d.registros.sortedWith(compareBy({ Turnos.orden(it.turno) }, { it.absIni })), key = { it.id }) { r ->
                TarjetaRegistro(r, p, fotosPorRegistro[r.id] ?: 0) { alAbrir(r) }
            }
        }
    }
}

@Composable
private fun TarjetaRegistro(r: Registro, p: Proyecto, nFotos: Int, alTocar: () -> Unit) {
    val alertas = Calc.alertas(r, p).filter { !it.startsWith("Longitud") }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = alTocar),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Abscisa.texto(r.absIni)} → ${Abscisa.texto(r.absFin)}", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(r.turno, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "${Fmt.num(r.longitud)} m × ${Fmt.num(r.perimetro)} m = ${Fmt.num(r.area, 1)} m²" + if (r.longitudManual != null) "  (ajustada)" else "",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary
            )
            val partes = listOfNotNull(
                if (r.horaIni.isNotBlank() || r.horaFin.isNotBlank()) "${r.horaIni}–${r.horaFin}" else null,
                r.minutos?.takeIf { it > 0 }?.let { Fmt.duracion(it.toDouble()) },
                r.responsable.ifBlank { null },
                if (r.rollos.isNotEmpty()) "Rollos: ${r.rollos.joinToString(", ")}" else null
            )
            if (partes.isNotEmpty()) Text(partes.joinToString("  ·  "), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 6.dp)) {
                Etiqueta("Fotos $nFotos/4", if (nFotos == 4) 0 else 1)
                Etiqueta("Presión: ${r.prueba.etiqueta}", when (r.prueba) { Prueba.APROBADO -> 0; Prueba.NA -> 1; Prueba.NO_APROBADO -> 2 })
                alertas.filter { !it.startsWith("Prueba") }.forEach { Etiqueta(it, 2) }
            }
            if (r.notas.isNotBlank()) Text(r.notas, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 3)
        }
    }
}
