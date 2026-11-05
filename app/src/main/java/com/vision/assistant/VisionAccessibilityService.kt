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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import kotlin.math.abs

class VisionAccessibilityService : AccessibilityService() {

    companion object {
        var instance: VisionAccessibilityService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private var volUp = false
    private var volDown = false

    private var bubble: ImageView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var longPressed = false

    private val showTask = Runnable { showBubble() }
    private val longPressTask = Runnable {
        longPressed = true
        disableBubble()
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // Volume + and - together for 0.1 sec shows the bubble
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        val down = event.action == KeyEvent.ACTION_DOWN
        if (down && event.repeatCount > 0) return false

        if (code == KeyEvent.KEYCODE_VOLUME_UP) volUp = down else volDown = down

        handler.removeCallbacks(showTask)
        if (volUp && volDown && bubble == null) {
            handler.postDelayed(showTask, 100)
        }
        return false
    }

    private fun showBubble() {
        if (bubble != null) return
        val size = (64 * resources.displayMetrics.density).toInt()

        val view = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22000000"))
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            setOnTouchListener(touchListener)
        }

        val lp = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - 24
            y = resources.displayMetrics.heightPixels / 2
        }

        wm.addView(view, lp)
        bubble = view
        bubbleParams = lp
    }

    private val touchListener = View.OnTouchListener { v, e ->
        val lp = bubbleParams ?: return@OnTouchListener false
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX
                downY = e.rawY
                startX = lp.x
                startY = lp.y
                dragging = false
                longPressed = false
                handler.postDelayed(longPressTask, 2000)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - downX
                val dy = e.rawY - downY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                    handler.removeCallbacks(longPressTask)
                }
                if (dragging) {
                    lp.x = startX + dx.toInt()
                    lp.y = startY + dy.toInt()
                    wm.updateViewLayout(v, lp)
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressTask)
                if (!dragging && !longPressed) openVision()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressTask)
                true
            }
            else -> false
        }
    }

    private fun openVision() {
        AutoPilot.on = true
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                .putExtra("auto_pilot", true)
        )
    }

    // Long press (2 sec) on the bubble turns it off
    private fun disableBubble() {
        AutoPilot.on = false
        bubble?.let { wm.removeView(it) }
        bubble = null
        bubbleParams = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(showTask)
        handler.removeCallbacks(longPressTask)
        disableBubble()
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