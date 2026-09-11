# Phase 02 — Build Baseline Report


---

## 1. Toolchain & Environment Baseline

- **Node.js**: v20+ / npm v10+
- **Java**: OpenJDK 17 (Java 17 runtime)
- **Gradle**: 8.14.3
- **Android Gradle Plugin (AGP)**: 8.8.2
- **Kotlin**: 2.1.10
- **compileSdkVersion**: 35 (Android 15)
- **targetSdkVersion**: 35 (Android 15)
- **minSdkVersion**: 24 (Android 7.0+)
- **applicationId**: `com.clouddrive.leech`
- **versionCode**: 6
- **versionName**: 1.0.5

---

## 2. Build Commands

- **Capacitor Asset Sync**: `npx cap sync android`
- **Automated Unit Tests**: `cd android; .\gradlew.bat testDebugUnitTest --no-daemon`
- **Debug APK Build**: `cd android; .\gradlew.bat assembleDebug --no-daemon`
- **Device Install**: `adb -s badee46f install -r -d "android/app/build/outputs/apk/debug/app-debug.apk"`

---

## 3. Test & Build Status

- **Unit Tests**: Pass (61/61 tasks executed/up-to-date, zero failures).
- **Assemble Debug**: Pass (100/100 tasks executed/up-to-date, zero compile errors).
- **Physical Device**: Device `badee46f` successfully installed and launched.
