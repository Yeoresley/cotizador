package com.assi.cotizadortransporte.license

import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.assi.cotizadortransporte.BuildConfig
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object LicenseManager {
    private const val PREFS = "assi_license_prefs"
    private const val KEY_LICENSE = "license_token"
    const val REQUEST_PREFIX = "ASSI-REQ-1."
    const val LICENSE_PREFIX = "ASSI-LIC-1."

    // Clave pública de DESARROLLO. Antes de comercializar, rotar por una clave de producción.
    private const val PUBLIC_KEY_DER_B64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAndWd9gi2qMuyuwCr3hNj6zkjpbrIKt3vpq5hjTgn0Ydzk3mvmKnO+CzV1g2jqiH2vCROjuM5W0JG2Jg9rjyBw=="

    // Se configura cuando el propietario indique el correo definitivo de licencias.
    const val LICENSE_REQUEST_EMAIL = "kcodguez@gmail.com"

    data class Status(
        val valid: Boolean,
        val message: String,
        val licenseId: String = "",
        val customer: String = "",
        val phone: String = "",
        val deviceId: String = "",
        val issuedAtEpochSec: Long = 0,
        val expiresAtEpochSec: Long = 0,
        val updatesUntilEpochSec: Long = 0
    )

    fun deviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
        val source = "${androidId}|${context.packageName}"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
        val hex = digest.take(12).joinToString("") { "%02X".format(it) }
        return hex.chunked(4).joinToString("-")
    }

    fun buildRequestCode(
        context: Context,
        customer: String,
        phone: String,
        email: String
    ): String {
        val payload = JSONObject()
            .put("version", 1)
            .put("customer", customer.trim())
            .put("phone", phone.trim())
            .put("email", email.trim())
            .put("deviceId", deviceId(context))
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("requestedAtEpochSec", System.currentTimeMillis() / 1000L)
            .toString()
        return REQUEST_PREFIX + base64Url(payload.toByteArray(StandardCharsets.UTF_8))
    }

    fun requestSummary(context: Context, customer: String, phone: String, email: String): String {
        val code = buildRequestCode(context, customer, phone, email)
        return buildString {
            appendLine("SOLICITUD DE LICENCIA · CotiRuta")
            appendLine()
            appendLine("Cliente: ${customer.trim()}")
            appendLine("Teléfono: ${phone.trim()}")
            appendLine("Correo: ${email.trim()}")
            appendLine("Dispositivo: ${deviceId(context)}")
            appendLine("Versión app: ${BuildConfig.VERSION_NAME}")
            appendLine()
            appendLine("Código de solicitud:")
            append(code)
        }
    }

    fun currentStatus(context: Context): Status {
        val token = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LICENSE, null)
            ?: return Status(false, "Sin licencia activada.", deviceId = deviceId(context))
        return validate(context, token)
    }

    fun activate(context: Context, token: String): Status {
        val status = validate(context, token.trim())
        if (status.valid) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LICENSE, token.trim())
                .apply()
        }
        return status
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LICENSE)
            .apply()
    }

    fun validate(context: Context, token: String): Status {
        return runCatching {
            require(token.startsWith(LICENSE_PREFIX)) { "Formato de licencia no reconocido." }
            val body = token.removePrefix(LICENSE_PREFIX)
            val parts = body.split('.')
            require(parts.size == 2) { "Licencia incompleta." }

            val payloadBytes = base64UrlDecode(parts[0])
            val signatureBytes = base64UrlDecode(parts[1])

            val keyBytes = Base64.decode(PUBLIC_KEY_DER_B64, Base64.DEFAULT)
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(keyBytes))

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKey)
            verifier.update(payloadBytes)
            require(verifier.verify(signatureBytes)) { "Firma de licencia inválida." }

            val payload = JSONObject(String(payloadBytes, StandardCharsets.UTF_8))
            require(payload.optInt("version", 0) == 1) { "Versión de licencia no compatible." }

            val licensedDevice = payload.optString("deviceId", "")
            val currentDevice = deviceId(context)
            require(licensedDevice == currentDevice) {
                "Esta licencia pertenece a otro teléfono."
            }

            val now = System.currentTimeMillis() / 1000L
            val expiresAt = payload.optLong("expiresAtEpochSec", 0L)
            require(expiresAt == 0L || now <= expiresAt) { "La licencia ha vencido." }

            Status(
                valid = true,
                message = "Licencia válida.",
                licenseId = payload.optString("licenseId", ""),
                customer = payload.optString("customer", ""),
                phone = payload.optString("phone", ""),
                deviceId = licensedDevice,
                issuedAtEpochSec = payload.optLong("issuedAtEpochSec", 0L),
                expiresAtEpochSec = expiresAt,
                updatesUntilEpochSec = payload.optLong("updatesUntilEpochSec", 0L)
            )
        }.getOrElse {
            Status(
                valid = false,
                message = it.message ?: "Licencia inválida.",
                deviceId = deviceId(context)
            )
        }
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun base64UrlDecode(value: String): ByteArray =
        Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
