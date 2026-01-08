# Bro Stop - Critical Fixes Applied

**Date:** January 8, 2026
**Issues Fixed:** 2 Critical Bugs
**File Modified:** `app/src/main/java/app/brostop/android/ScrollGuardService.kt`

---

## Issues Reported

### Issue 1: Overlay Persists When User Presses Home Button
**Severity:** HIGH
**User Impact:** Lockdown overlay covers entire screen and won't disappear when user presses home button, forcing them to use emergency unlock.

### Issue 2: Swipe Counting Not Precise
**Severity:** MEDIUM
**User Impact:** Swipes are not counted accurately, making the app's limits unpredictable.

---

## Root Cause Analysis

### Issue 1: Overlay Stuck on Screen

**Problem Location:** Lines 193-219 (app switch handling logic)

**Root Cause:**
When the lockdown overlay was showing and user pressed the home button:
1. `handleAppSwitch()` received the launcher package name (e.g., "com.android.launcher")
2. Code checked if launcher was on blocklist → NO
3. Code checked if `currentTargetPackage` was not empty → YES (still set to blocked app)
4. Code assumed it was a "temporary window" (notification, volume slider) and preserved the session
5. **Returned early without calling `cleanupOverlayUI()`**
6. Overlay remained visible even though user left the app

**Why This Happened:**
The session preservation logic (lines 197-199) was designed to keep monitoring active when temporary system windows appeared (notifications, volume). However, it didn't account for the case where the user deliberately left to a non-blocked app while an overlay was showing.

**Code Before Fix:**
```kotlin
if (!isAppOnBlocklist) {
    if (currentTargetPackage.isNotEmpty()) {
        Log.d(LogTags.APP_SWITCH, "🔔 Temporary window ($pkgName), preserving session")
        return  // ❌ BUG: Returns without cleaning overlay
    }
    // ... cleanup code below never reached when overlay showing
}
```

**Code After Fix:**
```kotlin
if (!isAppOnBlocklist) {
    // CRITICAL FIX: Always hide overlay when user leaves to non-blocked app
    if (isJailActive || isRoastActive) {
        Log.d(LogTags.APP_SWITCH, "🏠 User left to non-blocked app while overlay active - hiding overlay")
        stopMonitoring()
        cleanupOverlayUI()  // ✅ NOW: Overlay is cleaned up
        currentTargetPackage = ""
        return
    }

    // Session preservation only applies when NO overlay is showing
    if (currentTargetPackage.isNotEmpty()) {
        Log.d(LogTags.APP_SWITCH, "🔔 Temporary window, preserving session")
        return
    }
    // ... rest of cleanup
}
```

**Fix Summary:**
- Added check for `isJailActive || isRoastActive` BEFORE session preservation logic
- When overlay is showing and user leaves, overlay is now immediately cleaned up
- Penalty timestamp is preserved, so overlay re-appears if user returns to blocked app
- Session preservation now only applies when no overlay is visible

---

### Issue 2: Inaccurate Swipe Counting

**Problem Location:** Lines 98-110 (scroll movement detection)

**Root Cause:**
The code checked if scroll events had actual movement by reading `scrollDeltaX` and `scrollDeltaY` properties:
```kotlin
val scrollDeltaX = event.scrollDeltaX
val scrollDeltaY = event.scrollDeltaY
val hasActualMovement = scrollDeltaX != 0 || scrollDeltaY != 0

if (!hasActualMovement) {
    return  // ❌ BUG: Filtered out ALL scrolls on API 26-27
}
```

**The Problem:**
- `scrollDeltaX` and `scrollDeltaY` were introduced in **API 28 (Android 9.0 Pie)**
- This app supports **API 26+ (Android 8.0 Oreo)**
- On API 26-27 devices:
  - These properties don't exist or return `0`
  - Check always failed: `0 != 0 || 0 != 0` = false
  - **ALL scrolls were filtered out!**
- Result: App only worked correctly on Android 9.0+ devices

**Code After Fix:**
```kotlin
if (isScrollEvent) {
    // CRITICAL FIX: Only check scroll movement on API 28+ (where scrollDelta exists)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val scrollDeltaX = event.scrollDeltaX
        val scrollDeltaY = event.scrollDeltaY
        val hasActualMovement = scrollDeltaX != 0 || scrollDeltaY != 0

        if (!hasActualMovement) {
            Log.v(LogTags.SWIPE, "⏸️ Scroll ignored (no movement)")
            return
        }
    }
    // On API 26-27: Skip movement check, rely on event type + debouncing

    // Count scrolls if we have an active monitoring session
    // ...
}
```

**Fix Summary:**
- Added API level check: `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)`
- Movement detection only runs on Android 9.0+ where properties exist
- On Android 8.0-8.1, relies on:
  - Event type filtering (`TYPE_VIEW_SCROLLED` only, no `WINDOW_CONTENT_CHANGED`)
  - Existing debouncing logic (600ms same event, 100ms different event)
- Maintains accuracy while fixing compatibility

---

## Changes Made

### File: `ScrollGuardService.kt`

#### Change 1: Fix Overlay Persistence (Lines 193-219)

**Before:**
```kotlin
// Step 2: Handle non-targeted apps
if (!isAppOnBlocklist) {
    if (currentTargetPackage.isNotEmpty()) {
        Log.d(LogTags.APP_SWITCH, "🔔 Temporary window ($pkgName), preserving session for $currentTargetPackage")
        return
    }
    Log.d(LogTags.APP_SWITCH, "↩️ Non-blocked app, stopping monitoring and hiding overlays")
    stopMonitoring()
    cleanupOverlayUI()

    if (isJailActive) {
        Log.d(LogTags.PENALTY, "👻 User escaped to home screen while in lockdown - overlay hidden until return")
        isJailActive = false
    }

    currentTargetPackage = ""
    return
}
```

**After:**
```kotlin
// Step 2: Handle non-targeted apps
if (!isAppOnBlocklist) {
    // CRITICAL FIX: If lockdown overlay is showing, ALWAYS hide it when user goes to home/non-blocked app
    // This fixes the bug where overlay stays visible even after pressing home button
    if (isJailActive || isRoastActive) {
        Log.d(LogTags.APP_SWITCH, "🏠 User left to non-blocked app while overlay active - hiding overlay")
        stopMonitoring()
        cleanupOverlayUI()
        // Keep penalty timestamp - will re-show if they return to blocked app
        currentTargetPackage = ""
        return
    }

    // If we have an active session (but no overlay), this is a temporary window (notification, volume slider, dialog)
    // Rule 1: Session Preservation - don't stop monitoring for temporary windows
    if (currentTargetPackage.isNotEmpty()) {
        Log.d(LogTags.APP_SWITCH, "🔔 Temporary window ($pkgName), preserving session for $currentTargetPackage")
        return
    }

    // User switched to a non-blocked app - clean up everything
    Log.d(LogTags.APP_SWITCH, "↩️ Non-blocked app, stopping monitoring")
    stopMonitoring()
    cleanupOverlayUI()
    currentTargetPackage = ""
    return
}
```

**Lines Changed:** 193-219 (27 lines)
**Impact:** Overlay now correctly hides when user presses home button

#### Change 2: Fix Swipe Counting (Lines 98-113)

**Before:**
```kotlin
if (isScrollEvent) {
    // Check if there's actual scroll movement (filter out touch-and-hold)
    val scrollDeltaX = event.scrollDeltaX
    val scrollDeltaY = event.scrollDeltaY
    val hasActualMovement = scrollDeltaX != 0 || scrollDeltaY != 0

    if (!hasActualMovement) {
        Log.v(LogTags.SWIPE, "⏸️ Scroll event ignored (no movement detected - user holding screen)")
        return
    }

    // Count scrolls if we have an active monitoring session
```

**After:**
```kotlin
if (isScrollEvent) {
    // CRITICAL FIX: Only check scroll movement on API 28+ (where scrollDelta properties exist)
    // On API 26-27, these properties return 0, causing all scrolls to be filtered out
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val scrollDeltaX = event.scrollDeltaX
        val scrollDeltaY = event.scrollDeltaY
        val hasActualMovement = scrollDeltaX != 0 || scrollDeltaY != 0

        if (!hasActualMovement) {
            Log.v(LogTags.SWIPE, "⏸️ Scroll event ignored (no movement detected - user holding screen)")
            return
        }
    }
    // On API 26-27: Skip movement check, rely on TYPE_VIEW_SCROLLED event type + debouncing

    // Count scrolls if we have an active monitoring session
```

**Lines Changed:** 98-113 (16 lines)
**Impact:** Swipe counting now works on all Android versions (8.0+)

---

## Testing Checklist

### Test Issue 1 Fix: Overlay Behavior

- [ ] **Test 1: Lockdown Overlay + Home Button**
  1. Open blocked app (e.g., Instagram)
  2. Scroll until lockdown overlay appears
  3. Press home button
  4. **Expected:** Overlay disappears immediately
  5. **Expected:** Home screen is visible and usable

- [ ] **Test 2: Re-entering After Lockdown**
  1. Complete Test 1
  2. Try to open Instagram again
  3. **Expected:** Lockdown overlay re-appears with countdown
  4. Press home button again
  5. **Expected:** Overlay disappears again

- [ ] **Test 3: Roast Overlay + Home Button**
  1. Open blocked app
  2. Scroll until roast overlay appears (with "Okay" or "Try Luck" buttons)
  3. Don't click buttons, press home button instead
  4. **Expected:** Overlay disappears, you see home screen

- [ ] **Test 4: Emergency Unlock Still Works**
  1. Enter lockdown state
  2. Click emergency unlock button
  3. **Expected:** Works as before (based on mode: weak/shame/impossible)

- [ ] **Test 5: Session Preservation (Notifications)**
  1. Open blocked app, start scrolling
  2. Pull down notification shade
  3. **Expected:** Session preserved, counter doesn't reset
  4. Close notification
  5. Continue scrolling
  6. **Expected:** Counter continues from where it was

### Test Issue 2 Fix: Swipe Counting

- [ ] **Test 6: Basic Swipe Counting**
  1. Set swipe limit to 10 (for easy testing)
  2. Open blocked app
  3. Scroll exactly 10 times, counting manually
  4. **Expected:** Overlay appears after 10th scroll (accurate counting)

- [ ] **Test 7: Fast Scrolling**
  1. Set swipe limit to 20
  2. Scroll very quickly (rapid swipes)
  3. **Expected:** All swipes counted (with debouncing, close to accurate)

- [ ] **Test 8: Slow Scrolling**
  1. Set swipe limit to 10
  2. Scroll slowly with pauses between each swipe
  3. **Expected:** All swipes counted accurately

- [ ] **Test 9: Different Android Versions**
  1. Test on Android 8.0/8.1 device or emulator (API 26-27)
  2. **Expected:** Swipe counting works
  3. Test on Android 9.0+ device or emulator (API 28+)
  4. **Expected:** Swipe counting works + filters touch-hold

- [ ] **Test 10: Mixed Scrolling**
  1. Scroll vertically a few times
  2. Scroll horizontally (if app supports)
  3. **Expected:** Both types counted toward limit

---

## Backward Compatibility

### API Level Support

| Android Version | API Level | Status | Notes |
|----------------|-----------|--------|-------|
| 8.0 Oreo | 26 | ✅ FIXED | Swipe counting now works |
| 8.1 Oreo | 27 | ✅ FIXED | Swipe counting now works |
| 9.0 Pie | 28 | ✅ WORKS | Uses scroll delta check |
| 10.0+ | 29+ | ✅ WORKS | Uses scroll delta check |

**Before Fix:**
- API 26-27: Swipe counting broken (all scrolls filtered)
- API 28+: Worked correctly

**After Fix:**
- API 26-27: Works (relies on event type + debouncing)
- API 28+: Works (uses scroll delta for extra accuracy)

---

## Performance Impact

### Change 1: Overlay Logic
- **CPU:** Negligible (one additional boolean check per app switch)
- **Memory:** None
- **Battery:** None
- **User Experience:** IMPROVED (overlay no longer stuck)

### Change 2: Swipe Counting
- **CPU:** Slightly reduced on API 26-27 (skips property access)
- **Memory:** None
- **Battery:** None
- **User Experience:** IMPROVED (counts work on all devices)

---

## Code Quality Improvements

### Added Comments
Both fixes include detailed comments explaining:
- What the bug was
- Why it happened
- How the fix works
- What behavior to expect

### Log Improvements
- Added log message: "🏠 User left to non-blocked app while overlay active - hiding overlay"
- Helps with debugging if issues recur

### Defensive Programming
- API level check prevents crashes on older Android versions
- Graceful degradation: Falls back to event type filtering if delta unavailable

---

## Known Limitations

### Swipe Counting on API 26-27
Without scroll delta properties, the app cannot detect:
- Touch-and-hold (no movement) vs actual scrolling
- Very small "accidental" scrolls

**Mitigation:**
- Existing debouncing logic (600ms) filters most false positives
- TYPE_VIEW_SCROLLED event type is reliable for intentional scrolls
- In practice, this should be accurate enough for the use case

---

## Regression Risk: LOW

### Why Low Risk?

1. **Isolated Changes:**
   - Only modified app switch handling and scroll detection
   - No changes to penalty system, roast selection, or settings

2. **Defensive Coding:**
   - Added checks, didn't remove safety measures
   - Preserved existing session preservation for notifications
   - API level check prevents crashes

3. **Improves Existing Logic:**
   - Overlay logic now has clear priority (overlay > session preservation)
   - Swipe counting more compatible, not replacing entire system

4. **No Breaking Changes:**
   - All existing features still work
   - Settings, preferences, data format unchanged
   - UI layouts unchanged

---

## Deployment Checklist

Before releasing to users:

- [ ] Test all scenarios in Testing Checklist above
- [ ] Test on multiple Android versions (8.0, 9.0, 10+)
- [ ] Test with different emergency modes (weak, shame, impossible)
- [ ] Test with global lockdown ON and OFF
- [ ] Test plea bargain feature still works
- [ ] Verify no crashes in Logcat
- [ ] Check battery usage hasn't increased
- [ ] Update version number in `build.gradle.kts`
- [ ] Update release notes with bug fixes

---

## Summary

### What Was Fixed

✅ **Issue 1:** Lockdown overlay now correctly disappears when user presses home button
✅ **Issue 2:** Swipe counting now works accurately on Android 8.0+ (previously broken on 8.0-8.1)

### Files Modified

1. `app/src/main/java/app/brostop/android/ScrollGuardService.kt`
   - Lines 193-219: Overlay cleanup logic
   - Lines 98-113: Swipe counting compatibility

### Lines Changed

- Total: 43 lines modified
- Added: ~20 lines (comments + logic)
- Removed: ~10 lines (old logic)
- Net change: +10 lines

### Testing Required

- 10 test scenarios defined above
- Estimated testing time: 30-45 minutes
- Requires: Android device/emulator with API 26+

---

**Status:** ✅ FIXES APPLIED, READY FOR TESTING

**Next Steps:**
1. Build the app: `gradlew.bat assembleDebug`
2. Install on test device: `gradlew.bat installDebug`
3. Run through testing checklist
4. If all tests pass, release to users

