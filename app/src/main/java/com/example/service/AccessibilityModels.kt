package com.example.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent

/**
 * Represents a monitored UI event captured by MaxAccessibilityService across apps.
 */
data class UiEventRecord(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: Int,
    val eventTypeName: String,
    val packageName: String,
    val className: String,
    val text: String,
    val contentDescription: String,
    val isUserInteraction: Boolean = false
) {
    companion object {
        fun resolveEventTypeName(eventType: Int): String = when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICK"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONG_CLICK"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> "VIEW_SELECTED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TEXT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> "NOTIFICATION"
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> "HOVER_ENTER"
            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> "HOVER_EXIT"
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> "GESTURE_START"
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> "GESTURE_END"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "CONTENT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> "TEXT_SELECTION_CHANGED"
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> "ANNOUNCEMENT"
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> "ACCESSIBILITY_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> "FOCUS_CLEARED"
            else -> "EVENT_$eventType"
        }
    }
}

/**
 * Represents an interactive node on the currently displayed window.
 */
data class InteractiveElement(
    val id: String,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isFocused: Boolean
) {
    val centerX: Float get() = bounds.exactCenterX()
    val centerY: Float get() = bounds.exactCenterY()
    val displayName: String
        get() = when {
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            viewId.isNotBlank() -> viewId.substringAfterLast(":id/")
            else -> className.substringAfterLast(".")
        }
}

/**
 * Details from captured incoming notifications.
 */
data class NotificationEventInfo(
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val title: String,
    val text: String
)

/**
 * Result of a screen hierarchy scan for AI and automation.
 */
data class ScreenInspectionResult(
    val packageName: String,
    val fullText: String,
    val interactiveElements: List<InteractiveElement>,
    val timestamp: Long = System.currentTimeMillis()
)
