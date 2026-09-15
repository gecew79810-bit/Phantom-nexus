package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * MAX AI OS Native Accessibility Service.
 * Monitors real-time UI events across installed apps and performs precise gestures
 * (tap, swipe, scroll, click, input) to enable AI-driven device interactions.
 */
class MaxAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "MaxAccessibilityService"
        private const val MAX_EVENT_LOG_SIZE = 50

        @Volatile
        private var instance: MaxAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _currentPackage = MutableStateFlow("")
        val currentPackage: StateFlow<String> = _currentPackage.asStateFlow()

        private val _currentActivity = MutableStateFlow("")
        val currentActivity: StateFlow<String> = _currentActivity.asStateFlow()

        private val _recentEvents = MutableStateFlow<List<UiEventRecord>>(emptyList())
        val recentEvents: StateFlow<List<UiEventRecord>> = _recentEvents.asStateFlow()

        private val _lastNotification = MutableStateFlow<NotificationEventInfo?>(null)
        val lastNotification: StateFlow<NotificationEventInfo?> = _lastNotification.asStateFlow()

        private val _lastCapturedScreenText = MutableStateFlow("")
        val lastCapturedScreenText: StateFlow<String> = _lastCapturedScreenText.asStateFlow()

        fun isServiceRunning(): Boolean = instance != null

        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings", e)
            }
        }

        fun clearEventLogs() {
            _recentEvents.value = emptyList()
        }

        // =========================================================================
        // UI MONITORING & EVENT AWAITING
        // =========================================================================

        /**
         * Suspends until the target package name becomes the foreground window or timeout expires.
         */
        suspend fun waitForPackage(targetPackage: String, timeoutMs: Long = 5000L): Boolean {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (_currentPackage.value.contains(targetPackage, ignoreCase = true)) {
                    return true
                }
                delay(150)
            }
            return false
        }

        /**
         * Suspends until the target text appears on the active screen or timeout expires.
         */
        suspend fun waitForText(targetText: String, timeoutMs: Long = 5000L): Boolean {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val screenText = captureCurrentScreenText()
                if (screenText.contains(targetText, ignoreCase = true)) {
                    return true
                }
                delay(200)
            }
            return false
        }

        // =========================================================================
        // GESTURE ENGINE (dispatchGesture API 24+)
        // =========================================================================

        /**
         * Dispatches an arbitrary GestureDescription asynchronously using coroutines.
         */
        suspend fun dispatchGestureAsync(gesture: GestureDescription): Boolean {
            val service = instance ?: run {
                Log.w(TAG, "Cannot dispatch gesture: service is null or disabled.")
                return false
            }

            return suspendCancellableCoroutine { continuation ->
                val callback = object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        super.onCompleted(gestureDescription)
                        Log.d(TAG, "Gesture completed successfully.")
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        super.onCancelled(gestureDescription)
                        Log.w(TAG, "Gesture cancelled by system.")
                        if (continuation.isActive) continuation.resume(false)
                    }
                }

                val dispatched = service.dispatchGesture(gesture, callback, Handler(Looper.getMainLooper()))
                if (!dispatched) {
                    Log.e(TAG, "dispatchGesture returned false immediately.")
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }

        /**
         * Taps at the specified coordinate (x, y).
         */
        suspend fun tap(x: Float, y: Float, durationMs: Long = 60L): Boolean {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(10L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGestureAsync(gesture)
        }

        /**
         * Performs a double tap at (x, y).
         */
        suspend fun doubleTap(x: Float, y: Float, delayBetweenMs: Long = 100L): Boolean {
            val firstTap = tap(x, y, 50L)
            if (!firstTap) return false
            delay(delayBetweenMs)
            return tap(x, y, 50L)
        }

        /**
         * Performs a long press at (x, y).
         */
        suspend fun longPress(x: Float, y: Float, durationMs: Long = 750L): Boolean {
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGestureAsync(gesture)
        }

        /**
         * Swipes smoothly from (startX, startY) to (endX, endY).
         */
        suspend fun swipe(
            startX: Float,
            startY: Float,
            endX: Float,
            endY: Float,
            durationMs: Long = 300L
        ): Boolean {
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGestureAsync(gesture)
        }

        /**
         * Drags from (fromX, fromY) and drops at (toX, toY).
         */
        suspend fun dragAndDrop(
            fromX: Float,
            fromY: Float,
            toX: Float,
            toY: Float,
            holdMs: Long = 500L,
            moveMs: Long = 400L
        ): Boolean {
            val path = Path().apply {
                moveTo(fromX, fromY)
                lineTo(toX, toY)
            }
            val stroke = GestureDescription.StrokeDescription(path, holdMs, moveMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGestureAsync(gesture)
        }

        /**
         * Scrolls downward by simulating an upward finger swipe.
         */
        suspend fun scrollDown(durationMs: Long = 320L): Boolean {
            val service = instance
            val metrics = service?.resources?.displayMetrics
            val width = metrics?.widthPixels?.toFloat() ?: 1080f
            val height = metrics?.heightPixels?.toFloat() ?: 2400f

            val startX = width / 2f
            val startY = height * 0.72f
            val endY = height * 0.28f

            return swipe(startX, startY, startX, endY, durationMs)
        }

        /**
         * Scrolls upward by simulating a downward finger swipe.
         */
        suspend fun scrollUp(durationMs: Long = 320L): Boolean {
            val service = instance
            val metrics = service?.resources?.displayMetrics
            val width = metrics?.widthPixels?.toFloat() ?: 1080f
            val height = metrics?.heightPixels?.toFloat() ?: 2400f

            val startX = width / 2f
            val startY = height * 0.28f
            val endY = height * 0.72f

            return swipe(startX, startY, startX, endY, durationMs)
        }

        /**
         * Scrolls left (content moves right).
         */
        suspend fun scrollLeft(durationMs: Long = 300L): Boolean {
            val metrics = instance?.resources?.displayMetrics
            val width = metrics?.widthPixels?.toFloat() ?: 1080f
            val height = metrics?.heightPixels?.toFloat() ?: 2400f
            return swipe(width * 0.2f, height / 2f, width * 0.8f, height / 2f, durationMs)
        }

        /**
         * Scrolls right (content moves left).
         */
        suspend fun scrollRight(durationMs: Long = 300L): Boolean {
            val metrics = instance?.resources?.displayMetrics
            val width = metrics?.widthPixels?.toFloat() ?: 1080f
            val height = metrics?.heightPixels?.toFloat() ?: 2400f
            return swipe(width * 0.8f, height / 2f, width * 0.2f, height / 2f, durationMs)
        }

        /**
         * Two-finger pinch gesture (zoom in or zoom out).
         */
        suspend fun pinch(centerX: Float, centerY: Float, zoomIn: Boolean, durationMs: Long = 400L): Boolean {
            val offsetStart = if (zoomIn) 120f else 350f
            val offsetEnd = if (zoomIn) 350f else 120f

            val path1 = Path().apply {
                moveTo(centerX, centerY - offsetStart)
                lineTo(centerX, centerY - offsetEnd)
            }
            val path2 = Path().apply {
                moveTo(centerX, centerY + offsetStart)
                lineTo(centerX, centerY + offsetEnd)
            }

            val stroke1 = GestureDescription.StrokeDescription(path1, 0, durationMs)
            val stroke2 = GestureDescription.StrokeDescription(path2, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke1).addStroke(stroke2).build()
            return dispatchGestureAsync(gesture)
        }

        // =========================================================================
        // NODE INTERACTION & ACCESSIBILITY ACTIONS
        // =========================================================================

        /**
         * Robust click: searches nodes for text. Tries Accessibility ACTION_CLICK.
         * If the node or parents are not clickable via action, dispatches a hardware-accurate
         * coordinate tap on the node's screen center!
         */
        suspend fun clickNodeWithText(targetText: String, exactMatch: Boolean = false): Boolean {
            val s = instance ?: return false
            val root = s.rootInActiveWindow ?: return false

            val nodes = root.findAccessibilityNodeInfosByText(targetText)
            for (node in nodes) {
                val nodeText = node.text?.toString() ?: ""
                val nodeDesc = node.contentDescription?.toString() ?: ""
                val matches = if (exactMatch) {
                    nodeText.equals(targetText, ignoreCase = true) || nodeDesc.equals(targetText, ignoreCase = true)
                } else {
                    nodeText.contains(targetText, ignoreCase = true) || nodeDesc.contains(targetText, ignoreCase = true)
                }

                if (matches) {
                    // Try direct click action
                    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                    // Try parent clickable action
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            return true
                        }
                        parent = parent.parent
                    }

                    // Fallback to gesture coordinate tap!
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0) {
                        Log.d(TAG, "Dispatching gesture tap on center bounds: (${bounds.centerX()}, ${bounds.centerY()})")
                        return tap(bounds.exactCenterX(), bounds.exactCenterY())
                    }
                }
            }
            return false
        }

        /**
         * Clicks a node by its view resource ID.
         */
        suspend fun clickNodeWithId(targetId: String): Boolean {
            val s = instance ?: return false
            val root = s.rootInActiveWindow ?: return false
            val nodes = root.findAccessibilityNodeInfosByViewId(targetId)
            for (node in nodes) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                    parent = parent.parent
                }
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    return tap(bounds.exactCenterX(), bounds.exactCenterY())
                }
            }
            return false
        }

        /**
         * Inputs text into an editable node or currently focused node.
         */
        suspend fun inputText(textToEnter: String, targetHintOrId: String? = null): Boolean {
            val s = instance ?: return false
            val root = s.rootInActiveWindow ?: return false

            val targetNode: AccessibilityNodeInfo? = if (!targetHintOrId.isNullOrBlank()) {
                val byId = root.findAccessibilityNodeInfosByViewId(targetHintOrId)
                if (byId.isNotEmpty()) byId.first()
                else {
                    val byText = root.findAccessibilityNodeInfosByText(targetHintOrId)
                    byText.firstOrNull { it.isEditable || it.isFocusable }
                }
            } else {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: findFirstEditableNode(root)
            }

            if (targetNode != null) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToEnter)
                }
                val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (success) return true

                // Fallback: tap the node to focus, delay, then try set text
                val bounds = Rect()
                targetNode.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    tap(bounds.exactCenterX(), bounds.exactCenterY())
                    delay(200)
                    return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }
            return false
        }

        private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isEditable) return node
            for (i in 0 until node.childCount) {
                val found = findFirstEditableNode(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        // =========================================================================
        // SCREEN TEXT & HIERARCHY INSPECTION
        // =========================================================================

        fun captureCurrentScreenText(): String {
            val s = instance ?: return "Accessibility Service is not active. Please enable in Settings."
            val root = s.rootInActiveWindow ?: return "No active window found to inspect."
            val sb = StringBuilder()
            extractNodeText(root, sb, 0)
            val result = sb.toString().trim()
            _lastCapturedScreenText.value = result
            return if (result.isNotEmpty()) result else "Screen inspected: No readable text detected."
        }

        private fun extractNodeText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
            if (node == null || depth > 30) return
            val text = node.text?.toString()?.trim()
            val desc = node.contentDescription?.toString()?.trim()

            if (!text.isNullOrEmpty()) {
                sb.append(text).append("\n")
            } else if (!desc.isNullOrEmpty()) {
                sb.append(desc).append("\n")
            }

            for (i in 0 until node.childCount) {
                extractNodeText(node.getChild(i), sb, depth + 1)
            }
        }

        /**
         * Scans the full screen hierarchy and returns interactive elements (buttons, fields, lists).
         */
        fun inspectScreenHierarchy(): ScreenInspectionResult {
            val s = instance ?: return ScreenInspectionResult(
                packageName = "",
                fullText = "Service not active",
                interactiveElements = emptyList()
            )
            val root = s.rootInActiveWindow ?: return ScreenInspectionResult(
                packageName = _currentPackage.value,
                fullText = "No active window",
                interactiveElements = emptyList()
            )

            val fullTextBuilder = StringBuilder()
            val elements = mutableListOf<InteractiveElement>()
            collectInteractiveNodes(root, elements, fullTextBuilder, 0)

            return ScreenInspectionResult(
                packageName = root.packageName?.toString() ?: _currentPackage.value,
                fullText = fullTextBuilder.toString().trim(),
                interactiveElements = elements
            )
        }

        private fun collectInteractiveNodes(
            node: AccessibilityNodeInfo?,
            list: MutableList<InteractiveElement>,
            textCollector: StringBuilder,
            depth: Int
        ) {
            if (node == null || depth > 30) return

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""

            if (text.isNotEmpty()) textCollector.append(text).append("\n")
            else if (desc.isNotEmpty()) textCollector.append(desc).append("\n")

            val isInteractive = node.isClickable || node.isEditable || node.isScrollable || node.isCheckable
            if (isInteractive || text.isNotEmpty() || desc.isNotEmpty()) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0) {
                    list.add(
                        InteractiveElement(
                            id = "${viewId}_${bounds.left}_${bounds.top}",
                            text = text,
                            contentDescription = desc,
                            viewId = viewId,
                            className = className,
                            bounds = bounds,
                            isClickable = node.isClickable,
                            isEditable = node.isEditable,
                            isScrollable = node.isScrollable,
                            isFocused = node.isFocused
                        )
                    )
                }
            }

            for (i in 0 until node.childCount) {
                collectInteractiveNodes(node.getChild(i), list, textCollector, depth + 1)
            }
        }

        // =========================================================================
        // GLOBAL SYSTEM ACTIONS
        // =========================================================================

        fun performHome(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false

        fun performBack(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false

        fun performRecents(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false

        fun performNotifications(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false

        fun performQuickSettings(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) ?: false

        fun performPowerDialog(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) ?: false

        fun performLockScreen(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
            } else false

        fun performSplitScreen(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                instance?.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) ?: false
            } else false

        suspend fun takeScreenshotAsync(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return suspendCancellableCoroutine { continuation ->
                    service.takeScreenshot(
                        0,
                        service.mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                Log.i(TAG, "Screenshot taken successfully.")
                                if (continuation.isActive) continuation.resume(true)
                            }

                            override fun onFailure(errorCode: Int) {
                                Log.e(TAG, "Screenshot failed with error code: $errorCode")
                                if (continuation.isActive) continuation.resume(false)
                            }
                        }
                    )
                }
            }
            return false
        }
    }

    // =============================================================================
    // SERVICE LIFECYCLE & EVENT PROCESSING
    // =============================================================================

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        Log.i(TAG, "MaxAccessibilityService connected successfully. UI monitor active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val pkg = event.packageName?.toString() ?: ""
            val cls = event.className?.toString() ?: ""
            val eventType = event.eventType
            val eventName = UiEventRecord.resolveEventTypeName(eventType)

            // Extract text from event records
            val textBuilder = StringBuilder()
            event.text?.forEach { charSeq ->
                if (!charSeq.isNullOrBlank()) {
                    textBuilder.append(charSeq).append(" ")
                }
            }
            val eventText = textBuilder.toString().trim()
            val contentDesc = event.contentDescription?.toString() ?: ""

            // 1. Update foreground window tracking
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg.isNotEmpty()) {
                _currentPackage.value = pkg
                _currentActivity.value = cls
            }

            // 2. Intercept incoming notifications
            if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                val parcelableData = event.parcelableData
                val notifText = if (parcelableData is Notification) {
                    val extras = parcelableData.extras
                    val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                    val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: eventText
                    _lastNotification.value = NotificationEventInfo(
                        packageName = pkg,
                        title = title,
                        text = body
                    )
                    "$title: $body"
                } else {
                    _lastNotification.value = NotificationEventInfo(
                        packageName = pkg,
                        title = "Notification",
                        text = eventText
                    )
                    eventText
                }
                Log.d(TAG, "Notification intercepted from $pkg: $notifText")
            }

            // 3. Keep bounded event log for monitoring HUD
            val record = UiEventRecord(
                eventType = eventType,
                eventTypeName = eventName,
                packageName = pkg,
                className = cls,
                text = eventText,
                contentDescription = contentDesc,
                isUserInteraction = eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                        eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                        eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
            )

            val currentList = _recentEvents.value.toMutableList()
            if (currentList.size >= MAX_EVENT_LOG_SIZE) {
                currentList.removeAt(0)
            }
            currentList.add(record)
            _recentEvents.value = currentList

        } catch (e: Exception) {
            Log.e(TAG, "Error handling accessibility event", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "MaxAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isConnected.value = false
        Log.i(TAG, "MaxAccessibilityService destroyed.")
    }
}
