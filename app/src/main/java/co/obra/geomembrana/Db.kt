package co.obra.geomembrana

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Información de una foto guardada. El archivo vive en filesDir/fotos/. */
data class Foto(val registroId: String, val momento: Momento, val archivo: String, val tomada: String)

/** Base de datos SQLite dentro del teléfono. Cada cambio se guarda al instante. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "geomembrana.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE registros(" +
                "id TEXT PRIMARY KEY, fecha TEXT NOT NULL, turno TEXT NOT NULL, responsable TEXT, cuadrilla TEXT, " +
                "abs_ini REAL NOT NULL, abs_fin REAL NOT NULL, hora_ini TEXT, hora_fin TEXT, " +
                "longitud_manual REAL, perimetro REAL, rollos TEXT, temp_cuna REAL, vel_soldadura REAL, " +
                "prueba TEXT, geotextil TEXT, notas TEXT, creado TEXT)"
        )
        db.execSQL(
            "CREATE TABLE fotos(registro_id TEXT NOT NULL, momento TEXT NOT NULL, archivo TEXT NOT NULL, " +
                "tomada TEXT, PRIMARY KEY(registro_id, momento))"
        )
        db.execSQL("CREATE INDEX idx_registros_fecha ON registros(fecha)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión 1: aún no hay migraciones. Las futuras solo deben AGREGAR tablas o columnas.
    }

    private fun Cursor.txt(c: String): String = getColumnIndex(c).let { if (it < 0 || isNull(it)) "" else getString(it) }
    private fun Cursor.dbl(c: String): Double? = getColumnIndex(c).let { if (it < 0 || isNull(it)) null else getDouble(it) }

    private fun leer(c: Cursor) = Registro(
        id = c.txt("id"), fecha = c.txt("fecha"), turno = c.txt("turno"),
        responsable = c.txt("responsable"), cuadrilla = c.txt("cuadrilla"),
        absIni = c.dbl("abs_ini") ?: 0.0, absFin = c.dbl("abs_fin") ?: 0.0,
        horaIni = c.txt("hora_ini"), horaFin = c.txt("hora_fin"),
        longitudManual = c.dbl("longitud_manual"), perimetro = c.dbl("perimetro"),
        rollos = c.txt("rollos").split("\n").map { it.trim() }.filter { it.isNotEmpty() },
        tempCuna = c.dbl("temp_cuna"), velSoldadura = c.dbl("vel_soldadura"),
        prueba = Prueba.de(c.txt("prueba")), geotextil = Geotextil.de(c.txt("geotextil")),
        notas = c.txt("notas"), creado = c.txt("creado")
    )

    fun registros(): List<Registro> =
        readableDatabase.rawQuery("SELECT * FROM registros", null).use { c ->
            val l = mutableListOf<Registro>()
            while (c.moveToNext()) l += leer(c)
            Calc.ordenar(l)
        }

    private fun valores(r: Registro) = ContentValues().apply {
        put("id", r.id); put("fecha", r.fecha); put("turno", r.turno)
        put("responsable", r.responsable); put("cuadrilla", r.cuadrilla)
        put("abs_ini", r.absIni); put("abs_fin", r.absFin)
        put("hora_ini", r.horaIni); put("hora_fin", r.horaFin)
        if (r.longitudManual != null) put("longitud_manual", r.longitudManual) else putNull("longitud_manual")
        if (r.perimetro != null) put("perimetro", r.perimetro) else putNull("perimetro")
        put("rollos", r.rollos.joinToString("\n"))
        if (r.tempCuna != null) put("temp_cuna", r.tempCuna) else putNull("temp_cuna")
        if (r.velSoldadura != null) put("vel_soldadura", r.velSoldadura) else putNull("vel_soldadura")
        put("prueba", r.prueba.name); put("geotextil", r.geotextil.name)
        put("notas", r.notas); put("creado", r.creado)
    }

    fun guardar(r: Registro) {
        writableDatabase.insertWithOnConflict("registros", null, valores(r), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun eliminar(id: String) {
        writableDatabase.delete("registros", "id = ?", arrayOf(id))
        writableDatabase.delete("fotos", "registro_id = ?", arrayOf(id))
    }

    // ---------- Fotos ----------

    fun fotos(): List<Foto> =
        readableDatabase.rawQuery("SELECT * FROM fotos", null).use { c ->
            val l = mutableListOf<Foto>()
            while (c.moveToNext()) {
                val m = runCatching { Momento.valueOf(c.txt("momento")) }.getOrNull() ?: continue
                l += Foto(c.txt("registro_id"), m, c.txt("archivo"), c.txt("tomada"))
            }
            l
        }

    fun fotosDe(registroId: String): Map<Momento, Foto> =
        fotos().filter { it.registroId == registroId }.associateBy { it.momento }

    fun guardarFoto(f: Foto) {
        val v = ContentValues().apply {
            put("registro_id", f.registroId); put("momento", f.momento.name)
            put("archivo", f.archivo); put("tomada", f.tomada)
        }
        writableDatabase.insertWithOnConflict("fotos", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun quitarFoto(registroId: String, m: Momento) {
        writableDatabase.delete("fotos", "registro_id = ? AND momento = ?", arrayOf(registroId, m.name))
    }

    /** Reemplaza todo el contenido (restaurar copia de seguridad). */
    fun reemplazarTodo(registros: List<Registro>, fotos: List<Foto>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("registros", null, null)
            db.delete("fotos", null, null)
            registros.forEach { db.insert("registros", null, valores(it)) }
            fotos.forEach { f ->
                db.insert("fotos", null, ContentValues().apply {
                    put("registro_id", f.registroId); put("momento", f.momento.name)
                    put("archivo", f.archivo); put("tomada", f.tomada)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
