package com.vision.assistant

import org.json.JSONArray
import org.json.JSONObject

object Tools {

    private fun fn(
        name: String,
        desc: String,
        props: JSONObject = JSONObject(),
        required: List<String> = emptyList()
    ): JSONObject {
        val params = JSONObject().put("type", "object").put("properties", props)
        if (required.isNotEmpty()) params.put("required", JSONArray(required))
        return JSONObject()
            .put("type", "function")
            .put("function", JSONObject().put("name", name).put("description", desc).put("parameters", params))
    }

    private fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)
    private fun int(desc: String) = JSONObject().put("type", "integer").put("description", desc)
    private fun bool(desc: String) = JSONObject().put("type", "boolean").put("description", desc)
    private fun obj(vararg pairs: Pair<String, JSONObject>) = JSONObject().apply {
        pairs.forEach { put(it.first, it.second) }
    }

    val all: JSONArray = JSONArray().apply {
        put(fn("open_app", "Open an installed app by its name, e.g. WhatsApp, Camera, Settings.",
            obj("name" to str("App name")), listOf("name")))
        put(fn("set_alarm", "Set an alarm in the clock app.",
            obj("hour" to int("0-23"), "minute" to int("0-59"), "label" to str("Alarm label")),
            listOf("hour", "minute")))
        put(fn("set_timer", "Start a countdown timer.",
            obj("seconds" to int("Duration in seconds"), "label" to str("Label")), listOf("seconds")))
        put(fn("dial_number", "Open the dialer with a number ready. The user presses call.",
            obj("number" to str("Phone number")), listOf("number")))
        put(fn("send_sms", "Open the messages app with a prefilled text. The user presses send.",
            obj("number" to str("Phone number"), "message" to str("Message text")),
            listOf("number", "message")))
            put(fn("play_music", "Play a song, artist or playlist. Opens Spotify search and taps the best match. Use for any music request.",
        obj("query" to str("Song, artist or playlist name")), listOf("query")))
        put(fn("search_web", "Search Google.",
            obj("query" to str("Search query")), listOf("query")))
        put(fn("play_youtube", "Search YouTube for a song or video.",
            obj("query" to str("Song or video name")), listOf("query")))
        put(fn("open_url", "Open a website in the browser.",
            obj("url" to str("Full https URL")), listOf("url")))
        put(fn("set_volume", "Set media volume to a percentage.",
            obj("percent" to int("0-100")), listOf("percent")))
        put(fn("change_volume", "Turn media volume up or down.",
            obj("direction" to str("up or down")), listOf("direction")))
        put(fn("set_mute", "Mute or unmute media sound.",
            obj("muted" to bool("true to mute, false to unmute")), listOf("muted")))
        put(fn("set_brightness", "Set screen brightness as a percentage.",
            obj("percent" to int("0-100")), listOf("percent")))
        put(fn("set_flashlight", "Turn the flashlight on or off.",
            obj("on" to bool("true for on")), listOf("on")))
        put(fn("open_settings", "Open a settings page.",
            obj("page" to str("wifi, bluetooth, internet, display, sound, or home")), listOf("page")))
        put(fn("get_battery", "Read battery level and charging state.", JSONObject()))
        put(fn("get_time", "Read the current time and date.", JSONObject()))
        put(fn("system_action", "Press a system button or take a screenshot.",
            obj("action" to str("back, home, recents, notifications, quick_settings, lock, screenshot, or power_menu")),
            listOf("action")))
        put(fn("scroll", "Scroll the current screen.",
            obj("direction" to str("up or down")), listOf("direction")))
        put(fn("tap_text", "Tap a button or item on screen that shows this text.",
            obj("text" to str("Visible text")), listOf("text")))
        put(fn("type_text", "Type text into the focused text field on screen.",
            obj("text" to str("Text to type")), listOf("text")))
    }
}
