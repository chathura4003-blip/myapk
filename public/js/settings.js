'use strict';

/**
 * ==============================================================================
 * ⚙️ CLOUD DRIVE LEECH - SETTINGS & VPN COMMAND CENTER (2026 NEXT-GEN)
 * ==============================================================================
 * Comprehensive settings controller managing:
 *   1. Settings Modal Lifecycle & UI State
 *   2. Google Colab Remote Runner Automation
 *   3. Cloudflare Tunnel / Railway Worker Health & Latency Testing
 *   4. Google Drive Quota, Multi-Account OAuth & Storage Telemetry
 *   5. System Cache & Application Maintenance Utilities
 *   6. Native V2Ray / VLESS VPN Engine (Traffic Polling, Health Check, Ping)
 *   7. Server List Hub (Search, Favorite, Duplicate, Delete, Ping-All)
 *   8. 6-Level Deep Connection Diagnostics (DNS, TCP Socket, HTTP 204 Route)
 *   9. Advanced Routing Modes & Per-App Split Tunneling
 *  10. HTTPS Subscription Pack Manager & Automated Node Syncing
 *  11. Safe Audit Log Viewer & Export System
 *  12. QR Code Image Scanner & Decoder Engine
 * ==============================================================================
 */

// ==============================================================================
// 1. DOM ELEMENTS & GLOBAL CONFIGURATION
// ==============================================================================

// Settings Modal References
const settingsModal = document.getElementById('settingsModal');
const btnSettings = document.getElementById('btnSettings');
const btnCloseSettings = document.getElementById('btnCloseSettings');

// Cloud Worker Controller References
const inputCloudWorkerUrl = document.getElementById('inputCloudWorkerUrl');
const btnSaveCloudWorker = document.getElementById('btnSaveCloudWorker');
const cloudWorkerStatusBox = document.getElementById('cloudWorkerStatusBox');
const cloudWorkerStatusLabel = document.getElementById('cloudWorkerStatusLabel');
const cloudWorkerLatencyBadge = document.getElementById('cloudWorkerLatencyBadge');


// Google Drive Profile Telemetry References
const settingsGdriveAvatar = document.getElementById('settingsGdriveAvatar');
const settingsGdriveName = document.getElementById('settingsGdriveName');
const settingsGdriveEmail = document.getElementById('settingsGdriveEmail');
const settingsDriveQuotaText = document.getElementById('settingsDriveQuotaText');
const settingsDriveQuotaFill = document.getElementById('settingsDriveQuotaFill');
const btnGoogleSignInSettings = document.getElementById('btnGoogleSignInSettings');
const btnGoogleDisconnectSettings = document.getElementById('btnGoogleDisconnectSettings');

// Maintenance & Diagnostic References
const btnOtaCheckSettings = document.getElementById('btnOtaCheckSettings');
const btnSettingsClearCache = document.getElementById('btnSettingsClearCache');

// ==============================================================================
// 2. SETTINGS MODAL LIFECYCLE
// ==============================================================================

/**
 * Opens the main Settings Command Center modal and syncs latest telemetry.
 */
window.openSettingsModal = function () {
  if (settingsModal) {
    settingsModal.style.removeProperty('display');
    if (window.openModalWithHistory) {
      window.openModalWithHistory(settingsModal);
    } else {
      settingsModal.classList.remove('hidden');
    }
    const scrollBody = settingsModal.querySelector('.settings-scroll-body');
    if (scrollBody) scrollBody.scrollTop = 0;
    loadSettingsConfig();
    if (typeof window.syncVpnStatusFromNative === 'function') {
      window.syncVpnStatusFromNative();
    }
  }
};

/**
 * Closes the Settings modal gracefully.
 */
window.closeSettingsModal = function () {
  if (settingsModal) {
    if (window.closeModalWithHistory) {
      window.closeModalWithHistory(settingsModal);
    } else {
      settingsModal.classList.add('hidden');
    }
    settingsModal.style.removeProperty('display');
  }
};

btnSettings?.addEventListener('click', window.openSettingsModal);
btnCloseSettings?.addEventListener('click', window.closeSettingsModal);

/**
 * Fetches settings state, Google Drive quota, and performs background worker ping.
 */
async function loadSettingsConfig() {
  try {
    const savedWorker = localStorage.getItem('cdl_cloud_worker_url') || '';
    if (inputCloudWorkerUrl) {
      inputCloudWorkerUrl.value = savedWorker;
    }
    window.CLOUD_WORKER_URL = savedWorker;

    // 1. Sync Native Google Drive State
    if (window.Capacitor?.Plugins?.NativeGdrive?.getActiveAccount) {
      try {
        const nativeAcc = await window.Capacitor.Plugins.NativeGdrive.getActiveAccount();
        if (nativeAcc && nativeAcc.connected && nativeAcc.email) {
          if (settingsGdriveEmail) settingsGdriveEmail.textContent = nativeAcc.email;
          if (settingsGdriveName) settingsGdriveName.textContent = nativeAcc.displayName || nativeAcc.email;
          if (settingsGdriveAvatar && nativeAcc.photo) settingsGdriveAvatar.src = nativeAcc.photo;

          const usageBytes = Number(nativeAcc.quotaUsage || 0);
          const limitBytes = Number(nativeAcc.quotaLimit || (15 * 1024 * 1024 * 1024));
          const usedGB = (usageBytes / (1024 * 1024 * 1024)).toFixed(2);
          const limitGB = limitBytes > 0 ? (limitBytes / (1024 * 1024 * 1024)).toFixed(0) : '15';
          const pct = limitBytes > 0 ? Math.min(100, ((usageBytes / limitBytes) * 100)).toFixed(1) : '0';

          if (settingsDriveQuotaText) settingsDriveQuotaText.textContent = `${usedGB} GB / ${limitGB} GB Used (${pct}%)`;
          if (settingsDriveQuotaFill) settingsDriveQuotaFill.style.width = `${pct}%`;
        }
      } catch (e) {
        console.warn('[Settings] NativeGdrive.getActiveAccount error:', e);
      }
    }

    // 2. Direct ping for Cloud Worker
    if (savedWorker) {
      testCloudWorkerPing(savedWorker);
    } else {
      setCloudWorkerOfflineUI('Paste Colab URL Below');
    }

    // 3. Native Drive profile already queried at step 1 via NativeGdrive
  } catch (err) {
    console.warn('[Settings] Failed to load configuration:', err);
  }
  if (typeof syncAdultSettingsUI === 'function') {
    syncAdultSettingsUI();
  }
}

// ==============================================================================
// 4. CLOUD WORKER TUNNEL CONTROLLER & LATENCY BENCHMARKING
// ==============================================================================

/**
 * Tests connection latency and health of the external Cloudflare / Railway Worker.
 */
async function testCloudWorkerPing(url) {
  if (!url) {
    setCloudWorkerOfflineUI('No URL configured');
    return;
  }
  const cleanUrl = url.trim().replace(/\/+$/, '');
  if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
    setCloudWorkerOfflineUI('Invalid URL format');
    return;
  }

  if (cloudWorkerStatusLabel) cloudWorkerStatusLabel.textContent = 'Testing Cloud Runner Line...';
  if (cloudWorkerLatencyBadge) {
    cloudWorkerLatencyBadge.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Testing...';
    cloudWorkerLatencyBadge.style.color = '#00f2fe';
  }

  const startTime = Date.now();
  let isOnline = false;
  let latency = 0;
  let activeTasks = 0;

  // 1. Direct CORS fetch to /ping or /health
  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 6000);

    let res = await fetch(`${cleanUrl}/ping`, {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
      signal: controller.signal
    }).catch(() => null);

    if (!res || !res.ok) {
      res = await fetch(`${cleanUrl}/health`, {
        method: 'GET',
        headers: { 'Accept': 'application/json' },
        signal: controller.signal
      }).catch(() => null);
    }
    clearTimeout(timeoutId);

    if (res && res.ok) {
      latency = Date.now() - startTime;
      isOnline = true;
      try {
        const d = await res.json();
        activeTasks = d.activeTasks || 0;
      } catch (_) { }
    }
  } catch (_) { }

  if (isOnline) {
    if (cloudWorkerStatusBox) cloudWorkerStatusBox.className = 'cloud-worker-status-box online';
    if (cloudWorkerStatusLabel) {
      cloudWorkerStatusLabel.innerHTML = `<span class="status-pulse-dot"></span> Cloud Runner: Online (${activeTasks} Active)`;
    }
    if (cloudWorkerLatencyBadge) {
      cloudWorkerLatencyBadge.innerHTML = `<i class="fa-solid fa-gauge-high"></i> ~${latency}ms`;
      cloudWorkerLatencyBadge.style.color = '#2ed573';
    }
  } else {
    setCloudWorkerOfflineUI('Worker Offline / Tunnel Closed');
  }
}

/**
 * Updates UI to indicate worker offline state.
 */
function setCloudWorkerOfflineUI(reason) {
  if (cloudWorkerStatusBox) cloudWorkerStatusBox.className = 'cloud-worker-status-box';
  if (cloudWorkerStatusLabel) {
    cloudWorkerStatusLabel.innerHTML = `<span class="status-pulse-dot" style="background: #ff4757; box-shadow: 0 0 10px #ff4757;"></span> Cloud Runner: Offline (${reason || 'Ready to connect'})`;
  }
  if (cloudWorkerLatencyBadge) {
    cloudWorkerLatencyBadge.innerHTML = '<i class="fa-solid fa-triangle-exclamation"></i> Offline';
    cloudWorkerLatencyBadge.style.color = '#ff4757';
  }
}

// Save & Benchmark Worker URL
btnSaveCloudWorker?.addEventListener('click', async (e) => {
  if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
    e?.preventDefault?.();
    e?.stopPropagation?.();
    if (window.showInApp404) {
      window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Cloud Runner & Upload pipeline is restricted under this installation. Please activate a PRO License Key.');
    }
    return;
  }
  const url = inputCloudWorkerUrl ? inputCloudWorkerUrl.value.trim().replace(/\/+$/, '') : '';
  if (!url) {
    window.showToast('Please enter a Cloudflare Tunnel URL', 'error');
    return;
  }

  window.showToast('Connecting to Cloud Runner...', 'info');
  btnSaveCloudWorker.disabled = true;
  btnSaveCloudWorker.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Saving...';

  try {
    localStorage.setItem('cdl_cloud_worker_url', url);
    window.CLOUD_WORKER_URL = url;

    await testCloudWorkerPing(url);
    window.showToast('✅ Cloud Runner URL saved & line tested!', 'success');
  } catch (err) {
    window.showToast('Connection failed: ' + err.message, 'error');
    setCloudWorkerOfflineUI(err.message);
  } finally {
    btnSaveCloudWorker.disabled = false;
    btnSaveCloudWorker.innerHTML = '<i class="fa-solid fa-cloud-arrow-up"></i> <span>Save & Ping</span>';
  }
});

// ==============================================================================
// 5. GOOGLE DRIVE QUOTA & OAUTH LIFECYCLE
// ==============================================================================

/**
 * Retrieves storage usage information from Google Drive API.
 */
async function fetchDriveQuota() {
  try {
    if (window.Capacitor?.Plugins?.NativeGdrive?.fetchRealGoogleDriveData) {
      const data = await window.Capacitor.Plugins.NativeGdrive.fetchRealGoogleDriveData();
      if (data && data.success && data.storageQuota) {
        const used = parseInt(data.storageQuota.usage || 0, 10);
        const limit = parseInt(data.storageQuota.limit || 0, 10);

        const usedGB = (used / (1024 * 1024 * 1024)).toFixed(2);
        const limitGB = limit > 0 ? (limit / (1024 * 1024 * 1024)).toFixed(0) : '15';
        const pct = limit > 0 ? Math.min(100, (used / limit) * 100).toFixed(1) : '5';

        if (settingsDriveQuotaText) settingsDriveQuotaText.textContent = `${usedGB} GB / ${limitGB} GB Used (${pct}%)`;
        if (settingsDriveQuotaFill) settingsDriveQuotaFill.style.width = `${pct}%`;
        return;
      }
    }
  } catch (_) { }
}

// Sign In / Connect Google Drive
btnGoogleSignInSettings?.addEventListener('click', async (e) => {
  if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_ACCOUNT_LINK')) {
    e?.preventDefault?.();
    e?.stopPropagation?.();
    if (window.showInApp404) {
      window.showInApp404('Google Account Link', 'HTTP 404: Cloud Account Synchronization & Google OAuth pipeline is restricted under this installation. Please activate a PRO License Key.');
    }
    return;
  }
  if (window.Capacitor?.Plugins?.NativeGdrive?.launchGoogleOAuthBrowser) {
    try {
      await window.Capacitor.Plugins.NativeGdrive.launchGoogleOAuthBrowser();
      return;
    } catch (err) {
      window.showToast('Auth error: ' + err.message, 'error');
      return;
    }
  }

  window.showToast('Google Drive integration requires running on Android device.', 'warning');
});

// Disconnect Google Drive Account
btnGoogleDisconnectSettings?.addEventListener('click', async () => {
  if (confirm('Are you sure you want to disconnect Google Drive?')) {
    if (window.Capacitor?.Plugins?.NativeGdrive?.disconnectAccount) {
      try {
        await window.Capacitor.Plugins.NativeGdrive.disconnectAccount();
      } catch (_) { }
    }

    window.rawDriveFiles = [];
    if (window.state) {
      window.state.driveConnected = false;
      window.state.driveUser = null;
    }
    window.showToast('Google Drive disconnected', 'info');
    window.closeSettingsModal();
    if (window.checkStatus) window.checkStatus();
    if (window.loadDriveAccountProfile) window.loadDriveAccountProfile();
    if (window.loadDriveFiles) window.loadDriveFiles();
  }
});

// ==============================================================================
// 6. SYSTEM CACHE & MAINTENANCE UTILITIES
// ==============================================================================

// OTA Updates Check in Settings
btnOtaCheckSettings?.addEventListener('click', () => {
  if (window.checkOtaUpdate) window.checkOtaUpdate(true);
});

// Clear Cache & Reset App State
btnSettingsClearCache?.addEventListener('click', () => {
  if (confirm('Clear local cache and refresh app? (Your account and downloads will remain safe)')) {
    try {
      if (typeof window.clearStreamDiskCache === 'function') {
        window.clearStreamDiskCache();
      }
      Object.keys(localStorage).forEach(k => {
        if (k.startsWith('cdl_details_res_') || k.startsWith('cdl_stream_res_') || k.startsWith('cdl_movie_cache_') || k.startsWith('cdl_movie_search_')) {
          localStorage.removeItem(k);
        }
      });
      localStorage.removeItem('cdl_adult_search_cache');
      window.showToast('🧹 All Movie & Stream Caches Cleared! Fresh links will be fetched.', 'success');
      setTimeout(() => {
        window.location.reload();
      }, 500);
    } catch (_) { }
  }
});

// ==============================================================================
// 6.2. 🔞 18+ DISCOVERY, ALGORITHM & PRIVACY CONTROLS (Sections 35, 36, 70, 71)
// ==============================================================================

function syncAdultSettingsUI() {
  const toggleAdultPersonalization = document.getElementById('toggleAdultPersonalization');
  const toggleAdultHistory = document.getElementById('toggleAdultHistory');
  const toggleAdultContinueWatching = document.getElementById('toggleAdultContinueWatching');

  if (toggleAdultPersonalization) {
    toggleAdultPersonalization.checked = localStorage.getItem('cdl_adult_personalization_enabled') !== 'false';
  }
  if (toggleAdultHistory) {
    toggleAdultHistory.checked = localStorage.getItem('cdl_adult_history_enabled') !== 'false';
  }
  if (toggleAdultContinueWatching) {
    toggleAdultContinueWatching.checked = localStorage.getItem('cdl_adult_continue_watching_enabled') !== 'false';
  }
}

function initAdultSettingsControls() {
  const toggleAdultPersonalization = document.getElementById('toggleAdultPersonalization');
  const toggleAdultHistory = document.getElementById('toggleAdultHistory');
  const toggleAdultContinueWatching = document.getElementById('toggleAdultContinueWatching');
  const btnResetAdultTaste = document.getElementById('btnResetAdultTaste');
  const btnClearAdultHistory = document.getElementById('btnClearAdultHistory');
  const btnClearAdultCache = document.getElementById('btnClearAdultCache');
  const btnLockAdultGate = document.getElementById('btnLockAdultGate');

  syncAdultSettingsUI();

  toggleAdultPersonalization?.addEventListener('change', (e) => {
    localStorage.setItem('cdl_adult_personalization_enabled', e.target.checked ? 'true' : 'false');
    window.showToast(e.target.checked ? '18+ Taste Personalization enabled' : '18+ Taste Personalization disabled (Default feed)', 'info');
  });

  toggleAdultHistory?.addEventListener('change', (e) => {
    localStorage.setItem('cdl_adult_history_enabled', e.target.checked ? 'true' : 'false');
    window.showToast(e.target.checked ? '18+ Watch History tracking enabled' : '18+ Watch History tracking paused', 'info');
  });

  toggleAdultContinueWatching?.addEventListener('change', (e) => {
    localStorage.setItem('cdl_adult_continue_watching_enabled', e.target.checked ? 'true' : 'false');
    window.showToast(e.target.checked ? 'Continue Watching rail enabled' : 'Continue Watching rail disabled', 'info');
    if (window.renderContinueWatchingRail) window.renderContinueWatchingRail();
  });

  btnResetAdultTaste?.addEventListener('click', () => {
    if (confirm('Reset your 18+ recommendation algorithm and learned taste preferences?')) {
      localStorage.removeItem('adult_taste_profile_v3');
      localStorage.removeItem('adult_taste_profile_v2');
      localStorage.removeItem('cdl_adult_events_v2');
      window.showToast('✨ 18+ Recommendation algorithm reset to clean slate!', 'success');
      if (window.renderAdultGrid && window.rawAdultResults?.length > 0) {
        window.renderAdultGrid();
      }
    }
  });

  btnClearAdultHistory?.addEventListener('click', () => {
    if (confirm('Clear your local 18+ watch history and Continue Watching queue?')) {
      localStorage.removeItem('adult_watch_history');
      localStorage.removeItem('cdl_adult_continue_watching');
      localStorage.removeItem('cdl_adult_last_watched_item');
      window.showToast('🧹 18+ Watch history and playback progress cleared!', 'success');
      if (window.renderContinueWatchingRail) window.renderContinueWatchingRail();
      if (window.renderBecauseYouWatchedRail) window.renderBecauseYouWatchedRail();
    }
  });

  btnClearAdultCache?.addEventListener('click', () => {
    localStorage.removeItem('cdl_adult_feed_cache_v2');
    localStorage.removeItem('cdl_adult_search_cache');
    if (window.seenAdultUrls) window.seenAdultUrls.clear();
    window.showToast('⚡ 18+ Feed cache purged. Fresh streams will load on next visit.', 'success');
  });

  btnLockAdultGate?.addEventListener('click', () => {
    if (window.lockAdultGate) {
      window.lockAdultGate();
    }
    if (window.closeModalWithHistory && settingsModal) {
      window.closeModalWithHistory(settingsModal);
    } else if (settingsModal) {
      settingsModal.classList.add('hidden');
    }
  });
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initAdultSettingsControls);
} else {
  initAdultSettingsControls();
}

// ==============================================================================
// 7. REAL-TIME V2RAY / VLESS NATIVE VPN CONTROLLER
// ==============================================================================
(function initCyberVpnController() {
  // Main Toggle & Status Elements
  const vpnMasterToggle = document.getElementById('vpnMasterToggle');
  const vpnStatusBox = document.getElementById('vpnStatusBox');
  const vpnStatusLabel = document.getElementById('vpnStatusLabel');
  const vpnLatencyBadge = document.getElementById('vpnLatencyBadge');
  const vpnHeaderBadge = document.getElementById('vpnHeaderBadge');
  const vpnHeaderFlag = document.getElementById('vpnHeaderFlag');
  const vpnHeaderCode = document.getElementById('vpnHeaderCode');
  const vpnHeaderPing = document.getElementById('vpnHeaderPing');
  const inputV2RayPayload = document.getElementById('inputV2RayPayload');
  const btnSaveV2RayPayload = document.getElementById('btnSaveV2RayPayload');
  const btnPasteV2Ray = document.getElementById('btnPasteV2Ray');
  const btnSavePayloadToList = document.getElementById('btnSavePayloadToList');
  const savedPayloadsList = document.getElementById('savedPayloadsList');

  // NetMod Compact Card Elements
  const netmodVpnCard = document.getElementById('netmodVpnCard');
  const netmodNodeSelector = document.getElementById('netmodNodeSelector');
  const netmodNodeFlag = document.getElementById('netmodNodeFlag');
  const netmodNodeTitle = document.getElementById('netmodNodeTitle');
  const netmodNodeSub = document.getElementById('netmodNodeSub');
  const netmodProtoPill = document.getElementById('netmodProtoPill');
  const btnToggleUriDrawer = document.getElementById('btnToggleUriDrawer');
  const netmodUriDrawer = document.getElementById('netmodUriDrawer');
  const netmodMsgStrip = document.getElementById('netmodMsgStrip');
  const netmodMsgIcon = document.getElementById('netmodMsgIcon');
  const netmodMsgText = document.getElementById('netmodMsgText');

  // Traffic Telemetry Counters
  const vpnDownloadBytes = document.getElementById('vpnDownloadBytes');
  const vpnUploadBytes = document.getElementById('vpnUploadBytes');
  const vpnDownloadSpeed = document.getElementById('vpnDownloadSpeed');
  const vpnUploadSpeed = document.getElementById('vpnUploadSpeed');

  // Custom Save Payload Modal Elements
  const savePayloadModal = document.getElementById('savePayloadModal');
  const btnCloseSavePayloadModal = document.getElementById('btnCloseSavePayloadModal');
  const btnCancelSavePayload = document.getElementById('btnCancelSavePayload');
  const btnConfirmSavePayload = document.getElementById('btnConfirmSavePayload');
  const inputSavePayloadName = document.getElementById('inputSavePayloadName');
  const savePayloadModalFlag = document.getElementById('savePayloadModalFlag');
  const savePayloadModalProtoBadge = document.getElementById('savePayloadModalProtoBadge');
  const savePayloadModalHost = document.getElementById('savePayloadModalHost');

  const btnVpnOpenServers = document.getElementById('btnVpnOpenServers');
  const vpnServerListModal = document.getElementById('vpnServerListModal');

  // 🔒 V2Ray Card 404 Interceptor (Master Prompt Section 35)
  netmodVpnCard?.addEventListener('click', (e) => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_VPN')) {
      e.preventDefault();
      e.stopPropagation();
      const vpnToggle = document.getElementById('vpnMasterToggle');
      if (vpnToggle) vpnToggle.checked = false;
      if (window.showInApp404) {
        window.showInApp404('V2Ray VPN Engine', 'HTTP 404: The Native Anti-Censorship V2Ray/Xray VPN tunneling service endpoint is restricted under this installation. Please activate a PRO License Key to enable VPN routing.');
      }
      return false;
    }
  }, true);
  const btnCloseServerListModal = document.getElementById('btnCloseServerListModal');
  const vpnServerListItems = document.getElementById('vpnServerListItems');
  const serverListSearchInput = document.getElementById('serverListSearchInput');
  const serverListCountSubtitle = document.getElementById('serverListCountSubtitle');
  const btnTestAllServers = document.getElementById('btnTestAllServers');

  // Diagnostics Modal References
  const btnVpnOpenDiag = document.getElementById('btnVpnOpenDiag');
  const vpnDiagnosticsModal = document.getElementById('vpnDiagnosticsModal');
  const btnCloseDiagnosticsModal = document.getElementById('btnCloseDiagnosticsModal');
  const btnRunFullDiagnostics = document.getElementById('btnRunFullDiagnostics');
  const diagTestResultsBox = document.getElementById('diagTestResultsBox');
  const diagCoreState = document.getElementById('diagCoreState');
  const diagTunState = document.getElementById('diagTunState');
  const diagVpnState = document.getElementById('diagVpnState');
  const diagDnsState = document.getElementById('diagDnsState');
  const diagServerState = document.getElementById('diagServerState');
  const diagInternetState = document.getElementById('diagInternetState');

  // Routing Modal References
  const btnVpnOpenRouting = document.getElementById('btnVpnOpenRouting');
  const vpnRoutingModal = document.getElementById('vpnRoutingModal');
  const btnCloseRoutingModal = document.getElementById('btnCloseRoutingModal');
  const selectRoutingMode = document.getElementById('selectRoutingMode');
  const selectVpnDns = document.getElementById('selectVpnDns');
  const inputVpnMtu = document.getElementById('inputVpnMtu');
  const chkAutoReconnect = document.getElementById('chkAutoReconnect');
  const perAppListSection = document.getElementById('perAppListSection');
  const appFilterInput = document.getElementById('appFilterInput');
  const vpnInstalledAppsList = document.getElementById('vpnInstalledAppsList');
  const btnSaveRoutingConfig = document.getElementById('btnSaveRoutingConfig');

  // Subscriptions Modal References
  const btnVpnOpenSubs = document.getElementById('btnVpnOpenSubs');
  const vpnSubscriptionsModal = document.getElementById('vpnSubscriptionsModal');
  const btnCloseSubsModal = document.getElementById('btnCloseSubsModal');
  const inputSubName = document.getElementById('inputSubName');
  const inputSubUrl = document.getElementById('inputSubUrl');
  const btnAddSubscription = document.getElementById('btnAddSubscription');
  const vpnSubscriptionsList = document.getElementById('vpnSubscriptionsList');

  // Audit Logs Modal References
  const btnVpnOpenLogs = document.getElementById('btnVpnOpenLogs');
  const vpnLogsModal = document.getElementById('vpnLogsModal');
  const btnCloseLogsModal = document.getElementById('btnCloseLogsModal');
  const btnCopySafeLogs = document.getElementById('btnCopySafeLogs');
  const btnClearSafeLogs = document.getElementById('btnClearSafeLogs');
  const vpnLogLinesContainer = document.getElementById('vpnLogLinesContainer');

  // QR Scanner Modal References
  const btnVpnOpenQrScan = document.getElementById('btnVpnOpenQrScan');
  const vpnQrScanModal = document.getElementById('vpnQrScanModal');
  const btnCloseQrModal = document.getElementById('btnCloseQrModal');
  const inputQrImageFile = document.getElementById('inputQrImageFile');
  const dropQrZone = document.getElementById('dropQrZone');
  const qrPreviewCard = document.getElementById('qrPreviewCard');
  const qrDecodedFlag = document.getElementById('qrDecodedFlag');
  const qrDecodedProto = document.getElementById('qrDecodedProto');
  const qrDecodedName = document.getElementById('qrDecodedName');
  const qrDecodedHost = document.getElementById('qrDecodedHost');
  const btnImportQrOnly = document.getElementById('btnImportQrOnly');
  const btnImportAndTestQr = document.getElementById('btnImportAndTestQr');

  // VPN Runtime State
  let isVpnConnected = false;
  let activeCountryFlag = '🌐';
  let activeCountryCode = 'VPN';
  let activeServerName = 'V2Ray Tunnel';
  let activeProto = 'VLESS';
  let activeHost = '';
  let activePort = 443;
  let activePing = 0;
  let pingInterval = null;
  let trafficStatsTimer = null;
  let consecutivePingFailures = 0;

  // Staged Payload Buffers
  let pendingParsedNode = null;
  let pendingPayloadToSave = '';
  let pendingQrDecodedPayload = '';
  let pendingQrDecodedNode = null;

  // Profiles & Apps Memory
  let userSavedConfigs = [];
  let loadedInstalledApps = [];
  let currentSelectedPackages = new Set();

  // ============================================================================
  // 8. PROFILE STORAGE & QUICK PRESETS
  // ============================================================================

  /**
   * Loads saved V2Ray/VLESS profiles from Native Plugin or LocalStorage fallback.
   */
  async function loadProfilesFromNative() {
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.getProfiles) {
        const res = await window.Capacitor.Plugins.NativeVpn.getProfiles();
        if (res && Array.isArray(res.profiles)) {
          userSavedConfigs = res.profiles;
          renderSavedPayloadsUI();
          return;
        }
      }
    } catch (_) { }

    try {
      userSavedConfigs = JSON.parse(localStorage.getItem('cdl_saved_vpn_configs') || '[]');
    } catch (_) {
      userSavedConfigs = [];
    }
    renderSavedPayloadsUI();
  }

  /**
   * Renders quick preset pills inside the settings card.
   */
  function renderSavedPayloadsUI() {
    if (!savedPayloadsList) return;
    if (userSavedConfigs.length === 0) {
      savedPayloadsList.innerHTML = `<span style="font-size: 0.72rem; color: #64748b; padding: 4px 8px;"><i class="fa-solid fa-circle-info"></i> Paste your VLESS link & click "Save" to add quick profiles.</span>`;
      return;
    }

    savedPayloadsList.innerHTML = userSavedConfigs.map(cfg => {
      const isCurrent = inputV2RayPayload && inputV2RayPayload.value.trim() === (cfg.rawPayload || cfg.payload || '').trim();
      return `
        <div class="vpn-preset-btn ${isCurrent ? 'active' : ''}" style="display: inline-flex; align-items: center; gap: 6px;" data-id="${cfg.id}">
          <span class="btn-select-cfg" style="cursor: pointer;">${cfg.countryFlag || '🌐'} ${cfg.name}</span>
          <i class="fa-solid fa-xmark btn-delete-cfg" style="cursor: pointer; opacity: 0.6; font-size: 0.7em; margin-left: 2px;" data-del="${cfg.id}" title="Remove"></i>
        </div>
      `;
    }).join('');

    // Attach selection handlers
    savedPayloadsList.querySelectorAll('.btn-select-cfg').forEach(el => {
      el.addEventListener('click', async () => {
        const parent = el.closest('.vpn-preset-btn');
        const id = parent?.getAttribute('data-id');
        const found = userSavedConfigs.find(c => c.id === id);
        if (found && inputV2RayPayload) {
          const payload = found.rawPayload || found.payload || '';
          inputV2RayPayload.value = payload;
          renderSavedPayloadsUI();
          await handleVpnConnect(true, payload);
        }
      });
    });

    // Attach deletion handlers
    savedPayloadsList.querySelectorAll('.btn-delete-cfg').forEach(el => {
      el.addEventListener('click', async (e) => {
        e.stopPropagation();
        const delId = el.getAttribute('data-del');
        try {
          if (window.Capacitor?.Plugins?.NativeVpn?.deleteProfile) {
            await window.Capacitor.Plugins.NativeVpn.deleteProfile({ id: delId });
          }
        } catch (_) { }
        userSavedConfigs = userSavedConfigs.filter(c => c.id !== delId);
        localStorage.setItem('cdl_saved_vpn_configs', JSON.stringify(userSavedConfigs));
        renderSavedPayloadsUI();
        window.showToast('Profile removed', 'info');
      });
    });
  }

  // ============================================================================
  // 9. REAL-TIME TRAFFIC POLLING & STATE SYNCHRONIZATION
  // ============================================================================

  /**
   * High-fidelity Speed Formatter - converts Mbps float to clean, readable telemetry.
   */
  function formatSpeedStr(mbps, fallbackFormatted) {
    if (fallbackFormatted) return fallbackFormatted;
    const val = Number(mbps || 0);
    if (val <= 0) return '0.0 Mbps';
    if (val >= 1.0) return `${val.toFixed(1)} Mbps`;
    const kbs = Math.round(val * 1000 / 8);
    if (kbs >= 1) return `${kbs} KB/s`;
    return `${val.toFixed(2)} Mbps`;
  }

  /**
   * Starts high-precision 1-second interval querying live upload/download bytes.
   */
  function startTrafficPolling() {
    if (trafficStatsTimer) return;
    if (document.hidden) return;
    trafficStatsTimer = setInterval(async () => {
      if (!isVpnConnected || document.hidden) {
        stopTrafficPolling();
        return;
      }
      try {
        if (window.Capacitor?.Plugins?.NativeVpn?.getStatus) {
          const s = await window.Capacitor.Plugins.NativeVpn.getStatus();
          if (s && s.isConnected) {
            if (vpnDownloadBytes) vpnDownloadBytes.textContent = s.downloadFormatted || '0.0 MB';
            if (vpnUploadBytes) vpnUploadBytes.textContent = s.uploadFormatted || '0.0 MB';
            if (vpnDownloadSpeed) {
              vpnDownloadSpeed.textContent = s.downloadSpeedFormatted || formatSpeedStr(s.downloadMbps);
            }
            if (vpnUploadSpeed) {
              vpnUploadSpeed.textContent = s.uploadSpeedFormatted || formatSpeedStr(s.uploadMbps);
            }
          }
        }
      } catch (_) { }
    }, 1000);
  }

  function stopTrafficPolling() {
    if (trafficStatsTimer) {
      clearInterval(trafficStatsTimer);
      trafficStatsTimer = null;
    }
  }

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      stopTrafficPolling();
    } else if (isVpnConnected) {
      startTrafficPolling();
    }
  });

  /**
   * Dedicated non-destructive notification banner for NetMod card.
   */
  function showNetmodMessage(msg, type = 'info') {
    if (!netmodMsgStrip || !netmodMsgText) return;
    if (!msg) {
      netmodMsgStrip.classList.add('hidden');
      return;
    }
    netmodMsgStrip.className = `netmod-msg-strip ${type}`;
    netmodMsgText.textContent = msg;
    if (netmodMsgIcon) {
      netmodMsgIcon.className = type === 'error' ? 'fa-solid fa-circle-exclamation' :
        type === 'warning' ? 'fa-solid fa-triangle-exclamation' :
        type === 'success' ? 'fa-solid fa-circle-check' : 'fa-solid fa-circle-info';
    }
  }

  /**
   * Authoritative State Renderer - Updates DOM, Badges, and Global Events.
   */
  function renderVpnState(connected, flag, code, pingMs, serverName, downFormatted, upFormatted, errorMsg, downMbps = 0, upMbps = 0) {
    isVpnConnected = connected;
    activePing = pingMs || 0;
    if (flag) activeCountryFlag = flag;
    if (code) activeCountryCode = code;
    if (serverName) activeServerName = serverName;

    if (connected) {
      startTrafficPolling();
      showNetmodMessage('');
    } else if (trafficStatsTimer) {
      clearInterval(trafficStatsTimer);
      trafficStatsTimer = null;
    }

    // Master Switch Toggle
    if (vpnMasterToggle) vpnMasterToggle.checked = connected;

    // NetMod Compact Card & Node Selector State
    if (netmodVpnCard) netmodVpnCard.classList.toggle('connected', connected);
    if (netmodNodeSelector) netmodNodeSelector.classList.toggle('connected', connected);
    if (netmodNodeFlag) netmodNodeFlag.textContent = activeCountryFlag || '🌐';
    if (netmodNodeTitle) netmodNodeTitle.textContent = activeServerName || 'Select VPN Server Node';
    if (netmodNodeSub) {
      netmodNodeSub.textContent = activeHost ? `${activeHost}:${activePort} • ${activeProto} Core` : 'Tap to open server list • Xray Core';
    }
    if (netmodProtoPill && activeProto) {
      netmodProtoPill.textContent = activeProto.toUpperCase();
    }

    // Status Box in Settings
    if (vpnStatusBox) {
      vpnStatusBox.classList.toggle('connected', connected);
      vpnStatusBox.classList.toggle('offline', !connected);
    }
    if (vpnStatusLabel) {
      if (connected) {
        vpnStatusLabel.textContent = 'CONNECTED';
      } else if (errorMsg) {
        vpnStatusLabel.textContent = 'ERROR';
        showNetmodMessage(errorMsg, 'error');
      } else {
        vpnStatusLabel.textContent = 'OFFLINE';
      }
    }
    if (vpnLatencyBadge) {
      if (connected && activePing > 0) {
        vpnLatencyBadge.innerHTML = `<i class="fa-solid fa-gauge-high" style="color: #2ed573;"></i> ${activePing}ms ⚡`;
      } else if (connected) {
        vpnLatencyBadge.innerHTML = '<i class="fa-solid fa-gauge-high" style="color: #ffa502;"></i> ... ms';
      } else {
        vpnLatencyBadge.innerHTML = '<i class="fa-solid fa-gauge-high"></i> -- ms';
      }
    }

    // Live Traffic Counters
    if (vpnDownloadBytes) vpnDownloadBytes.textContent = downFormatted || '0.0 MB';
    if (vpnUploadBytes) vpnUploadBytes.textContent = upFormatted || '0.0 MB';
    if (vpnDownloadSpeed) vpnDownloadSpeed.textContent = formatSpeedStr(downMbps);
    if (vpnUploadSpeed) vpnUploadSpeed.textContent = formatSpeedStr(upMbps);

    // Global Header Badge
    if (vpnHeaderBadge) {
      if (connected) {
        vpnHeaderBadge.classList.remove('hidden');
        if (vpnHeaderFlag) vpnHeaderFlag.textContent = activeCountryFlag || '🌐';
        if (vpnHeaderCode) vpnHeaderCode.textContent = activeCountryCode || 'VPN';
        if (vpnHeaderPing) vpnHeaderPing.textContent = activePing > 0 ? `${activePing}ms ⚡` : 'ON ⚡';
      } else {
        vpnHeaderBadge.classList.add('hidden');
      }
    }

    // Broadcast change event when state changes
    const stateKey = `${connected}:${activeCountryCode}:${activeCountryFlag}`;
    if (window._lastVpnStateKey !== stateKey) {
      window._lastVpnStateKey = stateKey;
      localStorage.setItem('cdl_vpn_enabled', connected ? 'true' : 'false');
      localStorage.setItem('cdl_vpn_code', activeCountryCode);
      localStorage.setItem('cdl_vpn_flag', activeCountryFlag);
      localStorage.setItem('cdl_vpn_name', activeServerName);
      window.dispatchEvent(new CustomEvent('cdl:vpn-state-changed', {
        detail: { isVpn: connected, code: activeCountryCode, flag: activeCountryFlag, name: activeServerName }
      }));
    }
  }

  // ============================================================================
  // 10. HEALTH MONITORING & CONNECTION LIFECYCLE
  // ============================================================================

  /**
   * Health check timer measuring socket latency every 4 seconds.
   */
  async function performHealthCheck() {
    if (!isVpnConnected) return;
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.measurePing) {
        const res = await window.Capacitor.Plugins.NativeVpn.measurePing({ host: activeHost, port: activePort });
        if (res) {
          if (res.status === 'online' && res.pingMs > 0) {
            consecutivePingFailures = 0;
            activePing = res.pingMs;
            renderVpnState(true, activeCountryFlag, activeCountryCode, activePing, activeServerName, res.downloadFormatted, res.uploadFormatted, '', res.downloadMbps, res.uploadMbps);
          } else {
            consecutivePingFailures++;
            if (consecutivePingFailures >= 5) {
              renderVpnState(true, activeCountryFlag, activeCountryCode, 0, activeServerName, res.downloadFormatted, res.uploadFormatted, '⚠️ High Latency / Network Jitter', res.downloadMbps, res.uploadMbps);
              if (vpnHeaderPing) vpnHeaderPing.textContent = '⚠️ Slow';
            } else {
              renderVpnState(true, activeCountryFlag, activeCountryCode, activePing, activeServerName, res.downloadFormatted, res.uploadFormatted, '', res.downloadMbps, res.uploadMbps);
            }
          }
        }
      }
    } catch (_) { }
  }

  /**
   * Primary entry point for initiating or terminating VPN tunnels.
   */
  async function handleVpnConnect(enable, customPayload = '') {
    const payload = (customPayload || (inputV2RayPayload?.value || '')).trim();

    if (enable && !payload) {
      renderVpnState(false, '🌐', 'VPN', 0, '', '0.0 MB', '0.0 MB', 'Please paste a valid V2Ray / VLESS payload');
      showNetmodMessage('Please paste or select a VPN node', 'warning');
      window.showToast('Please paste a valid V2Ray / VLESS payload', 'warning');
      return;
    }

    if (enable) {
      if (vpnStatusLabel) vpnStatusLabel.textContent = 'CONNECTING';
      showNetmodMessage('Testing Handshake & Establishing Xray Tunnel...', 'info');
    }

    try {
      if (window.Capacitor?.Plugins?.NativeVpn) {
        if (enable) {
          const res = await window.Capacitor.Plugins.NativeVpn.connect({ payload: payload });
          if (res && res.success && res.isConnected) {
            activeHost = res.server;
            activePort = res.port;
            if (res.protocol) activeProto = res.protocol.toUpperCase();
            renderVpnState(true, res.countryFlag, res.countryCode, res.latencyMs, res.countryName || res.server, res.downloadFormatted, res.uploadFormatted, '', res.downloadMbps, res.uploadMbps);
            window.showToast(`🛡️ Connected: ${res.countryFlag} ${res.countryName || res.server} (${res.latencyMs}ms)!`, 'success');
            showNetmodMessage(`Connected via Xray Core • ${res.latencyMs || 0}ms`, 'success');
            startPingTimer();
            checkBatteryOptimization();
          } else {
            const err = (res && (res.errorMessage || res.errorCode)) ? res.errorMessage : 'Server unreachable / Connection failed';
            renderVpnState(false, '🌐', 'VPN', 0, '', '0.0 MB', '0.0 MB', err);
            window.showToast(`🔴 Connection Failed: ${err}`, 'error');
            stopPingTimer();
          }
        } else {
          await window.Capacitor.Plugins.NativeVpn.disconnect();
          renderVpnState(false, activeCountryFlag, activeCountryCode, 0, activeServerName, '0.0 MB', '0.0 MB');
          showNetmodMessage('VPN Tunnel Disconnected', 'info');
          window.showToast('VPN Disconnected', 'info');
          stopPingTimer();
        }
        return;
      }

      renderVpnState(false, '🌐', 'VPN', 0, '', '0.0 MB', '0.0 MB', 'Native VPN engine is not available on this platform');
      window.showToast('Native VPN engine not supported on browser preview', 'warning');
    } catch (err) {
      renderVpnState(false, '🌐', 'VPN', 0, '', '0.0 MB', '0.0 MB', err.message);
      window.showToast('Connection Error: ' + err.message, 'error');
      stopPingTimer();
    }
  }

  async function checkBatteryOptimization() {
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.isIgnoringBatteryOptimizations) {
        const res = await window.Capacitor.Plugins.NativeVpn.isIgnoringBatteryOptimizations();
        const banner = document.getElementById('netmodBgSyncBanner');
        if (banner) {
          if (res && res.isIgnoring === false) {
            banner.style.display = 'flex';
            banner.classList.remove('hidden');
          } else {
            banner.style.display = 'none';
            banner.classList.add('hidden');
          }
        }
      }
    } catch (_) {}
  }

  const btnEnableBgSync = document.getElementById('btnEnableBgSync');
  btnEnableBgSync?.addEventListener('click', async () => {
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.requestBatteryOptimizationExemption) {
        await window.Capacitor.Plugins.NativeVpn.requestBatteryOptimizationExemption();
        const banner = document.getElementById('netmodBgSyncBanner');
        if (banner) {
          banner.style.display = 'none';
          banner.classList.add('hidden');
        }
        window.showToast('Requested unrestricted background operation for all apps', 'success');
      }
    } catch (_) {}
  });

  function startPingTimer() {
    stopPingTimer();
    pingInterval = setInterval(performHealthCheck, 4000);
  }

  function stopPingTimer() {
    if (pingInterval) {
      clearInterval(pingInterval);
      pingInterval = null;
    }
  }

  // Event Listeners for VPN Controls
  async function getClipboardText() {
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.readClipboard) {
        const res = await window.Capacitor.Plugins.NativeVpn.readClipboard();
        if (res && res.success && res.text) {
          return res.text.trim();
        }
      }
    } catch (_) { }

    try {
      if (navigator.clipboard?.readText) {
        const text = await navigator.clipboard.readText();
        if (text) return text.trim();
      }
    } catch (_) { }

    return '';
  }

  vpnMasterToggle?.addEventListener('change', async (e) => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_VPN')) {
      e.preventDefault();
      vpnMasterToggle.checked = false;
      if (window.showInApp404) {
        window.showInApp404('V2Ray VPN Engine', 'HTTP 404: The Native Anti-Censorship V2Ray/Xray VPN tunneling service endpoint is restricted under this installation. Please activate a PRO License Key to enable VPN routing.');
      }
      return;
    }
    const shouldEnable = e.target.checked;
    if (!shouldEnable) {
      await handleVpnConnect(false);
      return;
    }

    let payload = (inputV2RayPayload?.value || '').trim();

    if (!payload && userSavedConfigs.length > 0) {
      const activeOrFirst = userSavedConfigs.find(c => c.isFavorite) || userSavedConfigs[0];
      payload = (activeOrFirst.rawPayload || activeOrFirst.payload || '').trim();
      if (inputV2RayPayload) inputV2RayPayload.value = payload;
    }

    if (!payload) {
      const clip = await getClipboardText();
      const validPrefixes = ['vmess://', 'vless://', 'trojan://', 'ss://', 'ssr://', 'tuic://', 'hy2://', 'hysteria2://', 'hysteria://'];
      if (clip && validPrefixes.some(p => clip.toLowerCase().startsWith(p))) {
        payload = clip;
        if (inputV2RayPayload) inputV2RayPayload.value = payload;
      }
    }

    if (!payload) {
      vpnMasterToggle.checked = false;
      netmodUriDrawer?.classList.remove('hidden');
      inputV2RayPayload?.focus();
      window.showToast('Please paste a VPN server link (SS, SSR, Trojan, VLESS, VMess, TUIC, Hy2) or choose a server', 'warning');
      showNetmodMessage('Please paste or select a VPN server node', 'warning');
      return;
    }

    await handleVpnConnect(true, payload);
  });

  vpnHeaderBadge?.addEventListener('click', () => {
    if (window.openSettingsModal) window.openSettingsModal();
  });

  btnSaveV2RayPayload?.addEventListener('click', async () => {
    const payload = (inputV2RayPayload?.value || '').trim();
    if (!payload) {
      window.showToast('Please paste a valid VPN server payload (SS/SSR/Trojan/VLESS/VMess/TUIC/Hy2)', 'warning');
      return;
    }

    // Auto-save to saved profiles if valid
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.parsePayload) {
        const parsed = await window.Capacitor.Plugins.NativeVpn.parsePayload({ payload });
        if (parsed && parsed.valid) {
          if (parsed.hasAutoFixes && parsed.autoFixes && parsed.autoFixes.length > 0) {
            window.showToast(`✨ Auto-repaired ${parsed.autoFixes.length} setting(s): ${parsed.autoFixes[0]}`, 'success');
            showNetmodMessage(`Auto-repaired: ${parsed.autoFixes.join(' • ')}`, 'info');
          }
          const alreadyExists = userSavedConfigs.some(c => (c.rawPayload || c.payload || '').trim() === payload);
          if (!alreadyExists && window.Capacitor?.Plugins?.NativeVpn?.saveProfile) {
            await window.Capacitor.Plugins.NativeVpn.saveProfile({
              id: 'node_' + Date.now(),
              name: parsed.remark || parsed.countryName || `${parsed.protocol} ${parsed.host}`,
              payload: payload,
              rawPayload: payload,
              protocol: parsed.protocol || 'VLESS',
              host: parsed.host,
              port: parsed.port,
              security: parsed.security || 'none',
              transport: parsed.transport || 'tcp',
              countryFlag: parsed.countryFlag || '🌐',
              countryCode: parsed.countryCode || 'VPN'
            });
            await loadProfilesFromNative();
          }
        }
      }
    } catch (_) { }

    await handleVpnConnect(true, payload);
  });

  inputV2RayPayload?.addEventListener('input', () => {
    const currentVal = (inputV2RayPayload?.value || '').trim();
    if (isVpnConnected && currentVal !== pendingPayloadToSave) {
      showNetmodMessage('Payload edited — Tap "Connect" to apply new node', 'warning');
    }
  });

  btnPasteV2Ray?.addEventListener('click', async () => {
    try {
      const payload = await getClipboardText();
      netmodUriDrawer?.classList.remove('hidden');

      if (!payload) {
        window.showToast('Clipboard is empty. Please enter your VPN link below.', 'info');
        inputV2RayPayload?.focus();
        return;
      }

      if (inputV2RayPayload) inputV2RayPayload.value = payload;

      // Parse payload immediately via Native plugin
      let parsed = null;
      try {
        if (window.Capacitor?.Plugins?.NativeVpn?.parsePayload) {
          parsed = await window.Capacitor.Plugins.NativeVpn.parsePayload({ payload });
        }
      } catch (_) { }

      if (parsed && parsed.valid) {
        activeCountryFlag = parsed.countryFlag || '🌐';
        activeCountryCode = parsed.countryCode || 'VPN';
        activeServerName = parsed.remark || parsed.countryName || parsed.host || 'Imported Node';
        activeHost = parsed.host || '';
        activePort = parsed.port || 443;
        activeProto = (parsed.protocol || 'VLESS').toUpperCase();

        if (netmodNodeFlag) netmodNodeFlag.textContent = activeCountryFlag;
        if (netmodNodeTitle) netmodNodeTitle.textContent = activeServerName;
        if (netmodNodeSub) netmodNodeSub.textContent = `${activeHost}:${activePort} • ${activeProto}`;
        if (netmodProtoPill) netmodProtoPill.textContent = activeProto;

        // Auto-save to saved profiles
        try {
          const alreadyExists = userSavedConfigs.some(c => (c.rawPayload || c.payload || '').trim() === payload);
          if (!alreadyExists && window.Capacitor?.Plugins?.NativeVpn?.saveProfile) {
            await window.Capacitor.Plugins.NativeVpn.saveProfile({
              id: 'node_' + Date.now(),
              name: activeServerName,
              payload: payload,
              rawPayload: payload,
              protocol: activeProto,
              host: activeHost,
              port: activePort,
              security: parsed.security || 'none',
              transport: parsed.transport || 'tcp',
              countryFlag: activeCountryFlag,
              countryCode: activeCountryCode
            });
            await loadProfilesFromNative();
          }
        } catch (_) { }

        if (parsed.hasAutoFixes && parsed.autoFixes && parsed.autoFixes.length > 0) {
          window.showToast(`✨ Auto-repaired ${parsed.autoFixes.length} setting(s): ${parsed.autoFixes[0]}`, 'success');
          showNetmodMessage(`Auto-repaired: ${parsed.autoFixes.join(' • ')}`, 'info');
        } else {
          window.showToast(`📋 Imported ${activeCountryFlag} ${activeServerName}! Connecting...`, 'success');
          showNetmodMessage(`Imported ${activeServerName} (${activeProto}). Connecting...`, 'info');
        }
        await handleVpnConnect(true, payload);
      } else {
        window.showToast('📋 Payload pasted from clipboard! Connecting...', 'info');
        showNetmodMessage('Connecting with pasted payload...', 'info');
        await handleVpnConnect(true, payload);
      }
    } catch (_) {
      window.showToast('Please enter your VPN link into the Direct URI drawer.', 'info');
      netmodUriDrawer?.classList.remove('hidden');
      inputV2RayPayload?.focus();
    }
  });

  // NetMod Card Click Handlers
  netmodNodeSelector?.addEventListener('click', () => {
    btnVpnOpenServers?.click();
  });

  btnToggleUriDrawer?.addEventListener('click', () => {
    netmodUriDrawer?.classList.toggle('hidden');
    if (!netmodUriDrawer?.classList.contains('hidden')) {
      inputV2RayPayload?.focus();
    }
  });

  vpnLatencyBadge?.addEventListener('click', async (e) => {
    e.stopPropagation();
    if (window.Capacitor?.Plugins?.NativeVpn?.measurePing && activeHost) {
      vpnLatencyBadge.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> ...';
      try {
        const res = await window.Capacitor.Plugins.NativeVpn.measurePing({ host: activeHost, port: activePort });
        if (res && res.pingMs > 0) {
          activePing = res.pingMs;
          vpnLatencyBadge.innerHTML = `<i class="fa-solid fa-gauge-high" style="color: #2ed573;"></i> ${activePing}ms ⚡`;
          window.showToast(`⚡ Latency: ${activePing}ms`, 'success');
        } else {
          vpnLatencyBadge.innerHTML = '<i class="fa-solid fa-gauge-high" style="color: #ff4757;"></i> Timeout';
          window.showToast('Server ping timed out', 'warning');
        }
      } catch (_) {
        vpnLatencyBadge.innerHTML = '<i class="fa-solid fa-gauge-high"></i> -- ms';
      }
    } else {
      window.showToast('Tap on node to pick a server first', 'info');
    }
  });

  // ============================================================================
  // 11. SAVE PAYLOAD DIALOG MODAL
  // ============================================================================

  function openSavePayloadDialog(payload, parsed) {
    pendingPayloadToSave = payload;
    pendingParsedNode = parsed;

    if (savePayloadModalFlag) savePayloadModalFlag.textContent = parsed?.countryFlag || '🌐';
    if (savePayloadModalProtoBadge) {
      const proto = (parsed?.protocol || 'VLESS').toUpperCase();
      const transport = (parsed?.transport || 'TCP').toUpperCase();
      const sec = (parsed?.security || 'NONE').toUpperCase();
      savePayloadModalProtoBadge.textContent = `${proto} • ${transport} • ${sec}`;
    }
    if (savePayloadModalHost) savePayloadModalHost.textContent = `${parsed?.host || 'remote'}:${parsed?.port || 443}`;

    const defaultName = (parsed && parsed.remark) ?
      parsed.remark :
      `${parsed?.countryFlag || '⚡'} ${parsed?.countryCode || 'VPN'} Profile`;

    if (inputSavePayloadName) {
      inputSavePayloadName.value = defaultName;
    }

    if (savePayloadModal) {
      savePayloadModal.classList.remove('hidden');
      setTimeout(() => inputSavePayloadName?.focus(), 150);
    }
  }

  function closeSavePayloadDialog() {
    if (savePayloadModal) savePayloadModal.classList.add('hidden');
    pendingPayloadToSave = '';
    pendingParsedNode = null;
  }

  btnCloseSavePayloadModal?.addEventListener('click', closeSavePayloadDialog);
  btnCancelSavePayload?.addEventListener('click', closeSavePayloadDialog);
  savePayloadModal?.addEventListener('click', (e) => {
    if (e.target === savePayloadModal) closeSavePayloadDialog();
  });

  btnSavePayloadToList?.addEventListener('click', async () => {
    const payload = (inputV2RayPayload?.value || '').trim();
    if (!payload) {
      window.showToast('Please paste a valid VPN link (SS, SSR, Trojan, VLESS, VMess, TUIC, Hy2) first', 'warning');
      return;
    }

    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.parsePayload) {
        const parsed = await window.Capacitor.Plugins.NativeVpn.parsePayload({ payload });
        if (parsed && parsed.valid) {
          openSavePayloadDialog(payload, parsed);
          return;
        } else {
          window.showToast(`🔴 Invalid Payload: ${parsed?.errorMessage || 'Malformed URI'}`, 'error');
          return;
        }
      }
    } catch (_) { }

    openSavePayloadDialog(payload, { protocol: 'VLESS', host: 'remote', port: 443, countryFlag: '🌐', countryCode: 'VPN' });
  });

  btnConfirmSavePayload?.addEventListener('click', async () => {
    const name = (inputSavePayloadName?.value || '').trim();
    if (!name) {
      window.showToast('Please enter a profile name', 'warning');
      return;
    }
    if (!pendingPayloadToSave) {
      closeSavePayloadDialog();
      return;
    }

    const p = pendingParsedNode;
    let savedToNative = false;
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.saveProfile) {
        await window.Capacitor.Plugins.NativeVpn.saveProfile({
          id: String(Date.now()),
          name: name,
          payload: pendingPayloadToSave,
          rawPayload: pendingPayloadToSave,
          protocol: p?.protocol || 'VLESS',
          host: p?.host || '',
          port: p?.port || 443,
          security: p?.security || 'none',
          transport: p?.transport || 'tcp',
          countryFlag: p?.countryFlag || '🌐',
          countryCode: p?.countryCode || 'VPN'
        });
        savedToNative = true;
      }
    } catch (_) { }

    if (!savedToNative) {
      userSavedConfigs.push({
        id: String(Date.now()),
        name: name,
        rawPayload: pendingPayloadToSave,
        payload: pendingPayloadToSave,
        protocol: p?.protocol || 'VLESS',
        host: p?.host || '',
        port: p?.port || 443,
        security: p?.security || 'none',
        transport: p?.transport || 'tcp',
        countryFlag: p?.countryFlag || '🌐',
        countryCode: p?.countryCode || 'VPN'
      });
      localStorage.setItem('cdl_saved_vpn_configs', JSON.stringify(userSavedConfigs));
    }

    await loadProfilesFromNative();
    closeSavePayloadDialog();
    window.showToast(`🛡️ Profile "${name}" saved!`, 'success');
  });

  // ============================================================================
  // 12. SERVER LIST HUB MODAL (SEARCH, DUP, FAVORITE, PING-ALL)
  // ============================================================================

  function openServerListModal() {
    if (vpnServerListModal) {
      vpnServerListModal.classList.remove('hidden');
      renderServerCards();
    }
  }

  function closeServerListModal() {
    if (vpnServerListModal) vpnServerListModal.classList.add('hidden');
  }

  btnVpnOpenServers?.addEventListener('click', openServerListModal);
  btnCloseServerListModal?.addEventListener('click', closeServerListModal);
  vpnServerListModal?.addEventListener('click', (e) => {
    if (e.target === vpnServerListModal) closeServerListModal();
  });

  function renderServerCards() {
    if (!vpnServerListItems) return;
    const search = (serverListSearchInput?.value || '').trim().toLowerCase();
    const filtered = userSavedConfigs.filter(cfg => {
      if (!search) return true;
      return (cfg.name || '').toLowerCase().includes(search) ||
             (cfg.countryCode || '').toLowerCase().includes(search) ||
             (cfg.host || '').toLowerCase().includes(search) ||
             (cfg.protocol || '').toLowerCase().includes(search);
    });

    if (serverListCountSubtitle) {
      serverListCountSubtitle.textContent = `${userSavedConfigs.length} configured nodes (${filtered.length} matched)`;
    }

    if (filtered.length === 0) {
      vpnServerListItems.innerHTML = `
        <div style="text-align: center; padding: 30px 10px; color: #64748b;">
          <i class="fa-solid fa-server" style="font-size: 2rem; opacity: 0.4; margin-bottom: 8px;"></i>
          <p style="margin: 0; font-size: 0.84rem;">No VPN profiles found.</p>
          <small style="font-size: 0.72rem; color: #475569;">Paste SS, SSR, Trojan, VLESS, VMess, TUIC, or Hysteria2 link or scan QR code.</small>
        </div>
      `;
      return;
    }

    vpnServerListItems.innerHTML = filtered.map(cfg => {
      const payloadVal = cfg.rawPayload || cfg.payload || '';
      const isCurrent = isVpnConnected && inputV2RayPayload && inputV2RayPayload.value.trim() === payloadVal.trim();
      const proto = (cfg.protocol || 'VLESS').toUpperCase();
      const protoClass = proto.toLowerCase();
      const favClass = cfg.isFavorite ? 'active' : '';
      const pingText = cfg.lastLatencyMs && cfg.lastLatencyMs > 0 ? `${cfg.lastLatencyMs}ms` : '-- ms';

      return `
        <div class="vpn-server-card ${isCurrent ? 'active-server' : ''}" data-id="${cfg.id}">
          <span class="vpn-server-flag">${cfg.countryFlag || '🌐'}</span>
          <div class="vpn-server-info">
            <div class="vpn-server-title">
              <span>${cfg.name || 'Unnamed Node'}</span>
              <span class="vpn-proto-tag ${protoClass}">${proto}</span>
            </div>
            <div class="vpn-server-sub">
              ${cfg.host || 'unknown'}:${cfg.port || 443} • ${(cfg.transport || 'tcp').toUpperCase()} • <span class="card-ping-badge" data-host="${cfg.host}" data-port="${cfg.port}">${pingText}</span>
            </div>
          </div>
          <div class="vpn-server-actions">
            <button class="btn-server-action btn-fav ${favClass}" title="Favorite" data-id="${cfg.id}">
              <i class="fa-solid fa-star"></i>
            </button>
            <button class="btn-server-action btn-dup" title="Duplicate" data-id="${cfg.id}">
              <i class="fa-solid fa-clone"></i>
            </button>
            <button class="btn-server-action btn-del" title="Delete" data-id="${cfg.id}">
              <i class="fa-solid fa-trash"></i>
            </button>
            <button class="btn-server-action btn-connect-server" data-payload="${encodeURIComponent(payloadVal)}">
              ${isCurrent ? '<i class="fa-solid fa-check"></i> Active' : '<i class="fa-solid fa-bolt"></i> Connect'}
            </button>
          </div>
        </div>
      `;
    }).join('');

    // Attach card event handlers
    vpnServerListItems.querySelectorAll('.btn-fav').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-id');
        try {
          if (window.Capacitor?.Plugins?.NativeVpn?.toggleFavorite) {
            await window.Capacitor.Plugins.NativeVpn.toggleFavorite({ id });
          }
        } catch (_) { }
        await loadProfilesFromNative();
        renderServerCards();
      });
    });

    vpnServerListItems.querySelectorAll('.btn-dup').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-id');
        try {
          if (window.Capacitor?.Plugins?.NativeVpn?.duplicateProfile) {
            await window.Capacitor.Plugins.NativeVpn.duplicateProfile({ id });
            window.showToast('Profile duplicated', 'success');
          }
        } catch (_) { }
        await loadProfilesFromNative();
        renderServerCards();
      });
    });

    vpnServerListItems.querySelectorAll('.btn-del').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-id');
        try {
          if (window.Capacitor?.Plugins?.NativeVpn?.deleteProfile) {
            await window.Capacitor.Plugins.NativeVpn.deleteProfile({ id });
            window.showToast('Profile deleted', 'info');
          }
        } catch (_) { }
        await loadProfilesFromNative();
        renderServerCards();
      });
    });

    vpnServerListItems.querySelectorAll('.btn-connect-server').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const raw = decodeURIComponent(btn.getAttribute('data-payload') || '');
        if (raw) {
          if (inputV2RayPayload) inputV2RayPayload.value = raw;
          closeServerListModal();
          await handleVpnConnect(true, raw);
        }
      });
    });
  }

  serverListSearchInput?.addEventListener('input', renderServerCards);

  // Benchmarking Latency for All Stored Nodes
  btnTestAllServers?.addEventListener('click', async () => {
    if (!window.Capacitor?.Plugins?.NativeVpn?.measurePing) {
      window.showToast('Ping testing requires Android environment', 'info');
      return;
    }
    const pingBadges = vpnServerListItems?.querySelectorAll('.card-ping-badge');
    if (!pingBadges || pingBadges.length === 0) return;

    btnTestAllServers.disabled = true;
    btnTestAllServers.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Pinging...';

    for (const badge of pingBadges) {
      const host = badge.getAttribute('data-host');
      const port = parseInt(badge.getAttribute('data-port') || '443', 10);
      if (!host) continue;
      badge.innerHTML = '<i class="fa-solid fa-spinner fa-spin" style="font-size: 0.65rem;"></i>';
      try {
        const res = await window.Capacitor.Plugins.NativeVpn.measurePing({ host, port });
        if (res && res.status === 'online' && res.pingMs > 0) {
          badge.textContent = `${res.pingMs}ms`;
          badge.style.color = res.pingMs < 150 ? '#2ed573' : '#ffa502';
        } else {
          badge.textContent = 'Unreachable';
          badge.style.color = '#ff4757';
        }
      } catch (_) {
        badge.textContent = 'Err';
        badge.style.color = '#ff4757';
      }
    }

    btnTestAllServers.disabled = false;
    btnTestAllServers.innerHTML = '<i class="fa-solid fa-gauge-high"></i> Ping All';
    window.showToast('Latency test complete', 'success');
  });

  // ============================================================================
  // 13. 6-LEVEL DIAGNOSTICS MODAL ENGINE
  // ============================================================================

  async function openDiagnosticsModal() {
    if (!vpnDiagnosticsModal) return;
    vpnDiagnosticsModal.classList.remove('hidden');

    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.getStatus) {
        const s = await window.Capacitor.Plugins.NativeVpn.getStatus();
        if (diagCoreState) {
          diagCoreState.textContent = s.isConnected ? 'RUNNING' : 'IDLE';
          diagCoreState.className = `vpn-diag-val ${s.isConnected ? 'ok' : ''}`;
        }
        if (diagTunState) {
          diagTunState.textContent = s.isConnected ? 'TUN0 ACTIVE' : 'DOWN';
          diagTunState.className = `vpn-diag-val ${s.isConnected ? 'ok' : ''}`;
        }
        if (diagVpnState) {
          diagVpnState.textContent = s.isConnected ? 'CONNECTED' : 'DISCONNECTED';
          diagVpnState.className = `vpn-diag-val ${s.isConnected ? 'ok' : ''}`;
        }
        if (diagDnsState) {
          diagDnsState.textContent = 'CONFIGURED';
          diagDnsState.className = 'vpn-diag-val ok';
        }
        if (diagServerState) {
          diagServerState.textContent = s.isConnected && s.server ? `${s.server}:${s.port}` : '--';
        }
        if (diagInternetState) {
          diagInternetState.textContent = s.isConnected && s.latencyMs > 0 ? `${s.latencyMs}ms (HTTP 204 OK)` : '--';
          diagInternetState.className = `vpn-diag-val ${s.isConnected ? 'ok' : ''}`;
        }
      }
    } catch (_) { }
  }

  function closeDiagnosticsModal() {
    if (vpnDiagnosticsModal) vpnDiagnosticsModal.classList.add('hidden');
  }

  btnVpnOpenDiag?.addEventListener('click', openDiagnosticsModal);
  btnCloseDiagnosticsModal?.addEventListener('click', closeDiagnosticsModal);
  vpnDiagnosticsModal?.addEventListener('click', (e) => {
    if (e.target === vpnDiagnosticsModal) closeDiagnosticsModal();
  });

  btnRunFullDiagnostics?.addEventListener('click', async () => {
    const payload = (inputV2RayPayload?.value || (userSavedConfigs[0]?.rawPayload || userSavedConfigs[0]?.payload || '')).trim();
    if (!payload) {
      window.showToast('Please paste or select a VPN node payload first', 'warning');
      return;
    }

    if (diagTestResultsBox) {
      diagTestResultsBox.innerHTML = `
        <div style="display: flex; align-items: center; gap: 8px; color: #00f2fe;">
          <i class="fa-solid fa-spinner fa-spin"></i>
          <span>Executing 6-Level Diagnostics: Syntax ➔ DNS ➔ Socket RTT ➔ Route...</span>
        </div>
      `;
    }

    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.testConfigDiagnostics) {
        const rep = await window.Capacitor.Plugins.NativeVpn.testConfigDiagnostics({ payload });
        let html = '<div style="display: flex; flex-direction: column; gap: 6px;">';

        const step = (num, title, ok, detail) => `
          <div style="display: flex; justify-content: space-between; align-items: center; padding: 4px 0; border-bottom: 1px solid rgba(255,255,255,0.04);">
            <span><strong>Level ${num}:</strong> ${title}</span>
            <span style="color: ${ok ? '#2ed573' : '#ff4757'}; font-weight: 700;">
              ${ok ? '✓ PASS' : '✗ FAIL'} ${detail ? `<small style="color: #94a3b8;">(${detail})</small>` : ''}
            </span>
          </div>
        `;

        html += step(1, 'URI Syntax & Scheme', rep.level1SyntaxOk);
        html += step(2, 'Parameters & Crypto Keys', rep.level2FieldsOk);
        html += step(3, 'Tunnel Handshake Config', rep.level3ConfigOk);
        html += step(4, 'DNS Query & Resolution', rep.level4DnsOk, rep.resolvedIp || '');
        html += step(5, 'Direct TCP Handshake Socket RTT', rep.level5ServerReachable, rep.realLatencyMs > 0 ? `${rep.realLatencyMs}ms` : '');
        html += step(6, 'Layer-4 HTTP 204 Route Verification', rep.level6TunnelOk);

        if (!rep.level6TunnelOk && rep.failureReason) {
          html += `
            <div style="margin-top: 8px; padding: 8px; background: rgba(239,68,68,0.15); border-radius: 8px; border: 1px solid rgba(239,68,68,0.3); color: #f87171;">
              <strong>${rep.failureStage || 'DIAGNOSTIC_FAILURE'}:</strong> ${rep.failureReason} (${rep.failureCode || 'CODE_ERR'})
            </div>
          `;
        } else if (rep.level6TunnelOk) {
          html += `
            <div style="margin-top: 8px; padding: 8px; background: rgba(46,213,115,0.15); border-radius: 8px; border: 1px solid rgba(46,213,115,0.3); color: #2ed573;">
              <i class="fa-solid fa-circle-check"></i> Node is 100% verified and ready for high-speed zero-leak tunneling!
            </div>
          `;
        }

        html += '</div>';
        if (diagTestResultsBox) diagTestResultsBox.innerHTML = html;
        return;
      }

      if (diagTestResultsBox) {
        diagTestResultsBox.innerHTML = '<span style="color: #ffa502;">Diagnostics plugin unavailable in browser sandbox. Test inside native Android app.</span>';
      }
    } catch (err) {
      if (diagTestResultsBox) {
        diagTestResultsBox.innerHTML = `<span style="color: #ff4757;">Diagnostic Error: ${err.message}</span>`;
      }
    }
  });

  // ============================================================================
  // 14. ROUTING & PER-APP SPLIT TUNNELING
  // ============================================================================

  async function openRoutingModal() {
    if (!vpnRoutingModal) return;
    vpnRoutingModal.classList.remove('hidden');

    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.getRoutingConfig) {
        const cfg = await window.Capacitor.Plugins.NativeVpn.getRoutingConfig();
        if (selectRoutingMode) selectRoutingMode.value = cfg.mode || 'FULL_VPN';
        if (selectVpnDns) {
          const dnsKey = `${cfg.primaryDns || '1.1.1.1'},${cfg.secondaryDns || '8.8.8.8'}`;
          selectVpnDns.value = dnsKey;
        }
        if (inputVpnMtu) inputVpnMtu.value = cfg.mtu || 1400;
        if (chkAutoReconnect) chkAutoReconnect.checked = cfg.autoReconnect !== false;

        currentSelectedPackages = new Set(cfg.selectedPackages || []);
        updatePerAppSectionVisibility();
      }

      if (window.Capacitor?.Plugins?.NativeVpn?.getInstalledApps) {
        const res = await window.Capacitor.Plugins.NativeVpn.getInstalledApps();
        loadedInstalledApps = res?.apps || [];
        renderAppsList();
      }
    } catch (_) { }
  }

  function closeRoutingModal() {
    if (vpnRoutingModal) vpnRoutingModal.classList.add('hidden');
  }

  function updatePerAppSectionVisibility() {
    const mode = selectRoutingMode?.value || 'FULL_VPN';
    if (perAppListSection) {
      if (mode === 'PER_APP_ALLOW' || mode === 'PER_APP_DISALLOW') {
        perAppListSection.classList.remove('hidden');
      } else {
        perAppListSection.classList.add('hidden');
      }
    }
  }

  selectRoutingMode?.addEventListener('change', updatePerAppSectionVisibility);

  function renderAppsList() {
    if (!vpnInstalledAppsList) return;
    const filter = (appFilterInput?.value || '').trim().toLowerCase();
    const filtered = loadedInstalledApps.filter(a => {
      if (!filter) return true;
      return (a.appName || '').toLowerCase().includes(filter) ||
             (a.packageName || '').toLowerCase().includes(filter);
    });

    vpnInstalledAppsList.innerHTML = filtered.map(app => {
      const isChecked = currentSelectedPackages.has(app.packageName);
      return `
        <div class="vpn-app-row">
          <div>
            <div class="vpn-app-name">${app.appName || app.packageName}</div>
            <div class="vpn-app-pkg">${app.packageName}</div>
          </div>
          <input type="checkbox" class="chk-vpn-app" data-pkg="${app.packageName}" ${isChecked ? 'checked' : ''}>
        </div>
      `;
    }).join('');

    vpnInstalledAppsList.querySelectorAll('.chk-vpn-app').forEach(chk => {
      chk.addEventListener('change', (e) => {
        const pkg = chk.getAttribute('data-pkg');
        if (e.target.checked) {
          currentSelectedPackages.add(pkg);
        } else {
          currentSelectedPackages.delete(pkg);
        }
      });
    });
  }

  appFilterInput?.addEventListener('input', renderAppsList);
  btnVpnOpenRouting?.addEventListener('click', openRoutingModal);
  btnCloseRoutingModal?.addEventListener('click', closeRoutingModal);
  vpnRoutingModal?.addEventListener('click', (e) => {
    if (e.target === vpnRoutingModal) closeRoutingModal();
  });

  btnSaveRoutingConfig?.addEventListener('click', async () => {
    const mode = selectRoutingMode?.value || 'FULL_VPN';
    const dnsParts = (selectVpnDns?.value || '1.1.1.1,8.8.8.8').split(',');
    const primaryDns = dnsParts[0]?.trim() || '1.1.1.1';
    const secondaryDns = dnsParts[1]?.trim() || '8.8.8.8';
    const mtu = parseInt(inputVpnMtu?.value || '1400', 10);
    const autoReconnect = chkAutoReconnect ? chkAutoReconnect.checked : true;
    const selectedPackages = Array.from(currentSelectedPackages);

    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.saveRoutingConfig) {
        await window.Capacitor.Plugins.NativeVpn.saveRoutingConfig({
          mode,
          primaryDns,
          secondaryDns,
          mtu,
          autoReconnect,
          selectedPackages
        });
        window.showToast('Routing configuration saved!', 'success');
        closeRoutingModal();
        return;
      }
    } catch (err) {
      window.showToast(`Failed to save routing: ${err.message}`, 'error');
    }
  });

  // ============================================================================
  // 15. SUBSCRIPTION PACK MANAGER & AUTO NODE SYNC
  // ============================================================================

  async function openSubscriptionsModal() {
    if (!vpnSubscriptionsModal) return;
    vpnSubscriptionsModal.classList.remove('hidden');
    await renderSubscriptionsList();
  }

  function closeSubscriptionsModal() {
    if (vpnSubscriptionsModal) vpnSubscriptionsModal.classList.add('hidden');
  }

  async function renderSubscriptionsList() {
    if (!vpnSubscriptionsList) return;
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.getSubscriptions) {
        const res = await window.Capacitor.Plugins.NativeVpn.getSubscriptions();
        const subs = res?.subscriptions || [];
        if (subs.length === 0) {
          vpnSubscriptionsList.innerHTML = `
            <div style="text-align: center; padding: 20px; color: #64748b; font-size: 0.8rem;">
              <i class="fa-solid fa-rss" style="font-size: 1.6rem; opacity: 0.4; margin-bottom: 6px;"></i>
              <p style="margin: 0;">No active subscriptions.</p>
              <small style="color: #475569;">Add an HTTPS link to automatically sync node packs.</small>
            </div>
          `;
          return;
        }

        vpnSubscriptionsList.innerHTML = subs.map(s => `
          <div style="background: rgba(0,0,0,0.3); border: 1px solid rgba(255,255,255,0.08); border-radius: 10px; padding: 10px 12px; margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; gap: 8px;">
            <div style="flex: 1; min-width: 0;">
              <div style="font-size: 0.84rem; font-weight: 700; color: #fff;">${s.name || 'Subscription'}</div>
              <div style="font-size: 0.7rem; color: #94a3b8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${s.url}</div>
              <div style="font-size: 0.65rem; color: #f59e0b; margin-top: 2px;">
                ${s.profileCount || 0} nodes • Updated: ${s.lastUpdated ? new Date(s.lastUpdated).toLocaleDateString() : 'Never'}
              </div>
            </div>
            <div style="display: flex; gap: 6px;">
              <button class="btn-micro-action btn-sync-sub" data-id="${s.id}" title="Sync Nodes" style="background: rgba(245,158,11,0.15); border: 1px solid rgba(245,158,11,0.3); color: #f59e0b; border-radius: 6px; padding: 4px 8px; cursor: pointer;">
                <i class="fa-solid fa-rotate"></i> Sync
              </button>
              <button class="btn-micro-action btn-del-sub" data-id="${s.id}" title="Delete" style="background: rgba(239,68,68,0.15); border: 1px solid rgba(239,68,68,0.3); color: #f87171; border-radius: 6px; padding: 4px 8px; cursor: pointer;">
                <i class="fa-solid fa-trash"></i>
              </button>
            </div>
          </div>
        `).join('');

        vpnSubscriptionsList.querySelectorAll('.btn-sync-sub').forEach(b => {
          b.addEventListener('click', async () => {
            const id = b.getAttribute('data-id');
            b.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
            try {
              const r = await window.Capacitor.Plugins.NativeVpn.updateSubscription({ id });
              if (r && r.success) {
                window.showToast(`Subscription synced! ${r.message || ''}`, 'success');
              } else {
                window.showToast(`Sync failed: ${r?.message || 'Network error'}`, 'error');
              }
            } catch (_) { }
            await loadProfilesFromNative();
            await renderSubscriptionsList();
          });
        });

        vpnSubscriptionsList.querySelectorAll('.btn-del-sub').forEach(b => {
          b.addEventListener('click', async () => {
            const id = b.getAttribute('data-id');
            try {
              await window.Capacitor.Plugins.NativeVpn.deleteSubscription({ id });
              window.showToast('Subscription deleted', 'info');
            } catch (_) { }
            await renderSubscriptionsList();
          });
        });
      }
    } catch (_) { }
  }

  btnVpnOpenSubs?.addEventListener('click', openSubscriptionsModal);
  btnCloseSubsModal?.addEventListener('click', closeSubscriptionsModal);
  vpnSubscriptionsModal?.addEventListener('click', (e) => {
    if (e.target === vpnSubscriptionsModal) closeSubscriptionsModal();
  });

  btnAddSubscription?.addEventListener('click', async () => {
    const name = (inputSubName?.value || '').trim();
    const url = (inputSubUrl?.value || '').trim();
    if (!url) {
      window.showToast('Please enter an HTTPS subscription URL', 'warning');
      return;
    }

    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.saveSubscription) {
        const res = await window.Capacitor.Plugins.NativeVpn.saveSubscription({ name: name || 'Remote Pack', url });
        if (res && res.success && res.id) {
          window.showToast('Subscription added! Fetching nodes...', 'info');
          if (inputSubName) inputSubName.value = '';
          if (inputSubUrl) inputSubUrl.value = '';
          await window.Capacitor.Plugins.NativeVpn.updateSubscription({ id: res.id });
          await loadProfilesFromNative();
          await renderSubscriptionsList();
          window.showToast('Nodes synced successfully!', 'success');
        }
      }
    } catch (err) {
      window.showToast(`Failed to add subscription: ${err.message}`, 'error');
    }
  });

  // ============================================================================
  // 16. SAFE AUDIT LOGS MODAL & EXPORTER
  // ============================================================================

  async function openLogsModal() {
    if (!vpnLogsModal) return;
    vpnLogsModal.classList.remove('hidden');
    await refreshLogsDisplay();
  }

  function closeLogsModal() {
    if (vpnLogsModal) vpnLogsModal.classList.add('hidden');
  }

  async function refreshLogsDisplay() {
    if (!vpnLogLinesContainer) return;
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.getLogs) {
        const res = await window.Capacitor.Plugins.NativeVpn.getLogs({ limit: 150 });
        const logs = res?.logs || [];
        if (logs.length === 0) {
          vpnLogLinesContainer.innerHTML = '<div style="color: #64748b; padding: 10px;">No logs recorded yet.</div>';
          return;
        }
        vpnLogLinesContainer.innerHTML = logs.map(l => `
          <div class="vpn-log-line ${l.level || 'INFO'}">
            <span style="color: #64748b;">[${l.time || ''}]</span>
            <span style="font-weight: 700;">[${l.level || 'INFO'}]</span> ${l.message || ''}
          </div>
        `).join('');
        vpnLogLinesContainer.scrollTop = vpnLogLinesContainer.scrollHeight;
      }
    } catch (_) { }
  }

  btnVpnOpenLogs?.addEventListener('click', openLogsModal);
  btnCloseLogsModal?.addEventListener('click', closeLogsModal);
  vpnLogsModal?.addEventListener('click', (e) => {
    if (e.target === vpnLogsModal) closeLogsModal();
  });

  btnCopySafeLogs?.addEventListener('click', async () => {
    try {
      let text = '';
      if (window.Capacitor?.Plugins?.NativeVpn?.exportSafeLogs) {
        const res = await window.Capacitor.Plugins.NativeVpn.exportSafeLogs();
        text = res?.logs || '';
      }
      if (!text && vpnLogLinesContainer) {
        text = vpnLogLinesContainer.innerText;
      }
      await navigator.clipboard.writeText(text);
      window.showToast('📋 Safe audit log copied to clipboard!', 'success');
    } catch (_) {
      window.showToast('Could not copy log text', 'info');
    }
  });

  btnClearSafeLogs?.addEventListener('click', async () => {
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.clearLogs) {
        await window.Capacitor.Plugins.NativeVpn.clearLogs();
      }
      if (vpnLogLinesContainer) {
        vpnLogLinesContainer.innerHTML = '<div style="color: #64748b; padding: 10px;">Logs cleared.</div>';
      }
      window.showToast('Logs cleared', 'info');
    } catch (_) { }
  });

  // ============================================================================
  // 17. QR CODE SCANNER & DECODER ENGINE
  // ============================================================================

  function openQrScanModal() {
    if (!vpnQrScanModal) return;
    vpnQrScanModal.classList.remove('hidden');
    if (qrPreviewCard) qrPreviewCard.classList.add('hidden');
    if (inputQrImageFile) inputQrImageFile.value = '';
    pendingQrDecodedPayload = '';
    pendingQrDecodedNode = null;
  }

  function closeQrScanModal() {
    if (vpnQrScanModal) vpnQrScanModal.classList.add('hidden');
  }

  btnVpnOpenQrScan?.addEventListener('click', openQrScanModal);
  btnCloseQrModal?.addEventListener('click', closeQrScanModal);
  vpnQrScanModal?.addEventListener('click', (e) => {
    if (e.target === vpnQrScanModal) closeQrScanModal();
  });

  dropQrZone?.addEventListener('click', () => {
    inputQrImageFile?.click();
  });

  dropQrZone?.addEventListener('dragover', (e) => {
    e.preventDefault();
    dropQrZone.style.borderColor = '#00f2fe';
  });

  dropQrZone?.addEventListener('dragleave', () => {
    dropQrZone.style.borderColor = 'rgba(192, 132, 252, 0.35)';
  });

  dropQrZone?.addEventListener('drop', (e) => {
    e.preventDefault();
    dropQrZone.style.borderColor = 'rgba(192, 132, 252, 0.35)';
    if (e.dataTransfer?.files?.length) {
      handleQrImageFile(e.dataTransfer.files[0]);
    }
  });

  inputQrImageFile?.addEventListener('change', (e) => {
    if (e.target.files?.length) {
      handleQrImageFile(e.target.files[0]);
    }
  });

  function handleQrImageFile(file) {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async (ev) => {
      const dataUrl = ev.target?.result;
      if (!dataUrl) return;
      const base64 = dataUrl.split(',')[1] || dataUrl;

      window.showToast('🔍 Decoding QR code...', 'info');
      try {
        if (window.Capacitor?.Plugins?.NativeVpn?.decodeQrCode) {
          const res = await window.Capacitor.Plugins.NativeVpn.decodeQrCode({ image: base64 });
          if (res && res.success && res.text) {
            const rawPayload = res.text.trim();
            pendingQrDecodedPayload = rawPayload;

            let parsed = null;
            if (window.Capacitor?.Plugins?.NativeVpn?.parsePayload) {
              const p = await window.Capacitor.Plugins.NativeVpn.parsePayload({ payload: rawPayload });
              if (p && p.valid) parsed = p;
            }

            pendingQrDecodedNode = parsed;
            if (qrDecodedFlag) qrDecodedFlag.textContent = parsed?.countryFlag || '🌐';
            if (qrDecodedProto) qrDecodedProto.textContent = (parsed?.protocol || 'VPN').toUpperCase();
            if (qrDecodedName) qrDecodedName.textContent = parsed?.remark || `${parsed?.countryCode || 'VPN'} Node`;
            if (qrDecodedHost) qrDecodedHost.textContent = `${parsed?.host || 'remote'}:${parsed?.port || 443}`;
            if (qrPreviewCard) qrPreviewCard.classList.remove('hidden');
            window.showToast('✅ QR code successfully decoded!', 'success');
            return;
          } else {
            window.showToast(`Could not decode QR code: ${res?.errorMessage || 'No QR found'}`, 'error');
            return;
          }
        }
        window.showToast('QR decoder unavailable in browser preview', 'warning');
      } catch (err) {
        window.showToast(`QR Decode Error: ${err.message}`, 'error');
      }
    };
    reader.readAsDataURL(file);
  }

  btnImportQrOnly?.addEventListener('click', async () => {
    if (!pendingQrDecodedPayload) return;
    const p = pendingQrDecodedNode;
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.saveProfile) {
        await window.Capacitor.Plugins.NativeVpn.saveProfile({
          id: String(Date.now()),
          name: p?.remark || `${p?.countryFlag || '⚡'} ${p?.countryCode || 'VPN'} QR Node`,
          payload: pendingQrDecodedPayload,
          rawPayload: pendingQrDecodedPayload,
          protocol: p?.protocol || 'VLESS',
          host: p?.host || '',
          port: p?.port || 443,
          security: p?.security || 'none',
          transport: p?.transport || 'tcp',
          countryFlag: p?.countryFlag || '🌐',
          countryCode: p?.countryCode || 'VPN'
        });
      }
    } catch (_) { }
    await loadProfilesFromNative();
    closeQrScanModal();
    window.showToast('QR Node imported to server profiles!', 'success');
  });

  btnImportAndTestQr?.addEventListener('click', async () => {
    if (!pendingQrDecodedPayload) return;
    const raw = pendingQrDecodedPayload;
    const p = pendingQrDecodedNode;
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.saveProfile) {
        await window.Capacitor.Plugins.NativeVpn.saveProfile({
          id: String(Date.now()),
          name: p?.remark || `${p?.countryFlag || '⚡'} ${p?.countryCode || 'VPN'} QR Node`,
          payload: raw,
          rawPayload: raw,
          protocol: p?.protocol || 'VLESS',
          host: p?.host || '',
          port: p?.port || 443,
          security: p?.security || 'none',
          transport: p?.transport || 'tcp',
          countryFlag: p?.countryFlag || '🌐',
          countryCode: p?.countryCode || 'VPN'
        });
      }
    } catch (_) { }
    await loadProfilesFromNative();
    closeQrScanModal();
    if (inputV2RayPayload) inputV2RayPayload.value = raw;
    await handleVpnConnect(true, raw);
  });

  // ============================================================================
  // 18. INITIALIZATION & BOOT SYNCHRONIZATION
  // ============================================================================
  loadProfilesFromNative();

  async function checkInitialStatus() {
    try {
      if (window.Capacitor?.Plugins?.NativeVpn?.getStatus) {
        const s = await window.Capacitor.Plugins.NativeVpn.getStatus();
        if (s && s.isConnected) {
          activeHost = s.server;
          activePort = s.port;
          if (s.protocol) activeProto = s.protocol.toUpperCase();
          renderVpnState(true, s.countryFlag, s.countryCode, s.latencyMs, s.countryName || s.server, s.downloadFormatted, s.uploadFormatted, '', s.downloadMbps, s.uploadMbps);
          startPingTimer();
        } else {
          renderVpnState(false, '🌐', 'VPN', 0, '', '0.0 MB', '0.0 MB');
        }
      }
    } catch (_) { }
  }
  window.syncVpnStatusFromNative = checkInitialStatus;
  checkInitialStatus();
})();
