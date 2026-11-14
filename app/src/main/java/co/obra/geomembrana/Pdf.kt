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
 *    avance acumulado, curva S y anexo con las 4 fotos de la jornada.
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

    /** Franjas del túnel por sección: instalado (amarillo), lo de la jornada (azul) y lo pendiente (gris). */
    private fun franjas(h: Hojas, p: Proyecto, secs: List<AvanceSeccion>, delDia: List<Registro>) {
        val etiq = 70f
        h.espacio(30f + secs.size * 16f)
        h.y += 8f
        val c = h.c
        val x0 = M + etiq; val w = ancho - etiq; val alto = 11f
        fun xDe(a: Double) = x0 + ((a - p.absIni) / p.longitudTotal * w).toFloat().coerceIn(0f, w)
        val fe = tp(7f, true, AZUL)
        secs.forEach { s ->
            val y0 = h.y
            c.drawText(s.seccion.nombre + if (s.seccion.terminada) " ✓" else "", M, y0 + 8.5f, fe)
            relleno.color = PEND; c.drawRect(x0, y0, x0 + w, y0 + alto, relleno)
            relleno.color = GEO
            s.cobertura.tramos.forEach { t -> c.drawRect(xDe(t.ini), y0, xDe(t.fin).coerceAtLeast(xDe(t.ini) + 0.8f), y0 + alto, relleno) }
            relleno.color = GEO_DIA
            Calc.cobertura(Calc.deSeccion(delDia, s.seccion.nombre), p).tramos.forEach { t ->
                c.drawRect(xDe(t.ini), y0, xDe(t.fin).coerceAtLeast(xDe(t.ini) + 1.2f), y0 + alto, relleno)
            }
            c.drawRect(x0, y0, x0 + w, y0 + alto, borde)
            val pct = Fmt.pct(s.porcentaje)
            h.y += alto + 4f
            c.drawText(pct, x0 + w - tp(6.5f).measureText(pct) - 2f, y0 + 8.3f, tp(6.5f, true))
        }
        // Marcas cada 100 m
        val f = tp(6.3f, color = GRIS)
        var a = Math.ceil(p.absIni / 100.0) * 100.0
        while (a <= p.absFin + 1e-6) {
            val x = xDe(a)
            c.drawLine(x, h.y - 4f, x, h.y - 1f, borde)
            val t = Abscisa.texto(a).substringBefore('.')
            val tw = f.measureText(t)
            c.drawText(t, (x - tw / 2).coerceIn(x0, x0 + w - tw), h.y + 6f, f)
            a += 100.0
        }
        val ly = h.y + 16f
        var lx = x0
        listOf(GEO to "Instalado", GEO_DIA to "Instalado en la jornada", PEND to "Pendiente").forEach { (col, et) ->
            if (et.endsWith("jornada") && delDia.isEmpty()) return@forEach
            relleno.color = col; c.drawRect(lx, ly - 6f, lx + 10f, ly + 1f, relleno); c.drawRect(lx, ly - 6f, lx + 10f, ly + 1f, borde)
            c.drawText(et, lx + 13f, ly, f); lx += 22f + f.measureText(et)
        }
        h.y = ly + 4f
    }

    /** Curva S: avance real (azul) vs proyectado según rendimientos (amarillo: últimos turnos; gris: promedio). */
    private fun curvaS(h: Hojas, cs: CurvaS, p: Proyecto) {
        if (cs.real.isEmpty()) return
        val alto = 190f
        h.espacio(alto + 46f)
        val c = h.c
        val izq = M + 34f; val der = M + ancho - 8f; val arr = h.y + 6f; val aba = h.y + alto
        val todos = cs.real + cs.proyReciente + cs.proyPromedio
        val x0 = todos.minOf { it.fecha.toEpochDay() }.toDouble()
        val x1 = maxOf(todos.maxOf { it.fecha.toEpochDay() }.toDouble(), x0 + 1)
        fun px(d: java.time.LocalDate) = (izq + (d.toEpochDay() - x0) / (x1 - x0) * (der - izq)).toFloat()
        fun py(m: Double) = (aba - (m / cs.total).coerceIn(0.0, 1.0) * (aba - arr)).toFloat()
        val rej = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.4f; color = Color.rgb(200, 206, 214) }
        val f = tp(6.5f, color = GRIS)
        for (k in 0..4) {
            val y = py(cs.total * k / 4.0)
            c.drawLine(izq, y, der, y, rej)
            val t = "${k * 25} %"
            c.drawText(t, izq - 4f - f.measureText(t), y + 2.5f, f)
        }
        // Marcas mensuales
        var mes = java.time.LocalDate.ofEpochDay(x0.toLong()).withDayOfMonth(1).plusMonths(1)
        while (mes.toEpochDay() <= x1) {
            val x = px(mes)
            c.drawLine(x, arr, x, aba, rej)
            val t = mes.format(DateTimeFormatter.ofPattern("MMM yyyy", Fmt.ES))
            c.drawText(t, x - f.measureText(t) / 2, aba + 9f, f)
            mes = mes.plusMonths(1)
        }
        c.drawLine(izq, arr, izq, aba, borde); c.drawLine(izq, aba, der, aba, borde)
        fun linea(pts: List<PuntoCurva>, color: Int, ancho: Float, punteada: Boolean) {
            if (pts.size < 2) return
            val pa = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = ancho; this.color = color
                if (punteada) pathEffect = android.graphics.DashPathEffect(floatArrayOf(5f, 3f), 0f)
            }
            val path = android.graphics.Path()
            pts.forEachIndexed { i, q -> if (i == 0) path.moveTo(px(q.fecha), py(q.metros)) else path.lineTo(px(q.fecha), py(q.metros)) }
            c.drawPath(path, pa)
        }
        linea(cs.proyPromedio, GRIS, 1.2f, true)
        linea(cs.proyReciente, GEO, 1.8f, true)
        linea(cs.real, AZUL, 2f, false)
        val ult = cs.real.last()
        relleno.color = AZUL; c.drawCircle(px(ult.fecha), py(ult.metros), 2.6f, relleno)
        val fu = tp(7f, true, AZUL)
        c.drawText("${Fmt.pct(ult.metros / cs.total)} al ${T.fechaCorta(ult.fecha.toString())}", px(ult.fecha) - 40f, py(ult.metros) - 6f, fu)
        cs.proyReciente.lastOrNull()?.let { q ->
            c.drawText("100 % ≈ ${T.fechaCorta(q.fecha.toString())}", (px(q.fecha) - 62f).coerceAtLeast(izq), py(q.metros) - 4f, tp(7f, true, Color.rgb(138, 100, 0)))
        }
        h.y = aba + 14f
        val ley = tp(7f)
        var lx = izq
        fun item(col: Int, t: String, punt: Boolean) {
            val pa = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = col
                if (punt) pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 2f), 0f) }
            c.drawLine(lx, h.y - 2.5f, lx + 16f, h.y - 2.5f, pa)
            c.drawText(t, lx + 20f, h.y, ley); lx += 30f + ley.measureText(t)
        }
        item(AZUL, "Avance real acumulado", false)
        if (cs.proyReciente.isNotEmpty()) item(GEO, "Proyectado últimos ${p.turnosParaEta} turnos (${Fmt.num(cs.mPorDiaReciente)} m/día) → ${T.fechaCorta(cs.proyReciente.last().fecha.toString())}", true)
        if (cs.proyPromedio.isNotEmpty()) item(GRIS, "Proyectado promedio (${Fmt.num(cs.mPorDiaPromedio)} m/día) → ${T.fechaCorta(cs.proyPromedio.last().fecha.toString())}", true)
        h.y += 6f
    }

    // ---------- Tabla de registros ----------

    private val COLS = listOf(
        "Fecha" to 40f, "Sección" to 44f, "Abscisa\ninicial" to 54f, "Abscisa\nfinal" to 54f, "Longitud\n(m)" to 42f,
        "Perímetro\n(m)" to 42f, "Área\n(m²)" to 48f, "Horario" to 56f, "Horas" to 32f, "m/h" to 32f, "m²/h" to 38f,
        "Rollos" to 120f
    )

    private fun tablaRegistros(h: Hojas, regs: List<Registro>, p: Proyecto) {
        val enc = tp(6.8f, true, Color.WHITE); val f = tp(7.2f)
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
            val vals = listOf(
                T.fechaCorta(r.fecha).take(5), r.seccion, Abscisa.texto(r.absIni), Abscisa.texto(r.absFin),
                Fmt.fijo(r.longitud) + if (r.longitudManual != null) "*" else "", Fmt.num(r.perimetro),
                Fmt.fijo(r.area) + if (r.areaManual != null) "°" else "",
                if (r.horaIni.isNotBlank() || r.horaFin.isNotBlank()) "${r.horaIni}–${r.horaFin}" else "",
                Fmt.num(hrs), Fmt.num(hrs?.let { r.longitud / it }), Fmt.num(if (hrs != null && r.area != null) r.area!! / hrs else null, 1),
                r.rollos.joinToString(", ")
            )
            val hFila = maxOf(15f, alto(vals[11], f, ws[11]))
            h.espacio(hFila)
            var x = M
            vals.forEachIndexed { i, v ->
                celda(h.c, x, h.y, ws[i], hFila, v.ifBlank { if (i in 4..10) "–" else "" }, f,
                    if (i == 11) NORMAL else CENTRO)
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
        val notasTabla = listOfNotNull(
            if (regs.any { it.longitudManual != null }) "* Longitud ajustada a mano." else null,
            if (regs.any { it.areaManual != null }) "° Cantidad oficial en m² (difiere de longitud × perímetro por redondeo)." else null
        )
        if (notasTabla.isNotEmpty()) {
            h.espacio(12f)
            h.y += 2f + texto(h.c, M, h.y + 2f, ancho, notasTabla.joinToString("   "), tp(6.8f, color = GRIS))
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
        val porDia = fotos.groupBy { it.jornada }
        Calc.porFecha(regs).filter { porDia[it.fecha]?.isNotEmpty() == true }.forEach { d ->
            h.nueva()
            encabezado(h, p, logo, "Anexo fotográfico")
            h.y += 6f
            val resumenDia = Calc.metricas(d.registros, p).joinToString("  ·  ") { g ->
                "${g.nombre}: ${Fmt.fijo(g.total.longitud)} m, ${Fmt.fijo(g.total.area)} m²"
            }
            celda(h.c, M, h.y, ancho, 18f, "${T.fechaLarga(d.fecha)}  ·  $resumenDia", tp(8.5f, true, AZUL), fondo = CLARO)
            h.y += 22f
            val mapa = porDia[d.fecha].orEmpty().associateBy { it.momento }
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

    /** Tabla de avance global: un renglón por grupo (en negrilla) y uno por cada sección. */
    private fun tablaAvance(h: Hojas, grupos: List<AvanceGrupo>, secs: List<AvanceSeccion>) {
        val tit = listOf("Grupo / sección", "Perímetro (m)", "Cubierto (m)", "Avance", "Pendiente (m)", "Área instalada (m²)", "Área total (m²)", "Terminación estimada")
        val rel = listOf(2.2f, 1f, 1.1f, 0.9f, 1.1f, 1.4f, 1.3f, 2f)
        val ws = rel.map { it / rel.sum() * ancho }
        val enc = tp(7f, true, Color.WHITE)
        h.espacio(40f)
        var x = M
        tit.forEachIndexed { i, t -> celda(h.c, x, h.y, ws[i], 18f, t, enc, CENTRO, fondo = AZUL); x += ws[i] }
        h.y += 18f
        fun fila(v: List<String>, negrilla: Boolean) {
            h.espacio(14f)
            val f = tp(7.6f, negrilla, if (negrilla) AZUL else TINTA)
            var xx = M
            v.forEachIndexed { i, t -> celda(h.c, xx, h.y, ws[i], 14f, t, f, if (i == 0) NORMAL else CENTRO, fondo = if (negrilla) CLARO else null); xx += ws[i] }
            h.y += 14f
        }
        fun eta(f: LocalDateTime?, terminada: Boolean) = if (terminada) "Terminada" else f?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) ?: "–"
        grupos.forEach { g ->
            fila(listOf(g.nombre, "", "", Fmt.pct(g.porcentaje), if (g.terminada) "–" else Fmt.fijo(g.pendiente), Fmt.fijo(g.rend.area),
                Fmt.fijo(g.areaTotal), eta(g.etaFecha, g.terminada)), true)
            if (g.secciones.size > 1 || g.secciones.first().seccion.nombre != g.nombre) g.secciones.forEach { s ->
                fila(listOf("   " + s.seccion.nombre, Fmt.num(s.seccion.perimetro), Fmt.fijo(s.cobertura.cubierto), Fmt.pct(s.porcentaje),
                    if (s.seccion.terminada) "–" else Fmt.fijo(s.cobertura.restante), Fmt.fijo(s.rend.area), Fmt.fijo(s.areaTotal),
                    eta(s.eta.fechaHora, s.terminada)), false)
            }
        }
        fila(listOf("TOTAL PROYECTO", "", "", Fmt.pct(Calc.avanceGlobal(secs)), "", Fmt.fijo(secs.sumOf { it.rend.area }),
            Fmt.fijo(secs.sumOf { it.areaTotal }), ""), true)
        h.espacio(12f)
        h.y += 2f + texto(h.c, M, h.y + 2f, ancho, "Avance por área (perímetro × metros cubiertos, sin contar traslapos). Las secciones terminadas cuentan al 100 %.",
            tp(6.8f, color = GRIS))
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
        val secs = Calc.porSeccion(acumulado, p)
        val grupos = Calc.porGrupo(secs).sortedBy { it.terminada }

        encabezado(h, p, logo, if (dia == null) "Informe de avance acumulado" else "Reporte diario de instalación")
        filaInfo(h, listOf(
            "Proyecto:" to p.nombre,
            "Abscisas del túnel:" to "${Abscisa.texto(p.absIni)} – ${Abscisa.texto(p.absFin)}",
            "Longitud:" to "${Fmt.fijo(p.longitudTotal)} m",
            "Secciones:" to p.secciones.joinToString(", ") { "${it.nombre} ${Fmt.num(it.perimetro)} m" }
        ), listOf(46f, 110f, 82f, 130f, 44f, 70f, 50f, 204f).let { l -> val s = l.sum(); l.map { it / s * ancho } })
        filaInfo(h, listOf(
            (if (dia == null) "Corte al:" else "Fecha:") to (if (dia == null) T.fechaLarga(acumulado.lastOrNull()?.fecha ?: T.hoy()) else T.fechaLarga(dia)),
            "Turnos:" to alcance.map { it.turno }.distinct().joinToString(" y ").ifBlank { "–" },
            "Responsable(s):" to alcance.map { it.responsable }.filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { "–" }
        ), listOf(50f, 210f, 44f, 80f, 76f, 276f).let { l -> val s = l.sum(); l.map { it / s * ancho } })
        h.y += 6f

        if (dia != null) {
            titulo(h, "Métricas de la jornada")
            val mets = Calc.metricas(alcance, p)
            if (mets.isEmpty()) {
                h.y += 2f + texto(h.c, M, h.y + 4f, ancho, "Sin registros en esta fecha.", tp(8f))
            }
            mets.forEach { g ->
                h.espacio(60f)
                h.y += 4f
                val r = g.total
                kpis(h, listOf(
                    Triple(g.nombre, "${Fmt.fijo(r.longitud)} m", "${r.registros} registro(s)"),
                    Triple("Área cubierta", "${Fmt.fijo(r.area)} m²", "longitud × perímetro"),
                    Triple("Horas trabajadas", if (r.minutos > 0) Fmt.duracion(r.minutos.toDouble()) else "–", ""),
                    Triple("Rendimiento", if (r.mPorHora != null) "${Fmt.num(r.mPorHora)} m/h" else "–",
                        if (r.m2PorHora != null) "${Fmt.num(r.m2PorHora, 1)} m²/h" else "registrar horario")
                ), alto = 40f, fondo = Color.rgb(255, 246, 214))
                if (g.porSeccion.size > 1 || g.porSeccion.first().first.nombre != g.nombre) {
                    val det = g.porSeccion.joinToString("   ·   ") { (s, rr) -> "${s.nombre}: ${Fmt.fijo(rr.longitud)} m × ${Fmt.num(s.perimetro)} m = ${Fmt.fijo(rr.area)} m²" }
                    h.y += 2f + texto(h.c, M + 4f, h.y + 3f, ancho - 8f, det, tp(7.5f))
                }
            }
        }

        titulo(h, if (dia == null) "Avance global por grupo y sección" else "Avance acumulado al ${T.fechaCorta(dia)}")
        tablaAvance(h, grupos, secs)
        franjas(h, p, secs, if (dia == null) emptyList() else alcance)

        secs.filter { !it.seccion.terminada && it.registros.isNotEmpty() }.forEach { s ->
            titulo(h, "Curva S de avance · ${s.seccion.nombre} (real vs proyectado según rendimientos)")
            curvaS(h, Calc.curvaS(acumulado, s.seccion, p), p)
        }

        titulo(h, if (dia == null) "Registros de instalación" else "Registros de la jornada")
        tablaRegistros(h, alcance, p)
        notas(h, alcance)

        if (dia == null) {
            titulo(h, "Avance por fecha")
            val nombres = grupos.map { it.nombre }
            tablaSimple(h, listOf("Fecha") + nombres.flatMap { listOf("$it (m)", "$it (m²)") } + "Total (m²)",
                listOf(1.4f) + nombres.flatMap { listOf(1f, 1.2f) } + 1.2f,
                Calc.porFecha(acumulado).map { x ->
                    val m = Calc.metricas(x.registros, p).associateBy { it.nombre }
                    listOf(T.fechaCorta(x.fecha)) + nombres.flatMap { n ->
                        listOf(m[n]?.let { Fmt.fijo(it.total.longitud) } ?: "", m[n]?.let { Fmt.fijo(it.total.area) } ?: "")
                    } + Fmt.fijo(x.rend.area)
                })
            val rollos = Calc.porRollo(acumulado)
            if (rollos.isNotEmpty()) {
                titulo(h, "Desglose por rollo (longitud y área repartidas entre los rollos de cada registro)")
                tablaSimple(h, listOf("Rollo", "Registros", "Longitud (m)", "Área (m²)", "Primer uso", "Último uso"),
                    listOf(2f, 1f, 1.3f, 1.3f, 1.3f, 1.3f),
                    rollos.map { listOf(it.codigo, it.registros.toString(), Fmt.fijo(it.longitud), Fmt.fijo(it.area),
                        T.fechaCorta(it.primera), T.fechaCorta(it.ultima)) })
            }
            val cuad = Calc.porCuadrilla(acumulado).filter { it.nombre != "Sin asignar" }
            if (cuad.isNotEmpty()) {
                titulo(h, "Rendimiento por cuadrilla")
                tablaSimple(h, listOf("Cuadrilla / responsable", "Turnos", "Longitud (m)", "Área (m²)", "Horas", "m/h", "m²/h"),
                    listOf(3f, 1f, 1.3f, 1.3f, 1f, 1f, 1f),
                    cuad.map { listOf(it.nombre, it.turnos.toString(), Fmt.fijo(it.longitud), Fmt.fijo(it.area),
                        Fmt.num(it.minutos / 60.0), Fmt.num(it.mPorHora), Fmt.num(it.m2PorHora, 1)) })
            }
            val pend = secs.filter { !it.seccion.terminada }.flatMap { s -> s.cobertura.pendientes.map { s.seccion.nombre to it } }
            if (pend.isNotEmpty()) {
                titulo(h, "Tramos pendientes por instalar")
                tablaSimple(h, listOf("Sección", "Desde", "Hasta", "Longitud (m)"), listOf(1f, 1f, 1f, 1f),
                    pend.map { (n, t) -> listOf(n, Abscisa.texto(t.ini), Abscisa.texto(t.fin), Fmt.fijo(t.longitud)) })
            }
        }

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
