package dev.opencarl.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Gives the agent eyes and fingers: it can describe the current screen, tap by
 * text, type into the focused field, swipe and press the global buttons.
 */
class CarlAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun describeScreen(maxNodes: Int = 120): String {
        val root = rootInActiveWindow ?: return "no window is available right now"
        val builder = StringBuilder()
        builder.append("package: ").append(root.packageName).append('\n')
        var count = 0
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || count >= maxNodes || depth > 24) return
            val text = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            val label = text.ifBlank { description }
            if (label.isNotBlank() || node.isClickable || node.isEditable) {
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                builder.append("  ".repeat(depth))
                if (label.isNotBlank()) builder.append('"').append(label.take(160)).append('"')
                if (node.isEditable) builder.append(" [editable]")
                if (node.isClickable) builder.append(" [clickable]")
                if (node.isScrollable) builder.append(" [scrollable]")
                val id = node.viewIdResourceName?.substringAfterLast('/')
                if (!id.isNullOrBlank()) builder.append(" #").append(id)
                builder.append(" (").append(bounds.left).append(',').append(bounds.top)
                    .append('-').append(bounds.right).append(',').append(bounds.bottom).append(")\n")
                count++
            }
            for (index in 0 until node.childCount) walk(node.getChild(index), depth + 1)
        }
        walk(root, 0)
        if (count == 0) builder.append("the screen has no readable text\n")
        return builder.toString()
    }

    fun clickText(text: String, exact: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = find(root) { node ->
            val label = node.text?.toString() ?: node.contentDescription?.toString()
            label != null && (if (exact) label.equals(text, true) else label.contains(text, true))
        } ?: return false
        var node: AccessibilityNodeInfo? = target
        while (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            node = node.parent
        }
        val bounds = Rect().also { target.getBoundsInScreen(it) }
        return tap(bounds.exactCenterX(), bounds.exactCenterY())
    }

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun typeText(text: String): Boolean {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun foregroundPackage(): String? = rootInActiveWindow?.packageName?.toString()

    private fun find(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            find(child, predicate)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile
        var instance: CarlAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null
    }
}
