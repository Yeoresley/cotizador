package com.assi.cotizadortransporte.profile

import android.content.Context

object BusinessProfileManager {
    private const val PREFS = "cotiruta_business_profile"
    private const val KEY_NAME = "name"
    private const val KEY_CONTACT = "contact"
    private const val KEY_LOGO_URI = "logo_uri"

    data class Profile(
        val name: String = "",
        val contact: String = "",
        val logoUri: String = ""
    )

    fun load(context: Context): Profile {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Profile(
            name = p.getString(KEY_NAME, "").orEmpty(),
            contact = p.getString(KEY_CONTACT, "").orEmpty(),
            logoUri = p.getString(KEY_LOGO_URI, "").orEmpty()
        )
    }

    fun save(context: Context, profile: Profile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, profile.name.trim())
            .putString(KEY_CONTACT, profile.contact.trim())
            .putString(KEY_LOGO_URI, profile.logoUri.trim())
            .apply()
    }
}
