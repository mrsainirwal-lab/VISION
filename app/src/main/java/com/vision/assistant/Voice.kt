package com.vision.assistant

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class Voice(private val ctx: Context) {

    var onSpeakStart: (() -> Unit)? = null
    var onSpeakDone: (() -> Unit)? = null

    private var fallback: TextToSpeech? = null
    private var fallbackReady = false

    init {
        fallback = TextToSpeech(ctx) { status ->
            fallbackReady = status == TextToSpeech.SUCCESS
            if (fallbackReady) fallback?.language = Locale.US
        }
    }

    suspend fun speak(text: String) {
        onSpeakStart?.invoke()
        val mp3 = fetchElevenLabs(text)
        if (mp3 != null) playFile(mp3) else speakFallback(text)
    }

    // Tries ElevenLabs slot 1, then slot 2. Returns null if both fail.
    private suspend fun fetchElevenLabs(text: String): File? = withContext(Dispatchers.IO) {
        val voiceId = Keys.get(ctx, Keys.VOICE_ID)
        if (voiceId.isBlank()) return@withContext null

        for (slot in listOf(Keys.ELEVEN_1, Keys.ELEVEN_2)) {
            val key = Keys.get(ctx, slot)
            if (key.isBlank()) continue
            try {
                val conn = (URL("https://api.elevenlabs.io/v1/text-to-speech/$voiceId").openConnection()
                        as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "audio/mpeg")
                    setRequestProperty("xi-api-key", key)
                }
                val body = JSONObject()
                    .put("text", text)
                    .put("model_id", "eleven_multilingual_v2")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                if (conn.responseCode in 200..299) {
                    val file = File(ctx.cacheDir, "reply.mp3")
                    conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                    return@withContext file
                }
                // Quota reached or bad key: try the next slot.
            } catch (e: Exception) {
                // Network problem: try the next slot.
            }
        }
        null
    }

    private suspend fun playFile(file: File) = withContext(Dispatchers.Main) {
        val player = MediaPlayer()
        player.setDataSource(file.absolutePath)
        player.setOnPreparedListener { it.start() }
        player.setOnCompletionListener {
            it.release()
            onSpeakDone?.invoke()
        }
        player.setOnErrorListener { mp, _, _ ->
            mp.release()
            onSpeakDone?.invoke()
            true
        }
        player.prepareAsync()
    }

    private fun speakFallback(text: String) {
        if (!fallbackReady) {
            onSpeakDone?.invoke()
            return
        }
        fallback?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onSpeakDone?.invoke() }
            override fun onError(utteranceId: String?) { onSpeakDone?.invoke() }
        })
        fallback?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vision-reply")
    }

    fun shutdown() {
        fallback?.shutdown()
    }
}
