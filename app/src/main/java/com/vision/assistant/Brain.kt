package com.vision.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Brain(private val ctx: Context, private val actions: PhoneActions) {

    private data class Provider(val name: String, val url: String, val model: String, val key: String)

    private val system = """
        You are Vision, a voice assistant on an Android phone. Keep answers short and natural.
        Reply in the language the user speaks (English, Hindi, or Hinglish).
        When the user asks for a phone action, use the provided tools.
        Never say an action worked unless the tool result says it worked.
    """.trimIndent()

    private fun providers(): List<Provider> = listOf(
        Provider("Groq", "https://api.groq.com/openai/v1/chat/completions",
            "llama-3.3-70b-versatile", Keys.get(ctx, Keys.GROQ)),
        Provider("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "gemini-2.0-flash", Keys.get(ctx, Keys.GEMINI))
    ).filter { it.key.isNotBlank() }

    suspend fun process(userText: String): String {
        val list = providers()
        if (list.isEmpty()) return "Please add a Groq or Gemini API key in Settings."

        val base = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", userText))

        var lastError = ""
        for (p in list) {
            try {
                return runConversation(p, JSONArray(base.toString()))
            } catch (e: Exception) {
                lastError = e.message ?: "unknown error"
            }
        }
        return "AI service failed: $lastError"
    }

    private suspend fun runConversation(p: Provider, messages: JSONArray): String {
        for (step in 0 until 5) {
            val message = callApi(p, messages)
            val toolCalls = message.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                return message.optString("content", "").ifBlank { "Done." }
            }

            messages.put(message)
            for (i in 0 until toolCalls.length()) {
                val call = toolCalls.getJSONObject(i)
                val fn = call.getJSONObject("function")
                val args = try {
                    JSONObject(fn.optString("arguments", "{}"))
                } catch (e: Exception) {
                    JSONObject()
                }
                val result = withContext(Dispatchers.Main) {
                    actions.run(fn.getString("name"), args)
                }
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.getString("id"))
                        .put("content", result)
                )
            }
        }
        return "I couldn't finish that request."
    }

    private suspend fun callApi(p: Provider, messages: JSONArray): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = (URL(p.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${p.key}")
            }
            val body = JSONObject()
                .put("model", p.model)
                .put("messages", messages)
                .put("tools", Tools.all)
                .put("tool_choice", "auto")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("${p.name} error $code")

            JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
        }
}
