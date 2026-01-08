package app.brostop.android

/**
 * Centralized constants for SharedPreferences keys and app configurations.
 * All persistent data keys are defined here for easy maintenance.
 */
object PrefsConstants {
    // Preferences file name
    const val PREFS_NAME = "BroStopPrefs"

    // Setup & User Profile
    const val KEY_IS_SETUP_DONE = "IS_SETUP_DONE"
    const val KEY_USER_NAME = "USER_NAME"
    const val KEY_USER_GOAL = "USER_GOAL"
    const val KEY_USER_GENDER = "USER_GENDER"  // "he" or "she"
    const val KEY_USER_LANG = "USER_LANG"      // language code: "en", "hi", etc.

    // Personalization
    const val KEY_USER_HOBBIES = "USER_HOBBIES"      // StringSet of user hobbies
    const val KEY_USER_OCCUPATION = "USER_OCCUPATION"
    const val KEY_USER_HUMOR_STYLES = "USER_HUMOR_STYLES"  // StringSet of humor preferences

    // App Blocking Configuration
    const val KEY_BLOCKED_PACKAGES = "BLOCKED_PACKAGES"  // StringSet of package names to block

    // Swipe/Scroll Limits (randomized per session)
    const val KEY_MIN_SWIPE = "MIN_SWIPE"          // minimum swipes before intervention
    const val KEY_MAX_SWIPE = "MAX_SWIPE"          // maximum swipes before intervention
    const val KEY_MIN_TIME = "MIN_TIME"            // minimum time limit in minutes
    const val KEY_MAX_TIME = "MAX_TIME"            // maximum time limit in minutes

    // Penalty System
    const val KEY_PENALTY_TIME = "PENALTY_TIME"           // lockdown duration in minutes
    const val KEY_GLOBAL_LOCKDOWN = "GLOBAL_LOCKDOWN"     // true = global, false = per-app
    const val KEY_UNBLOCK_TIME = "UNBLOCK_TIME"           // global penalty timestamp
    const val KEY_APP_PENALTY_PREFIX = "PENALTY_TIME_"    // per-app penalty prefix (append package name)

    // Plea Bargain System
    const val KEY_PLEA_ENABLED = "PLEA_ENABLED"      // enable/disable plea bargain feature
    const val KEY_BONUS_DURATION = "BONUS_DURATION"  // bonus time in minutes if user wins plea
    const val KEY_MERCY_USED = "MERCY_USED"          // tracks if user already used mercy this session

    // Emergency Unlock Mode
    const val KEY_EMERGENCY_MODE = "EMERGENCY_MODE"  // "weak", "shame", or "impossible"

    // User Preferences
    const val KEY_VIBRATION_ENABLED = "VIBRATION_ENABLED"  // vibration feedback toggle
}

/**
 * Accessibility service configuration constants
 */
object ServiceConstants {
    const val DEBOUNCE_SWIPE = 600L  // debounce scroll events by 600ms
}

/**
 * Logcat tag prefixes for debugging
 */
object LogTags {
    const val SWIPE = "BroStop_Swipe"
    const val APP_SWITCH = "BroStop_AppSwitch"
    const val MONITOR = "BroStop_Monitor"
    const val ROULETTE = "BroStop_Roulette"
    const val INTERVENTION = "BroStop_Intervention"
    const val PENALTY = "BroStop_Penalty"
    const val ROAST = "BroStop_Roast"
    const val PLEA = "BroStop_Plea"
}

/**
 * Default values for settings
 */
object Defaults {
    const val DEFAULT_GENDER = "he"
    const val DEFAULT_LANGUAGE = "en"
    const val DEFAULT_EMERGENCY_MODE = "weak"
    const val DEFAULT_MIN_SWIPE = 20
    const val DEFAULT_MAX_SWIPE = 50
    const val DEFAULT_MIN_TIME = 5       // minutes
    const val DEFAULT_MAX_TIME = 15      // minutes
    const val DEFAULT_PENALTY_TIME = 10  // minutes
    const val DEFAULT_BONUS_DURATION = 5 // minutes
    const val DEFAULT_PLEA_ENABLED = true
    const val DEFAULT_VIBRATION_ENABLED = true
    const val DEFAULT_GLOBAL_LOCKDOWN = false
}
