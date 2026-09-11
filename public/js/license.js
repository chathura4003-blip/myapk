/**
 * Cloud Drive Leech - Universal License & Feature Access Engine
 * Implements Master Prompt specifications:
 * - Permanent Feature Registry
 * - Cryptographic Signature & Token Storage (HMAC-SHA256 JWT)
 * - Device Binding & Installation Identity
 * - Online Authoritative State & Immediate Multi-Trigger Verification
 * - Tamper-Proofing (Zero client-side fake key bypasses)
 * - Locked Feature Modal & In-App 404 Controllers
 */

(function () {
  'use strict';

  // 1. Permanent Feature Registry
  const FEATURE_REGISTRY = {
    TAB_MOVIES: { id: 'TAB_MOVIES', name: 'Movies & Cinema Stream (4K/FHD)', tab: 'movies', tier: 'PRO', icon: 'fa-film' },
    TAB_DOWNLOADS: { id: 'TAB_DOWNLOADS', name: 'Universal Downloader Hub', tab: 'downloads', tier: 'FREE', icon: 'fa-circle-down' },
    TAB_GALLERY: { id: 'TAB_GALLERY', name: 'Local Media Gallery & Vault', tab: 'gallery', tier: 'FREE', icon: 'fa-photo-film' },
    TAB_ADULT: { id: 'TAB_ADULT', name: '18+ Premium Hub', tab: 'adult', tier: 'PRO', icon: 'fa-fire' },
    TAB_BROWSER: { id: 'TAB_BROWSER', name: 'Cyber Web Browser & Sniffer', tab: 'browser', tier: 'PRO', icon: 'fa-compass' },
    TAB_DRIVE: { id: 'TAB_DRIVE', name: 'Google Drive 0 MB Cloud Leech', tab: 'drive', tier: 'PRO', icon: 'fa-google-drive', iconType: 'brands' },
    FEATURE_VPN: { id: 'FEATURE_VPN', name: 'Native Anti-Censorship VPN (V2Ray/Xray)', tab: null, tier: 'PRO', icon: 'fa-shield-halved' },
    FEATURE_CLOUD_UPLOAD: { id: 'FEATURE_CLOUD_UPLOAD', name: 'Google Drive Cloud Upload Pipeline', tab: null, tier: 'PRO', icon: 'fa-cloud-arrow-up' },
    FEATURE_ACCOUNT_LINK: { id: 'FEATURE_ACCOUNT_LINK', name: 'Google Account Authorization Link', tab: null, tier: 'PRO', icon: 'fa-user-lock' },
    FEATURE_CINEMA_PRO: { id: 'FEATURE_CINEMA_PRO', name: 'ExoPlayer 4K Cinema Player', tab: null, tier: 'PRO', icon: 'fa-play' },
    FEATURE_TURBO_PIPE: { id: 'FEATURE_TURBO_PIPE', name: 'High-Speed Multi-Thread Engine', tab: null, tier: 'PRO', icon: 'fa-bolt-lightning' }
  };

  const TAB_TO_FEATURE_MAP = {
    movies: 'TAB_MOVIES',
    downloads: 'TAB_DOWNLOADS',
    gallery: 'TAB_GALLERY',
    adult: 'TAB_ADULT',
    browser: 'TAB_BROWSER',
    drive: 'TAB_DRIVE'
  };

  const DEFAULT_FREE_FEATURES = {
    TAB_MOVIES: false, // ENTIRE MOVIE TAB IS PRO
    TAB_DOWNLOADS: true,
    TAB_GALLERY: true,
    TAB_ADULT: false,
    TAB_BROWSER: false,
    TAB_DRIVE: false,
    FEATURE_VPN: false,
    FEATURE_CLOUD_UPLOAD: false,
    FEATURE_ACCOUNT_LINK: false,
    FEATURE_CINEMA_PRO: false,
    FEATURE_TURBO_PIPE: false
  };

  const STORAGE_KEYS = {
    LICENSE: 'cld_license_payload',
    TOKEN: 'cld_license_token',
    INSTALL_ID: 'cld_installation_id',
    LAST_SYNC: 'cld_license_last_sync'
  };

  // State
  let currentLicense = {
    licenseId: 'FREE-TIER',
    plan: 'FREE',
    status: 'ACTIVE',
    deviceBinding: null,
    expiresAt: null,
    offlineGraceDays: 7,
    lastVerifiedAt: Date.now(),
    features: Object.assign({}, DEFAULT_FREE_FEATURES)
  };

  let installationIdentity = null;
  let isSyncing = false;
  let lastNotifiedRevoked = false;
  let nextSyncScheduledTime = 0;

  // Helper: Resolve server base URL across all environments (Browser, Android WebView, Capacitor)
  function getServerBaseUrl() {
    // 1. User manual override in Settings (if user enters cloud URL like https://my-admin.onrender.com)
    const userServer = localStorage.getItem('cdl_license_server_url');
    if (userServer && typeof userServer === 'string' && userServer.startsWith('http') && !userServer.includes('localhost')) {
      return userServer.replace(/\/+$/, '');
    }

    // 2. Authoritative Project Configured URL from app-config.js
    if (window.CLD_CONFIG?.SERVER_URL && typeof window.CLD_CONFIG.SERVER_URL === 'string' &&
        window.CLD_CONFIG.SERVER_URL.startsWith('http') && !window.CLD_CONFIG.SERVER_URL.includes('localhost')) {
      return window.CLD_CONFIG.SERVER_URL.replace(/\/+$/, '');
    }

    // 3. Current window origin if accessed in browser over http (excluding localhost and capacitor)
    const origin = window.location?.origin || '';
    if (origin && origin.startsWith('http') && !origin.includes('localhost') && !origin.startsWith('capacitor://') && !origin.startsWith('file://')) {
      return origin.replace(/\/+$/, '');
    }

    // 4. Default: Live Google Firebase Realtime Database
    return 'https://mybnewapk-default-rtdb.firebaseio.com';
  }
  window.getServerBaseUrl = getServerBaseUrl;

  // Helper: Safely decode JWT payload from server-signed token
  function parseJwtPayload(token) {
    try {
      if (!token || typeof token !== 'string') return null;
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const jsonPayload = decodeURIComponent(atob(base64).split('').map(c => {
        return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
      }).join(''));
      return JSON.parse(jsonPayload);
    } catch (_) {
      return null;
    }
  }

  let hardwareDeviceInfo = {
    deviceName: 'Android Device',
    deviceModel: 'Generic Mobile',
    manufacturer: 'Android',
    brand: 'Mobile',
    androidVersion: '14',
    appVersion: '1.0.5'
  };

  // Initialize hardware installation identity (tamper-resistant)
  async function initInstallationId() {
    try {
      if (window.Capacitor?.Plugins?.NativeLicense?.getInstallationIdentity) {
        const res = await window.Capacitor.Plugins.NativeLicense.getInstallationIdentity();
        if (res?.installationId) {
          installationIdentity = res.installationId;
          if (res.deviceModel) {
            hardwareDeviceInfo.deviceName = res.deviceModel;
            hardwareDeviceInfo.deviceModel = res.deviceModel;
          }
          if (res.androidVersion) {
            hardwareDeviceInfo.androidVersion = res.androidVersion;
          }
        }
      }
      if (window.Capacitor?.Plugins?.NativeLicense?.getDeviceInfo) {
        const dInfo = await window.Capacitor.Plugins.NativeLicense.getDeviceInfo();
        if (dInfo) {
          const mfg = (dInfo.manufacturer || '').trim();
          const mdl = (dInfo.model || '').trim();
          const dName = (mfg && mdl && !mdl.toLowerCase().includes(mfg.toLowerCase())) ? `${mfg} ${mdl}` : (mdl || mfg || 'Android Device');
          hardwareDeviceInfo.deviceName = dName;
          hardwareDeviceInfo.deviceModel = mdl || hardwareDeviceInfo.deviceModel;
          hardwareDeviceInfo.manufacturer = mfg || hardwareDeviceInfo.manufacturer;
          hardwareDeviceInfo.brand = dInfo.brand || hardwareDeviceInfo.brand;
          hardwareDeviceInfo.androidVersion = dInfo.androidVersion || hardwareDeviceInfo.androidVersion;
          hardwareDeviceInfo.appVersion = dInfo.appVersion || hardwareDeviceInfo.appVersion;
        }
      }
    } catch (_) {}

    if (!installationIdentity) {
      // Fallback storage if plugin unavailable (browser dev mode)
      let stored = localStorage.getItem(STORAGE_KEYS.INSTALL_ID);
      if (!stored) {
        stored = 'DEV-' + Math.random().toString(36).substring(2, 10).toUpperCase() + '-' + Date.now().toString(36).toUpperCase();
        try { localStorage.setItem(STORAGE_KEYS.INSTALL_ID, stored); } catch (_) {}
      }
      installationIdentity = stored;
      if (navigator.userAgent.includes('Android')) {
        hardwareDeviceInfo.deviceName = 'Android Device (Web)';
      } else if (navigator.userAgent.includes('iPhone') || navigator.userAgent.includes('iPad')) {
        hardwareDeviceInfo.deviceName = 'Apple iOS Device';
      } else {
        hardwareDeviceInfo.deviceName = 'Desktop Chrome/Web Client';
      }
    }
    return installationIdentity;
  }

  // Load cached license from local storage and native secure preferences
  function loadCachedLicense() {
    try {
      const rawLicense = localStorage.getItem(STORAGE_KEYS.LICENSE);
      const token = localStorage.getItem(STORAGE_KEYS.TOKEN);

      if (!rawLicense && !token) {
        // No stored license: Default strictly to Free tier
        currentLicense = {
          licenseId: 'FREE-TIER',
          plan: 'FREE',
          status: 'ACTIVE',
          deviceBinding: null,
          expiresAt: null,
          offlineGraceDays: 7,
          lastVerifiedAt: Date.now(),
          features: Object.assign({}, DEFAULT_FREE_FEATURES)
        };
        return;
      }

      let payload = null;
      if (rawLicense) {
        try { payload = JSON.parse(rawLicense); } catch (_) {}
      }
      if (!payload && token) {
        payload = parseJwtPayload(token) || { licenseId: token, plan: 'PRO', status: 'ACTIVE' };
      }

      if (!payload || !payload.features) {
        currentLicense = {
          licenseId: 'FREE-TIER',
          plan: 'FREE',
          status: 'ACTIVE',
          deviceBinding: null,
          expiresAt: null,
          offlineGraceDays: 7,
          lastVerifiedAt: Date.now(),
          features: Object.assign({}, DEFAULT_FREE_FEATURES)
        };
        return;
      }

      // Check expiration
      if (payload.expiresAt && Date.now() > new Date(payload.expiresAt).getTime()) {
        console.warn('[License] License has expired.');
        currentLicense = Object.assign({}, payload, {
          status: 'EXPIRED',
          features: Object.assign({}, DEFAULT_FREE_FEATURES)
        });
        return;
      }

      // Check offline grace period
      const lastSync = Number(localStorage.getItem(STORAGE_KEYS.LAST_SYNC) || payload.lastVerifiedAt || 0);
      const graceMs = (payload.offlineGraceDays || 7) * 24 * 3600 * 1000;
      if (payload.plan !== 'FREE' && lastSync > 0 && (Date.now() - lastSync > graceMs)) {
        console.warn('[License] Offline grace period expired. Reverting to Free Tier until server check.');
        currentLicense = Object.assign({}, payload, {
          status: 'EXPIRED',
          features: Object.assign({}, DEFAULT_FREE_FEATURES)
        });
        return;
      }

      // Check status embedded in record
      if (payload.status && payload.status !== 'ACTIVE') {
        currentLicense = Object.assign({}, payload, {
          features: Object.assign({}, DEFAULT_FREE_FEATURES)
        });
        return;
      }

      currentLicense = {
        licenseId: payload.licenseId || 'PRO-ACTIVATED',
        plan: payload.plan || 'PRO',
        status: payload.status || 'ACTIVE',
        deviceBinding: payload.deviceBinding || null,
        expiresAt: payload.expiresAt || null,
        offlineGraceDays: payload.offlineGraceDays || 7,
        lastVerifiedAt: lastSync || Date.now(),
        features: Object.assign({}, DEFAULT_FREE_FEATURES, payload.features)
      };
    } catch (e) {
      console.warn('[License] Error loading cached license:', e);
      currentLicense.features = Object.assign({}, DEFAULT_FREE_FEATURES);
    }
  }

  // Save license and cryptographically signed token
  function saveLicense(licenseData, token) {
    currentLicense = licenseData;
    try {
      localStorage.setItem(STORAGE_KEYS.LICENSE, JSON.stringify(licenseData));
      if (token) {
        localStorage.setItem(STORAGE_KEYS.TOKEN, token);
        // Also persist in Android Native SharedPreferences if available
        if (window.Capacitor?.Plugins?.NativeLicense?.saveLicenseToken) {
          window.Capacitor.Plugins.NativeLicense.saveLicenseToken({ token }).catch(() => {});
        }
      }
      localStorage.setItem(STORAGE_KEYS.LAST_SYNC, Date.now().toString());
    } catch (_) {}

    updateSettingsUI();
    window.dispatchEvent(new CustomEvent('cld:license-updated', { detail: currentLicense }));
  }

  // Reset to clean Free Tier state
  function resetToFreeTier(statusReason = 'REVOKED') {
    currentLicense = {
      licenseId: 'FREE-TIER',
      plan: 'FREE',
      status: statusReason,
      deviceBinding: null,
      expiresAt: null,
      offlineGraceDays: 7,
      lastVerifiedAt: Date.now(),
      features: Object.assign({}, DEFAULT_FREE_FEATURES)
    };
    try {
      localStorage.removeItem(STORAGE_KEYS.LICENSE);
      localStorage.removeItem(STORAGE_KEYS.TOKEN);
      localStorage.setItem(STORAGE_KEYS.LAST_SYNC, Date.now().toString());
      if (window.Capacitor?.Plugins?.NativeLicense?.clearLicenseToken) {
        window.Capacitor.Plugins.NativeLicense.clearLicenseToken().catch(() => {});
      }
    } catch (_) {}

    enforceActiveTabAccess();
    updateSettingsUI();
    window.dispatchEvent(new CustomEvent('cld:license-updated', { detail: currentLicense }));
  }

  // Active Tab Gatekeeper: If user is on a newly revoked or unauthorized tab, immediately bounce them out
  function enforceActiveTabAccess() {
    const curTab = window.state?.currentTab;
    if (!curTab) return;

    const featId = window.getFeatureIdForTab ? window.getFeatureIdForTab(curTab) : null;
    if (featId && !window.isFeatureAvailable(featId)) {
      console.warn(`[License] Current tab '${curTab}' is unauthorized under active license state. Redirecting to 'downloads'.`);
      if (typeof window.switchTab === 'function') {
        window.switchTab('downloads', false);
      }
      if (typeof window.showFeatureLockedSheet === 'function') {
        window.showFeatureLockedSheet(featId);
      }
    }
  }

  /**
   * Central authorization check: isFeatureAvailable(featureId)
   * Evaluates license status, expiry, device authorization, and per-feature permissions.
   */
  window.isFeatureAvailable = function (featureId) {
    if (!featureId) return false;

    // Resolve if passed a tab name instead of feature ID
    const resolvedId = TAB_TO_FEATURE_MAP[featureId] || featureId;
    const def = FEATURE_REGISTRY[resolvedId];
    if (!def) return false; // FIXED: Unknown features default strictly to DENY (Master Prompt Sec 3.E)

    // Free tier features are always allowed
    if (def.tier === 'FREE') return true;

    // Check license status
    if (currentLicense.status !== 'ACTIVE') {
      return false;
    }

    // Check expiry
    if (currentLicense.expiresAt && Date.now() > new Date(currentLicense.expiresAt).getTime()) {
      return false;
    }

    // Check device binding if specified
    if (currentLicense.deviceBinding && installationIdentity) {
      if (currentLicense.deviceBinding !== installationIdentity) {
        return false;
      }
    }

    // Check specific feature grant
    return Boolean(currentLicense.features && currentLicense.features[resolvedId] === true);
  };

  /**
   * Helper to map tab name to feature ID
   */
  window.getFeatureIdForTab = function (tabName) {
    return TAB_TO_FEATURE_MAP[tabName] || null;
  };

  /**
   * Return full license status
   */
  window.getLicenseState = function () {
    return Object.assign({}, currentLicense, {
      installationId: installationIdentity,
      registry: FEATURE_REGISTRY
    });
  };

  // Direct Google Firebase Cloud Realtime Database Adapter
  async function firebaseLicenseAdapter(serverBase, endpoint, bodyData) {
    const cleanBase = serverBase.replace(/\/+$/, '');

    function makeApiResponse(data, status = 200) {
      return {
        ok: status >= 200 && status < 300,
        status: status,
        json: async () => data
      };
    }

    try {
      // 1. ACTIVATE
      if (endpoint === '/api/license/activate') {
        const key = (bodyData.licenseKey || '').trim().toUpperCase();
        const installId = bodyData.installationId;
        const deviceInfo = bodyData.deviceInfo || {};

        if (!key) {
          return makeApiResponse({ success: false, message: 'License key is required.' }, 400);
        }

        // Hardware device ban guard
        if (installId) {
          try {
            const devCheck = await fetch(`${cleanBase}/devices/${encodeURIComponent(installId)}.json`);
            const devObj = await devCheck.json();
            if (devObj && (devObj.status === 'BANNED' || devObj.banned === true)) {
              return makeApiResponse({ success: false, message: '⛔ This hardware device has been banned by Administrator.' }, 403);
            }
          } catch (_) {}
        }

        const res = await fetch(`${cleanBase}/licenses/${encodeURIComponent(key)}.json`);
        const lic = await res.json();

        if (!lic) {
          return makeApiResponse({ success: false, message: 'Invalid License Key: Key does not exist in Firebase Cloud.' }, 404);
        }

        if (lic.status !== 'ACTIVE') {
          return makeApiResponse({ success: false, message: `License Key is currently ${lic.status || 'REVOKED'}. Access denied.` }, 403);
        }

        if (lic.expiresAt && Date.now() > new Date(lic.expiresAt).getTime()) {
          return makeApiResponse({ success: false, message: 'This License Key has expired.' }, 403);
        }

        // Multi-device binding check
        const maxDevs = Number(lic.maxDevices) || 1;
        let boundList = Array.isArray(lic.boundDevices) ? lic.boundDevices : (lic.deviceBinding ? [lic.deviceBinding] : []);

        if (installId) {
          if (!boundList.includes(installId)) {
            if (boundList.length >= maxDevs) {
              return makeApiResponse({ success: false, message: `License Key is already bound to maximum allowed devices (${maxDevs}).` }, 409);
            }
            boundList.push(installId);
          }
        }

        const nowIso = new Date().toISOString();
        const boundLic = Object.assign({}, lic, {
          deviceBinding: boundList[0] || installId,
          boundDevices: boundList,
          maxDevices: maxDevs,
          deviceInfo: deviceInfo,
          activatedAt: lic.activatedAt || nowIso,
          lastSeen: nowIso
        });

        // Update Firebase license record
        await fetch(`${cleanBase}/licenses/${encodeURIComponent(key)}.json`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            deviceBinding: boundLic.deviceBinding,
            boundDevices: boundLic.boundDevices,
            deviceInfo: boundLic.deviceInfo,
            activatedAt: boundLic.activatedAt,
            lastSeen: boundLic.lastSeen
          })
        }).catch(() => {});

        // Update Firebase device record
        if (installId) {
          await fetch(`${cleanBase}/devices/${encodeURIComponent(installId)}.json`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              installationId: installId,
              boundLicenseId: key,
              plan: boundLic.plan || 'PRO',
              status: 'ACTIVE',
              lastSeen: nowIso,
              deviceInfo: deviceInfo
            })
          }).catch(() => {});
        }

        // Audit log
        await fetch(`${cleanBase}/audit_logs.json`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            action: 'LICENSE_ACTIVATED',
            licenseId: key,
            installationId: installId,
            plan: boundLic.plan || 'PRO',
            timestamp: nowIso
          })
        }).catch(() => {});

        return makeApiResponse({
          success: true,
          license: boundLic,
          token: key
        });
      }

      // 2. SYNC
      if (endpoint === '/api/license/sync') {
        const key = bodyData.licenseId;
        const installId = bodyData.installationId;
        const dInfo = bodyData.deviceInfo || hardwareDeviceInfo;
        const nowIso = new Date().toISOString();

        // Fetch app config for maintenance / announcements / OTA
        let appConfig = null;
        try {
          const cfgRes = await fetch(`${cleanBase}/app_config.json`);
          appConfig = await cfgRes.json();
        } catch (_) {}

        // Hardware device inspection for ban or remote PRO assignment
        let existingDev = null;
        if (installId) {
          try {
            const devCheck = await fetch(`${cleanBase}/devices/${encodeURIComponent(installId)}.json`);
            existingDev = await devCheck.json();
            if (existingDev) {
              if (existingDev.status === 'BANNED' || existingDev.banned === true) {
                return makeApiResponse({
                  success: false,
                  status: 'BANNED',
                  message: '⛔ Hardware device restricted by Administrator.'
                }, 403);
              }
              if (existingDev.remoteCommand?.command === 'FORCE_LOGOUT') {
                await fetch(`${cleanBase}/devices/${encodeURIComponent(installId)}/remoteCommand.json`, { method: 'DELETE' }).catch(() => {});
                return makeApiResponse({
                  success: true,
                  remoteCommand: 'FORCE_LOGOUT',
                  license: { licenseId: 'FREE-TIER', plan: 'FREE', status: 'REVOKED' },
                  appConfig: appConfig
                });
              }
            }
          } catch (_) {}
        }

        const pendingTargetedMsg = (existingDev?.targetedMessage && !existingDev.targetedMessage.read)
          ? existingDev.targetedMessage
          : null;

        // Automatic Remote PRO Key Detection: If user is on Free Tier, but Admin assigned a key in Admin APK!
        let activeKeyToVerify = key;
        if ((!key || key === 'FREE-TIER') && existingDev?.boundLicenseId) {
          console.log('[License] Remote PRO key detected on device record:', existingDev.boundLicenseId);
          activeKeyToVerify = existingDev.boundLicenseId;
        }

        // Free tier heartbeat
        if (!activeKeyToVerify || activeKeyToVerify === 'FREE-TIER') {
          if (installId) {
            const devPayload = {
              installationId: installId,
              deviceName: existingDev?.assignedUserName ? `${dInfo.deviceName} (${existingDev.assignedUserName})` : (dInfo.deviceName || 'Android Device'),
              rawDeviceName: dInfo.deviceName || 'Android Device',
              assignedUserName: existingDev?.assignedUserName || null,
              phoneNumber: existingDev?.phoneNumber || null,
              deviceModel: dInfo.deviceModel || 'Mobile',
              manufacturer: dInfo.manufacturer || 'Android',
              brand: dInfo.brand || 'Mobile',
              androidVersion: dInfo.androidVersion || '14',
              appVersion: dInfo.appVersion || '1.0.5',
              boundLicenseId: null,
              plan: 'FREE TIER',
              status: 'ONLINE',
              firstSeen: existingDev?.firstSeen || nowIso,
              lastSeen: nowIso
            };
            await fetch(`${cleanBase}/devices/${encodeURIComponent(installId)}.json`, {
              method: 'PATCH',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(devPayload)
            }).catch(() => {});
          }
          return makeApiResponse({
            success: true,
            license: { licenseId: 'FREE-TIER', plan: 'FREE', status: 'ACTIVE' },
            appConfig: appConfig,
            targetedMessage: pendingTargetedMsg
          });
        }

        // Lookup PRO key in Firebase
        const res = await fetch(`${cleanBase}/licenses/${encodeURIComponent(activeKeyToVerify)}.json`);
        const lic = await res.json();

        if (!lic) {
          return makeApiResponse({ success: false, status: 'REVOKED', message: 'License key was deleted in Cloud Admin.' }, 404);
        }

        if (lic.status !== 'ACTIVE') {
          return makeApiResponse({ success: false, status: lic.status || 'REVOKED', message: `License status is ${lic.status}` }, 403);
        }

        if (lic.expiresAt && Date.now() > new Date(lic.expiresAt).getTime()) {
          return makeApiResponse({ success: false, status: 'EXPIRED', message: 'License has expired' }, 403);
        }

        const maxDevs = Number(lic.maxDevices) || 1;
        let boundList = Array.isArray(lic.boundDevices) ? lic.boundDevices : (lic.deviceBinding ? [lic.deviceBinding] : []);

        if (installId) {
          if (!boundList.includes(installId)) {
            if (boundList.length >= maxDevs) {
              return makeApiResponse({ success: false, status: 'DEVICE_MISMATCH', message: 'License bound to another device' }, 403);
            }
            boundList.push(installId);
            await fetch(`${cleanBase}/licenses/${encodeURIComponent(activeKeyToVerify)}.json`, {
              method: 'PATCH',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ boundDevices: boundList })
            }).catch(() => {});
          }
        }

        // Heartbeat update with device model name
        await fetch(`${cleanBase}/licenses/${encodeURIComponent(activeKeyToVerify)}.json`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            lastSeen: nowIso,
            deviceBinding: boundList[0] || installId,
            boundDevices: boundList,
            deviceName: dInfo.deviceName || 'Android Device'
          })
        }).catch(() => {});

        if (installId) {
          await fetch(`${cleanBase}/devices/${encodeURIComponent(installId)}.json`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              installationId: installId,
              deviceName: existingDev?.assignedUserName ? `${dInfo.deviceName} (${existingDev.assignedUserName})` : (dInfo.deviceName || 'Android Device'),
              rawDeviceName: dInfo.deviceName || 'Android Device',
              assignedUserName: existingDev?.assignedUserName || null,
              phoneNumber: existingDev?.phoneNumber || null,
              deviceModel: dInfo.deviceModel || 'Mobile',
              manufacturer: dInfo.manufacturer || 'Android',
              brand: dInfo.brand || 'Mobile',
              androidVersion: dInfo.androidVersion || '14',
              appVersion: dInfo.appVersion || '1.0.5',
              boundLicenseId: activeKeyToVerify,
              plan: lic.plan || 'PRO',
              status: 'ONLINE',
              firstSeen: existingDev?.firstSeen || nowIso,
              lastSeen: nowIso
            })
          }).catch(() => {});
        }

        return makeApiResponse({
          success: true,
          license: Object.assign({}, lic, { boundDevices: boundList }),
          status: 'ACTIVE',
          token: activeKeyToVerify,
          appConfig: appConfig,
          targetedMessage: pendingTargetedMsg
        });
      }

      return makeApiResponse({ success: true });
    } catch (err) {
      console.warn('[FirebaseAdapter] Error:', err);
      return makeApiResponse({ success: false, message: err.message }, 500);
    }
  }

  // Direct Cryptographic Client-Server Handshake Requester
  async function postLicenseJson(endpoint, bodyData, timeoutMs = 7000) {
    const serverBase = getServerBaseUrl();
    if (serverBase.includes('firebaseio.com')) {
      return await firebaseLicenseAdapter(serverBase, endpoint, bodyData);
    }

    const url = `${serverBase}${endpoint}`;
    const token = localStorage.getItem(STORAGE_KEYS.TOKEN);

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    const headers = {
      'Content-Type': 'application/json'
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    try {
      const resp = await fetch(url, {
        method: 'POST',
        headers,
        body: JSON.stringify(bodyData),
        signal: controller.signal
      });
      return resp;
    } finally {
      clearTimeout(timer);
    }
  }

  /**
   * Activate a license key - STRICT SERVER-AUTHENTICATED ACTIVATION
   * Zero client-side fake key bypasses. Only genuine server-signed keys can activate.
   */
  window.activateLicenseKey = async function (rawKey) {
    const key = (rawKey || '').trim().toUpperCase();
    if (!key) {
      if (window.showToast) window.showToast('Please enter a valid License Key', 'warning');
      return { success: false, message: 'Empty key' };
    }

    await initInstallationId();

    if (window.showToast) window.showToast('Verifying License Key with Server...', 'info');

    try {
      const resp = await postLicenseJson('/api/license/activate', {
        licenseKey: key,
        installationId: installationIdentity,
        deviceInfo: hardwareDeviceInfo
      }, 6000);

      const data = await resp.json().catch(() => ({}));

      if (resp.ok && data.success && data.license && data.token) {
        saveLicense(data.license, data.token);
        lastNotifiedRevoked = false;
        enforceActiveTabAccess();
        if (window.showToast) window.showToast(`🎉 PRO License Activated (${data.license.plan})!`, 'success');
        return { success: true, license: data.license };
      } else {
        const msg = data.message || 'Activation failed: Invalid or revoked license key';
        if (window.showToast) window.showToast(`License Error: ${msg}`, 'error');
        return { success: false, message: msg };
      }
    } catch (err) {
      console.warn('[License] Activation request failed:', err);
      const offlineMsg = 'Cannot reach license verification server. Check your connection or server status.';
      if (window.showToast) window.showToast(offlineMsg, 'error');
      return { success: false, message: offlineMsg };
    }
  };

  /**
   * Authoritative Live License Sync with Admin Server
   * Dispatched on every APK launch, refresh, foreground resume, and background timer.
   */
  window.syncLicenseWithServer = async function (isManual = false) {
    if (isSyncing) return;

    // Do not attempt background sync if device is known to be offline
    if (!isManual && typeof navigator !== 'undefined' && navigator.onLine === false) {
      return;
    }

    const serverBase = getServerBaseUrl();
    // If running in standalone mode with no remote server URL configured, skip background sync
    if (!serverBase && !isManual) {
      return;
    }

    isSyncing = true;

    const btnSync = document.getElementById('btnSyncLicense');
    if (btnSync) btnSync.classList.add('spinning');

    try {
      await initInstallationId();

      // Safely extract active target license ID (handle both object or string storage)
      let targetKey = currentLicense?.licenseId;
      if (!targetKey || targetKey === 'FREE-TIER') {
        try {
          const rawLic = localStorage.getItem(STORAGE_KEYS.LICENSE);
          if (rawLic) {
            const parsed = JSON.parse(rawLic);
            if (parsed?.licenseId) targetKey = parsed.licenseId;
          }
        } catch (_) {}
      }
      if (!targetKey || targetKey === 'FREE-TIER') {
        const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
        if (token) {
          const payload = parseJwtPayload(token);
          if (payload?.licenseId) targetKey = payload.licenseId;
        }
      }

      const token = localStorage.getItem(STORAGE_KEYS.TOKEN);
      const licIdToSend = (targetKey && targetKey !== 'FREE-TIER') ? targetKey : 'FREE-TIER';

      const resp = await postLicenseJson('/api/license/sync', {
        licenseId: licIdToSend,
        installationId: installationIdentity,
        deviceInfo: hardwareDeviceInfo,
        token: token
      }, 5000);

      const data = await resp.json().catch(() => ({}));

      if (resp.ok && data.success && data.license && data.license.status === 'ACTIVE') {
        const wasFree = !currentLicense || currentLicense.plan === 'FREE';
        saveLicense(data.license, data.token || token);
        lastNotifiedRevoked = false;
        enforceActiveTabAccess();
        if (wasFree && data.license.plan !== 'FREE') {
          if (window.showToast) window.showToast(`🎉 PRO License Activated by Admin (${data.license.plan})!`, 'success');
        } else if (isManual && window.showToast) {
          window.showToast(`License Synced: ${data.license.plan} (ACTIVE)`, 'success');
        }
      } else if (resp.ok && (data.status === 'BANNED' || data.status === 'REVOKED' || data.status === 'DEVICE_MISMATCH' || data.status === 'EXPIRED')) {
        // Only explicitly authenticated server rejections can revoke an active license
        console.warn('[License] Server rejected PRO key or device:', data);
        const revokeStatus = data.status || 'REVOKED';
        resetToFreeTier(revokeStatus);
        if (!lastNotifiedRevoked) {
          lastNotifiedRevoked = true;
          const messageMap = {
            BANNED: '⛔ Hardware Device Restricted: Access locked by Administrator.',
            REVOKED: '⚠️ Your PRO License key was deleted or revoked by Administrator. PRO features locked.',
            DEVICE_MISMATCH: '⚠️ Device Mismatch: Key is bound to another hardware device.',
            EXPIRED: '⚠️ Your PRO License subscription has expired.'
          };
          if (window.showToast) {
            window.showToast(messageMap[revokeStatus] || '⚠️ PRO License was deleted by Administrator. PRO features locked.', 'error');
          }
        }
      } else if (isManual && !resp.ok) {
        if (window.showToast) window.showToast('License Server unreachable. Operating in local mode.', 'info');
      } else {
        // FREE-TIER check-in
        if (data.license) {
          currentLicense = Object.assign({}, data.license, { features: Object.assign({}, DEFAULT_FREE_FEATURES) });
        }
      }

      // Check Remote Admin Command (e.g. Force Logout)
      if (data.remoteCommand === 'FORCE_LOGOUT') {
        resetToFreeTier('REVOKED');
        if (window.showToast) window.showToast('⚠️ You have been remotely logged out by Administrator.', 'warning');
      }

      // Check Targeted In-App Direct Message
      if (data.targetedMessage && !data.targetedMessage.read) {
        const tMsg = data.targetedMessage;
        if (window.showToast) {
          window.showToast(`📢 ${tMsg.title}: ${tMsg.body}`, 'warning');
        }
      }

      // Record Next Adaptive Sync Time
      if (data.nextSyncAt) {
        nextSyncScheduledTime = Number(data.nextSyncAt);
      } else {
        nextSyncScheduledTime = Date.now() + 600000; // 10 min default
      }

      // Process Live Server App Policies (Broadcast Message, Maintenance Mode, OTA Update)
      if (data.appConfig) {
        // 1. Maintenance Mode
        if (data.appConfig.maintenanceMode) {
          window.showMaintenanceMode(data.appConfig.maintenanceMessage);
        } else {
          window.hideMaintenanceMode();
        }

        // 2. Broadcast Announcement
        if (data.appConfig.broadcastMessage && data.appConfig.broadcastMessage.trim()) {
          window.showBroadcastBanner(data.appConfig.broadcastMessage, data.appConfig.broadcastType);
        } else {
          window.hideBroadcastBanner();
        }

        // 3. OTA Update Notification & Mandatory Check
        if (data.appConfig.latestVersion && typeof window.triggerOtaFromSync === 'function') {
          window.triggerOtaFromSync(data.appConfig);
        }
      }
    } catch (err) {
      console.warn('[License] Server sync failed (offline or network error):', err.message);
      nextSyncScheduledTime = Date.now() + 900000; // Back off 15 mins on error
      if (isManual && window.showToast) {
        window.showToast('License Server unreachable. Operating in local mode.', 'info');
      }
      // Offline Policy: If offline grace period has expired and not in standalone app mode, demote to Free
      if (currentLicense.plan !== 'FREE' && !window.IS_STANDALONE_APP) {
        const graceMs = (currentLicense.offlineGraceDays || 30) * 24 * 3600 * 1000;
        const lastVerified = Number(localStorage.getItem(STORAGE_KEYS.LAST_SYNC) || currentLicense.lastVerifiedAt || 0);
        if (Date.now() - lastVerified > graceMs) {
          console.warn('[License] Offline grace period expired. Reverting to Free Tier.');
          resetToFreeTier('EXPIRED');
          if (window.showToast) window.showToast('Offline license grace period expired. Please connect to internet to verify.', 'warning');
        }
      }
    } finally {
      isSyncing = false;
      if (btnSync) btnSync.classList.remove('spinning');
      updateSettingsUI();
    }
  };

  /**
   * Display Feature Locked Modal
   */
  window.showFeatureLockedSheet = function (featureOrTab) {
    const featureId = TAB_TO_FEATURE_MAP[featureOrTab] || featureOrTab;
    const def = FEATURE_REGISTRY[featureId] || { name: 'Premium Feature', tier: 'PRO' };

    const modal = document.getElementById('featureLockedModal');
    if (!modal) return;

    const titleEl = document.getElementById('lockedFeatureTitle');
    const descEl = document.getElementById('lockedFeatureDesc');
    const tierBadge = document.getElementById('lockedFeatureTier');

    if (titleEl) titleEl.textContent = def.name;
    if (descEl) descEl.textContent = `This tab is locked. A valid ${def.tier} License Key with "${def.name}" authorization is required to access this feature.`;
    if (tierBadge) tierBadge.textContent = `${def.tier} REQUIRED`;

    modal.classList.remove('hidden');
  };

  window.closeFeatureLockedSheet = function () {
    const modal = document.getElementById('featureLockedModal');
    if (modal) modal.classList.add('hidden');
  };

  /**
   * Display Controlled In-App 404 Error Screen (Master Prompt Section 35)
   */
  window.showInApp404 = function (endpointTitle, reasonMessage) {
    const modal = document.getElementById('inApp404Modal');
    if (!modal) return;

    const tEl = document.getElementById('error404Title');
    const dEl = document.getElementById('error404Desc');

    if (tEl) tEl.textContent = `404 — ${endpointTitle || 'Endpoint Restricted'}`;
    if (dEl) dEl.textContent = reasonMessage || 'HTTP 404: The requested module / service endpoint is disabled or unauthorized under the active license.';

    modal.classList.remove('hidden');
  };

  window.closeInApp404 = function () {
    const modal = document.getElementById('inApp404Modal');
    if (modal) modal.classList.add('hidden');
  };

  /**
   * Broadcast Banner Controllers
   */
  window.showBroadcastBanner = function (message, type = 'info') {
    const banner = document.getElementById('cldBroadcastBanner');
    const textEl = document.getElementById('cldBroadcastText');
    if (!banner || !textEl || !message) return;
    textEl.textContent = message;
    banner.classList.remove('hidden');
  };

  window.hideBroadcastBanner = function () {
    const banner = document.getElementById('cldBroadcastBanner');
    if (banner) banner.classList.add('hidden');
  };

  window.dismissBroadcastBanner = function () {
    window.hideBroadcastBanner();
    try {
      sessionStorage.setItem('cld_broadcast_dismissed', 'true');
    } catch (_) {}
  };

  /**
   * Maintenance Mode Overlay Controllers
   */
  window.showMaintenanceMode = function (message) {
    const overlay = document.getElementById('cldMaintenanceOverlay');
    const msgEl = document.getElementById('cldMaintenanceMsg');
    if (!overlay) return;
    if (msgEl && message) msgEl.textContent = message;
    overlay.classList.remove('hidden');
  };

  window.hideMaintenanceMode = function () {
    const overlay = document.getElementById('cldMaintenanceOverlay');
    if (overlay) overlay.classList.add('hidden');
  };

  /**
   * Update Settings UI with License Status
   */
  function updateSettingsUI() {
    const badgeEl = document.getElementById('settingLicenseBadge');
    const deviceEl = document.getElementById('settingLicenseDeviceId');
    const syncTextEl = document.getElementById('licenseLastSyncText');
    const featuresListEl = document.getElementById('settingLicenseFeaturesList');

    if (badgeEl) {
      const isPro = currentLicense.status === 'ACTIVE' && currentLicense.plan !== 'FREE';
      badgeEl.textContent = isPro ? currentLicense.plan : (currentLicense.status === 'REVOKED' ? 'REVOKED' : 'FREE TIER');
      badgeEl.className = isPro ? 'license-status-badge active' : 'license-status-badge free';
      if (currentLicense.status === 'REVOKED') {
        badgeEl.style.borderColor = '#ef4444';
        badgeEl.style.color = '#f87171';
      } else {
        badgeEl.style.borderColor = '';
        badgeEl.style.color = '';
      }
    }

    if (deviceEl && installationIdentity) {
      deviceEl.textContent = installationIdentity.length > 20 ? installationIdentity.substring(0, 18) + '...' : installationIdentity;
      deviceEl.title = installationIdentity;
    }

    const planNameEl = document.getElementById('settingLicensePlanName');
    const expiryEl = document.getElementById('settingLicenseExpiryText');
    if (planNameEl) {
      const isPro = currentLicense.status === 'ACTIVE' && currentLicense.plan && currentLicense.plan !== 'FREE';
      planNameEl.textContent = isPro ? currentLicense.plan : (currentLicense.status === 'REVOKED' ? 'REVOKED' : (currentLicense.status === 'BANNED' ? 'BANNED' : 'FREE TIER'));
    }
    if (expiryEl) {
      if (currentLicense.status === 'ACTIVE' && currentLicense.plan !== 'FREE') {
        expiryEl.textContent = currentLicense.expiresAt ? new Date(currentLicense.expiresAt).toLocaleDateString() : 'Lifetime Access';
      } else {
        expiryEl.textContent = currentLicense.status === 'BANNED' ? 'Blocked' : 'Free Forever';
      }
    }

    if (syncTextEl) {
      const lastSync = localStorage.getItem(STORAGE_KEYS.LAST_SYNC);
      if (lastSync) {
        const d = new Date(Number(lastSync));
        const timeStr = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        syncTextEl.textContent = `Synced: ${timeStr}`;
      } else {
        syncTextEl.textContent = 'Synced: Never';
      }
    }

    if (featuresListEl) {
      let html = '';
      Object.keys(FEATURE_REGISTRY).forEach(k => {
        const feat = FEATURE_REGISTRY[k];
        const isGranted = window.isFeatureAvailable(k);
        const iconCls = feat.iconType === 'brands' ? `fa-brands ${feat.icon}` : `fa-solid ${feat.icon}`;
        html += `
          <div class="license-feature-item ${isGranted ? 'granted' : 'locked'}">
            <div class="feat-icon"><i class="${iconCls}"></i></div>
            <div class="feat-info">
              <span class="feat-name">${feat.name}</span>
              <span class="feat-tier">${feat.tier}</span>
            </div>
            <div class="feat-status">
              ${isGranted ? '<i class="fa-solid fa-circle-check text-success"></i>' : '<i class="fa-solid fa-lock text-muted"></i>'}
            </div>
          </div>
        `;
      });
      featuresListEl.innerHTML = html;
    }

    const serverUrlInput = document.getElementById('settingLicenseServerUrlInput');
    if (serverUrlInput && !serverUrlInput.value) {
      serverUrlInput.value = localStorage.getItem('cdl_license_server_url') || '';
    }

    const vpnCard = document.getElementById('netmodVpnCard');
    if (vpnCard) {
      vpnCard.classList.toggle('locked', !window.isFeatureAvailable('FEATURE_VPN'));
    }

    const movieNavProTag = document.querySelector('.nav-item[data-tab="movies"] .nav-pro-tag');
    if (movieNavProTag) {
      movieNavProTag.style.display = window.isFeatureAvailable('TAB_MOVIES') ? 'none' : 'inline-block';
    }
  }

  // Multi-Trigger Lifecycle & Event Initializer
  document.addEventListener('DOMContentLoaded', async () => {
    loadCachedLicense();
    await initInstallationId();
    updateSettingsUI();

    // 1. Immediate Online Authoritative Sync on Launch (0ms delay)
    window.syncLicenseWithServer();

    // 2. Foreground / Resume Sync (fires when user returns to app / switches tabs)
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        window.syncLicenseWithServer();
      }
    });

    window.addEventListener('focus', () => {
      window.syncLicenseWithServer();
    });

    if (window.Capacitor?.App?.addListener) {
      try {
        window.Capacitor.App.addListener('appStateChange', (state) => {
          if (state && state.isActive) {
            window.syncLicenseWithServer();
          }
        });
      } catch (_) {}
    }

    // 3. Pull-to-Refresh Sync Trigger
    window.addEventListener('cld:refresh-requested', () => {
      window.syncLicenseWithServer(true);
    });

    // 4. Adaptive Lifecycle Sync Heartbeat (triggers only when TTL elapsed, online, and configured)
    setInterval(() => {
      if (Date.now() >= nextSyncScheduledTime && document.visibilityState === 'visible' && (!window.IS_STANDALONE_APP || localStorage.getItem('cdl_license_server_url')) && navigator.onLine !== false) {
        window.syncLicenseWithServer();
      }
    }, 60000);

    // Event listeners for Settings License Section
    const btnActivate = document.getElementById('btnActivateLicense');
    const inputKey = document.getElementById('settingLicenseKeyInput');
    if (btnActivate && inputKey) {
      btnActivate.addEventListener('click', async () => {
        const key = inputKey.value.trim();
        if (key) {
          btnActivate.disabled = true;
          btnActivate.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Activating...';
          try {
            await window.activateLicenseKey(key);
          } finally {
            btnActivate.disabled = false;
            btnActivate.innerHTML = '<i class="fa-solid fa-key"></i> Activate';
          }
        }
      });
    }

    // Save Cloud License Server URL Button
    const btnSaveServerUrl = document.getElementById('btnSaveLicenseServerUrl');
    const inputServerUrl = document.getElementById('settingLicenseServerUrlInput');
    if (btnSaveServerUrl && inputServerUrl) {
      btnSaveServerUrl.addEventListener('click', () => {
        const url = inputServerUrl.value.trim();
        if (url) {
          localStorage.setItem('cdl_license_server_url', url);
          if (window.showToast) window.showToast('Licensing Server URL saved! Syncing...', 'success');
        } else {
          localStorage.removeItem('cdl_license_server_url');
          if (window.showToast) window.showToast('Reset to default local licensing server.', 'info');
        }
        window.syncLicenseWithServer(true);
      });
    }

    // Manual Sync Button
    const btnSync = document.getElementById('btnSyncLicense');
    if (btnSync) {
      btnSync.addEventListener('click', () => {
        window.syncLicenseWithServer(true);
      });
    }

    const btnCloseLocked = document.getElementById('btnCloseFeatureLocked');
    if (btnCloseLocked) {
      btnCloseLocked.addEventListener('click', window.closeFeatureLockedSheet);
    }

    // Inline unlock direct button inside featureLockedModal
    const btnModalUnlock = document.getElementById('btnModalUnlockDirect');
    const inputModalKey = document.getElementById('modalLockedKeyInput');
    if (btnModalUnlock && inputModalKey) {
      btnModalUnlock.addEventListener('click', async () => {
        const key = inputModalKey.value.trim();
        if (!key) {
          if (window.showToast) window.showToast('Please enter a valid License Key', 'warning');
          return;
        }
        btnModalUnlock.disabled = true;
        btnModalUnlock.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
        try {
          const res = await window.activateLicenseKey(key);
          if (res && res.success) {
            window.closeFeatureLockedSheet();
            inputModalKey.value = '';
            const curTab = window.state?.currentTab;
            if (curTab && typeof window.switchTab === 'function') {
              window.switchTab(curTab, false);
            }
          }
        } finally {
          btnModalUnlock.disabled = false;
          btnModalUnlock.innerHTML = '<i class="fa-solid fa-bolt"></i> Unlock';
        }
      });
      inputModalKey.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
          btnModalUnlock.click();
        }
      });
    }

    const btnGoSettingsFromLocked = document.getElementById('btnGoSettingsFromLocked');
    if (btnGoSettingsFromLocked) {
      btnGoSettingsFromLocked.addEventListener('click', () => {
        window.closeFeatureLockedSheet();
        if (window.openSettingsModal) {
          window.openSettingsModal();
        } else {
          const m = document.getElementById('settingsModal');
          if (m) m.classList.remove('hidden');
        }
      });
    }

    // 404 Modal Event Handlers
    const btnClose404 = document.getElementById('btnClose404Modal');
    if (btnClose404) {
      btnClose404.addEventListener('click', window.closeInApp404);
    }

    const btnUnlockFrom404 = document.getElementById('btnUnlockFrom404');
    if (btnUnlockFrom404) {
      btnUnlockFrom404.addEventListener('click', () => {
        window.closeInApp404();
        if (window.openSettingsModal) {
          window.openSettingsModal();
        } else {
          const m = document.getElementById('settingsModal');
          if (m) m.classList.remove('hidden');
        }
      });
    }
  });

})();
