package co.obra.geomembrana

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Genera el Excel (.xlsx) de avance directamente en el teléfono, sin conexión ni librerías externas. */
object Excel {
    // Índices de estilos (cellXfs en styles.xml)
    private const val NORMAL = 0
    private const val TITULO = 1
    private const val ENC = 2
    private const val ETIQ = 3
    private const val TEXTO = 4
    private const val DEC2 = 5
    private const val NUM = 7
    private const val TOT_ETIQ = 8
    private const val TOT_NUM = 9
    private const val CENTRO = 10
    private const val HORA = 11
    private const val FECHA = 12
    private const val SUB = 13
    private const val PCT = 15

    /** Fórmula con su resultado ya calculado (para visores que no recalculan). */
    private class Formula(val expr: String, val valor: Double)

    private class Celda(val valor: Any?, val estilo: Int)

    private class Hoja(val nombre: String, val anchos: List<Double>) {
        val filas = mutableListOf<Pair<Double?, List<Celda>>>()
        val combinadas = mutableListOf<String>()
        var filtro: String? = null
        var congelar = 0
        fun fila(celdas: List<Celda>, alto: Double? = null) { filas.add(alto to celdas) }
        val siguiente get() = filas.size + 1
    }

    private fun c(v: Any?, s: Int) = Celda(v, s)

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun esc(s: String) = s.replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun hora(t: String): Celda = c(Horas.aMin(t)?.let { it / 1440.0 }, HORA)
    private fun d(v: Double?, estilo: Int = DEC2): Celda = c(v?.let { Math.round(it * 100) / 100.0 }, estilo)

    // ================= Libro de avance =================

    fun avance(ctx: Context, registros: List<Registro>, fotos: List<Foto>, p: Proyecto): File {
        val regs = Calc.ordenar(registros)
        val secs = Calc.porSeccion(regs, p)
        val grupos = Calc.porGrupo(secs)
        val rend = Calc.rendimiento(regs)

        // ---------- Resumen: global, por grupo y por sección ----------
        val res = Hoja("Resumen", listOf(24.0, 14.0, 12.0, 14.0, 12.0, 14.0, 14.0, 12.0, 12.0, 20.0))
        res.fila(listOf(c(p.titulo, TITULO)) + List(9) { c(null, TITULO) }, 30.0); res.combinadas += "A1:J1"
        res.fila(listOf(c(listOf(p.empresa, p.nombre, "${Abscisa.texto(p.absIni)} a ${Abscisa.texto(p.absFin)} (${Fmt.num(p.longitudTotal)} m)",
            "Generado ${T.marcaCorta(T.marca())}").filter { it.isNotBlank() }.joinToString("  ·  "), SUB))); res.combinadas += "A2:J2"
        res.fila(emptyList())
        res.fila(listOf("Grupo / sección", "Perímetro (m)", "Estado", "Cubierto (m)", "Avance", "Pendiente (m)", "Área instalada (m²)",
            "Área total (m²)", "m/h", "Terminación estimada").map { c(it, ENC) }, 32.0)
        fun eta(f: java.time.LocalDateTime?, terminada: Boolean) = if (terminada) "Terminada" else f?.let { T.fechaHora(it) } ?: "–"
        grupos.forEach { g ->
            res.fila(listOf(c(g.nombre, TOT_ETIQ), c(null, TOT_NUM), c(if (g.terminada) "Terminada" else "En curso", TOT_ETIQ), c(null, TOT_NUM),
                c(g.porcentaje, PCT), d(g.pendiente, TOT_NUM), d(g.rend.area, TOT_NUM), d(g.areaTotal, TOT_NUM), d(g.rend.mPorHora, TOT_NUM),
                c(eta(g.etaFecha, g.terminada), TOT_ETIQ)))
            g.secciones.forEach { s ->
                res.fila(listOf(c("   " + s.seccion.nombre, TEXTO), d(s.seccion.perimetro), c(if (s.terminada) "Terminada" else "En curso", CENTRO),
                    d(s.cobertura.cubierto), c(s.porcentaje, PCT), d(if (s.seccion.terminada) 0.0 else s.cobertura.restante),
                    d(s.rend.area), d(s.areaTotal), d(s.rend.mPorHora), c(eta(s.eta.fechaHora, s.terminada), CENTRO)))
            }
        }
        res.fila(listOf(c("TOTAL PROYECTO", TOT_ETIQ), c(null, TOT_NUM), c(null, TOT_ETIQ), c(null, TOT_NUM), c(Calc.avanceGlobal(secs), PCT),
            c(null, TOT_NUM), d(rend.area, TOT_NUM), d(Calc.areaTotal(p), TOT_NUM), d(rend.mPorHora, TOT_NUM), c(null, TOT_ETIQ)))
        res.fila(emptyList())
        res.fila(listOf(c("Avance por área: perímetro × metros cubiertos (sin traslapos) frente a perímetro × longitud del túnel. " +
            "Las secciones terminadas cuentan al 100 %.", SUB)))

        // ---------- Registros ----------
        val porDia = fotos.groupBy { it.jornada }
        val reg = Hoja("Registros", listOf(11.0, 8.0, 12.0, 18.0, 16.0, 12.0, 12.0, 11.0, 11.0, 11.0, 11.0, 12.0, 9.0, 9.0, 9.0, 9.0, 10.0,
            22.0, 11.0, 11.0, 13.0, 26.0, 36.0))
        reg.congelar = 1
        reg.fila(listOf("Fecha", "Turno", "Sección", "Responsable", "Cuadrilla", "PK inicial", "PK final", "Abs. inicial (m)",
            "Abs. final (m)", "Longitud (m)", "Perímetro (m)", "Cantidad (m²)", "Hora inicio", "Hora fin", "Horas", "m/h", "m²/h",
            "Rollos", "Temp. cuña (°C)", "Vel. soldadura (m/min)", "Prueba de presión", "Geotextil", "Notas").map { c(it, ENC) }, 32.0)
        regs.forEach { r ->
            val f = reg.siguiente
            val h = r.minutos?.takeIf { it > 0 }?.let { it / 60.0 }
            val area = r.area
            reg.fila(listOf(c(T.serialExcel(r.fecha), FECHA), c(r.turno, CENTRO), c(r.seccion, TEXTO), c(r.responsable, TEXTO), c(r.cuadrilla, TEXTO),
                c(Abscisa.texto(r.absIni), CENTRO), c(Abscisa.texto(r.absFin), CENTRO), d(r.absIni), d(r.absFin),
                d(r.longitud), d(r.perimetro),
                when {
                    area == null -> c(null, DEC2)
                    r.areaManual != null -> d(area)                       // cantidad oficial
                    else -> c(Formula("J$f*K$f", Math.round(area * 100) / 100.0), DEC2)
                },
                hora(r.horaIni), hora(r.horaFin), d(h),
                d(h?.let { r.longitud / it }), d(if (h != null && area != null) area / h else null),
                c(r.rollos.joinToString(", "), TEXTO), d(r.tempCuna, NUM), d(r.velSoldadura),
                c(r.prueba.etiqueta, CENTRO), c(r.geotextil.etiqueta, TEXTO), c(r.notas, TEXTO)))
        }
        val ult = reg.filas.size
        if (regs.isNotEmpty()) {
            reg.fila(List(9) { c(if (it == 0) "TOTAL" else null, TOT_ETIQ) } +
                listOf(c(Formula("SUM(J2:J$ult)", rend.longitud), TOT_NUM), c(null, TOT_NUM),
                    c(Formula("SUM(L2:L$ult)", rend.area), TOT_NUM), c(null, TOT_NUM), c(null, TOT_NUM),
                    c(Formula("SUM(O2:O$ult)", rend.minutos / 60.0), TOT_NUM)))
            reg.combinadas += "A${reg.filas.size}:I${reg.filas.size}"
        }
        reg.filtro = "A1:W${maxOf(2, ult)}"

        // ---------- Por fecha, separado por grupo ----------
        val nombres = grupos.map { it.nombre }
        val pf = Hoja("Por fecha", listOf(12.0) + nombres.flatMap { listOf(12.0, 13.0) } + listOf(13.0, 8.0))
        pf.congelar = 1
        pf.fila(listOf(c("Fecha", ENC)) + nombres.flatMap { listOf(c("$it (m)", ENC), c("$it (m²)", ENC)) } +
            listOf(c("Total (m²)", ENC), c("Fotos", ENC)), 32.0)
        Calc.porFecha(regs).forEach { x ->
            val m = Calc.metricas(x.registros, p).associateBy { it.nombre }
            pf.fila(listOf(c(T.serialExcel(x.fecha), FECHA)) +
                nombres.flatMap { n -> listOf(d(m[n]?.total?.longitud), d(m[n]?.total?.area)) } +
                listOf(d(x.rend.area, DEC2), c("${porDia[x.fecha]?.size ?: 0}/4", CENTRO)))
        }

        // ---------- Curva S (secciones en curso) ----------
        val hojas = mutableListOf(res, reg, pf)
        secs.filter { !it.seccion.terminada }.forEach { s ->
            val cs = Calc.curvaS(regs, s.seccion, p)
            if (cs.real.isEmpty()) return@forEach
            val h = Hoja("Curva S ${s.seccion.nombre}".take(31), listOf(12.0, 14.0, 10.0, 16.0, 16.0, 10.0))
            h.congelar = 1
            h.fila(listOf("Fecha", "Real acumulado (m)", "Real (%)", "Proyectado reciente (m)", "Proyectado promedio (m)", "Proy. (%)").map { c(it, ENC) }, 32.0)
            val pr = cs.proyReciente.associateBy { it.fecha }; val pp = cs.proyPromedio.associateBy { it.fecha }
            val re = cs.real.associateBy { it.fecha }
            (cs.real.map { it.fecha } + cs.proyReciente.map { it.fecha } + cs.proyPromedio.map { it.fecha }).distinct().sorted().forEach { f ->
                val vr = re[f]?.metros; val a = pr[f]?.metros; val b = pp[f]?.metros
                h.fila(listOf(c(T.serialExcel(f.toString()), FECHA), d(vr), c(vr?.let { it / cs.total }, PCT), d(a), d(b), c(a?.let { it / cs.total }, PCT)))
            }
            h.fila(emptyList())
            h.fila(listOf(c("Proyección: ${Fmt.num(cs.mPorDiaReciente)} m/día (últimos ${p.turnosParaEta} turnos) y " +
                "${Fmt.num(cs.mPorDiaPromedio)} m/día (todos los turnos), desde el último día registrado.", SUB)))
            hojas += h
        }

        // ---------- Cuadrillas y rollos ----------
        val cu = Hoja("Cuadrillas", listOf(24.0, 9.0, 10.0, 13.0, 13.0, 10.0, 10.0, 10.0))
        cu.congelar = 1
        cu.fila(listOf("Cuadrilla / responsable", "Turnos", "Registros", "Longitud (m)", "Área (m²)", "Horas", "m/h", "m²/h").map { c(it, ENC) }, 32.0)
        Calc.porCuadrilla(regs).forEach { x ->
            cu.fila(listOf(c(x.nombre, TEXTO), c(x.turnos.toDouble(), NUM), c(x.registros.toDouble(), NUM), d(x.longitud),
                d(x.area), d(x.minutos / 60.0), d(x.mPorHora), d(x.m2PorHora)))
        }
        hojas += cu
        val rollos = Calc.porRollo(regs)
        if (rollos.isNotEmpty()) {
            val ro = Hoja("Rollos", listOf(16.0, 11.0, 15.0, 15.0, 12.0, 12.0))
            ro.congelar = 1
            ro.fila(listOf("Rollo", "Registros", "Longitud (m)*", "Área (m²)*", "Primer uso", "Último uso").map { c(it, ENC) }, 32.0)
            rollos.forEach { x ->
                ro.fila(listOf(c(x.codigo, TEXTO), c(x.registros.toDouble(), NUM), d(x.longitud), d(x.area),
                    c(T.serialExcel(x.primera), FECHA), c(T.serialExcel(x.ultima), FECHA)))
            }
            ro.fila(emptyList())
            ro.fila(listOf(c("* Cuando un registro usa varios rollos, su longitud y área se reparten por igual entre ellos.", SUB)))
            hojas += ro
        }

        return escribir(ctx, nombreArchivo("Geomembrana_${p.nombre}_${T.hoy()}"), hojas)
    }

    // ================= Escritura del archivo =================

    private fun nombreArchivo(base: String) = Normalizer.normalize(base, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^A-Za-z0-9_\\-]+"), "_") + ".xlsx"

    private fun hojaXml(h: Hoja): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        sb.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
        if (h.congelar > 0) {
            sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="${h.congelar}" topLeftCell="A${h.congelar + 1}" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        } else {
            sb.append("""<sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>""")
        }
        sb.append("<cols>")
        h.anchos.forEachIndexed { i, w -> sb.append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        sb.append("</cols><sheetData>")
        h.filas.forEachIndexed { ri, (alto, celdas) ->
            val r = ri + 1
            sb.append("<row r=\"$r\"")
            if (alto != null) sb.append(" ht=\"$alto\" customHeight=\"1\"")
            sb.append(">")
            celdas.forEachIndexed { ci, cel ->
                val ref = col(ci) + r
                when (val v = cel.valor) {
                    null -> sb.append("<c r=\"$ref\" s=\"${cel.estilo}\"/>")
                    is Formula -> sb.append("<c r=\"$ref\" s=\"${cel.estilo}\"><f>${esc(v.expr)}</f><v>${v.valor}</v></c>")
                    is Number -> sb.append("<c r=\"$ref\" s=\"${cel.estilo}\"><v>${v.toDouble()}</v></c>")
                    else -> {
                        val s = v.toString()
                        if (s.isEmpty()) sb.append("<c r=\"$ref\" s=\"${cel.estilo}\"/>")
                        else sb.append("<c r=\"$ref\" s=\"${cel.estilo}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(s)}</t></is></c>")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        h.filtro?.let { sb.append("<autoFilter ref=\"$it\"/>") }
        if (h.combinadas.isNotEmpty()) {
            sb.append("<mergeCells count=\"${h.combinadas.size}\">")
            h.combinadas.forEach { sb.append("<mergeCell ref=\"$it\"/>") }
            sb.append("</mergeCells>")
        }
        sb.append("""<pageMargins left="0.4" right="0.4" top="0.5" bottom="0.5" header="0.3" footer="0.3"/>""")
        sb.append("""<pageSetup paperSize="1" orientation="landscape" fitToWidth="1" fitToHeight="0"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private const val ESTILOS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<numFmts count="2"><numFmt numFmtId="164" formatCode="# ?/?"/><numFmt numFmtId="165" formatCode="dd/mm/yyyy"/></numFmts>
<fonts count="5">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="14"/><color rgb="FF002F5C"/><name val="Calibri"/></font>
<font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
<font><b/><sz val="11"/><color rgb="FF002F5C"/><name val="Calibri"/></font>
<font><sz val="10"/><color rgb="FF38628E"/><name val="Calibri"/></font>
</fonts>
<fills count="4">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FF002F5C"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFDEE8F6"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="2">
<border><left/><right/><top/><bottom/><diagonal/></border>
<border><left style="thin"><color rgb="FF46505C"/></left><right style="thin"><color rgb="FF46505C"/></right><top style="thin"><color rgb="FF46505C"/></top><bottom style="thin"><color rgb="FF46505C"/></bottom><diagonal/></border>
</borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="16">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>
<xf numFmtId="2" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
<xf numFmtId="2" fontId="3" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="20" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>
<xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
<xf numFmtId="10" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

    private fun escribir(ctx: Context, nombre: String, hojas: List<Hoja>): File {
        val dir = File(ctx.filesDir, "exportes").apply { mkdirs() }
        val f = File(dir, nombre)
        ZipOutputStream(FileOutputStream(f)).use { z ->
            fun put(ruta: String, contenido: String) {
                z.putNextEntry(ZipEntry(ruta)); z.write(contenido.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
            put("[Content_Types].xml", xml +
                """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
                """<Default Extension="xml" ContentType="application/xml"/>""" +
                """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
                hojas.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } +
                "</Types>")
            put("_rels/.rels", xml +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
                "</Relationships>")
            val definidos = hojas.mapIndexedNotNull { i, h ->
                h.filtro?.let { ref ->
                    val partes = ref.split(":")
                    val abs = partes.joinToString(":") { p -> "$" + p.takeWhile { it.isLetter() } + "$" + p.dropWhile { it.isLetter() } }
                    """<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">'${esc(h.nombre)}'!$abs</definedName>"""
                }
            }
            put("xl/workbook.xml", xml +
                """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                hojas.mapIndexed { i, h -> """<sheet name="${esc(h.nombre)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") +
                "</sheets>" +
                (if (definidos.isNotEmpty()) "<definedNames>" + definidos.joinToString("") + "</definedNames>" else "") +
                """<calcPr calcId="0" fullCalcOnLoad="1"/></workbook>""")
            put("xl/_rels/workbook.xml.rels", xml +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                hojas.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                """<Relationship Id="rId${hojas.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
                "</Relationships>")
            put("xl/styles.xml", ESTILOS)
            hojas.forEachIndexed { i, h -> put("xl/worksheets/sheet${i + 1}.xml", hojaXml(h)) }
        }
        return f
    }
}
