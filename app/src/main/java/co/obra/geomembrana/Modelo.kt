package co.obra.geomembrana

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Modelo de datos. Este archivo y Calculos.kt no dependen de Android:
 * se prueban con JUnit en la JVM (app/src/test).
 */

/** Resultado de la prueba de presión del canal de aire de la soldadura de doble cuña. */
enum class Prueba(val etiqueta: String) {
    APROBADO("Aprobado"),
    NO_APROBADO("No aprobado"),
    NA("N.A.");

    companion object {
        fun de(s: String?): Prueba = entries.firstOrNull { it.name == s } ?: NA
    }
}

/** Estado del geotextil de soporte antes de colocar la geomembrana. */
enum class Geotextil(val etiqueta: String) {
    OK("Inspeccionado – OK"),
    OBSERVACIONES("Inspeccionado – con observaciones"),
    NO_INSPECCIONADO("No inspeccionado");

    companion object {
        fun de(s: String?): Geotextil = entries.firstOrNull { it.name == s } ?: NO_INSPECCIONADO
    }
}

/** Las cuatro fotos de cada jornada (reporte diario). */
enum class Momento(val etiqueta: String, val corto: String) {
    INICIO("Inicio de la jornada", "Inicio"),
    INTERMEDIA_1("Momento intermedio 1", "Intermedia 1"),
    INTERMEDIA_2("Momento intermedio 2", "Intermedia 2"),
    FINAL("Final de la jornada", "Final")
}

object Turnos {
    const val DIA = "Día"
    const val NOCHE = "Noche"
    fun orden(t: String): Int = if (t == NOCHE) 1 else 0
}

/**
 * Sección de la geomembrana que se instala a lo largo del túnel (bota HD, bota HI, bóveda…).
 * Cada sección avanza por su cuenta y tiene su propio perímetro (desarrollo transversal).
 * [terminada]: la sección se da por completa aunque por abscisas falten centímetros (se cuenta al 100 %).
 * [grupo]: las secciones del mismo grupo se reportan juntas (Bota = HD + HI); vacío = la sección es su propio grupo.
 */
data class Seccion(val nombre: String, val perimetro: Double, val terminada: Boolean = false, val grupo: String = "") {
    val grupoEfectivo: String get() = grupo.trim().ifBlank { nombre }
}

/**
 * Un registro de avance: lo instalado de una sección en un turno, entre dos abscisas.
 * Las abscisas se guardan en metros (K6+176.57 = 6176.57) y en el orden en que se escribieron:
 * la instalación puede avanzar en cualquier sentido (p. ej. de K7+173 hacia K6+176).
 */
data class Registro(
    val id: String,
    val fecha: String,              // yyyy-MM-dd (fecha en que inicia el turno)
    val turno: String,              // "Día" | "Noche"
    val seccion: String,            // nombre de la sección
    val responsable: String,
    val cuadrilla: String,
    val absIni: Double,             // abscisa donde empezó la instalación
    val absFin: Double,             // abscisa donde terminó
    val horaIni: String,            // HH:mm
    val horaFin: String,            // HH:mm (si es menor que la inicial, cruzó la medianoche)
    val longitudManual: Double?,    // null = |absFin - absIni|
    val perimetro: Double?,         // desarrollo transversal instalado (m)
    val areaManual: Double?,        // cantidad oficial en m² si difiere de longitud × perímetro (redondeos)
    val rollos: List<String>,       // códigos de rollos (opcional, versión anterior)
    val numRollos: Int?,            // cantidad de rollos usados (unidades)
    val tempCuna: Double?,          // °C
    val velSoldadura: Double?,      // m/min
    val prueba: Prueba,
    val geotextil: Geotextil,
    val notas: String,
    val creado: String
) {
    /** Abscisa menor y mayor del tramo, sin importar el sentido. */
    val desde: Double get() = min(absIni, absFin)
    val hasta: Double get() = max(absIni, absFin)

    /** Longitud por abscisas. */
    val longitudAbscisas: Double get() = abs(absFin - absIni)

    /** Longitud que cuenta para rendimiento: la ajustada a mano o la de abscisas. */
    val longitud: Double get() = longitudManual ?: longitudAbscisas

    /** Área instalada en m²: la cantidad oficial si se ajustó, si no longitud × perímetro. */
    val area: Double? get() = areaManual ?: perimetro?.let { longitud * it }

    /** Minutos trabajados en el turno. */
    val minutos: Int? get() = Horas.diff(horaIni, horaFin)
}

/** Datos del proyecto (editables en Configuración). */
data class Proyecto(
    val nombre: String = "Túnel 0",
    val empresa: String = "",
    val titulo: String = "Registro de instalación de geomembrana",
    val codigoFormato: String = "",
    val absIni: Double = 6176.57,
    val absFin: Double = 7173.00,
    val secciones: List<Seccion> = SECCIONES,
    val turnosPorDia: Int = 2,           // 1 = solo turno día, 2 = día y noche
    val horaInicioDia: String = "06:00",
    val horaInicioNoche: String = "18:00",
    val turnosParaEta: Int = 6,          // últimos turnos que se promedian para la proyección
    val tempMin: Double? = null,         // especificación de temperatura de cuña / extrusora
    val tempMax: Double? = null,
    val velMin: Double? = null,          // especificación de velocidad de soldadura
    val velMax: Double? = null
) {
    val longitudTotal: Double get() = absFin - absIni

    fun seccion(nombre: String): Seccion? = secciones.firstOrNull { Texto.clave(it.nombre) == Texto.clave(nombre) }

    companion object {
        val SECCIONES = listOf(
            Seccion("Bota HD", 2.0, terminada = true, grupo = "Bota"),
            Seccion("Bota HI", 2.0, terminada = true, grupo = "Bota"),
            Seccion("Bóveda", 19.0, grupo = "Bóveda")
        )
    }
}

object Texto {
    /** "Bóveda " -> "boveda": para comparar nombres sin tildes, mayúsculas ni espacios extra. */
    fun clave(s: String): String = java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("\\s+"), " ")
}

object Horas {
    fun aMin(t: String): Int? {
        val p = t.trim().split(":")
        if (p.size != 2) return null
        val h = p[0].trim().toIntOrNull() ?: return null
        val m = p[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** Diferencia en minutos; si cruza la medianoche suma 24 h. */
    fun diff(a: String, b: String): Int? {
        val x = aMin(a) ?: return null
        val y = aMin(b) ?: return null
        var d = y - x
        if (d < 0) d += 1440
        return d
    }

    fun formato(min: Int): String = "%02d:%02d".format(min / 60 % 24, min % 60)
}
