# Lightweight Android Build Setup (No Android Studio)

**Goal:** Build the Bro Stop app with fixes using minimal tools
**Time:** 15-30 minutes
**Download Size:** ~500MB (vs 5GB for Android Studio)
**Difficulty:** Intermediate (command-line based)

---

## Step 1: Install Java JDK (Required)

### Download Java JDK 17

1. Go to: **https://adoptium.net/temurin/releases/**
2. Select:
   - **Operating System:** Windows
   - **Architecture:** x64
   - **Package Type:** JDK
   - **Version:** 17 (LTS)
3. Download the `.msi` installer (around 100MB)
4. Run the installer
5. **Important:** Check the box "Add to PATH" during installation
6. Click through with default settings

### Verify Java Installation

Open a **new** Command Prompt (important - new window to refresh PATH):

```cmd
java -version
```

**Expected output:**
```
openjdk version "17.0.x" ...
```

If you see this, Java is installed correctly!

### Set JAVA_HOME Environment Variable

1. Press `Windows + R`, type `sysdm.cpl`, press Enter
2. Click "Advanced" tab → "Environment Variables" button
3. Under "System variables", click "New"
4. Variable name: `JAVA_HOME`
5. Variable value: `C:\Program Files\Eclipse Adoptium\jdk-17.0.x-hotspot`
   - (Adjust version number to match your installation)
6. Click OK

7. Find "Path" variable in "System variables", click "Edit"
8. Click "New", add: `%JAVA_HOME%\bin`
9. Click OK on all dialogs

### Test JAVA_HOME

Open a **new** Command Prompt:

```cmd
echo %JAVA_HOME%
javac -version
```

Should show your Java installation path and version.

---

## Step 2: Install Android SDK Command-Line Tools

### Download SDK Command-Line Tools

1. Go to: **https://developer.android.com/studio#command-tools**
2. Scroll to "Command line tools only"
3. Download: **commandlinetools-win-11076708_latest.zip** (around 150MB)
4. **Do NOT extract yet**

### Create SDK Directory Structure

Open Command Prompt and run:

```cmd
mkdir C:\Android
mkdir C:\Android\cmdline-tools
```

### Extract Command-Line Tools

1. Extract the downloaded ZIP file
2. You'll see a folder named `cmdline-tools` inside
3. **Rename it to `latest`**
4. Move the `latest` folder to: `C:\Android\cmdline-tools\`

**Final structure should be:**
```
C:\Android\
  └── cmdline-tools\
      └── latest\
          ├── bin\
          ├── lib\
          └── ...
```

### Set ANDROID_HOME Environment Variable

1. Press `Windows + R`, type `sysdm.cpl`, press Enter
2. Click "Advanced" tab → "Environment Variables"
3. Under "System variables", click "New"
4. Variable name: `ANDROID_HOME`
5. Variable value: `C:\Android`
6. Click OK

7. Find "Path" variable, click "Edit"
8. Add these entries (click "New" for each):
   ```
   %ANDROID_HOME%\cmdline-tools\latest\bin
   %ANDROID_HOME%\platform-tools
   %ANDROID_HOME%\emulator
   ```
9. Click OK on all dialogs

### Verify SDK Tools

Open a **new** Command Prompt:

```cmd
sdkmanager --version
```

**Expected output:**
```
9.0 or similar
```

If you see the version, SDK tools are installed correctly!

---

## Step 3: Install Required Android SDK Components

Now we'll install the specific components needed to build the app.

### Accept Licenses First

Open Command Prompt:

```cmd
sdkmanager --licenses
```

- Type `y` and press Enter for each license prompt
- There are usually 5-7 licenses to accept

### Install SDK Platform and Build Tools

This is a single command that installs everything needed:

```cmd
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
```

**This will download ~300MB and take 5-10 minutes.**

**What this installs:**
- `platform-tools`: ADB and fastboot (to install APK on device)
- `platforms;android-36`: Android 14 SDK (API 36 - what the app targets)
- `build-tools;35.0.0`: Latest build tools for compiling

### Wait for Download

You'll see progress like:
```
Downloading ...
Installing ...
```

When done, you should see:
```
[=========                             ] 100%
```

### Verify Installation

```cmd
sdkmanager --list_installed
```

**Expected output:**
```
build-tools;35.0.0
platform-tools
platforms;android-36
```

---

## Step 4: Build the App

Now you're ready to build the app with the fixes!

### Navigate to Project

```cmd
cd C:\Users\AnupamPatil\Downloads\brostop-main\brostop-main
```

### Build Debug APK

Run the Gradle wrapper (already included in the project):

```cmd
gradlew.bat assembleDebug
```

**First time build will download dependencies (~50-100MB) and take 2-5 minutes.**

### Watch the Build

You'll see:
```
> Task :app:preBuild
> Task :app:compileDebugKotlin
> Task :app:mergeDebugResources
...
BUILD SUCCESSFUL in 2m 34s
```

### Locate the APK

If successful, your APK is here:
```
app\build\outputs\apk\debug\app-debug.apk
```

**This APK includes both fixes I applied!**

---

## Step 5: Install on Device

### Method 1: Via USB (Recommended)

#### Enable USB Debugging on Phone

1. On your Android phone, go to **Settings**
2. Tap **About Phone** → Tap **Build Number** 7 times
3. Go back to **Settings** → **Developer Options**
4. Enable **USB Debugging**

#### Connect Phone and Install

1. Connect phone to computer via USB cable
2. On phone, tap "Allow" when prompted for USB debugging
3. In Command Prompt:

```cmd
adb devices
```

**Should show your device:**
```
List of devices attached
ABC123456789    device
```

4. Install the APK:

```cmd
adb install app\build\outputs\apk\debug\app-debug.apk
```

**Expected output:**
```
Success
```

### Method 2: Manual Transfer

If USB doesn't work:

1. Copy `app\build\outputs\apk\debug\app-debug.apk` to your phone
   - Via email, cloud storage, or file transfer
2. On phone, open the APK file
3. Tap "Install"
4. If prompted, enable "Install from unknown sources"

---

## Step 6: Test the Fixes

### Test Fix 1: Overlay Disappears on Home Button

1. Open the app, complete setup
2. Add a test app (e.g., Instagram) to blocked list
3. Set swipe limit to 10 for easy testing
4. Open Instagram and scroll 10 times
5. Lockdown overlay appears with countdown
6. **Press HOME button**
7. ✅ **Overlay should disappear immediately**
8. Try to open Instagram again
9. ✅ **Overlay should reappear**

### Test Fix 2: Swipe Counting Works

1. Set swipe limit to 10
2. Open blocked app
3. Scroll and count: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
4. ✅ **Overlay should appear after exactly 10 scrolls**

If both tests pass, the fixes are working! 🎉

---

## Troubleshooting

### Java: "command not found" or "JAVA_HOME not set"

**Solution:**
1. Verify Java is installed: `java -version`
2. Check JAVA_HOME: `echo %JAVA_HOME%`
3. If empty, re-do Step 1 environment variable setup
4. **Important:** Open a NEW Command Prompt after setting variables

### sdkmanager: "command not found"

**Solution:**
1. Check directory structure: `dir C:\Android\cmdline-tools\latest\bin`
2. Should see `sdkmanager.bat`
3. If not, re-extract command-line tools with correct folder structure
4. Verify ANDROID_HOME: `echo %ANDROID_HOME%`
5. Open NEW Command Prompt

### gradlew: "SDK location not found"

**Solution:**
1. Check ANDROID_HOME: `echo %ANDROID_HOME%`
2. Should show: `C:\Android`
3. Check file exists: `dir %ANDROID_HOME%\platforms\android-36`
4. If missing, re-run: `sdkmanager "platforms;android-36"`

### Build fails: "Could not find build-tools"

**Solution:**
```cmd
sdkmanager "build-tools;35.0.0"
```

### Build fails: "Kotlin daemon terminated unexpectedly"

**Solution:**
1. Close all Command Prompts
2. Delete: `C:\Users\AnupamPatil\.gradle\` folder
3. Re-run: `gradlew.bat assembleDebug`

### adb: "no devices/emulators found"

**Solution:**
1. On phone: Settings → Developer Options → USB Debugging (enable)
2. Reconnect USB cable
3. On phone, tap "Allow" on debugging prompt
4. Run: `adb devices` again

---

## What's Installed (Total Size)

| Component | Size | Purpose |
|-----------|------|---------|
| Java JDK 17 | ~200MB | Compile Kotlin code |
| Command-line tools | ~150MB | SDK manager |
| Platform-tools | ~15MB | ADB for installing APK |
| Android SDK (API 36) | ~60MB | Build target |
| Build-tools | ~80MB | Compile and package |
| **Total** | **~500MB** | vs 5GB for Android Studio |

---

## Cleaning Up (Optional)

If you want to free space after building:

### Keep Only Essentials

```cmd
# Keep these for future builds:
- C:\Android\
- C:\Program Files\Eclipse Adoptium\

# Can delete:
- Downloaded commandlinetools-win-*.zip
- C:\Users\YourName\.gradle\caches\ (gradle cache, can be regenerated)
```

### Completely Remove

Only if you're done building forever:

```cmd
rmdir /s /q C:\Android
```

Then remove ANDROID_HOME from environment variables.

---

## Building Release APK (Signed)

The APK we built is a debug APK (for testing). For distribution:

### Generate Signing Key

```cmd
keytool -genkey -v -keystore brostop.keystore -alias brostop -keyalg RSA -keysize 2048 -validity 10000
```

Answer the prompts (organization name, etc.).

### Build Release APK

Create `app/keystore.properties`:
```properties
storeFile=../brostop.keystore
storePassword=YOUR_PASSWORD
keyAlias=brostop
keyPassword=YOUR_PASSWORD
```

Then build:
```cmd
gradlew.bat assembleRelease
```

Output: `app\build\outputs\apk\release\app-release.apk`

---

## Next Builds (Faster)

After this first setup, future builds are much faster:

```cmd
cd C:\Users\AnupamPatil\Downloads\brostop-main\brostop-main
gradlew.bat assembleDebug
```

**Takes only 30-60 seconds** (dependencies already downloaded).

---

## Summary

✅ **What You Did:**
- Installed Java JDK 17 (~200MB)
- Installed Android SDK command-line tools (~350MB)
- Built the app with both fixes applied
- Installed on your Android device

✅ **What You Get:**
- App with overlay fix (disappears on home button)
- App with swipe counting fix (works on all Android versions)
- Ability to build future changes yourself
- No bloated IDE (saved 4.5GB!)

✅ **Build Time:**
- First build: 2-5 minutes
- Future builds: 30-60 seconds

**You're all set! Enjoy the fixed app.** 🚀

---

## Quick Reference Card

```cmd
# Navigate to project
cd C:\Users\AnupamPatil\Downloads\brostop-main\brostop-main

# Build debug APK
gradlew.bat assembleDebug

# Install on connected device
adb install app\build\outputs\apk\debug\app-debug.apk

# Check connected devices
adb devices

# Clean build (if issues)
gradlew.bat clean assembleDebug
```

Save this for future use!
