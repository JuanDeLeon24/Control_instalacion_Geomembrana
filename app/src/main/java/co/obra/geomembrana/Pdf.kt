package co.obra.geomembrana

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Informes en PDF (carta horizontal) generados en el teléfono:
 *  - Reporte diario: lo instalado en una fecha (longitud, perímetro y ÁREA de la jornada),
 *    avance acumulado, calidad y anexo con las 4 fotos de cada registro.
 *  - Informe de avance: todo el proyecto, con desgloses por fecha, rollo y cuadrilla.
 */
object Pdf {
    private const val PW = 792
    private const val PH = 612
    private const val M = 28f
    private val AZUL = Color.rgb(0, 47, 92)
    private val CLARO = Color.rgb(222, 232, 246)
    private val LINEA = Color.rgb(70, 80, 92)
    private val TINTA = Color.rgb(20, 25, 30)
    private val GRIS = Color.rgb(110, 118, 128)
    private val GEO = Color.rgb(242, 183, 5)
    private val GEO_DIA = Color.rgb(0, 144, 174)
    private val PEND = Color.rgb(236, 239, 243)
    private val ROJO = Color.rgb(164, 22, 26)

    private val NORMAL = Layout.Alignment.ALIGN_NORMAL
    private val CENTRO = Layout.Alignment.ALIGN_CENTER

    private val borde = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = LINEA }
    private val relleno = Paint().apply { style = Paint.Style.FILL }

    private fun tp(size: Float, bold: Boolean = false, color: Int = TINTA) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
        }

    private fun lay(t: String, p: TextPaint, w: Float, a: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(t, 0, t.length, p, maxOf(1, w.toInt())).setAlignment(a).setIncludePad(false).build()

    private fun alto(t: String, p: TextPaint, w: Float, pad: Float = 3f): Float =
        if (t.isEmpty()) 0f else lay(t, p, w - 2 * pad, NORMAL).height + 2 * pad

    private fun celda(
        c: Canvas, x: Float, y: Float, w: Float, h: Float, t: String, p: TextPaint,
        a: Layout.Alignment = NORMAL, fondo: Int? = null, pad: Float = 3f, conBorde: Boolean = true
    ) {
        if (fondo != null) { relleno.color = fondo; c.drawRect(x, y, x + w, y + h, relleno) }
        if (conBorde) c.drawRect(x, y, x + w, y + h, borde)
        if (t.isNotEmpty()) {
            val l = lay(t, p, w - 2 * pad, a)
            c.save()
            c.clipRect(x, y, x + w, y + h)
            c.translate(x + pad, y + (h - l.height) / 2f)
            l.draw(c)
            c.restore()
        }
    }

    private fun texto(c: Canvas, x: Float, y: Float, w: Float, t: String, p: TextPaint): Float {
        val l = lay(t, p, w, NORMAL)
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return l.height.toFloat()
    }

    /** Lienzo con paginación automática. */
    private class Hojas(val doc: PdfDocument, val pie: String) {
        var num = 0
        var pagina: PdfDocument.Page? = null
        lateinit var c: Canvas
        var y = M
        val limite = PH - M - 14f
        var alCambiar: (() -> Unit)? = null

        fun nueva() {
            pagina?.let { doc.finishPage(it) }
            num++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, num).create())
            pagina = p; c = p.canvas; y = M
            val f = tp(7f, color = Color.GRAY)
            c.drawText(pie, M, PH - 12f, f)
            val t = "Página $num"
            c.drawText(t, PW - M - f.measureText(t), PH - 12f, f)
        }

        /** Si no caben [h] puntos, pasa de página (y repite el encabezado de tabla si hay uno). */
        fun espacio(h: Float) {
            if (y + h > limite) { nueva(); alCambiar?.invoke() }
        }

        fun cerrar() { pagina?.let { doc.finishPage(it) }; pagina = null }
    }

    private val ancho = PW - 2 * M

    private fun encabezado(h: Hojas, p: Proyecto, logo: Bitmap?, subtitulo: String) {
        val c = h.c
        val hEnc = 46f
        celda(c, M, h.y, 120f, hEnc, if (logo == null) p.empresa.uppercase() else "", tp(8f, true, AZUL), CENTRO)
        if (logo != null) {
            val s = minOf((120f - 12f) / logo.width, (hEnc - 10f) / logo.height)
            val w = logo.width * s; val hh = logo.height * s
            val left = M + (120f - w) / 2f; val top = h.y + (hEnc - hh) / 2f
            c.drawBitmap(logo, null, RectF(left, top, left + w, top + hh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        celda(c, M + 120f, h.y, ancho - 240f, hEnc, "${p.titulo}\n$subtitulo", tp(11.5f, true, AZUL), CENTRO)
        celda(c, M + ancho - 120f, h.y, 120f, hEnc, p.codigoFormato, tp(10f, true, AZUL), CENTRO)
        h.y += hEnc
    }

    private fun filaInfo(h: Hojas, pares: List<Pair<String, String>>, anchos: List<Float>) {
        val lab = tp(7.5f, true, AZUL); val v = tp(8f)
        var x = M
        pares.forEachIndexed { i, (k, valor) ->
            val we = anchos[i * 2]; val wv = anchos[i * 2 + 1]
            celda(h.c, x, h.y, we, 17f, k, lab, fondo = CLARO); x += we
            celda(h.c, x, h.y, wv, 17f, valor, v); x += wv
        }
        h.y += 17f
    }

    private fun kpis(h: Hojas, items: List<Triple<String, String, String>>, alto: Float = 42f, fondo: Int? = null) {
        val w = ancho / items.size
        items.forEachIndexed { i, (et, valor, sub) ->
            val x = M + i * w
            if (fondo != null) { relleno.color = fondo; h.c.drawRect(x, h.y, x + w, h.y + alto, relleno) }
            h.c.drawRect(x, h.y, x + w, h.y + alto, borde)
            celda(h.c, x, h.y + 2f, w, 11f, et.uppercase(), tp(6.5f, true, GRIS), CENTRO, conBorde = false)
            celda(h.c, x, h.y + 12f, w, 17f, valor, tp(12.5f, true, AZUL), CENTRO, conBorde = false)
            celda(h.c, x, h.y + 29f, w, 11f, sub, tp(6.8f, color = GRIS), CENTRO, conBorde = false)
        }
        h.y += alto
    }

    private fun titulo(h: Hojas, t: String) {
        h.espacio(40f)
        h.y += 8f
        celda(h.c, M, h.y, ancho, 16f, t, tp(9f, true, Color.WHITE), fondo = AZUL)
        h.y += 16f
    }

    /** Franja del túnel: tramos instalados (amarillo), los de la jornada (azul) y lo pendiente (gris). */
    private fun franja(h: Hojas, p: Proyecto, acumulado: List<Registro>, delDia: List<Registro>) {
        h.espacio(48f)
        h.y += 8f
        val c = h.c
        val x0 = M; val w = ancho; val y0 = h.y + 4f; val alto = 18f
        fun xDe(a: Double) = x0 + ((a - p.absIni) / p.longitudTotal * w).toFloat().coerceIn(0f, w)
        relleno.color = PEND; c.drawRect(x0, y0, x0 + w, y0 + alto, relleno)
        relleno.color = GEO
        Calc.cobertura(acumulado, p).tramos.forEach { t -> c.drawRect(xDe(t.ini), y0, xDe(t.fin).coerceAtLeast(xDe(t.ini) + 0.8f), y0 + alto, relleno) }
        relleno.color = GEO_DIA
        Calc.cobertura(delDia, p).tramos.forEach { t -> c.drawRect(xDe(t.ini), y0, xDe(t.fin).coerceAtLeast(xDe(t.ini) + 0.8f), y0 + alto, relleno) }
        c.drawRect(x0, y0, x0 + w, y0 + alto, borde)
        // Marcas cada 100 m
        val f = tp(6.3f, color = GRIS)
        var a = Math.ceil(p.absIni / 100.0) * 100.0
        while (a <= p.absFin + 1e-6) {
            val x = xDe(a)
            c.drawLine(x, y0 + alto, x, y0 + alto + 3f, borde)
            val t = Abscisa.texto(a).substringBefore('.')
            val tw = f.measureText(t)
            c.drawText(t, (x - tw / 2).coerceIn(x0, x0 + w - tw), y0 + alto + 10f, f)
            a += 100.0
        }
        // Leyenda
        val ly = y0 + alto + 20f
        var lx = x0
        listOf(GEO to "Instalado", GEO_DIA to "Instalado en la jornada", PEND to "Pendiente").forEach { (col, et) ->
            if (et.endsWith("jornada") && delDia.isEmpty()) return@forEach
            relleno.color = col; c.drawRect(lx, ly - 6f, lx + 10f, ly + 1f, relleno); c.drawRect(lx, ly - 6f, lx + 10f, ly + 1f, borde)
            c.drawText(et, lx + 13f, ly, f); lx += 22f + f.measureText(et)
        }
        h.y = ly + 4f
    }

    // ---------- Tabla de registros ----------

    private val COLS = listOf(
        "Fecha" to 46f, "Turno" to 34f, "Abscisa\ninicial" to 54f, "Abscisa\nfinal" to 54f, "Longitud\n(m)" to 42f,
        "Perímetro\n(m)" to 42f, "Área\n(m²)" to 48f, "Horario" to 56f, "Horas" to 32f, "m/h" to 32f, "m²/h" to 38f,
        "Rollos" to 72f, "T° cuña\n(°C)" to 36f, "Vel.\n(m/min)" to 34f, "Prueba de\npresión" to 48f, "Geotextil" to 68f
    )

    private fun tablaRegistros(h: Hojas, regs: List<Registro>, p: Proyecto) {
        val enc = tp(6.8f, true, Color.WHITE); val f = tp(7.2f); val fRojo = tp(7.2f, true, ROJO)
        val anchos = COLS.map { it.second }
        val extra = (ancho - anchos.sum()) / anchos.size
        val ws = anchos.map { it + extra }
        fun cabecera() {
            var x = M
            COLS.forEachIndexed { i, (t, _) -> celda(h.c, x, h.y, ws[i], 22f, t, enc, CENTRO, fondo = AZUL); x += ws[i] }
            h.y += 22f
        }
        h.espacio(50f)
        cabecera()
        h.alCambiar = { cabecera() }
        regs.forEach { r ->
            val hrs = r.minutos?.takeIf { it > 0 }?.let { it / 60.0 }
            val alertaT = r.tempCuna != null && ((p.tempMin != null && r.tempCuna < p.tempMin) || (p.tempMax != null && r.tempCuna > p.tempMax))
            val alertaV = r.velSoldadura != null && ((p.velMin != null && r.velSoldadura < p.velMin) || (p.velMax != null && r.velSoldadura > p.velMax))
            val vals = listOf(
                T.fechaCorta(r.fecha).take(5), r.turno, Abscisa.texto(r.absIni), Abscisa.texto(r.absFin),
                Fmt.fijo(r.longitud) + if (r.longitudManual != null) "*" else "", Fmt.num(r.perimetro), Fmt.fijo(r.area),
                if (r.horaIni.isNotBlank() || r.horaFin.isNotBlank()) "${r.horaIni}–${r.horaFin}" else "",
                Fmt.num(hrs), Fmt.num(hrs?.let { r.longitud / it }), Fmt.num(if (hrs != null && r.area != null) r.area!! / hrs else null, 1),
                r.rollos.joinToString(", "),
                Fmt.num(r.tempCuna, 1), Fmt.num(r.velSoldadura), r.prueba.etiqueta, r.geotextil.etiqueta
            )
            val hFila = maxOf(15f, alto(vals[11], f, ws[11]), alto(vals[15], f, ws[15]))
            h.espacio(hFila)
            var x = M
            vals.forEachIndexed { i, v ->
                val rojo = (i == 12 && alertaT) || (i == 13 && alertaV) || (i == 14 && r.prueba == Prueba.NO_APROBADO)
                celda(h.c, x, h.y, ws[i], hFila, v.ifBlank { if (i in 4..14) "–" else "" }, if (rojo) fRojo else f,
                    if (i == 11 || i == 15) NORMAL else CENTRO)
                x += ws[i]
            }
            h.y += hFila
        }
        // Totales
        val rend = Calc.rendimiento(regs)
        val tot = tp(7.5f, true, AZUL)
        h.espacio(17f)
        val wEt = ws.take(4).sum()
        celda(h.c, M, h.y, wEt, 17f, "TOTAL  (${regs.size} ${if (regs.size == 1) "registro" else "registros"})", tot, fondo = CLARO)
        var x = M + wEt
        val totales = listOf(Fmt.fijo(rend.longitud), "", Fmt.fijo(rend.area), "", Fmt.num(rend.minutos / 60.0),
            Fmt.num(rend.mPorHora), Fmt.num(rend.m2PorHora, 1))
        totales.forEachIndexed { i, t -> celda(h.c, x, h.y, ws[4 + i], 17f, t, tot, CENTRO, fondo = CLARO); x += ws[4 + i] }
        celda(h.c, x, h.y, ancho - (x - M), 17f, "", tot, fondo = CLARO)
        h.y += 17f
        h.alCambiar = null
        if (regs.any { it.longitudManual != null }) {
            h.espacio(12f)
            h.y += 2f + texto(h.c, M, h.y + 2f, ancho, "* Longitud ajustada a mano (traslapos); el área usa esa longitud.", tp(6.8f, color = GRIS))
        }
    }

    private fun notas(h: Hojas, regs: List<Registro>) {
        val conInfo = regs.filter { it.notas.isNotBlank() || it.responsable.isNotBlank() || it.cuadrilla.isNotBlank() }
        if (conInfo.isEmpty()) return
        titulo(h, "Responsables y observaciones")
        val f = tp(7.5f)
        conInfo.forEach { r ->
            val t = "${T.fechaCorta(r.fecha)} · ${r.turno} · ${Abscisa.texto(r.absIni)} – ${Abscisa.texto(r.absFin)}" +
                (if (r.responsable.isNotBlank()) " · Responsable: ${r.responsable}" else "") +
                (if (r.cuadrilla.isNotBlank()) " · Cuadrilla: ${r.cuadrilla}" else "") +
                "\n" + r.notas.ifBlank { "Sin observaciones." }
            val hh = alto(t, f, ancho)
            h.espacio(hh)
            celda(h.c, M, h.y, ancho, hh, t, f)
            h.y += hh
        }
    }

    private fun calidad(h: Hojas, regs: List<Registro>, p: Proyecto) {
        val q = Calc.calidad(regs, p)
        titulo(h, "Control de calidad")
        val f = tp(7.8f)
        fun esp(a: Double?, b: Double?, u: String) = when {
            a != null && b != null -> " (especificación ${Fmt.num(a)} a ${Fmt.num(b)} $u)"
            a != null -> " (mínimo ${Fmt.num(a)} $u)"
            b != null -> " (máximo ${Fmt.num(b)} $u)"
            else -> ""
        }
        val lineas = listOf(
            "Temperatura de cuña / extrusora: " + (if (q.conTemp == 0) "sin datos" else
                "promedio ${Fmt.num(q.tempProm, 1)} °C, rango ${Fmt.num(q.tempMin, 1)} a ${Fmt.num(q.tempMax, 1)} °C" +
                    esp(p.tempMin, p.tempMax, "°C") + (q.tempFuera?.let { " · $it fuera de especificación" } ?: "")),
            "Velocidad de soldadura: " + (if (q.conVel == 0) "sin datos" else
                "promedio ${Fmt.num(q.velProm)} m/min, rango ${Fmt.num(q.velMin)} a ${Fmt.num(q.velMax)} m/min" +
                    esp(p.velMin, p.velMax, "m/min") + (q.velFuera?.let { " · $it fuera de especificación" } ?: "")),
            "Prueba de presión del canal de aire: ${q.aprobadas} aprobadas · ${q.noAprobadas} no aprobadas · ${q.pruebasNA} N.A.",
            "Geotextil de soporte: ${q.geoOk} OK · ${q.geoObs} con observaciones · ${q.geoNo} no inspeccionado"
        )
        lineas.forEach { t -> val hh = alto(t, f, ancho); h.espacio(hh); celda(h.c, M, h.y, ancho, hh, t, f); h.y += hh }
        if (q.alertas.isNotEmpty()) {
            val fr = tp(7.5f, color = ROJO)
            q.alertas.forEach { (r, a) ->
                val t = "${T.fechaCorta(r.fecha)} ${r.turno} (${Abscisa.texto(r.absIni)} – ${Abscisa.texto(r.absFin)}): ${a.joinToString(" · ")}"
                val hh = alto(t, fr, ancho); h.espacio(hh); celda(h.c, M, h.y, ancho, hh, t, fr); h.y += hh
            }
        }
    }

    private fun tablaSimple(h: Hojas, titulos: List<String>, anchosRel: List<Float>, filas: List<List<String>>) {
        val total = anchosRel.sum()
        val ws = anchosRel.map { it / total * ancho }
        val enc = tp(7f, true, Color.WHITE); val f = tp(7.5f)
        fun cab() {
            var x = M
            titulos.forEachIndexed { i, t -> celda(h.c, x, h.y, ws[i], 18f, t, enc, CENTRO, fondo = AZUL); x += ws[i] }
            h.y += 18f
        }
        h.espacio(40f); cab()
        h.alCambiar = { cab() }
        filas.forEach { fila ->
            h.espacio(14f)
            var x = M
            fila.forEachIndexed { i, t -> celda(h.c, x, h.y, ws[i], 14f, t, f, if (i == 0) NORMAL else CENTRO); x += ws[i] }
            h.y += 14f
        }
        h.alCambiar = null
    }

    private fun firmas(h: Hojas, responsables: List<String>) {
        h.espacio(70f)
        h.y += 40f
        val w = (ancho - 60f) / 3f
        val f = tp(7.8f, true, AZUL); val g = tp(7f, color = GRIS)
        listOf("Elaboró" to responsables.joinToString(", "), "Revisó (Inspección / Interventoría)" to "", "Aprobó (Residente de obra)" to "")
            .forEachIndexed { i, (et, nombre) ->
                val x = M + i * (w + 30f)
                h.c.drawLine(x, h.y, x + w, h.y, borde)
                h.c.drawText(et, x, h.y + 11f, f)
                if (nombre.isNotBlank()) h.c.drawText(nombre.take(70), x, h.y + 21f, g)
            }
        h.y += 26f
    }

    private fun anexoFotos(ctx: Context, h: Hojas, p: Proyecto, logo: Bitmap?, regs: List<Registro>, fotos: List<Foto>) {
        val porReg = fotos.groupBy { it.registroId }
        regs.filter { porReg[it.id]?.isNotEmpty() == true }.forEach { r ->
            h.nueva()
            encabezado(h, p, logo, "Anexo fotográfico")
            h.y += 6f
            celda(h.c, M, h.y, ancho, 18f,
                "${T.fechaLarga(r.fecha)} · Turno ${r.turno} · ${Abscisa.texto(r.absIni)} – ${Abscisa.texto(r.absFin)} · " +
                    "${Fmt.fijo(r.longitud)} m × ${Fmt.num(r.perimetro)} m = ${Fmt.fijo(r.area)} m²",
                tp(8.5f, true, AZUL), fondo = CLARO)
            h.y += 22f
            val mapa = porReg[r.id].orEmpty().associateBy { it.momento }
            val gap = 8f
            val cw = (ancho - gap) / 2f
            val ch = (h.limite - h.y - gap) / 2f
            Momento.entries.forEachIndexed { i, m ->
                val x = M + (i % 2) * (cw + gap)
                val y = h.y + (i / 2) * (ch + gap)
                h.c.drawRect(x, y, x + cw, y + ch, borde)
                val foto = mapa[m]
                val pie = m.etiqueta + (foto?.let { " · " + T.marcaCorta(it.tomada) } ?: " · sin foto")
                celda(h.c, x, y + ch - 14f, cw, 14f, pie, tp(7.5f, true, AZUL), CENTRO, fondo = CLARO)
                val bmp = foto?.let { runCatching { Fotos.cargar(ctx, it, 1200) }.getOrNull() }
                if (bmp != null) {
                    val aw = cw - 8f; val ah = ch - 22f
                    val s = minOf(aw / bmp.width, ah / bmp.height)
                    val w = bmp.width * s; val hh = bmp.height * s
                    val left = x + (cw - w) / 2f; val top = y + 4f + (ah - hh) / 2f
                    h.c.drawBitmap(bmp, null, RectF(left, top, left + w, top + hh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                    bmp.recycle()
                } else {
                    celda(h.c, x, y, cw, ch - 14f, "Sin foto", tp(9f, color = GRIS), CENTRO, conBorde = false)
                }
            }
        }
    }

    /**
     * @param dia fecha (yyyy-MM-dd) para el reporte diario; null = informe de avance de todo el proyecto.
     */
    fun generar(
        ctx: Context, todos: List<Registro>, fotos: List<Foto>, p: Proyecto, logo: Bitmap?,
        dia: String?, conFotos: Boolean
    ): File {
        val doc = PdfDocument()
        val generado = "Generado el " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
            " · Control Geomembrana · ${p.nombre}"
        val h = Hojas(doc, generado)
        h.nueva()

        val acumulado = Calc.ordenar(if (dia == null) todos else todos.filter { it.fecha <= dia })
        val alcance = if (dia == null) acumulado else acumulado.filter { it.fecha == dia }
        val cob = Calc.cobertura(acumulado, p)
        val eta = Calc.eta(acumulado, p, cob)
        val rendAc = Calc.rendimiento(acumulado)
        val areaTot = Calc.areaTotal(p)

        encabezado(h, p, logo, if (dia == null) "Informe de avance acumulado" else "Reporte diario de instalación")
        filaInfo(h, listOf(
            "Proyecto:" to p.nombre,
            "Abscisas del túnel:" to "${Abscisa.texto(p.absIni)} – ${Abscisa.texto(p.absFin)}",
            "Longitud:" to "${Fmt.fijo(p.longitudTotal)} m",
            "Perímetro ref.:" to (p.perimetroDefecto?.let { "${Fmt.num(it)} m" } ?: "–")
        ), listOf(50f, 120f, 82f, 140f, 44f, 80f, 62f, 158f).let { l -> val s = l.sum(); l.map { it / s * ancho } })
        filaInfo(h, listOf(
            (if (dia == null) "Corte al:" else "Fecha:") to (if (dia == null) T.fechaLarga(acumulado.lastOrNull()?.fecha ?: T.hoy()) else T.fechaLarga(dia)),
            "Turnos:" to alcance.map { it.turno }.distinct().joinToString(" y ").ifBlank { "–" },
            "Responsable(s):" to alcance.map { it.responsable }.filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { "–" }
        ), listOf(50f, 210f, 44f, 80f, 76f, 276f).let { l -> val s = l.sum(); l.map { it / s * ancho } })
        h.y += 6f

        if (dia != null) {
            val rd = Calc.rendimiento(alcance)
            kpis(h, listOf(
                Triple("Longitud instalada en la jornada", "${Fmt.fijo(rd.longitud)} m", "${alcance.size} registro(s)"),
                Triple("Área cubierta en la jornada", "${Fmt.fijo(rd.area)} m²", "longitud × perímetro"),
                Triple("Horas trabajadas", Fmt.duracion(rd.minutos.toDouble()), ""),
                Triple("Rendimiento", "${Fmt.num(rd.mPorHora)} m/h", "${Fmt.num(rd.m2PorHora, 1)} m²/h")
            ), fondo = Color.rgb(255, 246, 214))
            h.y += 4f
        }
        kpis(h, listOf(
            Triple("Avance acumulado", Fmt.pct(cob.porcentaje), "${Fmt.fijo(cob.cubierto)} de ${Fmt.fijo(cob.total)} m"),
            Triple("Área acumulada", "${Fmt.fijo(rendAc.area)} m²", areaTot?.let { "de ${Fmt.fijo(it)} m² estimados" } ?: ""),
            Triple("Pendiente", "${Fmt.fijo(cob.restante)} m", "${cob.pendientes.size} tramo(s)"),
            Triple("Rendimiento acumulado", "${Fmt.num(rendAc.mPorHora)} m/h", "${Fmt.num(rendAc.m2PorHora, 1)} m²/h"),
            Triple("Terminación estimada", if (eta.completado) "Completado" else (eta.fechaHora?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) ?: "–"),
                eta.mPorTurno?.let { "${Fmt.num(it)} m/turno (últimos ${eta.turnosUsados})" } ?: "sin datos")
        ))
        franja(h, p, acumulado, if (dia == null) emptyList() else alcance)

        titulo(h, if (dia == null) "Registros de instalación" else "Registros de la jornada")
        tablaRegistros(h, alcance, p)
        notas(h, alcance)

        if (dia == null) {
            titulo(h, "Avance por fecha")
            var acum = 0.0
            tablaSimple(h, listOf("Fecha", "Turnos", "Longitud (m)", "Área (m²)", "Horas", "m/h", "m²/h", "Acumulado (m)"),
                listOf(2f, 1f, 1.4f, 1.4f, 1f, 1f, 1f, 1.4f),
                Calc.porFecha(acumulado).map { x ->
                    val r = x.rend; acum += r.longitud
                    listOf(T.fechaCorta(x.fecha), x.turnos.toString(), Fmt.fijo(r.longitud), Fmt.fijo(r.area),
                        Fmt.num(r.minutos / 60.0), Fmt.num(r.mPorHora), Fmt.num(r.m2PorHora, 1), Fmt.fijo(acum))
                })
            val rollos = Calc.porRollo(acumulado)
            if (rollos.isNotEmpty()) {
                titulo(h, "Desglose por rollo (longitud y área repartidas entre los rollos de cada registro)")
                tablaSimple(h, listOf("Rollo", "Registros", "Longitud (m)", "Área (m²)", "Primer uso", "Último uso"),
                    listOf(2f, 1f, 1.3f, 1.3f, 1.3f, 1.3f),
                    rollos.map { listOf(it.codigo, it.registros.toString(), Fmt.fijo(it.longitud), Fmt.fijo(it.area),
                        T.fechaCorta(it.primera), T.fechaCorta(it.ultima)) })
            }
            titulo(h, "Rendimiento por cuadrilla")
            tablaSimple(h, listOf("Cuadrilla / responsable", "Turnos", "Longitud (m)", "Área (m²)", "Horas", "m/h", "m²/h"),
                listOf(3f, 1f, 1.3f, 1.3f, 1f, 1f, 1f),
                Calc.porCuadrilla(acumulado).map { listOf(it.nombre, it.turnos.toString(), Fmt.fijo(it.longitud), Fmt.fijo(it.area),
                    Fmt.num(it.minutos / 60.0), Fmt.num(it.mPorHora), Fmt.num(it.m2PorHora, 1)) })
            if (cob.pendientes.isNotEmpty()) {
                titulo(h, "Tramos pendientes por instalar")
                tablaSimple(h, listOf("Desde", "Hasta", "Longitud (m)"), listOf(1f, 1f, 1f),
                    cob.pendientes.map { listOf(Abscisa.texto(it.ini), Abscisa.texto(it.fin), Fmt.fijo(it.longitud)) })
            }
        }

        calidad(h, alcance, p)
        firmas(h, alcance.map { it.responsable }.filter { it.isNotBlank() }.distinct())

        if (conFotos) anexoFotos(ctx, h, p, logo, alcance, fotos)
        h.cerrar()

        val base = if (dia == null) "Avance_geomembrana_${T.hoy()}" else "Reporte_geomembrana_$dia"
        val nombre = Normalizer.normalize(base, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .replace(Regex("[^A-Za-z0-9_\\-]+"), "_") + ".pdf"
        val dir = File(ctx.filesDir, "exportes").apply { mkdirs() }
        val f = File(dir, nombre)
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
