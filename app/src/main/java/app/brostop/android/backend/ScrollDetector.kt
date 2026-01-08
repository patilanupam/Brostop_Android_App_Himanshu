package app.brostop.android.backend

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import app.brostop.android.LogTags
import app.brostop.android.ServiceConstants

/**
 * Detects and debounces scroll events.
 * 
 * Responsibilities:
 * - Identify scroll events
 * - Debounce rapid-fire scroll events
 * - Track event timing and types
 */
class ScrollDetector {
    
    private var lastScrollTime: Long = 0L
    private var lastScrollEventType: Int = 0
    
    /**
     * Check if this accessibility event is a scroll
     */
    fun isScrollEvent(event: AccessibilityEvent): Boolean {
        return event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
               event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    }
    
    /**
     * Check if this scroll event should be counted (not debounced)
     * @return true if event should be counted, false if debounced
     */
    fun shouldCountScroll(event: AccessibilityEvent): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLastScroll = now - lastScrollTime
        
        // Prevent double-counting: ignore if same event type within debounce window
        if (timeSinceLastScroll < ServiceConstants.DEBOUNCE_SWIPE && 
            event.eventType == lastScrollEventType) {
            Log.v(LogTags.SWIPE, "⏭️ Debounced (same event within ${ServiceConstants.DEBOUNCE_SWIPE}ms)")
            return false
        }
        
        // Also ignore if different event type but within 100ms (same gesture)
        if (timeSinceLastScroll < 100 && event.eventType != lastScrollEventType) {
            Log.v(LogTags.SWIPE, "⏭️ Debounced (different event within 100ms)")
            return false
        }
        
        // Update tracking
        lastScrollTime = now
        lastScrollEventType = event.eventType
        
        return true
    }
    
    /**
     * Get event type as string for logging
     */
    fun getEventTypeName(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            else -> "UNKNOWN($eventType)"
        }
    }
    
    /**
     * Reset detector state
     */
    fun reset() {
        lastScrollTime = 0L
        lastScrollEventType = 0
    }
}
