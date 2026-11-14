package co.obra.geomembrana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class CalculosTest {
    private val p = Proyecto()

    private fun reg(
        ini: Double, fin: Double, fecha: String = "2026-11-10", turno: String = Turnos.DIA,
        hi: String = "07:00", hf: String = "17:00", perim: Double? = 12.5, manual: Double? = null,
        rollos: List<String> = emptyList(), cuadrilla: String = "", temp: Double? = null, vel: Double? = null,
        prueba: Prueba = Prueba.APROBADO, geo: Geotextil = Geotextil.OK, id: String = "$fecha-$turno-$ini",
        seccion: String = "Bóveda", areaManual: Double? = null
    ) = Registro(id, fecha, turno, seccion, "Residente", cuadrilla, ini, fin, hi, hf, manual, perim, areaManual, rollos, null, temp, vel,
        prueba, geo, "", id)

    @Test fun abscisas() {
        assertEquals(6176.57, Abscisa.leer("K6+176.57")!!, 1e-9)
        assertEquals(6176.57, Abscisa.leer(" k6+176,57 ")!!, 1e-9)
        assertEquals(7173.0, Abscisa.leer("7+173")!!, 1e-9)
        assertEquals(6200.0, Abscisa.leer("6200")!!, 1e-9)
        assertNull(Abscisa.leer("K6+1176"))
        assertNull(Abscisa.leer("abc"))
        assertNull(Abscisa.leer(""))
        assertEquals("K6+176.57", Abscisa.texto(6176.57))
        assertEquals("K7+173.00", Abscisa.texto(7173.0))
        assertEquals("K7+000.00", Abscisa.texto(6999.999))
        assertEquals("K6+005.10", Abscisa.texto(6005.1))
    }

    @Test fun longitudTotal() {
        assertEquals(996.43, p.longitudTotal, 1e-9)
    }

    @Test fun validacion() {
        assertTrue(Calc.errores(6176.57, 6200.0).isEmpty())
        assertTrue(Calc.errores(6300.0, 6200.0).isEmpty())          // sentido descendente permitido
        assertEquals(1, Calc.errores(6200.0, 6200.0).size)
        assertEquals(2, Calc.errores(null, null).size)
        assertNull(Calc.fueraDeRango(7173.0, 7153.0, p))
        assertNotNull(Calc.fueraDeRango(7173.35, 7153.0, p))
    }

    @Test fun coberturaConTraslapoYPendientes() {
        val rs = listOf(reg(6176.57, 6200.0), reg(6195.0, 6220.0), reg(6300.0, 6310.0))
        val c = Calc.cobertura(rs, p)
        assertEquals(43.43 + 10.0, c.cubierto, 1e-6)
        assertEquals(2, c.tramos.size)
        assertEquals(2, c.pendientes.size)
        assertEquals(6220.0, c.pendientes[0].ini, 1e-9)
        assertEquals(7173.0, c.pendientes[1].fin, 1e-9)
        assertEquals(996.43 - 53.43, c.restante, 1e-6)
        assertEquals(1, Calc.todosLosTraslapos(rs).size)
        assertEquals(5.0, Calc.todosLosTraslapos(rs)[0].tramo.longitud, 1e-9)
        // Misma zona en otra sección: no es traslapo
        assertEquals(1, Calc.todosLosTraslapos(rs + reg(6176.57, 6200.0, seccion = "Bota HD")).size)
    }

    @Test fun sentidoDescendenteYAreaOficial() {
        val r = reg(7173.0, 7153.0, perim = 2.0, areaManual = 40.70)
        assertEquals(20.0, r.longitud, 1e-9)
        assertEquals(7153.0, r.desde, 1e-9)
        assertEquals(40.70, r.area!!, 1e-9)
        assertEquals(20.0, Calc.cobertura(listOf(r), p).cubierto, 1e-9)
        assertEquals(7153.0, Calc.siguiente(listOf(r))!!, 1e-9)
    }

    @Test fun rendimientoYManual() {
        val r = reg(6200.0, 6220.0, hi = "22:00", hf = "02:00", turno = Turnos.NOCHE, manual = 18.0)
        assertEquals(240, r.minutos)
        assertEquals(18.0, r.longitud, 1e-9)
        assertEquals(225.0, r.area!!, 1e-9)
        val rend = Calc.rendimiento(listOf(r))
        assertEquals(4.5, rend.mPorHora!!, 1e-9)
        assertEquals(56.25, rend.m2PorHora!!, 1e-9)
        assertTrue(Calc.alertas(r, p).any { it.startsWith("Longitud ajustada") })
    }

    @Test fun etaDosTurnosPorDia() {
        // 2 turnos de 20 m cada uno; quedan 996.43 - 40 = 956.43 m -> 47.82 turnos
        val rs = listOf(
            reg(6176.57, 6196.57, fecha = "2026-11-10", turno = Turnos.DIA, hi = "06:00", hf = "16:00"),
            reg(6196.57, 6216.57, fecha = "2026-11-10", turno = Turnos.NOCHE, hi = "18:00", hf = "04:00")
        )
        val e = Calc.eta(rs, p.copy(turnosPorDia = 2))
        assertFalse(e.completado)
        assertEquals(20.0, e.mPorTurno!!, 1e-9)
        assertEquals(2.0, e.mPorHora!!, 1e-9)
        assertEquals(10.0, e.horasPorTurno!!, 1e-9)
        assertEquals(47.8215, e.turnosRestantes!!, 1e-4)
        // 47 turnos completos después de la noche del 10 -> el turno 48 es la noche del 2026-12-04;
        // la fracción 0.8215 × 10 h = 8 h 13 min desde las 18:00
        assertEquals(LocalDateTime.of(2026, 12, 5, 2, 13), e.fechaHora)
    }

    @Test fun etaUnTurnoPorDiaYCompletado() {
        val p1 = p.copy(turnosPorDia = 1, absFin = 6276.57)   // túnel de 100 m
        val rs = listOf(reg(6176.57, 6226.57, fecha = "2026-11-10", hi = "06:00", hf = "16:00"))
        val e = Calc.eta(rs, p1)
        assertEquals(1.0, e.turnosRestantes!!, 1e-9)
        assertEquals(LocalDateTime.of(2026, 11, 11, 16, 0), e.fechaHora)
        val fin = Calc.eta(rs + reg(6226.57, 6276.57, fecha = "2026-11-11"), p1)
        assertTrue(fin.completado)
        assertNull(fin.fechaHora)
    }

    @Test fun etaSinDatos() {
        val e = Calc.eta(emptyList(), p)
        assertFalse(e.completado)
        assertNull(e.fechaHora)
        assertEquals(996.43, e.restante, 1e-9)
    }

    @Test fun desgloses() {
        val rs = listOf(
            reg(6176.57, 6196.57, rollos = listOf("R-01", "r-02"), cuadrilla = "Cuadrilla A"),
            reg(6196.57, 6206.57, fecha = "2026-11-11", rollos = listOf("R-02"), cuadrilla = "Cuadrilla B", temp = 500.0),
        )
        val ro = Calc.porRollo(rs)
        assertEquals(listOf("R-01", "R-02"), ro.map { it.codigo })
        assertEquals(20.0, ro[1].longitud, 1e-9)
        assertEquals(2, ro[1].registros)
        val cu = Calc.porCuadrilla(rs)
        assertEquals("Cuadrilla A", cu[0].nombre)
        val q = Calc.calidad(rs, p.copy(tempMin = 300.0, tempMax = 450.0))
        assertEquals(1, q.tempFuera)
        assertNull(q.velFuera)
        assertEquals(1, q.alertas.size)
        assertNotNull(q.tempProm)
    }

    @Test fun areaPorFecha() {
        val rs = listOf(
            reg(6176.57, 6186.57, fecha = "2026-11-10", turno = Turnos.DIA),
            reg(6186.57, 6196.57, fecha = "2026-11-10", turno = Turnos.NOCHE, hi = "19:00", hf = "23:00"),
            reg(6196.57, 6200.57, fecha = "2026-11-11", perim = null)
        )
        val f = Calc.porFecha(rs)
        assertEquals(2, f.size)
        assertEquals(2, f[0].turnos)
        assertEquals(250.0, f[0].rend.area, 1e-9)      // 20 m × 12,5 m
        assertEquals(0.0, f[1].rend.area, 1e-9)        // sin perímetro no hay área
        assertEquals(23.0 * 996.43, Calc.areaTotal(p), 1e-6)   // (2 + 2 + 19) m × 996,43 m
    }
}

class AvanceRealTest {
    private val p = Proyecto()

    private fun archivo(): String {
        val f = listOf("src/main/assets/avance_inicial.csv", "app/src/main/assets/avance_inicial.csv").map { File(it) }.first { it.exists() }
        return f.readText()
    }

    private val res by lazy { Importacion.leer(archivo(), p) }

    @Test fun importaLos100Registros() {
        assertTrue(res.errores.toString(), res.errores.isEmpty())
        assertTrue(res.seccionesNuevas.isEmpty())
        assertEquals(100, res.registros.size)   // la bóveda del 13/11/2026 se ingresa a mano
        val porSec = res.registros.groupBy { it.seccion }
        assertEquals(listOf(28, 16, 56), listOf("Bota HD", "Bota HI", "Bóveda").map { porSec[it]!!.size })
        // Cantidades oficiales: se conservan exactas
        assertEquals(1990.98, porSec["Bota HD"]!!.sumOf { it.area!! }, 1e-6)
        assertEquals(1990.52, porSec["Bota HI"]!!.sumOf { it.area!! }, 1e-6)
        assertEquals(12558.26, porSec["Bóveda"]!!.sumOf { it.area!! }, 1e-6)
        assertEquals(995.37, porSec["Bota HD"]!!.sumOf { it.longitud }, 1e-6)
        assertEquals(660.98, porSec["Bóveda"]!!.sumOf { it.longitud }, 1e-6)
    }

    @Test fun avancePorSeccion() {
        val s = Calc.porSeccion(res.registros, p).associateBy { it.seccion.nombre }
        assertEquals(995.0, s["Bota HD"]!!.cobertura.cubierto, 1e-6)       // K6+178 a K7+173 (lo de K7+173.35 sobresale)
        assertEquals(995.0, s["Bota HI"]!!.cobertura.cubierto, 1e-6)
        assertEquals(660.61, s["Bóveda"]!!.cobertura.cubierto, 1e-6)       // K6+512.39 a K7+173
        assertEquals(335.82, s["Bóveda"]!!.cobertura.restante, 1e-6)
        assertEquals(1, s["Bóveda"]!!.cobertura.pendientes.size)
        assertEquals(6176.57, s["Bóveda"]!!.cobertura.pendientes[0].ini, 1e-9)
        assertEquals(6512.39, s["Bóveda"]!!.cobertura.pendientes[0].fin, 1e-9)
        assertTrue(Calc.todosLosTraslapos(res.registros).isEmpty())
        // Botas marcadas como terminadas cuentan al 100 %
        val pt = p.copy(secciones = p.secciones.map { if (it.nombre.startsWith("Bota")) it.copy(terminada = true) else it })
        val st = Calc.porSeccion(res.registros, pt)
        assertEquals(1.0, st[0].porcentaje, 1e-9)
        assertTrue(st[0].terminada)
        val grupos = Calc.porGrupo(st)
        assertEquals(listOf("Bota", "Bóveda"), grupos.map { it.nombre })
        assertEquals(1.0, grupos[0].porcentaje, 1e-9)
        assertEquals(3981.50, grupos[0].rend.area, 1e-6)          // 1990,98 + 1990,52 m²
        assertEquals(660.61 / 996.43, grupos[1].porcentaje, 1e-9)
        assertEquals(335.82, grupos[1].pendiente, 1e-6)
        assertEquals(s["Bóveda"]!!.eta.fechaHora, grupos[1].etaFecha)
        // Métricas de una jornada: 2026-07-23 solo bota (HD y HI)
        val m = Calc.metricas(res.registros.filter { it.fecha == "2026-07-23" }, p)
        assertEquals(1, m.size)
        assertEquals("Bota", m[0].nombre)
        assertEquals(2 * 146.70, m[0].total.area, 1e-6)
        assertEquals(2, m[0].porSeccion.size)
        val g = Calc.avanceGlobal(st)
        assertEquals((2 * 2.0 * 996.43 + 19.0 * 660.61) / (23.0 * 996.43), g, 1e-9)
    }

    @Test fun proyeccionBoveda() {
        val bov = Calc.deSeccion(res.registros, "Bóveda")
        val e = Calc.eta(bov, p)
        // Últimos 6 turnos (día y noche): 10/10 D 7,29 · 10/10 N 7,29 · 10/11 D 10 · 10/12 N 7,29 · 10/13 N 7,29 · 10/14 D 3,16
        assertEquals(listOf("2026-10-10 Día", "2026-10-10 Noche", "2026-10-11 Día", "2026-10-12 Noche", "2026-10-13 Noche", "2026-10-14 Día"),
            Calc.porTurno(bov).takeLast(6).map { "${it.fecha} ${it.turno}" })
        assertEquals(42.32 / 6, e.mPorTurno!!, 1e-9)
        assertEquals(335.82 / (42.32 / 6), e.turnosRestantes!!, 1e-9)
        // Horas de esos 6 turnos: 10 h, 10 h, 7 h, 8 h, 10 h, 10 h
        assertEquals((600 + 600 + 420 + 480 + 600 + 600) / 6.0 / 60.0, e.horasPorTurno!!, 1e-9)
        assertNotNull(e.fechaHora)
        // Rendimiento por hora con los horarios reales
        val r = Calc.rendimiento(bov)
        assertTrue(r.mPorHora!! > 0)
        assertEquals(776, r.rollos)
        val c = Calc.curvaS(res.registros, p.secciones[2], p)
        assertEquals(LocalDate.of(2026, 8, 13), c.real.first().fecha)
        assertEquals(0.0, c.real.first().metros, 1e-9)
        assertEquals(LocalDate.of(2026, 10, 14), c.real.last().fecha)
        assertEquals(660.61, c.real.last().metros, 1e-6)
        // La curva real nunca baja
        assertTrue(c.real.zipWithNext().all { (a, b) -> b.metros >= a.metros - 1e-9 })
        assertEquals(e.fechaHora!!.toLocalDate().let { if (it.isAfter(c.proyReciente.last().fecha)) it else c.proyReciente.last().fecha }, c.proyReciente.last().fecha)
        assertEquals(996.43, c.proyReciente.last().metros, 1e-9)
        assertTrue(c.proyPromedio.isNotEmpty())
        // Promedio de todos los turnos de la bóveda: 660,98 m en 55 turnos (42 de día + 13 de noche), × 2 turnos por día
        assertEquals(660.98 / 55 * 2, c.mPorDiaPromedio!!, 1e-9)
        // Botas: sin proyección si se marcan terminadas
        val bota = Calc.curvaS(res.registros, Seccion("Bota HD", 2.0, terminada = true), p)
        assertTrue(bota.proyReciente.isEmpty())
    }

    @Test fun jornadaHorasYRollos() {
        val bov = Calc.deSeccion(res.registros, "Bóveda")
        assertEquals(13, bov.count { it.turno == Turnos.NOCHE })
        val n = bov.first { it.fecha == "2026-09-25" && it.turno == Turnos.NOCHE }
        assertEquals("18:30", n.horaIni); assertEquals("04:15", n.horaFin)
        assertEquals(585, n.minutos)                       // cruza la medianoche
        assertEquals(34, n.numRollos)
        assertEquals(131, Calc.deSeccion(res.registros, "Bota HD").sumOf { it.numRollos ?: 0 })
        assertEquals(124, Calc.deSeccion(res.registros, "Bota HI").sumOf { it.numRollos ?: 0 })
        val m = Calc.metricas(res.registros.filter { it.fecha == "2026-09-25" }, p)
        assertEquals(54, m[0].total.rollos)
        assertEquals(19.5, m[0].total.longitud, 1e-9)
    }

    @Test fun fusionActualizaSinDuplicar() {
        // Datos como los tenía el teléfono antes (sin jornada, horas ni rollos) + uno ingresado a mano
        val viejos = res.registros.map { it.copy(id = "v-" + it.creado, turno = Turnos.DIA, horaIni = "", horaFin = "", numRollos = null, notas = "nota") }
        val manual = res.registros.first().copy(id = "manual", fecha = "2026-11-13", absIni = 6512.39, absFin = 6507.0, seccion = "Bóveda")
        val f = Importacion.fusionar(viejos + manual, res.registros)
        assertTrue(f.nuevos.isEmpty())
        assertEquals(100, f.actualizados.size)
        assertTrue(f.actualizados.all { it.id.startsWith("v-") && it.notas == "nota" && it.numRollos != null && it.horaIni.isNotEmpty() })
        // Aplicarlo dos veces no cambia nada más
        assertEquals(100, Importacion.fusionar(f.actualizados, res.registros).iguales)
    }

    @Test fun fechasConBarras() {
        assertEquals("2026-07-23", Importacion.fecha("7/23/2026", true))
        assertEquals("2026-07-23", Importacion.fecha("23/07/2026", false))
        val txt = "Sección\tFecha\tPK inicial\tPK final\tLongitud (m)\tCantidad (m²)\nBota HD\t7/23/2026\tK7+173.35\tK7+153.00\t20.35\t40.70\n" +
            "Bóveda\t11/13/2026\tK6+512.39\tK6+507.00\t5.39\t102.41\nArco\t11/14/2026\tK6+507\tK6+500\t7\t70\n"
        val r = Importacion.leer(txt, p)
        assertEquals(listOf("2026-07-23", "2026-11-13", "2026-11-14"), r.registros.map { it.fecha })
        assertEquals(1, r.seccionesNuevas.size)
        assertEquals(10.0, r.seccionesNuevas[0].perimetro, 1e-9)
    }
}
