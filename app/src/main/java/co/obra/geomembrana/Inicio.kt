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

/** Tarjeta de avance de un grupo (Bóveda, Bota) con el detalle de sus secciones. */
@Composable
fun TarjetaGrupo(g: AvanceGrupo, p: Proyecto, registros: List<Registro>, compacta: Boolean = false) {
    Bloque(g.nombre + if (g.terminada) "  ✓ terminada" else "") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.pct(g.porcentaje), fontSize = if (compacta) 30.sp else 38.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(6.dp))
            Text("${Fmt.num(g.rend.area, 1)} m² instalados de ${Fmt.num(g.areaTotal, 0)} m²", fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        LinearProgressIndicator(progress = { g.porcentaje.toFloat() }, modifier = Modifier.fillMaxWidth().height(10.dp),
            color = Amarillo, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        g.secciones.forEach { s ->
            Spacer(Modifier.height(8.dp))
            if (g.secciones.size > 1 || s.seccion.nombre != g.nombre) {
                Text("${s.seccion.nombre} · ${Fmt.num(s.seccion.perimetro)} m · ${Fmt.num(s.cobertura.cubierto)} m (${Fmt.pct(s.porcentaje)})" +
                    if (s.seccion.terminada) " ✓" else "", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            FranjaTunel(p, Calc.deSeccion(registros, s.seccion.nombre), alto = if (compacta) 16 else 22)
        }
        if (!g.terminada) {
            val eta = g.etaFecha
            val sec = g.secciones.filter { !it.terminada }
            KpiFila(
                { Kpi("Pendiente", "${Fmt.num(g.pendiente)} m", sec.flatMap { it.cobertura.pendientes }.firstOrNull()?.let { t ->
                    "${Abscisa.texto(t.ini)} – ${Abscisa.texto(t.fin)}" } ?: "", it) },
                { Kpi("Terminación estimada", eta?.let { T.fechaHora(it) } ?: "–",
                    sec.firstNotNullOfOrNull { it.eta.mPorTurno }?.let { m -> "${Fmt.num(m)} m/turno (últimos ${p.turnosParaEta})" } ?: "Sin datos",
                    it, destacado = true) }
            )
        }
    }
}

/** Contenido de la pantalla principal: avance por grupo + registros agrupados por fecha. */
@Composable
fun ContenidoInicio(
    p: Proyecto, registros: List<Registro>, fotosPorDia: Map<String, Int>, pad: PaddingValues,
    alAbrir: (Registro) -> Unit, alReporteDia: (String) -> Unit, alFotosDia: (String) -> Unit, alEstadisticas: () -> Unit,
    alRegistrar: () -> Unit
) {
    val secciones = Calc.porSeccion(registros, p)
    val grupos = Calc.porGrupo(secciones).sortedBy { it.terminada }   // lo que está en curso, primero
    val dias = Calc.porFecha(registros).reversed()

    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 24.dp)) {
        item {
            Text("${p.nombre} · ${Abscisa.texto(p.absIni)} a ${Abscisa.texto(p.absFin)} · ${Fmt.num(p.longitudTotal)} m",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            // Acción principal, grande y arriba
            OpcionMenu("➕", "Registrar avance de hoy", T.fechaLarga(T.hoy()), destacada = true, alTocar = alRegistrar)
            val hoy = registros.filter { it.fecha == T.hoy() }
            if (hoy.isNotEmpty()) {
                val f = fotosPorDia[T.hoy()] ?: 0
                Text("Hoy: " + Calc.metricas(hoy, p).joinToString("  ·  ") { "${it.nombre} ${Fmt.num(it.total.area, 2)} m²" } +
                    "  ·  Fotos $f/4", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
            }
            Text("AVANCE", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp))
        }
        items(grupos, key = { "g-" + it.nombre }) { g -> TarjetaGrupo(g, p, registros, compacta = g.terminada) }
        item {
            OutlinedButton(onClick = alEstadisticas, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("📈  Ver curva S y proyección", fontSize = 16.sp)
            }
            Text("JORNADAS REGISTRADAS", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 14.dp))
            Text("Toca un registro para corregirlo.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (dias.isEmpty()) item {
            Text("Aún no hay registros. Toca “Nuevo registro” para anotar el primer tramo instalado.",
                fontSize = 16.sp, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        dias.forEach { d ->
            item(key = "dia-" + d.fecha) {
                val nFotos = fotosPorDia[d.fecha] ?: 0
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(T.fechaLarga(d.fecha), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Calc.metricas(d.registros, p).forEach { g ->
                            Text("${g.nombre}: ${Fmt.num(g.total.longitud)} m · ${Fmt.num(g.total.area, 2)} m²" +
                                (if (g.total.rollos > 0) " · " + Fmt.rollos(g.total.rollos, g.total.rollosEstimados) else "") +
                                (if (g.total.minutos > 0) " · ${Fmt.duracion(g.total.minutos.toDouble())}" else ""),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            OutlinedButton(onClick = { alFotosDia(d.fecha) }) { Text("📷 Fotos $nFotos/4", fontSize = 15.sp) }
                            OutlinedButton(onClick = { alReporteDia(d.fecha) }) { Text("📄 Reporte PDF", fontSize = 15.sp) }
                        }
                    }
                }
            }
            items(d.registros, key = { it.id }) { r -> TarjetaRegistro(r, p) { alAbrir(r) } }
        }
    }
}

@Composable
private fun TarjetaRegistro(r: Registro, p: Proyecto, alTocar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = alTocar),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.seccion, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(r.turno, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${Abscisa.texto(r.absIni)} → ${Abscisa.texto(r.absFin)}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "${Fmt.num(r.longitud)} m × ${Fmt.num(r.perimetro)} m = ${Fmt.num(r.area, 2)} m²" +
                    if (r.areaManual != null) "  (oficial)" else if (r.longitudManual != null) "  (long. ajustada)" else "",
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary
            )
            val partes = listOfNotNull(
                if (r.horaIni.isNotBlank() || r.horaFin.isNotBlank()) "${r.horaIni}–${r.horaFin}" else null,
                r.minutos?.takeIf { it > 0 }?.let { Fmt.duracion(it.toDouble()) },
                r.responsable.ifBlank { null },
                Fmt.rollos(r.numRollos, r.rollosEstimados).ifBlank { null }
            )
            if (partes.isNotEmpty()) Text(partes.joinToString("  ·  "), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.notas.isNotBlank()) Text(r.notas, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 3)
        }
    }
}
