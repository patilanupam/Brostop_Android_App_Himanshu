# Bro Stop - New Backend Architecture

## Overview
This document describes the new modular backend architecture that replaces the monolithic `ScrollGuardService.kt` while keeping all frontend/UI components unchanged.

## ✅ Scroll Counting Method (FIXED - January 5, 2026)

**Problem Resolved:** Session resets and inaccurate swipe counting

**Solution Implemented:**
1. **Only count `TYPE_VIEW_SCROLLED` events** - Excludes `TYPE_WINDOW_CONTENT_CHANGED` which fires during internal navigation
2. **Session preservation on same-app events** - When user taps buttons (comment, share, like) inside a blocked app, the `TYPE_WINDOW_STATE_CHANGED` event no longer resets the session
3. **Check if already monitoring** - Before resetting session, verify if `currentTargetPackage == packageName` and preserve existing scroll counter

**Implementation Details:**
```kotlin
// Only count actual scroll gestures
val isScrollEvent = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED

// Preserve session for internal navigation
if (currentTargetPackage == pkgName && currentTargetPackage.isNotEmpty()) {
    // Don't reset - user is still in the same app
    return
}
```

**Result:** Scroll counter accurately tracks user swipes without false resets from button taps or internal screen changes.

---

## Architecture Components

### 1. **SessionManager** (`backend/SessionManager.kt`)
**Responsibilities:**
- Track current monitoring session (package, scroll count, limits)
- Randomize swipe and time limits from user preferences
- Increment scroll counter and detect limit breaches
- Extend sessions (for plea bargain wins)
- Start/stop sessions

**Key Methods:**
- `startSession(packageName)` - Initialize new session with random limits
- `incrementScroll()` - Increment counter and check if limit reached
- `extendSession(bonusSwipes, bonusMinutes)` - Grant bonus time/swipes
- `stopSession()` - Clean up session state

---

### 2. **PenaltyManager** (`backend/PenaltyManager.kt`)
**Responsibilities:**
- Apply penalties (global or per-app timestamps)
- Check if packages have active penalties
- Pardon/remove penalties
- Calculate remaining penalty time

**Key Methods:**
- `applyPenalty()` - Apply global penalty, returns true if global mode
- `applyAppPenalty(packageName)` - Apply app-specific penalty
- `getActivePenalty(packageName)` - Check for active penalty, returns timestamp or null
- `pardon(isGlobal, packageName)` - Remove penalty
- `getRemainingTime(unblockTime)` - Calculate milliseconds remaining

---

### 3. **MemeProvider** (`backend/MemeProvider.kt`)
**Responsibilities:**
- Load memes from `assets/memes.json`
- Filter memes by language, gender, humor style, tags
- Provide personalized roasts for different scenarios

**Key Methods:**
- `getRoast(tag?)` - Get personalized meme, optionally filtered by tag
- `isLoaded()` - Check if memes loaded successfully
- `getMemeCount()` - Get total number of available memes

**Filtering Logic:**
1. Filter by language (falls back to English)
2. If tag provided (e.g., "plea_won"), use that
3. Filter by gender
4. Filter by user humor styles
5. Match hobbies and occupation (equal weighting)
6. Return random from matches or fallback

---

### 4. **AppMonitor** (`backend/AppMonitor.kt`)
**Responsibilities:**
- Maintain list of blocked packages
- Filter system packages and keyboards
- Detect temporary windows vs real app switches

**Key Methods:**
- `updateBlockedList()` - Reload blocked apps from SharedPreferences
- `isBlocked(packageName)` - Check if package should be monitored
- `isSystemPackage(packageName, selfPackage)` - Filter keyboards and self
- `isTemporaryWindow(packageName)` - Detect notification shade, volume, etc.
- `getBlockedPackages()` - Get current blocked list

---

### 5. **ScrollDetector** (`backend/ScrollDetector.kt`)
**Responsibilities:**
- Identify scroll events from AccessibilityEvents
- Debounce rapid-fire scroll events
- Track event timing and types

**Key Methods:**
- `isScrollEvent(event)` - Check if event is VIEW_SCROLLED or WINDOW_CONTENT_CHANGED
- `shouldCountScroll(event)` - Apply debouncing logic (600ms same event, 100ms different)
- `getEventTypeName(eventType)` - Convert event type to readable string
- `reset()` - Clear detector state

**Debouncing Rules:**
- Same event type within 600ms → Ignore
- Different event type within 100ms → Ignore (same gesture)

---

### 6. **InterventionUI** (`backend/InterventionUI.kt`)
**Responsibilities:**
- Display/hide overlay views
- Show roast screen with optional plea bargain
- Show lockdown screen with countdown timer
- Handle emergency unlock modes (weak/shame/impossible)

**Key Methods:**
- `showRoast(isGlobal, preservedPackage)` - Display roast overlay
- `showLockdown(unblockTime, isGlobal, packageName)` - Display penalty countdown
- `hide()` - Remove overlay from window
- `isShowing()` - Check if overlay is currently visible

**Callbacks:**
- `onPleaWin` - Called when user wins plea bargain (20% chance)
- `onPleaLose` - Called when user loses plea bargain (80% chance)
- `onExitApp` - Called when user clicks "Okay" or loses plea
- `onEmergencyUnlock` - Called when emergency unlock succeeds

**Emergency Modes:**
- **Weak:** Simple tap button to unlock
- **Shame:** Type exact confession sentence
- **Impossible:** No unlock, must wait

---

### 7. **BroStopService** (`backend/BroStopService.kt`)
**Main orchestrator - new replacement for `ScrollGuardService.kt`**

**Responsibilities:**
- Initialize all backend components
- Handle AccessibilityEvents (app switching, scrolling)
- Coordinate between components
- Manage monitoring lifecycle

**Event Flow:**
1. `onAccessibilityEvent()` receives events
2. **App Switch:** Call `handleAppSwitch()`
   - Filter system packages via `AppMonitor`
   - Check penalty via `PenaltyManager`
   - If penalty → show lockdown via `InterventionUI`
   - If clean → start session via `SessionManager`
3. **Scroll Event:** Call `handleScrollEvent()`
   - Check if scroll should count via `ScrollDetector`
   - Increment counter via `SessionManager`
   - If limit reached → trigger intervention
4. **Intervention:** Call `triggerIntervention()`
   - Stop monitoring
   - Vibrate if enabled
   - Apply penalty via `PenaltyManager`
   - Show roast via `InterventionUI`

---

## Data Flow

```
User opens Instagram
       ↓
BroStopService.onAccessibilityEvent()
       ↓
handleAppSwitch("com.instagram.android")
       ↓
AppMonitor.isBlocked() → true
       ↓
PenaltyManager.getActivePenalty() → null (no penalty)
       ↓
SessionManager.startSession("com.instagram.android")
  - Randomizes limits: 25 swipes, 8 minutes
       ↓
startMonitoring()
  - Posts time limit callback
       ↓
User scrolls...
       ↓
handleScrollEvent()
       ↓
ScrollDetector.shouldCountScroll() → true (not debounced)
       ↓
SessionManager.incrementScroll() → 25/25 (LIMIT!)
       ↓
triggerIntervention("Swipe Limit Reached")
       ↓
PenaltyManager.applyPenalty() → isGlobal = true
       ↓
InterventionUI.showRoast(isGlobal=true, package="com.instagram.android")
  - Shows roast with plea bargain option
       ↓
User clicks "Try Luck"
       ↓
handlePleaBargain()
  - Random(100) < 20 → LOSE (80% chance)
       ↓
onPleaLose callback
       ↓
exitApp()
  - Sends user to Home Screen
  - Kills Instagram process
       ↓
User tries to open Instagram again
       ↓
PenaltyManager.getActivePenalty() → 1704448800000 (10 minutes from now)
       ↓
InterventionUI.showLockdown(unblockTime, isGlobal=true)
  - Shows countdown: 09:58, 09:57, ...
```

---

## Benefits of New Architecture

### 1. **Separation of Concerns**
Each component has a single, well-defined responsibility. No more 726-line monolithic service.

### 2. **Testability**
Components can be unit tested independently:
- Test `SessionManager` limit randomization
- Test `ScrollDetector` debouncing logic
- Test `PenaltyManager` timestamp calculations
- Test `MemeProvider` filtering algorithms

### 3. **Maintainability**
- Easy to locate bugs (clear component boundaries)
- Easy to add features (extend specific component)
- Easy to understand (each file < 200 lines)

### 4. **Reusability**
Components could be reused in other projects or features:
- `ScrollDetector` could work in any scroll-tracking scenario
- `MemeProvider` could be used for other content filtering
- `PenaltyManager` could handle any time-based restrictions

### 5. **Flexibility**
Easy to swap implementations:
- Replace `MemeProvider` with API-based content
- Replace `PenaltyManager` with server-synced penalties
- Add new intervention types without touching existing code

---

## Migration Guide

### To Switch to New Backend:

**Option 1: Replace Service** (Recommended)
1. Update `AndroidManifest.xml` to register `BroStopService` instead of `ScrollGuardService`
2. Test thoroughly - all UI remains unchanged
3. Delete old `ScrollGuardService.kt` once verified

**Option 2: Parallel Testing**
1. Keep both services in codebase
2. Switch between them in manifest for A/B testing
3. Compare behavior and logs
4. Remove old service once new one is proven

### No Frontend Changes Required!

All these changes are in the backend only:
- ✅ MainActivity - No changes
- ✅ SetupActivity - No changes
- ✅ PersonalizationActivity - No changes
- ✅ AppSelectionActivity - No changes
- ✅ SettingsActivity - No changes
- ✅ All layouts (XML) - No changes
- ✅ Constants, Models, Preferences - No changes

The new backend uses the exact same:
- SharedPreferences keys (PrefsConstants)
- Data structures (Meme, AppInfo)
- Layout resources (overlay_roast.xml, overlay_blocked.xml)
- Defaults and configurations

---

## File Structure

```
app/src/main/java/app/brostop/android/
├── backend/                          # NEW BACKEND PACKAGE
│   ├── SessionManager.kt            # Session state & limits
│   ├── PenaltyManager.kt            # Penalty timestamps
│   ├── MemeProvider.kt              # Roast content
│   ├── AppMonitor.kt                # Package filtering
│   ├── ScrollDetector.kt            # Scroll event detection
│   ├── InterventionUI.kt            # Overlay management
│   └── BroStopService.kt            # Main orchestrator (NEW SERVICE)
├── MainActivity.kt                   # UNCHANGED
├── SetupActivity.kt                  # UNCHANGED
├── PersonalizationActivity.kt        # UNCHANGED
├── AppSelectionActivity.kt           # UNCHANGED
├── SettingsActivity.kt               # UNCHANGED
├── Constants.kt                      # UNCHANGED
├── Models.kt                         # UNCHANGED
└── ScrollGuardService.kt             # OLD SERVICE (can be deleted after migration)
```

---

## Testing Checklist

- [ ] Service initializes without errors
- [ ] Blocked apps trigger session start
- [ ] Scroll counter increments correctly
- [ ] Swipe limit triggers intervention
- [ ] Time limit triggers intervention
- [ ] Roast overlay displays with correct meme
- [ ] Plea bargain win grants bonus time/swipes
- [ ] Plea bargain lose exits app immediately
- [ ] Global penalty blocks all apps
- [ ] App-specific penalty blocks only that app
- [ ] Weak emergency unlock works
- [ ] Shame emergency unlock validates input
- [ ] Impossible mode shows no unlock button
- [ ] Session preservation works (temporary windows)
- [ ] Session reset works (switch to non-blocked app)
- [ ] Vibration toggle works
- [ ] All settings from SettingsActivity apply correctly

---

## Future Enhancements

With this modular architecture, these features become easy to add:

1. **Analytics Component**
   - Track scroll counts per app
   - Generate usage reports
   - Export data to CSV/JSON

2. **Cloud Sync Component**
   - Sync penalties across devices
   - Backup meme preferences
   - Share custom memes

3. **Machine Learning Component**
   - Predict when user likely to scroll excessively
   - Adaptive limit adjustment
   - Personalized intervention timing

4. **Notification Component**
   - Warning notifications at 50%, 75% limits
   - Daily summary notifications
   - Streak tracking

5. **Widget Component**
   - Home screen widget showing current limits
   - Quick disable/enable toggle
   - Penalty countdown display

---

**Created:** January 5, 2026  
**Version:** 2.0  
**Status:** Ready for Testing
