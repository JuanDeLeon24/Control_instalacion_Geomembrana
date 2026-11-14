package co.obra.geomembrana

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Foto de una jornada (reporte diario). [jornada] es la fecha yyyy-MM-dd. El archivo vive en filesDir/fotos/. */
data class Foto(val jornada: String, val momento: Momento, val archivo: String, val tomada: String)

/** Base de datos SQLite dentro del teléfono. Cada cambio se guarda al instante. */
class Db(ctx: Context) : SQLiteOpenHelper(ctx, "geomembrana.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE registros(" +
                "id TEXT PRIMARY KEY, fecha TEXT NOT NULL, turno TEXT NOT NULL, seccion TEXT, responsable TEXT, cuadrilla TEXT, " +
                "abs_ini REAL NOT NULL, abs_fin REAL NOT NULL, hora_ini TEXT, hora_fin TEXT, " +
                "longitud_manual REAL, perimetro REAL, area_manual REAL, rollos TEXT, num_rollos REAL, rollos_estimados INTEGER, temp_cuna REAL, vel_soldadura REAL, " +
                "prueba TEXT, geotextil TEXT, notas TEXT, creado TEXT)"
        )
        db.execSQL("CREATE INDEX idx_registros_fecha ON registros(fecha)")
        crearFotos(db)
    }

    private fun crearFotos(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS fotos_jornada(fecha TEXT NOT NULL, momento TEXT NOT NULL, archivo TEXT NOT NULL, " +
                "tomada TEXT, PRIMARY KEY(fecha, momento))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 -> v2: secciones, cantidad oficial y fotos por jornada (antes por registro)
            db.execSQL("ALTER TABLE registros ADD COLUMN seccion TEXT")
            db.execSQL("ALTER TABLE registros ADD COLUMN area_manual REAL")
            db.execSQL("UPDATE registros SET seccion = 'Bóveda' WHERE seccion IS NULL")
            crearFotos(db)
            db.execSQL(
                "INSERT OR IGNORE INTO fotos_jornada(fecha, momento, archivo, tomada) " +
                    "SELECT r.fecha, f.momento, f.archivo, f.tomada FROM fotos f JOIN registros r ON r.id = f.registro_id"
            )
            db.execSQL("DROP TABLE IF EXISTS fotos")
        }
        if (oldVersion < 3) {
            // v2 -> v3: cantidad de rollos (unidades)
            db.execSQL("ALTER TABLE registros ADD COLUMN num_rollos INTEGER")
        }
        if (oldVersion < 4) {
            // v3 -> v4: rollos estimados (la columna num_rollos ya admite decimales en SQLite)
            db.execSQL("ALTER TABLE registros ADD COLUMN rollos_estimados INTEGER")
        }
    }

    private fun Cursor.txt(c: String): String = getColumnIndex(c).let { if (it < 0 || isNull(it)) "" else getString(it) }
    private fun Cursor.dbl(c: String): Double? = getColumnIndex(c).let { if (it < 0 || isNull(it)) null else getDouble(it) }

    private fun leer(c: Cursor) = Registro(
        id = c.txt("id"), fecha = c.txt("fecha"), turno = c.txt("turno"), seccion = c.txt("seccion"),
        responsable = c.txt("responsable"), cuadrilla = c.txt("cuadrilla"),
        absIni = c.dbl("abs_ini") ?: 0.0, absFin = c.dbl("abs_fin") ?: 0.0,
        horaIni = c.txt("hora_ini"), horaFin = c.txt("hora_fin"),
        longitudManual = c.dbl("longitud_manual"), perimetro = c.dbl("perimetro"), areaManual = c.dbl("area_manual"),
        rollos = c.txt("rollos").split("\n").map { it.trim() }.filter { it.isNotEmpty() },
        numRollos = c.dbl("num_rollos"),
        rollosEstimados = c.getColumnIndex("rollos_estimados").let { it >= 0 && !c.isNull(it) && c.getInt(it) == 1 },
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

    private fun ContentValues.num(k: String, v: Double?) { if (v != null) put(k, v) else putNull(k) }

    private fun valores(r: Registro) = ContentValues().apply {
        put("id", r.id); put("fecha", r.fecha); put("turno", r.turno); put("seccion", r.seccion)
        put("responsable", r.responsable); put("cuadrilla", r.cuadrilla)
        put("abs_ini", r.absIni); put("abs_fin", r.absFin)
        put("hora_ini", r.horaIni); put("hora_fin", r.horaFin)
        num("longitud_manual", r.longitudManual); num("perimetro", r.perimetro); num("area_manual", r.areaManual)
        put("rollos", r.rollos.joinToString("\n"))
        num("num_rollos", r.numRollos); put("rollos_estimados", if (r.rollosEstimados) 1 else 0)
        num("temp_cuna", r.tempCuna); num("vel_soldadura", r.velSoldadura)
        put("prueba", r.prueba.name); put("geotextil", r.geotextil.name)
        put("notas", r.notas); put("creado", r.creado)
    }

    fun guardar(r: Registro) {
        writableDatabase.insertWithOnConflict("registros", null, valores(r), SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Agrega varios registros en una sola transacción (importación). */
    fun agregar(lista: List<Registro>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            lista.forEach { db.insertWithOnConflict("registros", null, valores(it), SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Actualiza varios registros en una sola transacción. */
    fun actualizar(lista: List<Registro>) = agregar(lista)

    fun eliminar(id: String) {
        writableDatabase.delete("registros", "id = ?", arrayOf(id))
    }

    // ---------- Fotos de la jornada ----------

    private fun valoresFoto(f: Foto) = ContentValues().apply {
        put("fecha", f.jornada); put("momento", f.momento.name); put("archivo", f.archivo); put("tomada", f.tomada)
    }

    fun fotos(): List<Foto> =
        readableDatabase.rawQuery("SELECT * FROM fotos_jornada", null).use { c ->
            val l = mutableListOf<Foto>()
            while (c.moveToNext()) {
                val m = runCatching { Momento.valueOf(c.txt("momento")) }.getOrNull() ?: continue
                l += Foto(c.txt("fecha"), m, c.txt("archivo"), c.txt("tomada"))
            }
            l
        }

    fun fotosDe(jornada: String): Map<Momento, Foto> = fotos().filter { it.jornada == jornada }.associateBy { it.momento }

    fun guardarFoto(f: Foto) {
        writableDatabase.insertWithOnConflict("fotos_jornada", null, valoresFoto(f), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun quitarFoto(jornada: String, m: Momento) {
        writableDatabase.delete("fotos_jornada", "fecha = ? AND momento = ?", arrayOf(jornada, m.name))
    }

    /** Reemplaza todo el contenido (restaurar copia de seguridad). */
    fun reemplazarTodo(registros: List<Registro>, fotos: List<Foto>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("registros", null, null)
            db.delete("fotos_jornada", null, null)
            registros.forEach { db.insert("registros", null, valores(it)) }
            fotos.forEach { db.insert("fotos_jornada", null, valoresFoto(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
