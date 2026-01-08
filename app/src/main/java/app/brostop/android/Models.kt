package app.brostop.android

import android.graphics.drawable.Drawable

/**
 * Data model for memes loaded from memes.json
 * Each meme has personalization tags for hobby, occupation, humor style, etc.
 */
data class Meme(
    val id: Int,
    val text: String,
    val tags: List<String>,      // Tags for matching: language, gender, hobby, occupation, humor style, scenario
    val language: String         // Language code: "en", "hi", etc.
)

/**
 * Data model for installed apps in AppSelectionActivity
 */
data class AppInfo(
    val name: String,           // User-friendly app name
    val packageName: String,    // Package identifier
    val icon: Drawable,         // App icon
    var isSelected: Boolean     // Whether user selected it for blocking
)
