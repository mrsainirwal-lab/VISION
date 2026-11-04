package com.vision.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Brain(private val ctx: Context, private val actions: PhoneActions) {

    private data class Provider(
        val name: String,
        val baseUrl: String,
        val key: String,
        val fixed: List<String>,
        val fixedFirst: Boolean,
        val filter: (String) -> Boolean,
        val sortKey: (String) -> Int
    )

    private data class Target(val provider: Provider, val model: String)

    private class ActionRanException(msg: String) : Exception(msg)

    companion object {
        private const val MAX_TRIES = 8
    }

    private val system = """
        You are Vision, a voice assistant on an Android phone. Keep answers short and natural.
        Reply in the language the user speaks (English, Hindi, or Hinglish).
        When the user asks for a phone action, use the provided tools.
        Never say an action worked unless the tool result says it worked.
    """.trimIndent()

    private val excluded = listOf(
        "embed", "tts", "image", "live", "audio", "robotics", "imagen",
        "veo", "aqa", "computer-use", "whisper", "guard", "orpheus", "prompt"
    )

    // Model lists fetched from the providers, kept for this app session
    private val cache = HashMap<String, List<String>>()

    private fun providers(): List<Provider> = listOf(
        Provider(
            name = "Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
            key = Keys.get(ctx, Keys.GEMINI),
            fixed = listOf("gemini-flash-lite-latest", "gemini-flash-latest"),
            fixedFirst = false,
            filter = { id ->
                id.startsWith("gemini") &&
                        !Regex("gemini-[12]").containsMatchIn(id) &&
                        excluded.none { id.contains(it) }
            },
            sortKey = { id ->
                when {
                    id.contains("lite") -> 0
                    id.contains("flash") -> 1
                    else -> 2
                }
            }
        ),
        Provider(
            name = "Groq",
            baseUrl = "https://api.groq.com/openai/v1/",
            key = Keys.get(ctx, Keys.GROQ),
            fixed = listOf(
                "llama-3.1-8b-instant",
                "openai/gpt-oss-20b",
                "openai/gpt-oss-120b",
                "llama-3.3-70b-versatile"
            ),
            fixedFirst = true,
            filter = { id -> excluded.none { id.contains(it) } },
            sortKey = { 0 }
        ),
        Provider(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/",
            key = Keys.get(ctx, Keys.OPENROUTER),
            fixed = listOf(
                "openai/gpt-oss-20b:free",
                "meta-llama/llama-3.3-70b-instruct:free",
                "google/gemma-3-27b-it:free"
            ),
            fixedFirst = true,
            filter = { id -> id.endsWith(":free") },
            sortKey = { 0 }
        )
    )

    private suspend fun discover(p: Provider): List<String> {
        cache[p.name]?.let { return it }
        val ids = try {
            withContext(Dispatchers.IO) {
                val conn = (URL(p.baseUrl + "models").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("Authorization", "Bearer ${p.key}")
                }
                if (conn.responseCode !in 200..299) {
                    emptyList()
                } else {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val arr = json.optJSONArray("data") ?: JSONArray()
                    (0 until arr.length()).map {
                        arr.getJSONObject(it).getString("id").removePrefix("models/")
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
        if (ids.isNotEmpty()) cache[p.name] = ids
        return ids
    }

    private suspend fun buildTargets(): List<Target> {
        val out = mutableListOf<Target>()
        for (p in providers().filter { it.key.isNotBlank() }) {
            val discovered = discover(p)
                .filter(p.filter)
                .sortedWith(compareBy<String> { p.sortKey(it) }.thenByDescending { it })
            val ids = if (p.fixedFirst) p.fixed + discovered else discovered + p.fixed
            ids.distinct().forEach { out.add(Target(p, it)) }
        }
        return out.take(MAX_TRIES)
    }

    suspend fun process(userText: String): String {
        val targets = buildTargets()
        if (targets.isEmpty()) {
            return "Settings me kam se kam ek AI key daalo: Gemini, Groq ya OpenRouter."
        }

        val base = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", userText))

        var last = ""
        for (t in targets) {
            try {
                return runConversation(t, JSONArray(base.toString()))
            } catch (e: ActionRanException) {
                return "Action ho gaya, lekin AI ka jawab nahi aaya: ${e.message}"
            } catch (e: Exception) {
                last = "${t.provider.name} ${t.model}: ${e.message}"
            }
        }
        return "Sab AI models fail hue. Last error: $last"
    }

    private suspend fun runConversation(t: Target, messages: JSONArray): String {
        var actionsRan = false
        try {
            for (step in 0 until 5) {
                val message = callApi(t, messages)
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
                    actionsRan = true
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
            return "Main ye kaam poora nahi kar paya."
        } catch (e: Exception) {
            // If a phone action already ran, don't retry with another model (it would repeat the action)
            if (actionsRan) throw ActionRanException(e.message ?: "unknown error")
            throw e
        }
    }

    private suspend fun callApi(t: Target, messages: JSONArray): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = (URL(t.provider.baseUrl + "chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${t.provider.key}")
            }
            val body = JSONObject()
                .put("model", t.model)
                .put("messages", messages)
                .put("tools", Tools.all)
                .put("tool_choice", "auto")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code ${text.take(200)}")
            }

            JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
        }
}
