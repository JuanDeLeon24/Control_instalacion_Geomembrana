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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

private const val TODO = "Todo el proyecto"

@Composable
fun ContenidoEstadisticas(p: Proyecto, registros: List<Registro>, pad: PaddingValues) {
    val todas = Calc.porSeccion(registros, p)
    val grupos = Calc.porGrupo(todas).sortedBy { it.terminada }
    var elegido by rememberSaveable { mutableStateOf(grupos.firstOrNull()?.nombre ?: TODO) }
    val grupo = grupos.firstOrNull { it.nombre == elegido }
    val secs = grupo?.secciones ?: todas
    val regs = grupo?.registros ?: registros
    val rend = Calc.rendimiento(regs)
    val porFecha = Calc.porFecha(regs)
    val traslapos = Calc.todosLosTraslapos(regs)

    Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Spacer(Modifier.height(8.dp))
        Opciones(grupos.map { it.nombre } + TODO, elegido, { it }) { elegido = it }

        Bloque("Avance acumulado · $elegido") {
            val pct = grupo?.porcentaje ?: Calc.avanceGlobal(todas)
            val areaTot = secs.sumOf { it.areaTotal }
            KpiFila(
                { Kpi("Avance (por área)", Fmt.pct(pct), "", it, destacado = true) },
                { Kpi("Área instalada", "${Fmt.num(rend.area, 1)} m²", "de ${Fmt.num(areaTot, 0)} m²", it) }
            )
            secs.forEach { s ->
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("${s.seccion.nombre} (perímetro ${Fmt.num(s.seccion.perimetro)} m)" + if (s.seccion.terminada) " ✓ terminada" else "",
                    fontWeight = FontWeight.Bold)
                FranjaTunel(p, s.registros, alto = 22)
                Linea("Cubierto (sin traslapos)", "${Fmt.fijo(s.cobertura.cubierto)} m · ${Fmt.pct(s.cobertura.porcentaje)}", true)
                Linea("Pendiente", if (s.seccion.terminada) "–" else "${Fmt.fijo(s.cobertura.restante)} m")
                Linea("Longitud registrada", "${Fmt.fijo(s.rend.longitud)} m")
                Linea("Área instalada", "${Fmt.fijo(s.rend.area)} m²")
            }
        }

        Bloque("Rendimiento · $elegido") {
            val turnos = Calc.porTurno(regs)
            Linea("Días con instalación", "${porFecha.size}")
            Linea("Promedio por día", if (porFecha.isEmpty()) "–" else "${Fmt.num(rend.longitud / porFecha.size)} m · ${Fmt.num(rend.area / porFecha.size, 1)} m²")
            Linea("Turnos registrados", "${turnos.size} (${turnos.count { it.turno == Turnos.DIA }} día · ${turnos.count { it.turno == Turnos.NOCHE }} noche)")
            Linea("Rollos usados", Fmt.rollos(rend.rollos, rend.rollosEstimados).ifBlank { "–" } +
                if (rend.rollos > 0 && rend.area > 0) " · ${Fmt.num(rend.area / rend.rollos, 1)} m²/rollo" else "")
            if (rend.minutos > 0) {
                Linea("Horas trabajadas (registros con horario)", Fmt.duracion(rend.minutos.toDouble()))
                Linea("Rendimiento por hora", "${Fmt.num(rend.mPorHora)} m/h · ${Fmt.num(rend.m2PorHora, 1)} m²/h", true)
            } else {
                Text("Para m/h y m²/h registra la hora de inicio y fin en cada registro.", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        secs.filter { !it.seccion.terminada }.forEach { s ->
            Bloque("Curva S y proyección · ${s.seccion.nombre}") {
                GraficoCurvaS(Calc.curvaS(registros, s.seccion, p))
                Spacer(Modifier.height(8.dp))
                val e = s.eta
                if (e.completado) Etiqueta("Sección completada", 0) else {
                    Kpi("Terminación estimada", T.fechaHora(e.fechaHora), if (e.fechaHora == null) "Se necesitan registros con avance" else "",
                        Modifier.fillMaxWidth(), destacado = true)
                    Linea("Turnos promediados (últimos)", "${e.turnosUsados}")
                    Linea("Avance promedio por turno", e.mPorTurno?.let { "${Fmt.num(it)} m · ${Fmt.num(it * s.seccion.perimetro, 1)} m²" } ?: "–")
                    Linea("Faltan", "${Fmt.fijo(e.restante)} m · ${Fmt.num(e.restante * s.seccion.perimetro, 1)} m²")
                    Linea("Turnos restantes", Fmt.num(e.turnosRestantes, 1))
                    Linea("Días restantes (${p.turnosPorDia} turno${if (p.turnosPorDia == 1) "" else "s"}/día)", Fmt.num(e.diasRestantes, 1))
                    Text("Proyectado desde el último registro con el promedio de los últimos ${p.turnosParaEta} turnos " +
                        "(línea amarilla) y con el promedio de todos los turnos (línea gris).",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        if (porFecha.isNotEmpty()) Bloque("Avance por fecha · $elegido") {
            val max = porFecha.maxOf { it.rend.area }.takeIf { it > 0 } ?: 1.0
            porFecha.reversed().forEach { d ->
                val r = d.rend
                Column(Modifier.padding(vertical = 4.dp)) {
                    Row {
                        Text(T.fechaCorta(d.fecha), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${Fmt.num(r.longitud)} m · ${Fmt.num(r.area, 2)} m²" + if (r.rollos > 0) " · " + Fmt.rollos(r.rollos, r.rollosEstimados) else "", fontSize = 14.sp)
                    }
                    Barra((r.area / max).toFloat())
                }
            }
        }

        val cuadrillas = Calc.porCuadrilla(regs).filter { it.nombre != "Sin asignar" || regs.isNotEmpty() }
        if (cuadrillas.isNotEmpty()) Bloque("Por cuadrilla / responsable") {
            val max = cuadrillas.maxOf { it.area }.takeIf { it > 0 } ?: 1.0
            cuadrillas.forEach { c ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(c.nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${c.turnos} turno(s) · ${Fmt.num(c.longitud)} m · ${Fmt.num(c.area, 1)} m²" +
                        (if (c.mPorHora != null) " · ${Fmt.num(c.mPorHora)} m/h · ${Fmt.num(c.m2PorHora, 1)} m²/h" else ""), fontSize = 14.sp)
                    Barra((c.area / max).toFloat(), MaterialTheme.colorScheme.primary)
                }
            }
        }

        Bloque("Tramos pendientes y traslapos") {
            val pend = secs.filter { !it.seccion.terminada }.flatMap { s -> s.cobertura.pendientes.map { s.seccion.nombre to it } }
            if (pend.isEmpty()) Etiqueta("Sin tramos pendientes", 0)
            pend.forEach { (n, t) -> Linea("$n: ${Abscisa.texto(t.ini)} – ${Abscisa.texto(t.fin)}", "${Fmt.fijo(t.longitud)} m") }
            if (traslapos.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Traslapos entre registros de la misma sección", fontWeight = FontWeight.Bold)
                traslapos.forEach { t ->
                    Linea("${t.a.seccion}: ${T.fechaCorta(t.a.fecha)} / ${T.fechaCorta(t.b.fecha)}",
                        "${Abscisa.texto(t.tramo.ini)} – ${Abscisa.texto(t.tramo.fin)} (${Fmt.num(t.tramo.longitud)} m)")
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
