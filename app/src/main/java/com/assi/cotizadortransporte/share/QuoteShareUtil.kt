package com.assi.cotizadortransporte.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
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
        val pricePerKmUsd: Double,
        val currencyCode: String,
        val currencyRatePerUsd: Double,
        val entityName: String = "",
        val entityContact: String = "",
        val logoUri: String = "",
        val demo: Boolean = false
    )

    fun shareImage(context: Context, p: Presentation) {
        val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawSummary(context, canvas, p, 1200, 1600)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "cotiruta_oferta.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shareFile(context, file, "image/png")
    }

    fun sharePdf(context: Context, p: Presentation) {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(1200, 1600, 1).create()
        val page = pdf.startPage(pageInfo)
        drawSummary(context, page.canvas, p, 1200, 1600)
        pdf.finishPage(page)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "cotiruta_oferta.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        shareFile(context, file, "application/pdf")
    }

    private fun drawSummary(context: Context, canvas: Canvas, p: Presentation, width: Int, height: Int) {
        canvas.drawColor(Color.WHITE)
        val navy = Color.rgb(13, 42, 74)
        val mint = Color.rgb(0, 134, 106)
        val text = Color.rgb(30, 30, 30)
        val muted = Color.rgb(95, 95, 95)
        val demoRed = Color.rgb(176, 48, 48)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
        }

        paint.color = navy
        canvas.drawRect(0f, 0f, width.toFloat(), 190f, paint)

        val logo = loadLogo(context, p.logoUri)
        if (logo != null) {
            val maxW = 120
            val maxH = 120
            val scale = minOf(maxW.toFloat() / logo.width, maxH.toFloat() / logo.height)
            val w = (logo.width * scale).toInt()
            val h = (logo.height * scale).toInt()
            val left = width - 70 - w
            val top = 35
            canvas.drawBitmap(
                logo,
                null,
                android.graphics.Rect(left, top, left + w, top + h),
                Paint(Paint.ANTI_ALIAS_FLAG)
            )
        }

        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText(p.entityName.ifBlank { "CotiRuta" }.take(30), 70f, 72f, paint)
        paint.textSize = 27f
        paint.typeface = android.graphics.Typeface.DEFAULT
        canvas.drawText("Cotización profesional de transporte", 70f, 118f, paint)
        if (p.entityContact.isNotBlank()) {
            paint.textSize = 21f
            canvas.drawText(p.entityContact.take(65), 70f, 154f, paint)
        }

        if (p.demo) {
            paint.color = demoRed
            canvas.drawRect(0f, 190f, width.toFloat(), 245f, paint)
            paint.color = Color.WHITE
            paint.textSize = 25f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("MODO DEMO · NO VÁLIDO COMO OFERTA COMERCIAL", 70f, 226f, paint)
        }

        var y = if (p.demo) 315f else 270f
        fun line(label: String, value: String) {
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = 31f
            paint.color = muted
            canvas.drawText(label, 70f, y, paint)
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.color = text
            canvas.drawText(value.take(48), 440f, y, paint)
            y += 78f
        }

        if (p.client.isNotBlank()) line("Cliente", p.client)
        line("Servicio", p.service.ifBlank { "Servicio de transporte" })
        line("Origen", p.origin.ifBlank { "—" })
        line("Destino", p.destination.ifBlank { "—" })
        line("Tipo de viaje", p.tripType)
        line("Kilómetros", String.format(Locale.US, "%.2f km", p.totalKm))
        line("Equipo", p.vehicle)
        line("Duración", "${p.days} día(s)")

        y += 28f
        paint.color = mint
        canvas.drawRoundRect(70f, y, width - 70f, y + 220f, 28f, 28f, paint)
        paint.color = Color.WHITE
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = 38f
        canvas.drawText("PRECIO OFERTA", 115f, y + 76f, paint)
        paint.textSize = 62f
        canvas.drawText(money(p.offerPriceUsd, p.currencyCode, p.currencyRatePerUsd), 115f, y + 165f, paint)
        y += 285f

        paint.color = muted
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.textSize = 28f
        canvas.drawText(
            "Tarifa referencial: ${money(p.pricePerKmUsd, p.currencyCode, p.currencyRatePerUsd)} / km",
            70f,
            y,
            paint
        )

        if (p.demo) {
            paint.save()
        }

        paint.textSize = 22f
        canvas.drawText(
            "Moneda de salida: ${p.currencyCode.uppercase(Locale.US)} · Generado por CotiRuta.",
            70f,
            height - 100f,
            paint
        )
        canvas.drawText(
            "Desarrollado por ASSI SURL.",
            70f,
            height - 62f,
            paint
        )

        if (p.demo) {
            val watermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(35, 176, 48, 48)
                textSize = 150f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.save()
            canvas.rotate(-28f, width / 2f, height / 2f)
            canvas.drawText("COTIRUTA DEMO", width / 2f, height / 2f, watermark)
            canvas.restore()
        }
    }

    private fun loadLogo(context: Context, uriText: String): Bitmap? {
        if (uriText.isBlank()) return null
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriText))?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun money(valueUsd: Double, code: String, rate: Double): String {
        val normalized = code.trim().uppercase(Locale.US).ifBlank { "USD" }
        val safeRate = if (rate > 0.0) rate else 1.0
        return String.format(Locale.US, "%s %,.2f", normalized, valueUsd * safeRate)
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
