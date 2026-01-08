# Bro Stop - Complete Beginner's Guide

## Table of Contents
1. [What is Bro Stop?](#what-is-bro-stop)
2. [Project Architecture](#project-architecture)
3. [Prerequisites & Setup](#prerequisites--setup)
4. [Building & Running the App](#building--running-the-app)
5. [How the App Works](#how-the-app-works)
6. [Code Structure](#code-structure)
7. [Configuration & Settings](#configuration--settings)
8. [Troubleshooting](#troubleshooting)

---

## What is Bro Stop?

**Bro Stop** is a gamified digital wellness Android application that helps users manage screen time addiction through humor-based interventions. Instead of boring timers, it uses:
- Personalized roasting with memes
- Plea bargaining mechanics (20% chance to win extra time)
- Various lockdown modes
- Real-time scroll counting

### Key Features:
- Monitors targeted apps in real-time
- Counts your scrolls
- Shows funny personalized memes when you exceed limits
- Blocks apps with countdown timers
- Fully offline and privacy-first (no data leaves your device)

---

## Project Architecture

This is an **Android application** written in **Kotlin**, NOT a Python project!

### Technology Stack:
- **Language:** Kotlin 2.0.21
- **Build System:** Gradle 8.13.2
- **Minimum Android Version:** API 26 (Android 8.0 Oreo)
- **Target Android Version:** API 36 (Android 14+)
- **Key Dependencies:**
  - AndroidX Core KTX
  - Material Design Components 3
  - GSON (for JSON parsing)

### File Structure:
```
brostop-main/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/app/brostop/android/
│   │       │   ├── backend/                    # Core logic
│   │       │   │   ├── SessionManager.kt       # Session tracking & limits
│   │       │   │   ├── PenaltyManager.kt       # Penalty/lockdown system
│   │       │   │   ├── MemeProvider.kt         # Roast content management
│   │       │   │   ├── AppMonitor.kt           # App detection
│   │       │   │   ├── ScrollDetector.kt       # Scroll event detection
│   │       │   │   ├── InterventionUI.kt       # Overlay UI management
│   │       │   │   └── BroStopService.kt       # New modular service
│   │       │   ├── MainActivity.kt             # Dashboard/Home screen
│   │       │   ├── SetupActivity.kt            # Initial setup
│   │       │   ├── PersonalizationActivity.kt  # User preferences
│   │       │   ├── AppSelectionActivity.kt     # Choose apps to block
│   │       │   ├── SettingsActivity.kt         # App configuration
│   │       │   ├── ScrollGuardService.kt       # Old monolithic service
│   │       │   ├── Constants.kt                # All configuration keys
│   │       │   └── Models.kt                   # Data classes
│   │       ├── res/                            # Resources (layouts, images)
│   │       │   ├── layout/                     # XML UI layouts
│   │       │   ├── drawable/                   # Icons & graphics
│   │       │   ├── mipmap/                     # App icons
│   │       │   └── xml/                        # Accessibility config
│   │       ├── assets/
│   │       │   └── memes.json                  # Meme database
│   │       └── AndroidManifest.xml             # App configuration
│   ├── build.gradle.kts                        # App-level build config
│   └── release/
│       ├── app-release.apk                     # Prebuilt APK
│       └── app-release.aab                     # Prebuilt bundle
├── gradle/
│   ├── libs.versions.toml                      # Version catalog
│   └── wrapper/                                # Gradle wrapper
├── build.gradle.kts                            # Project-level build config
├── settings.gradle.kts                         # Project settings
├── PRD.md                                      # Product Requirements
├── BACKEND_ARCHITECTURE.md                     # Architecture docs
└── REBUILD_COMPLETE.md                         # Rebuild notes
```

---

## Prerequisites & Setup

### Step 1: Install Java JDK

Android development requires Java Development Kit (JDK).

**Download JDK 17 (Recommended):**
1. Go to: https://adoptium.net/
2. Download JDK 17 (LTS) for Windows
3. Install with default settings
4. **Set JAVA_HOME environment variable:**
   - Search "Environment Variables" in Windows
   - Click "Environment Variables" button
   - Under "System variables", click "New"
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot` (adjust path)
   - Add `%JAVA_HOME%\bin` to PATH variable

**Verify Installation:**
```bash
java -version
# Should show: openjdk version "17.0.x"
```

### Step 2: Install Android Studio

Android Studio includes Android SDK and build tools.

**Download & Install:**
1. Go to: https://developer.android.com/studio
2. Download Android Studio for Windows
3. Run installer with default settings
4. On first launch, complete setup wizard:
   - Choose "Standard" installation
   - Accept licenses
   - Wait for SDK components to download (2-3 GB)

**Verify SDK Installation:**
- SDK location: `C:\Users\YourName\AppData\Local\Android\Sdk`

### Step 3: Clone/Extract the Project

You already have the project at:
```
C:\Users\AnupamPatil\Downloads\brostop-main\brostop-main
```

---

## Building & Running the App

### Method 1: Using Android Studio (Recommended for Beginners)

1. **Open Project:**
   - Launch Android Studio
   - Click "Open" (or File > Open)
   - Navigate to `C:\Users\AnupamPatil\Downloads\brostop-main\brostop-main`
   - Click "OK"
   - Wait for Gradle sync to complete (first time takes 5-10 minutes)

2. **Create Virtual Device (Emulator):**
   - Click "Device Manager" (phone icon in toolbar)
   - Click "Create Device"
   - Select "Pixel 5" or similar
   - System Image: Download and select API 36 (Android 14)
   - Click "Finish"

3. **Build & Run:**
   - Click green "Run" button (▶) in toolbar
   - Select your virtual device
   - Wait for build to complete
   - App will launch on emulator

### Method 2: Using Command Line (Gradle)

**Build Debug APK:**
```bash
cd C:\Users\AnupamPatil\Downloads\brostop-main\brostop-main
gradlew.bat assembleDebug
```

**Output Location:**
```
app/build/outputs/apk/debug/app-debug.apk
```

**Install on Physical Device:**
1. Enable Developer Options on Android phone:
   - Go to Settings > About Phone
   - Tap "Build Number" 7 times
   - Go back to Settings > Developer Options
   - Enable "USB Debugging"
2. Connect phone via USB
3. Run: `gradlew.bat installDebug`

### Method 3: Use Pre-built APK

A pre-built APK already exists!

**Location:**
```
app/release/app-release.apk
```

**Install on Phone:**
1. Copy `app-release.apk` to your phone
2. Open file on phone
3. Tap "Install" (allow unknown sources if prompted)

---

## How the App Works

### Setup Flow (First Launch)

1. **MainActivity** checks if setup is done
   - If not, redirects to **SetupActivity**

2. **SetupActivity** - Initial Configuration
   - Enter your name
   - Set your goal (1-10 rating)
   - Choose gender (for pronoun personalization: "Bro" vs "Sis")
   - Select language (English default)
   - Set swipe limits range (min-max: 10-100)
   - Set time limits range (min-max: 1-60 minutes)
   - Saves to SharedPreferences

3. **PersonalizationActivity** - User Preferences
   - Select up to 3 hobbies (used to personalize memes)
   - Select occupation (student, engineer, etc.)
   - Choose humor styles (sarcastic, motivational, dark, friendly)
   - Marks setup as complete

4. **MainActivity** - Dashboard
   - Shows your name: "Hey Bro, [Name]!"
   - Displays permission status:
     - Accessibility Service (required for monitoring)
     - Overlay Permission (required for blocking screens)
   - Buttons to grant permissions
   - Navigation menu:
     - Choose Apps (select apps to block)
     - Settings (configure penalties, plea bargains)

5. **AppSelectionActivity** - Choose Apps to Block
   - Lists all launchable apps on your device
   - Toggle switches to select apps (Instagram, TikTok, etc.)
   - Selected apps saved to SharedPreferences

6. **SettingsActivity** - Configure Behavior
   - Penalty duration (0-60 minutes lockdown)
   - Enable/disable plea bargain
   - Bonus duration for winning plea (1-10 minutes)
   - Emergency unlock mode:
     - Weak: Simple tap to unlock
     - Shame: Type confession sentence to unlock
     - Impossible: No unlock, must wait
   - Global lockdown toggle:
     - ON: Single penalty blocks ALL apps
     - OFF: Independent penalties per app
   - Vibration toggle

### Runtime Flow (How Monitoring Works)

1. **User opens blocked app** (e.g., Instagram)

2. **ScrollGuardService** (AccessibilityService) detects app switch
   - Checks if Instagram is in blocked list → YES
   - Checks if Instagram has active penalty → NO

3. **SessionManager** starts new session
   - Randomizes limits from your ranges:
     - Example: 25 swipes (from 20-50 range)
     - Example: 8 minutes (from 5-15 range)
   - Starts monitoring

4. **User scrolls Instagram**
   - **ScrollDetector** counts scroll events
   - Debounces rapid events (600ms threshold)
   - Current count: 1, 2, 3... 25 swipes

5. **Limit Reached!** (25/25 swipes)
   - **PenaltyManager** applies penalty
   - **MemeProvider** selects personalized roast:
     - Filters by language, gender, humor style
     - Matches hobbies/occupation tags
   - **InterventionUI** shows roast overlay

6. **Roast Overlay Appears**
   - Shows funny meme text
   - Two options:
     - **"Okay"** → Exit app, start penalty countdown
     - **"Try Luck"** → Plea bargain (if enabled & mercy not used)

7. **Plea Bargain** (if clicked "Try Luck")
   - Random 20% win / 80% lose probability
   - **Win:**
     - Shows success meme
     - Grants +30 bonus swipes + bonus minutes
     - Resume session (counter NOT reset)
   - **Lose:**
     - Shows failure meme
     - Immediate lockdown
     - Exit app

8. **Penalty Active** (10 minutes lockdown)
   - **PenaltyManager** stores unblock timestamp
   - User tries to open Instagram again
   - **InterventionUI** shows lockdown overlay
   - Countdown timer: 09:58, 09:57, 09:56...
   - Emergency unlock button (if not "impossible" mode)

9. **Penalty Expires** (timer reaches 00:00)
   - Auto-unlock
   - User can open app again
   - New session starts with fresh random limits

### Architecture Pattern (New Backend)

The app was recently rebuilt with modular architecture:

**Old:** Monolithic `ScrollGuardService.kt` (726 lines)

**New:** Modular components:
- **SessionManager:** Tracks current session, limits, counters
- **PenaltyManager:** Handles lockdown timestamps
- **MemeProvider:** Loads and filters memes from JSON
- **AppMonitor:** Maintains blocked app list
- **ScrollDetector:** Identifies and debounces scroll events
- **InterventionUI:** Displays roast and lockdown overlays
- **BroStopService:** Orchestrates all components

**Note:** The manifest currently uses the old `ScrollGuardService`. The new `BroStopService` is available but not yet active.

---

## Code Structure

### Key Classes Explained

#### 1. Constants.kt
Single source of truth for all configuration.

```kotlin
object PrefsConstants {
    const val PREFS_NAME = "BroStopPrefs"
    const val KEY_IS_SETUP_DONE = "IS_SETUP_DONE"
    const val KEY_USER_NAME = "USER_NAME"
    const val KEY_BLOCKED_PACKAGES = "BLOCKED_PACKAGES"
    // ... 28+ keys total
}

object Defaults {
    const val DEFAULT_MIN_SWIPE = 20
    const val DEFAULT_MAX_SWIPE = 50
    const val DEFAULT_PENALTY_TIME = 10  // minutes
}
```

#### 2. Models.kt
Data classes for app data.

```kotlin
data class Meme(
    val id: Int,
    val text: String,
    val tags: List<String>,
    val language: String
)

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isSelected: Boolean
)
```

#### 3. ScrollGuardService.kt
Core accessibility service that monitors apps.

**Key Methods:**
- `onAccessibilityEvent()` - Receives app switch and scroll events
- `handleAppSwitch()` - Detects blocked app entry
- `handleScrollEvent()` - Counts scrolls
- `triggerIntervention()` - Shows roast when limit reached
- `checkForPenalty()` - Blocks app if penalty active

#### 4. MainActivity.kt
Dashboard entry point.

**Key Methods:**
- `onCreate()` - Enforces setup gate
- `setupUI()` - Displays dashboard
- `isAccessibilityServiceEnabled()` - Checks if monitoring active
- `updatePermissionStatus()` - Shows permission checkmarks

#### 5. MemeProvider (backend/)
Loads memes from `assets/memes.json`.

**Filtering Logic:**
1. Filter by language (English, Hindi, etc.)
2. Filter by gender (he/she pronouns)
3. Filter by humor styles (sarcastic, motivational)
4. Match hobbies and occupation tags
5. Return random from matches

#### 6. InterventionUI (backend/)
Manages overlay UI.

**Overlays:**
- **Roast Overlay** (`overlay_roast.xml`)
  - Shows meme text
  - Buttons: "Okay" / "Try Luck"
- **Lockdown Overlay** (`overlay_blocked.xml`)
  - Shows countdown timer
  - Emergency unlock button (mode-dependent)

---

## Configuration & Settings

### SharedPreferences Keys

All user data stored in `SharedPreferences` (file: `BroStopPrefs`):

| Key | Type | Description |
|-----|------|-------------|
| `IS_SETUP_DONE` | Boolean | Setup completion flag |
| `USER_NAME` | String | User's name |
| `USER_GENDER` | String | "he" or "she" |
| `USER_HOBBIES` | StringSet | Up to 3 hobbies |
| `USER_OCCUPATION` | String | Job/role |
| `USER_HUMOR_STYLES` | StringSet | Humor preferences |
| `BLOCKED_PACKAGES` | StringSet | App package names |
| `MIN_SWIPE` / `MAX_SWIPE` | Int | Swipe limit range |
| `MIN_TIME` / `MAX_TIME` | Int | Time limit range (minutes) |
| `PENALTY_TIME` | Int | Lockdown duration (minutes) |
| `PLEA_ENABLED` | Boolean | Enable plea bargain |
| `BONUS_DURATION` | Int | Win bonus time (minutes) |
| `EMERGENCY_MODE` | String | "weak", "shame", or "impossible" |
| `GLOBAL_LOCKDOWN` | Boolean | Global vs per-app penalties |
| `VIBRATION_ENABLED` | Boolean | Haptic feedback |
| `UNBLOCK_TIME` | Long | Global penalty timestamp |
| `PENALTY_TIME_<package>` | Long | Per-app penalty timestamp |
| `MERCY_USED` | Boolean | Plea bargain used this session |

### Meme JSON Structure

**Location:** `app/src/main/assets/memes.json`

```json
[
  {
    "id": 1,
    "text": "Bro really thought he'd escape the scroll trap? 😂",
    "tags": ["generic"],
    "language": "en",
    "type": "roast"
  },
  {
    "id": 2,
    "text": "Your guitar isn't gonna learn itself, but here you are scrolling!",
    "tags": ["music", "guitar"],
    "language": "en",
    "type": "roast"
  }
]
```

**Tags:** hobbies, occupations, or "generic"
**Types:** roast, bonus (plea win), shame (plea lose)

---

## Troubleshooting

### Build Issues

**Error: `JAVA_HOME is not set`**
- Solution: Install JDK and set JAVA_HOME environment variable (see Prerequisites)

**Error: `SDK location not found`**
- Solution: Install Android Studio with SDK components

**Error: `Gradle sync failed`**
- Solution: In Android Studio, File > Invalidate Caches > Restart

**Error: `compileSdk syntax error`**
- Solution: Already fixed! Updated `app/build.gradle.kts` line 8

### Runtime Issues

**App crashes on launch:**
- Check Logcat in Android Studio for error messages
- Ensure `memes.json` exists in `app/src/main/assets/`

**Accessibility service not working:**
- Go to Settings > Accessibility > Bro Stop > Enable
- Grant overlay permission: Settings > Apps > Bro Stop > Display over other apps

**Overlays not showing:**
- Check overlay permission granted
- Ensure blocked apps list is not empty

**Memes not personalized:**
- Check `memes.json` has tags matching your hobbies/occupation
- Verify hobbies saved in SharedPreferences

### Testing Tips

**View SharedPreferences data:**
```bash
adb shell
run-as app.brostop.android
cat shared_prefs/BroStopPrefs.xml
```

**View Logcat (filter by app):**
```bash
adb logcat | grep BroStop
```

**Test specific scroll count:**
- Lower limits to 5 swipes for quick testing
- Use Settings > Penalty Duration = 1 minute

**Reset setup:**
- Settings > Reset Setup (clears `IS_SETUP_DONE`)
- Or uninstall and reinstall app

---

## Permissions Explained

### Critical Permissions

1. **Accessibility Service (`BIND_ACCESSIBILITY_SERVICE`)**
   - **Why:** Monitor app switches and scroll events
   - **Grant:** Settings > Accessibility > Bro Stop > Enable
   - **Privacy:** Cannot read text content, only detects app/scroll events

2. **Overlay Permission (`SYSTEM_ALERT_WINDOW`)**
   - **Why:** Display roast and lockdown overlays on top of other apps
   - **Grant:** Settings > Apps > Bro Stop > Display over other apps

### Standard Permissions

- `KILL_BACKGROUND_PROCESSES` - Exit blocked apps
- `QUERY_ALL_PACKAGES` - List installed apps
- `VIBRATE` - Haptic feedback
- `INTERNET` - Update checks (optional)
- `POST_NOTIFICATIONS` - Update notifications

---

## Development Workflow

### Making Code Changes

1. Open project in Android Studio
2. Edit Kotlin files in `app/src/main/java/app/brostop/android/`
3. Click "Sync Now" if Gradle prompts
4. Click "Run" to build and test

### Adding New Memes

1. Open `app/src/main/assets/memes.json`
2. Add new entry:
```json
{
  "id": 100,
  "text": "Your custom roast here!",
  "tags": ["gaming", "generic"],
  "language": "en",
  "type": "roast"
}
```
3. Rebuild app
4. Meme will appear if tags match user profile

### Switching to New Backend

To use the new modular `BroStopService`:

1. Open `app/src/main/AndroidManifest.xml`
2. Replace line 33:
```xml
<!-- OLD -->
<service android:name="app.brostop.android.ScrollGuardService"

<!-- NEW -->
<service android:name="app.brostop.android.backend.BroStopService"
```
3. Rebuild and test thoroughly

---

## Next Steps

### For Beginners:
1. Install Android Studio + JDK
2. Open project in Android Studio
3. Build and run on emulator
4. Test setup flow
5. Add some apps to block
6. Test scrolling and interventions

### For Developers:
1. Read `PRD.md` for full requirements
2. Read `BACKEND_ARCHITECTURE.md` for architecture details
3. Explore modular backend in `backend/` folder
4. Run tests (coming soon)
5. Contribute new memes or features

### For Users:
1. Install pre-built APK from `app/release/app-release.apk`
2. Complete setup (5 minutes)
3. Grant permissions
4. Select your problem apps
5. Start mindful scrolling!

---

## Support & Resources

- **Documentation:**
  - `PRD.md` - Product requirements
  - `BACKEND_ARCHITECTURE.md` - Technical architecture
  - `REBUILD_COMPLETE.md` - Recent rebuild notes

- **Android Development:**
  - https://developer.android.com/
  - https://kotlinlang.org/docs/home.html

- **Accessibility Services:**
  - https://developer.android.com/guide/topics/ui/accessibility/service

---

## License & Privacy

- **Privacy First:** No data leaves your device
- **No Analytics:** No tracking or telemetry
- **Open Source:** Code is transparent and auditable
- **No Account:** No sign-up or login required

---

**Made with humor and good intentions to help you reclaim your time! 🚀**
