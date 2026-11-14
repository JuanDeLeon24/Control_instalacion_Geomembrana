package co.obra.geomembrana

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// ======================= Abscisas =======================

object Abscisa {
    private val CON_K = Regex("^K?(\\d+)\\+(\\d+(?:\\.\\d+)?)$")
    private val METROS = Regex("^\\d+(?:\\.\\d+)?$")

    /** "K6+176.57", "k6+176,57", "6+176.57" o "6176.57" -> 6176.57 m. Null si no se entiende. */
    fun leer(s: String): Double? {
        val t = s.trim().uppercase().replace(" ", "").replace(',', '.')
        if (t.isEmpty()) return null
        CON_K.matchEntire(t)?.let { m ->
            val km = m.groupValues[1].toDouble()
            val mt = m.groupValues[2].toDouble()
            if (mt >= 1000.0) return null
            return km * 1000.0 + mt
        }
        if (METROS.matches(t)) return t.toDouble()
        return null
    }

    /** 6176.57 -> "K6+176.57". */
    fun texto(m: Double): String {
        val c = (m * 100).roundToLong()           // centímetros, evita 6+999.999 -> 6+1000.00
        val km = c / 100_000
        val resto = c % 100_000
        return "K$km+%03d.%02d".format(Locale.US, resto / 100, resto % 100)
    }
}

// ======================= Formato de números =======================

object Fmt {
    val ES: Locale = Locale("es", "CO")

    fun num(d: Double?, dec: Int = 2): String {
        if (d == null || d.isNaN() || d.isInfinite()) return "–"
        val nf = NumberFormat.getNumberInstance(ES)
        nf.maximumFractionDigits = dec
        nf.minimumFractionDigits = 0
        return nf.format(d)
    }

    fun fijo(d: Double?, dec: Int = 2): String {
        if (d == null || d.isNaN() || d.isInfinite()) return "–"
        val nf = NumberFormat.getNumberInstance(ES)
        nf.maximumFractionDigits = dec
        nf.minimumFractionDigits = dec
        return nf.format(d)
    }

    fun pct(d: Double?): String = if (d == null) "–" else fijo(d * 100, 1) + " %"

    fun duracion(min: Double?): String {
        if (min == null) return "–"
        val total = Math.round(min).toInt()
        val h = total / 60; val m = total % 60
        return if (h > 0) "$h h ${m.toString().padStart(2, '0')} min" else "$m min"
    }

    /** Lee un número escrito con coma o punto decimal. */
    fun leer(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
}

// ======================= Cobertura del túnel =======================

data class Tramo(val ini: Double, val fin: Double) {
    val longitud: Double get() = fin - ini
}

data class Traslapo(val a: Registro, val b: Registro, val tramo: Tramo)

data class Cobertura(
    val cubierto: Double,            // metros del túnel con geomembrana (sin contar traslapos dos veces)
    val tramos: List<Tramo>,         // tramos instalados, unidos
    val pendientes: List<Tramo>,     // tramos sin geomembrana
    val total: Double
) {
    val restante: Double get() = max(0.0, total - cubierto)
    val porcentaje: Double get() = if (total > 0) min(1.0, cubierto / total) else 0.0
}

// ======================= Rendimiento =======================

data class Rendimiento(
    val registros: Int,
    val longitud: Double,            // m (suma de longitudes de los registros)
    val area: Double,                // m² (solo registros con perímetro)
    val minutos: Int,                // minutos trabajados (solo registros con horas)
    val mPorHora: Double?,
    val m2PorHora: Double?
)

data class PorTurno(val fecha: String, val turno: String, val registros: List<Registro>) {
    val longitud: Double get() = registros.sumOf { it.longitud }
    val area: Double get() = registros.sumOf { it.area ?: 0.0 }
    val minutos: Int get() = registros.sumOf { it.minutos ?: 0 }
}

data class PorFecha(val fecha: String, val registros: List<Registro>) {
    val turnos: Int get() = registros.map { it.turno }.distinct().size
    val rend: Rendimiento get() = Calc.rendimiento(registros)
}

data class Eta(
    val completado: Boolean,
    val restante: Double,
    val turnosUsados: Int,           // turnos promediados
    val mPorTurno: Double?,
    val mPorHora: Double?,
    val horasPorTurno: Double?,
    val turnosRestantes: Double?,
    val diasRestantes: Double?,
    val fechaHora: LocalDateTime?
)

data class PorRollo(
    val codigo: String,
    val registros: Int,
    val longitud: Double,            // longitud del registro repartida entre sus rollos
    val area: Double,
    val primera: String,
    val ultima: String
)

data class PorCuadrilla(
    val nombre: String,
    val turnos: Int,
    val registros: Int,
    val longitud: Double,
    val area: Double,
    val minutos: Int,
    val mPorHora: Double?,
    val m2PorHora: Double?
)

data class Calidad(
    val conTemp: Int, val tempProm: Double?, val tempMin: Double?, val tempMax: Double?, val tempFuera: Int?,
    val conVel: Int, val velProm: Double?, val velMin: Double?, val velMax: Double?, val velFuera: Int?,
    val aprobadas: Int, val noAprobadas: Int, val pruebasNA: Int,
    val geoOk: Int, val geoObs: Int, val geoNo: Int,
    val alertas: List<Pair<Registro, List<String>>>
)

object Calc {
    private const val EPS = 1e-6

    // ---------- Validación ----------

    /** Errores que impiden guardar el registro. */
    fun errores(absIni: Double?, absFin: Double?, p: Proyecto): List<String> {
        val e = mutableListOf<String>()
        val rango = "${Abscisa.texto(p.absIni)} a ${Abscisa.texto(p.absFin)}"
        if (absIni == null) e += "Abscisa inicial no válida (ej. K6+176.57)."
        else if (absIni < p.absIni - EPS || absIni > p.absFin + EPS) e += "La abscisa inicial está fuera del túnel ($rango)."
        if (absFin == null) e += "Abscisa final no válida (ej. K6+190.00)."
        else if (absFin < p.absIni - EPS || absFin > p.absFin + EPS) e += "La abscisa final está fuera del túnel ($rango)."
        if (absIni != null && absFin != null && absFin <= absIni + EPS) e += "La abscisa final debe ser mayor que la inicial."
        return e
    }

    /** Fuera de especificación: lista de textos para mostrar como alerta. */
    fun alertas(r: Registro, p: Proyecto): List<String> {
        val a = mutableListOf<String>()
        r.tempCuna?.let { t ->
            if ((p.tempMin != null && t < p.tempMin) || (p.tempMax != null && t > p.tempMax))
                a += "Temperatura ${Fmt.num(t, 1)} °C fuera de especificación"
        }
        r.velSoldadura?.let { v ->
            if ((p.velMin != null && v < p.velMin) || (p.velMax != null && v > p.velMax))
                a += "Velocidad ${Fmt.num(v, 2)} m/min fuera de especificación"
        }
        if (r.prueba == Prueba.NO_APROBADO) a += "Prueba de presión no aprobada"
        if (r.geotextil == Geotextil.OBSERVACIONES) a += "Geotextil con observaciones"
        if (r.geotextil == Geotextil.NO_INSPECCIONADO) a += "Geotextil no inspeccionado"
        if (r.longitudManual != null && abs(r.longitudManual - r.longitudAbscisas) > 0.005)
            a += "Longitud ajustada (${Fmt.num(r.longitudManual)} m vs ${Fmt.num(r.longitudAbscisas)} m por abscisas)"
        return a
    }

    // ---------- Cobertura ----------

    private fun unir(tramos: List<Tramo>): List<Tramo> {
        val orden = tramos.filter { it.longitud > EPS }.sortedBy { it.ini }
        val res = mutableListOf<Tramo>()
        for (t in orden) {
            val u = res.lastOrNull()
            if (u != null && t.ini <= u.fin + EPS) res[res.size - 1] = Tramo(u.ini, max(u.fin, t.fin))
            else res += t
        }
        return res
    }

    fun cobertura(registros: List<Registro>, p: Proyecto): Cobertura {
        val recortados = registros.map { Tramo(max(it.absIni, p.absIni), min(it.absFin, p.absFin)) }
        val tramos = unir(recortados)
        val pendientes = mutableListOf<Tramo>()
        var cursor = p.absIni
        for (t in tramos) {
            if (t.ini > cursor + EPS) pendientes += Tramo(cursor, t.ini)
            cursor = max(cursor, t.fin)
        }
        if (p.absFin > cursor + EPS) pendientes += Tramo(cursor, p.absFin)
        return Cobertura(tramos.sumOf { it.longitud }, tramos, pendientes, p.longitudTotal)
    }

    /** Registros (distintos de [excepto]) que se traslapan con el intervalo dado. */
    fun traslapos(absIni: Double, absFin: Double, registros: List<Registro>, excepto: String? = null): List<Pair<Registro, Tramo>> =
        registros.filter { it.id != excepto }.mapNotNull { r ->
            val a = max(absIni, r.absIni); val b = min(absFin, r.absFin)
            if (b - a > 0.005) r to Tramo(a, b) else null
        }

    fun todosLosTraslapos(registros: List<Registro>): List<Traslapo> {
        val orden = registros.sortedBy { it.absIni }
        val res = mutableListOf<Traslapo>()
        for (i in orden.indices) for (k in i + 1 until orden.size) {
            val a = orden[i]; val b = orden[k]
            if (b.absIni >= a.absFin) break
            val ini = max(a.absIni, b.absIni); val fin = min(a.absFin, b.absFin)
            if (fin - ini > 0.005) res += Traslapo(a, b, Tramo(ini, fin))
        }
        return res
    }

    // ---------- Rendimiento ----------

    fun rendimiento(registros: List<Registro>): Rendimiento {
        val conHoras = registros.filter { (it.minutos ?: 0) > 0 }
        val min = conHoras.sumOf { it.minutos ?: 0 }
        val horas = min / 60.0
        val longHoras = conHoras.sumOf { it.longitud }
        val conArea = conHoras.filter { it.area != null }
        val minArea = conArea.sumOf { it.minutos ?: 0 }
        return Rendimiento(
            registros = registros.size,
            longitud = registros.sumOf { it.longitud },
            area = registros.sumOf { it.area ?: 0.0 },
            minutos = min,
            mPorHora = if (horas > 0) longHoras / horas else null,
            m2PorHora = if (minArea > 0) conArea.sumOf { it.area ?: 0.0 } / (minArea / 60.0) else null
        )
    }

    fun ordenar(registros: List<Registro>): List<Registro> =
        registros.sortedWith(compareBy({ it.fecha }, { Turnos.orden(it.turno) }, { Horas.aMin(it.horaIni) ?: 0 }, { it.absIni }))

    fun porTurno(registros: List<Registro>): List<PorTurno> =
        ordenar(registros).groupBy { it.fecha to it.turno }
            .map { (k, v) -> PorTurno(k.first, k.second, v) }
            .sortedWith(compareBy({ it.fecha }, { Turnos.orden(it.turno) }))

    fun porFecha(registros: List<Registro>): List<PorFecha> =
        ordenar(registros).groupBy { it.fecha }.map { (k, v) -> PorFecha(k, v) }.sortedBy { it.fecha }

    /** Área total del túnel a cubrir con el perímetro de referencia (null si no hay perímetro). */
    fun areaTotal(p: Proyecto): Double? = p.perimetroDefecto?.let { it * p.longitudTotal }

    // ---------- Proyección de término ----------

    /** Índice consecutivo de turno: permite avanzar turnos sin recorrer el calendario. */
    private fun indice(fecha: LocalDate, turno: String, porDia: Int): Long =
        if (porDia >= 2) fecha.toEpochDay() * 2 + Turnos.orden(turno) else fecha.toEpochDay()

    private fun inicioTurno(indice: Long, porDia: Int, p: Proyecto): LocalDateTime {
        val (dia, turno) = if (porDia >= 2) Math.floorDiv(indice, 2L) to (if (Math.floorMod(indice, 2L) == 1L) Turnos.NOCHE else Turnos.DIA)
        else indice to Turnos.DIA
        val hora = Horas.aMin(if (turno == Turnos.NOCHE) p.horaInicioNoche else p.horaInicioDia) ?: 0
        return LocalDate.ofEpochDay(dia).atTime(LocalTime.of(hora / 60, hora % 60))
    }

    /**
     * Fecha y hora estimadas de terminación.
     * Velocidad = promedio de metros por turno en los últimos [Proyecto.turnosParaEta] turnos registrados.
     * Desde el último turno registrado se cuentan los turnos que faltan (1 o 2 por día según la configuración);
     * en el último turno parcial se suma la fracción de las horas trabajadas en promedio por turno.
     */
    fun eta(registros: List<Registro>, p: Proyecto, cob: Cobertura = cobertura(registros, p)): Eta {
        val restante = cob.restante
        val turnos = porTurno(registros)
        val ultimos = turnos.takeLast(max(1, p.turnosParaEta))
        if (restante <= 0.005) return Eta(true, 0.0, ultimos.size, null, null, null, 0.0, 0.0, null)
        if (ultimos.isEmpty()) return Eta(false, restante, 0, null, null, null, null, null, null)

        val mPorTurno = ultimos.sumOf { it.longitud } / ultimos.size
        val rend = rendimiento(ultimos.flatMap { it.registros })
        val conHoras = ultimos.filter { it.minutos > 0 }
        val horasPorTurno = if (conHoras.isNotEmpty()) conHoras.sumOf { it.minutos } / 60.0 / conHoras.size else null
        if (mPorTurno <= EPS) return Eta(false, restante, ultimos.size, mPorTurno, rend.mPorHora, horasPorTurno, null, null, null)

        val porDia = if (p.turnosPorDia >= 2) 2 else 1
        val faltan = restante / mPorTurno
        val ultimo = ultimos.last()
        val fechaUlt = runCatching { LocalDate.parse(ultimo.fecha) }.getOrNull()
            ?: return Eta(false, restante, ultimos.size, mPorTurno, rend.mPorHora, horasPorTurno, faltan, faltan / porDia, null)

        val base = indice(fechaUlt, ultimo.turno, porDia)
        val completos = floor(faltan + EPS).toLong()
        val fraccion = faltan - completos
        val hTurno = horasPorTurno ?: (24.0 / porDia)
        val fechaHora = if (fraccion < 1e-4) {
            inicioTurno(base + completos, porDia, p).plusMinutes((hTurno * 60).roundToLong())
        } else {
            inicioTurno(base + completos + 1, porDia, p).plusMinutes((fraccion * hTurno * 60).roundToLong())
        }
        return Eta(false, restante, ultimos.size, mPorTurno, rend.mPorHora, horasPorTurno, faltan, faltan / porDia, fechaHora)
    }

    // ---------- Desgloses ----------

    fun porRollo(registros: List<Registro>): List<PorRollo> {
        data class Acum(var n: Int = 0, var l: Double = 0.0, var a: Double = 0.0, var f1: String = "", var f2: String = "")
        val mapa = linkedMapOf<String, Acum>()
        ordenar(registros).forEach { r ->
            val cods = r.rollos.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
            if (cods.isEmpty()) return@forEach
            cods.forEach { c ->
                val x = mapa.getOrPut(c) { Acum(f1 = r.fecha) }
                x.n++; x.l += r.longitud / cods.size; x.a += (r.area ?: 0.0) / cods.size; x.f2 = r.fecha
            }
        }
        return mapa.map { (k, v) -> PorRollo(k, v.n, v.l, v.a, v.f1, v.f2) }.sortedBy { it.codigo }
    }

    fun nombreCuadrilla(r: Registro): String = r.cuadrilla.trim().ifBlank { r.responsable.trim() }.ifBlank { "Sin asignar" }

    fun porCuadrilla(registros: List<Registro>): List<PorCuadrilla> =
        registros.groupBy { nombreCuadrilla(it) }.map { (k, v) ->
            val r = rendimiento(v)
            PorCuadrilla(k, v.map { it.fecha to it.turno }.distinct().size, v.size, r.longitud, r.area, r.minutos, r.mPorHora, r.m2PorHora)
        }.sortedByDescending { it.longitud }

    fun calidad(registros: List<Registro>, p: Proyecto): Calidad {
        val t = registros.mapNotNull { it.tempCuna }
        val v = registros.mapNotNull { it.velSoldadura }
        val tFuera = if (p.tempMin == null && p.tempMax == null) null else
            t.count { (p.tempMin != null && it < p.tempMin) || (p.tempMax != null && it > p.tempMax) }
        val vFuera = if (p.velMin == null && p.velMax == null) null else
            v.count { (p.velMin != null && it < p.velMin) || (p.velMax != null && it > p.velMax) }
        return Calidad(
            t.size, t.average().takeIf { t.isNotEmpty() }, t.minOrNull(), t.maxOrNull(), tFuera,
            v.size, v.average().takeIf { v.isNotEmpty() }, v.minOrNull(), v.maxOrNull(), vFuera,
            registros.count { it.prueba == Prueba.APROBADO },
            registros.count { it.prueba == Prueba.NO_APROBADO },
            registros.count { it.prueba == Prueba.NA },
            registros.count { it.geotextil == Geotextil.OK },
            registros.count { it.geotextil == Geotextil.OBSERVACIONES },
            registros.count { it.geotextil == Geotextil.NO_INSPECCIONADO },
            ordenar(registros).map { it to alertas(it, p) }.filter { it.second.isNotEmpty() }
        )
    }
}
