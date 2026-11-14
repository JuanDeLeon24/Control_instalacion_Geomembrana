package co.obra.geomembrana

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copia de seguridad completa en un .zip: datos.json (configuración, registros, fotos) + carpeta fotos/.
 * Sirve también para pasar todo a otro teléfono.
 */
object Respaldo {
    private const val APP = "control-geomembrana"

    private fun num(d: Double?): Any = d ?: JSONObject.NULL

    fun registroAJson(r: Registro): JSONObject = JSONObject()
        .put("id", r.id).put("fecha", r.fecha).put("turno", r.turno).put("seccion", r.seccion)
        .put("responsable", r.responsable).put("cuadrilla", r.cuadrilla)
        .put("absIni", r.absIni).put("absFin", r.absFin).put("horaIni", r.horaIni).put("horaFin", r.horaFin)
        .put("longitudManual", num(r.longitudManual)).put("perimetro", num(r.perimetro)).put("areaManual", num(r.areaManual))
        .put("rollos", JSONArray(r.rollos)).put("tempCuna", num(r.tempCuna)).put("velSoldadura", num(r.velSoldadura))
        .put("prueba", r.prueba.name).put("geotextil", r.geotextil.name).put("notas", r.notas).put("creado", r.creado)

    private fun JSONObject.txt(k: String): String = if (isNull(k)) "" else optString(k, "")
    private fun JSONObject.dbl(k: String): Double? = if (!has(k) || isNull(k)) null else optDouble(k).takeIf { !it.isNaN() }

    fun registroDesdeJson(o: JSONObject): Registro {
        val rollos = o.optJSONArray("rollos") ?: JSONArray()
        return Registro(
            id = o.getString("id"), fecha = o.getString("fecha"), turno = o.txt("turno").ifBlank { Turnos.DIA },
            seccion = o.txt("seccion"),
            responsable = o.txt("responsable"), cuadrilla = o.txt("cuadrilla"),
            absIni = o.getDouble("absIni"), absFin = o.getDouble("absFin"),
            horaIni = o.txt("horaIni"), horaFin = o.txt("horaFin"),
            longitudManual = o.dbl("longitudManual"), perimetro = o.dbl("perimetro"), areaManual = o.dbl("areaManual"),
            rollos = (0 until rollos.length()).map { rollos.getString(it) },
            tempCuna = o.dbl("tempCuna"), velSoldadura = o.dbl("velSoldadura"),
            prueba = Prueba.de(o.txt("prueba")), geotextil = Geotextil.de(o.txt("geotextil")),
            notas = o.txt("notas"), creado = o.txt("creado")
        )
    }

    fun exportar(ctx: Context, db: Db, salida: OutputStream) {
        val fotos = db.fotos()
        val json = JSONObject().put("app", APP).put("version", 2).put("creado", T.marca())
            .put("proyecto", Ajustes.proyectoAJson(Ajustes.proyecto(ctx)))
            .put("registros", JSONArray().apply { db.registros().forEach { put(registroAJson(it)) } })
            .put("fotos", JSONArray().apply {
                fotos.forEach { put(JSONObject().put("jornada", it.jornada).put("momento", it.momento.name)
                    .put("archivo", it.archivo).put("tomada", it.tomada)) }
            })
        ZipOutputStream(salida).use { z ->
            z.putNextEntry(ZipEntry("datos.json")); z.write(json.toString(1).toByteArray(Charsets.UTF_8)); z.closeEntry()
            fotos.forEach { f ->
                val a = Fotos.archivo(ctx, f)
                if (a.exists()) {
                    z.putNextEntry(ZipEntry("fotos/" + f.archivo))
                    a.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
    }

    /** Restaura una copia. Reemplaza todos los registros y fotos actuales. Devuelve cuántos registros quedaron. */
    fun importar(ctx: Context, db: Db, entrada: InputStream): Int {
        val tmp = File(ctx.cacheDir, "restaurar").apply { deleteRecursively(); mkdirs() }
        var datos: String? = null
        ZipInputStream(entrada).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val nombre = e.name.substringAfterLast('/')
                when {
                    e.isDirectory -> {}
                    e.name == "datos.json" -> datos = z.readBytes().toString(Charsets.UTF_8)
                    e.name.startsWith("fotos/") && nombre.matches(Regex("[A-Za-z0-9_\\-]+\\.jpg")) ->
                        File(tmp, nombre).outputStream().use { z.copyTo(it) }
                }
            }
        }
        val o = JSONObject(datos ?: throw IllegalArgumentException("El archivo no es una copia de esta app"))
        if (o.optString("app") != APP) throw IllegalArgumentException("El archivo no es una copia de esta app")
        val regs = o.getJSONArray("registros").let { a -> (0 until a.length()).map { registroDesdeJson(a.getJSONObject(it)) } }
        val fotos = o.optJSONArray("fotos")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val f = a.getJSONObject(i)
                val m = runCatching { Momento.valueOf(f.getString("momento")) }.getOrNull() ?: return@mapNotNull null
                Foto(f.getString("jornada"), m, f.getString("archivo"), f.optString("tomada"))
            }
        } ?: emptyList()
        val validas = fotos.filter { File(tmp, it.archivo).exists() }

        // Todo leído sin errores: ahora sí se reemplaza
        val dir = Fotos.dir(ctx)
        dir.listFiles()?.forEach { it.delete() }
        validas.forEach { File(tmp, it.archivo).copyTo(File(dir, it.archivo), overwrite = true) }
        db.reemplazarTodo(regs, validas)
        o.optJSONObject("proyecto")?.let { Ajustes.guardarProyecto(ctx, Ajustes.proyectoDesdeJson(it)) }
        tmp.deleteRecursively()
        return regs.size
    }
}
