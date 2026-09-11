'use strict';

/**
 * ============================================================================
 * CLOUD DRIVE LEECH - CORE RUNTIME PLATFORM & LIFECYCLE ENGINE (core.js)
 * ============================================================================
 * Architecture Overview:
 *   SECTION 1:  Global Runtime State, Platform Environment & Task Store
 *   SECTION 2:  Universal URL Resolver & API Host Configuration
 *   SECTION 3:  Kotlin Native & Standalone Interceptor Pipeline (window.fetch)
 *   SECTION 4:  Ultra-Modern Toast Notification Engine (Deduplicated & Animated)
 *   SECTION 5:  Tab Navigation, View Switcher & Deep Linking
 *   SECTION 6:  Universal In-App Direct Download Manager & Media Stream Dispatcher
 *   SECTION 7:  Modal Stack Management & Android Hardware Back-Button Engine
 *   SECTION 8:  AMOLED Pure Black & Cyber Dark Theme Controller
 *   SECTION 9:  Pull-to-Refresh Gesture Physics Engine & Tab Refresher
 *   SECTION 10: Smart Clipboard Auto-Paste Detector & Tab Auto-Healing Lifecycle
 *   SECTION 11: Real-Time Socket.IO Synchronization Hub & Health Monitor
 *   SECTION 12: Picture-in-Picture (PiP) Controller & DOM Bootstrap Lifecycle
 * ============================================================================
 */

// ============================================================================
// SECTION 1: Global Runtime State, Platform Environment & Task Store
// ============================================================================

(function initPlatformEnvironment() {
  const isCapacitor = window.Capacitor !== undefined ||
    window.location.protocol === 'capacitor:' ||
    window.location.protocol === 'file:' ||
    (window.location.hostname === 'localhost' && window.location.port === '');

  let configuredServer = '';
  window.IS_STANDALONE_APP = isCapacitor;

  // Standard web browser environment
  if (window.location.protocol.startsWith('http') && !isCapacitor && window.location.port) {
    configuredServer = window.location.origin;
  } else if (!isCapacitor) {
    try {
      configuredServer = localStorage.getItem('cloud_server_host') || '';
    } catch (_) { }
  }

  window.API_BASE = configuredServer;
})();

// Load the optional streaming library only when an HLS stream is opened.
let hlsLoadPromise = null;
window.ensureHlsLoaded = function () {
  if (window.Hls) return Promise.resolve(window.Hls);
  if (hlsLoadPromise) return hlsLoadPromise;

  hlsLoadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://cdn.jsdelivr.net/npm/hls.js@latest';
    script.async = true;
    script.onload = () => window.Hls ? resolve(window.Hls) : reject(new Error('HLS library unavailable'));
    script.onerror = () => reject(new Error('HLS library could not be loaded'));
    document.head.appendChild(script);
  });

  return hlsLoadPromise;
};

/** Global HTML Escaper for XSS Defense (Phase 40) */
window.escapeHtml = function (str = '') {
  if (typeof str !== 'string') return String(str ?? '');
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
};

/** Global App Memory State Store */
window.state = {
  currentTab: null,
  tasks: new Map(),
  downloads: new Map(),
  driveConnected: false,
  driveUser: null,
  selectedMovie: null
};

/** Offline Standalone Local Task Storage Helpers */
const LOCAL_TASKS_KEY = 'cdl_standalone_tasks';

function readLocalTasks() {
  try {
    return JSON.parse(localStorage.getItem(LOCAL_TASKS_KEY) || '[]');
  } catch (_) {
    return [];
  }
}

function writeLocalTasks(tasks) {
  try {
    localStorage.setItem(LOCAL_TASKS_KEY, JSON.stringify(tasks));
  } catch (_) { }
}

// ============================================================================
// SECTION 2: Universal URL Resolver & API Host Configuration
// ============================================================================

/**
 * Resolves relative API paths against configured backend host
 * Handles absolute URLs, data URIs, and blob URIs transparently.
 */
window.resolveApiUrl = function (url) {
  if (!url) return '';
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('blob:') || url.startsWith('data:')) {
    return url;
  }
  const base = (window.API_BASE || '').replace(/\/+$/, '');
  if (url.startsWith('/')) {
    return base ? `${base}${url}` : url;
  }
  return base ? `${base}/${url}` : url;
};

/**
 * Dynamic server host switcher (e.g. for self-hosted Node server)
 */
window.setServerHost = function (newHost) {
  if (window.IS_STANDALONE_APP) {
    window.API_BASE = '';
    try { localStorage.removeItem('cloud_server_host'); } catch (_) { }
    if (window.showToast) {
      window.showToast('Standalone mode: Node backend is disabled', 'info');
    }
    return;
  }

  let clean = (newHost || '').trim().replace(/\/+$/, '');
  if (clean && !clean.startsWith('http://') && !clean.startsWith('https://')) {
    clean = 'http://' + clean;
  }

  window.API_BASE = clean;
  try {
    localStorage.setItem('cloud_server_host', clean);
  } catch (_) { }

  if (window.socket && window.socket.disconnect) {
    try {
      window.socket.disconnect();
      if (clean && window.io) {
        window.socket = window.io(clean);
      }
    } catch (_) { }
  }

  if (window.checkStatus) window.checkStatus();
};

// ============================================================================
// SECTION 3: Strongly-Typed Clean Native Bridge Architecture (NativeBridge)
// ============================================================================

(function initNativeBridge() {
  function bridgeSuccess(data = {}) {
    return {
      success: true,
      data: data,
      errorCode: null,
      errorMessage: null
    };
  }

  function bridgeError(code, message) {
    return {
      success: false,
      data: null,
      errorCode: code || 'UNKNOWN_ERROR',
      errorMessage: message || 'An unexpected native bridge error occurred.'
    };
  }

  // 1. NativeApp Module
  const NativeApp = {
    isNativePlatform: function () {
      return Boolean(window.Capacitor?.isNativePlatform?.());
    },
    getAppVersion: function () {
      return bridgeSuccess({
        version: window.APP_VERSION || '1.0.5',
        buildEnv: window.CLD_CONFIG?.BUILD_ENV || 'production',
        isStandalone: Boolean(window.IS_STANDALONE_APP)
      });
    },
    checkHealth: async function () {
      const plugins = window.Capacitor?.Plugins || {};
      const status = {
        nativeGdrive: Boolean(plugins.NativeGdrive),
        nativeExtractor: Boolean(plugins.NativeExtractor),
        nativeDownload: Boolean(plugins.NativeDownload),
        nativePlayer: Boolean(plugins.NativePlayer),
        nativeLicense: Boolean(plugins.NativeLicense),
        nativeTorrent: Boolean(plugins.NativeTorrent),
        nativeVpn: Boolean(plugins.NativeVpn),
        nativeBrowser: Boolean(plugins.NativeBrowser),
        nativeStorage: Boolean(plugins.NativeStorage)
      };
      return bridgeSuccess(status);
    }
  };

  // 2. NativeStorage Module
  const NativeStorage = {
    getItem: async function (key) {
      if (!key || typeof key !== 'string') return bridgeError('INVALID_KEY', 'Storage key must be a valid string');
      try {
        if (window.Capacitor?.Plugins?.NativeStorage?.getItem) {
          const res = await window.Capacitor.Plugins.NativeStorage.getItem({ key });
          return bridgeSuccess({ value: res?.value || null });
        }
        return bridgeSuccess({ value: localStorage.getItem(key) });
      } catch (err) {
        return bridgeError('STORAGE_READ_FAILED', err.message);
      }
    },
    setItem: async function (key, value) {
      if (!key || typeof key !== 'string') return bridgeError('INVALID_KEY', 'Storage key must be a valid string');
      try {
        const valStr = typeof value === 'string' ? value : JSON.stringify(value);
        if (window.Capacitor?.Plugins?.NativeStorage?.setItem) {
          await window.Capacitor.Plugins.NativeStorage.setItem({ key, value: valStr });
        }
        try { localStorage.setItem(key, valStr); } catch (_) { }
        return bridgeSuccess({ key, stored: true });
      } catch (err) {
        return bridgeError('STORAGE_WRITE_FAILED', err.message);
      }
    },
    removeItem: async function (key) {
      if (!key || typeof key !== 'string') return bridgeError('INVALID_KEY', 'Storage key must be a valid string');
      try {
        if (window.Capacitor?.Plugins?.NativeStorage?.removeItem) {
          await window.Capacitor.Plugins.NativeStorage.removeItem({ key });
        }
        try { localStorage.removeItem(key); } catch (_) { }
        return bridgeSuccess({ key, removed: true });
      } catch (err) {
        return bridgeError('STORAGE_REMOVE_FAILED', err.message);
      }
    }
  };

  // 3. NativeDownloads Module
  const NativeDownloads = {
    startDownload: async function ({ url, filename, category = 'default' }) {
      if (!url || typeof url !== 'string') return bridgeError('INVALID_URL', 'Download URL is required');
      const safeFilename = (filename || 'download.mp4').replace(/[/\\?%*:|"<>]/g, '_');
      try {
        if (window.Capacitor?.Plugins?.NativeDownload?.startDownload) {
          const res = await window.Capacitor.Plugins.NativeDownload.startDownload({
            url,
            filename: safeFilename,
            category
          });
          return bridgeSuccess(res || { id: String(Date.now()), filename: safeFilename, status: 'downloading' });
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'NativeDownload plugin is not available on this platform');
      } catch (err) {
        return bridgeError('DOWNLOAD_START_FAILED', err.message);
      }
    },
    getDownloads: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeDownload?.getDownloads) {
          const res = await window.Capacitor.Plugins.NativeDownload.getDownloads();
          const items = Array.isArray(res?.downloads) ? res.downloads : [];
          // Update presentation cache safely
          if (window.state?.downloads) {
            window.state.downloads.clear();
            items.forEach(d => { if (d && d.id) window.state.downloads.set(d.id, d); });
          }
          return bridgeSuccess({ downloads: items });
        }
        const cached = Array.from(window.state?.downloads?.values() || []);
        return bridgeSuccess({ downloads: cached });
      } catch (err) {
        return bridgeError('GET_DOWNLOADS_FAILED', err.message);
      }
    },
    pauseDownload: async function (id) {
      if (!id) return bridgeError('INVALID_ID', 'Download ID is required');
      try {
        if (window.Capacitor?.Plugins?.NativeDownload?.pauseDownload) {
          await window.Capacitor.Plugins.NativeDownload.pauseDownload({ id });
        }
        const item = window.state?.downloads?.get(id);
        if (item) item.status = 'paused';
        return bridgeSuccess({ id, status: 'paused' });
      } catch (err) {
        return bridgeError('PAUSE_FAILED', err.message);
      }
    },
    resumeDownload: async function (id) {
      if (!id) return bridgeError('INVALID_ID', 'Download ID is required');
      try {
        if (window.Capacitor?.Plugins?.NativeDownload?.resumeDownload) {
          await window.Capacitor.Plugins.NativeDownload.resumeDownload({ id });
        }
        const item = window.state?.downloads?.get(id);
        if (item) item.status = 'downloading';
        return bridgeSuccess({ id, status: 'downloading' });
      } catch (err) {
        return bridgeError('RESUME_FAILED', err.message);
      }
    },
    deleteDownload: async function ({ id, path, filename }) {
      if (!id) return bridgeError('INVALID_ID', 'Download ID is required');
      try {
        if (window.Capacitor?.Plugins?.NativeDownload?.deleteDownload) {
          await window.Capacitor.Plugins.NativeDownload.deleteDownload({
            id,
            path: path || '',
            filename: filename || ''
          });
        }
        if (window.state?.downloads) window.state.downloads.delete(id);
        return bridgeSuccess({ id, deleted: true });
      } catch (err) {
        return bridgeError('DELETE_FAILED', err.message);
      }
    }
  };

  // 4. NativeMedia Module
  const NativeMedia = {
    playVideo: function (params = {}) {
      if (!params.streamUrl && !params.url && !params.downloadUrl) {
        return bridgeError('INVALID_STREAM_URL', 'Media stream URL is required');
      }
      try {
        if (window.Capacitor?.Plugins?.NativePlayer?.playVideo) {
          window.Capacitor.Plugins.NativePlayer.playVideo(params);
          return bridgeSuccess({ launchedNativePlayer: true });
        }
        if (typeof window.openPlayer === 'function') {
          window.openPlayer(params);
          return bridgeSuccess({ launchedInAppPlayer: true });
        }
        return bridgeError('PLAYER_UNAVAILABLE', 'No suitable player could be launched');
      } catch (err) {
        return bridgeError('PLAY_FAILED', err.message);
      }
    },
    getProxiedStreamUrl: async function (url) {
      if (!url) return bridgeError('INVALID_URL', 'URL is required');
      try {
        if (window.Capacitor?.Plugins?.NativePlayer?.getProxiedStreamUrl) {
          const res = await window.Capacitor.Plugins.NativePlayer.getProxiedStreamUrl({ url });
          return bridgeSuccess({ url: res?.url || res?.proxiedUrl || url });
        }
        return bridgeSuccess({ url });
      } catch (err) {
        return bridgeError('PROXY_ERROR', err.message);
      }
    },
    getProxiedEmbedUrl: async function (url) {
      if (!url) return bridgeError('INVALID_URL', 'URL is required');
      try {
        if (window.Capacitor?.Plugins?.NativePlayer?.getProxiedEmbedUrl) {
          const res = await window.Capacitor.Plugins.NativePlayer.getProxiedEmbedUrl({ url });
          return bridgeSuccess({ url: res?.url || url });
        }
        return bridgeSuccess({ url });
      } catch (err) {
        return bridgeError('PROXY_ERROR', err.message);
      }
    }
  };

  // 5. NativeExtractor Module
  const NativeExtractor = {
    searchMovies: async function ({ query, page = 1 }) {
      const q = (query || '').trim();
      if (!q) return bridgeError('INVALID_QUERY', 'Search query cannot be empty');
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.searchMovies) {
          const res = await window.Capacitor.Plugins.NativeExtractor.searchMovies({ query: q, page });
          if (res && Array.isArray(res.results) && res.results.length > 0) {
            return bridgeSuccess({ results: res.results });
          }
        }
        if (window.StandaloneEngine?.searchMovies) {
          const results = await window.StandaloneEngine.searchMovies(q);
          return bridgeSuccess({ results: results || [] });
        }
        return bridgeSuccess({ results: [] });
      } catch (err) {
        return bridgeError('MOVIE_SEARCH_FAILED', err.message);
      }
    },
    getMovieDetails: async function (url) {
      if (!url) return bridgeError('INVALID_URL', 'Movie details URL is required');
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.getMovieDetails) {
          const res = await window.Capacitor.Plugins.NativeExtractor.getMovieDetails({ url });
          if (res?.details) return bridgeSuccess({ details: res.details });
        }
        if (window.StandaloneEngine?.getMovieDetails) {
          const details = await window.StandaloneEngine.getMovieDetails(url);
          return bridgeSuccess({ details });
        }
        return bridgeError('MOVIE_DETAILS_NOT_FOUND', 'Could not resolve movie details');
      } catch (err) {
        return bridgeError('MOVIE_DETAILS_FAILED', err.message);
      }
    },
    resolveMovieStream: async function (url) {
      if (!url) return bridgeError('INVALID_URL', 'Movie stream URL is required');
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.resolveMovieStream) {
          const res = await window.Capacitor.Plugins.NativeExtractor.resolveMovieStream({ url });
          if (res?.streamUrl || res?.downloadUrl) return bridgeSuccess(res);
        }
        if (window.StandaloneEngine?.resolveFinalDownloadUrl) {
          const resolved = await window.StandaloneEngine.resolveFinalDownloadUrl(url);
          return bridgeSuccess(resolved);
        }
        return bridgeError('MOVIE_STREAM_FAILED', 'Could not resolve movie stream');
      } catch (err) {
        return bridgeError('MOVIE_RESOLVE_FAILED', err.message);
      }
    },
    searchAdult: async function ({ query = 'popular', page = 1, source = 'all' }) {
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.searchAdult) {
          const res = await window.Capacitor.Plugins.NativeExtractor.searchAdult({ query, page, source });
          if (res && Array.isArray(res.results) && res.results.length > 0) {
            return bridgeSuccess({ results: res.results });
          }
        }
        if (window.StandaloneEngine?.searchAdult) {
          const results = await window.StandaloneEngine.searchAdult(query, page, source);
          return bridgeSuccess({ results: results || [] });
        }
        return bridgeSuccess({ results: [] });
      } catch (err) {
        return bridgeError('ADULT_SEARCH_FAILED', err.message);
      }
    },
    resolveAdultStream: async function (url) {
      if (!url) return bridgeError('INVALID_URL', 'Adult video URL is required');
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.resolveAdultStream) {
          const res = await window.Capacitor.Plugins.NativeExtractor.resolveAdultStream({ url });
          if (res) return bridgeSuccess(res);
        }
        if (window.StandaloneEngine?.resolveAdultStream) {
          const resolved = await window.StandaloneEngine.resolveAdultStream(url);
          return bridgeSuccess(resolved);
        }
        return bridgeError('ADULT_RESOLVE_FAILED', 'Could not resolve adult stream');
      } catch (err) {
        return bridgeError('ADULT_RESOLVE_ERROR', err.message);
      }
    },
    extractMedia: async function (url) {
      if (!url) return bridgeError('INVALID_URL', 'Media URL is required');
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.extractMedia) {
          const res = await window.Capacitor.Plugins.NativeExtractor.extractMedia({ url });
          if (res) return bridgeSuccess(res);
        }
        if (window.StandaloneEngine?.extractMedia) {
          const data = await window.StandaloneEngine.extractMedia(url);
          return bridgeSuccess(data);
        }
        return bridgeError('MEDIA_EXTRACTION_FAILED', 'Could not extract media formats');
      } catch (err) {
        return bridgeError('MEDIA_EXTRACT_ERROR', err.message);
      }
    }
  };

  // 6. NativeDrive Module
  const NativeDrive = {
    getActiveAccount: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.getActiveAccount) {
          const acc = await window.Capacitor.Plugins.NativeGdrive.getActiveAccount();
          const isConn = Boolean(acc && acc.connected && acc.email);
          window.state.driveConnected = isConn;
          window.state.driveUser = isConn ? acc : null;
          return bridgeSuccess(acc || { connected: false });
        }
        window.state.driveConnected = false;
        window.state.driveUser = null;
        return bridgeSuccess({ connected: false });
      } catch (err) {
        window.state.driveConnected = false;
        window.state.driveUser = null;
        return bridgeError('DRIVE_ACCOUNT_FAILED', err.message);
      }
    },
    launchOAuth: async function (switchAccount = false) {
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.launchGoogleOAuthBrowser) {
          const res = await window.Capacitor.Plugins.NativeGdrive.launchGoogleOAuthBrowser({ switchAccount });
          return bridgeSuccess(res || {});
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'NativeGdrive plugin is unavailable');
      } catch (err) {
        return bridgeError('OAUTH_LAUNCH_FAILED', err.message);
      }
    },
    disconnect: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.disconnectAccount) {
          await window.Capacitor.Plugins.NativeGdrive.disconnectAccount();
        }
        window.state.driveConnected = false;
        window.state.driveUser = null;
        return bridgeSuccess({ disconnected: true });
      } catch (err) {
        return bridgeError('DISCONNECT_FAILED', err.message);
      }
    },
    listFiles: async function (category = 'all') {
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.listFiles) {
          const res = await window.Capacitor.Plugins.NativeGdrive.listFiles({ category });
          return bridgeSuccess({ files: Array.isArray(res?.files) ? res.files : [] });
        }
        return bridgeSuccess({ files: [] });
      } catch (err) {
        return bridgeError('LIST_FILES_FAILED', err.message);
      }
    },
    uploadFile: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.uploadFile) {
          const res = await window.Capacitor.Plugins.NativeGdrive.uploadFile();
          return bridgeSuccess(res || {});
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'Upload plugin unavailable');
      } catch (err) {
        return bridgeError('UPLOAD_FAILED', err.message);
      }
    },
    deleteFile: async function (fileId) {
      if (!fileId) return bridgeError('INVALID_FILE_ID', 'File ID is required');
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.deleteFile) {
          const res = await window.Capacitor.Plugins.NativeGdrive.deleteFile({ fileId });
          return bridgeSuccess(res || { deleted: true });
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'Delete plugin unavailable');
      } catch (err) {
        return bridgeError('DELETE_FAILED', err.message);
      }
    },
    renameFile: async function ({ fileId, newName }) {
      if (!fileId || !newName) return bridgeError('INVALID_PARAMS', 'fileId and newName are required');
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.renameFile) {
          const res = await window.Capacitor.Plugins.NativeGdrive.renameFile({ fileId, newName });
          return bridgeSuccess(res || { renamed: true });
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'Rename plugin unavailable');
      } catch (err) {
        return bridgeError('RENAME_FAILED', err.message);
      }
    },
    createFolder: async function (folderName) {
      if (!folderName) return bridgeError('INVALID_NAME', 'Folder name is required');
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.createFolder) {
          const res = await window.Capacitor.Plugins.NativeGdrive.createFolder({ name: folderName, folderName });
          return bridgeSuccess(res || { created: true });
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'Create folder plugin unavailable');
      } catch (err) {
        return bridgeError('CREATE_FOLDER_FAILED', err.message);
      }
    },
    shareFilePublicly: async function (fileId) {
      if (!fileId) return bridgeError('INVALID_FILE_ID', 'File ID is required');
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.makeFilePublicAndGetShareLink) {
          const res = await window.Capacitor.Plugins.NativeGdrive.makeFilePublicAndGetShareLink({
            fileId,
            makePublic: true
          });
          return bridgeSuccess(res || {});
        }
        return bridgeSuccess({ shareLink: `https://drive.google.com/file/d/${fileId}/view?usp=sharing` });
      } catch (err) {
        return bridgeError('SHARE_FAILED', err.message);
      }
    },
    startCloudTransferNative: async function ({ title, url, type = 'media' }) {
      if (!url) return bridgeError('INVALID_URL', 'Source URL is required');
      try {
        if (window.Capacitor?.Plugins?.NativeGdrive?.startCloudTransferNative) {
          const res = await window.Capacitor.Plugins.NativeGdrive.startCloudTransferNative({ title, url, type });
          return bridgeSuccess(res || {});
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'Native cloud transfer plugin is unavailable');
      } catch (err) {
        return bridgeError('TRANSFER_START_FAILED', err.message);
      }
    }
  };

  // 7. NativeVPN Module
  const NativeVPN = {
    startVpn: async function (profile = {}) {
      try {
        if (window.Capacitor?.Plugins?.NativeVpn?.startVpn) {
          const res = await window.Capacitor.Plugins.NativeVpn.startVpn(profile);
          return bridgeSuccess(res || { started: true });
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'NativeVPN plugin is unavailable');
      } catch (err) {
        return bridgeError('VPN_START_FAILED', err.message);
      }
    },
    stopVpn: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeVpn?.stopVpn) {
          const res = await window.Capacitor.Plugins.NativeVpn.stopVpn();
          return bridgeSuccess(res || { stopped: true });
        }
        return bridgeSuccess({ stopped: true });
      } catch (err) {
        return bridgeError('VPN_STOP_FAILED', err.message);
      }
    },
    getStatus: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeVpn?.getVpnStatus) {
          const res = await window.Capacitor.Plugins.NativeVpn.getVpnStatus();
          return bridgeSuccess(res || { isRunning: false });
        }
        return bridgeSuccess({ isRunning: false });
      } catch (err) {
        return bridgeError('VPN_STATUS_FAILED', err.message);
      }
    }
  };

  // 8. NativeBrowser Module
  const NativeBrowser = {
    openUrl: async function (url) {
      if (!url) return bridgeError('INVALID_URL', 'URL is required');
      try {
        if (window.Capacitor?.Plugins?.NativeBrowser?.openBrowser) {
          const res = await window.Capacitor.Plugins.NativeBrowser.openBrowser({ url });
          return bridgeSuccess(res || { opened: true });
        }
        window.open(url, '_blank');
        return bridgeSuccess({ opened: true });
      } catch (err) {
        return bridgeError('BROWSER_OPEN_FAILED', err.message);
      }
    }
  };

  // 9. NativeLicense Module
  const NativeLicense = {
    getInstallationIdentity: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeLicense?.getInstallationIdentity) {
          const res = await window.Capacitor.Plugins.NativeLicense.getInstallationIdentity();
          return bridgeSuccess(res || {});
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'NativeLicense plugin is unavailable');
      } catch (err) {
        return bridgeError('IDENTITY_FAILED', err.message);
      }
    },
    getDeviceInfo: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeLicense?.getDeviceInfo) {
          const res = await window.Capacitor.Plugins.NativeLicense.getDeviceInfo();
          return bridgeSuccess(res || {});
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'NativeLicense plugin is unavailable');
      } catch (err) {
        return bridgeError('DEVICE_INFO_FAILED', err.message);
      }
    },
    saveLicenseToken: async function (token) {
      if (!token) return bridgeError('INVALID_TOKEN', 'Token is required');
      try {
        if (window.Capacitor?.Plugins?.NativeLicense?.saveLicenseToken) {
          const res = await window.Capacitor.Plugins.NativeLicense.saveLicenseToken({ token });
          return bridgeSuccess(res || { saved: true });
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'NativeLicense plugin is unavailable');
      } catch (err) {
        return bridgeError('SAVE_TOKEN_FAILED', err.message);
      }
    },
    getLicenseToken: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeLicense?.getLicenseToken) {
          const res = await window.Capacitor.Plugins.NativeLicense.getLicenseToken();
          return bridgeSuccess({ token: res?.token || null });
        }
        return bridgeSuccess({ token: null });
      } catch (err) {
        return bridgeError('GET_TOKEN_FAILED', err.message);
      }
    },
    clearLicenseToken: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeLicense?.clearLicenseToken) {
          await window.Capacitor.Plugins.NativeLicense.clearLicenseToken();
        }
        return bridgeSuccess({ cleared: true });
      } catch (err) {
        return bridgeError('CLEAR_TOKEN_FAILED', err.message);
      }
    },
    saveVerifiedLicense: async function ({ token, plan, status, expiresAt, payload }) {
      try {
        if (window.Capacitor?.Plugins?.NativeLicense?.saveVerifiedLicense) {
          const res = await window.Capacitor.Plugins.NativeLicense.saveVerifiedLicense({
            token: token || '',
            plan: plan || 'FREE',
            status: status || 'ACTIVE',
            expiresAt: expiresAt || '',
            payload: typeof payload === 'object' ? JSON.stringify(payload) : (payload || '')
          });
          return bridgeSuccess(res || { saved: true });
        }
        return bridgeError('PLUGIN_UNAVAILABLE', 'NativeLicense plugin is unavailable');
      } catch (err) {
        return bridgeError('SAVE_VERIFIED_FAILED', err.message);
      }
    },
    getVerifiedLicense: async function () {
      try {
        if (window.Capacitor?.Plugins?.NativeLicense?.getVerifiedLicense) {
          const res = await window.Capacitor.Plugins.NativeLicense.getVerifiedLicense();
          return bridgeSuccess(res || { valid: false, plan: 'FREE' });
        }
        return bridgeSuccess({ valid: false, plan: 'FREE' });
      } catch (err) {
        return bridgeError('GET_VERIFIED_FAILED', err.message);
      }
    }
  };

  // 10. NativeUpdates Module
  const NativeUpdates = {
    checkUpdates: async function (manual = false) {
      try {
        if (typeof window.checkOtaUpdate === 'function') {
          const res = await window.checkOtaUpdate(manual);
          return bridgeSuccess(res || { upToDate: true });
        }
        return bridgeSuccess({ upToDate: true });
      } catch (err) {
        return bridgeError('UPDATE_CHECK_FAILED', err.message);
      }
    }
  };

  // Expose as top-level clean abstractions
  window.NativeApp = NativeApp;
  window.NativeStorage = NativeStorage;
  window.NativeDownloads = NativeDownloads;
  window.NativeMedia = NativeMedia;
  window.NativeExtractor = NativeExtractor;
  window.NativeDrive = NativeDrive;
  window.NativeVPN = NativeVPN;
  window.NativeBrowser = NativeBrowser;
  window.NativeLicense = NativeLicense;
  window.NativeUpdates = NativeUpdates;

  window.NativeBridge = {
    App: NativeApp,
    Storage: NativeStorage,
    Downloads: NativeDownloads,
    Media: NativeMedia,
    Extractor: NativeExtractor,
    Drive: NativeDrive,
    VPN: NativeVPN,
    Browser: NativeBrowser,
    License: NativeLicense,
    Updates: NativeUpdates
  };
})();

// ============================================================================
// SECTION 4: Ultra-Modern Toast Notification Engine (Deduplicated & Animated)
// ============================================================================

let lastToastMessage = '';
let lastToastTime = 0;

/**
 * Modern floating glassmorphism toast notifier with rate-limit deduplication
 * @param {string|object} message
 * @param {'info'|'success'|'error'|'warning'} type
 */
window.showToast = function (message, type = 'info') {
  const container = document.getElementById('toastContainer');
  if (!container) return;

  // Clean raw technical error strings
  let cleanMsg = typeof message === 'string' ? message : (message?.message || JSON.stringify(message));
  cleanMsg = cleanMsg.replace(/^Error:\s*/i, '');
  cleanMsg = cleanMsg.replace(/Request failed with status code\s*(\d+)/i, 'Response status ($1)');

  // Deduplicate identical toast messages triggered within 2000ms
  const now = Date.now();
  if (cleanMsg === lastToastMessage && (now - lastToastTime) < 2000) {
    return;
  }
  lastToastMessage = cleanMsg;
  lastToastTime = now;

  const toast = document.createElement('div');
  toast.className = `toast ${type}`;

  let icon = 'fa-circle-info';
  if (type === 'success') icon = 'fa-circle-check';
  else if (type === 'error') icon = 'fa-triangle-exclamation';
  else if (type === 'warning') icon = 'fa-circle-exclamation';

  toast.innerHTML = `
    <div class="toast-content">
      <i class="fa-solid ${icon} toast-icon"></i>
      <span class="toast-text">${cleanMsg}</span>
    </div>
    <button class="toast-close" title="Dismiss">&times;</button>
    <div class="toast-progress-bar"></div>
  `;

  toast.querySelector('.toast-close')?.addEventListener('click', (e) => {
    e.stopPropagation();
    toast.classList.add('toast-leaving');
    setTimeout(() => toast.remove(), 250);
  });

  container.appendChild(toast);

  // Maintain maximum 2 concurrent toasts on screen
  while (container.children.length > 2) {
    container.firstElementChild.remove();
  }

  const timer = setTimeout(() => {
    if (toast.parentNode) {
      toast.classList.add('toast-leaving');
      setTimeout(() => toast.remove(), 250);
    }
  }, 2600);

  toast.addEventListener('mouseenter', () => clearTimeout(timer));
};

// ============================================================================
// SECTION 5: Tab Navigation, View Switcher & Deep Linking
// ============================================================================

/**
 * Universal Tab Navigation Switcher with persistence, routing, and clean modal resets
 * @param {string} tabName
 * @param {boolean} pushHistory
 */
window.switchTab = function (tabName, pushHistory = true) {
  // Alias redirections
  if (tabName === 'media') tabName = 'downloads';
  if (tabName === 'tasks') {
    tabName = 'downloads';
    setTimeout(() => {
      if (window.switchToDriveTransfersSubTab) {
        window.switchToDriveTransfersSubTab();
      }
    }, 40);
  }

  const validTabs = ['movies', 'downloads', 'adult', 'gallery', 'browser', 'drive'];
  if (!validTabs.includes(tabName)) tabName = 'movies';

  // Central Feature Access Gatekeeper (Master Prompt Section 13 & 14)
  if (typeof window.isFeatureAvailable === 'function') {
    const featId = window.getFeatureIdForTab ? window.getFeatureIdForTab(tabName) : null;
    if (featId && !window.isFeatureAvailable(featId)) {
      if (window.showFeatureLockedSheet) {
        window.showFeatureLockedSheet(featId);
      }
      return; // Gatekeeper blocks access to unauthorized tab
    }
  }

  if (window.state.currentTab === tabName) return;
  const prevTab = window.state.currentTab;
  window.state.currentTab = tabName;

  // 1. INSTANT VISUAL SWITCH FIRST (0ms latency)
  const views = {
    movies: document.getElementById('view-movies'),
    downloads: document.getElementById('view-downloads'),
    adult: document.getElementById('view-adult'),
    gallery: document.getElementById('view-gallery'),
    browser: document.getElementById('view-browser'),
    drive: document.getElementById('view-drive')
  };

  const navItems = document.querySelectorAll('.nav-item');
  navItems.forEach(nav => nav.classList.toggle('active', nav.dataset.tab === tabName));

  Object.keys(views).forEach(key => {
    if (views[key]) {
      views[key].classList.toggle('active', key === tabName);
    }
  });

  // Toggle full-screen in-browser layout class on body
  document.body.classList.toggle('in-browser-mode', tabName === 'browser');

  // Terminate active players if open
  if (window.closePlayer && document.getElementById('playerModal') && !document.getElementById('playerModal').classList.contains('hidden')) {
    window.closePlayer();
  }
  if (window.closeMovieModal && document.getElementById('movieModal') && !document.getElementById('movieModal').classList.contains('hidden')) {
    window.closeMovieModal(false);
  }

  // Dismiss open modals efficiently (only touch if active)
  if (document.querySelector('.modal-overlay:not(.hidden)')) {
    document.querySelectorAll('.modal-overlay:not(.hidden)').forEach(m => {
      m.classList.add('hidden');
      m.style.removeProperty('display');
    });
    if (window.modalStack) window.modalStack = [];
  }

  document.body.classList.remove('modal-open');
  document.body.style.overflow = '';
  document.documentElement.style.overflow = '';

  // Persist active tab selection
  try {
    localStorage.setItem('cloud_active_tab', tabName);
  } catch (_) { }

  // Update browser history hash
  if (window.location.hash !== '#' + tabName) {
    if (pushHistory) {
      history.pushState({ tab: tabName }, '', '#' + tabName);
    } else {
      history.replaceState({ tab: tabName }, '', '#' + tabName);
    }
  }

  // Dispatch custom active-tab-changed event
  window.dispatchEvent(new CustomEvent('cloud:active-tab-changed', { detail: { tab: tabName, prevTab } }));

  // Control native in-app browser tab visibility
  const nativeBrowser = window.Capacitor?.Plugins?.NativeBrowser;
  if (nativeBrowser) {
    if (tabName === 'browser') {
      nativeBrowser.showBrowser({ url: 'about:home' }).catch(() => { });
    } else {
      nativeBrowser.hideBrowser().catch(() => { });
    }
  }

  // Stop 18+ HTML5 video / iframe playback if leaving adult tab
  const adultHtmlVideoPlayer = document.getElementById('adultHtmlVideoPlayer');
  const adultIframePlayer = document.getElementById('adultIframePlayer');
  if (tabName !== 'adult') {
    if (adultHtmlVideoPlayer) {
      adultHtmlVideoPlayer.pause();
    }
    if (adultIframePlayer && adultIframePlayer.src && adultIframePlayer.src !== 'about:blank') {
      adultIframePlayer.src = 'about:blank';
      adultIframePlayer.classList.add('hidden');
    }
  }

  // 2. SCHEDULE HEAVIER DATA OPERATIONS OFF THE ANIMATION FRAME
  requestAnimationFrame(() => {
    // Auto-fetch data for newly active tab if empty
    if (tabName === 'movies') {
      const movieGrid = document.getElementById('movieGrid');
      if (movieGrid && movieGrid.children.length === 0 && window.searchMovies) {
        window.searchMovies('2026');
      }
    }

    if (tabName === 'adult') {
      if (typeof window.checkAdultGateState === 'function') {
        window.checkAdultGateState();
      }
      const adultGrid = document.getElementById('adultGrid');
      const isUnlocked = typeof window.isAdultGateUnlocked === 'function' ? window.isAdultGateUnlocked() : false;
      if (isUnlocked && adultGrid && adultGrid.children.length === 0 && window.searchAdult) {
        window.searchAdult('for-you', window.currentAdultSource || 'all');
      }
    }

    if (tabName === 'gallery' && window.loadOfflineGallery) {
      const now = Date.now();
      if (!window._lastGalleryScan || (now - window._lastGalleryScan > 20000)) {
        window._lastGalleryScan = now;
        window.loadOfflineGallery();
      }
    }

    if (tabName === 'drive' && window.loadDriveAccountProfile && window.loadDriveFiles) {
      const now = Date.now();
      const driveGrid = document.getElementById('driveFilesGrid');
      const hasFiles = driveGrid && driveGrid.children.length > 0;
      if (!hasFiles || !window._lastDriveScan || (now - window._lastDriveScan > 30000)) {
        window._lastDriveScan = now;
        window.loadDriveAccountProfile();
        window.loadDriveFiles();
      }
    }

    if (tabName === 'downloads') {
      if (window.renderDownloadsList) window.renderDownloadsList();
      if (window.renderTasks) window.renderTasks();
    }

    if (tabName === 'tasks' && window.renderTasks) {
      window.renderTasks();
    }
  });
};

// ============================================================================
// SECTION 6: Universal In-App Direct Download Manager & Stream Dispatcher
// ============================================================================

/**
 * Universal Direct Download Manager Pipeline
 * Resolves redirectors, normalizes cloud endpoints, auto-categorizes, and routes to Kotlin Native Download Manager.
 * @param {string} url
 * @param {string} filename
 * @param {string|null} customCategory
 */
window.triggerDirectDownload = async function (url, filename, customCategory = null) {
  if (!url) {
    window.showToast('Invalid download URL', 'error');
    return;
  }

  // 1. Dynamic Filename & Extension Extraction (No Forced .mp4)
  let cleanName = (filename || '').replace(/[/\\?%*:|"<>]/g, '_').trim();
  let targetUrl = url.trim();

  // 2. Unwrap nested stream URL param if present
  if (targetUrl.includes('url=')) {
    try {
      const parts = targetUrl.split('url=');
      if (parts.length > 1) {
        const decoded = decodeURIComponent(parts[1].split('&')[0]);
        if (decoded.startsWith('http://') || decoded.startsWith('https://')) {
          targetUrl = decoded;
        }
      }
    } catch (_) { }
  }

  // Normalize PixelDrain and Google Drive direct binary endpoints early
  const pdM = targetUrl.match(/pixeldrain\.com\/(?:u|l|api\/file|e)\/([a-zA-Z0-9_-]+)/);
  if (pdM) {
    targetUrl = `https://pixeldrain.com/api/file/${pdM[1]}?download`;
  }
  const gdM = targetUrl.match(/(?:drive\.google\.com|drive\.usercontent\.google\.com)\/(?:file\/d\/|open\?id=|uc\?id=|download\?id=)([a-zA-Z0-9_-]+)/);
  if (gdM) {
    targetUrl = `https://drive.usercontent.google.com/download?id=${gdM[1]}&export=download&authuser=0`;
  }

  if (!cleanName || cleanName === 'video_download' || cleanName === 'Media_Download' || cleanName === 'download') {
    try {
      const parsedPath = new URL(targetUrl).pathname;
      const lastSeg = parsedPath.split('/').filter(Boolean).pop();
      if (lastSeg && lastSeg.includes('.')) {
        cleanName = decodeURIComponent(lastSeg).replace(/[/\\?%*:|"<>]/g, '_').trim();
      }
    } catch (_) { }
  }

  if (!cleanName) cleanName = 'download_file';

  // If filename still lacks extension, check if targetUrl has one (zip, rar, apk, pdf, mp3, mkv, etc.)
  if (!cleanName.includes('.')) {
    const extM = targetUrl.match(/\.([a-zA-Z0-9]{2,5})(?:[?#]|$)/);
    if (extM && !['php', 'html', 'htm', 'asp', 'aspx', 'jsp', 'cgi'].includes(extM[1].toLowerCase())) {
      cleanName += '.' + extM[1];
    }
  }

  // Check for duplicate active downloads (Section 22)
  if (window.state?.downloads) {
    for (const [, item] of window.state.downloads.entries()) {
      if ((item.status === 'downloading' || item.status === 'pending' || item.status === 'resolving') &&
        (item.url === targetUrl || item.fileName === cleanName || item.filename === cleanName || item.title === cleanName)) {
        window.showToast('Download already in progress.', 'info');
        return;
      }
    }
  }

  // 3. Auto-route Torrent / Magnet links strictly to Native Torrent Engine
  if (targetUrl.startsWith('magnet:') || targetUrl.includes('.torrent') || targetUrl.includes('/torrent/download/')) {
    if (window.startInAppTorrentDownload) {
      window.startInAppTorrentDownload(targetUrl, cleanName);
      return;
    } else {
      window.showToast('⚠️ Native BitTorrent engine is required for torrents/magnets.', 'error');
      return;
    }
  }

  // Seamlessly route client-side encrypted MEGA links to browser
  if (targetUrl.includes('mega.nz') || targetUrl.includes('mega.co.nz') || targetUrl.includes('mega.io')) {
    if (window.Capacitor?.Plugins?.NativeBrowser?.showBrowser) {
      window.showToast('⚡ Opening MEGA Cloud in browser to decrypt & download...', 'info');
      if (window.switchTab) window.switchTab('browser');
      window.Capacitor.Plugins.NativeBrowser.showBrowser({ url: targetUrl }).catch(() => {
        window.open(targetUrl, '_system');
      });
    } else {
      window.showToast('⚡ Opening MEGA in browser...', 'info');
      window.open(targetUrl, '_system');
    }
    return;
  }

  // 4. Fast-Pass Direct Link Detection
  const isDirectLink = /\.(mp4|mkv|avi|webm|mov|flv|ts|m4v|3gp|mp3|m4a|aac|flac|wav|ogg|opus|zip|rar|7z|tar|gz|bz2|xz|iso|apk|xapk|pdf|epub|mobi|doc|docx|xls|xlsx|ppt|pptx|txt|jpg|jpeg|png|webp|bin)(?:[?#]|$)/i.test(targetUrl) ||
    targetUrl.includes('pixeldrain.com/api/file/') ||
    targetUrl.includes('drive.usercontent.google.com/download') ||
    targetUrl.includes('cloudflarestorage.com') ||
    targetUrl.includes('shegu.st') ||
    targetUrl.includes('workers.dev') ||
    targetUrl.includes('ddl.sinhalasub.net') ||
    targetUrl.includes('dlserver') ||
    targetUrl.includes('userdrive.org:8443') ||
    (targetUrl.includes('userdrive') && targetUrl.includes('/d/'));

  // 5. Auto-detect category
  let category = customCategory;
  if (!category || category === 'media') {
    const u = targetUrl.toLowerCase();
    const fn = cleanName.toLowerCase();
    if (fn.endsWith('.apk') || fn.endsWith('.xapk')) {
      category = 'apps';
    } else if (fn.endsWith('.zip') || fn.endsWith('.rar') || fn.endsWith('.7z') || fn.endsWith('.tar') || fn.endsWith('.iso') || fn.endsWith('.gz')) {
      category = 'files';
    } else if (fn.endsWith('.pdf') || fn.endsWith('.epub') || fn.endsWith('.doc') || fn.endsWith('.docx') || fn.endsWith('.txt')) {
      category = 'documents';
    } else if (fn.endsWith('.mp3') || fn.endsWith('.m4a') || fn.endsWith('.flac') || fn.endsWith('.wav') || fn.endsWith('.aac')) {
      category = 'music';
    } else if (u.includes('porn') || u.includes('hamster') || u.includes('eporner') || u.includes('xvideos') ||
      u.includes('xnxx') || u.includes('redtube') || u.includes('spankbang') || u.includes('xhcdn') ||
      u.includes('phncdn') || fn.includes('porn') || fn.includes('hamster') || fn.includes('eporner') ||
      fn.includes('18+') || fn.includes('cuckold') || fn.includes('jav') || fn.includes('waka')) {
      category = 'adult';
    } else if (u.includes('sinhala') || u.includes('baiscope') || u.includes('piratelk') || u.includes('sub') ||
      u.includes('movie') || fn.includes('sinhala') || fn.includes('baiscope') || fn.includes('sub') ||
      fn.includes('movie') || fn.endsWith('.mp4') || fn.endsWith('.mkv')) {
      category = 'movies';
    } else {
      category = 'files';
    }
  }

  // 6. Only resolve protector/web pages if NOT already a direct stream or file link
  if (!isDirectLink) {
    // 6a. Auto-resolve movie redirector & protector links on-device
    if (targetUrl.includes('/links/') || targetUrl.includes('sinhalasub') || targetUrl.includes('baiscope') ||
      targetUrl.includes('piratelk') || targetUrl.includes('sub.lk') || targetUrl.includes('cinesubz') ||
      targetUrl.includes('usersdrive') || targetUrl.includes('userdrive') || targetUrl.includes('filespayout') ||
      targetUrl.includes('cinejoy') || targetUrl.includes('netflix') || targetUrl.includes('shegu.st')) {
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.resolveMovieStream) {
          const nRes = await window.Capacitor.Plugins.NativeExtractor.resolveMovieStream({ url: targetUrl });
          if (nRes && (nRes.downloadUrl || nRes.streamUrl)) {
            targetUrl = nRes.downloadUrl || nRes.streamUrl;
          }
        } else if (window.StandaloneEngine?.resolveFinalDownloadUrl) {
          const sRes = await window.StandaloneEngine.resolveFinalDownloadUrl(targetUrl);
          if (sRes && (sRes.downloadUrl || sRes.streamUrl)) {
            targetUrl = sRes.downloadUrl || sRes.streamUrl;
          }
        }
      } catch (_) { }
    }

    // 6b. Auto-resolve 18+ Web Page URLs on-device
    if ((targetUrl.includes('eporner.com') || targetUrl.includes('xvideos.com') || targetUrl.includes('xnxx.com') ||
      targetUrl.includes('xhamster.com') || targetUrl.includes('pornhub.com') || targetUrl.includes('spankbang.com')) &&
      !targetUrl.includes('.mp4')) {
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.resolveAdultStream) {
          const aRes = await window.Capacitor.Plugins.NativeExtractor.resolveAdultStream({ url: targetUrl });
          if (aRes && (aRes.downloadUrl || aRes.streamUrl)) targetUrl = aRes.downloadUrl || aRes.streamUrl;
        } else if (window.StandaloneEngine?.resolveAdultStream) {
          const aRes = await window.StandaloneEngine.resolveAdultStream(targetUrl);
          if (aRes && (aRes.downloadUrl || aRes.streamUrl)) targetUrl = aRes.downloadUrl || aRes.streamUrl;
        }
      } catch (_) { }
    }

    // 6c. Auto-resolve Social Media (YouTube, TikTok, Instagram, Twitter)
    if ((targetUrl.includes('youtube.com') || targetUrl.includes('youtu.be') || targetUrl.includes('tiktok.com') ||
      targetUrl.includes('instagram.com') || targetUrl.includes('twitter.com') || targetUrl.includes('x.com')) &&
      !targetUrl.includes('.mp4') && !targetUrl.includes('googlevideo.com')) {
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.extractMedia) {
          const mRes = await window.Capacitor.Plugins.NativeExtractor.extractMedia({ url: targetUrl });
          const mInfo = mRes?.info || mRes?.data || {};
          if (mInfo.formats && mInfo.formats.length > 0) {
            const directFmt = mInfo.formats.find(f => f.downloadUrl && f.downloadUrl.startsWith('http') && !f.downloadUrl.includes('youtube.com/watch') && !f.downloadUrl.includes('instagram.com/p/')) || mInfo.formats[0];
            if (directFmt?.downloadUrl && directFmt.downloadUrl !== targetUrl) targetUrl = directFmt.downloadUrl;
          }
        } else if (window.StandaloneEngine?.extractMedia) {
          const mRes = await window.StandaloneEngine.extractMedia(targetUrl);
          const mInfo = mRes?.info || mRes || {};
          if (mInfo.formats && mInfo.formats.length > 0) {
            const directFmt = mInfo.formats.find(f => f.downloadUrl && f.downloadUrl.startsWith('http') && !f.downloadUrl.includes('youtube.com/watch') && !f.downloadUrl.includes('instagram.com/p/')) || mInfo.formats[0];
            if (directFmt?.downloadUrl && directFmt.downloadUrl !== targetUrl) targetUrl = directFmt.downloadUrl;
          }
        }
      } catch (_) { }
    }
  }

  // Root Cause Protection: If targetUrl is still a social media web page URL, do not download HTML
  const isStillWebpage = /^(https?:\/\/)?(www\.)?(youtube\.com\/watch|youtu\.be\/|tiktok\.com\/@|instagram\.com\/(p|reel|tv)|facebook\.com\/|fb\.watch\/|twitter\.com\/|x\.com\/)/i.test(targetUrl) &&
    !targetUrl.includes('googlevideo.com') &&
    !targetUrl.includes('.mp4') &&
    !targetUrl.includes('.m3u8') &&
    !targetUrl.includes('.webm');

  if (isStillWebpage) {
    window.showToast('⚠️ Could not extract direct video stream. Please check the link.', 'error');
    return;
  }

  // ⚡ 7. Direct Kotlin Native Android Download Manager (Instant Startup)
  if (window.Capacitor?.Plugins?.NativeDownload?.startDownload) {
    try {
      window.showToast(`📥 Starting Download: ${cleanName}`, 'success');
      const res = await window.Capacitor.Plugins.NativeDownload.startDownload({
        url: targetUrl,
        filename: cleanName,
        title: cleanName,
        category: category
      });
      if (res && res.id) {
        if (window.state && window.state.downloads) {
          window.state.downloads.set(res.id, {
            id: res.id,
            title: cleanName,
            fileName: cleanName,
            url: res.url || targetUrl,
            category: category,
            status: 'downloading',
            progress: 0,
            percent: 0,
            downloadedMB: '0.0',
            totalMB: '0.0',
            speedMBps: '0.0',
            etaSec: 0,
            createdTimestamp: Date.now()
          });
        }
        if (window.switchTab) {
          window.switchTab('downloads');
        }
        if (window.startDownloadsPolling) window.startDownloadsPolling();
        if (window.renderDownloadsList) {
          window.renderDownloadsList(true);
        } else if (window.renderDownloads) {
          window.renderDownloads(true);
        }
        const badge = document.getElementById('navDownloadsBadge');
        if (badge) {
          badge.classList.remove('hidden');
          badge.textContent = (window.state?.downloads?.size || 1).toString();
        }
        return;
      }
    } catch (e) {
      console.warn('[NativeDownload] Plugin fallback:', e);
    }
  }

  // 🌐 9. Client-Side Standalone Direct Download Fallback
  if (window.StandaloneEngine && window.StandaloneEngine.triggerDirectDownload) {
    window.StandaloneEngine.triggerDirectDownload(targetUrl, cleanName);
  } else {
    const a = document.createElement('a');
    a.href = targetUrl;
    a.download = cleanName;
    a.target = '_blank';
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    setTimeout(() => {
      try { document.body.removeChild(a); } catch (_) { }
    }, 1500);
  }
};

// ============================================================================
// ============================================================================
// SECTION 7: Modal Overlay Stack, In-App Browser & Android Back-Button Engine
// ============================================================================

window.modalStack = [];

/**
 * In-App Browser Context Tracker & Session Bridge
 * Enables embedded video players, web links, or user navigation to open in the APK's
 * built-in browser tab, and cleanly return to the player/modal on Android Back press.
 */
window._inAppBrowserReturnContext = null;

window.openInAppBrowser = function (url, originContext = null) {
  if (!url) return;
  const isPlayerOpen = document.getElementById('playerModal') && !document.getElementById('playerModal').classList.contains('hidden');
  const isMovieModalOpen = document.getElementById('movieModal') && !document.getElementById('movieModal').classList.contains('hidden');

  window._inAppBrowserReturnContext = originContext || {
    tab: window.state?.currentTab || 'movies',
    playerOpen: isPlayerOpen,
    movieModalOpen: isMovieModalOpen
  };

  if (window.switchTab) {
    window.switchTab('browser');
  }

  const nativeBrowser = window.Capacitor?.Plugins?.NativeBrowser;
  if (nativeBrowser?.showBrowser) {
    nativeBrowser.showBrowser({ url: url }).catch(() => { });
  } else {
    if (typeof window.loadWebBrowserUrl === 'function') {
      window.loadWebBrowserUrl(url);
    }
  }
};

window.closeInAppBrowser = function () {
  const nativeBrowser = window.Capacitor?.Plugins?.NativeBrowser;
  if (nativeBrowser?.hideBrowser) {
    nativeBrowser.hideBrowser().catch(() => { });
  }

  const ctx = window._inAppBrowserReturnContext;
  window._inAppBrowserReturnContext = null;

  if (ctx) {
    if (ctx === 'player' || ctx.playerOpen) {
      const pModal = document.getElementById('playerModal');
      if (pModal) {
        pModal.classList.remove('hidden');
        document.body.classList.add('modal-open');
      }
      return;
    }
    if (ctx === 'movieModal' || ctx.movieModalOpen) {
      const mModal = document.getElementById('movieModal');
      if (mModal) {
        mModal.classList.remove('hidden');
        document.body.classList.add('modal-open');
      }
      return;
    }
    if (ctx.tab && window.switchTab) {
      window.switchTab(ctx.tab);
      return;
    }
  }

  // Fallback: If currently in browser mode, return to 'movies' tab
  if (window.state?.currentTab === 'browser' && window.switchTab) {
    window.switchTab('movies');
  }
};

/**
 * Universal Known Ad / Betting / Malware Filter
 */
window.isKnownAdUrl = function (url) {
  if (!url || typeof url !== 'string') return false;
  const lower = url.toLowerCase();

  // Whitelist legitimate video providers, CDNs, and media sources
  if (lower.includes('vidsrc2.ru') || lower.includes('vidlink.pro') ||
    lower.includes('vidsrc') || lower.includes('cloudorchestranova') || lower.includes('vidsrcme') ||
    lower.includes('vidapi.cloud') || lower.includes('2embed') ||
    lower.includes('autoembed') || lower.includes('cineru') ||
    lower.includes('pixeldrain') || lower.includes('google') ||
    lower.includes('mega.nz') || lower.includes('mega.io') ||
    lower.includes('sinhalasub') || lower.includes('sub.lk') ||
    lower.includes('baiscope') || lower.includes('piratelk') ||
    lower.includes('themoviedb') || lower.includes('tmdb') ||
    lower.includes('cloudflare') || lower.includes('jsdelivr') ||
    lower.includes('localhost') || lower.includes('capacitor://') ||
    lower.includes('cinejoy.to') || lower.includes('shegu.st') ||
    lower.includes('netflix.com') || lower.includes('netmirror')) {
    if (!lower.includes('/ads/') && !lower.includes('/popunder') && !lower.includes('/onclick')) {
      return false;
    }
  }

  // Betting keywords
  const betting = [
    '1xbet', 'betwinner', 'betway', 'parimatch', 'melbet', 'mostbet',
    'linebet', 'dafabet', 'stake.com', 'bet365', '1x-bet', 'mega-pari',
    'bcgame', 'betmaster', 'betclic', 'bwin', 'betfair', 'pin-up', '1win'
  ];
  for (const b of betting) {
    if (lower.includes(b)) return true;
  }

  // Known Ad Networks, Clickjackers & Popunders
  const adNetworks = [
    'onclick', 'adcash', 'popads', 'adsterra', 'propellerads', 'propeller',
    'histats', 'exoclick', 'juicyads', 'doubleclick', 'googlesyndication',
    'adnxs', 'adsystem', 'adskeeper', 'mgid', 'outbrain', 'taboola',
    'trafficjunky', 'yieldmo', 'bidvertiser', 'revcontent', 'infolinks',
    'ero-advertising', 'trafficstars', 'yllix', 'clickadu', 'hilltopads',
    'richpush', 'evadav', 'monetag', 'coinhive', 'pubfuture', 'highrevenuenetwork',
    'profitablegatecpm', 'deloplen', 'whomeeno', 'bidgear', 'dexpredict',
    'creativecdn', 'popunder', 'adsupply', 'tsyndicate', 'adsco.re',
    'smartadserver', 'realsrv.com', 'tsyndicate.com', 'wpadmngr.com', 'onclickalgo.com',
    'propush', 'highcpmgate', 'profitablecpmrate', 'alwingulla', 'creative',
    'counter', 'banner', 'adserver', 'yadro', 'livejasmin', 'directrev'
  ];
  for (const ad of adNetworks) {
    if (lower.includes(ad)) return true;
  }

  return false;
};

// 🛡️ Global Early Window.Open Shield (blocks popups while player modal is active)
if (!window.__origWindowOpen) {
  window.__origWindowOpen = window.open;
  window.open = function (url, target, features) {
    const isPlayerOpen = document.getElementById('playerModal') && !document.getElementById('playerModal').classList.contains('hidden');
    if (isPlayerOpen) {
      console.warn('[AdBlock Shield] Completely blocked popup while player modal is active:', url);
      return null;
    }
    if (!url || typeof url !== 'string') return null;
    if (window.isKnownAdUrl && window.isKnownAdUrl(url)) {
      console.warn('[AdBlock Shield] Blocked known ad URL popup:', url);
      return null;
    }
    return window.__origWindowOpen.apply(window, arguments);
  };
}

/**
 * Opens modal with history tracking so Android Back Button dismisses it naturally
 */
window.openModalWithHistory = function (modalEl) {
  if (!modalEl) return;
  modalEl.style.removeProperty('display');
  modalEl.classList.remove('hidden');
  document.body.classList.add('modal-open');
  window.modalStack.push(modalEl);

  history.pushState({ modalOpen: true, id: modalEl.id }, '');
};

/**
 * Force unlocks page scrolling if no modals are open
 */
window.forceUnlockPageScroll = function () {
  const visibleModals = Array.from(document.querySelectorAll('.modal-overlay:not(.hidden)'));
  if (visibleModals.length === 0) {
    document.body.classList.remove('modal-open');
    document.body.style.overflow = '';
    document.documentElement.style.overflow = '';
    window.modalStack = [];
  }
};

/**
 * Closes modal with proper media cleanup, picture-in-picture exit, and scroll restoration
 */
window.closeModalWithHistory = function (modalEl) {
  if (!modalEl) return;

  // Delegate to specific module closers with recursion guards
  if (modalEl.id === 'movieModal' && window.closeMovieModal && !modalEl._isClosing) {
    modalEl._isClosing = true;
    try {
      window.closeMovieModal(true);
    } finally {
      modalEl._isClosing = false;
    }
    return;
  }

  if (modalEl.id === 'playerModal' && window.closePlayer && !modalEl._isClosing) {
    modalEl._isClosing = true;
    try {
      window.closePlayer();
    } finally {
      modalEl._isClosing = false;
    }
    return;
  }

  if (modalEl.id === 'galleryMiniModal' && window.closeGalleryMiniPlayer) {
    window.closeGalleryMiniPlayer();
    return;
  }

  modalEl.classList.add('hidden');

  if (document.pictureInPictureElement) {
    document.exitPictureInPicture().catch(() => { });
  }

  // Terminate any audio/video or iframe playing inside the closing modal
  modalEl.querySelectorAll('video, audio').forEach(v => {
    try {
      v.pause();
      v.currentTime = 0;
      v.removeAttribute('src');
      v.src = '';
      v.load();
    } catch (_) { }
  });

  modalEl.querySelectorAll('iframe').forEach(ifr => {
    try {
      ifr.src = 'about:blank';
      ifr.removeAttribute('src');
    } catch (_) { }
  });

  if (window.modalStack) {
    window.modalStack = window.modalStack.filter(m => m !== modalEl);
  }

  const remainingOpen = Array.from(document.querySelectorAll('.modal-overlay:not(.hidden)')).filter(m => m !== modalEl);
  if (remainingOpen.length === 0) {
    document.body.classList.remove('modal-open');
    document.body.style.overflow = '';
    document.documentElement.style.overflow = '';
    window.modalStack = [];
  }
};

let lastBackActionTime = 0;

/**
 * Master Android Hardware & Gesture Back Button Handler
 * Evaluates backstack hierarchically:
 *  1. Live Search Suggestions (Movies & Adult)
 *  2. In-App Browser (Native Browser Overlay or Browser Tab)
 *  3. Gallery Mini Player Modal
 *  4. Adult 18+ Fullscreen Watch Mode
 *  5. Main Stream / Video Player Modal
 *  6. Movie Details Modal
 *  7. Any other active modal overlays
 *  8. Active Search Query in Movies Tab
 *  9. Return to default 'movies' tab if currently on secondary tab
 * 10. Root 'movies' tab -> returns false for native 2-second double-tap exit toast
 *
 * Includes a 350ms cooldown debounce to prevent Capacitor & Android Native dual-triggering.
 * @returns {boolean} True if back action was handled internally, false if app can exit
 */
window.handleAndroidBackButton = function () {
  const now = Date.now();
  if (now - lastBackActionTime < 350) {
    // Duplicate back event triggered within 350ms (Capacitor bridge + Native dispatcher) -> debounce consume
    return true;
  }

  // 1. Live Search Suggestions Dropdown (Movies & Adult)
  const movieSugg = document.getElementById('movieLiveSuggestions');
  if (movieSugg && !movieSugg.classList.contains('hidden')) {
    movieSugg.classList.add('hidden');
    lastBackActionTime = now;
    return true;
  }
  const adultSugg = document.getElementById('adultLiveSuggestions');
  if (adultSugg && !adultSugg.classList.contains('hidden')) {
    adultSugg.classList.add('hidden');
    lastBackActionTime = now;
    return true;
  }

  // 2. In-App Browser active / container visible
  const browserView = document.getElementById('view-browser');
  if (window._inAppBrowserReturnContext || (browserView && browserView.classList.contains('active'))) {
    const nativeBrowser = window.Capacitor?.Plugins?.NativeBrowser;
    if (nativeBrowser?.goBack) {
      nativeBrowser.goBack().then(res => {
        if (!res?.handled && window.closeInAppBrowser) {
          window.closeInAppBrowser();
        }
      }).catch(() => {
        if (window.closeInAppBrowser) window.closeInAppBrowser();
      });
      lastBackActionTime = now;
      return true;
    }

    // Web Preview / Fallback Mode
    const startPage = document.getElementById('webBrowserStartPage');
    const frameContainer = document.getElementById('webBrowserFrameContainer');
    const iframe = document.getElementById('browserProxyIframe');
    if (frameContainer && !frameContainer.classList.contains('hidden')) {
      frameContainer.classList.add('hidden');
      if (startPage) startPage.classList.remove('hidden');
      if (iframe) iframe.src = 'about:blank';
      lastBackActionTime = now;
      return true;
    }

    if (window.closeInAppBrowser) {
      window.closeInAppBrowser();
      lastBackActionTime = now;
      return true;
    }
  }

  // 3. Gallery Mini Popup Player
  const galleryMiniModal = document.getElementById('galleryMiniModal');
  if (galleryMiniModal && !galleryMiniModal.classList.contains('hidden')) {
    if (window.closeGalleryMiniPlayer) {
      window.closeGalleryMiniPlayer();
    } else {
      galleryMiniModal.classList.add('hidden');
    }
    lastBackActionTime = now;
    return true;
  }

  // 4. Adult 18+ Fullscreen Watch Mode
  const adultWatchView = document.getElementById('adultWatchView');
  if (adultWatchView && !adultWatchView.classList.contains('hidden')) {
    if (window.closeAdultWatchMode) window.closeAdultWatchMode();
    lastBackActionTime = now;
    return true;
  }

  // 5. Main Stream / Video Player Modal
  const playerModal = document.getElementById('playerModal');
  if (playerModal && !playerModal.classList.contains('hidden')) {
    if (window.closePlayer) {
      window.closePlayer();
    } else {
      playerModal.classList.add('hidden');
      document.body.classList.remove('modal-open');
    }
    lastBackActionTime = now;
    return true;
  }

  // 6. Movie Details Modal
  const movieModal = document.getElementById('movieModal');
  if (movieModal && !movieModal.classList.contains('hidden')) {
    if (window.closeMovieModal) {
      window.closeMovieModal();
    } else {
      movieModal.classList.add('hidden');
      document.body.classList.remove('modal-open');
    }
    lastBackActionTime = now;
    return true;
  }

  // 7. Any other open modal overlay
  const openModals = Array.from(document.querySelectorAll('.modal-overlay:not(.hidden)'));
  if (openModals.length > 0) {
    const top = openModals[openModals.length - 1];
    if (top.id === 'playerModal' && window.closePlayer) {
      window.closePlayer();
    } else if (top.id === 'movieModal' && window.closeMovieModal) {
      window.closeMovieModal();
    } else if (window.closeModalWithHistory) {
      window.closeModalWithHistory(top);
    } else {
      top.classList.add('hidden');
      document.body.classList.remove('modal-open');
    }
    lastBackActionTime = now;
    return true;
  }

  // 8. Clear Active Movie Search Query if user is searching
  const movieSearchInput = document.getElementById('movieSearchInput');
  if (movieSearchInput && movieSearchInput.value && movieSearchInput.value.trim().length > 0) {
    movieSearchInput.value = '';
    const btnClearMovieSearch = document.getElementById('btnClearMovieSearch');
    if (btnClearMovieSearch) btnClearMovieSearch.classList.add('hidden');
    movieSearchInput.blur();
    if (window.searchMovies) {
      window.searchMovies('2026');
    }
    if (window.showToast) {
      window.showToast('🔍 Search cleared', 'info');
    }
    lastBackActionTime = now;
    return true;
  }

  // 9. Reset non-trending / portal filter in Movies tab if active
  if (window.state?.currentTab === 'movies') {
    const activeCategoryPill = document.querySelector('.category-pills-2026 .tag-pill.active');
    const activePortalBtn = document.querySelector('.portal-btn.active');
    if (activePortalBtn || (activeCategoryPill && activeCategoryPill.dataset.q !== 'Trending')) {
      if (activePortalBtn) activePortalBtn.classList.remove('active');
      document.querySelectorAll('.category-pills-2026 .tag-pill').forEach(p => p.classList.remove('active'));
      const trendingPill = document.querySelector('.category-pills-2026 .tag-pill[data-q="Trending"]');
      if (trendingPill) trendingPill.classList.add('active');
      if (window.searchMovies) {
        window.searchMovies('2026');
      }
      lastBackActionTime = now;
      return true;
    }
  }

  // 10. Return to default 'movies' tab if currently on secondary tab
  if (window.state && window.state.currentTab && window.state.currentTab !== 'movies') {
    if (window.switchTab) {
      window.switchTab('movies');
      lastBackActionTime = now;
      return true;
    }
  }

  // 11. Root state: Double-tap back within 2 seconds to exit app
  const lastExitTime = window._lastRootBackPressTime || 0;
  if (now - lastExitTime < 2000) {
    if (window.Capacitor?.Plugins?.App?.exitApp) {
      window.Capacitor.Plugins.App.exitApp();
    }
    return false;
  } else {
    window._lastRootBackPressTime = now;
    lastBackActionTime = now;
    if (window.showToast) {
      window.showToast('Press back again to exit', 'info');
    }
    return true;
  }
};

// Capacitor Hardware Back Button Hook
if (window.Capacitor?.Plugins?.App?.addListener) {
  try {
    window.Capacitor.Plugins.App.addListener('backButton', () => {
      const handled = window.handleAndroidBackButton();
      if (!handled && window.Capacitor?.Plugins?.App?.exitApp) {
        window.Capacitor.Plugins.App.exitApp();
      }
    });
  } catch (_) { }
}

// ============================================================================
// SECTION 8: AMOLED Pure Black & Cyber Dark Theme Controller
// ============================================================================

(function initAmoledTheme() {
  const btnAmoled = document.getElementById('btnAmoledToggle');
  const isAmoled = localStorage.getItem('cdl_amoled_mode') === 'true';

  if (isAmoled) {
    document.body.classList.add('amoled-theme');
    if (btnAmoled) btnAmoled.innerHTML = '<i class="fa-solid fa-sun" style="color:#f59e0b;"></i>';
  }

  if (btnAmoled) {
    btnAmoled.addEventListener('click', () => {
      const active = document.body.classList.toggle('amoled-theme');
      localStorage.setItem('cdl_amoled_mode', active ? 'true' : 'false');
      btnAmoled.innerHTML = active
        ? '<i class="fa-solid fa-sun" style="color:#f59e0b;"></i>'
        : '<i class="fa-solid fa-moon"></i>';
      window.showToast(active ? '🌓 AMOLED Pure Black Theme Active' : '✨ Cyber Dark Theme Active', 'info');
    });
  }
})();

// ============================================================================
// SECTION 9: Pull-to-Refresh Gesture Physics Engine & Tab Refresher
// ============================================================================

/**
 * Triggers a live fresh data fetch for the currently active tab
 * @param {boolean} showToastNotice
 */
window.refreshActiveTab = async function (showToastNotice = true) {
  const current = window.state.currentTab || localStorage.getItem('cloud_active_tab') || 'movies';

  if (showToastNotice) {
    try { if (navigator.vibrate) navigator.vibrate(25); } catch (_) { }
  }

  try {
    switch (current) {
      case 'movies': {
        const input = document.getElementById('movieSearchInput');
        const activePill = document.querySelector('#view-movies .category-pills-2026 .tag-pill.active, #view-movies .quick-tags .tag-pill.active');
        const pillQuery = activePill ? activePill.dataset.q : '';
        const currentQuery = input?.value?.trim() || pillQuery || '2026';

        if (window.searchMovies) {
          await window.searchMovies(currentQuery, true, true);
        }
        break;
      }
      case 'adult': {
        const watchView = document.getElementById('adultWatchView');
        if (watchView && !watchView.classList.contains('hidden')) {
          if (showToastNotice) window.showToast('Playback active', 'info');
          break;
        }
        const input = document.getElementById('adultSearchInput');
        const activeTag = document.querySelector('.adult-tag-pill.active');
        const tagQuery = activeTag ? activeTag.dataset.tag : '';
        const q = input?.value?.trim() || tagQuery || 'popular';
        if (window.searchAdult) {
          await window.searchAdult(q, window.currentAdultSource || 'all');
        }
        if (showToastNotice) window.showToast('🔞 18+ feed updated', 'info');
        break;
      }
      case 'gallery': {
        if (window.loadOfflineGallery) {
          await window.loadOfflineGallery();
        }
        if (showToastNotice) window.showToast('📱 Video Gallery storage refreshed', 'info');
        break;
      }
      case 'downloads': {
        if (window.loadDownloads) {
          await window.loadDownloads();
        } else if (window.renderDownloadsList) {
          window.renderDownloadsList(true);
        }
        if (showToastNotice) window.showToast('⚡ Downloads list refreshed', 'info');
        break;
      }
      case 'drive': {
        if (window.loadDriveFiles) {
          await window.loadDriveFiles();
        }
        if (showToastNotice) window.showToast('☁️ Google Drive files refreshed', 'info');
        break;
      }
      case 'tasks': {
        if (window.loadActiveTransfers) {
          await window.loadActiveTransfers();
        }
        if (showToastNotice) window.showToast('🔄 Transfer tasks refreshed', 'info');
        break;
      }
      default:
        break;
    }
  } catch (err) {
    console.warn('[RefreshActiveTab] Error:', err);
  }
};

/**
 * Checks whether any modal, player, or watch view is currently open
 */
function isModalOrPlayerActive() {
  if (document.body.classList.contains('modal-open')) return true;
  if (document.querySelector('.modal-overlay:not(.hidden)')) return true;
  const pModal = document.getElementById('playerModal');
  if (pModal && !pModal.classList.contains('hidden')) return true;
  const mModal = document.getElementById('movieModal');
  if (mModal && !mModal.classList.contains('hidden')) return true;
  const aWatch = document.getElementById('adultWatchView');
  if (aWatch && !aWatch.classList.contains('hidden')) return true;
  return false;
}
window.isModalOrPlayerActive = isModalOrPlayerActive;

(function initPullToRefresh() {
  const indicator = document.getElementById('ptrIndicator');
  const ptrText = document.getElementById('ptrText');
  let touchStartY = 0;
  let touchStartX = 0;
  let touchMoveY = 0;
  let isPulling = false;
  let isRefreshing = false;
  let refreshSafetyTimer = null;
  const PULL_THRESHOLD = 60;

  function isPageAtTop() {
    const winY = window.pageYOffset || window.scrollY || 0;
    const docY = document.documentElement ? document.documentElement.scrollTop : 0;
    const bodyY = document.body ? document.body.scrollTop : 0;
    const activeTab = document.querySelector('.tab-view.active');
    const tabY = activeTab ? activeTab.scrollTop : 0;
    return winY <= 2 && docY <= 2 && bodyY <= 2 && tabY <= 2;
  }

  function cleanupIndicator() {
    if (refreshSafetyTimer) {
      clearTimeout(refreshSafetyTimer);
      refreshSafetyTimer = null;
    }
    if (indicator) {
      indicator.style.transition = 'transform 0.3s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.3s ease';
      indicator.classList.remove('ptr-refreshing', 'ptr-visible');
      indicator.style.transform = 'translateX(-50%) translateY(-120px)';
    }
    isRefreshing = false;
    isPulling = false;
    touchStartY = 0;
    touchStartX = 0;
    touchMoveY = 0;
    if (ptrText) ptrText.textContent = 'Pull to refresh';
  }

  window.addEventListener('touchstart', (e) => {
    if (isRefreshing || e.touches.length !== 1) return;
    if (isModalOrPlayerActive()) return;

    // Disallow pull-to-refresh when touching interactive UI components
    const target = e.target;
    if (target.closest('input, textarea, select, button, .modal, .slider, .player-container, #playerModal, #movieModal, #adultWatchView')) {
      return;
    }

    if (!isPageAtTop()) return;

    touchStartY = e.touches[0].clientY;
    touchStartX = e.touches[0].clientX;
    touchMoveY = touchStartY;
    isPulling = false;
  }, { passive: true });

  window.addEventListener('touchmove', (e) => {
    if (isRefreshing || e.touches.length !== 1 || !touchStartY) return;
    if (isModalOrPlayerActive() || !isPageAtTop()) {
      if (isPulling) cleanupIndicator();
      return;
    }

    touchMoveY = e.touches[0].clientY;
    const touchX = e.touches[0].clientX;
    const diffY = touchMoveY - touchStartY;
    const diffX = Math.abs(touchX - touchStartX);

    // Cancel if moving upward or swiping sideways
    if (diffY <= 0 || diffX > diffY) {
      if (isPulling) cleanupIndicator();
      return;
    }

    // Ignore minor accidental moves under 30px
    if (diffY < 30) {
      if (indicator && !isRefreshing) {
        indicator.classList.remove('ptr-visible');
      }
      return;
    }

    isPulling = true;

    // Damped elastic physics curve
    const pullDistance = Math.min(Math.pow(diffY - 30, 0.82) * 1.5, 95);

    if (indicator) {
      indicator.style.transition = 'none';
      indicator.classList.add('ptr-visible');
      const progress = Math.min(pullDistance / PULL_THRESHOLD, 1);
      const translateY = -100 + (progress * 160);
      indicator.style.transform = `translateX(-50%) translateY(${translateY}px)`;

      const icon = indicator.querySelector('.ptr-icon');
      if (icon) icon.style.transform = `rotate(${progress * 360}deg)`;

      if (ptrText) {
        ptrText.textContent = pullDistance >= PULL_THRESHOLD ? 'Release to refresh' : 'Pull to refresh';
      }
    }
  }, { passive: true });

  window.addEventListener('touchend', async () => {
    if (!isPulling || isRefreshing) {
      isPulling = false;
      touchStartY = 0;
      return;
    }
    isPulling = false;

    const diffY = touchMoveY - touchStartY;
    const pullDistance = diffY > 30 ? Math.min(Math.pow(diffY - 30, 0.82) * 1.5, 95) : 0;
    touchStartY = 0;

    if (pullDistance >= PULL_THRESHOLD && indicator) {
      isRefreshing = true;
      indicator.style.transition = 'transform 0.25s cubic-bezier(0.16, 1, 0.3, 1)';
      indicator.classList.add('ptr-refreshing');
      indicator.style.transform = 'translateX(-50%) translateY(20px)';
      if (ptrText) ptrText.textContent = 'Refreshing content...';

      try { if (navigator.vibrate) navigator.vibrate([20, 10, 20]); } catch (_) { }

      // Safe fallback timer (6s max so indicator never hangs indefinitely)
      refreshSafetyTimer = setTimeout(cleanupIndicator, 6000);

      try {
        window.dispatchEvent(new CustomEvent('cld:refresh-requested'));
        await Promise.race([
          Promise.all([
            window.refreshActiveTab(false),
            typeof window.syncLicenseWithServer === 'function' ? window.syncLicenseWithServer(true) : Promise.resolve(),
            typeof window.checkOtaUpdate === 'function' ? window.checkOtaUpdate(false) : Promise.resolve()
          ]),
          new Promise(r => setTimeout(r, 5500))
        ]);
      } catch (_) { }

      setTimeout(cleanupIndicator, 350);
    } else {
      cleanupIndicator();
    }
  });

  window.addEventListener('touchcancel', () => {
    cleanupIndicator();
  });
})();

// ============================================================================
// SECTION 10: Smart Clipboard Auto-Paste Detector & Tab Auto-Healing Lifecycle
// ============================================================================

(function initClipboardSmartDetector() {
  const banner = document.getElementById('clipboardSmartBanner');
  let lastCheckedClip = '';

  async function checkClipboard() {
    if (!navigator.clipboard || !navigator.clipboard.readText || !banner) return;

    try {
      const text = (await navigator.clipboard.readText() || '').trim();
      if (!text || text === lastCheckedClip || !text.startsWith('http')) return;

      const isSupported = /(youtube\.com|youtu\.be|tiktok\.com|instagram\.com|facebook\.com|fb\.watch|twitter\.com|x\.com|reddit\.com|pinterest\.com|pornhub\.com|xvideos\.com|xnxx\.com|eporner\.com|redtube\.com|pixeldrain\.com|mega\.nz|sinhalasub|baiscope|sub\.lk|workers\.dev)/i.test(text);

      if (isSupported) {
        lastCheckedClip = text;
        renderClipboardBanner(text);
      }
    } catch (_) { }
  }

  function renderClipboardBanner(url) {
    if (!banner) return;
    let targetTab = 'media';
    let iconClass = 'fa-solid fa-link';
    let label = 'Link Detected';

    if (/pornhub|xvideos|xnxx|eporner|redtube/i.test(url)) {
      targetTab = 'adult';
      iconClass = 'fa-solid fa-fire';
      label = '18+ Video Detected';
    } else if (/sinhalasub|baiscope|sub\.lk|piratelk/i.test(url)) {
      targetTab = 'movies';
      iconClass = 'fa-solid fa-film';
      label = 'Movie Link Detected';
    } else if (/youtube|tiktok|instagram|facebook|twitter|reddit/i.test(url)) {
      targetTab = 'media';
      iconClass = 'fa-solid fa-play';
      label = 'Social Video Detected';
    }

    banner.innerHTML = `
      <div class="clipboard-info">
        <div class="clipboard-icon-badge"><i class="${iconClass}"></i></div>
        <div class="clipboard-text-col">
          <div class="clipboard-title">${label}</div>
          <div class="clipboard-url-snippet">${url}</div>
        </div>
      </div>
      <div class="clipboard-actions">
        <button class="btn-clipboard-paste" id="btnClipboardPaste">
          <i class="fa-solid fa-paste"></i> <span>Paste & Open</span>
        </button>
        <button class="btn-clipboard-dismiss" id="btnClipboardDismiss">&times;</button>
      </div>
    `;

    banner.classList.remove('hidden');

    document.getElementById('btnClipboardDismiss')?.addEventListener('click', () => {
      banner.classList.add('hidden');
    });

    document.getElementById('btnClipboardPaste')?.addEventListener('click', () => {
      banner.classList.add('hidden');
      window.switchTab(targetTab);

      if (targetTab === 'media') {
        const input = document.getElementById('mediaUrlInput');
        if (input) {
          input.value = url;
          document.getElementById('btnExtractMedia')?.click();
        }
      } else if (targetTab === 'adult') {
        if (window.openAdultWatchView) {
          window.openAdultWatchView({ link: url, title: 'Video Stream', source: 'Stream' });
        }
      } else if (targetTab === 'movies') {
        const input = document.getElementById('movieSearchInput');
        if (input) {
          input.value = url;
          document.getElementById('btnSearchMovie')?.click();
        }
      }
    });

    setTimeout(() => {
      if (banner) banner.classList.add('hidden');
    }, 9000);
  }

  /**
   * Auto-heal feeds when returning to the app if DOM grids are empty
   */
  let lastAutoHealTime = 0;
  let autoHealRetries = 0;
  let isAutoHealing = false;
  const AUTO_HEAL_COOLDOWN_MS = 45000; // 45 seconds cooldown
  const MAX_AUTO_HEAL_RETRIES = 3;

  function autoHealActiveTab() {
    const now = Date.now();
    if (isAutoHealing || (now - lastAutoHealTime < AUTO_HEAL_COOLDOWN_MS)) {
      return;
    }
    if (autoHealRetries >= MAX_AUTO_HEAL_RETRIES) {
      return;
    }
    isAutoHealing = true;
    lastAutoHealTime = now;
    autoHealRetries++;

    try {
      checkClipboard();
      const tab = window.state?.currentTab || 'movies';
      if (tab === 'movies') {
        const grid = document.getElementById('movieGrid');
        if (grid && (!grid.children || grid.children.length === 0) && window.searchMovies) {
          window.searchMovies('2026', false);
        }
      } else if (tab === 'adult') {
        const grid = document.getElementById('adultGrid');
        if (grid && (!grid.children || grid.children.length === 0) && window.searchAdult) {
          window.searchAdult('for-you', window.currentAdultSource || 'all');
        }
      } else if (tab === 'downloads') {
        if (window.refreshDownloadsProgress) window.refreshDownloadsProgress();
      } else if (tab === 'gallery') {
        if (window.loadOfflineGallery) window.loadOfflineGallery();
      }
    } catch (_) {
    } finally {
      setTimeout(() => { isAutoHealing = false; }, 2000);
    }
  }

  setTimeout(autoHealActiveTab, 1800);
  window.addEventListener('focus', autoHealActiveTab);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') autoHealActiveTab();
  });
})();

// ============================================================================
// SECTION 11: Real-Time Socket.IO Synchronization Hub & Health Monitor
// ============================================================================

/**
 * Truthfully checks Native Google Drive and on-device engine status and updates UI badges
 */
window.checkStatus = async function () {
  const driveStatusBadge = document.getElementById('driveStatusBadge');
  try {
    if (window.Capacitor && window.Capacitor.isNativePlatform() && window.Capacitor.Plugins?.NativeGdrive?.getActiveAccount) {
      const acc = await window.Capacitor.Plugins.NativeGdrive.getActiveAccount();
      if (acc && acc.connected && acc.email) {
        window.state.driveConnected = true;
        window.state.driveUser = acc;
        if (driveStatusBadge) {
          driveStatusBadge.className = 'status-badge connected';
          const email = acc.email || 'Account Linked';
          driveStatusBadge.innerHTML = `<span class="status-dot"></span><span>Drive Ready (${email.split('@')[0]})</span>`;
        }
        return;
      }
    }
  } catch (_) { }

  // Standalone on-device engine state
  window.state.driveConnected = false;
  if (driveStatusBadge) {
    driveStatusBadge.className = 'status-badge direct';
    driveStatusBadge.innerHTML = `<span class="status-dot"></span><span>⚡ Direct Engine</span>`;
  }
};

setTimeout(window.checkStatus, 150);

// SECTION 11: Standalone On-Device Architecture (Decoupled from Node / Sockets)
// App operates 100% on-device via Kotlin & SQLite; no remote Socket.IO required.
window.initSocketSync = function () {
  return null;
};

// ============================================================================
// SECTION 12: Picture-in-Picture (PiP) Controller & DOM Bootstrap Lifecycle
// ============================================================================

(function initPictureInPicture() {
  const btnAdultPiP = document.getElementById('btnAdultWatchPiP');
  const videoEl = document.getElementById('adultHtmlVideoPlayer');

  if (btnAdultPiP && videoEl) {
    btnAdultPiP.addEventListener('click', async () => {
      try {
        if (document.pictureInPictureElement) {
          await document.exitPictureInPicture();
          window.showToast('Exited Picture-in-Picture', 'info');
        } else if (document.pictureInPictureEnabled && !videoEl.classList.contains('hidden')) {
          await videoEl.requestPictureInPicture();
          window.showToast('📺 Picture-in-Picture active!', 'success');
        } else {
          window.showToast('PiP is available when HTML video stream is active.', 'info');
        }
      } catch (err) {
        window.showToast(`PiP not supported for this stream: ${err.message}`, 'error');
      }
    });
  }
})();

// Master DOM Lifecycle Bootstrap
document.addEventListener('DOMContentLoaded', () => {
  // 1. Unregister obsolete service workers
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.getRegistrations().then(regs => {
      for (let reg of regs) {
        reg.unregister().catch(() => { });
      }
    }).catch(() => { });
  }

  // 2. Popstate (Back/Forward browser buttons)
  window.addEventListener('popstate', (e) => {
    if (window.handleAndroidBackButton()) {
      return;
    }

    const hash = (window.location.hash || '').replace('#', '').trim();
    const targetTab = (e.state && e.state.tab) || hash || 'movies';
    if (targetTab && targetTab !== window.state.currentTab) {
      window.switchTab(targetTab, false);
    }
  });

  // 3. Modal backdrop outside tap dismissal
  document.querySelectorAll('.modal-overlay').forEach(modal => {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        window.closeModalWithHistory(modal);
      }
    });
  });

  // 4. Physical Keyboard Escape key handler
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && window.modalStack.length > 0) {
      const topModal = window.modalStack.pop();
      if (topModal) {
        window.closeModalWithHistory(topModal);
      }
    }
  });

  // 5. Bottom Navigation Bar Tab Clicks (Instant Touch Feedback)
  document.querySelectorAll('.nav-item').forEach(item => {
    let touched = false;
    const triggerTab = (e) => {
      if (e.type === 'touchstart') {
        touched = true;
      } else if (e.type === 'click' && touched) {
        touched = false;
        return;
      }
      const targetTab = item.dataset.tab;
      if (targetTab === window.state.currentTab) {
        if (window.isModalOrPlayerActive && window.isModalOrPlayerActive()) {
          return;
        }
        const isAlreadyAtTop = (window.pageYOffset || window.scrollY || 0) <= 15;
        window.scrollTo({ top: 0, behavior: 'smooth' });
        if (isAlreadyAtTop) {
          const icon = item.querySelector('i');
          if (icon) {
            icon.classList.add('fa-spin');
            setTimeout(() => icon.classList.remove('fa-spin'), 800);
          }
          if (window.refreshActiveTab) window.refreshActiveTab(true);
        }
      } else {
        window.switchTab(targetTab);
      }
    };

    item.addEventListener('touchstart', triggerTab, { passive: true });
    item.addEventListener('click', triggerTab);
  });

  // 6. Initialize Web Browser UI & Speed Dials
  if (typeof window.initWebBrowserUI === 'function') {
    window.initWebBrowserUI();
  }
});

/**
 * Cyber Web Browser UI Controller & Fallback Bridge
 */
window.initWebBrowserUI = function () {
  const urlInput = document.getElementById('webBrowserUrlInput');
  const btnGo = document.getElementById('btnWebBrowserGo');
  const btnClear = document.getElementById('btnClearWebBrowserUrl');
  const btnHome = document.getElementById('btnWebBrowserHome');
  const btnReload = document.getElementById('btnWebBrowserReload');
  const engineSelect = document.getElementById('webBrowserEngineSelect');
  const startPage = document.getElementById('webBrowserStartPage');
  const frameContainer = document.getElementById('webBrowserFrameContainer');
  const iframe = document.getElementById('browserProxyIframe');

  if (!urlInput || !btnGo) return;

  function loadUrl(inputUrl) {
    let url = (inputUrl || '').trim();
    if (!url) return;

    if (!url.startsWith('http://') && !url.startsWith('https://')) {
      if (url.includes('.') && !url.includes(' ')) {
        url = 'https://' + url;
      } else {
        const engine = engineSelect ? engineSelect.value : 'google';
        const engines = {
          google: 'https://www.google.com/search?q=',
          duckduckgo: 'https://duckduckgo.com/?q=',
          bing: 'https://www.bing.com/search?q='
        };
        url = (engines[engine] || engines.google) + encodeURIComponent(url);
      }
    }

    urlInput.value = url;
    if (btnClear) btnClear.classList.remove('hidden');

    const nativeBrowser = window.Capacitor?.Plugins?.NativeBrowser;
    if (nativeBrowser?.showBrowser) {
      nativeBrowser.showBrowser({ url: url }).catch(() => { });
    } else {
      // Fallback in web view
      if (startPage) startPage.classList.add('hidden');
      if (frameContainer) frameContainer.classList.remove('hidden');
      if (iframe) {
        iframe.src = url;
      }
    }
  }

  window.loadWebBrowserUrl = loadUrl;

  btnGo.addEventListener('click', () => loadUrl(urlInput.value));
  urlInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      loadUrl(urlInput.value);
    }
  });

  urlInput.addEventListener('input', () => {
    if (btnClear) {
      btnClear.classList.toggle('hidden', !urlInput.value);
    }
  });

  if (btnClear) {
    btnClear.addEventListener('click', () => {
      urlInput.value = '';
      btnClear.classList.add('hidden');
      urlInput.focus();
    });
  }

  if (btnHome) {
    btnHome.addEventListener('click', () => {
      urlInput.value = '';
      if (btnClear) btnClear.classList.add('hidden');
      const nativeBrowser = window.Capacitor?.Plugins?.NativeBrowser;
      if (nativeBrowser?.showBrowser) {
        nativeBrowser.showBrowser({ url: 'about:home' }).catch(() => { });
      } else {
        if (frameContainer) frameContainer.classList.add('hidden');
        if (startPage) startPage.classList.remove('hidden');
        if (iframe) iframe.src = 'about:blank';
      }
    });
  }

  if (btnReload) {
    btnReload.addEventListener('click', () => {
      if (urlInput.value) {
        loadUrl(urlInput.value);
      }
    });
  }

  // Speed Dials
  document.querySelectorAll('.web-speed-dial').forEach(dial => {
    dial.addEventListener('click', () => {
      const url = dial.dataset.url;
      if (url) loadUrl(url);
    });
  });

  // Google Drive Status Card
  const btnDriveAct = document.getElementById('btnWebBrowserDriveAction');
  if (btnDriveAct) {
    btnDriveAct.addEventListener('click', () => {
      if (window.switchTab) window.switchTab('drive');
    });
  }
};

// ==============================================================================
// 9. NETWORK CONNECTIVITY & OFFLINE RESILIENCE MANAGER
// ==============================================================================
(function () {
  window.isAppOffline = !navigator.onLine;

  window.checkNetworkOnline = function (showToastMessage = true) {
    if (window.isAppOffline || !navigator.onLine) {
      if (showToastMessage && typeof window.showToast === 'function') {
        window.showToast('📡 You are offline. Saved downloads, local media, and library remain available.', 'warning');
      }
      return false;
    }
    return true;
  };

  window.addEventListener('offline', () => {
    window.isAppOffline = true;
    console.warn('[Network] Device transitioned to OFFLINE mode.');
    if (typeof window.showToast === 'function') {
      window.showToast('📡 Offline Mode Active. Local downloads & library are accessible.', 'info');
    }
    document.body.classList.add('app-offline');
  });

  window.addEventListener('online', () => {
    window.isAppOffline = false;
    console.info('[Network] Device transitioned to ONLINE mode.');
    if (typeof window.showToast === 'function') {
      window.showToast('🌐 Internet Connection Restored.', 'success');
    }
    document.body.classList.remove('app-offline');
  });
})();
