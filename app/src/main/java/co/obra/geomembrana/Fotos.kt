package co.obra.geomembrana

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Fotos de la jornada (inicio, dos intermedias y final) de cada reporte diario.
 * Se guardan reducidas (lado mayor 1600 px, JPEG) en filesDir/fotos/.
 */
object Fotos {
    private const val LADO = 1600

    fun dir(ctx: Context): File = File(ctx.filesDir, "fotos").apply { mkdirs() }
    fun archivo(ctx: Context, f: Foto): File = File(dir(ctx), f.archivo)

    /** Archivo temporal donde la app de cámara deja la foto. */
    fun captura(ctx: Context): File = File(File(ctx.cacheDir, "camara").apply { mkdirs() }, "captura.jpg")

    private fun leerFecha(abrir: () -> InputStream?): String? = runCatching {
        abrir()?.use { s ->
            val e = ExifInterface(s)
            val t = e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME)
            t?.let { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")).toString() }
        }
    }.getOrNull()

    private fun orientacion(abrir: () -> InputStream?): Int = runCatching {
        abrir()?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    private fun decodificar(abrir: () -> InputStream?, lado: Int): Bitmap? {
        val op = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Con inJustDecodeBounds decodeStream siempre devuelve null: solo llena outWidth/outHeight
        val s = abrir() ?: return null
        s.use { BitmapFactory.decodeStream(it, null, op) }
        if (op.outWidth <= 0 || op.outHeight <= 0) return null
        var muestra = 1
        while (maxOf(op.outWidth, op.outHeight) / (muestra * 2) >= lado) muestra *= 2
        val op2 = BitmapFactory.Options().apply { inSampleSize = muestra }
        return abrir()?.use { BitmapFactory.decodeStream(it, null, op2) }
    }

    private fun enderezar(b: Bitmap, o: Int): Bitmap {
        val m = Matrix()
        when (o) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    private fun guardar(ctx: Context, abrir: () -> InputStream?, jornada: String, momento: Momento, db: Db): Foto? {
        val bruto = decodificar(abrir, LADO) ?: return null
        val recto = enderezar(bruto, orientacion(abrir))
        val esc = minOf(1f, LADO.toFloat() / maxOf(recto.width, recto.height))
        val fin = if (esc < 1f) Bitmap.createScaledBitmap(recto, (recto.width * esc).toInt(), (recto.height * esc).toInt(), true) else recto
        val nombre = "${jornada}_${momento.name}_${System.currentTimeMillis()}.jpg"
        FileOutputStream(File(dir(ctx), nombre)).use { fin.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        // Borra la foto anterior de ese momento, si había
        db.fotosDe(jornada)[momento]?.let { archivo(ctx, it).delete() }
        val foto = Foto(jornada, momento, nombre, leerFecha(abrir) ?: T.marca())
        db.guardarFoto(foto)
        return foto
    }

    fun desdeCaptura(ctx: Context, jornada: String, momento: Momento, db: Db): Foto? {
        val f = captura(ctx)
        if (!f.exists() || f.length() == 0L) return null
        val r = guardar(ctx, { f.inputStream() }, jornada, momento, db)
        f.delete()
        return r
    }

    fun desdeUri(ctx: Context, uri: Uri, jornada: String, momento: Momento, db: Db): Foto? =
        guardar(ctx, { ctx.contentResolver.openInputStream(uri) }, jornada, momento, db)

    fun quitar(ctx: Context, f: Foto, db: Db) {
        archivo(ctx, f).delete()
        db.quitarFoto(f.jornada, f.momento)
    }

    /** Imagen reducida para mostrar en pantalla o en el PDF. */
    fun cargar(ctx: Context, f: Foto, lado: Int): Bitmap? {
        val a = archivo(ctx, f)
        if (!a.exists()) return null
        return decodificar({ a.inputStream() }, lado)
    }
}
