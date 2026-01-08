package app.brostop.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Personalization activity - user preferences setup.
 * 
 * Responsibilities:
 * - Collect hobbies (up to 3)
 * - Collect humor styles (multiple selection)
 * - Collect occupation
 * - Save to SharedPreferences
 * - Set IS_SETUP_DONE = true
 * - Redirect to MainActivity
 */
class PersonalizationActivity : AppCompatActivity() {

    private lateinit var cgHobbies: ChipGroup
    private lateinit var chipGroupHumor: ChipGroup
    private lateinit var chipGroupOccupation: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personalization)

        cgHobbies = findViewById(R.id.cgHobbies)
        chipGroupHumor = findViewById(R.id.chipGroupHumor)
        chipGroupOccupation = findViewById(R.id.chipGroupOccupation)
        val btnInitialize = findViewById<MaterialButton>(R.id.btnInitialize)

        val prefs = getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)

        val hobbies = arrayOf(
            "gym", "coding", "gaming", "trading", "art", "music", "writing", "photography",
            "cooking", "travel", "reading", "anime", "movies", "sports", "fashion", "cars",
            "content creation", "startups", "philosophy", "history", "chess", "dancing",
            "yoga", "hiking", "meditation", "politics", "science", "diy", "crypto", "other"
        )
        
        // Allow multiple selection for hobbies (up to 3)
        cgHobbies.isSingleSelection = false
        populateChipGroup(cgHobbies, hobbies)
        
        // Add selection-count validation to prevent selecting more than 3
        cgHobbies.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.size > 3) {
                // Find the most recently checked chip and uncheck it
                val lastCheckedId = checkedIds.last()
                group.findViewById<com.google.android.material.chip.Chip>(lastCheckedId)?.isChecked = false
                Toast.makeText(this, "You can select up to 3 hobbies only", Toast.LENGTH_SHORT).show()
            }
        }

        // Load previously saved data
        val savedHobbies = prefs.getStringSet(PrefsConstants.KEY_USER_HOBBIES, emptySet()) ?: emptySet()
        val savedHumor = prefs.getStringSet(PrefsConstants.KEY_USER_HUMOR_STYLES, emptySet()) ?: emptySet()
        val savedOccupation = prefs.getString(PrefsConstants.KEY_USER_OCCUPATION, "") ?: ""

        // Pre-select and style all chip groups
        preselectChips(cgHobbies, savedHobbies)
        preselectChips(chipGroupHumor, savedHumor)
        preselectChips(chipGroupOccupation, setOf(savedOccupation))

        // Setup styling listeners
        setupStylingListener(cgHobbies)
        setupStylingListener(chipGroupHumor)
        setupStylingListener(chipGroupOccupation)

        btnInitialize.setOnClickListener {
            val selectedHobbies = getSelectedChips(cgHobbies)
            val selectedHumor = getSelectedChips(chipGroupHumor)
            val selectedOccupation = getSelectedChips(chipGroupOccupation).firstOrNull() ?: "student"

            if (selectedHobbies.isEmpty()) {
                Toast.makeText(this, "Select at least 1 hobby", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedHumor.isEmpty()) {
                Toast.makeText(this, "Select at least 1 humor style", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().apply {
                putStringSet(PrefsConstants.KEY_USER_HOBBIES, selectedHobbies.toSet())
                putStringSet(PrefsConstants.KEY_USER_HUMOR_STYLES, selectedHumor.toSet())
                putString(PrefsConstants.KEY_USER_OCCUPATION, selectedOccupation)
                putBoolean(PrefsConstants.KEY_IS_SETUP_DONE, true)
                apply()
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun populateChipGroup(chipGroup: ChipGroup, items: Array<String>) {
        chipGroup.removeAllViews()
        for (item in items) {
            val chip = layoutInflater.inflate(R.layout.hobby_chip_item, chipGroup, false) as Chip
            chip.text = item
            chipGroup.addView(chip)
        }
    }

    private fun preselectChips(chipGroup: ChipGroup, savedValues: Set<String>) {
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip
            if (chip != null && chip.text.toString().lowercase() in savedValues) {
                chip.isChecked = true
            }
        }
    }

    private fun setupStylingListener(chipGroup: ChipGroup) {
        chipGroup.setOnCheckedStateChangeListener { group, _ ->
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip
                chip?.apply {
                    if (isChecked) {
                        setChipBackgroundColorResource(R.color.neon_lime)
                        setTextColor(resources.getColor(R.color.black_bg, null))
                    } else {
                        setChipBackgroundColorResource(android.R.color.transparent)
                        setTextColor(resources.getColor(R.color.white_text, null))
                    }
                }
            }
        }
    }

    private fun getSelectedChips(chipGroup: ChipGroup): List<String> {
        return chipGroup.checkedChipIds.map { chipId ->
            findViewById<Chip>(chipId).text.toString().lowercase()
        }
    }
}