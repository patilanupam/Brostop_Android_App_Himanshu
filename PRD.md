# Product Requirements Document: Bro Stop

## 1. Product Overview

**Product Name:** Bro Stop  
**Version:** 1.0  
**Platform:** Android (API 26+)  
**Category:** Digital Wellness / Productivity

### Executive Summary
Bro Stop is a gamified digital wellness Android application that helps users manage screen time addiction through humor-based interventions. Unlike traditional screen time management apps that use boring timers and notifications, Bro Stop employs personalized roasting, plea bargaining mechanics, and various lockdown modes to make the experience engaging and effective.

### Problem Statement
Users struggle with excessive screen time and mindless scrolling, but existing solutions are:
- Too serious and guilt-inducing
- Easy to ignore or bypass
- Lack personalization and engagement
- Don't adapt to user behavior

### Solution
An accessibility-powered app that monitors targeted apps in real-time, counts scrolls, and triggers humorous interventions when limits are breached. The app uses personalized memes, gamified plea bargains, and configurable penalties to make digital wellness entertaining.

---

## 2. Objectives & Goals

### Primary Objectives
1. **Reduce mindless scrolling** by 40% within the first week of use
2. **Increase user awareness** of screen time habits through real-time feedback
3. **Maintain engagement** through humor and gamification (70% 7-day retention)
4. **Empower users** with flexible, personalized controls

### Key Results
- Users complete setup within 3 minutes
- 60% of users enable at least 3 blocked apps
- Average session reduction of 30% on targeted apps
- 50% of users unlock premium features (if monetized)

---

## 3. Target Audience

### Primary Personas

**1. The Chronic Scroller (18-25 years)**
- Spends 4+ hours daily on social media
- Aware of the problem but lacks self-control
- Values humor and doesn't respond well to guilt tactics
- Tech-savvy, comfortable with app permissions

**2. The Productivity Seeker (25-35 years)**
- Professional trying to reduce distractions during work
- Uses multiple productivity apps
- Wants strict enforcement with emergency escape hatches
- Values data and progress tracking

**3. The Casual Reducer (35-50 years)**
- Moderate screen time but wants healthier habits
- Prefers gentle reminders over harsh penalties
- May need guidance with setup
- Values simplicity and clarity

---

## 4. User Stories

### Setup & Onboarding
- **As a new user**, I want to complete setup quickly so I can start managing my screen time immediately
- **As a user**, I want to personalize my experience with hobbies and humor preferences so the roasts feel relevant
- **As a user**, I want to select specific apps to monitor so I only target my problem apps

### Monitoring & Interventions
- **As a chronic scroller**, I want to be stopped mid-scroll with a funny roast so I realize I'm wasting time
- **As a user**, I want unpredictable limits so I can't game the system
- **As a user**, I want personalized memes based on my interests so the interventions feel fresh

### Penalties & Unlocks
- **As a user**, I want a chance to negotiate (plea bargain) so I feel some control
- **As a productivity seeker**, I want strict lockdown modes so I can't easily bypass the app
- **As a casual user**, I want flexible emergency unlocks so I'm not blocked during genuine emergencies
- **As a user**, I want to choose between global lockdowns (all apps) or per-app penalties

### Settings & Control
- **As a user**, I want to adjust penalty durations so I can find the right balance
- **As a user**, I want to toggle vibration feedback so I can customize notifications
- **As a user**, I want to enable/disable plea bargains so I can control difficulty

---

## 5. Features & Functionality

### 5.1 Core Features

#### Setup Flow (MVP)
**Components:** SetupActivity, PersonalizationActivity

**Requirements:**
- Collect user name, goal statement, gender (for pronoun personalization)
- Configure swipe limits (min-max range: 10-100 swipes)
- Configure time limits (min-max range: 1-60 minutes)
- Select hobbies (up to 3 from 30+ options)
- Select occupation (single choice from predefined list)
- Choose humor styles (sarcastic, motivational, dark, friendly)
- Language selection (English default)

**Acceptance Criteria:**
- Setup must be completed before accessing main app
- All inputs validated (e.g., min < max for ranges)
- Progress saved at each step
- "Back" navigation disabled to prevent skip

#### App Selection (MVP)
**Component:** AppSelectionActivity

**Requirements:**
- Display all launchable apps on device with icons
- Allow multi-select via toggle switches
- Show selected apps at top of list
- Exclude system apps (keyboard, settings)
- Save selections to persistent storage

**Acceptance Criteria:**
- Apps load within 3 seconds on mid-range devices
- Selected apps persist across app restarts
- Minimum 1 app must be selected to proceed

#### Real-Time Monitoring (MVP)
**Component:** ScrollGuardService (AccessibilityService)

**Requirements:**
- Monitor app switches using `TYPE_WINDOW_STATE_CHANGED` events
- Count scroll events using `TYPE_VIEW_SCROLLED` events
- Maintain session state per targeted app
- Randomize limits from configured ranges at session start
- Debounce scroll events (600ms threshold)

**Acceptance Criteria:**
- Session starts when user enters blocked app with no active penalty
- Session preserves during temporary windows (notifications, volume)
- Session resets only on deliberate app switch to non-targeted app
- Scroll counter increments only in active targeted app
- Service survives system memory pressure

#### Intervention System (MVP)
**Component:** ScrollGuardService + Overlay UI

**Requirements:**
- Trigger intervention when scroll limit OR time limit exceeded
- Display full-screen roast overlay with personalized meme
- Show "Okay" button that exits app (default)
- Show "Win/Lose" buttons if plea bargain enabled AND mercy unused
- Apply penalty timestamp (global or app-specific based on settings)
- Vibrate device if enabled

**Acceptance Criteria:**
- Overlay appears within 500ms of limit breach
- Meme matches user's language and tags (hobby/occupation)
- Overlay blocks all background interactions
- User cannot dismiss overlay without action

#### Plea Bargain System (MVP)
**Component:** ScrollGuardService

**Requirements:**
- One-time chance per session (tracked via KEY_MERCY_USED)
- 20% win probability, 80% lose probability
- **Win:** Grant bonus time/swipes, display success meme, resume session without resetting counter
- **Lose:** Display failure meme, trigger immediate lockdown, exit app

**Acceptance Criteria:**
- Win adds 30 bonus swipes + configurable bonus minutes to current session
- Lose triggers lockdown with no second chance
- Mercy flag resets only on session reset
- Probability is truly random

#### Penalty & Lockdown System (MVP)
**Component:** ScrollGuardService + Lockdown Overlay

**Requirements:**
- **Global Lockdown Mode:** Single penalty blocks ALL targeted apps
- **App-Specific Mode:** Independent penalties per app
- Display countdown timer in MM:SS format
- Support 3 emergency unlock modes:
  - **Weak:** Simple tap button
  - **Shame:** Type exact confession sentence
  - **Impossible:** No unlock, must wait
- Auto-unlock when timer reaches zero

**Acceptance Criteria:**
- Countdown accuracy within 1 second
- Global mode: penalty checked for any targeted app
- App-specific mode: only penalized app shows lock screen
- Toggling global/app-specific clears existing penalties
- Emergency unlock performs pardon + session reset

#### Settings & Configuration (MVP)
**Component:** SettingsActivity

**Requirements:**
- **Penalty Duration:** 0-60 minutes (0 = disable all punishments)
- **Plea Bargain Toggle:** Enable/disable + bonus duration (1-10 min)
- **Emergency Mode:** Weak / Shame / Impossible
- **Global Lockdown:** Toggle between modes
- **Vibration:** Enable/disable haptic feedback
- **Reset Setup:** Clear IS_SETUP_DONE flag to restart onboarding

**Acceptance Criteria:**
- All changes saved immediately via SharedPreferences
- Toggling global lockdown clears KEY_UNBLOCK_TIME
- Penalty duration 0 disables interventions entirely
- UI reflects current settings on load

#### Meme System (MVP)
**Component:** JSON-based content management

**Requirements:**
- Load memes from `assets/memes.json` at service startup
- Filter by language, then by tags (hobbies/occupation)
- Fallback to generic roasts if no tag match
- Support multiple meme types (roast, bonus, shame)
- Each meme has: text, tags[], language, type

**Acceptance Criteria:**
- Meme loading fails silently if JSON malformed
- Tag matching is case-insensitive
- At least 50 unique roasts in initial release
- Memes update without app recompile (asset swap)

### 5.2 Secondary Features

#### Main Dashboard (Future Enhancement)
- Display total swipes saved today
- Show current streak (days active)
- List app-specific penalties with countdowns
- Quick toggle to disable service temporarily

#### Statistics & Insights (Future Enhancement)
- Daily/weekly scroll counts per app
- Time saved vs. time wasted
- Intervention win/loss ratio
- Most productive hours

#### Social Features (Future Enhancement)
- Share roasts to social media
- Friend leaderboards for swipes saved
- Challenge friends to screen time reduction

#### Advanced Customization (Future Enhancement)
- Import custom meme packs
- Schedule-based limits (work hours vs. leisure)
- Whitelist mode (block all except selected apps)
- Geofencing (stricter limits at work/school)

---

## 6. Technical Requirements

### 6.1 Platform & Compatibility
- **Minimum SDK:** API 26 (Android 8.0 Oreo)
- **Target SDK:** API 36 (Android 14+)
- **Architecture:** Single-activity with fragments (future)
- **Language:** Kotlin 2.0.21
- **Build System:** Gradle 8.13.2

### 6.2 Permissions
**Critical (Required for Core Functionality):**
- `BIND_ACCESSIBILITY_SERVICE`: Monitor app usage and scrolls
- `SYSTEM_ALERT_WINDOW`: Display overlay UI

**Standard:**
- `KILL_BACKGROUND_PROCESSES`: Exit blocked apps
- `QUERY_ALL_PACKAGES`: List installed apps
- `VIBRATE`: Haptic feedback
- `INTERNET`: Update checks
- `POST_NOTIFICATIONS`: Update notifications

### 6.3 Dependencies
- AndroidX AppCompat
- Material Design Components 3
- GSON (JSON parsing)
- JUnit, Espresso (testing)

### 6.4 Data Storage
- **SharedPreferences** for all user data and state
- **Assets folder** for meme JSON
- No network storage (offline-first)

### 6.5 Performance Requirements
- App selection loading: < 3 seconds on mid-range devices
- Intervention trigger latency: < 500ms from limit breach
- Service memory footprint: < 50MB
- Battery impact: < 5% daily drain

### 6.6 Security & Privacy
- No user data leaves device
- No analytics tracking (privacy-first)
- No account creation required
- Open-source codebase for transparency

---

## 7. User Experience

### 7.1 Setup Flow
1. Welcome screen → Explain app purpose
2. Grant accessibility permission → System dialog
3. Grant overlay permission → System dialog
4. SetupActivity → Name, goal, gender, limits
5. PersonalizationActivity → Hobbies, occupation, humor
6. AppSelectionActivity → Choose apps to block
7. Main Dashboard → Ready to use

**Time to Value:** < 5 minutes

### 7.2 Daily Usage Flow
1. User opens blocked app (e.g., Instagram)
2. Service detects app switch, starts session with random limits
3. User scrolls normally until limit breached
4. Roast overlay appears with personalized meme
5. User chooses plea bargain (if enabled) or exits
6. If locked out, countdown timer displays with emergency unlock
7. User returns to Home Screen or different app

### 7.3 Error States & Edge Cases
- **Accessibility disabled:** Show persistent notification with re-enable button
- **Overlay permission revoked:** Show in-app prompt to re-grant
- **Meme JSON missing:** Fall back to hardcoded default roast
- **App crashes:** Service auto-restarts, restores last state
- **Rapid app switching:** Debounce prevents false triggers

---

## 8. Success Metrics

### 8.1 Engagement Metrics
- **Daily Active Users (DAU):** Target 60% of installed base
- **7-Day Retention:** 70%+
- **30-Day Retention:** 50%+
- **Average Sessions per Day:** 3-5 interventions

### 8.2 Effectiveness Metrics
- **Screen Time Reduction:** 30% decrease on blocked apps
- **Scroll Count Reduction:** 40% decrease in swipes
- **Lockdown Compliance:** 80% of users wait out timers

### 8.3 User Satisfaction
- **Setup Completion Rate:** 85%+
- **Plea Bargain Win Rate:** 20% (by design)
- **Emergency Unlock Usage:** < 30% of lockdowns
- **App Store Rating:** 4.5+ stars

---

## 9. Release Plan

### Phase 1: MVP (Current State)
**Timeline:** Completed  
**Features:**
- Full setup flow
- App selection
- Real-time monitoring
- Roast interventions
- Plea bargain system
- Global/app-specific lockdowns
- Emergency unlocks
- Settings configuration

### Phase 2: Polish & Feedback
**Timeline:** 1-2 months  
**Features:**
- Main dashboard with stats
- Improved meme library (100+ roasts)
- In-app tutorial/walkthrough
- Bug fixes from early users
- Performance optimizations

### Phase 3: Advanced Features
**Timeline:** 3-6 months  
**Features:**
- Statistics & insights dashboard
- Custom meme pack imports
- Schedule-based limits
- Social sharing
- Premium features (if monetized)

---

## 10. Risks & Mitigations

### Technical Risks
**Risk:** Android OS kills AccessibilityService during memory pressure  
**Mitigation:** Implement foreground service notification, persist state frequently

**Risk:** Users bypass app by disabling accessibility  
**Mitigation:** Show persistent notification when service disabled, gamify compliance

**Risk:** Meme content becomes stale  
**Mitigation:** Community-driven meme submissions, regular content updates

### User Experience Risks
**Risk:** Interventions feel annoying rather than helpful  
**Mitigation:** Extensive user testing, configurable humor styles, disable option

**Risk:** Users abandon app after 1-2 days  
**Mitigation:** Onboarding improvements, streak tracking, progressive difficulty

### Market Risks
**Risk:** Google Play Store rejects due to accessibility service abuse  
**Mitigation:** Clear privacy policy, justified use case, no data collection

---

## 11. Future Considerations

### Monetization (Optional)
- **Freemium Model:** Free basic features, premium for advanced stats/custom memes
- **One-Time Purchase:** $2.99 for lifetime access
- **No Ads:** Maintain user trust and avoid conflicts with wellness mission

### Platform Expansion
- iOS version (using Screen Time API)
- Web dashboard for cross-device sync
- Browser extension for desktop monitoring

### Content Expansion
- Multiple language support (Hindi, Spanish, etc.)
- Celebrity voice packs for roasts
- Video memes (short clips instead of static images)
- Integration with motivational quotes APIs

---

## 12. Appendix

### Glossary
- **Intervention:** The roast overlay displayed when limits are breached
- **Plea Bargain:** One-time chance to extend session with 20% win probability
- **Lockdown:** Penalty state where app access is blocked with countdown timer
- **Session:** Period of continuous use in a targeted app with unique randomized limits
- **Mercy:** User's unused plea bargain opportunity in current session
- **Pardon:** Emergency unlock that clears penalty and resets session

### References
- Android AccessibilityService Documentation
- Material Design 3 Guidelines
- Digital Wellness Research Papers
- Competitor Analysis: Screen Time, Freedom, Forest

---

**Document Owner:** Product Team  
**Last Updated:** January 5, 2026  
**Version:** 1.0  
**Status:** Approved for Development
