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
        val cob = Calc.cobertura(regs, p)
        val rend = Calc.rendimiento(regs)
        val eta = Calc.eta(regs, p, cob)
        val q = Calc.calidad(regs, p)
        val areaTot = Calc.areaTotal(p)

        // ---------- Resumen ----------
        val res = Hoja("Resumen", listOf(36.0, 40.0))
        res.fila(listOf(c(p.titulo, TITULO), c(null, TITULO)), 30.0); res.combinadas += "A1:B1"
        res.fila(listOf(c(listOf(p.empresa, p.nombre).filter { it.isNotBlank() }.joinToString("  ·  "), SUB))); res.combinadas += "A2:B2"
        res.fila(emptyList())
        fun kv(k: String, v: Any?, est: Int = TEXTO) { res.fila(listOf(c(k, ETIQ), c(v, est))) }
        kv("Abscisa inicial del túnel", Abscisa.texto(p.absIni))
        kv("Abscisa final del túnel", Abscisa.texto(p.absFin))
        kv("Longitud total (m)", p.longitudTotal, DEC2)
        kv("Perímetro de referencia (m)", p.perimetroDefecto, DEC2)
        kv("Área total a cubrir (m²)", areaTot, DEC2)
        kv("Longitud cubierta (m, sin traslapos)", cob.cubierto, DEC2)
        kv("Avance", cob.porcentaje, PCT)
        kv("Longitud pendiente (m)", cob.restante, DEC2)
        kv("Longitud registrada (m, suma de registros)", rend.longitud, DEC2)
        kv("Área instalada (m²)", rend.area, DEC2)
        kv("Horas trabajadas", rend.minutos / 60.0, DEC2)
        kv("Rendimiento (m/h)", rend.mPorHora, DEC2)
        kv("Rendimiento (m²/h)", rend.m2PorHora, DEC2)
        kv("Promedio por turno (últimos ${eta.turnosUsados}) (m)", eta.mPorTurno, DEC2)
        kv("Turnos restantes estimados", eta.turnosRestantes, DEC2)
        kv("Terminación estimada", if (eta.completado) "Completado" else T.fechaHora(eta.fechaHora))
        kv("Pruebas de presión aprobadas / no aprobadas / N.A.", "${q.aprobadas} / ${q.noAprobadas} / ${q.pruebasNA}")
        kv("Generado", T.marcaCorta(T.marca()))

        // ---------- Registros ----------
        val porId = fotos.groupBy { it.registroId }
        val reg = Hoja("Registros", listOf(11.0, 8.0, 18.0, 16.0, 12.0, 12.0, 11.0, 11.0, 11.0, 11.0, 12.0, 9.0, 9.0, 9.0, 9.0, 10.0,
            22.0, 11.0, 11.0, 13.0, 26.0, 8.0, 36.0))
        reg.congelar = 1
        reg.fila(listOf("Fecha", "Turno", "Responsable", "Cuadrilla", "Abscisa inicial", "Abscisa final", "Abs. inicial (m)",
            "Abs. final (m)", "Longitud (m)", "Perímetro (m)", "Área (m²)", "Hora inicio", "Hora fin", "Horas", "m/h", "m²/h",
            "Rollos", "Temp. cuña (°C)", "Vel. soldadura (m/min)", "Prueba de presión", "Geotextil", "Fotos", "Notas").map { c(it, ENC) }, 32.0)
        regs.forEach { r ->
            val f = reg.siguiente
            val h = r.minutos?.takeIf { it > 0 }?.let { it / 60.0 }
            reg.fila(listOf(c(T.serialExcel(r.fecha), FECHA), c(r.turno, CENTRO), c(r.responsable, TEXTO), c(r.cuadrilla, TEXTO),
                c(Abscisa.texto(r.absIni), CENTRO), c(Abscisa.texto(r.absFin), CENTRO), d(r.absIni), d(r.absFin),
                d(r.longitud),
                d(r.perimetro),
                if (r.area != null) c(Formula("I$f*J$f", Math.round(r.area!! * 100) / 100.0), DEC2) else c(null, DEC2),
                hora(r.horaIni), hora(r.horaFin), d(h),
                d(h?.let { r.longitud / it }), d(if (h != null && r.area != null) r.area!! / h else null),
                c(r.rollos.joinToString(", "), TEXTO), d(r.tempCuna, NUM), d(r.velSoldadura),
                c(r.prueba.etiqueta, CENTRO), c(r.geotextil.etiqueta, TEXTO),
                c("${porId[r.id]?.size ?: 0}/4", CENTRO), c(r.notas, TEXTO)))
        }
        val ult = reg.filas.size
        if (regs.isNotEmpty()) {
            reg.fila(List(8) { c(if (it == 0) "TOTAL" else null, TOT_ETIQ) } +
                listOf(c(Formula("SUM(I2:I$ult)", rend.longitud), TOT_NUM), c(null, TOT_NUM),
                    c(Formula("SUM(K2:K$ult)", rend.area), TOT_NUM), c(null, TOT_NUM), c(null, TOT_NUM),
                    c(Formula("SUM(N2:N$ult)", rend.minutos / 60.0), TOT_NUM)))
            reg.combinadas += "A${reg.filas.size}:H${reg.filas.size}"
        }
        reg.filtro = "A1:W${maxOf(2, ult)}"

        // ---------- Por fecha ----------
        val pf = Hoja("Por fecha", listOf(12.0, 9.0, 10.0, 13.0, 13.0, 10.0, 10.0, 10.0, 15.0, 12.0))
        pf.congelar = 1
        pf.fila(listOf("Fecha", "Turnos", "Registros", "Longitud (m)", "Área (m²)", "Horas", "m/h", "m²/h",
            "Acumulado registrado (m)", "Avance acum.").map { c(it, ENC) }, 32.0)
        var acum = 0.0
        Calc.porFecha(regs).forEach { x ->
            val r = x.rend
            acum += r.longitud
            pf.fila(listOf(c(T.serialExcel(x.fecha), FECHA), c(x.turnos.toDouble(), NUM), c(r.registros.toDouble(), NUM),
                d(r.longitud), d(r.area), d(r.minutos / 60.0), d(r.mPorHora), d(r.m2PorHora), d(acum),
                c(Calc.cobertura(regs.filter { it.fecha <= x.fecha }, p).porcentaje, PCT)))
        }

        // ---------- Rollos ----------
        val ro = Hoja("Rollos", listOf(16.0, 11.0, 15.0, 15.0, 12.0, 12.0))
        ro.congelar = 1
        ro.fila(listOf("Rollo", "Registros", "Longitud (m)*", "Área (m²)*", "Primer uso", "Último uso").map { c(it, ENC) }, 32.0)
        Calc.porRollo(regs).forEach { x ->
            ro.fila(listOf(c(x.codigo, TEXTO), c(x.registros.toDouble(), NUM), d(x.longitud), d(x.area),
                c(T.serialExcel(x.primera), FECHA), c(T.serialExcel(x.ultima), FECHA)))
        }
        ro.fila(emptyList())
        ro.fila(listOf(c("* Cuando un registro usa varios rollos, su longitud y área se reparten por igual entre ellos.", SUB)))

        // ---------- Cuadrillas ----------
        val cu = Hoja("Cuadrillas", listOf(24.0, 9.0, 10.0, 13.0, 13.0, 10.0, 10.0, 10.0))
        cu.congelar = 1
        cu.fila(listOf("Cuadrilla / responsable", "Turnos", "Registros", "Longitud (m)", "Área (m²)", "Horas", "m/h", "m²/h").map { c(it, ENC) }, 32.0)
        Calc.porCuadrilla(regs).forEach { x ->
            cu.fila(listOf(c(x.nombre, TEXTO), c(x.turnos.toDouble(), NUM), c(x.registros.toDouble(), NUM), d(x.longitud),
                d(x.area), d(x.minutos / 60.0), d(x.mPorHora), d(x.m2PorHora)))
        }

        // ---------- Pendientes ----------
        val pe = Hoja("Tramos pendientes", listOf(16.0, 16.0, 14.0))
        pe.fila(listOf("Desde", "Hasta", "Longitud (m)").map { c(it, ENC) }, 24.0)
        cob.pendientes.forEach { t -> pe.fila(listOf(c(Abscisa.texto(t.ini), CENTRO), c(Abscisa.texto(t.fin), CENTRO), d(t.longitud))) }

        return escribir(ctx, nombreArchivo("Geomembrana_${p.nombre}_${T.hoy()}"), listOf(res, reg, pf, ro, cu, pe))
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
