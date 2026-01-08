package app.brostop.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.RangeSlider
import com.google.android.material.textfield.TextInputEditText
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.TextView

/**
 * Setup activity - first-time user configuration.
 * 
 * Responsibilities:
 * - Collect user name, goal, gender, language
 * - Collect swipe and time limits
 * - Save all to SharedPreferences
 * - Redirect to PersonalizationActivity
 */
class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etGoal = findViewById<TextInputEditText>(R.id.etGoal)
        val rgGender = findViewById<RadioGroup>(R.id.rgGender)
        val chipGroupLang = findViewById<ChipGroup>(R.id.chipGroupLang)
        val sliderSwipes = findViewById<RangeSlider>(R.id.sliderSwipes)
        val tvSwipeRange = findViewById<TextView>(R.id.tvSwipeRange)
        val sliderTime = findViewById<RangeSlider>(R.id.sliderTime)
        val tvTimeRange = findViewById<TextView>(R.id.tvTimeRange)
        val btnNext = findViewById<MaterialButton>(R.id.btnNext)

        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

        // Load previously saved data
        val savedName = prefs.getString(PrefsConstants.KEY_USER_NAME, "") ?: ""
        val savedGoal = prefs.getString(PrefsConstants.KEY_USER_GOAL, "") ?: ""
        val savedGender = prefs.getString(PrefsConstants.KEY_USER_GENDER, "") ?: ""
        val savedLang = prefs.getString(PrefsConstants.KEY_USER_LANG, "") ?: ""
        val savedMinSwipe = prefs.getInt(PrefsConstants.KEY_MIN_SWIPE, 20)
        val savedMaxSwipe = prefs.getInt(PrefsConstants.KEY_MAX_SWIPE, 50)
        val savedMinTime = prefs.getInt(PrefsConstants.KEY_MIN_TIME, 5)
        val savedMaxTime = prefs.getInt(PrefsConstants.KEY_MAX_TIME, 15)

        // Pre-fill saved data in UI
        etName.setText(savedName)
        etGoal.setText(savedGoal)

        // Set gender radio button
        if (savedGender.isNotEmpty()) {
            val genderId = if (savedGender.equals("she", ignoreCase = true)) {
                R.id.rbShe
            } else if (savedGender.equals("he", ignoreCase = true)) {
                R.id.rbHe
            } else {
                -1
            }
            if (genderId != -1) {
                rgGender.check(genderId)
            }
        }

        // Set language chip
        if (savedLang.isNotEmpty()) {
            for (i in 0 until chipGroupLang.childCount) {
                val chip = chipGroupLang.getChildAt(i) as? com.google.android.material.chip.Chip
                if (chip != null && chip.tag.toString() == savedLang) {
                    chipGroupLang.check(chip.id)
                }
            }
        }

        // Set slider values
        sliderSwipes.setValues(savedMinSwipe.toFloat(), savedMaxSwipe.toFloat())
        tvSwipeRange.text = "Swipe Limit: $savedMinSwipe-$savedMaxSwipe"

        sliderTime.setValues(savedMinTime.toFloat(), savedMaxTime.toFloat())
        tvTimeRange.text = "Time Limit: $savedMinTime-$savedMaxTime mins"

        // Update swipe range label when slider changes
        sliderSwipes.addOnChangeListener { _, _, _ ->
            val min = sliderSwipes.values[0].toInt()
            val max = sliderSwipes.values[1].toInt()
            tvSwipeRange.text = "Swipe Limit: $min-$max"
        }

        // Update time range label when slider changes
        sliderTime.addOnChangeListener { _, _, _ ->
            val min = sliderTime.values[0].toInt()
            val max = sliderTime.values[1].toInt()
            tvTimeRange.text = "Time Limit: $min-$max mins"
        }

        btnNext.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "Please enter your name"
                return@setOnClickListener
            }

            val goal = etGoal.text.toString().trim()
            if (goal.isEmpty()) {
                etGoal.error = "Please enter your mission objective"
                return@setOnClickListener
            }

            val selectedGenderId = rgGender.checkedRadioButtonId
            if (selectedGenderId == -1) {
                android.widget.Toast.makeText(this, "Select your gender", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedGender = findViewById<RadioButton>(selectedGenderId).text.toString().lowercase()
            
            // Get language from ChipGroup
            val selectedLanguageChipId = chipGroupLang.checkedChipId
            if (selectedLanguageChipId == -1) {
                android.widget.Toast.makeText(this, "Select your language", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedLanguageChip = findViewById<com.google.android.material.chip.Chip>(selectedLanguageChipId)
            val language = selectedLanguageChip.tag.toString()

            val minSwipe = sliderSwipes.values[0].toInt()
            val maxSwipe = sliderSwipes.values[1].toInt()
            val minTime = sliderTime.values[0].toInt()
            val maxTime = sliderTime.values[1].toInt()

            // Save to SharedPreferences using centralized constants
            val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(PrefsConstants.KEY_USER_NAME, name)
                putString(PrefsConstants.KEY_USER_GOAL, goal)
                putString(PrefsConstants.KEY_USER_GENDER, selectedGender)
                putString(PrefsConstants.KEY_USER_LANG, language)
                putInt(PrefsConstants.KEY_MIN_SWIPE, minSwipe)
                putInt(PrefsConstants.KEY_MAX_SWIPE, maxSwipe)
                putInt(PrefsConstants.KEY_MIN_TIME, minTime)
                putInt(PrefsConstants.KEY_MAX_TIME, maxTime)
                apply()
            }

            // Show confirmation and redirect to PersonalizationActivity
            android.widget.Toast.makeText(this, "Identity setup saved!", android.widget.Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PersonalizationActivity::class.java))
            finish()
        }
    }
}
