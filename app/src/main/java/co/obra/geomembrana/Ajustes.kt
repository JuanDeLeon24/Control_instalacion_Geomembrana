package co.obra.geomembrana

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class ModoTema(val etiqueta: String) { SISTEMA("Según el teléfono"), CLARO("Claro (sol directo)"), OSCURO("Oscuro (túnel)") }

data class Ultimos(val responsable: String, val cuadrilla: String, val seccion: String)

/** Configuración del proyecto y preferencias, guardadas en el teléfono. */
object Ajustes {
    private fun sp(ctx: Context): SharedPreferences = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    fun proyectoAJson(p: Proyecto): JSONObject = JSONObject()
        .put("nombre", p.nombre).put("empresa", p.empresa).put("titulo", p.titulo)
        .put("codigoFormato", p.codigoFormato).put("absIni", p.absIni).put("absFin", p.absFin)
        .put("secciones", org.json.JSONArray().apply {
            p.secciones.forEach { put(JSONObject().put("nombre", it.nombre).put("perimetro", it.perimetro).put("terminada", it.terminada).put("grupo", it.grupo)) }
        })
        .put("turnosPorDia", p.turnosPorDia).put("horaInicioDia", p.horaInicioDia)
        .put("horaInicioNoche", p.horaInicioNoche).put("turnosParaEta", p.turnosParaEta)
        .put("tempMin", p.tempMin ?: JSONObject.NULL).put("tempMax", p.tempMax ?: JSONObject.NULL)
        .put("velMin", p.velMin ?: JSONObject.NULL).put("velMax", p.velMax ?: JSONObject.NULL)

    private fun JSONObject.dbl(k: String): Double? = if (!has(k) || isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }

    fun proyectoDesdeJson(o: JSONObject): Proyecto {
        val d = Proyecto()
        return Proyecto(
            nombre = o.optString("nombre", d.nombre), empresa = o.optString("empresa", d.empresa),
            titulo = o.optString("titulo", d.titulo), codigoFormato = o.optString("codigoFormato", d.codigoFormato),
            absIni = o.dbl("absIni") ?: d.absIni, absFin = o.dbl("absFin") ?: d.absFin,
            secciones = o.optJSONArray("secciones")?.let { a ->
                (0 until a.length()).map { i -> a.getJSONObject(i) }.map {
                    Seccion(it.optString("nombre"), it.optDouble("perimetro", 0.0).takeIf { v -> !v.isNaN() } ?: 0.0, it.optBoolean("terminada", false), it.optString("grupo", ""))
                }.filter { it.nombre.isNotBlank() }
            }?.takeIf { it.isNotEmpty() } ?: d.secciones,
            turnosPorDia = o.optInt("turnosPorDia", d.turnosPorDia),
            horaInicioDia = o.optString("horaInicioDia", d.horaInicioDia),
            horaInicioNoche = o.optString("horaInicioNoche", d.horaInicioNoche),
            turnosParaEta = o.optInt("turnosParaEta", d.turnosParaEta),
            tempMin = o.dbl("tempMin"), tempMax = o.dbl("tempMax"), velMin = o.dbl("velMin"), velMax = o.dbl("velMax")
        )
    }

    fun proyecto(ctx: Context): Proyecto =
        sp(ctx).getString("proyecto", null)?.let { runCatching { proyectoDesdeJson(JSONObject(it)) }.getOrNull() } ?: Proyecto()

    fun guardarProyecto(ctx: Context, p: Proyecto) {
        sp(ctx).edit().putString("proyecto", proyectoAJson(p).toString()).apply()
    }

    fun tema(ctx: Context): ModoTema = runCatching { ModoTema.valueOf(sp(ctx).getString("tema", "") ?: "") }.getOrDefault(ModoTema.SISTEMA)
    fun guardarTema(ctx: Context, m: ModoTema) { sp(ctx).edit().putString("tema", m.name).apply() }

    fun ultimos(ctx: Context): Ultimos = sp(ctx).let {
        Ultimos(it.getString("responsable", "") ?: "", it.getString("cuadrilla", "") ?: "", it.getString("seccion", "") ?: "")
    }

    fun recordar(ctx: Context, r: Registro) {
        sp(ctx).edit().putString("responsable", r.responsable).putString("cuadrilla", r.cuadrilla)
            .putString("seccion", r.seccion).apply()
    }

    /**
     * Versión del avance incluido en la app (assets/avance_inicial.csv) que ya se aplicó en este teléfono.
     * Cuando una versión nueva de la app trae datos corregidos o completados, se sube [AVANCE_INICIAL] y al abrir
     * se actualizan los registros existentes (sin duplicar ni borrar nada de lo ingresado a mano).
     */
    const val AVANCE_INICIAL = 3
    fun avanceInicialAplicado(ctx: Context): Int =
        sp(ctx).getInt("avance_inicial_version", if (sp(ctx).getBoolean("avance_inicial", false)) 1 else 0)
    fun marcarAvanceInicial(ctx: Context) { sp(ctx).edit().putInt("avance_inicial_version", AVANCE_INICIAL).apply() }
}

object T {
    fun uid(): String = UUID.randomUUID().toString()
    fun hoy(): String = LocalDate.now().toString()
    fun ahora(): String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    fun marca(): String = LocalDateTime.now().withNano(0).toString()
    fun turnoActual(): String { val h = LocalTime.now().hour; return if (h >= 18 || h < 6) Turnos.NOCHE else Turnos.DIA }

    private fun fecha(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()
    fun fechaCorta(iso: String): String = fecha(iso)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: iso
    fun fechaLarga(iso: String): String =
        fecha(iso)?.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Fmt.ES))
            ?.replaceFirstChar { it.titlecase(Fmt.ES) } ?: iso
    fun fechaHora(d: LocalDateTime?): String =
        d?.format(DateTimeFormatter.ofPattern("EEE d 'de' MMM yyyy, HH:mm", Fmt.ES))?.replaceFirstChar { it.titlecase(Fmt.ES) } ?: "–"
    fun marcaCorta(iso: String): String =
        runCatching { LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) }.getOrDefault(iso)
    fun serialExcel(iso: String): Double? = fecha(iso)?.let { it.toEpochDay() + 25569.0 }
}

/** Logo de la empresa: se elige de la galería del teléfono. */
object Logo {
    private fun archivo(ctx: Context) = File(ctx.filesDir, "logo.png")

    fun cargar(ctx: Context): Bitmap? {
        val f = archivo(ctx)
        if (f.exists()) BitmapFactory.decodeFile(f.path)?.let { return it }
        return runCatching { ctx.assets.open("logo.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }

    fun guardar(ctx: Context, uri: Uri): Boolean = try {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        if (bmp == null) false else {
            val esc = minOf(1f, 900f / maxOf(bmp.width, bmp.height))
            val fin = if (esc < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * esc).toInt(), (bmp.height * esc).toInt(), true) else bmp
            FileOutputStream(archivo(ctx)).use { fin.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    } catch (e: Exception) { false }

    fun quitar(ctx: Context) { archivo(ctx).delete() }
}
