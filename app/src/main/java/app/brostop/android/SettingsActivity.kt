package app.brostop.android

import android.content.Context
import android.os.Bundle
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Settings activity - system configuration.
 * 
 * Responsibilities:
 * - Configure penalty duration (0, 2, 10, 30 minutes)
 * - Configure plea bargain settings (enabled + bonus duration)
 * - Configure emergency unlock mode (weak/shame/impossible)
 * - Configure global vs app-specific lockdown
 * - Toggle vibration
 * - Save all to SharedPreferences
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

        // Penalty duration chip group (0, 2, 10, 30 minutes)
        val cgPenalty = findViewById<ChipGroup>(R.id.cgPenalty)
        val currentPenalty = prefs.getInt(PrefsConstants.KEY_PENALTY_TIME, Defaults.DEFAULT_PENALTY_TIME)
        
        // Find and select the appropriate chip based on saved penalty time
        val penaltyChipIds = mapOf(
            0 to R.id.chipPenaltyNone,
            2 to R.id.chipPenaltyShort,
            10 to R.id.chipPenaltyStrict,
            30 to R.id.chipPenaltyLockdown
        )
        
        penaltyChipIds[currentPenalty]?.let { cgPenalty.check(it) }
        
        cgPenalty.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == android.view.View.NO_ID) return@setOnCheckedChangeListener
            val selectedChip = findViewById<com.google.android.material.chip.Chip>(checkedId)
            if (selectedChip != null) {
                val penaltyTime = selectedChip.tag.toString().toInt()
                prefs.edit().putInt(PrefsConstants.KEY_PENALTY_TIME, penaltyTime).apply()
            }
        }

        // Plea bargain switch
        val swPlea = findViewById<SwitchMaterial>(R.id.switchPlea)
        val layoutBonusDuration = findViewById<android.view.View>(R.id.layoutBonusDuration)
        val cgBonus = findViewById<ChipGroup>(R.id.cgBonus)

        val pleaBargainEnabled = prefs.getBoolean(PrefsConstants.KEY_PLEA_ENABLED, Defaults.DEFAULT_PLEA_ENABLED)
        swPlea.isChecked = pleaBargainEnabled
        layoutBonusDuration.visibility = if (pleaBargainEnabled) android.view.View.VISIBLE else android.view.View.GONE

        // Setup bonus duration chip group
        val bonusDuration = prefs.getInt(PrefsConstants.KEY_BONUS_DURATION, Defaults.DEFAULT_BONUS_DURATION)
        val bonusChipIds = mapOf(
            2 to R.id.chipBonusShort,
            5 to R.id.chipBonusLong
        )
        bonusChipIds[bonusDuration]?.let { cgBonus.check(it) }

        swPlea.setOnCheckedChangeListener { _, isChecked ->
            layoutBonusDuration.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            prefs.edit().putBoolean(PrefsConstants.KEY_PLEA_ENABLED, isChecked).apply()
        }

        cgBonus.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == android.view.View.NO_ID) return@setOnCheckedChangeListener
            val selectedChip = findViewById<com.google.android.material.chip.Chip>(checkedId)
            if (selectedChip != null) {
                val bonus = selectedChip.tag.toString().toInt()
                prefs.edit().putInt(PrefsConstants.KEY_BONUS_DURATION, bonus).apply()
            }
        }

        // Global lockdown toggle
        val swPunishmentScope = findViewById<SwitchMaterial>(R.id.swPunishmentScope)
        val globalLockdown = prefs.getBoolean(PrefsConstants.KEY_GLOBAL_LOCKDOWN, Defaults.DEFAULT_GLOBAL_LOCKDOWN)
        swPunishmentScope.isChecked = globalLockdown

        swPunishmentScope.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().apply {
                putBoolean(PrefsConstants.KEY_GLOBAL_LOCKDOWN, isChecked)
                // Clear existing penalties when toggling to avoid scope mismatches
                remove(PrefsConstants.KEY_UNBLOCK_TIME)
                apply()
            }
        }

        // Vibration toggle
        val swVibration = findViewById<SwitchMaterial>(R.id.switchVibration)
        val vibrationEnabled = prefs.getBoolean(PrefsConstants.KEY_VIBRATION_ENABLED, Defaults.DEFAULT_VIBRATION_ENABLED)
        swVibration.isChecked = vibrationEnabled

        swVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PrefsConstants.KEY_VIBRATION_ENABLED, isChecked).apply()
        }

        // Emergency unlock mode radio group
        val rgEmergency = findViewById<RadioGroup>(R.id.rgEmergency)
        val currentMode = prefs.getString(PrefsConstants.KEY_EMERGENCY_MODE, Defaults.DEFAULT_EMERGENCY_MODE) ?: Defaults.DEFAULT_EMERGENCY_MODE

        val emergencyModeIds = mapOf(
            "weak" to R.id.rbWeak,
            "shame" to R.id.rbShame,
            "impossible" to R.id.rbImpossible
        )

        emergencyModeIds[currentMode]?.let { rgEmergency.check(it) }

        rgEmergency.setOnCheckedChangeListener { _, checkedId ->
            val selectedMode = when (checkedId) {
                R.id.rbWeak -> "weak"
                R.id.rbShame -> "shame"
                R.id.rbImpossible -> "impossible"
                else -> "weak"
            }
            prefs.edit().putString(PrefsConstants.KEY_EMERGENCY_MODE, selectedMode).apply()
        }

        // Save & Exit button
        val btnDone = findViewById<MaterialButton>(R.id.btnDone)
        btnDone.setOnClickListener {
            finish()
        }
    }
}
