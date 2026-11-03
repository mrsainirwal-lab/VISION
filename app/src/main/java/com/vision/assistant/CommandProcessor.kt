package com.vision.assistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CommandResult(val reply: String, val openUrl: String? = null)

object CommandProcessor {

    // Paste your OpenAI-compatible API key here for AI answers.
    // Do NOT commit a real key to GitHub. Use a local secret or a backend in production.
    private const val API_KEY = ""
    private const val AI_ENDPOINT = "https://api.openai.com/v1/chat/completions"
    private const val AI_MODEL = "gpt-4o-mini"

    private val playRegex = Regex("""^play\s+(.+)$""")
    private val searchRegex = Regex("""^(?:search|google)\s+(.+?)(?:\s+on google)?$""")

    suspend fun handle(raw: String): CommandResult {
        val cmd = raw.lowercase(Locale.getDefault()).trim()

        return when {
            "open youtube" in cmd ->
                CommandResult("Opening YouTube.", "https://www.youtube.com")

            "open google" in cmd || "open chrome" in cmd ->
                CommandResult("Opening Google.", "https://www.google.com")

            "what time" in cmd || "time is it" in cmd -> {
                val now = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                CommandResult("It's $now.")
            }

            playRegex.matches(cmd) -> {
                val song = playRegex.find(cmd)!!.groupValues[1]
                CommandResult(
                    "Playing $song on YouTube.",
                    "https://www.youtube.com/results?search_query=${encode(song)}"
                )
            }

            searchRegex.matches(cmd) -> {
                val query = searchRegex.find(cmd)!!.groupValues[1]
                CommandResult(
                    "Searching Google for $query.",
                    "https://www.google.com/search?q=${encode(query)}"
                )
            }

            else -> CommandResult(askAi(raw))
        }
    }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")

    private suspend fun askAi(prompt: String): String = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank()) {
            return@withContext "Add your API key in CommandProcessor.kt to get AI answers."
        }
        try {
            val conn = (URL(AI_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $API_KEY")
            }

            val body = JSONObject()
                .put("model", AI_MODEL)
                .put(
                    "messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
                )

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode !in 200..299) {
                return@withContext "The AI service returned an error."
            }

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (e: Exception) {
            "I couldn't reach the AI service. Check your internet connection."
        }
    }
}
