package co.obra.geomembrana

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

/** Las cuatro fotos de la jornada de instalación de cada registro. */
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
 * Un registro de avance: lo instalado por una cuadrilla en un turno, entre dos abscisas.
 * Las abscisas se guardan en metros (K6+176.57 = 6176.57).
 */
data class Registro(
    val id: String,
    val fecha: String,              // yyyy-MM-dd (fecha en que inicia el turno)
    val turno: String,              // "Día" | "Noche"
    val responsable: String,        // topógrafo / residente / inspector
    val cuadrilla: String,          // cuadrilla o frente de instalación (opcional)
    val absIni: Double,
    val absFin: Double,
    val horaIni: String,            // HH:mm
    val horaFin: String,            // HH:mm (si es menor que la inicial, cruzó la medianoche)
    val longitudManual: Double?,    // null = se usa absFin - absIni
    val perimetro: Double?,         // desarrollo transversal instalado (m)
    val rollos: List<String>,       // códigos de los rollos usados
    val tempCuna: Double?,          // °C
    val velSoldadura: Double?,      // m/min
    val prueba: Prueba,
    val geotextil: Geotextil,
    val notas: String,
    val creado: String
) {
    /** Longitud por abscisas, sin ajuste. */
    val longitudAbscisas: Double get() = absFin - absIni

    /** Longitud que cuenta para rendimiento: la ajustada a mano (traslapos) o la de abscisas. */
    val longitud: Double get() = longitudManual ?: longitudAbscisas

    /** Área instalada en m² (longitud × perímetro). Null si no hay perímetro. */
    val area: Double? get() = perimetro?.let { longitud * it }

    /** Minutos trabajados en el turno. */
    val minutos: Int? get() = Horas.diff(horaIni, horaFin)
}

/** Datos fijos del proyecto (editables en Configuración). */
data class Proyecto(
    val nombre: String = "Túnel 0",
    val empresa: String = "",
    val titulo: String = "Registro de instalación de geomembrana",
    val codigoFormato: String = "",
    val absIni: Double = 6176.57,
    val absFin: Double = 7173.00,
    val perimetroDefecto: Double? = 12.5,
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
