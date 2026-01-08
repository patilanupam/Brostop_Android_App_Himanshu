package app.brostop.android.backend

import android.content.Context
import android.util.Log
import app.brostop.android.Defaults
import app.brostop.android.LogTags
import app.brostop.android.Meme
import app.brostop.android.PrefsConstants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

/**
 * Provides personalized memes/roasts based on user preferences.
 * 
 * Responsibilities:
 * - Load memes from JSON
 * - Filter memes by language, gender, humor style, tags
 * - Select appropriate roasts for different scenarios
 */
class MemeProvider(private val context: Context) {
    
    private val prefs = context.getSharedPreferences(PrefsConstants.PREFS_NAME, Context.MODE_PRIVATE)
    private var memes: List<Meme> = emptyList()
    
    init {
        loadMemes()
    }
    
    /**
     * Load memes from assets/memes.json
     */
    private fun loadMemes() {
        try {
            val stream = context.assets.open("memes.json")
            val type = object : TypeToken<List<Meme>>() {}.type
            memes = Gson().fromJson(InputStreamReader(stream), type)
            Log.d(LogTags.ROAST, "📚 Loaded ${memes.size} memes from JSON")
        } catch (e: Exception) {
            Log.e(LogTags.ROAST, "❌ Failed to load memes.json", e)
            memes = emptyList()
        }
    }
    
    /**
     * Get a personalized roast based on user profile and optional tag
     */
    fun getRoast(tag: String? = null): String {
        val lang = prefs.getString(PrefsConstants.KEY_USER_LANG, Defaults.DEFAULT_LANGUAGE) 
            ?: Defaults.DEFAULT_LANGUAGE
        val userGender = prefs.getString(PrefsConstants.KEY_USER_GENDER, Defaults.DEFAULT_GENDER) 
            ?: Defaults.DEFAULT_GENDER
        val userHumorStyles = prefs.getStringSet(PrefsConstants.KEY_USER_HUMOR_STYLES, emptySet()) 
            ?: emptySet()
        val userHobbies = prefs.getStringSet(PrefsConstants.KEY_USER_HOBBIES, emptySet()) 
            ?: emptySet()
        val userOccupation = prefs.getString(PrefsConstants.KEY_USER_OCCUPATION, "") ?: ""
        
        // Filter by language first
        var filtered = memes.filter { it.language == lang }
            .ifEmpty { memes.filter { it.language == "en" } }
        
        // If explicit tag provided (for plea scenarios), use it
        if (tag != null) {
            val explicitMemes = filtered.filter { it.tags.contains(tag) }
            return explicitMemes.randomOrNull()?.text 
                ?: "You've reached your limit. Time to take a break!"
        }
        
        // Filter by gender
        filtered = filtered.filter { it.tags.contains(userGender) }
        
        // Filter by humor styles
        if (userHumorStyles.isNotEmpty()) {
            filtered = filtered.filter { meme ->
                userHumorStyles.any { humor -> meme.tags.contains(humor.lowercase()) }
            }
        }
        
        // Collect memes matching hobbies and occupation (equal weighting)
        val matchingMemes = mutableSetOf<Meme>()
        
        if (userHobbies.isNotEmpty()) {
            matchingMemes.addAll(
                filtered.filter { meme ->
                    userHobbies.any { hobby -> meme.tags.contains(hobby.lowercase()) }
                }
            )
        }
        
        if (userOccupation.isNotEmpty()) {
            matchingMemes.addAll(
                filtered.filter { it.tags.contains(userOccupation.lowercase()) }
            )
        }
        
        // Return random from matching set, or fallback
        return if (matchingMemes.isNotEmpty()) {
            Log.d(LogTags.ROAST, "Found ${matchingMemes.size} personalized roasts")
            matchingMemes.random().text
        } else {
            Log.d(LogTags.ROAST, "No personalized matches, using random from language: $lang")
            filtered.randomOrNull()?.text 
                ?: "You've reached your limit. Time to take a break!"
        }
    }
    
    /**
     * Check if memes are loaded
     */
    fun isLoaded(): Boolean = memes.isNotEmpty()
    
    /**
     * Get total number of loaded memes
     */
    fun getMemeCount(): Int = memes.size
}
