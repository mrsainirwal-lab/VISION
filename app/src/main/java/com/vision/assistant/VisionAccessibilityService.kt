package com.vision.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class VisionAccessibilityService : AccessibilityService() {

    companion object {
        var instance: VisionAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun global(action: String): String {
        val code = when (action) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "lock" -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            "screenshot" -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            "power_menu" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
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
