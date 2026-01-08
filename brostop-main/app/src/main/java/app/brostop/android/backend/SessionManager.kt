package app.brostop.android.backend

import android.content.Context
import android.util.Log
import app.brostop.android.Defaults
import app.brostop.android.LogTags
import app.brostop.android.PrefsConstants

/**
 * Manages monitoring sessions with randomized limits.
 * 
 * Responsibilities:
 * - Track scroll counts
 * - Maintain session state (active package, limits, counters)
 * - Determine when limits are breached
 */
class SessionManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
    
    // Session state
    var currentPackage: String = ""
        private set
    var scrollCount: Int = 0
        private set
    var swipeLimit: Int = 0
        private set
    var timeLimitMillis: Long = 0L
        private set
    
    // Session status flags
    var isActive: Boolean = false
        private set
    
    /**
     * Start a new monitoring session for the given package
     */
    fun startSession(packageName: String) {
        currentPackage = packageName
        
        // Randomize limits from user preferences
        val minSwipe = prefs.getInt(PrefsConstants.KEY_MIN_SWIPE, Defaults.DEFAULT_MIN_SWIPE)
        val maxSwipe = prefs.getInt(PrefsConstants.KEY_MAX_SWIPE, Defaults.DEFAULT_MAX_SWIPE)
        val minTime = prefs.getInt(PrefsConstants.KEY_MIN_TIME, Defaults.DEFAULT_MIN_TIME)
        val maxTime = prefs.getInt(PrefsConstants.KEY_MAX_TIME, Defaults.DEFAULT_MAX_TIME)
        
        val validMinSwipe = maxOf(1, minSwipe)
        val validMaxSwipe = maxOf(validMinSwipe, maxSwipe)
        val validMinTime = maxOf(1, minTime)
        val validMaxTime = maxOf(validMinTime, maxTime)
        
        swipeLimit = (validMinSwipe..validMaxSwipe).random()
        timeLimitMillis = (validMinTime..validMaxTime).random() * 60 * 1000L
        scrollCount = 0
        isActive = true
        
        // Reset mercy for this session
        prefs.edit().putBoolean(PrefsConstants.KEY_MERCY_USED, false).apply()
        
        Log.i(LogTags.ROULETTE, "🎲 SESSION STARTED for $packageName")
        Log.i(LogTags.ROULETTE, "   ├─ Swipe Limit: $swipeLimit (range: $validMinSwipe-$validMaxSwipe)")
        Log.i(LogTags.ROULETTE, "   ├─ Time Limit: ${timeLimitMillis / 1000}s")
        Log.i(LogTags.ROULETTE, "   └─ Scroll Counter: $scrollCount (RESET)")
    }
    
    /**
     * Increment scroll counter and check if limit reached
     * @return true if limit breached
     */
    fun incrementScroll(): Boolean {
        if (!isActive) return false
        
        scrollCount++
        val progress = if (swipeLimit > 0) (scrollCount * 100 / swipeLimit) else 0
        val remaining = swipeLimit - scrollCount
        
        Log.d(LogTags.SWIPE, "📊 SWIPE #$scrollCount | Limit: $swipeLimit | Progress: $progress% | Remaining: $remaining")
        
        return scrollCount >= swipeLimit
    }
    
    /**
     * Extend session limits (for plea bargain wins)
     */
    fun extendSession(bonusSwipes: Int, bonusTimeMinutes: Int) {
        swipeLimit = scrollCount + bonusSwipes
        timeLimitMillis = bonusTimeMinutes * 60 * 1000L
        
        Log.i(LogTags.PLEA, "✨ Session extended: +$bonusSwipes swipes, +$bonusTimeMinutes minutes")
        Log.i(LogTags.PLEA, "   New limits: $swipeLimit swipes, ${timeLimitMillis / 60000} minutes")
    }
    
    /**
     * Stop the current session
     */
    fun stopSession() {
        if (!isActive) return
        
        Log.d(LogTags.MONITOR, "⏹️ Session stopped for $currentPackage")
        isActive = false
        currentPackage = ""
        scrollCount = 0
        swipeLimit = 0
        timeLimitMillis = 0L
    }
    
    /**
     * Check if this package is currently being monitored
     */
    fun isMonitoring(packageName: String): Boolean {
        return isActive && currentPackage == packageName
    }
    
    /**
     * Get current session progress percentage
     */
    fun getProgress(): Int {
        return if (swipeLimit > 0) (scrollCount * 100 / swipeLimit) else 0
    }
}
