package com.assi.cotizadortransporte.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

object QuoteShareUtil {
    data class Presentation(
        val client: String,
        val service: String,
        val origin: String,
        val destination: String,
        val tripType: String,
        val totalKm: Double,
        val vehicle: String,
        val days: Int,
        val offerPriceUsd: Double,
        val pricePerKmUsd: Double
    )

    private val usd = NumberFormat.getCurrencyInstance(Locale.US)

    fun shareImage(context: Context, p: Presentation) {
        val bitmap = Bitmap.createBitmap(1200, 1500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawSummary(canvas, p, 1200, 1500)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "oferta_transporte.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shareFile(context, file, "image/png")
    }

    fun sharePdf(context: Context, p: Presentation) {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(1200, 1500, 1).create()
        val page = pdf.startPage(pageInfo)
        drawSummary(page.canvas, p, 1200, 1500)
        pdf.finishPage(page)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "oferta_transporte.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        shareFile(context, file, "application/pdf")
    }

    private fun drawSummary(canvas: Canvas, p: Presentation, width: Int, height: Int) {
        canvas.drawColor(Color.WHITE)
        val navy = Color.rgb(23, 54, 93)
        val green = Color.rgb(112, 173, 71)
        val text = Color.rgb(30, 30, 30)
        val muted = Color.rgb(95, 95, 95)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL) }
        paint.color = navy
        canvas.drawRect(0f, 0f, width.toFloat(), 150f, paint)
        paint.color = Color.WHITE
        paint.textSize = 54f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText("OFERTA DE SERVICIO", 70f, 95f, paint)

        var y = 235f
        fun line(label: String, value: String) {
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = 34f
            paint.color = muted
            canvas.drawText(label, 70f, y, paint)
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.color = text
            canvas.drawText(value.take(48), 440f, y, paint)
            y += 82f
        }

        if (p.client.isNotBlank()) line("Cliente", p.client)
        line("Servicio", p.service.ifBlank { "Servicio de transporte" })
        line("Origen", p.origin.ifBlank { "—" })
        line("Destino", p.destination.ifBlank { "—" })
        line("Tipo de viaje", p.tripType)
        line("Kilómetros", String.format(Locale.US, "%.2f km", p.totalKm))
        line("Equipo", p.vehicle)
        line("Duración", "${p.days} día(s)")

        y += 35f
        paint.color = green
        canvas.drawRoundRect(70f, y, width - 70f, y + 220f, 28f, 28f, paint)
        paint.color = Color.WHITE
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 40f
        canvas.drawText("PRECIO OFERTA", 115f, y + 78f, paint)
        paint.textSize = 68f
        canvas.drawText(usd.format(p.offerPriceUsd), 115f, y + 165f, paint)
        y += 285f

        paint.color = muted
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 30f
        canvas.drawText("Tarifa referencial: ${usd.format(p.pricePerKmUsd)} / km", 70f, y, paint)
        paint.textSize = 25f
        canvas.drawText("Valores expresados en USD. Oferta generada por ASSI Cotizador Transporte.", 70f, height - 70f, paint)
    }

    private fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir oferta"))
    }
}
