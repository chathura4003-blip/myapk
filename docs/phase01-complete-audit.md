# Phase 01 — Full Repository Audit Report



---

## 1. Executive Summary

This audit examines the codebase of CloudDrive Leech (`com.clouddrive.leech`) across Android (Kotlin), Frontend (Capacitor/HTML/CSS/JS), Build scripts, and Manifest definitions in accordance with Phase 01 of the Master Prompt.

The app is designed to be a **100% Standalone Android APK** without a Node.js/Express desktop backend runtime.

---

## 2. Audit Findings by Component

### Finding 1: License Trust Flaw (RESOLVED)
- **FILE**: `public/js/license.js`
- **FUNCTION**: `getStoredLicenseState()`
- **BUG**: Allowed fallback object `{ licenseId: token, plan: 'PRO', status: 'ACTIVE' }` granting unverified PRO tier access.
- **ROOT CAUSE**: Fallback object instantiated if JWT parsing failed.
- **SEVERITY**: High (Security / Business Logic)
- **DEPENDENCY**: License Engine
- **PROPOSED FIX**: Strictly validate JWT claims or valid server key; default to FREE-TIER.
- **TEST REQUIRED**: Run unit tests and test license checks in app.
- **STATUS**: ✅ Fixed and verified.

### Finding 2: Foreground Service Types on Android 14/15 (RESOLVED)
- **FILE**: `android/app/src/main/java/com/clouddrive/leech/service/DownloadForegroundService.kt` and `NativeVpnService.kt`
- **FUNCTION**: `startForeground()`
- **BUG**: Missing `CATEGORY_PROGRESS` and `FOREGROUND_SERVICE_IMMEDIATE` on download service; missing explicit `FOREGROUND_SERVICE_TYPE_DATA_SYNC` parameter on `NativeVpnService.kt` on Android Q+.
- **ROOT CAUSE**: Android 14/15 enforces explicit FGS types.
- **SEVERITY**: High (OS Crash / Delayed Notifications)
- **DEPENDENCY**: Android OS FGS Manager
- **PROPOSED FIX**: Pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` and set notification immediate behaviors.
- **STATUS**: ✅ Fixed and verified.

### Finding 3: Polling Leaks on Background & Tab Switch (RESOLVED)
- **FILE**: `public/js/tasks.js` & `public/js/settings.js`
- **FUNCTION**: `tabChanged` and ping timer
- **BUG**: Older `tabChanged` event was not triggering when core dispatched `cloud:active-tab-changed`, keeping tasks and VPN ping timers running in background.
- **ROOT CAUSE**: Event naming mismatch between router and modules.
- **SEVERITY**: Medium (Battery / CPU drain)
- **PROPOSED FIX**: Listen to `cloud:active-tab-changed` and pause pollers when leaving tabs or when `document.hidden`.
- **STATUS**: ✅ Fixed and verified.

### Finding 4: In-App OTA Update Package Installation (RESOLVED)
- **FILE**: `android/app/src/main/AndroidManifest.xml`
- **BUG**: Missing `REQUEST_INSTALL_PACKAGES` permission required on Android 8+ to hand downloaded APKs to the Android system package installer.
- **ROOT CAUSE**: Omitted during initial manifest setup.
- **SEVERITY**: Medium (OTA update blockage)
- **PROPOSED FIX**: Add `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`.
- **STATUS**: ✅ Fixed and verified.

### Finding 5: Cinema Player Subtitle Stream Reset (RESOLVED)
- **FILE**: `android/app/src/main/java/com/clouddrive/leech/player/MovieCinemaPlayerActivity.kt`
- **FUNCTION**: `loadExternalSubtitle()`
- **BUG**: Reconstructed MediaItem from scratch without preserving HLS/DASH configurations.
- **ROOT CAUSE**: MediaItem builder recreation.
- **SEVERITY**: Medium (Video playback reset)
- **PROPOSED FIX**: Use `currentMediaItem?.buildUpon()?.setSubtitleConfigurations(...)`.
- **STATUS**: ✅ Fixed and verified.

### Finding 6: Movies Tab Scraper Bottlenecks (IDENTIFIED FOR SPEEDUP)
- **FILE**: `android/app/src/main/java/com/clouddrive/leech/extractor/providers/movies/NetflixScraper.kt` & `SinhalasubScraper.kt`
- **FUNCTION**: `search()`
- **BUG**: Sequential HTTP requests across TMDB (6 requests) and multiple mirror pages, causing 6-7 second latency and scraper timeouts.
- **ROOT CAUSE**: Lack of coroutine parallelization and excessive sequential page fetches during initial search.
- **SEVERITY**: High (User Experience / Latency)
- **PROPOSED FIX**: Parallelize fetches via `async { }`, cache generic catalog in memory, and fetch initial page first with lazy background pagination.
- **STATUS**: ⏳ Currently addressing in Phase 17/35.
