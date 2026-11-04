package com.vision.assistant

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Keys {
    const val GROQ = "groq_key"
    const val GEMINI = "gemini_key"
    const val ELEVEN_1 = "eleven_key_1"
    const val ELEVEN_2 = "eleven_key_2"
    const val VOICE_ID = "eleven_voice_id"
const val OPENROUTER = "openrouter_key"
    private var prefs: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences {
        prefs?.let { return it }
        val masterKey = MasterKey.Builder(ctx, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val p = EncryptedSharedPreferences.create(
            ctx,
            "vision_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        prefs = p
        return p
    }

    fun get(ctx: Context, name: String): String = prefs(ctx).getString(name, "") ?: ""

    fun set(ctx: Context, name: String, value: String) {
        prefs(ctx).edit().putString(name, value.trim()).apply()
    }
}
