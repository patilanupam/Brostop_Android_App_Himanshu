package app.brostop.android

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import kotlin.random.Random

/**
 * Core AccessibilityService for monitoring app usage and enforcing screen time limits.
 * 
 * Responsibilities:
 * - Monitor app switching (TYPE_WINDOW_STATE_CHANGED)
 * - Track scrolling/swiping (TYPE_VIEW_SCROLLED)
 * - Manage sessions with randomized limits
 * - Display interventions (roasts, lockdowns)
 * - Handle penalty system (global or per-app)
 */
class ScrollGuardService : AccessibilityService() {

    // ===== STATE VARIABLES =====
    private var scrollCounter = 0              // Current session scroll count
    private var swipeLimit = 0                 // Randomized limit for current session
    private var timeLimitMillis = 0L           // Randomized time limit for current session
    private var lastScrollTime = 0L            // Timestamp of last scroll event (for debouncing)
    private var lastScrollEventType = 0        // Last scroll event type to prevent double-counting

    private var isRoastActive = false          // Overlay showing roast/plea bargain
    private var isJailActive = false           // Lockdown screen active
    private var isExiting = false              // Service shutdown flag

    private var currentTargetPackage = ""      // Currently monitored app package
    private var memes: List<Meme> = emptyList() // Loaded roast data
    private var blockedPackages: Set<String> = emptySet() // Apps user selected to block

    private var shameSentence = ""             // Current shame protocol sentence

    // ===== SYSTEM RESOURCES =====
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val timeHandler = Handler(Looper.getMainLooper())
    private var countdownTimer: CountDownTimer? = null
    private var vibrator: Vibrator? = null

    private val timerRunnable = Runnable {
        triggerIntervention("Time Limit Reached")
    }

    // ===== LIFECYCLE =====

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Check overlay permission
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        Log.i(LogTags.APP_SWITCH, "🔧 ScrollGuardService initializing... Overlay permission: $hasOverlayPermission")

        // Load memes from JSON asset
        try {
            val stream = assets.open("memes.json")
            val type = object : TypeToken<List<Meme>>() {}.type
            memes = Gson().fromJson(InputStreamReader(stream), type)
            Log.d(LogTags.ROAST, "📚 Loaded ${memes.size} memes from JSON")
        } catch (e: Exception) {
            Log.e(LogTags.ROAST, "❌ Failed to load memes.json", e)
        }

        updateBlockedList()
        Log.i(LogTags.APP_SWITCH, "✅ ScrollGuardService initialized - Blocking ${blockedPackages.size} apps")
        Log.i(LogTags.APP_SWITCH, "   Blocked apps: $blockedPackages")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == null || isExiting) return

        val pkgName = event.packageName.toString()

        // Handle app switching
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleAppSwitch(pkgName)
        }

        // Handle scroll detection - ONLY count TYPE_VIEW_SCROLLED (actual user scroll gestures)
        // WINDOW_CONTENT_CHANGED fires for internal navigation (stories, tabs) and causes false counts
        val isScrollEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        
        if (isScrollEvent) {
            // CRITICAL FIX: Only check scroll movement on API 28+ (where scrollDelta properties exist)
            // On API 26-27, these properties return 0, causing all scrolls to be filtered out
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val scrollDeltaX = event.scrollDeltaX
                val scrollDeltaY = event.scrollDeltaY
                val hasActualMovement = scrollDeltaX != 0 || scrollDeltaY != 0

                if (!hasActualMovement) {
                    Log.v(LogTags.SWIPE, "⏸️ Scroll event ignored (no movement detected - user holding screen)")
                    return
                }
            }
            // On API 26-27: Skip movement check, rely on TYPE_VIEW_SCROLLED event type + debouncing

            // Count scrolls if we have an active monitoring session
            val hasActiveSession = currentTargetPackage.isNotEmpty() && !isRoastActive && !isJailActive
            
            if (hasActiveSession) {
                val now = System.currentTimeMillis()
                val timeSinceLastScroll = now - lastScrollTime
                
                // Prevent double-counting: ignore if same event type within debounce window
                if (timeSinceLastScroll < ServiceConstants.DEBOUNCE_SWIPE && 
                    event.eventType == lastScrollEventType) {
                    Log.v(LogTags.SWIPE, "⏭️ Debounced scroll (same event within ${ServiceConstants.DEBOUNCE_SWIPE}ms)")
                    return
                }
                
                // Also ignore if different event type but within 100ms (same gesture)
                if (timeSinceLastScroll < 100 && event.eventType != lastScrollEventType) {
                    Log.v(LogTags.SWIPE, "⏭️ Debounced scroll (different event within 100ms)")
                    return
                }
                
                lastScrollTime = now
                lastScrollEventType = event.eventType

                scrollCounter++
                val progress = if (swipeLimit > 0) (scrollCounter * 100 / swipeLimit) else 0
                val remaining = swipeLimit - scrollCounter
                
                Log.d(LogTags.SWIPE, "📊 SWIPE #$scrollCounter | Limit: $swipeLimit | Progress: $progress% | Remaining: $remaining")
                Log.d(LogTags.SWIPE, "   ├── Target: $currentTargetPackage | Event: ${eventTypeToString(event.eventType)}")
                Log.d(LogTags.SWIPE, "   ├── Package: $pkgName")
                Log.d(LogTags.SWIPE, "   └── Status: ${if (remaining > 0) "$remaining swipes until limit" else "LIMIT REACHED!"}")

                if (scrollCounter >= swipeLimit) {
                    Log.w(LogTags.SWIPE, "🚨 ⚠️ SWIPE LIMIT REACHED! ⚠️")
                    Log.w(LogTags.SWIPE, "   ├── scrollCounter=$scrollCounter >= swipeLimit=$swipeLimit")
                    Log.w(LogTags.SWIPE, "   ├── Package: $currentTargetPackage")
                    Log.w(LogTags.SWIPE, "   └── Triggering intervention...")
                    triggerIntervention("Swipe Limit Reached")
                }
            } else {
                Log.v(LogTags.SWIPE, "⏸️ Scroll event ignored (no active session): target='$currentTargetPackage', roastActive=$isRoastActive, jailActive=$isJailActive")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(LogTags.APP_SWITCH, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LogTags.APP_SWITCH, "Service destroyed")
        cleanupOverlayUI()
        stopMonitoring()
    }

    // ===== CORE LOGIC: APP SWITCHING & SESSION MANAGEMENT =====

    /**
     * Handles app switching events.
     * Rule 1: Session Preservation - temporary system windows (notifications, volume) don't break monitoring
     * Rule 2: Penalty Check - check if target app has active lockdown
     * Rule 3: Session Start - initialize new monitoring session if clean entry
     */
    private fun handleAppSwitch(pkgName: String) {
        Log.d(LogTags.APP_SWITCH, "🔄 App switch detected: $pkgName")

        // Step 1: Filter out system packages and keyboards
        if (isExiting) return
        if (pkgName == packageName) {
            Log.d(LogTags.APP_SWITCH, "↩️ Ignoring self package")
            return
        }

        val defaultKeyboard = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.split("/")?.get(0)
        if (pkgName == defaultKeyboard || pkgName.contains("keyboard", true) || pkgName.contains("ime", true)) {
            Log.d(LogTags.APP_SWITCH, "↩️ Ignoring keyboard")
            return
        }

        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        updateBlockedList()
        val isAppOnBlocklist = blockedPackages.contains(pkgName)

        // Step 2: Handle non-targeted apps
        if (!isAppOnBlocklist) {
            // CRITICAL FIX: If lockdown overlay is showing, ALWAYS hide it when user goes to home/non-blocked app
            // This fixes the bug where overlay stays visible even after pressing home button
            if (isJailActive || isRoastActive) {
                Log.d(LogTags.APP_SWITCH, "🏠 User left to non-blocked app while overlay active - hiding overlay")
                stopMonitoring()
                cleanupOverlayUI()
                // Keep penalty timestamp - will re-show if they return to blocked app
                currentTargetPackage = ""
                return
            }

            // If we have an active session (but no overlay), this is a temporary window (notification, volume slider, dialog)
            // Rule 1: Session Preservation - don't stop monitoring for temporary windows
            if (currentTargetPackage.isNotEmpty()) {
                Log.d(LogTags.APP_SWITCH, "🔔 Temporary window ($pkgName), preserving session for $currentTargetPackage")
                return
            }

            // User switched to a non-blocked app - clean up everything
            Log.d(LogTags.APP_SWITCH, "↩️ Non-blocked app, stopping monitoring")
            stopMonitoring()
            cleanupOverlayUI()
            currentTargetPackage = ""
            return
        }

        // Step 3: Targeted app detected
        Log.i(LogTags.APP_SWITCH, "🎯 Blocked app detected: $pkgName")
        currentTargetPackage = pkgName

        // Step 4: Check for active penalties (Rule 2)
        val isGlobalLockdown = prefs.getBoolean(PrefsConstants.KEY_GLOBAL_LOCKDOWN, Defaults.DEFAULT_GLOBAL_LOCKDOWN)

        if (isGlobalLockdown) {
            // Global mode: one penalty applies to all apps
            val globalUnblockTime = prefs.getLong(PrefsConstants.KEY_UNBLOCK_TIME, 0L)
            if (System.currentTimeMillis() < globalUnblockTime) {
                Log.i(LogTags.PENALTY, "🔒 Global penalty active, entering lockdown")
                cleanupOverlayUI()
                enterLockdownState(globalUnblockTime, isGlobal = true)
                return
            }
        } else {
            // Per-app mode: each app has independent penalty
            val appUnblockTime = prefs.getLong("${PrefsConstants.KEY_APP_PENALTY_PREFIX}$pkgName", 0L)
            if (System.currentTimeMillis() < appUnblockTime) {
                Log.i(LogTags.PENALTY, "🔒 App-specific penalty active for $pkgName, entering lockdown")
                cleanupOverlayUI()
                enterLockdownState(appUnblockTime, isGlobal = false)
                return
            }
        }

        // Step 5: Check if already monitoring this package (preserve session for internal navigation)
        if (currentTargetPackage == pkgName && currentTargetPackage.isNotEmpty()) {
            Log.d(LogTags.MONITOR, "🔄 Already monitoring $pkgName, preserving session (scrolls: $scrollCounter/$swipeLimit)")
            // Don't reset - user is still in the same app (button taps, internal screens)
            return
        }
        
        // Step 6: No active penalty - start fresh session (Rule 3)
        Log.d(LogTags.MONITOR, "✅ No penalty, starting fresh monitoring session for $pkgName")
        resetRoulette()
        startMonitoring()
    }

    private fun updateBlockedList() {
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        blockedPackages = prefs.getStringSet(PrefsConstants.KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    // ===== MONITORING & SESSION MANAGEMENT =====

    private fun startMonitoring() {
        timeHandler.removeCallbacks(timerRunnable)
        timeHandler.postDelayed(timerRunnable, timeLimitMillis)
        Log.d(LogTags.MONITOR, "▶️ Monitoring started for $currentTargetPackage")
        Log.d(LogTags.MONITOR, "   ├── Time Limit: ${timeLimitMillis / 1000}s (${timeLimitMillis / 60000} minutes)")
        Log.d(LogTags.MONITOR, "   ├── Swipe Limit: $swipeLimit swipes")
        Log.d(LogTags.MONITOR, "   └── Current swipes: $scrollCounter")
    }

    private fun stopMonitoring() {
        timeHandler.removeCallbacks(timerRunnable)
        Log.d(LogTags.MONITOR, "⏹️ Monitoring stopped")
    }

    /**
     * Randomizes swipe and time limits for the current session.
     * Each time user enters a monitored app, new random limits are set.
     */
    private fun resetRoulette() {
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val minSwipe = prefs.getInt(PrefsConstants.KEY_MIN_SWIPE, Defaults.DEFAULT_MIN_SWIPE)
        val maxSwipe = prefs.getInt(PrefsConstants.KEY_MAX_SWIPE, Defaults.DEFAULT_MAX_SWIPE)
        val minTime = prefs.getInt(PrefsConstants.KEY_MIN_TIME, Defaults.DEFAULT_MIN_TIME)
        val maxTime = prefs.getInt(PrefsConstants.KEY_MAX_TIME, Defaults.DEFAULT_MAX_TIME)

        // Ensure valid ranges (minimum 1 swipe, 1 minute)
        val validMinSwipe = maxOf(1, minSwipe)
        val validMaxSwipe = maxOf(validMinSwipe, maxSwipe)
        val validMinTime = maxOf(1, minTime)
        val validMaxTime = maxOf(validMinTime, maxTime)

        swipeLimit = (validMinSwipe..validMaxSwipe).random()
        timeLimitMillis = (validMinTime..validMaxTime).random() * 60 * 1000L
        scrollCounter = 0

        // Reset mercy/plea bargain for this session
        prefs.edit().putBoolean(PrefsConstants.KEY_MERCY_USED, false).apply()

        Log.i(LogTags.ROULETTE, "🎲 SESSION INITIALIZED for $currentTargetPackage")
        Log.i(LogTags.ROULETTE, "   ├─ Swipe Limit: $swipeLimit (range: $validMinSwipe-$validMaxSwipe)")
        Log.i(LogTags.ROULETTE, "   ├─ Time Limit: ${timeLimitMillis / 1000}s (range: $validMinTime-$validMaxTime mins)")
        Log.i(LogTags.ROULETTE, "   ├─ Scroll Counter: $scrollCounter (RESET TO ZERO)")
        Log.i(LogTags.ROULETTE, "   └─ User needs ${swipeLimit} swipes OR ${timeLimitMillis / 60000} minutes to trigger intervention")
    }

    // ===== INTERVENTIONS =====

    /**
     * Triggered when scroll limit or time limit reached.
     * Shows roast overlay with plea bargain option.
     */
    private fun triggerIntervention(reason: String) {
        Log.w(LogTags.INTERVENTION, "🛑 ═══════════════════════════════════════")
        Log.w(LogTags.INTERVENTION, "🛑 INTERVENTION TRIGGERED!")
        Log.w(LogTags.INTERVENTION, "🛑 ═══════════════════════════════════════")
        Log.w(LogTags.INTERVENTION, "   ├── Reason: $reason")
        Log.w(LogTags.INTERVENTION, "   ├── Package: $currentTargetPackage")
        Log.w(LogTags.INTERVENTION, "   ├── Scroll Count: $scrollCounter / $swipeLimit")
        Log.w(LogTags.INTERVENTION, "   ├── Time Limit: ${timeLimitMillis / 1000}s")
        Log.w(LogTags.INTERVENTION, "   └── Trigger Type: ${if (reason.contains("Swipe")) "SWIPE LIMIT" else "TIME LIMIT"}")
        stopMonitoring()

        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

        // Vibrate if enabled
        if (prefs.getBoolean(PrefsConstants.KEY_VIBRATION_ENABLED, Defaults.DEFAULT_VIBRATION_ENABLED)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }
        }

        // Apply penalty (saves unblock time to SharedPreferences)
        val isGlobal = applyPenaltyTimestamp()
        Log.i(LogTags.PENALTY, "🔒 Penalty applied (Global: $isGlobal)")

        // Preserve package name before clearing session
        val preservedTarget = currentTargetPackage
        
        // Clear session to stop counting swipes
        currentTargetPackage = ""
        scrollCounter = 0
        
        // Show roast overlay with plea bargain, passing preserved target for per-app penalties
        showRoastOverlay(isGlobal, preservedTarget)
    }

    // ===== ROAST SYSTEM =====

    /**
     * Displays personalized roast with plea bargain option if enabled.
     */
    private fun showRoastOverlay(isGlobal: Boolean, preservedTarget: String) {
        if (overlayView != null) return

        isRoastActive = true
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_roast, null)

        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val gender = prefs.getString(PrefsConstants.KEY_USER_GENDER, Defaults.DEFAULT_GENDER) ?: Defaults.DEFAULT_GENDER
        val isPleaEnabled = prefs.getBoolean(PrefsConstants.KEY_PLEA_ENABLED, Defaults.DEFAULT_PLEA_ENABLED)
        val mercyUsed = prefs.getBoolean(PrefsConstants.KEY_MERCY_USED, false)

        // Set title based on gender
        val tvTitle = overlayView!!.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = if (gender == "he") "🛑 BRO STOP." else "🛑 SIS STOP."

        // Set personalized roast
        val tvRoast = overlayView!!.findViewById<TextView>(R.id.tvRoastMessage)
        tvRoast.text = getSmartRoast()

        val btnOkay = overlayView!!.findViewById<Button>(R.id.btnOkay)
        val layoutPlea = overlayView!!.findViewById<LinearLayout>(R.id.layoutPleaButtons)
        val tvBonusInfo = overlayView!!.findViewById<TextView>(R.id.tvBonusInfo)

        // Show plea bargain if enabled and not used yet this session
        if (isPleaEnabled && !mercyUsed) {
            btnOkay.visibility = View.GONE
            layoutPlea.visibility = View.VISIBLE

            val bonusDuration = prefs.getInt(PrefsConstants.KEY_BONUS_DURATION, Defaults.DEFAULT_BONUS_DURATION)
            tvBonusInfo.visibility = View.VISIBLE
            tvBonusInfo.text = "🎲 Win: Get $bonusDuration extra minutes • 💀 Lose: Instant lockdown"

            val btnPleaNo = overlayView!!.findViewById<Button>(R.id.btnPleaNo)
            val btnPleaYes = overlayView!!.findViewById<Button>(R.id.btnPleaYes)

            btnPleaNo.setOnClickListener {
                cleanupOverlayUI()
                exitApp()
            }

            btnPleaYes.setOnClickListener {
                handlePleaBargain(bonusDuration, isGlobal, preservedTarget)
            }
        } else {
            btnOkay.visibility = View.VISIBLE
            layoutPlea.visibility = View.GONE
            tvBonusInfo.visibility = View.GONE

            btnOkay.setOnClickListener {
                cleanupOverlayUI()
                exitApp()
            }
        }

        addOverlayToWindow(needsInput = false)
    }

    /**
     * Selects a personalized roast based on user's language, gender, humor style, hobbies, and occupation.
     * All matching roasts are equally weighted (no priority system).
     */
    private fun getSmartRoast(tag: String? = null): String {
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(PrefsConstants.KEY_USER_LANG, Defaults.DEFAULT_LANGUAGE) ?: Defaults.DEFAULT_LANGUAGE
        val userGender = prefs.getString(PrefsConstants.KEY_USER_GENDER, Defaults.DEFAULT_GENDER) ?: Defaults.DEFAULT_GENDER
        val userHumorStyles = prefs.getStringSet(PrefsConstants.KEY_USER_HUMOR_STYLES, emptySet()) ?: emptySet()

        // Filter by language first
        var filteredMemes = memes.filter { it.language == lang }.ifEmpty { memes.filter { it.language == "en" } }

        // If explicit tag provided (for plea scenarios), use it
        if (tag != null) {
            val explicitMemes = filteredMemes.filter { it.tags.contains(tag) }
            return explicitMemes.randomOrNull()?.text ?: "You've reached your limit. Time to take a break!"
        }

        // Filter by gender
        filteredMemes = filteredMemes.filter { it.tags.contains(userGender) }

        // Filter by humor styles
        if (userHumorStyles.isNotEmpty()) {
            filteredMemes = filteredMemes.filter { meme ->
                userHumorStyles.any { humor -> meme.tags.contains(humor.lowercase()) }
            }
        }

        // Collect memes matching hobbies and occupation (equal weighting, no priority)
        val userHobbies = prefs.getStringSet(PrefsConstants.KEY_USER_HOBBIES, emptySet()) ?: emptySet()
        val userOccupation = prefs.getString(PrefsConstants.KEY_USER_OCCUPATION, "") ?: ""

        val matchingMemes = mutableSetOf<Meme>()

        // Add hobby-matching memes
        if (userHobbies.isNotEmpty()) {
            matchingMemes.addAll(
                filteredMemes.filter { meme ->
                    userHobbies.any { hobby -> meme.tags.contains(hobby.lowercase()) }
                }
            )
        }

        // Add occupation-matching memes
        if (userOccupation.isNotEmpty()) {
            matchingMemes.addAll(
                filteredMemes.filter { it.tags.contains(userOccupation.lowercase()) }
            )
        }

        // Return random from matching set, or fallback to filtered meme
        return if (matchingMemes.isNotEmpty()) {
            Log.d(LogTags.ROAST, "Found ${matchingMemes.size} roasts matching language, gender, humor, hobbies/occupation")
            matchingMemes.random().text
        } else {
            Log.d(LogTags.ROAST, "No personalized matches, using random meme from language: $lang")
            filteredMemes.randomOrNull()?.text ?: "You've reached your limit. Time to take a break!"
        }
    }

    /**
     * Plea bargain system: user gets 20% chance to win and extend session.
     */
    private fun handlePleaBargain(bonusDuration: Int, isGlobal: Boolean, preservedTarget: String) {
        Log.d(LogTags.PLEA, "🎲 Plea bargain initiated")
        
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PrefsConstants.KEY_MERCY_USED, true).apply()

        val wonGamble = Random.nextInt(100) < 20 // 20% chance

        val tvRoast = overlayView!!.findViewById<TextView>(R.id.tvRoastMessage)
        val layoutPlea = overlayView!!.findViewById<LinearLayout>(R.id.layoutPleaButtons)
        val tvBonusInfo = overlayView!!.findViewById<TextView>(R.id.tvBonusInfo)
        layoutPlea.visibility = View.GONE
        tvBonusInfo.visibility = View.GONE

        if (wonGamble) {
            Log.i(LogTags.PLEA, "✅ User WON plea bargain (20% chance)")
            tvRoast.text = getSmartRoast("plea_won")
            tvRoast.setTextColor(Color.GREEN)

            Handler(Looper.getMainLooper()).postDelayed({
                performPardon(isGlobal, if (!isGlobal) preservedTarget else null)
                // Grant bonus swipes and time (without resetting counter)
                swipeLimit = scrollCounter + 30
                timeLimitMillis = bonusDuration * 60 * 1000L
                cleanupOverlayUI()
                startMonitoring()
            }, 2000)
        } else {
            Log.i(LogTags.PLEA, "❌ User LOST plea bargain (80% chance)")
            tvRoast.text = getSmartRoast("2nd_chance_fail")
            tvRoast.setTextColor(Color.RED)

            Handler(Looper.getMainLooper()).postDelayed({
                cleanupOverlayUI()
                exitApp()
            }, 2000)
        }
    }

    // ===== LOCKDOWN SYSTEM =====

    /**
     * Displays lockdown screen with countdown timer.
     * Supports three emergency modes: weak (tap), shame (confession), impossible (wait).
     */
    private fun enterLockdownState(unblockTime: Long, isGlobal: Boolean) {
        if (overlayView != null) return

        Log.i(LogTags.PENALTY, "🔓 Entering lockdown state")
        isJailActive = true
        stopMonitoring()

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_blocked, null)

        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val emergencyMode = prefs.getString(PrefsConstants.KEY_EMERGENCY_MODE, Defaults.DEFAULT_EMERGENCY_MODE) ?: Defaults.DEFAULT_EMERGENCY_MODE

        val tvTimer = overlayView!!.findViewById<TextView>(R.id.tvTimer)
        val btnEmergency = overlayView!!.findViewById<Button>(R.id.btnEmergency)
        val tvSubtitle = overlayView!!.findViewById<TextView>(R.id.tvSubtitle)

        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(unblockTime - System.currentTimeMillis(), 1000) {
            override fun onTick(ms: Long) {
                val min = (ms / 1000) / 60
                val sec = (ms / 1000) % 60
                tvTimer.text = String.format("%02d:%02d", min, sec)
            }

            override fun onFinish() {
                performPardon(isGlobal, if (!isGlobal) currentTargetPackage else null)
                cleanupOverlayUI()  // Remove overlay before exiting
                exitApp()
            }
        }.start()

        when (emergencyMode) {
            "weak" -> {
                tvSubtitle.text = "(Tap to unlock)"
                btnEmergency.setOnClickListener {
                    // Preserve package name before cleanup
                    val targetPackage = currentTargetPackage
                    
                    countdownTimer?.cancel()
                    performPardon(isGlobal, if (!isGlobal) targetPackage else null)
                    cleanupOverlayUI()
                    
                    // Restore package and reinitialize session
                    currentTargetPackage = targetPackage
                    resetRoulette()
                    startMonitoring()
                }
            }
            "shame" -> {
                tvSubtitle.text = "(Requires Shame Protocol)"
                btnEmergency.setOnClickListener { showShameInput() }
                setupShameProtocol(isGlobal)
            }
            "impossible" -> {
                btnEmergency.visibility = View.GONE
                tvSubtitle.text = "No escape. Wait it out."
            }
        }

        addOverlayToWindow(needsInput = emergencyMode == "shame")
    }

    private fun showShameInput() {
        overlayView?.findViewById<LinearLayout>(R.id.layoutBlockMain)?.visibility = View.GONE
        overlayView?.findViewById<LinearLayout>(R.id.layoutShameInput)?.visibility = View.VISIBLE
    }

    private fun setupShameProtocol(isGlobal: Boolean) {
        val shameSentences = listOf(
            "I am wasting my time scrolling.",
            "I lack self-control and discipline.",
            "I am weak and easily distracted."
        )
        shameSentence = shameSentences.random()

        val tvShameSentence = overlayView?.findViewById<TextView>(R.id.tvShameSentence)
        val etShameInput = overlayView?.findViewById<EditText>(R.id.etShameInput)
        val btnSubmit = overlayView!!.findViewById<Button>(R.id.btnSubmitShame)

        tvShameSentence?.text = shameSentence

        btnSubmit.setOnClickListener {
            if (etShameInput?.text.toString() == shameSentence) {
                // Preserve package name before cleanup
                val targetPackage = currentTargetPackage
                
                countdownTimer?.cancel()
                performPardon(isGlobal, if (!isGlobal) targetPackage else null)
                cleanupOverlayUI()
                
                // Restore package and reinitialize session
                currentTargetPackage = targetPackage
                resetRoulette()
                startMonitoring()
            } else {
                Toast.makeText(this, "❌ Incorrect. Type it exactly.", Toast.LENGTH_SHORT).show()
                etShameInput?.setText("")
            }
        }
    }

    // ===== PENALTY MANAGEMENT =====

    /**
     * Applies penalty timestamp (lockdown duration) to SharedPreferences.
     * Returns whether penalty is global (true) or per-app (false).
     */
    private fun applyPenaltyTimestamp(): Boolean {
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val mins = prefs.getInt(PrefsConstants.KEY_PENALTY_TIME, Defaults.DEFAULT_PENALTY_TIME)
        if (mins <= 0) return false

        val unlockAt = System.currentTimeMillis() + mins * 60 * 1000L
        val editor = prefs.edit()
        val isGlobal = prefs.getBoolean(PrefsConstants.KEY_GLOBAL_LOCKDOWN, Defaults.DEFAULT_GLOBAL_LOCKDOWN)

        if (isGlobal) {
            editor.putLong(PrefsConstants.KEY_UNBLOCK_TIME, unlockAt)
        } else {
            if (currentTargetPackage.isNotEmpty()) {
                editor.putLong("${PrefsConstants.KEY_APP_PENALTY_PREFIX}$currentTargetPackage", unlockAt)
            }
        }
        editor.apply()
        return isGlobal
    }

    /**
     * Removes penalty timestamp (clears lockdown).
     */
    private fun performPardon(isGlobal: Boolean, pkgName: String? = null) {
        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (isGlobal) {
            editor.remove(PrefsConstants.KEY_UNBLOCK_TIME)
        } else if (pkgName != null) {
            editor.remove("${PrefsConstants.KEY_APP_PENALTY_PREFIX}$pkgName")
        }
        editor.apply()

        Log.d(LogTags.PENALTY, "✅ Penalty pardoned")
        stopMonitoring()
        cleanupOverlayUI()
    }

    // ===== UI HELPERS =====

    private fun cleanupOverlayUI() {
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (_: Exception) {}
            overlayView = null
        }
        countdownTimer?.cancel()
        isRoastActive = false
        isJailActive = false
    }

    private fun addOverlayToWindow(needsInput: Boolean) {
        if (overlayView == null) {
            Log.e(LogTags.INTERVENTION, "❌ Cannot add overlay - overlayView is null")
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.e(LogTags.INTERVENTION, "❌ Cannot add overlay - SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        val baseFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        val finalFlags = if (needsInput) {
            baseFlags
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            finalFlags,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.CENTER
        try {
            windowManager?.addView(overlayView, params)
            Log.i(LogTags.INTERVENTION, "✅ Overlay added to window (needsInput: $needsInput)")
        } catch (e: Exception) {
            Log.e(LogTags.INTERVENTION, "❌ Failed to add overlay to window", e)
        }
    }

    private fun exitApp() {
        isExiting = true
        performGlobalAction(GLOBAL_ACTION_HOME)

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses(currentTargetPackage)
            } catch (_: Exception) {}
            isExiting = false
        }, 800)
    }

    private fun eventTypeToString(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "VIEW_SCROLLED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
            else -> "UNKNOWN($eventType)"
        }
    }
}
