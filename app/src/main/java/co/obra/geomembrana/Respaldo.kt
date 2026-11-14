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
        .put("rollos", JSONArray(r.rollos)).put("numRollos", r.numRollos ?: JSONObject.NULL).put("tempCuna", num(r.tempCuna)).put("velSoldadura", num(r.velSoldadura))
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
            numRollos = if (o.has("numRollos") && !o.isNull("numRollos")) o.optInt("numRollos") else null,
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

/**
 * Copia automática diaria (datos + fotos) en Descargas/ControlGeomembrana del teléfono.
 * Esa carpeta NO se borra al desinstalar la app: si algún día hubiera que reinstalar, se recupera todo con
 * "Restaurar copia de seguridad". Se guardan las últimas [MAXIMO] copias.
 */
object Autocopia {
    private const val CARPETA = "ControlGeomembrana"
    private const val PREFIJO = "Geomembrana_auto_"
    private const val MAXIMO = 10

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
    fun ultima(ctx: Context): String = sp(ctx).getString("autocopia", "") ?: ""

    /** Hace la copia si hoy aún no se ha hecho. Devuelve true si la hizo. */
    fun siToca(ctx: Context, db: Db): Boolean {
        val hoy = T.hoy()
        if (ultima(ctx) == hoy || db.registros().isEmpty()) return false
        hacer(ctx, db)
        sp(ctx).edit().putString("autocopia", hoy).apply()
        return true
    }

    fun hacer(ctx: Context, db: Db) {
        val nombre = "$PREFIJO${T.hoy()}.zip"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val r = ctx.contentResolver
            val col = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val ruta = android.os.Environment.DIRECTORY_DOWNLOADS + "/" + CARPETA + "/"
            // Si ya hay una copia de hoy (de esta instalación), se reemplaza
            r.delete(col, "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(nombre, ruta))
            val v = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, nombre)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, ruta)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = r.insert(col, v) ?: return
            try {
                r.openOutputStream(uri)!!.use { Respaldo.exportar(ctx, db, it) }
                v.clear(); v.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                r.update(uri, v, null, null)
            } catch (e: Exception) {
                r.delete(uri, null, null); throw e
            }
            // Conserva solo las últimas copias automáticas
            val viejas = mutableListOf<android.net.Uri>()
            r.query(col, arrayOf(android.provider.MediaStore.MediaColumns._ID, android.provider.MediaStore.MediaColumns.DISPLAY_NAME),
                "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf(ruta, "$PREFIJO%"), "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} DESC")?.use { c ->
                var i = 0
                while (c.moveToNext()) {
                    if (i++ >= MAXIMO) viejas += android.content.ContentUris.withAppendedId(col, c.getLong(0))
                }
            }
            viejas.forEach { runCatching { r.delete(it, null, null) } }
        } else {
            // Android 8 y 9: carpeta de la app en el almacenamiento compartido (se borra al desinstalar)
            val dir = File(ctx.getExternalFilesDir(null), CARPETA).apply { mkdirs() }
            File(dir, nombre).outputStream().use { Respaldo.exportar(ctx, db, it) }
            dir.listFiles { f -> f.name.startsWith(PREFIJO) }?.sortedByDescending { it.name }?.drop(MAXIMO)?.forEach { it.delete() }
        }
    }
}
