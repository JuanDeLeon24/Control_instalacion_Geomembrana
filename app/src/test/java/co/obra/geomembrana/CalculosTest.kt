package co.obra.geomembrana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CalculosTest {
    private val p = Proyecto()

    private fun reg(
        ini: Double, fin: Double, fecha: String = "2026-11-10", turno: String = Turnos.DIA,
        hi: String = "07:00", hf: String = "17:00", perim: Double? = 12.5, manual: Double? = null,
        rollos: List<String> = emptyList(), cuadrilla: String = "", temp: Double? = null, vel: Double? = null,
        prueba: Prueba = Prueba.APROBADO, geo: Geotextil = Geotextil.OK, id: String = "$fecha-$turno-$ini"
    ) = Registro(id, fecha, turno, "Residente", cuadrilla, ini, fin, hi, hf, manual, perim, rollos, temp, vel,
        prueba, geo, "", "")

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
        assertTrue(Calc.errores(6176.57, 6200.0, p).isEmpty())
        assertEquals(1, Calc.errores(6100.0, 6200.0, p).size)
        assertEquals(1, Calc.errores(6200.0, 7200.0, p).size)
        assertEquals(1, Calc.errores(6300.0, 6200.0, p).size)
        assertEquals(2, Calc.errores(null, null, p).size)
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
        val e = Calc.eta(rs, p)
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
        assertEquals(12455.375, Calc.areaTotal(p)!!, 1e-6)
    }
}
