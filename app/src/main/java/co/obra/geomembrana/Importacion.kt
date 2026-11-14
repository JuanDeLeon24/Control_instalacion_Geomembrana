package co.obra.geomembrana

import java.time.LocalDate
import java.util.UUID
import kotlin.math.abs

/**
 * Lee un registro de avance en texto (CSV con ; o , o texto pegado de Excel con tabuladores).
 * Columnas reconocidas por su título (el orden no importa):
 *   Sección | Fecha | PK inicial | PK final | Longitud (m) | Cantidad (m²)   [obligatorias: Sección, Fecha, PK inicial, PK final]
 *   Jornada (o Turno) | Hora inicio | Hora fin | Rollos (und) | Responsable | Cuadrilla | Notas   [opcionales]
 * No depende de Android (se prueba en la JVM).
 */
object Importacion {

    data class Resultado(
        val registros: List<Registro>,
        val seccionesNuevas: List<Seccion>,
        val errores: List<String>
    )

    private fun separador(linea: String): Char = when {
        '\t' in linea -> '\t'
        ';' in linea -> ';'
        else -> ','
    }

    private fun celdas(linea: String, sep: Char): List<String> {
        val res = mutableListOf<String>()
        val sb = StringBuilder()
        var comillas = false
        var i = 0
        while (i < linea.length) {
            val ch = linea[i]
            when {
                ch == '"' && comillas && i + 1 < linea.length && linea[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> comillas = !comillas
                ch == sep && !comillas -> { res += sb.toString().trim(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        res += sb.toString().trim()
        return res
    }

    private fun columna(titulos: List<String>, vararg claves: String): Int =
        titulos.indexOfFirst { t -> claves.any { c -> t.startsWith(c) } }

    private val FECHA_BARRAS = Regex("^(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})$")

    /**
     * Decide si las fechas con barras vienen como día/mes o mes/día mirando todo el archivo:
     * si algún primer número pasa de 12 es día/mes; si algún segundo pasa de 12 es mes/día; si no, día/mes.
     */
    private fun mesPrimero(fechas: List<String>): Boolean {
        var dm = false; var md = false
        fechas.forEach { f ->
            FECHA_BARRAS.matchEntire(f.trim())?.let { m ->
                if (m.groupValues[1].toInt() > 12) dm = true
                if (m.groupValues[2].toInt() > 12) md = true
            }
        }
        return md && !dm
    }

    fun fecha(s: String, mesPrimero: Boolean): String? {
        val t = s.trim()
        runCatching { return LocalDate.parse(t).toString() }
        val m = FECHA_BARRAS.matchEntire(t) ?: return null
        val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val y = m.groupValues[3].toInt()
        return runCatching { (if (mesPrimero) LocalDate.of(y, a, b) else LocalDate.of(y, b, a)).toString() }.getOrNull()
    }

    /** "7:30", "07:30", "07:30:00" -> "07:30"; vacío si no se entiende. */
    fun hora(s: String): String {
        val m = Regex("^(\\d{1,2})[:.](\\d{2})(?::\\d{2})?$").matchEntire(s.trim()) ?: return ""
        val min = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
        return if (m.groupValues[1].toInt() in 0..23 && m.groupValues[2].toInt() in 0..59) Horas.formato(min) else ""
    }

    fun leer(texto: String, p: Proyecto): Resultado {
        val lineas = texto.replace("﻿", "").lines().filter { it.isNotBlank() }
        if (lineas.isEmpty()) return Resultado(emptyList(), emptyList(), listOf("El archivo está vacío."))
        val sep = separador(lineas.first())
        val titulos = celdas(lineas.first(), sep).map { Texto.clave(it) }
        val cSec = columna(titulos, "seccion", "elemento")
        val cFecha = columna(titulos, "fecha")
        val cIni = columna(titulos, "pk inicial", "pk inicio", "abscisa inicial", "abs. inicial", "abs inicial", "k inicial")
        val cFin = columna(titulos, "pk final", "abscisa final", "abs. final", "abs final", "k final")
        val cLong = columna(titulos, "longitud")
        val cArea = columna(titulos, "cantidad", "area")
        val cTurno = columna(titulos, "turno", "jornada")
        val cHIni = columna(titulos, "hora inicio", "hora de inicio", "inicio")
        val cHFin = columna(titulos, "hora fin", "hora de fin", "hora final", "fin")
        val cResp = columna(titulos, "responsable")
        val cCuad = columna(titulos, "cuadrilla")
        val cRollos = columna(titulos, "rollo")
        val cNotas = columna(titulos, "nota", "observ")
        val faltan = listOf("Sección" to cSec, "Fecha" to cFecha, "PK inicial" to cIni, "PK final" to cFin).filter { it.second < 0 }
        if (faltan.isNotEmpty()) return Resultado(emptyList(), emptyList(),
            listOf("Faltan columnas: ${faltan.joinToString { it.first }}. La primera fila debe tener los títulos."))

        val filas = lineas.drop(1).map { celdas(it, sep) }
        fun col(f: List<String>, i: Int) = if (i >= 0 && i < f.size) f[i] else ""
        val mp = mesPrimero(filas.map { col(it, cFecha) })

        val errores = mutableListOf<String>()
        val nuevas = linkedMapOf<String, Seccion>()
        val registros = mutableListOf<Registro>()
        filas.forEachIndexed { k, f ->
            val n = k + 2
            val sec = col(f, cSec).trim()
            val fe = fecha(col(f, cFecha), mp)
            val a = Abscisa.leer(col(f, cIni)); val b = Abscisa.leer(col(f, cFin))
            if (sec.isEmpty() && fe == null && a == null && b == null) return@forEachIndexed   // fila vacía
            when {
                sec.isEmpty() -> { errores += "Fila $n: falta la sección."; return@forEachIndexed }
                fe == null -> { errores += "Fila $n: fecha no válida (${col(f, cFecha)})."; return@forEachIndexed }
                a == null || b == null -> { errores += "Fila $n: abscisa no válida."; return@forEachIndexed }
                abs(a - b) < 0.005 -> { errores += "Fila $n: las dos abscisas son iguales."; return@forEachIndexed }
            }
            val ini = a!!; val fin = b!!
            val longitudDada = Fmt.leer(col(f, cLong))
            val areaDada = Fmt.leer(col(f, cArea))
            val porAbs = abs(fin - ini)
            val longitud = longitudDada ?: porAbs
            val existente = p.seccion(sec) ?: nuevas[Texto.clave(sec)]
            val seccion = existente ?: Seccion(sec, if (areaDada != null && longitud > 0) Math.round(areaDada / longitud * 100) / 100.0 else 0.0)
                .also { nuevas[Texto.clave(sec)] = it }
            val perim = seccion.perimetro.takeIf { it > 0 }
            val longManual = longitudDada?.takeIf { abs(it - porAbs) > 0.005 }
            val areaManual = areaDada?.takeIf { perim == null || abs(it - longitud * perim) > 0.005 }
            registros += Registro(
                id = UUID.randomUUID().toString(), fecha = fe!!,
                turno = if (Texto.clave(col(f, cTurno)).startsWith("noche")) Turnos.NOCHE else Turnos.DIA,
                seccion = seccion.nombre, responsable = col(f, cResp), cuadrilla = col(f, cCuad),
                absIni = ini, absFin = fin, horaIni = hora(col(f, cHIni)), horaFin = hora(col(f, cHFin)),
                longitudManual = longManual, perimetro = perim, areaManual = areaManual,
                rollos = col(f, cRollos).takeIf { Fmt.leer(it) == null }?.split(',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
                numRollos = Fmt.leer(col(f, cRollos))?.let { Math.round(it).toInt() },
                tempCuna = null, velSoldadura = null, prueba = Prueba.NA, geotextil = Geotextil.OK,
                notas = col(f, cNotas), creado = "importado-%05d".format(n)
            )
        }
        return Resultado(registros, nuevas.values.toList(), errores)
    }

    data class Fusion(val nuevos: List<Registro>, val actualizados: List<Registro>, val iguales: Int)

    /**
     * Combina lo importado con lo que ya hay en el teléfono. Un registro con la misma sección, fecha y abscisas es el
     * mismo: se actualiza con los datos del archivo (jornada, horas, rollos, longitud y cantidad) conservando su
     * identificador, responsable, cuadrilla y notas si el archivo no los trae. Los demás se agregan como nuevos.
     */
    fun fusionar(existentes: List<Registro>, importados: List<Registro>): Fusion {
        val porClave = existentes.associateBy { clave(it) }
        val nuevos = mutableListOf<Registro>(); val act = mutableListOf<Registro>(); var iguales = 0
        importados.forEach { n ->
            val e = porClave[clave(n)]
            if (e == null) { nuevos += n; return@forEach }
            val u = e.copy(
                turno = n.turno,
                horaIni = n.horaIni.ifBlank { e.horaIni }, horaFin = n.horaFin.ifBlank { e.horaFin },
                numRollos = n.numRollos ?: e.numRollos,
                rollos = n.rollos.ifEmpty { e.rollos },
                longitudManual = n.longitudManual, areaManual = n.areaManual, perimetro = n.perimetro ?: e.perimetro,
                responsable = n.responsable.ifBlank { e.responsable }, cuadrilla = n.cuadrilla.ifBlank { e.cuadrilla },
                notas = n.notas.ifBlank { e.notas }
            )
            if (u == e) iguales++ else act += u
        }
        return Fusion(nuevos, act, iguales)
    }

    /** Clave para detectar un registro repetido: misma sección, fecha y abscisas. */
    fun clave(r: Registro): String = "${Texto.clave(r.seccion)}|${r.fecha}|${Math.round(r.absIni * 100)}|${Math.round(r.absFin * 100)}"
}
