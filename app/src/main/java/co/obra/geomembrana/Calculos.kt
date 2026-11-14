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

/** Estado de una sección: cobertura, área, rendimiento y proyección. */
data class AvanceSeccion(
    val seccion: Seccion,
    val registros: List<Registro>,
    val cobertura: Cobertura,
    val rend: Rendimiento,
    val eta: Eta
) {
    val terminada: Boolean get() = seccion.terminada || eta.completado
    /** Fracción avanzada (1.0 si la sección se marcó como terminada). */
    val porcentaje: Double get() = if (seccion.terminada) 1.0 else cobertura.porcentaje
    val areaTotal: Double get() = seccion.perimetro * cobertura.total
}

/** Grupo de secciones (p. ej. Bota = HD + HI) con sus totales. */
data class AvanceGrupo(val nombre: String, val secciones: List<AvanceSeccion>) {
    val registros: List<Registro> get() = secciones.flatMap { it.registros }
    val rend: Rendimiento get() = Calc.rendimiento(registros)
    val areaTotal: Double get() = secciones.sumOf { it.areaTotal }
    /** Área equivalente cubierta (perímetro × metros cubiertos, o el total si la sección está terminada). */
    val areaCubierta: Double get() = secciones.sumOf { it.porcentaje * it.areaTotal }
    val porcentaje: Double get() = if (areaTotal > 0) areaCubierta / areaTotal else 0.0
    val terminada: Boolean get() = secciones.all { it.terminada }
    /** Metros lineales pendientes, sumando todas las secciones del grupo. */
    val pendiente: Double get() = secciones.filter { !it.seccion.terminada }.sumOf { it.cobertura.restante }
    /** El grupo termina cuando termina su última sección. */
    val etaFecha: java.time.LocalDateTime? get() =
        if (secciones.any { !it.terminada && it.eta.fechaHora == null }) null
        else secciones.mapNotNull { it.eta.fechaHora }.maxOrNull()
}

/** Métricas de un conjunto de registros (una jornada o todo el proyecto) para un grupo y sus secciones. */
data class MetricasGrupo(val nombre: String, val total: Rendimiento, val porSeccion: List<Pair<Seccion, Rendimiento>>)

data class PuntoCurva(val fecha: java.time.LocalDate, val metros: Double)

/** Curva S de una sección: avance real acumulado y avance proyectado según los rendimientos. */
data class CurvaS(
    val seccion: Seccion,
    val total: Double,
    val real: List<PuntoCurva>,
    val proyReciente: List<PuntoCurva>,   // con el promedio de los últimos turnos
    val proyPromedio: List<PuntoCurva>,   // con el promedio de todos los turnos
    val mPorDiaReciente: Double?,
    val mPorDiaPromedio: Double?
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
    fun errores(absIni: Double?, absFin: Double?): List<String> {
        val e = mutableListOf<String>()
        if (absIni == null) e += "Abscisa inicial no válida (ej. K7+173.35)."
        if (absFin == null) e += "Abscisa final no válida (ej. K7+153.00)."
        if (absIni != null && absFin != null && abs(absFin - absIni) < 0.005) e += "La abscisa final debe ser distinta de la inicial."
        return e
    }

    /** Aviso (no impide guardar) si el tramo se sale del túnel configurado. */
    fun fueraDeRango(absIni: Double?, absFin: Double?, p: Proyecto): String? {
        val fuera = listOfNotNull(absIni, absFin).filter { it < p.absIni - 0.005 || it > p.absFin + 0.005 }
        if (fuera.isEmpty()) return null
        return "Fuera del túnel configurado (${Abscisa.texto(p.absIni)} a ${Abscisa.texto(p.absFin)}): " +
            fuera.joinToString(", ") { Abscisa.texto(it) } + ". Lo que sobresale no cuenta en el avance."
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
        if (r.longitudManual != null && abs(r.longitudManual - r.longitudAbscisas) > 0.015)
            a += "Longitud ajustada (${Fmt.num(r.longitudManual)} m vs ${Fmt.num(r.longitudAbscisas)} m por abscisas)"
        return a
    }

    // ---------- Cobertura ----------

    private fun unir(tramos: List<Tramo>): List<Tramo> {
        val orden = tramos.filter { it.longitud > EPS }.sortedBy { it.ini }
        val res = mutableListOf<Tramo>()
        for (t in orden) {
            val u = res.lastOrNull()
            if (u != null && t.ini <= u.fin + 0.005) res[res.size - 1] = Tramo(u.ini, max(u.fin, t.fin))
            else res += t
        }
        return res
    }

    /** Cobertura de UNA sección (pasar solo los registros de esa sección). */
    fun cobertura(registros: List<Registro>, p: Proyecto): Cobertura {
        val recortados = registros.map { Tramo(max(it.desde, p.absIni), min(it.hasta, p.absFin)) }
        val tramos = unir(recortados)
        val pendientes = mutableListOf<Tramo>()
        var cursor = p.absIni
        for (t in tramos) {
            if (t.ini > cursor + 0.005) pendientes += Tramo(cursor, t.ini)
            cursor = max(cursor, t.fin)
        }
        if (p.absFin > cursor + 0.005) pendientes += Tramo(cursor, p.absFin)
        return Cobertura(tramos.sumOf { it.longitud }, tramos, pendientes, p.longitudTotal)
    }

    fun deSeccion(registros: List<Registro>, seccion: String): List<Registro> =
        registros.filter { Texto.clave(it.seccion) == Texto.clave(seccion) }

    /** Registros de la misma sección (distintos de [excepto]) que se traslapan con el intervalo dado. */
    fun traslapos(absIni: Double, absFin: Double, registros: List<Registro>, excepto: String? = null): List<Pair<Registro, Tramo>> {
        val d = min(absIni, absFin); val h = max(absIni, absFin)
        return registros.filter { it.id != excepto }.mapNotNull { r ->
            val a = max(d, r.desde); val b = min(h, r.hasta)
            if (b - a > 0.015) r to Tramo(a, b) else null
        }
    }

    fun todosLosTraslapos(registros: List<Registro>): List<Traslapo> =
        registros.groupBy { Texto.clave(it.seccion) }.values.flatMap { lista ->
            val orden = lista.sortedBy { it.desde }
            val res = mutableListOf<Traslapo>()
            for (i in orden.indices) for (k in i + 1 until orden.size) {
                val a = orden[i]; val b = orden[k]
                if (b.desde >= a.hasta) break
                val ini = max(a.desde, b.desde); val fin = min(a.hasta, b.hasta)
                if (fin - ini > 0.015) res += Traslapo(a, b, Tramo(ini, fin))
            }
            res
        }

    /** Abscisa donde debería empezar el siguiente registro de la sección: donde terminó el último. */
    fun siguiente(registros: List<Registro>): Double? = ordenar(registros).lastOrNull()?.absFin

    // ---------- Rendimiento ----------

    fun rendimiento(registros: List<Registro>): Rendimiento {
        val conHoras = registros.filter { (it.minutos ?: 0) > 0 }
        val min = conHoras.sumOf { it.minutos ?: 0 }
        val horas = min / 60.0
        val conArea = conHoras.filter { it.area != null }
        val minArea = conArea.sumOf { it.minutos ?: 0 }
        return Rendimiento(
            registros = registros.size,
            longitud = registros.sumOf { it.longitud },
            area = registros.sumOf { it.area ?: 0.0 },
            minutos = min,
            mPorHora = if (horas > 0) conHoras.sumOf { it.longitud } / horas else null,
            m2PorHora = if (minArea > 0) conArea.sumOf { it.area ?: 0.0 } / (minArea / 60.0) else null
        )
    }

    fun ordenar(registros: List<Registro>): List<Registro> =
        registros.sortedWith(compareBy({ it.fecha }, { Turnos.orden(it.turno) }, { Horas.aMin(it.horaIni) ?: 0 }, { it.creado }))

    fun porTurno(registros: List<Registro>): List<PorTurno> =
        ordenar(registros).groupBy { it.fecha to it.turno }
            .map { (k, v) -> PorTurno(k.first, k.second, v) }
            .sortedWith(compareBy({ it.fecha }, { Turnos.orden(it.turno) }))

    fun porFecha(registros: List<Registro>): List<PorFecha> =
        ordenar(registros).groupBy { it.fecha }.map { (k, v) -> PorFecha(k, v) }.sortedBy { it.fecha }

    // ---------- Secciones ----------

    /** Avance de cada sección configurada (y de las que aparezcan en los registros sin estar configuradas). */
    fun porSeccion(registros: List<Registro>, p: Proyecto): List<AvanceSeccion> {
        val extra = registros.map { it.seccion }.distinctBy { Texto.clave(it) }
            .filter { p.seccion(it) == null }
            .map { n -> Seccion(n, deSeccion(registros, n).firstNotNullOfOrNull { it.perimetro } ?: 0.0) }
        return (p.secciones + extra).map { s ->
            val regs = deSeccion(registros, s.nombre)
            val cob = cobertura(regs, p)
            val eta = if (s.terminada) Eta(true, 0.0, 0, null, null, null, 0.0, 0.0, null) else eta(regs, p, cob)
            AvanceSeccion(s, regs, cob, rendimiento(regs), eta)
        }
    }

    fun porGrupo(secciones: List<AvanceSeccion>): List<AvanceGrupo> =
        secciones.groupBy { Texto.clave(it.seccion.grupoEfectivo) }.values.map { AvanceGrupo(it.first().seccion.grupoEfectivo, it) }

    /** Métricas por grupo y sección de un conjunto de registros (solo grupos con registros). */
    fun metricas(registros: List<Registro>, p: Proyecto): List<MetricasGrupo> {
        val secs = porSeccion(registros, p).map { it.seccion }
        return secs.groupBy { Texto.clave(it.grupoEfectivo) }.values.mapNotNull { grupo ->
            val partes = grupo.map { s -> s to deSeccion(registros, s.nombre) }.filter { it.second.isNotEmpty() }
            if (partes.isEmpty()) null
            else MetricasGrupo(grupo.first().grupoEfectivo, rendimiento(partes.flatMap { it.second }), partes.map { it.first to rendimiento(it.second) })
        }
    }

    /** Área total del túnel a cubrir: suma de perímetro × longitud de todas las secciones. */
    fun areaTotal(p: Proyecto): Double = p.secciones.sumOf { it.perimetro } * p.longitudTotal

    /** Avance global ponderado por área (cada sección pesa según su perímetro). */
    fun avanceGlobal(secciones: List<AvanceSeccion>): Double {
        val total = secciones.sumOf { it.areaTotal }
        return if (total > 0) secciones.sumOf { it.porcentaje * it.areaTotal } / total else 0.0
    }

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
     * Fecha y hora estimadas de terminación de UNA sección (pasar solo sus registros).
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

    // ---------- Curva S ----------

    private fun proyectar(desde: PuntoCurva, mPorDia: Double?, total: Double): List<PuntoCurva> {
        if (mPorDia == null || mPorDia <= EPS || desde.metros >= total - 0.005) return emptyList()
        val res = mutableListOf(desde)
        var k = 1L
        while (k <= 3650) {
            val v = desde.metros + k * mPorDia
            if (v >= total - 0.005) {
                // Último punto: día (fraccionario) en que se alcanza el total, redondeado al día siguiente
                res += PuntoCurva(desde.fecha.plusDays(k), total)
                break
            }
            res += PuntoCurva(desde.fecha.plusDays(k), v)
            k++
        }
        return res
    }

    /**
     * Curva S de una sección hasta la fecha [corte] (inclusive; null = todo).
     * Real: metros cubiertos acumulados (sin traslapos) al final de cada día con registros.
     * Proyectada: desde el último punto real, metros por día = metros por turno × turnos por día,
     * con el promedio de los últimos turnos (reciente) y con el de todos los turnos (promedio).
     */
    fun curvaS(registros: List<Registro>, s: Seccion, p: Proyecto, corte: String? = null): CurvaS {
        val regs = ordenar(deSeccion(registros, s.nombre)).filter { corte == null || it.fecha <= corte }
        val total = p.longitudTotal
        val fechas = regs.map { it.fecha }.distinct().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        val real = mutableListOf<PuntoCurva>()
        if (fechas.isNotEmpty()) {
            real += PuntoCurva(fechas.first().minusDays(1), 0.0)
            fechas.forEach { f ->
                val hasta = f.toString()
                real += PuntoCurva(f, cobertura(regs.filter { it.fecha <= hasta }, p).cubierto)
            }
        }
        val porDia = if (p.turnosPorDia >= 2) 2 else 1
        val turnos = porTurno(regs)
        val reciente = turnos.takeLast(max(1, p.turnosParaEta)).takeIf { it.isNotEmpty() }?.let { u -> u.sumOf { it.longitud } / u.size * porDia }
        val promedio = turnos.takeIf { it.isNotEmpty() }?.let { u -> u.sumOf { it.longitud } / u.size * porDia }
        val ultimo = real.lastOrNull()
        val terminada = s.terminada || (ultimo != null && ultimo.metros >= total - 0.005)
        return CurvaS(
            s, total, real,
            if (ultimo == null || terminada) emptyList() else proyectar(ultimo, reciente, total),
            if (ultimo == null || terminada) emptyList() else proyectar(ultimo, promedio, total),
            reciente, promedio
        )
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
        }.sortedByDescending { it.area }

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
