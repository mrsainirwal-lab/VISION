package com.vision.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class VisionAccessibilityService : AccessibilityService() {

    companion object {
        var instance: VisionAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var volUp = false
    private var volDown = false
    private var overlay: TextView? = null

    private val showTask = Runnable { showOverlay() }
    private val hideTask = Runnable { hideOverlay() }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // Volume + and - together: short press shows the bubble, hold for 2 sec hides it
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        val down = event.action == KeyEvent.ACTION_DOWN
        if (down && event.repeatCount > 0) return false

        if (code == KeyEvent.KEYCODE_VOLUME_UP) volUp = down else volDown = down

        handler.removeCallbacks(showTask)
        handler.removeCallbacks(hideTask)
        if (volUp && volDown) {
            if (overlay == null) handler.postDelayed(showTask, 600)
            else handler.postDelayed(hideTask, 2000)
        }
        return false
    }

    private fun showOverlay() {
        if (overlay != null) return
        val svc = this
        val size = (60 * resources.displayMetrics.density).toInt()

        val view = TextView(svc).apply {
            text = "V"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#CC3B9BFF"))
            }
            setOnClickListener {
                AutoPilot.on = true
                svc.startActivity(
                    Intent(svc, MainActivity::class.java)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                        .putExtra("auto_pilot", true)
                )
            }
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            x = 24
            y = 200
        }

        (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, params)
        overlay = view
    }

    private fun hideOverlay() {
        AutoPilot.on = false
        val v = overlay ?: return
        (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        overlay = null
    }

    override fun onDestroy() {
        hideOverlay()
        instance = null
        super.onDestroy()
    }

    fun global(action: String): String {
        val code = when (action) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            "lock" -> GLOBAL_ACTION_LOCK_SCREEN
            "screenshot" -> GLOBAL_ACTION_TAKE_SCREENSHOT
            "power_menu" -> GLOBAL_ACTION_POWER_DIALOG
            else -> return "Unknown system action."
        }
        return if (performGlobalAction(code)) "Done." else "That action failed."
    }

    fun scroll(forward: Boolean): String {
        val root = rootInActiveWindow ?: return "I can't read this screen."
        val node = findFirst(root) { it.isScrollable } ?: return "Nothing to scroll here."
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return if (node.performAction(action)) "Scrolled." else "Scrolling failed."
    }

    fun tapText(text: String): String {
        val root = rootInActiveWindow ?: return "I can't read this screen."
        val node = root.findAccessibilityNodeInfosByText(text).firstOrNull()
            ?: return "I can't see '$text' on the screen."
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        val ok = (target ?: node).performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (ok) "Tapped $text." else "Tap failed."
    }

    fun typeText(text: String): String {
        val root = rootInActiveWindow ?: return "I can't read this screen."
        val node = findFirst(root) { it.isFocused && it.isEditable }
            ?: return "No text field is focused."
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Typed." else "Typing failed."
    }

    private fun findFirst(node: AccessibilityNodeInfo, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (pred(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirst(child, pred)
            if (found != null) return found
        }
        return null
    }
}
