package com.vision.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneActions(private val ctx: Context) {

    fun run(name: String, a: JSONObject): String = try {
        when (name) {
            "open_app" -> openApp(a.getString("name"))
            "set_alarm" -> {
                start(
                    Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, a.getInt("hour"))
                        .putExtra(AlarmClock.EXTRA_MINUTES, a.getInt("minute"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Vision alarm"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
                "Alarm set."
            }
            "set_timer" -> {
                start(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, a.getInt("seconds"))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, a.optString("label", "Vision timer"))
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
                "Timer started."
            }
            "dial_number" -> {
                start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${a.getString("number")}")))
                "Dialer opened. Press call to dial."
            }
            "send_sms" -> {
                start(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${a.getString("number")}"))
                        .putExtra("sms_body", a.getString("message"))
                )
                "Messages opened with your text. Press send."
            }
            "search_web" -> {
                open("https://www.google.com/search?q=${enc(a.getString("query"))}")
                "Searching Google."
            }
            "play_youtube" -> {
                open("https://www.youtube.com/results?search_query=${enc(a.getString("query"))}")
                "Opening YouTube results."
            }
            "open_url" -> {
                open(a.getString("url"))
                "Opening the website."
            }
            "set_volume" -> setVolume(a.getInt("percent"))
            "change_volume" -> {
                val dir = if (a.getString("direction") == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
                "Volume changed."
            }
            "set_mute" -> {
                val dir = if (a.getBoolean("muted")) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
                audio().adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
                "Done."
            }
            "set_brightness" -> setBrightness(a.getInt("percent"))
            "set_flashlight" -> setTorch(a.getBoolean("on"))
            "open_settings" -> openSettings(a.getString("page"))
            "get_battery" -> battery()
            "get_time" -> SimpleDateFormat("EEEE, d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
            "system_action" ->
                VisionAccessibilityService.instance?.global(a.getString("action")) ?: needA11y()
            "scroll" ->
                VisionAccessibilityService.instance?.scroll(a.getString("direction") == "down") ?: needA11y()
            "tap_text" ->
                VisionAccessibilityService.instance?.tapText(a.getString("text")) ?: needA11y()
            "type_text" ->
                VisionAccessibilityService.instance?.typeText(a.getString("text")) ?: needA11y()
            else -> "Unknown action."
        }
    } catch (e: Exception) {
        "Failed: ${e.message}"
    }

    private fun needA11y() = "Turn on Vision's accessibility permission in Settings first."

    private fun audio() = ctx.getSystemService(AudioManager::class.java)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun start(i: Intent) {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun open(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun openApp(name: String): String {
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val match = apps.firstOrNull { it.loadLabel(pm).toString().equals(name, true) }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().contains(name, true) }
            ?: return "No app named $name was found."
        val intent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return "Cannot open $name."
        start(intent)
        return "Opened ${match.loadLabel(pm)}."
    }

    private fun setVolume(percent: Int): String {
        val am = audio()
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val p = percent.coerceIn(0, 100)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (p * max) / 100, AudioManager.FLAG_SHOW_UI)
        return "Volume set to $p percent."
    }

    private fun setBrightness(percent: Int): String {
        if (!Settings.System.canWrite(ctx)) {
            start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            return "Allow Vision to change system settings, then ask me again."
        }
        val p = percent.coerceIn(0, 100)
        Settings.System.putInt(
            ctx.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (p * 255) / 100)
        return "Brightness set to $p percent."
    }

    private fun setTorch(on: Boolean): String {
        val cm = ctx.getSystemService(CameraManager::class.java)
        cm.setTorchMode(cm.cameraIdList[0], on)
        return "Flashlight is ${if (on) "on" else "off"}."
    }

    private fun openSettings(page: String): String {
        val newApi = Build.VERSION.SDK_INT >= 29
        val action = when (page) {
            "wifi" -> if (newApi) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            "internet" -> if (newApi) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        start(Intent(action))
        return "Opened $page. Android does not let apps switch Wi-Fi or Bluetooth directly, so please tap the toggle."
    }

    private fun battery(): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "Battery status is unknown."
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return "Battery is at ${level * 100 / scale} percent, ${if (charging) "charging" else "not charging"}."
    }
}
