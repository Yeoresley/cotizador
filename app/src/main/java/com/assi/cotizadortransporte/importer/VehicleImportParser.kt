package com.assi.cotizadortransporte.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.assi.cotizadortransporte.data.VehicleEntity
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.zip.ZipInputStream

object VehicleImportParser {
    data class Result(
        val vehicles: List<VehicleEntity>,
        val skippedRows: Int,
        val warnings: List<String>
    )

    fun parse(context: Context, uri: Uri): Result {
        val name = displayName(context, uri).lowercase()
        return context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se pudo abrir el fichero." }
            when {
                name.endsWith(".csv") -> parseCsv(input.readBytes().toString(Charsets.UTF_8))
                name.endsWith(".xlsx") -> parseXlsx(input.readBytes())
                else -> throw IllegalArgumentException("Formato no soportado. Utilice .xlsx o .csv")
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "import"
        }
        return uri.lastPathSegment ?: "import"
    }

    private fun parseCsv(text: String): Result {
        val clean = text.removePrefix("\uFEFF")
        val first = clean.lineSequence().firstOrNull { it.isNotBlank() } ?: return Result(emptyList(), 0, listOf("CSV vacío"))
        val delimiter = listOf(';', ',', '\t').maxBy { d -> first.count { it == d } }
        val rows = parseDelimited(clean, delimiter)
        if (rows.isEmpty()) return Result(emptyList(), 0, listOf("CSV vacío"))
        return rowsToVehicles(rows)
    }

    private fun parseDelimited(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == delimiter && !quoted -> { row.add(cell.toString().trim()); cell.clear() }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString().trim()); cell.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        row.add(cell.toString().trim())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    private fun parseXlsx(bytes: ByteArray): Result {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory) entries[e.name] = zip.readBytes()
                e = zip.nextEntry
            }
        }

        val workbookXml = entries["xl/workbook.xml"] ?: error("XLSX inválido: falta workbook.xml")
        val relsXml = entries["xl/_rels/workbook.xml.rels"] ?: error("XLSX inválido: faltan relaciones")
        val sheetRid = findVehicleSheetRid(workbookXml)
        val target = findRelationshipTarget(relsXml, sheetRid)
        val sheetPath = normalizeSheetPath(target)
        val sheetXml = entries[sheetPath] ?: error("No se encontró la hoja Vehiculos")
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val rows = parseSheet(sheetXml, shared)
        return rowsToVehicles(rows)
    }

    private fun findVehicleSheetRid(xml: ByteArray): String {
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(xml), "UTF-8") }
        var fallback: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: ""
                val rid = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                    ?: parser.getAttributeValue(null, "r:id")
                if (fallback == null) fallback = rid
                if (normalize(name) == "vehiculos" && rid != null) return rid
            }
            parser.next()
        }
        return fallback ?: error("XLSX sin hojas")
    }

    private fun findRelationshipTarget(xml: ByteArray, rid: String): String {
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(xml), "UTF-8") }
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                if (parser.getAttributeValue(null, "Id") == rid) {
                    return parser.getAttributeValue(null, "Target") ?: error("Relación de hoja sin destino")
                }
            }
            parser.next()
        }
        error("No se encontró la relación de la hoja Vehiculos")
    }

    private fun normalizeSheetPath(target: String): String {
        val t = target.removePrefix("/")
        return when {
            t.startsWith("xl/") -> t
            t.startsWith("worksheets/") -> "xl/$t"
            else -> "xl/$t"
        }
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(xml), "UTF-8") }
        var inSi = false
        var text = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (parser.name == "si") { inSi = true; text = StringBuilder() }
                XmlPullParser.TEXT -> if (inSi) text.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") { out.add(text.toString()); inSi = false }
            }
            parser.next()
        }
        return out
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val rows = mutableMapOf<Int, MutableMap<Int, String>>()
        val parser = Xml.newPullParser().apply { setInput(ByteArrayInputStream(xml), "UTF-8") }
        var cellRef = ""
        var cellType: String? = null
        var value = ""
        var inlineText = ""
        var inV = false
        var inT = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "c" -> { cellRef = parser.getAttributeValue(null, "r") ?: ""; cellType = parser.getAttributeValue(null, "t"); value = ""; inlineText = "" }
                    "v" -> inV = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> {
                    if (inV) value += parser.text
                    if (inT) inlineText += parser.text
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        if (cellRef.isNotBlank()) {
                            val (row, col) = decodeCellRef(cellRef)
                            val raw = if (cellType == "inlineStr") inlineText else value
                            val resolved = if (cellType == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                            rows.getOrPut(row) { mutableMapOf() }[col] = resolved
                        }
                    }
                }
            }
            parser.next()
        }
        if (rows.isEmpty()) return emptyList()
        val maxCol = rows.values.flatMap { it.keys }.maxOrNull() ?: 0
        return rows.toSortedMap().values.map { r -> (0..maxCol).map { c -> r[c].orEmpty() } }
    }

    private fun decodeCellRef(ref: String): Pair<Int, Int> {
        val letters = ref.takeWhile { it.isLetter() }
        val number = ref.dropWhile { it.isLetter() }.toIntOrNull() ?: 1
        var col = 0
        letters.uppercase().forEach { col = col * 26 + (it - 'A' + 1) }
        return (number - 1) to (col - 1)
    }

    private fun rowsToVehicles(rows: List<List<String>>): Result {
        val headerIndex = rows.indexOfFirst { row ->
            val n = row.map(::normalize)
            n.contains("id") && n.any { it.contains("vehiculo") && it.contains("configuracion") }
        }
        require(headerIndex >= 0) { "No se reconoció la fila de encabezados del catálogo de vehículos." }
        val headers = rows[headerIndex].map(::normalize)

        fun idx(vararg aliases: String): Int = headers.indexOfFirst { h -> aliases.any { a -> h == normalize(a) } }
        val idI = idx("ID")
        val nameI = idx("Vehículo / configuración", "Vehiculo configuracion")
        val serviceI = idx("Tipo de servicio")
        val vehicleValueI = idx("Valor vehículo (USD)", "Valor vehiculo USD")
        val equipValueI = idx("Valor equipo / remolque (USD)", "Valor equipo remolque USD")
        val totalI = idx("Valor total AFT (USD)", "Valor total AFT USD")
        val fuelI = idx("Índice consumo (km/L)", "Indice consumo km L")
        val notesI = idx("Observaciones")
        require(idI >= 0 && nameI >= 0 && vehicleValueI >= 0 && fuelI >= 0) {
            "Faltan columnas obligatorias: ID, Vehículo/configuración, Valor vehículo e Índice consumo."
        }

        val items = mutableListOf<VehicleEntity>()
        var skipped = 0
        val warnings = mutableListOf<String>()
        rows.drop(headerIndex + 1).forEachIndexed { offset, row ->
            fun s(i: Int) = if (i >= 0) row.getOrNull(i).orEmpty().trim() else ""
            val id = s(idI)
            if (id.isBlank()) return@forEachIndexed
            val name = s(nameI)
            val vehicleValue = number(s(vehicleValueI))
            val equipment = number(s(equipValueI))
            val totalProvided = number(s(totalI))
            val fuel = number(s(fuelI))
            if (name.isBlank() || vehicleValue == null || fuel == null || fuel <= 0.0) {
                skipped++
                warnings.add("Fila ${headerIndex + offset + 2}: datos obligatorios incompletos")
                return@forEachIndexed
            }
            val equip = equipment ?: 0.0
            val total = totalProvided?.takeIf { it > 0.0 } ?: (vehicleValue + equip)
            items.add(
                VehicleEntity(
                    id = id,
                    name = name,
                    serviceType = s(serviceI),
                    vehicleValueUsd = vehicleValue,
                    equipmentValueUsd = equip,
                    totalAftUsd = total,
                    fuelKmPerLiter = fuel,
                    notes = s(notesI)
                )
            )
        }
        return Result(items.distinctBy { it.id }, skipped, warnings.take(10))
    }

    private fun number(text: String): Double? {
        if (text.isBlank()) return null
        val cleaned = text.replace("$", "").replace("USD", "", ignoreCase = true).trim()
        val normalized = when {
            cleaned.contains(',') && cleaned.contains('.') -> {
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.'))
                    cleaned.replace(".", "").replace(',', '.')
                else
                    cleaned.replace(",", "")
            }
            cleaned.contains(',') -> cleaned.replace(',', '.')
            else -> cleaned
        }
        return normalized.toDoubleOrNull()
    }

    private fun normalize(value: String): String {
        val n = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        return n.replace("\\p{M}+".toRegex(), "").replace("[^a-z0-9]+".toRegex(), "")
    }
}
