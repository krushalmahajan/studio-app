# Studio Attendance — Android app

Kotlin and the plain Android SDK only (no third-party libraries). Works on Android 7.0 (API 24) through Android 15 (API 35).
Package: `com.idbiintech.studio.attendance`

## Before building
In `gradle.properties`:
```
studioUrl=https://your-studio-domain/        ← your Studio address
studioVersionCode=1                          ← +1 for every new APK you hand out
studioVersionName=1.0.0
```
If your theme folder is not `twentysixteen`, change `studioApiPath` too.

## Option A — GitHub builds it (no software to install)
1. Create a **private** repository on GitHub and upload the contents of this `android/` folder, including `keystore/`, `keystore.properties` and the hidden `.github/` folder. (With git: `git init && git add -A && git commit -m "Studio Attendance" && git push`.)
2. Open the repository → **Actions** → **Build Studio Attendance APK** → **Run workflow**. Every push also builds automatically.
3. After about 5 minutes, open the finished run → **Artifacts** → `studio-attendance-apk` → unzip → `studio-attendance.apk`.

Optional, safer: don't commit the keystore. Instead add these repository secrets: `STUDIO_KEYSTORE_BASE64` (`base64 -w0 keystore/studio-release.jks`), `STUDIO_KEYSTORE_PASSWORD`, `STUDIO_KEY_ALIAS` (`studio`), `STUDIO_KEY_PASSWORD`.

## Option B — Android Studio
1. Install Android Studio (Ladybug 2024.2 or newer) → **Open** this folder → wait for Gradle sync.
2. **Build → Generate Signed App Bundle / APK** → APK → release, or run `gradle assembleRelease` in a terminal.
   The release build is signed automatically with `keystore.properties`.
3. The APK is at `app/build/outputs/apk/release/app-release.apk`.

## Signing key — important
`keystore/studio-release.jks` and `keystore.properties` were generated for you. **Back them up somewhere safe and keep them private.**
Android installs an update only if it is signed with the same key. If the key is lost, every employee has to uninstall the app and install it again.

## Screens
- **Activate**: Studio address + 8-character code from Studio → Mobile App.
- **Punch**: office area, live status (inside / outside / accuracy / fake GPS), a big Punch In / Punch Out button and today's punches (biometric and app).
- **History**: last 31 days, first–last punch and hours.
- **Sign out**: unlinks the phone.

Location is used only while the punch screen is open. There is no background tracking.

## Publishing on Google Play later (optional)
Use the same project. Play needs a developer account, a privacy policy URL, and the location permission declaration (foreground only).
