package app.brostop.android.backend

import android.content.Context
import android.util.Log
import app.brostop.android.Defaults
import app.brostop.android.LogTags
import app.brostop.android.PrefsConstants

/**
 * Manages penalty timestamps and lockdown state.
 * 
 * Responsibilities:
 * - Apply penalties (global or per-app)
 * - Check if penalties are active
 * - Pardon/remove penalties
 */
class PenaltyManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Apply a penalty timestamp
     * @return true if global lockdown, false if app-specific
     */
    fun applyPenalty(): Boolean {
        val penaltyMinutes = prefs.getInt(PrefsConstants.KEY_PENALTY_TIME, Defaults.DEFAULT_PENALTY_TIME)
        
        // If penalty is 0, interventions are disabled
        if (penaltyMinutes == 0) {
            Log.i(LogTags.PENALTY, "⚠️ Penalty disabled (0 minutes)")
            return false
        }
        
        val isGlobal = prefs.getBoolean(PrefsConstants.KEY_GLOBAL_LOCKDOWN, Defaults.DEFAULT_GLOBAL_LOCKDOWN)
        val unblockTime = System.currentTimeMillis() + (penaltyMinutes * 60 * 1000L)
        
        Log.i(LogTags.PENALTY, "🔒 Penalty applied: $penaltyMinutes minutes (Global: $isGlobal)")
        
        prefs.edit().apply {
            if (isGlobal) {
                putLong(PrefsConstants.KEY_UNBLOCK_TIME, unblockTime)
            }
            apply()
        }
        
        return isGlobal
    }
    
    /**
     * Apply app-specific penalty
     */
    fun applyAppPenalty(packageName: String) {
        val penaltyMinutes = prefs.getInt(PrefsConstants.KEY_PENALTY_TIME, Defaults.DEFAULT_PENALTY_TIME)
        if (penaltyMinutes == 0) return
        
        val unblockTime = System.currentTimeMillis() + (penaltyMinutes * 60 * 1000L)
        
        prefs.edit()
            .putLong("${PrefsConstants.KEY_APP_PENALTY_PREFIX}$packageName", unblockTime)
            .apply()
        
        Log.i(LogTags.PENALTY, "🔒 App penalty applied for $packageName: $penaltyMinutes minutes")
    }
    
    /**
     * Check if package has active penalty
     * @return penalty end time in millis, or null if no penalty
     */
    fun getActivePenalty(packageName: String): Long? {
        val isGlobal = prefs.getBoolean(PrefsConstants.KEY_GLOBAL_LOCKDOWN, Defaults.DEFAULT_GLOBAL_LOCKDOWN)
        val now = System.currentTimeMillis()
        
        if (isGlobal) {
            val globalUnblockTime = prefs.getLong(PrefsConstants.KEY_UNBLOCK_TIME, 0L)
            if (now < globalUnblockTime) {
                return globalUnblockTime
            }
        } else {
            val appUnblockTime = prefs.getLong("${PrefsConstants.KEY_APP_PENALTY_PREFIX}$packageName", 0L)
            if (now < appUnblockTime) {
                return appUnblockTime
            }
        }
        
        return null
    }
    
    /**
     * Remove penalty (pardon)
     */
    fun pardon(isGlobal: Boolean, packageName: String? = null) {
        Log.d(LogTags.PENALTY, "✅ Penalty pardoned (Global: $isGlobal, Package: $packageName)")
        
        prefs.edit().apply {
            if (isGlobal) {
                remove(PrefsConstants.KEY_UNBLOCK_TIME)
            } else if (packageName != null) {
                remove("${PrefsConstants.KEY_APP_PENALTY_PREFIX}$packageName")
            }
            apply()
        }
    }
    
    /**
     * Get remaining penalty time in milliseconds
     */
    fun getRemainingTime(unblockTime: Long): Long {
        return maxOf(0, unblockTime - System.currentTimeMillis())
    }
}
