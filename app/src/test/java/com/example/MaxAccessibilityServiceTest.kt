package com.example

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import com.example.service.InteractiveElement
import com.example.service.MaxAccessibilityService
import com.example.service.UiEventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaxAccessibilityServiceTest {

    @Test
    fun testUiEventRecordTypeResolution() {
        val clickName = UiEventRecord.resolveEventTypeName(AccessibilityEvent.TYPE_VIEW_CLICKED)
        assertEquals("CLICK", clickName)

        val windowChangeName = UiEventRecord.resolveEventTypeName(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        assertEquals("WINDOW_STATE_CHANGED", windowChangeName)

        val scrollName = UiEventRecord.resolveEventTypeName(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        assertEquals("VIEW_SCROLLED", scrollName)

        val notifName = UiEventRecord.resolveEventTypeName(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
        assertEquals("NOTIFICATION", notifName)
    }

    @Test
    fun testInteractiveElementCenterCalculations() {
        val rect = Rect(100, 200, 300, 400)
        val elem = InteractiveElement(
            id = "btn_1",
            text = "Submit",
            contentDescription = "",
            viewId = "com.test:id/submit_button",
            className = "android.widget.Button",
            bounds = rect,
            isClickable = true,
            isEditable = false,
            isScrollable = false,
            isFocused = false
        )

        assertEquals(200f, elem.centerX, 0.01f)
        assertEquals(300f, elem.centerY, 0.01f)
        assertEquals("Submit", elem.displayName)
    }

    @Test
    fun testServiceInitialStateWhenDisconnected() {
        assertFalse(MaxAccessibilityService.isServiceRunning())
        assertEquals("", MaxAccessibilityService.currentPackage.value)
        assertEquals(0, MaxAccessibilityService.recentEvents.value.size)
    }
}
