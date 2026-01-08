package app.brostop.android.backend

import android.content.Context
import android.provider.Settings
import android.util.Log
import app.brostop.android.LogTags
import app.brostop.android.PrefsConstants

/**
 * Monitors app switching and determines which apps to track.
 * 
 * Responsibilities:
 * - Maintain list of blocked packages
 * - Filter system packages and keyboards
 * - Detect app switches vs temporary windows
 */
class AppMonitor(private val context: Context) {
    
    private val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
    private var blockedPackages: Set<String> = emptySet()
    
    init {
        updateBlockedList()
    }
    
    /**
     * Reload blocked packages from preferences
     */
    fun updateBlockedList() {
        blockedPackages = prefs.getStringSet(PrefsConstants.KEY_BLOCKED_PACKAGES, emptySet()) 
            ?: emptySet()
        Log.d(LogTags.APP_SWITCH, "📋 Updated blocked list: ${blockedPackages.size} apps")
    }
    
    /**
     * Check if package should be monitored
     */
    fun isBlocked(packageName: String): Boolean {
        return blockedPackages.contains(packageName)
    }
    
    /**
     * Check if package is a system package that should be ignored
     */
    fun isSystemPackage(packageName: String, selfPackage: String): Boolean {
        // Ignore self
        if (packageName == selfPackage) {
            return true
        }
        
        // Ignore keyboard
        val defaultKeyboard = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )?.split("/")?.get(0)
        
        if (packageName == defaultKeyboard || 
            packageName.contains("keyboard", true) || 
            packageName.contains("ime", true)) {
            return true
        }
        
        return false
    }
    
    /**
     * Determine if this is a temporary window (notification, volume, etc.)
     * vs a real app switch
     */
    fun isTemporaryWindow(packageName: String): Boolean {
        // Common temporary system windows
        val temporaryPrefixes = listOf(
            "com.android.systemui",
            "com.vivo.upslide",
            "com.vivo.daemonService",
            "com.vivo.fingerprintui",
            "com.google.android.googlequicksearchbox"
        )
        
        return temporaryPrefixes.any { packageName.startsWith(it) }
    }
    
    /**
     * Get list of blocked packages
     */
    fun getBlockedPackages(): Set<String> = blockedPackages
    
    /**
     * Get count of blocked apps
     */
    fun getBlockedCount(): Int = blockedPackages.size
}
