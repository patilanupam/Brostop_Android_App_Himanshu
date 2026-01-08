package app.brostop.android.backend

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import app.brostop.android.Defaults
import app.brostop.android.LogTags
import app.brostop.android.PrefsConstants

/**
 * New modular AccessibilityService implementation.
 * 
 * This service orchestrates all backend components to monitor app usage
 * and enforce screen time limits with interventions.
 */
class BroStopService : AccessibilityService() {
    
    // Backend components
    private lateinit var sessionManager: SessionManager
    private lateinit var penaltyManager: PenaltyManager
    private lateinit var appMonitor: AppMonitor
    private lateinit var scrollDetector: ScrollDetector
    private lateinit var memeProvider: MemeProvider
    private lateinit var interventionUI: InterventionUI
    
    // System resources
    private var vibrator: Vibrator? = null
    private val timeHandler = Handler(Looper.getMainLooper())
    
    // State flags
    private var isExiting = false
    
    // Time limit timer
    private val timeLimitRunnable = Runnable {
        triggerIntervention("Time Limit Reached")
    }
    
    override fun onServiceConnected() {
        Log.i(LogTags.APP_SWITCH, "🔧 BroStopService initializing...")
        
        // Initialize components
        sessionManager = SessionManager(this)
        penaltyManager = PenaltyManager(this)
        appMonitor = AppMonitor(this)
        scrollDetector = ScrollDetector()
        memeProvider = MemeProvider(this)
        interventionUI = InterventionUI(this, memeProvider, penaltyManager)
        
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        // Setup intervention UI callbacks
        setupInterventionCallbacks()
        
        Log.i(LogTags.APP_SWITCH, "✅ BroStopService initialized - Blocking ${appMonitor.getBlockedCount()} apps")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == null || isExiting) return
        
        val pkgName = event.packageName.toString()
        
        // Handle app switching
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleAppSwitch(pkgName)
        }
        
        // Handle scroll detection
        if (scrollDetector.isScrollEvent(event)) {
            handleScrollEvent(event, pkgName)
        }
    }
    
    /**
     * Handle app switching logic
     */
    private fun handleAppSwitch(pkgName: String) {
        Log.d(LogTags.APP_SWITCH, "🔄 App switch detected: $pkgName")
        
        // Filter system packages
        if (appMonitor.isSystemPackage(pkgName, packageName)) {
            Log.d(LogTags.APP_SWITCH, "↩️ Ignoring system package")
            return
        }
        
        // Update blocked list (in case user changed settings)
        appMonitor.updateBlockedList()
        
        val isBlocked = appMonitor.isBlocked(pkgName)
        
        // Handle non-blocked apps
        if (!isBlocked) {
            // Check if this is a temporary window
            if (sessionManager.isActive && !appMonitor.isTemporaryWindow(pkgName)) {
                // User switched to real non-blocked app - stop session
                Log.d(LogTags.APP_SWITCH, "↩️ Non-blocked app, stopping session")
                stopMonitoring()
                interventionUI.hide()
                
                // Clear jail flag if user escaped
                // (overlay will re-show when they return to blocked app)
            } else if (sessionManager.isActive) {
                // Temporary window - preserve session
                Log.d(LogTags.APP_SWITCH, "🔔 Temporary window, preserving session")
            }
            return
        }
        
        // Blocked app detected
        Log.i(LogTags.APP_SWITCH, "🎯 Blocked app detected: $pkgName")
        
        // Check for active penalty
        val penaltyTime = penaltyManager.getActivePenalty(pkgName)
        if (penaltyTime != null) {
            val isGlobal = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PrefsConstants.KEY_GLOBAL_LOCKDOWN, Defaults.DEFAULT_GLOBAL_LOCKDOWN)
            
            Log.i(LogTags.PENALTY, "🔒 Penalty active, entering lockdown")
            interventionUI.hide()
            interventionUI.showLockdown(penaltyTime, isGlobal, pkgName)
            return
        }
        
        // No penalty - start new session
        Log.d(LogTags.MONITOR, "✅ No penalty, starting new session")
        sessionManager.startSession(pkgName)
        startMonitoring()
    }
    
    /**
     * Handle scroll events
     */
    private fun handleScrollEvent(event: AccessibilityEvent, pkgName: String) {
        // Only count if we have an active session and no overlay is showing
        if (!sessionManager.isActive || interventionUI.isShowing()) {
            Log.v(LogTags.SWIPE, "⏸️ Scroll ignored (no active session or overlay showing)")
            return
        }
        
        // Check if this scroll should be counted (debouncing)
        if (!scrollDetector.shouldCountScroll(event)) {
            return
        }
        
        // Increment and check limit
        if (sessionManager.incrementScroll()) {
            Log.w(LogTags.SWIPE, "🚨 SWIPE LIMIT REACHED!")
            triggerIntervention("Swipe Limit Reached")
        }
    }
    
    /**
     * Trigger intervention (roast overlay)
     */
    private fun triggerIntervention(reason: String) {
        Log.w(LogTags.INTERVENTION, "🛑 INTERVENTION TRIGGERED: $reason")
        
        stopMonitoring()
        
        // Vibrate if enabled
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PrefsConstants.KEY_VIBRATION_ENABLED, Defaults.DEFAULT_VIBRATION_ENABLED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        }
        
        // Apply penalty
        val isGlobal = penaltyManager.applyPenalty()
        if (!isGlobal) {
            penaltyManager.applyAppPenalty(sessionManager.currentPackage)
        }
        
        // Preserve package before session stops
        val preservedPackage = sessionManager.currentPackage
        
        // Stop session
        sessionManager.stopSession()
        
        // Show roast
        interventionUI.showRoast(isGlobal, preservedPackage)
    }
    
    /**
     * Start monitoring with time limit
     */
    private fun startMonitoring() {
        timeHandler.removeCallbacks(timeLimitRunnable)
        timeHandler.postDelayed(timeLimitRunnable, sessionManager.timeLimitMillis)
        
        Log.d(LogTags.MONITOR, "▶️ Monitoring started")
        Log.d(LogTags.MONITOR, "   ├── Time Limit: ${sessionManager.timeLimitMillis / 1000}s")
        Log.d(LogTags.MONITOR, "   └── Swipe Limit: ${sessionManager.swipeLimit}")
    }
    
    /**
     * Stop monitoring
     */
    private fun stopMonitoring() {
        timeHandler.removeCallbacks(timeLimitRunnable)
        sessionManager.stopSession()
        Log.d(LogTags.MONITOR, "⏹️ Monitoring stopped")
    }
    
    /**
     * Setup intervention UI callbacks
     */
    private fun setupInterventionCallbacks() {
        interventionUI.onPleaWin = { bonusDuration ->
            val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
            val isGlobal = prefs.getBoolean(PrefsConstants.KEY_GLOBAL_LOCKDOWN, Defaults.DEFAULT_GLOBAL_LOCKDOWN)
            
            // Need to get the preserved package somehow...
            // This is a limitation - we need to pass it through
            // For now, let's restart session with current package
            
            penaltyManager.pardon(isGlobal, sessionManager.currentPackage)
            sessionManager.extendSession(30, bonusDuration)
            startMonitoring()
        }
        
        interventionUI.onPleaLose = {
            exitApp()
        }
        
        interventionUI.onExitApp = {
            exitApp()
        }
        
        interventionUI.onEmergencyUnlock = { isGlobal, packageName ->
            penaltyManager.pardon(isGlobal, packageName)
            sessionManager.startSession(packageName)
            startMonitoring()
        }
    }
    
    /**
     * Exit the blocked app
     */
    private fun exitApp() {
        isExiting = true
        val targetPackage = sessionManager.currentPackage
        
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(targetPackage)
            } catch (_: Exception) {}
            isExiting = false
        }, 800)
    }
    
    override fun onInterrupt() {
        Log.d(LogTags.APP_SWITCH, "Service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(LogTags.APP_SWITCH, "Service destroyed")
        interventionUI.hide()
        stopMonitoring()
    }
}
