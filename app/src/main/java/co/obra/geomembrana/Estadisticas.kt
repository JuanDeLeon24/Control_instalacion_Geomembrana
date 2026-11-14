package co.obra.geomembrana

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Linea(k: String, v: String, fuerte: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(k, fontSize = 15.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, fontSize = 15.sp, fontWeight = if (fuerte) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
fun ContenidoEstadisticas(p: Proyecto, registros: List<Registro>, pad: PaddingValues) {
    val cob = Calc.cobertura(registros, p)
    val rend = Calc.rendimiento(registros)
    val eta = Calc.eta(registros, p, cob)
    val areaTot = Calc.areaTotal(p)
    val porFecha = Calc.porFecha(registros)
    val turnos = Calc.porTurno(registros)
    val q = Calc.calidad(registros, p)
    val traslapos = Calc.todosLosTraslapos(registros)

    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Seccion("Avance acumulado") {
            FranjaTunel(p, registros, alto = 34)
            Spacer(Modifier.height(8.dp))
            Linea("Longitud del túnel", "${Fmt.fijo(cob.total)} m")
            Linea("Cubierto (sin contar traslapos)", "${Fmt.fijo(cob.cubierto)} m · ${Fmt.pct(cob.porcentaje)}", true)
            Linea("Pendiente", "${Fmt.fijo(cob.restante)} m")
            Linea("Longitud registrada (suma)", "${Fmt.fijo(rend.longitud)} m")
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Linea("Área instalada", "${Fmt.fijo(rend.area)} m²", true)
            areaTot?.let { Linea("Área total estimada (perímetro ${Fmt.num(p.perimetroDefecto)} m)", "${Fmt.fijo(it)} m² · ${Fmt.pct(rend.area / it)}") }
        }

        Seccion("Rendimiento") {
            KpiFila(
                { Kpi("Metros por hora", Fmt.num(rend.mPorHora), "m/h", it) },
                { Kpi("Área por hora", Fmt.num(rend.m2PorHora, 1), "m²/h", it) }
            )
            Linea("Horas trabajadas", Fmt.duracion(rend.minutos.toDouble()))
            Linea("Turnos con registros", "${turnos.size}")
            Linea("Promedio por turno (todos)", if (turnos.isEmpty()) "–" else "${Fmt.num(rend.longitud / turnos.size)} m · ${Fmt.num(rend.area / turnos.size, 1)} m²")
        }

        Seccion("Proyección de terminación") {
            if (eta.completado) {
                Etiqueta("Geomembrana del túnel completada", 0)
            } else {
                Kpi("Fecha y hora estimadas", T.fechaHora(eta.fechaHora),
                    if (eta.fechaHora == null) "Se necesitan registros con avance" else "", Modifier.fillMaxWidth(), destacado = true)
                Spacer(Modifier.height(8.dp))
                Linea("Turnos promediados (últimos)", "${eta.turnosUsados} de ${turnos.size}")
                Linea("Avance promedio por turno", eta.mPorTurno?.let { "${Fmt.num(it)} m" } ?: "–")
                Linea("Horas promedio por turno", eta.horasPorTurno?.let { Fmt.duracion(it * 60) } ?: "–")
                Linea("Rendimiento reciente", eta.mPorHora?.let { "${Fmt.num(it)} m/h" } ?: "–")
                Linea("Faltan", "${Fmt.fijo(eta.restante)} m")
                Linea("Turnos restantes", Fmt.num(eta.turnosRestantes, 1))
                Linea("Días restantes (${p.turnosPorDia} turno${if (p.turnosPorDia == 1) "" else "s"}/día)", Fmt.num(eta.diasRestantes, 1))
                Text("Se proyecta desde el último turno registrado, con el promedio de los últimos ${p.turnosParaEta} turnos " +
                    "y turnos que inician a las ${p.horaInicioDia}" + (if (p.turnosPorDia >= 2) " y ${p.horaInicioNoche}" else "") + ".",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }

        if (porFecha.isNotEmpty()) Seccion("Avance por fecha") {
            val max = porFecha.maxOf { it.rend.longitud }.takeIf { it > 0 } ?: 1.0
            porFecha.reversed().forEach { d ->
                val r = d.rend
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row {
                        Text(T.fechaCorta(d.fecha), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${Fmt.num(r.longitud)} m · ${Fmt.num(r.area, 1)} m² · ${Fmt.num(r.mPorHora)} m/h", fontSize = 14.sp)
                    }
                    Barra((r.longitud / max).toFloat())
                }
            }
        }

        val cuadrillas = Calc.porCuadrilla(registros)
        if (cuadrillas.isNotEmpty()) Seccion("Rendimiento por cuadrilla") {
            val max = cuadrillas.maxOf { it.longitud }.takeIf { it > 0 } ?: 1.0
            cuadrillas.forEach { c ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(c.nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${c.turnos} turno(s) · ${Fmt.num(c.longitud)} m · ${Fmt.num(c.area, 1)} m² · " +
                        "${Fmt.num(c.mPorHora)} m/h · ${Fmt.num(c.m2PorHora, 1)} m²/h", fontSize = 14.sp)
                    Barra((c.longitud / max).toFloat(), MaterialTheme.colorScheme.primary)
                }
            }
        }

        val rollos = Calc.porRollo(registros)
        if (rollos.isNotEmpty()) Seccion("Rollos (${rollos.size})") {
            rollos.forEach { r ->
                Linea(r.codigo, "${r.registros} reg. · ${Fmt.num(r.longitud)} m · ${Fmt.num(r.area, 1)} m²")
            }
            Text("Si un registro usa varios rollos, su longitud y área se reparten por igual entre ellos.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Seccion("Tramos pendientes (${cob.pendientes.size})") {
            if (cob.pendientes.isEmpty()) Etiqueta("Sin tramos pendientes", 0)
            cob.pendientes.forEach { t -> Linea("${Abscisa.texto(t.ini)} – ${Abscisa.texto(t.fin)}", "${Fmt.fijo(t.longitud)} m") }
            if (traslapos.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Traslapos entre registros", fontWeight = FontWeight.Bold)
                traslapos.forEach { t ->
                    Linea("${T.fechaCorta(t.a.fecha)} ${t.a.turno} / ${T.fechaCorta(t.b.fecha)} ${t.b.turno}",
                        "${Abscisa.texto(t.tramo.ini)} – ${Abscisa.texto(t.tramo.fin)} (${Fmt.num(t.tramo.longitud)} m)")
                }
            }
        }

        Seccion("Control de calidad") {
            Linea("Temperatura cuña / extrusora", if (q.conTemp == 0) "sin datos" else
                "${Fmt.num(q.tempProm, 1)} °C prom. (${Fmt.num(q.tempMin, 1)} a ${Fmt.num(q.tempMax, 1)})")
            q.tempFuera?.let { Linea("   fuera de especificación", "$it") }
            Linea("Velocidad de soldadura", if (q.conVel == 0) "sin datos" else
                "${Fmt.num(q.velProm)} m/min prom. (${Fmt.num(q.velMin)} a ${Fmt.num(q.velMax)})")
            q.velFuera?.let { Linea("   fuera de especificación", "$it") }
            Linea("Pruebas de presión", "${q.aprobadas} aprob. · ${q.noAprobadas} no aprob. · ${q.pruebasNA} N.A.")
            Linea("Geotextil", "${q.geoOk} OK · ${q.geoObs} con obs. · ${q.geoNo} sin inspección")
            q.alertas.forEach { (r, a) ->
                Spacer(Modifier.height(4.dp))
                Etiqueta("${T.fechaCorta(r.fecha)} ${r.turno} ${Abscisa.texto(r.absIni)}: ${a.joinToString(" · ")}",
                    if (a.any { it.contains("no aprobada") || it.contains("fuera") }) 2 else 1)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
