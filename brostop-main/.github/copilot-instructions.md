# Bro Stop: AI Agent Instructions

**Project**: Android app that gamifies digital wellness by enforcing screen time limits with humorous interventions.

## Architecture Overview

**Core Components**:
- **ScrollGuardService** (`ScrollGuardService.kt`): The heart of the app - an AccessibilityService that monitors app usage, tracks scroll events, and triggers interventions (roasts, lockdowns). Uses SharedPreferences for state persistence.
- **UI Activities** (`MainActivity`, `SetupActivity`, `AppSelectionActivity`, `PersonalizationActivity`, `SettingsActivity`): Configuration and monitoring screens - users select blocked apps, set swipe/time limits, choose humor style, configure penalties.
- **Meme System**: JSON-based meme data (`memes.json` in assets) loaded at service startup, selected by tags matching user hobbies/occupations for context-aware roasting.

**Data Flow**:
1. User configures blocked apps and limits in UI → stored in SharedPreferences (key `BLOCKED_PACKAGES`, `MIN_SWIPE`, `MAX_SWIPE`, etc.)
2. ScrollGuardService monitors AccessibilityEvents for `TYPE_WINDOW_STATE_CHANGED` (app switching) and `TYPE_VIEW_SCROLLED` (scrolling)
3. When scroll limit or time limit breached → triggers interventions (roast overlay, shame protocol, global/app-specific lockdown)
4. Accessibility permissions required; overlay UI uses `WindowManager` with `TYPE_APPLICATION_OVERLAY`

## Current Logic Implementation (Code-Based Walkthrough)

### 1. Setup Flow (MainActivity → SetupActivity → PersonalizationActivity)
- **MainActivity.onCreate()**: Checks `IS_SETUP_DONE` flag. If false, immediately starts SetupActivity and finishes
- **SetupActivity**: Collects user name, goal, gender (he/she), language, swipe limits (min-max range), time limits (min-max minutes)
  - Saves all data to SharedPreferences on "Next" button
  - Starts PersonalizationActivity
- **PersonalizationActivity**: 
  - Collects hobbies (up to 3, from 30+ options), occupation (single choice), humor styles (multiple)
  - Saves hobbies as lowercase strings in set, occupation as string, humor styles as set
  - Sets `IS_SETUP_DONE = true` and redirects to MainActivity
  - **Critical**: Only after both activities complete can user access the main dashboard

### 2. App Selection (AppSelectionActivity)
- Loads all launchable apps on device (excludes system keyboard, non-launchable apps)
- Shows progress bar while fetching on background thread (thread safety via runOnUiThread)
- Displays app icons, names, and toggleable switches
- Saves selected packages to `BLOCKED_PACKAGES` as StringSet in SharedPreferences
- Sorting: Selected apps first, then alphabetical

### 3. ScrollGuardService: Monitoring & Session Management
**Initialization** (`onServiceConnected()`):
- Loads memes from `assets/memes.json` using GSON (silently fails if malformed)
- Fetches blocked package list from SharedPreferences

**Accessibility Event Handling** (`onAccessibilityEvent()`):
1. **Null-checks**: Ignores events without packageName or if service is exiting
2. **App Switch Detection** (`TYPE_WINDOW_STATE_CHANGED`):
   - Calls `handleAppSwitch(pkgName)` for ANY window change
3. **Scroll Detection** (`TYPE_VIEW_SCROLLED`):
   - Only counts scrolls if: matching `currentTargetPackage` AND no roast overlay active AND no jail active
   - Debounces with 600ms threshold (rapid scrolls ignored)
   - Increments `scrollCounter`
   - Triggers intervention if `scrollCounter >= swipeLimit`

**App Switch Logic** (`handleAppSwitch()`):
1. **Filters out non-launchable windows**:
   - Skips if: own package, keyboard app, non-launchable, or system dialogs
   - Allows temporary windows (volume slider, notification shade) to pass through silently
2. **If switching to non-targeted app** (e.g., Home Screen):
   - Stops monitoring
   - Clears `currentTargetPackage`
   - Removes any visible overlay
3. **If switching to targeted app**:
   - Checks for active penalties:
     - **Global lockdown active?** → Check `KEY_UNBLOCK_TIME` (timestamp)
     - **App-specific lockdown active?** → Check `PENALTY_TIME_<packageName>` (timestamp)
   - If penalty found and not expired → calls `enterLockdownState()`
   - **If no penalty and entering new target app** → calls `resetRoulette()` to start fresh session

**Session Timer & Swipe Roulette** (`resetRoulette()`):
- Randomizes `swipeLimit` from range [MIN_SWIPE, MAX_SWIPE]
- Randomizes `timeLimitMillis` from range [MIN_TIME, MAX_TIME] minutes
- Resets `scrollCounter = 0`
- Resets `KEY_MERCY_USED = false` (allows one plea bargain per session)
- Posts delayed callback: if `timeLimitMillis` elapses without activity, triggers intervention

### 4. Intervention Trigger (`triggerIntervention()`)
1. Stops monitoring (pauses timer)
2. Vibrates device (if enabled)
3. Calls `applyPenaltyTimestamp()` to save lockdown duration
4. Shows roast overlay via `showRoastOverlay()`

**Roast Overlay** (`showRoastOverlay()`):
- Displays personalized meme based on user hobbies (via `getSmartRoast()`)
- **If plea bargain enabled AND mercy not used**:
  - Shows "Win/Lose" buttons with 20% win chance
  - Win text: green, displays bonus meme
  - Lose text: red, triggers direct lockdown
- **Otherwise**:
  - Shows "Okay" button that exits app

**Smart Roasting** (`getSmartRoast(tag)`):
- Filters memes by language (from `KEY_USER_LANG`)
- Further filters by tags if hobby/occupation provided (lowercase comparison)
- Falls back to generic English roasts if no tag match

### 5. Plea Bargain System (`handlePleaBargain()`)
1. Marks `KEY_MERCY_USED = true`
2. **20% chance to win**:
   - Shows green success message (2 second delay)
   - Calls `performPardon()` (clears penalty)
   - **Critically**: Sets new limits → `swipeLimit = scrollCounter + 30` (bonus swipes), `timeLimitMillis = bonusDuration * 60 * 1000L` (bonus minutes)
   - **Does NOT reset scrollCounter to 0** (carries forward progress)
   - Restarts monitoring with new limits
3. **80% chance to lose**:
   - Shows red failure message (2 second delay)
   - Calls `exitApp()` directly (no mercy, instant lockdown with home action + kill process)

### 6. Lockdown State (`enterLockdownState(unblockTime, isGlobal)`):
1. Sets `isJailActive = true`, stops monitoring
2. Displays countdown timer overlay (formatted as MM:SS)
3. **Three emergency unlock modes** (configurable in SettingsActivity):
   - **"weak"**: Simple tap to unlock → calls `performPardon()` + `resetRoulette()` + restart monitoring
   - **"shame"**: User must type exact confession sentence → same unlock flow
   - **"impossible"**: No escape button, must wait out timer
4. **Timer completion** → Auto-unlock via `performPardon()` and `exitApp()`

### 7. Penalty System Mechanics
**Global Lockdown Mode** (`KEY_GLOBAL_LOCKDOWN = true`):
- When intervention triggered → saves single timestamp to `KEY_UNBLOCK_TIME`
- ANY targeted app switching checks this global penalty first
- Lock screen appears on ALL blocked apps until timer expires

**App-Specific Mode** (`KEY_GLOBAL_LOCKDOWN = false`):
- When intervention triggered → saves timestamp to `PENALTY_TIME_<packageName>`
- Each app has independent penalty
- Switching between targeted apps: only locked app shows lock screen
- Toggling this setting in SettingsActivity clears existing `KEY_UNBLOCK_TIME` to avoid scope mismatch

### 8. Settings Configuration (SettingsActivity)
**Configurable options**:
- **Penalty Duration**: 0-60 minutes (0 = disable all punishments)
- **Plea Bargain**: Toggle enabled/disabled + set bonus duration (1-10 minutes)
- **Emergency Mode**: weak/shame/impossible
- **Global Lockdown**: Toggle between global vs per-app penalties
- **Vibration**: Enable/disable vibration feedback
- **Special Logic**: Toggling global lockdown clears existing penalties to prevent scope mismatches

### 9. Update Check (MainActivity background task)
- Fetches version JSON from GitHub gist on startup (if notification permission granted)
- Checks if local version < remote version
- Displays notification if update available

## Core Rules (The Instruction Manual)

**Rule 1: The Setup Gate**
- User cannot use app until setup is complete
- `MainActivity` checks `IS_SETUP_DONE` flag in SharedPreferences on launch
- If false → immediately redirect to `SetupActivity` → `PersonalizationActivity`
- Only after personalization is `IS_SETUP_DONE` set to true, unlocking main dashboard

**Rule 2: The Monitoring Engine (ScrollGuardService)**
- Runs only if user grants Accessibility permissions
- Two primary jobs:
  1. **App Switching**: Listen for `TYPE_WINDOW_STATE_CHANGED` events to detect when user enters a new app
  2. **Swipe Counting**: Listen for `TYPE_VIEW_SCROLLED` events to count scrolls in active targeted app; ignore unrelated events
- Must null-check event and packageName to avoid processing invalid states

**Rule 3: Session Management (Timer & Swipe Counter)**
- **Session Start**: Begins when user enters a targeted app with no active penalty for that app
- **Session Preservation**: Session continues uninterrupted when temporary windows appear (notification shade, volume slider, dialogs)
- **Session Stop & Reset**: Only when user deliberately switches to a different, launchable app NOT on target list (Home Screen, WhatsApp)
- Temporary window switches (system dialogs) do NOT reset the session

**Rule 4: The Penalty System (Global vs App-Specific)**
- **Total Lockdown ON**: Single global penalty timer (`KEY_UNBLOCK_TIME`) saved when any targeted app exceeds limits → lock screen appears on ALL targeted apps
- **Total Lockdown OFF**: Per-app penalty timer saved (`PENALTY_TIME_<packageName>`) → only that specific app locked, others remain usable

**Rule 5: Emergency Unlocks & Plea Bargains**
- **Emergency Unlocks** (weak tap or shame protocol):
  1. `performPardon()`: Removes penalty timestamp
  2. `resetRoulette()`: Resets scrollCounter to 0 and randomizes limits for next session
  - Grants completely fresh start
- **Plea Bargains** (one-time session extension):
  - One per session only (`KEY_MERCY_USED` tracks this)
  - Win: Removes penalty, adds bonus time/swipes to current session (does NOT reset scroll counter to zero)
  - If limits exceeded in bonus session → direct lockdown with no second chance

## Key Patterns & Conventions

**SharedPreferences Usage**:
- Single preferences file named `"BroStopPrefs"` stores all user data
- Key constants defined in `ScrollGuardService.companion` - reuse these keys across activities
- Load on Activity `onCreate()`, save with `prefs.edit().apply()`

**Intervention States**:
- `isRoastActive`: Overlay is showing (blocks scroll detection)
- `isJailActive`: In lockdown state with countdown timer
- `isExiting`: Service shutdown flag
- **Lockdown Mechanics**: `enterLockdownState()` uses CountDownTimer; check `KEY_GLOBAL_LOCKDOWN` to determine if penalty is global or app-specific

**Meme Selection Logic**:
- `getSmartRoast(tag)`: Filters memes by tags (user hobbies/occupation) for personalized roasts
- Tags in meme JSON must match hobby/occupation strings (lowercase comparison)
- Falls back to generic roasts if no tag match

**UI Patterns**:
- Material Design Components (ChipGroup, RangeSlider, SwitchMaterial)
- Thread-heavy for app loading (`AppSelectionActivity` fetches installed apps on background thread)
- RecyclerView adapters for lists; LayoutInflater for dynamic chip creation

## Critical Dependencies & Setup

**Build**:
- Kotlin 2.0.21, AGP 8.13.2, minSdk 26, targetSdk 36
- Core dependencies: AndroidX AppCompat, Material Design, GSON (for meme JSON), Espresso/JUnit for testing
- Build variant: Single release build with ProGuard disabled

**Permissions** (AndroidManifest.xml):
- `SYSTEM_ALERT_WINDOW`: Required for overlay UI
- `KILL_BACKGROUND_PROCESSES`, `QUERY_ALL_PACKAGES`: For app management
- `BIND_ACCESSIBILITY_SERVICE`: Required to register as AccessibilityService
- `VIBRATE`, `INTERNET` (for update checks), `POST_NOTIFICATIONS`

**Accessibility Service Config** (`accessibility_config.xml`):
- Filters for `TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_STATE_CHANGED`
- Must be declared in manifest's service meta-data

## Build & Test Workflow

**Build**: `./gradlew :app:assembleRelease` → outputs to `app/release/app-release.aab`

**Testing**:
- Unit tests: `testImplementation(libs.junit)` - place in `app/src/test/`
- Instrumented tests: Use `AndroidJUnitRunner` (configured in `build.gradle.kts`)
- ScrollGuardService behavior difficult to unit test (tight coupling to AccessibilityService); prefer integration tests

**Common Issues**:
- Accessibility service must be manually enabled in system settings (no programmatic way on modern Android)
- Overlay UI requires `TYPE_APPLICATION_OVERLAY` (API 26+) and `SYSTEM_ALERT_WINDOW` permission
- Memes JSON must be valid - malformed JSON silently fails (wrapped in try-catch)

## Specific Conventions

- **Debouncing**: Scroll events debounced by 600ms (`DEBOUNCE` constant) to avoid rapid-fire triggers
- **Mercy Mode**: One-time pardon feature (`KEY_MERCY_USED`) - reset after penalty applied
- **Shame Protocol**: User types confession message; timestamp stored for future personalization
- **App Penalties**: Per-app lockdown times stored with `KEY_APP_PENALTY_PREFIX + packageName`
- **Vibration**: Enabled/disabled via `KEY_VIBRATION_ENABLED` preference

## When Making Changes

1. **Adding new settings**: Define key constant in `ScrollGuardService.companion`, add UI in appropriate Activity, update all 5 rules if logic changes
2. **Modifying meme system**: Update `memes.json` structure in `assets/` and `Meme` data class together
3. **Intervention logic**: Test both global (Rule 4) and app-specific lockdown paths; verify session preservation (Rule 3) with temporary windows
4. **UI state persistence**: Use `savedInstanceState` for temporary UI state; SharedPreferences for app-level persistence
5. **Session management**: When modifying app-switch detection, ensure Rule 3 is honored (temporary windows preserve session, only real app switches reset)
6. **Penalty & unlock logic**: Distinguish between `performPardon()` (clears penalty) and `resetRoulette()` (randomizes limits); ensure plea bargain only grants extension, not reset
7. **Setup flow**: Any changes to SetupActivity or PersonalizationActivity must maintain Rule 1 gate; always set `IS_SETUP_DONE` flag at completion
8. **Accessibility events**: Always null-check event and packageName; filter events early to avoid processing in invalid states
