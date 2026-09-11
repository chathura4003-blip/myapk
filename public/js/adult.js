'use strict';

/**
 * ============================================================================
 * CLOUD DRIVE LEECH - 18+ ADULT HUB & CINEMA THEATER (adult.js)
 * ============================================================================
 * Architecture Overview:
 *   SECTION 1:  DOM Elements, Player Controls & Screen Modes
 *   SECTION 2:  Playlist Navigation & Up-Next Auto-Play Engine (Deduplicated)
 *   SECTION 3:  Search Bar, Category Glow Pills & Source Selectors
 *   SECTION 4:  Neural Recommendation Engine & On-Device Taste Profiling
 *   SECTION 5:  Multi-Factor Smart Ranking & 80/20 Bandit Exploration
 *   SECTION 6:  Live Feed Grid Renderer & Infinite Batch Pagination
 *   SECTION 7:  Adult Search API & Zero-Latency Memory Cache
 *   SECTION 8:  Direct HLS / MP4 Stream Resolver & LocalMediaProxy Router
 *   SECTION 9:  Cinema Watch Mode Orchestrator & Quality Controller
 *   SECTION 10: Up-Next & Contextual Smart Recommendations Feed
 *   SECTION 11: Interactive Resolution Quality Picker Modal
 *   SECTION 12: Double-Tap Seek Gestures, Auto-Rotate & Lifecycle Bootstrap
 * ============================================================================
 */

// ============================================================================
// SECTION 1: DOM Elements, Player Controls & Screen Modes
// ============================================================================

const adultBrowseView = document.getElementById('adultBrowseView');
const adultWatchView = document.getElementById('adultWatchView');
const adultAgeGate = document.getElementById('adultAgeGate');
const btnConfirmAgeGate = document.getElementById('btnConfirmAgeGate');
const btnExitAgeGate = document.getElementById('btnExitAgeGate');
const adultContinueWatchingSection = document.getElementById('adultContinueWatchingSection');
const adultContinueWatchingRail = document.getElementById('adultContinueWatchingRail');
const adultBecauseYouWatchedSection = document.getElementById('adultBecauseYouWatchedSection');
const adultBecauseYouWatchedRail = document.getElementById('adultBecauseYouWatchedRail');
const adultBecauseYouWatchedTitle = document.getElementById('adultBecauseYouWatchedTitle');
const btnBackToAdultBrowse = document.getElementById('btnBackToAdultBrowse');
const adultWatchSourceBadge = document.getElementById('adultWatchSourceBadge');
const adultIframePlayer = document.getElementById('adultIframePlayer');
const adultHtmlVideoPlayer = document.getElementById('adultHtmlVideoPlayer');
const adultWatchTitle = document.getElementById('adultWatchTitle');
const adultWatchDuration = document.getElementById('adultWatchDuration');
const adultWatchRating = document.getElementById('adultWatchRating');
const adultQualityRow = document.getElementById('adultQualityRow');
const adultQualityPills = document.getElementById('adultQualityPills');
const btnAdultWatchDrive = document.getElementById('btnAdultWatchDrive');
const btnAdultWatchDownload = document.getElementById('btnAdultWatchDownload');
const btnAdultWatchCopy = document.getElementById('btnAdultWatchCopy');
const btnAdultWatchSource = document.getElementById('btnAdultWatchSource');

const adultSearchInput = document.getElementById('adultSearchInput');
const btnSearchAdult = document.getElementById('btnSearchAdult');
const btnClearAdultSearch = document.getElementById('btnClearAdultSearch');
const adultSourceSelect = document.getElementById('adultSourceSelect');
const adultLoading = document.getElementById('adultLoading');
const adultGrid = document.getElementById('adultGrid');
const adultSectionTitle = document.getElementById('adultSectionTitle');
const adultResultsCount = document.getElementById('adultResultsCount');

window.currentAdultSource = 'all';
window.currentAdultPlaylist = [];
window.currentAdultIndex = -1;
window.rawAdultResults = [];
window.currentAdultAlgo = 'for-you';
window.currentAdultRelatedList = [];
window.currentAdultPage = 1;
window.isAdultLoadingMore = false;
window.hasMoreAdultStreams = true;
window.seenAdultUrls = new Set();
window.lastAdultSearchQuery = 'trending';
let currentAdultSearchRequestId = 0;

function parseAdultViewCount(viewStr = '') {
  if (!viewStr) return 0;
  const clean = viewStr.toString().toLowerCase().trim();
  const numMatch = clean.match(/([\d.]+)/);
  if (!numMatch) return 0;
  const num = parseFloat(numMatch[1]);
  if (clean.includes('m')) return num * 1_000_000;
  if (clean.includes('k')) return num * 1_000;
  return num;
}

let adultFitMode = localStorage.getItem('adult_fit_mode') || 'fit-width';
let adultUpNextTimer = null;
let adultLastTapTime = 0;
let adultSearchDebounceTimer = null;
let currentAdultHls = null;
let currentAdultWatchSession = null;

const ADULT_FIT_MODES = [
  { id: 'fit-width', label: 'Fit Width', buttonLabel: 'Fit Width' },
  { id: 'fit-cover', label: 'Fill Screen', buttonLabel: 'Fill Screen' },
  { id: 'fit-contain', label: '16:9 Standard', buttonLabel: '16:9 Standard' }
];

window.setAdultFitMode = function (mode) {
  adultFitMode = mode;
  localStorage.setItem('adult_fit_mode', mode);
  const container = document.getElementById('adultTheaterContainer');
  if (container) {
    container.classList.remove('fit-width', 'fit-cover', 'fit-contain');
    container.classList.add(mode);
  }
  const modeObj = ADULT_FIT_MODES.find(m => m.id === mode) || ADULT_FIT_MODES[0];
  const fitLabel = document.getElementById('adultFitModeLabel');
  const fitBtnLabel = document.getElementById('adultFitButtonLabel');
  if (fitLabel) fitLabel.textContent = modeObj.label;
  if (fitBtnLabel) fitBtnLabel.textContent = modeObj.buttonLabel;
};

window.toggleAdultFitMode = function () {
  const currentIdx = ADULT_FIT_MODES.findIndex(m => m.id === adultFitMode);
  const nextIdx = (currentIdx + 1) % ADULT_FIT_MODES.length;
  const nextMode = ADULT_FIT_MODES[nextIdx];
  window.setAdultFitMode(nextMode.id);
  window.showToast(`🔲 Screen Mode: ${nextMode.label}`, 'info');
};

window.toggleAdultFullscreen = function () {
  const container = document.getElementById('adultTheaterContainer');
  if (!container) return;

  const isFull = container.classList.contains('is-fullscreen') || Boolean(document.fullscreenElement);

  if (isFull) {
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => { });
    }
    container.classList.remove('is-fullscreen');
    if (window.Capacitor?.Plugins?.NativePlayer?.setOrientation) {
      window.Capacitor.Plugins.NativePlayer.setOrientation({ orientation: 'portrait' }).catch(() => { });
    } else if (window.screen?.orientation?.unlock) {
      try { window.screen.orientation.unlock(); } catch (_) { }
    }
    window.showToast('Exited Fullscreen', 'info');
  } else {
    container.classList.add('is-fullscreen');
    if (container.requestFullscreen) {
      container.requestFullscreen().catch(() => { });
    } else if (adultHtmlVideoPlayer && adultHtmlVideoPlayer.webkitEnterFullscreen) {
      adultHtmlVideoPlayer.webkitEnterFullscreen();
    }
    if (window.Capacitor?.Plugins?.NativePlayer?.setOrientation) {
      window.Capacitor.Plugins.NativePlayer.setOrientation({ orientation: 'sensor_landscape' }).catch(() => { });
    } else if (window.screen?.orientation?.lock) {
      window.screen.orientation.lock('landscape').catch(() => { });
    }
    window.showToast('⛶ Fullscreen Cinema Mode', 'info');
  }
};

// ============================================================================
// SECTION 2: Playlist Navigation & Up-Next Auto-Play Engine
// ============================================================================

window.cancelAdultUpNext = function () {
  if (adultUpNextTimer) {
    clearInterval(adultUpNextTimer);
    adultUpNextTimer = null;
  }
  const overlay = document.getElementById('adultUpNextOverlay');
  if (overlay) overlay.classList.add('hidden');
};

window.playNextAdultVideo = function () {
  window.cancelAdultUpNext();

  let nextItem = null;
  const playlist = window.currentAdultPlaylist || [];
  const currentIdx = typeof window.currentAdultIndex === 'number' ? window.currentAdultIndex : -1;

  if (playlist.length > 0 && currentIdx >= 0 && currentIdx + 1 < playlist.length) {
    nextItem = playlist[currentIdx + 1];
    window.currentAdultIndex = currentIdx + 1;
  } else if (window.currentAdultRelatedList && window.currentAdultRelatedList.length > 0) {
    nextItem = window.currentAdultRelatedList[0];
    window.currentAdultPlaylist = [nextItem, ...window.currentAdultRelatedList.slice(1)];
    window.currentAdultIndex = 0;
  } else if (playlist.length > 0) {
    nextItem = playlist[0];
    window.currentAdultIndex = 0;
  }

  if (nextItem) {
    window.showToast(`▶ Next: ${nextItem.title.slice(0, 28)}...`, 'info');
    window.openAdultWatchMode(nextItem);
  } else {
    window.showToast('No more videos in queue.', 'info');
  }
};

window.playPrevAdultVideo = function () {
  window.cancelAdultUpNext();

  let prevItem = null;
  const playlist = window.currentAdultPlaylist || [];
  const currentIdx = typeof window.currentAdultIndex === 'number' ? window.currentAdultIndex : 0;

  if (playlist.length > 0 && currentIdx > 0) {
    prevItem = playlist[currentIdx - 1];
    window.currentAdultIndex = currentIdx - 1;
  }

  if (prevItem) {
    window.showToast(`◀ Previous: ${prevItem.title.slice(0, 28)}...`, 'info');
    window.openAdultWatchMode(prevItem);
  } else {
    window.showToast('Already at the first video.', 'info');
  }
};

window.showAdultUpNextOverlay = function (nextItem) {
  window.cancelAdultUpNext();
  const overlay = document.getElementById('adultUpNextOverlay');
  const secEl = document.getElementById('adultUpNextSec');
  const titleEl = document.getElementById('adultUpNextTitle');
  if (!overlay || !nextItem) {
    window.playNextAdultVideo();
    return;
  }

  // Prime 0ms stream preload while countdown runs
  preloadNextAdultVideo(nextItem);

  if (titleEl) titleEl.textContent = nextItem.title || 'Next Video';
  let countdown = 3;
  if (secEl) secEl.textContent = countdown;
  overlay.classList.remove('hidden');

  adultUpNextTimer = setInterval(() => {
    countdown--;
    if (secEl) secEl.textContent = countdown;
    if (countdown <= 0) {
      window.cancelAdultUpNext();
      window.playNextAdultVideo();
    }
  }, 1000);
};

// ============================================================================
// SECTION 3: Search Bar, Category Glow Pills & Source Selectors
// ============================================================================

const adultSearchPlaceholders = [
  'Search 18+ 4K Videos, Japanese, Amateur, VR...',
  'Search Trending Pornhub, xHamster, Eporner 4K, XVideos...',
  'Search 4K Ultra HD & 1080p Streams...',
  'Search Verified Amateur, Romance, Cosplay...',
  'Search Japanese Uncensored & Top Rated HD...'
];

let adultPlaceholderIndex = 0;
let adultPlaceholderTimer = null;

function stopAdultSearchPlaceholder() {
  if (adultPlaceholderTimer) {
    clearTimeout(adultPlaceholderTimer);
    adultPlaceholderTimer = null;
  }
}

function scheduleNextAdultPlaceholder() {
  stopAdultSearchPlaceholder();
  if (document.hidden) return;
  if (window.state && window.state.currentTab && window.state.currentTab !== 'adult') return;
  adultPlaceholderTimer = setTimeout(rotateAdultSearchPlaceholder, 5000);
}

function rotateAdultSearchPlaceholder() {
  adultPlaceholderTimer = null;
  if (document.hidden || (window.state && window.state.currentTab && window.state.currentTab !== 'adult')) {
    return;
  }
  if (!adultSearchInput || document.activeElement === adultSearchInput || (adultSearchInput.value && adultSearchInput.value.length > 0)) {
    scheduleNextAdultPlaceholder();
    return;
  }
  adultPlaceholderIndex = (adultPlaceholderIndex + 1) % adultSearchPlaceholders.length;
  adultSearchInput.setAttribute('placeholder', adultSearchPlaceholders[adultPlaceholderIndex]);
  scheduleNextAdultPlaceholder();
}

// Lifecycle listeners: Pause timer when app/tab is backgrounded, resume when active
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    stopAdultSearchPlaceholder();
  } else if (!window.state || !window.state.currentTab || window.state.currentTab === 'adult') {
    scheduleNextAdultPlaceholder();
  }
});

window.addEventListener('cloud:active-tab-changed', (e) => {
  if (e.detail?.tab === 'adult') {
    scheduleNextAdultPlaceholder();
  } else {
    stopAdultSearchPlaceholder();
  }
});

if (!document.hidden && (!window.state || !window.state.currentTab || window.state.currentTab === 'adult')) {
  scheduleNextAdultPlaceholder();
}

if (adultSourceSelect) {
  adultSourceSelect.addEventListener('change', () => {
    window.currentAdultSource = adultSourceSelect.value;
    document.querySelectorAll('.source-pill-bar-2026 .source-pill').forEach(p => p.classList.toggle('active', p.dataset.src === window.currentAdultSource));
    window.searchAdult(adultSearchInput ? adultSearchInput.value || 'popular' : 'popular', window.currentAdultSource);
  });
}

document.querySelectorAll('.source-pill-bar-2026 .source-pill').forEach(pill => {
  pill.addEventListener('click', () => {
    document.querySelectorAll('.source-pill-bar-2026 .source-pill').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    window.currentAdultSource = pill.dataset.src;
    if (adultSourceSelect) adultSourceSelect.value = window.currentAdultSource;
    window.searchAdult(adultSearchInput ? adultSearchInput.value || 'popular' : 'popular', window.currentAdultSource);
  });
});

btnSearchAdult?.addEventListener('click', () => {
  clearTimeout(adultSearchDebounceTimer);
  const q = adultSearchInput ? adultSearchInput.value.trim() : 'for-you';
  if (q && q !== 'for-you') recordUserInterest(q, '', 6.0, 'search_button');
  window.searchAdult(q || 'for-you', window.currentAdultSource);
});

adultSearchInput?.addEventListener('input', () => {
  const val = adultSearchInput.value.trim();
  if (val.length > 0) {
    btnClearAdultSearch?.classList.remove('hidden');
  } else {
    btnClearAdultSearch?.classList.add('hidden');
  }

  clearTimeout(adultSearchDebounceTimer);
  adultSearchDebounceTimer = setTimeout(() => {
    if (val.length >= 2) {
      recordUserInterest(val, '', 5.0, 'search_typed');
      window.searchAdult(val, window.currentAdultSource);
    } else if (val.length === 0) {
      window.searchAdult('for-you', window.currentAdultSource);
    }
  }, 350);
});

adultSearchInput?.addEventListener('keypress', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    clearTimeout(adultSearchDebounceTimer);
    const q = adultSearchInput ? adultSearchInput.value.trim() : 'for-you';
    if (q && q !== 'for-you') recordUserInterest(q, '', 6.0, 'search_enter');
    window.searchAdult(q || 'for-you', window.currentAdultSource);
  }
});

btnClearAdultSearch?.addEventListener('click', () => {
  if (adultSearchInput) adultSearchInput.value = '';
  btnClearAdultSearch?.classList.add('hidden');
  document.querySelectorAll('.category-pills-adult .tag-pill').forEach((p, idx) => {
    p.classList.toggle('active', idx === 0);
  });
  window.searchAdult('for-you', window.currentAdultSource);
});

document.querySelectorAll('.category-pills-adult .tag-pill').forEach(pill => {
  pill.addEventListener('click', () => {
    document.querySelectorAll('.category-pills-adult .tag-pill').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    const q = pill.dataset.q;
    if (q && q !== 'for-you' && q !== 'popular') {
      recordUserInterest(q, '', 4.0, 'category_pill');
    }
    if (q !== 'for-you' && adultSearchInput) {
      adultSearchInput.value = q;
      btnClearAdultSearch?.classList.remove('hidden');
    } else if (q === 'for-you' && adultSearchInput) {
      adultSearchInput.value = '';
      btnClearAdultSearch?.classList.add('hidden');
    }
    clearTimeout(adultSearchDebounceTimer);
    window.searchAdult(q, window.currentAdultSource);
  });
});

// ============================================================================
// SECTION 0: Content Safety & Age Verification Gatekeeper (Sections 2, 3)
// ============================================================================

const PROHIBITED_MINOR_REGEX = /\b(teen|teenager|schoolgirl|student|young|underage|minor|child|kinder|ped|chld)\b/i;

function isSafeAdultQuery(query = '') {
  if (!query) return true;
  return !PROHIBITED_MINOR_REGEX.test(query.toLowerCase());
}

function isValidMediaUrl(url = '') {
  if (!url || typeof url !== 'string') return false;
  const clean = url.trim().toLowerCase();
  if (clean.startsWith('javascript:') || clean.startsWith('vbscript:') || clean.startsWith('data:text/html')) {
    return false;
  }
  return clean.startsWith('http://') || clean.startsWith('https://') || clean.startsWith('//') || clean.startsWith('data:image/');
}

function isSafeAdultCandidate(item = {}) {
  if (!item) return false;
  const title = (item.title || '').toLowerCase();
  const url = (item.url || item.link || '').toLowerCase();
  const thumb = (item.thumbnail || '');
  if (thumb && !isValidMediaUrl(thumb)) return false;
  if (url && !isValidMediaUrl(url)) return false;
  const tags = Array.isArray(item.tags) ? item.tags.join(' ').toLowerCase() : (item.tags || '').toLowerCase();
  return !PROHIBITED_MINOR_REGEX.test(title) && !PROHIBITED_MINOR_REGEX.test(url) && !PROHIBITED_MINOR_REGEX.test(tags);
}

function escapeHtml(str = '') {
  if (typeof str !== 'string') return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// 🔒 In-memory session flag — only 18+ tab resets to locked on every app launch / reload
let _adultGateSessionUnlocked = false;

// Ensure any stale persistent storage from older app versions is permanently deleted
try {
  localStorage.removeItem('cdl_adult_age_unlocked');
  sessionStorage.removeItem('cdl_adult_age_unlocked');
} catch (_) { }

function isAdultGateUnlocked() {
  return _adultGateSessionUnlocked === true;
}

function setAdultGateUnlocked(unlocked = true) {
  _adultGateSessionUnlocked = (unlocked === true);
  try {
    localStorage.removeItem('cdl_adult_age_unlocked');
    sessionStorage.removeItem('cdl_adult_age_unlocked');
  } catch (_) { }
}

function checkAdultGateState() {
  const gateEl = document.getElementById('adultAgeGate') || adultAgeGate;
  const browseEl = document.getElementById('adultBrowseView') || adultBrowseView;
  const watchEl = document.getElementById('adultWatchView') || adultWatchView;
  const isUnlocked = isAdultGateUnlocked();

  if (!isUnlocked) {
    if (gateEl) gateEl.classList.remove('hidden');
    if (browseEl) browseEl.classList.add('hidden');
    if (watchEl) watchEl.classList.add('hidden');
    return false;
  } else {
    if (gateEl) gateEl.classList.add('hidden');
    if (browseEl && (!watchEl || watchEl.classList.contains('hidden'))) {
      browseEl.classList.remove('hidden');
    }
    return true;
  }
}

window.isAdultGateUnlocked = isAdultGateUnlocked;
window.setAdultGateUnlocked = setAdultGateUnlocked;
window.checkAdultGateState = checkAdultGateState;

window.lockAdultGate = function () {
  setAdultGateUnlocked(false);
  if (typeof window.closeAdultWatchMode === 'function') {
    window.closeAdultWatchMode();
  }
  checkAdultGateState();
  if (typeof window.showToast === 'function') {
    window.showToast('🔒 18+ Hub locked. Age verification required.', 'info');
  }
};

// Re-lock 18+ tab automatically whenever the app is closed or minimized to background
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') {
    _adultGateSessionUnlocked = false;
    checkAdultGateState();
  }
});

try {
  if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.App) {
    window.Capacitor.Plugins.App.addListener('appStateChange', (state) => {
      if (!state.isActive) {
        _adultGateSessionUnlocked = false;
        checkAdultGateState();
      }
    });
  }
} catch (_) { }

// ============================================================================
// SECTION 4: Neural Recommendation Engine & On-Device Taste Profiling
// ============================================================================

const ADULT_STOP_WORDS = new Set([
  'the', 'and', 'for', 'with', 'from', 'this', 'that', 'video', 'full', 'part',
  'scene', 'clip', 'best', 'new', 'more', 'com', 'www', 'free', 'online',
  'watch', 'tube', 'porn', 'xxx', 'sex', '2024', '2025', '2026', 'episode', 'series',
  'sub', 'raw', 'uncut', 'web', 'rip', 'cam', 'play', 'movie', 'actor', 'actress',
  'download', 'mp4', 'stream', 'hd', 'fhd', 'link', 'site', 'org', 'net'
]);

// 🔞 Content-Safe Adults-Only Taxonomy (Sections 2 & 5)
// Strictly purged of all minor, school-age, or ambiguous-age references
const ADULT_GENRE_TAXONOMY = {
  japanese: {
    label: 'Japanese JAV',
    seed: 'japanese',
    aliases: ['japanese', 'japan', 'jav', 'tokyo', 'asian', 'oriental', 'uncensored', 'censored', 'fc2', 's-cute', 'osakaporn', 'kurea', 'subtitled', 'ryouka', 'shinoda'],
    related: ['cosplay', 'massage', 'amateur']
  },
  cosplay: {
    label: 'Cosplay & Anime',
    seed: 'cosplay 4k',
    aliases: ['cosplay', 'costume', 'anime', 'maid', 'nurse', 'heroine', 'bunny', 'uniform', 'cheerleader', 'harley quinn'],
    related: ['japanese', 'amateur']
  },
  amateur: {
    label: 'Real Amateur & POV',
    seed: 'amateur verified',
    aliases: ['amateur', 'homemade', 'verified', 'real', 'girlfriend', 'couple', 'hidden', 'home', 'selfie', 'webcam', 'pov', 'point of view'],
    related: ['romance', 'pov']
  },
  quality_4k: {
    label: '4K UHD Cinema',
    seed: '4k ultra hd',
    aliases: ['4k', '2160p', 'uhd', 'ultra hd', '60fps', 'crystal', 'pornbcn 4k', 'eporner 4k'],
    related: ['amateur', 'japanese']
  },
  romance: {
    label: 'Romance & Sensual',
    seed: 'romance sensual',
    aliases: ['romance', 'romantic', 'sensual', 'erotic', 'love', 'passion', 'kiss', 'massage', 'tender', 'gentle', 'cuddle'],
    related: ['amateur', 'asian', 'pov']
  },
  milf: {
    label: 'MILF & Mature',
    seed: 'milf mature',
    aliases: ['milf', 'mature', 'mom', 'stepmom', 'older', 'aunt', 'cougar', 'housewife', 'mother'],
    related: ['amateur', 'threesome']
  },
  asian: {
    label: 'Asian & Oriental',
    seed: 'asian 4k',
    aliases: ['asian', 'chinese', 'korean', 'taiwan', 'thai', 'vietnamese', 'filipina', 'oriental', 'bj'],
    related: ['japanese', 'massage', 'romance']
  },
  hardcore: {
    label: 'Hardcore & Rough',
    seed: 'hardcore rough',
    aliases: ['hardcore', 'rough', 'deepthroat', 'creampie', 'gangbang', 'anal', 'double penetration', 'dp', 'hard'],
    related: ['threesome', 'amateur']
  },
  threesome: {
    label: 'Threesome & Group',
    seed: 'threesome group',
    aliases: ['threesome', 'group', 'orgy', 'foursome', 'menage', 'triad'],
    related: ['hardcore', 'lesbian']
  },
  lesbian: {
    label: 'Lesbian & Girls',
    seed: 'lesbian hd',
    aliases: ['lesbian', 'girl on girl', 'tribbing', 'scissoring', 'girls'],
    related: ['romance', 'solo']
  },
  solo: {
    label: 'Solo & Squirting',
    seed: 'solo masturbation',
    aliases: ['solo', 'masturbation', 'dildo', 'fingering', 'squirt', 'squirting', 'toy', 'vibrator'],
    related: ['lesbian', 'amateur']
  },
  massage: {
    label: 'Sensual Massage',
    seed: 'massage nuru',
    aliases: ['massage', 'spa', 'oil', 'nuru', 'bodyrub', 'sensual massage'],
    related: ['romance', 'japanese', 'asian']
  },
  vr: {
    label: 'Virtual Reality 360',
    seed: 'vr 4k',
    aliases: ['vr', 'virtual reality', '180', '360', 'oculus', 'sbs'],
    related: ['pov', 'quality_4k']
  },
  latina: {
    label: 'Latina',
    seed: 'latina amateur',
    aliases: ['latina', 'spanish', 'brazilian', 'mexican', 'colombian'],
    related: ['amateur', 'hardcore']
  },
  desi: {
    label: 'Desi & Indian',
    seed: 'desi romance',
    aliases: ['desi', 'indian', 'bhabhi', 'sinhala', 'tamil', 'hindi', 'mallu', 'aunty', 'devar', 'village'],
    related: ['romance', 'amateur']
  }
};

// ⚙️ Unified Recommendation Engine Configuration (Section 24)
const ADULT_RECOMMENDATION_CONFIG = {
  WEIGHTS: {
    IMPRESSION: 0,
    CLICK: 0.5,
    PLAY_START: 1.0,
    WATCH_5S: 0.5,
    WATCH_15S: 2.0,
    WATCH_30S: 3.5,
    WATCH_50_PCT: 5.0,
    WATCH_80_PCT: 8.0,
    WATCH_COMPLETE: 10.0,
    REPLAY: 7.0,
    LIKE: 10.0,
    DISLIKE: -6.0,
    NOT_INTERESTED: -12.0,
    HIDE_SOURCE: -20.0,
    BOUNCE: -1.5,
    DOWNLOAD: 3.0,
    SAVE: 4.0
  },
  TASTE_HALF_LIFE_DAYS: 7,
  RECENT_INTERACTION_HALF_LIFE_DAYS: 2,
  NEGATIVE_HALF_LIFE_DAYS: 14,
  EXPLORATION_RATE: 0.20, // 80% taste-matched, 20% fresh/trending
  DIVERSITY_LIMITS: {
    MAX_CONSECUTIVE_SAME_SOURCE: 2,
    MAX_CONSECUTIVE_SAME_CLUSTER: 2
  },
  FATIGUE_COOLDOWN_HOURS: 24,
  MAX_HISTORY: 50,
  MAX_EVENTS: 100,
  MAX_CONTINUE_WATCHING: 15
};

const TASTE_HALF_LIFE_MS = ADULT_RECOMMENDATION_CONFIG.TASTE_HALF_LIFE_DAYS * 24 * 60 * 60 * 1000;
const NEGATIVE_HALF_LIFE_MS = ADULT_RECOMMENDATION_CONFIG.NEGATIVE_HALF_LIFE_DAYS * 24 * 60 * 60 * 1000;

function extractVideoClusters(title = '', source = '') {
  if (!isSafeAdultQuery(title)) return [];
  const norm = (title + ' ' + (source || '')).toLowerCase();
  const matched = [];
  for (const [clusterKey, cluster] of Object.entries(ADULT_GENRE_TAXONOMY)) {
    for (const alias of cluster.aliases) {
      if (norm.includes(alias)) {
        matched.push(clusterKey);
        break;
      }
    }
  }
  return matched;
}

function getUserTasteProfileV3() {
  try {
    const raw = localStorage.getItem('adult_taste_profile_v3');
    if (raw) return JSON.parse(raw);

    const v2 = JSON.parse(localStorage.getItem('adult_taste_profile_v2') || '{}');
    const migrated = {
      clusters: v2.clusters || {},
      sources: v2.sources || {},
      qualities: {},
      durations: {},
      negativeItems: {},
      negativeClusters: {},
      hiddenSources: {},
      likes: {},
      dislikes: {},
      updatedAt: Date.now()
    };
    for (const k of Object.keys(migrated.clusters)) {
      if (PROHIBITED_MINOR_REGEX.test(k)) {
        delete migrated.clusters[k];
      }
    }
    return migrated;
  } catch (_) {
    return {
      clusters: {},
      sources: {},
      qualities: {},
      durations: {},
      negativeItems: {},
      negativeClusters: {},
      hiddenSources: {},
      likes: {},
      dislikes: {},
      updatedAt: Date.now()
    };
  }
}

function saveUserTasteProfileV3(profile) {
  try {
    if (!profile) return;
    profile.updatedAt = Date.now();
    localStorage.setItem('adult_taste_profile_v3', JSON.stringify(profile));
  } catch (_) { }
}

function getDecayedClusterScore(clusterData, now = Date.now()) {
  if (!clusterData || typeof clusterData.score !== 'number') return 0;
  const elapsed = Math.max(0, now - (clusterData.lastUpdated || now));
  const decayFactor = Math.pow(0.5, elapsed / TASTE_HALF_LIFE_MS);
  return clusterData.score * decayFactor;
}

function recordAdultEvent(eventType, item = {}, meta = {}) {
  try {
    if (localStorage.getItem('cdl_adult_history_enabled') === 'false') return;

    const events = JSON.parse(localStorage.getItem('cdl_adult_events_v2') || '[]');
    const title = item.title || meta.title || '';
    const source = item.source || meta.source || '';
    const itemId = item.url || item.link || title;

    if (!isSafeAdultQuery(title)) return;

    const event = {
      id: 'ev_' + Date.now() + '_' + Math.random().toString(36).slice(2, 7),
      eventType,
      itemId,
      title: title.slice(0, 100),
      source,
      durationWatched: meta.durationWatched || 0,
      position: meta.position || 0,
      timestamp: Date.now()
    };

    events.unshift(event);
    if (events.length > ADULT_RECOMMENDATION_CONFIG.MAX_EVENTS) {
      events.length = ADULT_RECOMMENDATION_CONFIG.MAX_EVENTS;
    }
    localStorage.setItem('cdl_adult_events_v2', JSON.stringify(events));

    if (localStorage.getItem('cdl_adult_personalization_enabled') !== 'false') {
      const weight = ADULT_RECOMMENDATION_CONFIG.WEIGHTS[eventType] ?? 0;
      if (weight !== 0 && title) {
        applyFeedbackToProfile(title, source, weight, eventType, itemId);
      }
    }
  } catch (err) {
    console.warn('[AdultEvents] Error recording event:', err);
  }
}

function recordUserInterest(queryOrTitle, source = '', weight = 4.0, reason = 'interest') {
  if (!queryOrTitle) return;
  recordAdultEvent('SEARCH', { title: queryOrTitle, source: source || window.currentAdultSource || 'all' }, { weight, reason });
}
window.recordUserInterest = recordUserInterest;

function applyFeedbackToProfile(title = '', source = '', boost = 1.0, reason = 'view', itemId = '') {
  try {
    const profile = getUserTasteProfileV3();
    const now = Date.now();
    const matchedClusters = extractVideoClusters(title, source);

    if (boost > 0) {
      if (matchedClusters.length === 0) {
        const words = title.toLowerCase().replace(/[^a-z0-9 ]/g, ' ').split(/\s+/).filter(w => w.length >= 3 && !ADULT_STOP_WORDS.has(w) && !PROHIBITED_MINOR_REGEX.test(w));
        words.slice(0, 2).forEach(w => {
          profile.clusters[w] = profile.clusters[w] || { score: 0, lastUpdated: now, count: 0 };
          const currentEffective = getDecayedClusterScore(profile.clusters[w], now);
          profile.clusters[w].score = Math.max(0, currentEffective + boost * 0.6);
          profile.clusters[w].lastUpdated = now;
          profile.clusters[w].count += 1;
        });
      } else {
        matchedClusters.forEach(cKey => {
          profile.clusters[cKey] = profile.clusters[cKey] || { score: 0, lastUpdated: now, count: 0 };
          const currentEffective = getDecayedClusterScore(profile.clusters[cKey], now);
          profile.clusters[cKey].score = Math.max(0, currentEffective + boost);
          profile.clusters[cKey].lastUpdated = now;
          profile.clusters[cKey].count += 1;
        });
      }

      if (source) {
        profile.sources[source] = (profile.sources[source] || 0) + (boost >= 5 ? 2 : 1);
      }

      if (title.toLowerCase().includes('4k') || title.toLowerCase().includes('2160')) {
        profile.qualities['4k'] = (profile.qualities['4k'] || 0) + 1;
      }

      if (reason === 'LIKE' && itemId) {
        profile.likes[itemId] = true;
        delete profile.dislikes[itemId];
      }

      const history = JSON.parse(localStorage.getItem('adult_watch_history') || '[]');
      history.unshift({ title, source, timestamp: now, reason, itemId });
      if (history.length > ADULT_RECOMMENDATION_CONFIG.MAX_HISTORY) history.length = ADULT_RECOMMENDATION_CONFIG.MAX_HISTORY;
      localStorage.setItem('adult_watch_history', JSON.stringify(history));

    } else {
      // Negative feedback
      const penalty = Math.abs(boost);
      matchedClusters.forEach(cKey => {
        profile.negativeClusters[cKey] = (profile.negativeClusters[cKey] || 0) + penalty;
        if (profile.clusters[cKey]) {
          profile.clusters[cKey].score = Math.max(0, (profile.clusters[cKey].score || 0) - penalty * 0.7);
        }
      });

      if (reason === 'NOT_INTERESTED' && itemId) {
        profile.negativeItems[itemId] = { penalty: 30, timestamp: now };
      } else if (reason === 'DISLIKE' && itemId) {
        profile.dislikes[itemId] = true;
        delete profile.likes[itemId];
        profile.negativeItems[itemId] = { penalty: 15, timestamp: now };
      } else if (reason === 'HIDE_SOURCE' && source) {
        profile.hiddenSources[source] = { hiddenAt: now };
      } else if (reason === 'BOUNCE' && itemId) {
        profile.negativeItems[itemId] = { penalty: 6, timestamp: now };
      }
    }

    // Prune stale items
    for (const [k, v] of Object.entries(profile.clusters)) {
      if (getDecayedClusterScore(v, now) < 0.2 && v.count <= 1) {
        delete profile.clusters[k];
      }
    }

    saveUserTasteProfileV3(profile);
  } catch (e) {
    console.warn('[TasteEngine] Update error:', e);
  }
}

function getTopTasteClusters(max = 3) {
  try {
    const profile = getUserTasteProfileV3();
    const now = Date.now();
    const sorted = Object.entries(profile.clusters || {})
      .filter(([k]) => !PROHIBITED_MINOR_REGEX.test(k))
      .map(([key, data]) => ({ key, score: getDecayedClusterScore(data, now) }))
      .filter(item => item.score >= 1.5)
      .sort((a, b) => b.score - a.score);
    return sorted.slice(0, max);
  } catch (_) {
    return [];
  }
}

// Deterministic Round-Robin Rotation for For-You Query (Section 23)
// Strictly ZERO VPN/Geo bias (Section 4)
let adultForYouRotationIndex = 0;

function getSmartForYouQuery() {
  const top = getTopTasteClusters(4);
  if (top.length >= 2) {
    adultForYouRotationIndex = (adultForYouRotationIndex + 1) % top.length;
    const pKey = top[adultForYouRotationIndex].key;
    const sKey = top[(adultForYouRotationIndex + 1) % top.length].key;
    const pCluster = ADULT_GENRE_TAXONOMY[pKey] || { seed: pKey };
    const sCluster = ADULT_GENRE_TAXONOMY[sKey] || { seed: sKey };
    return `${pCluster.seed} ${sCluster.seed}`;
  } else if (top.length === 1) {
    const pCluster = ADULT_GENRE_TAXONOMY[top[0].key] || { seed: top[0].key };
    return `${pCluster.seed} 4k`;
  }

  const neutralColdSeeds = ['trending 4k', 'verified amateur 4k', 'romance 4k', 'japanese 4k'];
  adultForYouRotationIndex = (adultForYouRotationIndex + 1) % neutralColdSeeds.length;
  return neutralColdSeeds[adultForYouRotationIndex];
}

// ============================================================================
// WATCH SESSION PROGRESS & COMPLETION TRACKER (Sections 8, 9, 10, 26, 28)
// ============================================================================

function startAdultWatchSession(item) {
  finalizeAdultWatchSession();
  currentAdultWatchSession = {
    item,
    startTime: Date.now(),
    lastActiveTime: Date.now(),
    activeWatchedSeconds: 0,
    maxPosition: 0,
    videoDuration: 0,
    hasTriggered15s: false,
    hasTriggered50Pct: false,
    hasTriggered80Pct: false,
    hasTriggeredComplete: false,
    isPaused: false
  };
  recordAdultEvent('PLAY_START', item);
}

function updateAdultWatchProgress(currentTime, duration) {
  if (!currentAdultWatchSession) return;
  const now = Date.now();
  if (!currentAdultWatchSession.isPaused) {
    const delta = Math.min((now - currentAdultWatchSession.lastActiveTime) / 1000, 2);
    currentAdultWatchSession.activeWatchedSeconds += delta;
  }
  currentAdultWatchSession.lastActiveTime = now;
  currentAdultWatchSession.maxPosition = Math.max(currentAdultWatchSession.maxPosition, currentTime);
  if (duration && Number.isFinite(duration) && duration > 0) {
    currentAdultWatchSession.videoDuration = duration;
  }

  const actSec = currentAdultWatchSession.activeWatchedSeconds;
  const dur = currentAdultWatchSession.videoDuration;
  const ratio = dur > 0 ? (actSec / dur) : 0;

  if (actSec >= 15 && !currentAdultWatchSession.hasTriggered15s) {
    currentAdultWatchSession.hasTriggered15s = true;
    recordAdultEvent('WATCH_15S', currentAdultWatchSession.item, { durationWatched: actSec });
  }
  if (ratio >= 0.50 && !currentAdultWatchSession.hasTriggered50Pct) {
    currentAdultWatchSession.hasTriggered50Pct = true;
    recordAdultEvent('WATCH_50_PCT', currentAdultWatchSession.item, { durationWatched: actSec, position: currentTime });
  }
  if (actSec >= 10 && !currentAdultWatchSession.hasTriggeredPreload) {
    currentAdultWatchSession.hasTriggeredPreload = true;
    scheduleNextAdultPreload();
  }
  if (ratio >= 0.80 && !currentAdultWatchSession.hasTriggered80Pct) {
    currentAdultWatchSession.hasTriggered80Pct = true;
    recordAdultEvent('WATCH_80_PCT', currentAdultWatchSession.item, { durationWatched: actSec, position: currentTime });
  }
}

function finalizeAdultWatchSession() {
  if (!currentAdultWatchSession) return;
  const session = currentAdultWatchSession;
  currentAdultWatchSession = null;

  const actSec = session.activeWatchedSeconds;
  const dur = session.videoDuration;
  const ratio = dur > 0 ? (session.maxPosition / dur) : 0;
  const item = session.item;

  if (!item || !item.title) return;

  // 1. Quick bounce detection (< 5s active watch)
  if (actSec < 5 && !session.hasTriggered15s) {
    recordAdultEvent('BOUNCE', item, { durationWatched: actSec });
    return;
  }

  // 2. Video completion (>= 90% watched)
  if (ratio >= 0.90 || session.hasTriggeredComplete) {
    recordAdultEvent('WATCH_COMPLETE', item, { durationWatched: actSec, position: session.maxPosition });
    removeFromContinueWatching(item.url || item.link || item.title);
    try {
      localStorage.setItem('cdl_adult_last_watched_item', JSON.stringify({
        title: item.title,
        source: item.source,
        quality: item.quality || '1080p',
        thumbnail: item.thumbnail || '',
        watchedAt: Date.now()
      }));
    } catch (_) { }
  } else if (ratio >= 0.10 && session.maxPosition >= 10) {
    // 3. Unfinished video (10% - 90% watched) -> Save to Continue Watching
    saveToContinueWatching(item, session.maxPosition, dur, Math.round(ratio * 100));
  }
}

window.finalizeAdultWatchSession = finalizeAdultWatchSession;

// ============================================================================
// PRO PRE-RESOLVE & ZERO-LATENCY NEXT STREAM PRELOAD PIPELINE (Section 9)
// ============================================================================

const adultPreloadCache = new Map();

async function preloadNextAdultVideo(item) {
  if (!item || (!item.url && !item.link)) return;
  const targetUrl = item.url || item.link;
  if (adultPreloadCache.has(targetUrl)) return;

  try {
    const isNativeApp = !!(window.Capacitor?.isNativePlatform?.() || window.Capacitor?.Plugins?.NativeExtractor);
    if (window.Capacitor?.Plugins?.NativeExtractor?.resolveAdultStream) {
      const nRes = await withAdultTimeout(
        window.Capacitor.Plugins.NativeExtractor.resolveAdultStream({ url: targetUrl }),
        6000
      );
      if (nRes) {
        const res = nRes.resolved || nRes.stream || nRes;
        if (res && (res.streamUrl || res.embedUrl || (res.qualities && res.qualities.length > 0))) {
          adultPreloadCache.set(targetUrl, res);
          console.log('[Adult Preload] Pre-resolved next stream:', item.title);
          return;
        }
      }
    }
    if (window.StandaloneEngine?.resolveAdultStream && !isNativeApp) {
      const sRes = await withAdultTimeout(window.StandaloneEngine.resolveAdultStream(targetUrl), 4000);
      if (sRes && (sRes.streamUrl || sRes.embedUrl)) {
        adultPreloadCache.set(targetUrl, sRes);
      }
    }
  } catch (_) { }
}

function scheduleNextAdultPreload() {
  const playlist = window.currentAdultPlaylist || [];
  const currentIdx = typeof window.currentAdultIndex === 'number' ? window.currentAdultIndex : -1;
  let nextItem = null;
  if (playlist.length > 0 && currentIdx >= 0 && currentIdx + 1 < playlist.length) {
    nextItem = playlist[currentIdx + 1];
  } else if (window.currentAdultRelatedList && window.currentAdultRelatedList.length > 0) {
    nextItem = window.currentAdultRelatedList[0];
  }
  if (nextItem) {
    preloadNextAdultVideo(nextItem);
  }
}

window.closeAdultWatchMode = function () {
  finalizeAdultWatchSession();
  if (currentAdultHls) {
    try { currentAdultHls.destroy(); } catch (_) { }
    currentAdultHls = null;
  }
  if (adultHtmlVideoPlayer) {
    adultHtmlVideoPlayer.pause();
    adultHtmlVideoPlayer.removeAttribute('src');
    try { adultHtmlVideoPlayer.load(); } catch (_) { }
    adultHtmlVideoPlayer.src = '';
    adultHtmlVideoPlayer.classList.add('hidden');
  }
  if (adultIframePlayer) {
    adultIframePlayer.src = 'about:blank';
    adultIframePlayer.removeAttribute('src');
    adultIframePlayer.classList.add('hidden');
  }
  if (adultWatchView) adultWatchView.classList.add('hidden');
  checkAdultGateState();
  renderContinueWatchingRail();
  renderBecauseYouWatchedRail();
};

btnBackToAdultBrowse?.addEventListener('click', window.closeAdultWatchMode);

// ============================================================================
// CONTINUE WATCHING & BECAUSE YOU WATCHED RAILS (Sections 26, 28)
// ============================================================================

function getContinueWatchingList() {
  try {
    return JSON.parse(localStorage.getItem('cdl_adult_continue_watching') || '[]');
  } catch (_) {
    return [];
  }
}

function saveToContinueWatching(item, position, duration, percent) {
  if (localStorage.getItem('cdl_adult_continue_watching_enabled') === 'false') return;
  try {
    let list = getContinueWatchingList();
    const id = item.url || item.link || item.title;
    list = list.filter(x => (x.url || x.link || x.title) !== id);
    list.unshift({
      id,
      url: item.url || item.link || '',
      link: item.link || item.url || '',
      title: item.title,
      thumbnail: item.thumbnail || '',
      source: item.source || '18+',
      quality: item.quality || 'HD',
      duration: item.duration || (duration ? Math.floor(duration / 60) + 'm' : 'HD'),
      position: Math.round(position),
      totalDuration: Math.round(duration || 0),
      percent: Math.min(Math.max(percent, 5), 95),
      lastWatchedAt: Date.now()
    });
    if (list.length > ADULT_RECOMMENDATION_CONFIG.MAX_CONTINUE_WATCHING) {
      list.length = ADULT_RECOMMENDATION_CONFIG.MAX_CONTINUE_WATCHING;
    }
    localStorage.setItem('cdl_adult_continue_watching', JSON.stringify(list));
    renderContinueWatchingRail();
  } catch (_) { }
}

function removeFromContinueWatching(id) {
  try {
    let list = getContinueWatchingList();
    list = list.filter(x => (x.id !== id && (x.url || x.link || x.title) !== id));
    localStorage.setItem('cdl_adult_continue_watching', JSON.stringify(list));
    renderContinueWatchingRail();
  } catch (_) { }
}

function renderContinueWatchingRail() {
  const section = document.getElementById('adultContinueWatchingSection');
  const rail = document.getElementById('adultContinueWatchingRail');
  if (!section || !rail) return;

  const isEnabled = localStorage.getItem('cdl_adult_continue_watching_enabled') !== 'false';
  const list = isEnabled ? getContinueWatchingList() : [];

  if (list.length === 0) {
    section.classList.add('hidden');
    rail.innerHTML = '';
    return;
  }

  section.classList.remove('hidden');
  rail.innerHTML = '';

  list.forEach(item => {
    const card = document.createElement('div');
    card.className = 'adult-continue-card';
    const fallbackThumb = 'data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22300%22%20height%3D%22200%22%20viewBox%3D%220%200%20300%20200%22%3E%3Crect%20fill%3D%22%23121526%22%20width%3D%22100%25%22%20height%3D%22100%25%22%2F%3E%3Ctext%20fill%3D%22%23ff007f%22%20font-family%3D%22sans-serif%22%20font-size%3D%2216%22%20font-weight%3D%22bold%22%20x%3D%2250%25%22%20y%3D%2250%25%22%20dominant-baseline%3D%22middle%22%20text-anchor%3D%22middle%22%3E18%2B%20STREAM%3C%2Ftext%3E%3C%2Fsvg%3E';
    let thumb = item.thumbnail || fallbackThumb;
    if (thumb.startsWith('//')) thumb = 'https:' + thumb;

    card.innerHTML = `
      <div class="adult-continue-thumb-wrap">
        <img src="${escapeHtml(thumb)}" alt="${escapeHtml(item.title)}" loading="lazy" decoding="async" onerror="this.onerror=null;this.src='${fallbackThumb}'">
        <span class="adult-continue-resume-pill"><i class="fa-solid fa-play"></i> ${item.percent}%</span>
        <button class="adult-continue-dismiss-btn" title="Remove from Continue Watching">×</button>
        <div class="adult-progress-bar-wrap">
          <div class="adult-progress-bar-fill" style="width: ${item.percent}%;"></div>
        </div>
      </div>
      <div class="adult-continue-info">
        <div class="adult-continue-title">${escapeHtml(item.title)}</div>
        <div class="adult-continue-meta">
          <span style="color: var(--accent-magenta); font-weight: 700;">${escapeHtml(item.source)}</span>
          <span>${escapeHtml(item.duration)}</span>
        </div>
      </div>
    `;

    card.addEventListener('click', (e) => {
      if (e.target.closest('.adult-continue-dismiss-btn')) return;
      window.openAdultWatchMode({ ...item, resumePosition: item.position });
    });

    card.querySelector('.adult-continue-dismiss-btn')?.addEventListener('click', (e) => {
      e.stopPropagation();
      removeFromContinueWatching(item.id);
    });

    rail.appendChild(card);
  });
}

function renderBecauseYouWatchedRail() {
  const section = document.getElementById('adultBecauseYouWatchedSection');
  const rail = document.getElementById('adultBecauseYouWatchedRail');
  const titleEl = document.getElementById('adultBecauseYouWatchedTitle');
  if (!section || !rail) return;

  const rawLast = localStorage.getItem('cdl_adult_last_watched_item');
  if (!rawLast) {
    section.classList.add('hidden');
    rail.innerHTML = '';
    return;
  }

  let lastWatched;
  try {
    lastWatched = JSON.parse(rawLast);
  } catch (_) {
    section.classList.add('hidden');
    return;
  }

  if (!lastWatched || !lastWatched.title) {
    section.classList.add('hidden');
    return;
  }

  const targetClusters = extractVideoClusters(lastWatched.title, lastWatched.source);
  if (targetClusters.length === 0 || !window.rawAdultResults || window.rawAdultResults.length === 0) {
    section.classList.add('hidden');
    return;
  }

  const lastTitleNorm = lastWatched.title.toLowerCase().replace(/[^a-z0-9]/g, '');
  const candidates = window.rawAdultResults.filter(it => {
    if (!isSafeAdultCandidate(it)) return false;
    const itNorm = (it.title || '').toLowerCase().replace(/[^a-z0-9]/g, '');
    if (itNorm === lastTitleNorm || itNorm.includes(lastTitleNorm.slice(0, 15))) return false;
    const itClusters = extractVideoClusters(it.title, it.source);
    return itClusters.some(c => targetClusters.includes(c));
  });

  if (candidates.length < 3) {
    section.classList.add('hidden');
    return;
  }

  section.classList.remove('hidden');
  if (titleEl) {
    const cleanTitleSnippet = lastWatched.title.length > 24 ? lastWatched.title.slice(0, 24) + '...' : lastWatched.title;
    titleEl.innerHTML = `<i class="fa-solid fa-sparkles" style="color: #ffd700;"></i> Because You Watched <span style="color: var(--accent-magenta); font-weight: 600;">"${escapeHtml(cleanTitleSnippet)}"</span>`;
  }

  rail.innerHTML = '';
  candidates.slice(0, 10).forEach(item => {
    const card = document.createElement('div');
    card.className = 'adult-continue-card';
    const fallbackThumb = 'data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22300%22%20height%3D%22200%22%20viewBox%3D%220%200%20300%20200%22%3E%3Crect%20fill%3D%22%23121526%22%20width%3D%22100%25%22%20height%3D%22100%25%22%2F%3E%3Ctext%20fill%3D%22%23ff007f%22%20font-family%3D%22sans-serif%22%20font-size%3D%2216%22%20font-weight%3D%22bold%22%20x%3D%2250%25%22%20y%3D%2250%25%22%20dominant-baseline%3D%22middle%22%20text-anchor%3D%22middle%22%3E18%2B%20STREAM%3C%2Ftext%3E%3C%2Fsvg%3E';
    let thumb = item.thumbnail || fallbackThumb;
    if (thumb.startsWith('//')) thumb = 'https:' + thumb;

    card.innerHTML = `
      <div class="adult-continue-thumb-wrap">
        <img src="${escapeHtml(thumb)}" alt="${escapeHtml(item.title)}" loading="lazy" decoding="async" onerror="this.onerror=null;this.src='${fallbackThumb}'">
        <span class="adult-continue-resume-pill"><i class="fa-solid fa-sparkles"></i> Sim</span>
      </div>
      <div class="adult-continue-info">
        <div class="adult-continue-title">${escapeHtml(item.title)}</div>
        <div class="adult-continue-meta">
          <span style="color: var(--accent-magenta); font-weight: 700;">${escapeHtml(item.source || '18+')}</span>
          <span>${escapeHtml(item.duration || 'HD')}</span>
        </div>
      </div>
    `;

    card.addEventListener('click', () => {
      recordAdultEvent('CLICK', item);
      window.openAdultWatchMode(item);
    });

    rail.appendChild(card);
  });
}

// ============================================================================
// SECTION 5: Multi-Factor Smart Ranking & Diversity Engine (Sections 21–25)
// ============================================================================

function applySmartAlgorithm(videos = [], algo = 'for-you') {
  if (!videos || videos.length === 0) return [];
  let list = videos.filter(isSafeAdultCandidate);

  const profile = getUserTasteProfileV3();
  const now = Date.now();
  const hiddenSources = profile.hiddenSources || {};
  const negativeItems = profile.negativeItems || {};
  const preferredSources = profile.sources || {};

  // Filter out hidden sources and heavy negative items
  list = list.filter(item => {
    if (item.source && hiddenSources[item.source]) return false;
    const itemId = item.url || item.link || item.title;
    if (negativeItems[itemId] && (now - negativeItems[itemId].timestamp < NEGATIVE_HALF_LIFE_MS)) {
      return false;
    }
    return true;
  });

  const personalizationEnabled = localStorage.getItem('cdl_adult_personalization_enabled') !== 'false';
  const sourcePersonalizationEnabled = localStorage.getItem('cdl_adult_source_personalization_enabled') !== 'false';
  const qualityPreference = localStorage.getItem('cdl_adult_quality_preference') || 'any';
  const trendingInfluence = localStorage.getItem('cdl_adult_trending_influence') || 'balanced';

  // Multi-Factor candidate scoring
  const scoredList = list.map(item => {
    let score = 50;
    const itemClusters = extractVideoClusters(item.title, item.source);

    // 1. User Taste Alignment (if personalization enabled)
    if (personalizationEnabled) {
      itemClusters.forEach(cKey => {
        const cData = profile.clusters[cKey];
        if (cData) {
          const decayed = getDecayedClusterScore(cData, now);
          score += Math.min(decayed * 18, 150);
        }
        if (profile.negativeClusters[cKey]) {
          score -= Math.min(profile.negativeClusters[cKey] * 4, 60);
        }
      });
    }

    // 2. Source Affinity (if source personalization enabled)
    if (sourcePersonalizationEnabled && item.source && preferredSources[item.source]) {
      score += Math.min(preferredSources[item.source] * 5, 25);
    }

    // 3. Viral Velocity & View Count Factor
    const viewsNum = parseAdultViewCount(item.views);
    let viewBoost = 0;
    if (viewsNum >= 1_000_000) viewBoost = 35;
    else if (viewsNum >= 500_000) viewBoost = 25;
    else if (viewsNum >= 100_000) viewBoost = 18;
    else if (viewsNum >= 30_000) viewBoost = 10;
    score += viewBoost;

    // 4. Quality Factor
    const is4K = (item.title || '').toLowerCase().includes('4k') ||
      (item.rating || '').includes('4K') ||
      item.source === 'Eporner 4K';
    const is1080p = (item.rating || '').includes('1080') || (item.quality || '').includes('1080');

    if (qualityPreference === '4k') {
      if (is4K) score += 35;
      else if (is1080p) score += 10;
    } else if (qualityPreference === '1080p') {
      if (is1080p) score += 25;
      if (is4K) score += 20;
    } else {
      if (is4K) score += (profile.qualities['4k'] ? 30 : 20);
      if (is1080p) score += 12;
    }

    // 5. Duration Factor
    const durParts = (item.duration || '0').split(':');
    const durMin = parseInt(durParts[0]) || 0;
    if (durMin >= 10) score += 12;
    else if (durMin >= 5) score += 6;

    // 6. Explicit User Likes Boost (if personalization enabled)
    const itemId = item.url || item.link || item.title;
    if (personalizationEnabled && profile.likes && profile.likes[itemId]) {
      score += 40;
    }

    const isTrending = viewsNum >= 120_000 ||
      (item.title || '').toLowerCase().includes('trending') ||
      (item.title || '').toLowerCase().includes('viral') ||
      score >= 85;

    item.isTrending = isTrending;
    item.is4K = is4K;
    item.parsedViews = viewsNum;
    item.score = score;
    item.dominantCluster = itemClusters[0] || 'general';

    return { item, score, is4K, isTrending, viewsNum, source: item.source, dominantCluster: item.dominantCluster };
  });

  if (algo === 'for-you') {
    scoredList.sort((a, b) => b.score - a.score);

    // Bounded Bandit Exploration modulated by trendingInfluence setting
    let explorationStep = 5; // default 20%
    if (trendingInfluence === 'low') explorationStep = 10; // 10%
    else if (trendingInfluence === 'high') explorationStep = 3; // ~33-40%

    const viralTrending = scoredList.filter(x => x.isTrending || x.viewsNum >= 100_000);
    const tasteExploit = scoredList.filter(x => !viralTrending.includes(x));

    const blended = [];
    let trIdx = 0;
    let tsIdx = 0;

    for (let i = 0; i < scoredList.length; i++) {
      if (i % explorationStep === (explorationStep - 1) && trIdx < viralTrending.length) {
        blended.push(viralTrending[trIdx++]);
      } else if (tsIdx < tasteExploit.length) {
        blended.push(tasteExploit[tsIdx++]);
      } else if (trIdx < viralTrending.length) {
        blended.push(viralTrending[trIdx++]);
      }
    }

    const initialList = blended.length > 0 ? blended : scoredList;

    // Diversity Re-ranking: Avoid more than 2 consecutive same source or cluster (Section 21)
    const diverseList = [];
    const pool = [...initialList];
    while (pool.length > 0) {
      let pickIdx = 0;
      if (diverseList.length >= 2) {
        const last1 = diverseList[diverseList.length - 1];
        const last2 = diverseList[diverseList.length - 2];
        // Look for candidate that doesn't violate consecutive constraints
        const candidateIdx = pool.findIndex(cand =>
          !(cand.source === last1.source && cand.source === last2.source) &&
          !(cand.dominantCluster === last1.dominantCluster && cand.dominantCluster === last2.dominantCluster)
        );
        if (candidateIdx !== -1) pickIdx = candidateIdx;
      }
      diverseList.push(pool.splice(pickIdx, 1)[0]);
    }

    return diverseList.map(x => x.item);
  } else if (algo === 'fresh') {
    return scoredList.sort((a, b) => {
      const vA = a.viewsNum || 0;
      const vB = b.viewsNum || 0;
      const qScoreA = (a.is4K ? 30 : 0) + (a.item.rating?.includes('1080') ? 15 : 0);
      const qScoreB = (b.is4K ? 30 : 0) + (b.item.rating?.includes('1080') ? 15 : 0);
      return (vA - qScoreA * 100) - (vB - qScoreB * 100);
    }).map(x => x.item);
  } else if (algo === 'preferred-sources') {
    const profSources = profile.sources || {};
    return scoredList.sort((a, b) => {
      const srcScoreA = (profSources[a.source] || 0) * 20 + a.score;
      const srcScoreB = (profSources[b.source] || 0) * 20 + b.score;
      return srcScoreB - srcScoreA;
    }).map(x => x.item);
  } else if (algo === 'trending') {
    return scoredList.sort((a, b) => {
      const vScoreA = (a.viewsNum / 1000) + (a.is4K ? 30 : 0) + (a.item.rating?.includes('1080') ? 15 : 0);
      const vScoreB = (b.viewsNum / 1000) + (b.is4K ? 30 : 0) + (b.item.rating?.includes('1080') ? 15 : 0);
      return vScoreB - vScoreA;
    }).map(x => x.item);
  } else if (algo === '4k') {
    return scoredList.sort((a, b) => {
      const pA = (a.is4K ? 100 : 0) + (a.viewsNum / 5000);
      const pB = (b.is4K ? 100 : 0) + (b.viewsNum / 5000);
      return pB - pA;
    }).map(x => x.item);
  } else if (algo === 'top-rated') {
    return scoredList.sort((a, b) => {
      const numA = parseFloat((a.item.rating || '8.0').replace(/[^0-9.]/g, '')) || 8.0;
      const numB = parseFloat((b.item.rating || '8.0').replace(/[^0-9.]/g, '')) || 8.0;
      return (numB * 10000 + b.viewsNum) - (numA * 10000 + a.viewsNum);
    }).map(x => x.item);
  }

  return list;
}

// Algorithm Switcher Pills
document.querySelectorAll('.adult-algo-selector-bar .algo-pill:not(.btn-algo-surprise)').forEach(pill => {
  pill.addEventListener('click', () => {
    document.querySelectorAll('.adult-algo-selector-bar .algo-pill').forEach(p => p.classList.remove('active'));
    pill.classList.add('active');
    window.currentAdultAlgo = pill.dataset.algo || 'for-you';
    window.showToast(`Switched to ${pill.textContent.trim()}`, 'info');

    if (window.rawAdultResults && window.rawAdultResults.length > 0) {
      renderAdultGrid();
    }
    window.searchAdult(window.currentAdultAlgo, window.currentAdultSource);
  });
});

const btnAdultSurprise = document.getElementById('btnAdultSurprise');
btnAdultSurprise?.addEventListener('click', () => {
  window.showToast('🎲 Discovering fresh trending videos from all 18+ hubs...', 'info');
  window.searchAdult('surprise', window.currentAdultSource);
});

// ============================================================================
// SECTION 6: Live Feed Grid Renderer & Infinite Batch Pagination
// ============================================================================

function showAdultUndoToast(item, cardElement, reason = 'item') {
  const toast = document.createElement('div');
  toast.className = 'adult-undo-toast';
  toast.style.cssText = 'position: fixed; bottom: 85px; left: 50%; transform: translateX(-50%); background: rgba(18, 8, 24, 0.95); border: 1px solid var(--accent-magenta); color: #fff; padding: 10px 18px; border-radius: 30px; font-size: 0.82rem; font-weight: 700; display: flex; align-items: center; gap: 12px; z-index: 9999; box-shadow: 0 8px 24px rgba(0,0,0,0.6);';

  const msgText = reason === 'source' ? `Hidden source "${escapeHtml(item.source || '18+')}"` : 'Hidden from recommendations';
  toast.innerHTML = `
    <span>${msgText}</span>
    <button style="background: var(--accent-magenta); color: #fff; border: none; padding: 4px 12px; border-radius: 20px; font-weight: 800; cursor: pointer;">Undo</button>
  `;

  let undone = false;
  const undoBtn = toast.querySelector('button');
  undoBtn.addEventListener('click', () => {
    undone = true;
    const profile = getUserTasteProfileV3();
    if (reason === 'source' && item.source) {
      if (profile.hiddenSources) delete profile.hiddenSources[item.source];
      saveUserTasteProfileV3(profile);
      if (cardElement && cardElement.style) {
        cardElement.style.display = '';
        cardElement.style.opacity = '1';
        cardElement.style.transform = 'scale(1)';
      }
      toast.remove();
      window.showToast(`Source "${item.source}" unhidden`, 'info');
      if (window.rawAdultResults && window.rawAdultResults.length > 0) {
        renderAdultGrid();
      }
    } else {
      const itemId = item.url || item.link || item.title;
      if (profile.negativeItems) delete profile.negativeItems[itemId];
      saveUserTasteProfileV3(profile);
      if (cardElement && cardElement.style) {
        cardElement.style.display = '';
        cardElement.style.opacity = '1';
        cardElement.style.transform = 'scale(1)';
      }
      toast.remove();
      window.showToast('Recommendation restored', 'info');
    }
  });

  document.body.appendChild(toast);
  setTimeout(() => {
    if (!undone && toast.parentNode) {
      toast.style.opacity = '0';
      toast.style.transition = 'opacity 0.3s';
      setTimeout(() => toast.remove(), 300);
    }
  }, 4500);
}

function renderAdultGrid() {
  const targetGrid = document.getElementById('adultGrid') || adultGrid;
  if (!targetGrid) return;
  targetGrid.innerHTML = '';

  const rankedVideos = applySmartAlgorithm(window.rawAdultResults, window.currentAdultAlgo);

  if (rankedVideos.length === 0) {
    targetGrid.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1;">
        <i class="fa-solid fa-film"></i>
        <p>No adult videos found. Try another search or refresh.</p>
      </div>
    `;
    return;
  }

  window.currentAdultPlaylist = rankedVideos;

  const targetResultsCount = document.getElementById('adultResultsCount') || adultResultsCount;
  if (targetResultsCount) {
    targetResultsCount.textContent = `Live HD Feed (${rankedVideos.length})`;
  }

  let renderedCount = 0;
  const BATCH_SIZE = 24;

  window.hasMoreAdultBatches = () => renderedCount < rankedVideos.length;

  function appendBatch() {
    const nextBatch = rankedVideos.slice(renderedCount, renderedCount + BATCH_SIZE);
    if (nextBatch.length === 0) {
      if (!window.isAdultLoadingMore && window.hasMoreAdultStreams && window.rawAdultResults?.length >= 10) {
        window.loadNextAdultPage();
      }
      return;
    }

    // Remove pagination skeleton placeholders before appending real cards
    document.querySelectorAll('.adult-pagination-skeleton').forEach(el => el.remove());

    const profile = getUserTasteProfileV3();

    nextBatch.forEach(item => {
      if (!isSafeAdultCandidate(item)) return;

      const card = document.createElement('div');
      card.className = 'yt-video-card-2026 adult-card-enter';
      const is4K = item.is4K || item.title?.toLowerCase().includes('4k') || item.rating?.includes('4K') || item.source === 'Eporner 4K';
      const viewsNum = item.parsedViews || parseAdultViewCount(item.views);
      const isTrending = item.isTrending || viewsNum >= 100_000 || (item.title || '').toLowerCase().includes('trending') || (item.title || '').toLowerCase().includes('viral');
      const fallbackThumb = 'data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22300%22%20height%3D%22200%22%20viewBox%3D%220%200%20300%20200%22%3E%3Crect%20fill%3D%22%23121526%22%20width%3D%22100%25%22%20height%3D%22100%25%22%2F%3E%3Ctext%20fill%3D%22%23ff007f%22%20font-family%3D%22sans-serif%22%20font-size%3D%2216%22%20font-weight%3D%22bold%22%20x%3D%2250%25%22%20y%3D%2250%25%22%20dominant-baseline%3D%22middle%22%20text-anchor%3D%22middle%22%3E18%2B%20STREAM%3C%2Ftext%3E%3C%2Fsvg%3E';
      let cleanThumb = (item.thumbnail || fallbackThumb).replace(/THUMBNUM/g, '1');
      if (cleanThumb.startsWith('//')) cleanThumb = 'https:' + cleanThumb;

      const itemId = item.url || item.link || item.title;
      const isLiked = Boolean(profile.likes && profile.likes[itemId]);

      card.innerHTML = `
        <div class="yt-thumb-wrap">
          <img src="${escapeHtml(cleanThumb)}" alt="${escapeHtml(item.title)}" loading="lazy" decoding="async" onerror="this.onerror=null;this.src='${fallbackThumb}'">
          <span class="yt-dur-pill">${escapeHtml(item.duration || 'HD')}</span>
          ${is4K ? '<span class="yt-4k-pill"><i class="fa-solid fa-crown"></i> 4K UHD</span>' : `<span class="yt-src-pill">${escapeHtml(item.source || '18+')}</span>`}
          ${isTrending ? '<span class="yt-trending-pill"><i class="fa-solid fa-fire"></i> Trending</span>' : ''}
        </div>
        <div class="yt-card-info">
          <div class="yt-card-title">${escapeHtml(item.title)}</div>
          <div class="yt-card-meta">
            <span style="color: var(--accent-magenta); font-weight: 700;">${escapeHtml(item.source || 'Direct Stream')}</span>
            <span class="yt-card-views"><i class="fa-solid fa-fire" style="color: #ff007f;"></i> ${escapeHtml(item.views || 'Hot')}</span>
          </div>
        </div>
        <!-- Card Action Bar: Download, Drive Leech, and Feedback Buttons -->
        <div class="yt-card-actions-bar">
          <div class="yt-card-action-btns">
            <button class="btn-card-action btn-card-dl" title="Direct Download to Device">
              <i class="fa-solid fa-download"></i>
              <span>Download</span>
            </button>
            <button class="btn-card-action btn-card-drive" title="Upload to Google Drive (0 MB Data Leech)">
              <i class="fa-solid fa-cloud-arrow-up"></i>
              <span>Drive</span>
            </button>
          </div>
          <div class="yt-card-feedback-btns">
            <button class="btn-card-feedback btn-card-like ${isLiked ? 'active-like' : ''}" title="Like video">
              <i class="fa-solid fa-heart"></i>
            </button>
            <button class="btn-card-feedback btn-feedback-dislike btn-card-dislike" title="Dislike">
              <i class="fa-solid fa-thumbs-down"></i>
            </button>
            <button class="btn-card-feedback btn-feedback-ban btn-card-not-interested" title="Not Interested">
              <i class="fa-solid fa-ban"></i>
            </button>
            <button class="btn-card-feedback btn-feedback-hide-source btn-card-hide-source" title="Hide all videos from this source">
              <i class="fa-solid fa-eye-slash"></i>
            </button>
          </div>
        </div>
      `;

      // Click card (Thumbnail or Info) -> Instant Cinema Watch Mode
      card.addEventListener('click', (e) => {
        if (e.target.closest('.btn-card-action') || e.target.closest('.btn-card-feedback')) return;
        recordAdultEvent('CLICK', item);
        window.openAdultWatchMode(item);
      });

      // ⬇️ Direct Download Button
      card.querySelector('.btn-card-dl')?.addEventListener('click', async (e) => {
        e.stopPropagation();
        recordAdultEvent('DOWNLOAD', item);
        const targetUrl = item.url || item.link || '';
        window.showToast('Resolving direct video stream for download...', 'info');
        try {
          let resolved = adultPreloadCache.get(targetUrl);
          if (!resolved && window.Capacitor?.Plugins?.NativeExtractor?.resolveAdultStream) {
            const nRes = await window.Capacitor.Plugins.NativeExtractor.resolveAdultStream({ url: targetUrl });
            resolved = nRes?.resolved || nRes?.stream || nRes;
          }
          const dl = resolved?.downloadUrl || resolved?.streamUrl || targetUrl;
          const qualities = resolved?.qualities;
          if (qualities && qualities.length > 1 && window.openResolutionPickerModal) {
            window.openResolutionPickerModal({
              title: resolved?.title || item.title,
              qualities: qualities,
              defaultDownloadUrl: dl
            });
          } else {
            window.triggerDirectDownload(dl, `${(resolved?.title || item.title).replace(/[/\\?%*:|"<>]/g, '_')}.mp4`, 'adult');
          }
        } catch (_) {
          window.triggerDirectDownload(targetUrl, `${item.title.replace(/[/\\?%*:|"<>]/g, '_')}.mp4`, 'adult');
        }
      });

      // ☁️ Direct Google Drive Leech Upload Button
      card.querySelector('.btn-card-drive')?.addEventListener('click', async (e) => {
        e.stopPropagation();
        if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
          if (window.showInApp404) {
            window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Google Drive upload pipeline is disabled or unauthorized under the active license. Please activate a PRO License Key.');
          }
          return;
        }
        recordAdultEvent('DOWNLOAD', item);
        const targetUrl = item.url || item.link || '';
        window.showToast('Connecting to Cloud Drive Leech...', 'info');
        try {
          let resolved = adultPreloadCache.get(targetUrl);
          if (!resolved && window.Capacitor?.Plugins?.NativeExtractor?.resolveAdultStream) {
            const nRes = await window.Capacitor.Plugins.NativeExtractor.resolveAdultStream({ url: targetUrl });
            resolved = nRes?.resolved || nRes?.stream || nRes;
          }
          const dl = resolved?.downloadUrl || resolved?.streamUrl || targetUrl;
          if (window.startCloudTransfer) {
            window.startCloudTransfer({
              title: `${resolved?.title || item.title}`,
              url: dl,
              type: 'adult',
              quality: 'HD'
            });
          }
        } catch (_) {
          if (window.startCloudTransfer) {
            window.startCloudTransfer({
              title: item.title,
              url: targetUrl,
              type: 'adult',
              quality: 'HD'
            });
          }
        }
      });

      // Like Button
      const btnLike = card.querySelector('.btn-card-like');
      btnLike?.addEventListener('click', (e) => {
        e.stopPropagation();
        const prof = getUserTasteProfileV3();
        const isCurrentlyLiked = Boolean(prof.likes && prof.likes[itemId]);
        if (isCurrentlyLiked) {
          delete prof.likes[itemId];
          btnLike.classList.remove('active-like');
          saveUserTasteProfileV3(prof);
          window.showToast('Like removed', 'info');
        } else {
          recordAdultEvent('LIKE', item);
          btnLike.classList.add('active-like');
          window.showToast('❤️ Added to preferred recommendations', 'success');
        }
      });

      // Dislike Button
      card.querySelector('.btn-card-dislike')?.addEventListener('click', (e) => {
        e.stopPropagation();
        recordAdultEvent('DISLIKE', item);
        card.style.transition = 'opacity 0.3s, transform 0.3s';
        card.style.opacity = '0.35';
        window.showToast('👎 Video disliked. Adjusting recommendations...', 'info');
      });

      // Not Interested Button
      card.querySelector('.btn-card-not-interested')?.addEventListener('click', (e) => {
        e.stopPropagation();
        recordAdultEvent('NOT_INTERESTED', item);
        card.style.transition = 'opacity 0.3s, transform 0.3s';
        card.style.opacity = '0';
        card.style.transform = 'scale(0.9)';
        setTimeout(() => {
          card.style.display = 'none';
          showAdultUndoToast(item, card, 'item');
        }, 300);
      });

      // Hide Source Button
      card.querySelector('.btn-card-hide-source')?.addEventListener('click', (e) => {
        e.stopPropagation();
        recordAdultEvent('HIDE_SOURCE', item);
        card.style.transition = 'opacity 0.3s, transform 0.3s';
        card.style.opacity = '0';
        card.style.transform = 'scale(0.9)';
        setTimeout(() => {
          card.style.display = 'none';
          showAdultUndoToast(item, card, 'source');
        }, 300);
      });

      targetGrid.appendChild(card);
    });

    renderedCount += nextBatch.length;
  }

  window.appendNextAdultBatch = appendBatch;
  appendBatch();
  setupAdultInfiniteScrollObserver();
}

// ⚡ Dynamic Continuous Pagination: Loads next batch of trending/fresh videos
window.loadNextAdultPage = async function () {
  if (window.isAdultLoadingMore || !window.hasMoreAdultStreams) return;
  window.isAdultLoadingMore = true;

  const targetGrid = document.getElementById('adultGrid') || adultGrid;
  // Insert 4 skeleton placeholder shimmer cards at bottom
  if (targetGrid) {
    document.querySelectorAll('.adult-pagination-skeleton').forEach(el => el.remove());
    for (let s = 0; s < 4; s++) {
      const skel = document.createElement('div');
      skel.className = 'skeleton-card-adult adult-pagination-skeleton';
      skel.innerHTML = '<div class="skeleton-thumb-wrap"></div><div class="skeleton-text-line"></div><div class="skeleton-text-line short"></div>';
      targetGrid.appendChild(skel);
    }
  }

  const nextPage = window.currentAdultPage + 1;
  const targetQ = window.lastAdultSearchQuery || 'trending';
  const targetSrc = window.currentAdultSource || 'all';

  try {
    let newItems = [];
    const isNativeApp = !!(window.Capacitor?.isNativePlatform?.() || window.Capacitor?.Plugins?.NativeExtractor);

    if (window.Capacitor?.Plugins?.NativeExtractor?.searchAdult) {
      try {
        const nRes = await withAdultTimeout(
          window.Capacitor.Plugins.NativeExtractor.searchAdult({ query: targetQ, page: nextPage, source: targetSrc }),
          6000
        );
        if (nRes && Array.isArray(nRes.results) && nRes.results.length > 0) {
          newItems = nRes.results;
        }
      } catch (err) {
        console.warn('[Adult Feed] Native pagination error:', err);
      }
    }

    if (newItems.length === 0 && window.StandaloneEngine?.searchAdult) {
      try {
        newItems = await withAdultTimeout(window.StandaloneEngine.searchAdult(targetQ, nextPage, targetSrc), 4000);
      } catch (_) { }
    }

    // Clean up pagination skeletons
    document.querySelectorAll('.adult-pagination-skeleton').forEach(el => el.remove());

    if (newItems && newItems.length > 0) {
      window.currentAdultPage = nextPage;
      const formatted = newItems.map(it => ({
        ...it,
        url: it.url || it.link || '',
        link: it.link || it.url || '',
        thumbnail: it.thumbnail || it.poster || ''
      }));

      const uniqueNew = [];
      formatted.forEach(it => {
        const norm = (it.url || it.link || '').toLowerCase().replace(/\/+$/, '');
        if (norm && !window.seenAdultUrls.has(norm)) {
          window.seenAdultUrls.add(norm);
          uniqueNew.push(it);
        }
      });

      if (uniqueNew.length > 0) {
        window.rawAdultResults.push(...uniqueNew);
        window.showToast(`🔥 +${uniqueNew.length} fresh streams loaded!`, 'info');
        renderAdultGrid();
      } else if (nextPage < 10) {
        window.currentAdultPage = nextPage;
        window.isAdultLoadingMore = false;
        return window.loadNextAdultPage();
      } else {
        window.hasMoreAdultStreams = false;
      }
    } else {
      window.hasMoreAdultStreams = false;
    }
  } catch (err) {
    console.warn('Error loading more adult items:', err);
    document.querySelectorAll('.adult-pagination-skeleton').forEach(el => el.remove());
  } finally {
    window.isAdultLoadingMore = false;
  }
};

// Hardware-Accelerated 60fps Infinite Scroll Sentinel Observer
let adultSentinelObserver = null;

function setupAdultInfiniteScrollObserver() {
  if (adultSentinelObserver) {
    adultSentinelObserver.disconnect();
    adultSentinelObserver = null;
  }

  const sentinel = document.getElementById('adultScrollSentinel');
  if (!sentinel) return;

  adultSentinelObserver = new IntersectionObserver((entries) => {
    const entry = entries[0];
    if (entry && entry.isIntersecting) {
      if (typeof window.hasMoreAdultBatches === 'function' && window.hasMoreAdultBatches()) {
        window.appendNextAdultBatch();
      } else if (!window.isAdultLoadingMore && window.hasMoreAdultStreams && window.rawAdultResults?.length >= 10) {
        window.loadNextAdultPage();
      }
    }
  }, {
    root: null,
    rootMargin: '650px 0px', // Trigger 650px before bottom for zero-latency scroll
    threshold: 0
  });

  adultSentinelObserver.observe(sentinel);
}

// Scroll to Top Floating Action Button
const btnAdultScrollTop = document.getElementById('btnAdultScrollTop');
btnAdultScrollTop?.addEventListener('click', () => {
  window.scrollTo({ top: 0, behavior: 'smooth' });
});

window.addEventListener('scroll', () => {
  if (adultBrowseView && !adultBrowseView.classList.contains('hidden')) {
    if (btnAdultScrollTop) {
      if (window.scrollY > 700) {
        btnAdultScrollTop.classList.remove('hidden');
      } else {
        btnAdultScrollTop.classList.add('hidden');
      }
    }
  }
}, { passive: true });

// ============================================================================
// SECTION 7: Adult Search API & Zero-Latency Memory Cache (Sections 42, 53, 54)
// ============================================================================

const clientAdultFeedCache = new Map();
const ADULT_FEED_CACHE_KEY = 'cdl_adult_feed_cache_v2';
const ADULT_FEED_CACHE_TTL_MS = 10 * 60 * 1000;

function readAdultFeedCache(cacheKey) {
  if (clientAdultFeedCache.has(cacheKey)) return clientAdultFeedCache.get(cacheKey);
  try {
    const stored = JSON.parse(localStorage.getItem(ADULT_FEED_CACHE_KEY) || '{}');
    const entry = stored[cacheKey];
    if (entry && Date.now() - entry.savedAt < ADULT_FEED_CACHE_TTL_MS && Array.isArray(entry.items)) {
      clientAdultFeedCache.set(cacheKey, entry.items);
      return entry.items;
    }
  } catch (_) { }
  return null;
}

function writeAdultFeedCache(cacheKey, items) {
  clientAdultFeedCache.set(cacheKey, items);
  try {
    const stored = JSON.parse(localStorage.getItem(ADULT_FEED_CACHE_KEY) || '{}');
    stored[cacheKey] = { savedAt: Date.now(), items: items.slice(0, 60) };
    localStorage.setItem(ADULT_FEED_CACHE_KEY, JSON.stringify(stored));
  } catch (_) { }
}

function withAdultTimeout(promise, timeoutMs = 4500) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('Adult source timeout')), timeoutMs))
  ]);
}

window.searchAdult = async function (query, source = 'all') {
  // 1. Age Gate Verification (Section 3)
  if (!checkAdultGateState()) {
    return;
  }

  if (!query || !query.trim()) query = 'for-you';
  const cleanQ = query.trim().toLowerCase();

  // 2. Prohibited Content Safety Check (Section 2)
  if (!isSafeAdultQuery(cleanQ)) {
    window.showToast('Restricted or prohibited search query rejected.', 'error');
    return;
  }

  const isForYou = (cleanQ === 'for-you' || cleanQ === 'popular');

  window.currentAdultPage = 1;
  window.hasMoreAdultStreams = true;
  window.seenAdultUrls.clear();

  // 3. Request ID tracking for race condition prevention (Section 53)
  const thisRequestId = ++currentAdultSearchRequestId;

  let searchTargetQ = cleanQ;
  if (isForYou) {
    window.currentAdultAlgo = 'for-you';
    searchTargetQ = getSmartForYouQuery();
    window.lastAdultSearchQuery = 'trending';
  } else {
    window.lastAdultSearchQuery = cleanQ;
  }

  // 4. Update Horizontal Recommendation Rails
  renderContinueWatchingRail();
  renderBecauseYouWatchedRail();

  const cacheKey = `${source}:${isForYou ? 'for-you:' + searchTargetQ : cleanQ}`;

  if (adultSectionTitle) {
    if (isForYou) {
      adultSectionTitle.innerHTML = '<i class="fa-solid fa-fire" style="color: var(--accent-magenta);"></i> 18+ Trending & HD Releases';
    } else {
      adultSectionTitle.innerHTML = `<i class="fa-solid fa-fire" style="color: var(--accent-magenta);"></i> Results for "${escapeHtml(query)}"`;
    }
  }

  // 0ms Cache-First Instant Render
  const cached = readAdultFeedCache(cacheKey);
  if (cached && cached.length > 0) {
    window.rawAdultResults = cached.filter(isSafeAdultCandidate);
    cached.forEach(it => {
      const norm = (it.url || it.link || '').toLowerCase().replace(/\/+$/, '');
      if (norm) window.seenAdultUrls.add(norm);
    });
    renderAdultGrid();
    if (adultLoading) adultLoading.classList.add('hidden');
  } else if (window.isAppOffline || !navigator.onLine) {
    if (adultLoading) adultLoading.classList.add('hidden');
    if (adultGrid) {
      adultGrid.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1; padding: 40px 20px; text-align: center;">
          <i class="fa-solid fa-plane-slash" style="font-size: 2.8rem; color: #ff4757; margin-bottom: 12px;"></i>
          <h3>Offline Mode Active</h3>
          <p style="color: var(--text-secondary); font-size: 0.85rem; max-width: 400px; margin: 0 auto 16px;">
            Internet is required to browse online feeds. Your downloaded items and continue watching rail remain fully playable offline.
          </p>
          <button class="btn btn-secondary" onclick="window.switchTab ? window.switchTab('downloads') : null" style="display: inline-flex; align-items: center; gap: 8px;">
            <i class="fa-solid fa-circle-down"></i> Open Downloads
          </button>
        </div>
      `;
    }
    return;
  } else {
    if (adultLoading) adultLoading.classList.remove('hidden');
    // Skeleton Shimmer Loading Cards (Section 42)
    if (adultGrid) {
      adultGrid.innerHTML = `
        <div class="skeleton-card-adult"><div class="skeleton-thumb-wrap"></div><div class="skeleton-text-line"></div><div class="skeleton-text-line short"></div></div>
        <div class="skeleton-card-adult"><div class="skeleton-thumb-wrap"></div><div class="skeleton-text-line"></div><div class="skeleton-text-line short"></div></div>
        <div class="skeleton-card-adult"><div class="skeleton-thumb-wrap"></div><div class="skeleton-text-line"></div><div class="skeleton-text-line short"></div></div>
        <div class="skeleton-card-adult"><div class="skeleton-thumb-wrap"></div><div class="skeleton-text-line"></div><div class="skeleton-text-line short"></div></div>
      `;
    }
  }

  try {
    let results = [];

    async function fetchFromExtractors(targetQ) {
      let r = [];
      const isNativeApp = !!(window.Capacitor?.isNativePlatform?.() || window.Capacitor?.Plugins?.NativeExtractor);

      // ⚡ 1. Direct Kotlin Native Extractor (Fast-Yield & Censorship-Bypassed)
      if (window.Capacitor?.Plugins?.NativeExtractor?.searchAdult) {
        try {
          const nRes = await withAdultTimeout(
            window.Capacitor.Plugins.NativeExtractor.searchAdult({ query: targetQ, page: 1, source }),
            5000
          );
          if (nRes && Array.isArray(nRes.results) && nRes.results.length > 0) {
            return nRes.results;
          }
        } catch (_) { }
      }

      // ⚡ 2. Standalone Client-Side Scraper Fallback
      if (r.length === 0 && window.StandaloneEngine?.searchAdult) {
        try {
          r = await withAdultTimeout(window.StandaloneEngine.searchAdult(targetQ, 1, source), 4000);
        } catch (_) { }
      }
      return r;
    }

    results = await fetchFromExtractors(searchTargetQ);

    // Guard against race conditions: Ignore if a newer search request was initiated
    if (thisRequestId !== currentAdultSearchRequestId) {
      return;
    }

    if (results.length > 0) {
      window.rawAdultResults = results
        .filter(isSafeAdultCandidate)
        .map(it => ({
          ...it,
          url: it.url || it.link || '',
          link: it.link || it.url || '',
          thumbnail: it.thumbnail || it.poster || ''
        }));

      window.rawAdultResults.forEach(it => {
        const norm = (it.url || it.link || '').toLowerCase().replace(/\/+$/, '');
        if (norm) window.seenAdultUrls.add(norm);
      });

      if (cleanQ !== 'surprise') {
        writeAdultFeedCache(cacheKey, window.rawAdultResults);
      }
      renderAdultGrid();
    } else if (!cached || cached.length === 0) {
      if (adultGrid) {
        adultGrid.innerHTML = `
          <div class="empty-state" style="grid-column: 1 / -1;">
            <i class="fa-solid fa-circle-exclamation"></i>
            <p>No streams found. Tap refresh or check connection.</p>
          </div>
        `;
      }
    }
  } catch (e) {
    if (thisRequestId !== currentAdultSearchRequestId) return;
    if (!cached || cached.length === 0) {
      if (adultGrid) {
        adultGrid.innerHTML = `
          <div class="empty-state" style="grid-column: 1 / -1;">
            <i class="fa-solid fa-circle-exclamation"></i>
            <p>Stream feed unavailable. Please try again.</p>
          </div>
        `;
      }
    }
  } finally {
    if (thisRequestId === currentAdultSearchRequestId && adultLoading) {
      adultLoading.classList.add('hidden');
    }
  }
};

// ============================================================================
// SECTION 8: Direct HLS / MP4 Stream Resolver & LocalMediaProxy Router
// ============================================================================

async function playDirectAdultMediaUrl(mediaUrl, fallbackEmbedUrl, savedPosition = 0, autoPlay = true) {
  if (currentAdultHls) {
    try {
      currentAdultHls.destroy();
    } catch (_) { }
    currentAdultHls = null;
  }

  const isEmbedOnly = !mediaUrl ||
    mediaUrl.includes('/embed') ||
    mediaUrl.includes('xembed') ||
    mediaUrl.includes('embed.php') ||
    mediaUrl.includes('pornhub.com/embed') ||
    mediaUrl.includes('xvideos.com/embed') ||
    mediaUrl.includes('xnxx.com/embed');

  if (isEmbedOnly) {
    if (adultHtmlVideoPlayer) {
      try { adultHtmlVideoPlayer.pause(); } catch (_) { }
      adultHtmlVideoPlayer.removeAttribute('src');
      adultHtmlVideoPlayer.classList.add('hidden');
    }
    if (adultIframePlayer) {
      let embedTarget = mediaUrl || fallbackEmbedUrl || 'about:blank';
      if (embedTarget.startsWith('http') && window.Capacitor?.Plugins?.NativePlayer?.getProxiedEmbedUrl) {
        try {
          const p = await window.Capacitor.Plugins.NativePlayer.getProxiedEmbedUrl({ url: embedTarget });
          if (p && p.url) embedTarget = p.url;
        } catch (_) { }
      }
      adultIframePlayer.src = embedTarget;
      adultIframePlayer.classList.remove('hidden');
      adultIframePlayer.onload = () => {
        if (typeof window.hideAdultTransitionBackdrop === 'function') window.hideAdultTransitionBackdrop();
      };
      setTimeout(() => {
        if (typeof window.hideAdultTransitionBackdrop === 'function') window.hideAdultTransitionBackdrop();
      }, 1000);
    }
    return;
  }

  let targetPlayUrl = mediaUrl;
  const needsProxy = targetPlayUrl && !targetPlayUrl.includes('127.0.0.1') &&
    (targetPlayUrl.includes('phncdn.com') ||
      targetPlayUrl.includes('pornhub.com') ||
      targetPlayUrl.includes('xhcdn.com') ||
      targetPlayUrl.includes('xhamster.com') ||
      targetPlayUrl.includes('rdtcdn.com') ||
      targetPlayUrl.includes('redtube.com') ||
      targetPlayUrl.includes('xvideos.com') ||
      targetPlayUrl.includes('xvideos-cdn') ||
      targetPlayUrl.includes('cdn77') ||
      targetPlayUrl.includes('xnxx.com') ||
      targetPlayUrl.includes('eporner.com'));

  if (needsProxy && window.Capacitor?.Plugins?.NativePlayer?.getProxiedStreamUrl && !targetPlayUrl.includes('127.0.0.1') && !targetPlayUrl.includes('blob:') && !targetPlayUrl.includes('data:')) {
    try {
      const pRes = await window.Capacitor.Plugins.NativePlayer.getProxiedStreamUrl({ url: targetPlayUrl });
      if (pRes && (pRes.url || pRes.proxiedUrl)) {
        targetPlayUrl = pRes.url || pRes.proxiedUrl;
      }
    } catch (_) { }
  }

  if (adultIframePlayer) {
    adultIframePlayer.src = 'about:blank';
    adultIframePlayer.classList.add('hidden');
  }

  if (!adultHtmlVideoPlayer) return;
  adultHtmlVideoPlayer.classList.remove('hidden');
  adultHtmlVideoPlayer.muted = false;

  // Wire Truthful Watch Metrics & Session Lifecycle (Sections 8, 9, 10, 26, 28)
  adultHtmlVideoPlayer.ontimeupdate = () => {
    updateAdultWatchProgress(adultHtmlVideoPlayer.currentTime, adultHtmlVideoPlayer.duration);
  };
  adultHtmlVideoPlayer.onpause = () => {
    if (currentAdultWatchSession) currentAdultWatchSession.isPaused = true;
  };
  adultHtmlVideoPlayer.onplay = () => {
    if (currentAdultWatchSession) {
      currentAdultWatchSession.isPaused = false;
      currentAdultWatchSession.lastActiveTime = Date.now();
    }
  };
  adultHtmlVideoPlayer.onended = () => {
    if (currentAdultWatchSession) {
      currentAdultWatchSession.hasTriggeredComplete = true;
    }
    finalizeAdultWatchSession();

    let nextItem = null;
    const playlist = window.currentAdultPlaylist || [];
    const currentIdx = typeof window.currentAdultIndex === 'number' ? window.currentAdultIndex : -1;
    if (playlist.length > 0 && currentIdx >= 0 && currentIdx + 1 < playlist.length) {
      nextItem = playlist[currentIdx + 1];
    } else if (window.currentAdultRelatedList && window.currentAdultRelatedList.length > 0) {
      nextItem = window.currentAdultRelatedList[0];
    }
    if (nextItem) {
      window.showAdultUpNextOverlay(nextItem);
    }
  };

  const decodedPlayUrl = (() => {
    try { return decodeURIComponent(targetPlayUrl); } catch (_) { return targetPlayUrl; }
  })();
  const isHls = targetPlayUrl.includes('.m3u8') ||
    decodedPlayUrl.includes('.m3u8') ||
    decodedPlayUrl.includes('/hls/') ||
    decodedPlayUrl.includes('m3u8');

  if (isHls) {
    try { await window.ensureHlsLoaded(); } catch (_) { }
  }

  if (isHls && window.Hls && window.Hls.isSupported()) {
    const hls = new window.Hls({
      enableWorker: true,
      lowLatencyMode: true,
      backBufferLength: 20,
      maxBufferLength: 20,
      maxMaxBufferLength: 40,
      maxBufferSize: 30 * 1000 * 1000,
      progressive: true,
      startFragPrefetch: true,
      autoStartLoad: true,
      capLevelToPlayerSize: false,
      nudgeOffset: 0.1,
      nudgeMaxRetry: 5,
      manifestLoadingTimeOut: 8000,
      manifestLoadingMaxRetry: 3,
      levelLoadingTimeOut: 8000,
      fragLoadingTimeOut: 10000
    });
    currentAdultHls = hls;

    hls.loadSource(targetPlayUrl);
    hls.attachMedia(adultHtmlVideoPlayer);

    hls.on(window.Hls.Events.MANIFEST_PARSED, () => {
      if (savedPosition > 0 && Number.isFinite(savedPosition)) {
        adultHtmlVideoPlayer.currentTime = savedPosition;
      }
      if (autoPlay) {
        adultHtmlVideoPlayer.play().catch(err => {
          console.warn('[Adult Player] HLS Autoplay notice, trying muted:', err.message);
          adultHtmlVideoPlayer.muted = true;
          adultHtmlVideoPlayer.play().catch(() => { });
        });
      }
    });

    let hlsErrorCount = 0;
    hls.on(window.Hls.Events.ERROR, async (event, data) => {
      if (data.fatal) {
        hlsErrorCount++;
        switch (data.type) {
          case window.Hls.ErrorTypes.NETWORK_ERROR:
            if (hlsErrorCount < 2) {
              hls.startLoad();
            } else if (fallbackEmbedUrl && adultIframePlayer) {
              try { hls.destroy(); } catch (_) { }
              currentAdultHls = null;
              adultHtmlVideoPlayer.classList.add('hidden');
              let embedTarget = fallbackEmbedUrl;
              if (embedTarget.startsWith('http') && window.Capacitor?.Plugins?.NativePlayer?.getProxiedEmbedUrl) {
                try {
                  const p = await window.Capacitor.Plugins.NativePlayer.getProxiedEmbedUrl({ url: embedTarget });
                  if (p && p.url) embedTarget = p.url;
                } catch (_) { }
              }
              adultIframePlayer.src = embedTarget;
              adultIframePlayer.classList.remove('hidden');
              adultIframePlayer.onload = () => {
                if (typeof window.hideAdultTransitionBackdrop === 'function') window.hideAdultTransitionBackdrop();
              };
            }
            break;
          case window.Hls.ErrorTypes.MEDIA_ERROR:
            hls.recoverMediaError();
            break;
          default:
            if (fallbackEmbedUrl && adultIframePlayer) {
              try { hls.destroy(); } catch (_) { }
              currentAdultHls = null;
              adultHtmlVideoPlayer.classList.add('hidden');
              let embedTarget = fallbackEmbedUrl;
              if (embedTarget.startsWith('http') && window.Capacitor?.Plugins?.NativePlayer?.getProxiedEmbedUrl) {
                try {
                  const p = await window.Capacitor.Plugins.NativePlayer.getProxiedEmbedUrl({ url: embedTarget });
                  if (p && p.url) embedTarget = p.url;
                } catch (_) { }
              }
              adultIframePlayer.src = embedTarget;
              adultIframePlayer.classList.remove('hidden');
              adultIframePlayer.onload = () => {
                if (typeof window.hideAdultTransitionBackdrop === 'function') window.hideAdultTransitionBackdrop();
              };
            }
            break;
        }
      }
    });
  } else {
    adultHtmlVideoPlayer.src = targetPlayUrl;
    adultHtmlVideoPlayer.load();

    let playTriggered = false;
    const restoreHandler = () => {
      try {
        if (savedPosition > 0 && Number.isFinite(savedPosition)) {
          adultHtmlVideoPlayer.currentTime = savedPosition;
        }
      } catch (_) { }
      if (autoPlay && !playTriggered) {
        playTriggered = true;
        adultHtmlVideoPlayer.play().catch(err => {
          console.warn('[Adult Player] Direct autoplay notice, trying muted:', err);
          adultHtmlVideoPlayer.muted = true;
          adultHtmlVideoPlayer.play().catch(() => { });
        });
      }
    };
    adultHtmlVideoPlayer.addEventListener('loadedmetadata', restoreHandler, { once: true });
    adultHtmlVideoPlayer.addEventListener('canplay', restoreHandler, { once: true });

    adultHtmlVideoPlayer.onerror = async () => {
      if (typeof window.hideAdultTransitionBackdrop === 'function') window.hideAdultTransitionBackdrop();
      if (fallbackEmbedUrl && adultIframePlayer) {
        adultHtmlVideoPlayer.classList.add('hidden');
        let embedTarget = fallbackEmbedUrl;
        if (embedTarget.startsWith('http') && window.Capacitor?.Plugins?.NativePlayer?.getProxiedEmbedUrl) {
          try {
            const p = await window.Capacitor.Plugins.NativePlayer.getProxiedEmbedUrl({ url: embedTarget });
            if (p && p.url) embedTarget = p.url;
          } catch (_) { }
        }
        adultIframePlayer.src = embedTarget;
        adultIframePlayer.classList.remove('hidden');
        adultIframePlayer.onload = () => {
          if (typeof window.hideAdultTransitionBackdrop === 'function') window.hideAdultTransitionBackdrop();
        };
      }
    };
  }
}

// ============================================================================
// SECTION 9: Cinema Watch Mode Orchestrator & Quality Controller
// ============================================================================

window.openAdultWatchMode = async function (item) {
  startAdultWatchSession(item);
  const resumePosition = (item && item.resumePosition && item.resumePosition > 0) ? item.resumePosition : 0;

  if (adultBrowseView) adultBrowseView.classList.add('hidden');
  if (adultWatchView) adultWatchView.classList.remove('hidden');
  window.scrollTo({ top: 0, behavior: 'smooth' });
  const theaterEl = document.getElementById('adultTheaterContainer');
  if (theaterEl) {
    theaterEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  window.cancelAdultUpNext();

  if (currentAdultHls) {
    try { currentAdultHls.destroy(); } catch (_) { }
    currentAdultHls = null;
  }

  if (!window.currentAdultPlaylist || window.currentAdultPlaylist.length === 0) {
    window.currentAdultPlaylist = (window.rawAdultResults && window.rawAdultResults.length > 0) ? [...window.rawAdultResults] : [item];
  }
  const foundIdx = window.currentAdultPlaylist.findIndex(p => (p.url && p.url === item.url) || (p.title && p.title === item.title));
  if (foundIdx !== -1) {
    window.currentAdultIndex = foundIdx;
  } else if (window.currentAdultRelatedList && window.currentAdultRelatedList.some(p => (p.url && p.url === item.url) || (p.title && p.title === item.title))) {
    const relIdx = window.currentAdultRelatedList.findIndex(p => (p.url && p.url === item.url) || (p.title && p.title === item.title));
    window.currentAdultPlaylist = [...window.currentAdultRelatedList];
    window.currentAdultIndex = relIdx;
  } else {
    window.currentAdultPlaylist.unshift(item);
    window.currentAdultIndex = 0;
  }

  window.setAdultFitMode(adultFitMode);

  // Wire Controls & Floating HUD
  const btnAdultOverlayPrev = document.getElementById('btnAdultOverlayPrev');
  const btnAdultWatchPrev = document.getElementById('btnAdultWatchPrev');
  if (btnAdultOverlayPrev) btnAdultOverlayPrev.onclick = () => window.playPrevAdultVideo();
  if (btnAdultWatchPrev) btnAdultWatchPrev.onclick = () => window.playPrevAdultVideo();

  const btnAdultOverlayNext = document.getElementById('btnAdultOverlayNext');
  const btnAdultWatchNext = document.getElementById('btnAdultWatchNext');
  if (btnAdultOverlayNext) btnAdultOverlayNext.onclick = () => window.playNextAdultVideo();
  if (btnAdultWatchNext) btnAdultWatchNext.onclick = () => window.playNextAdultVideo();

  const btnAdultOverlayFullscreen = document.getElementById('btnAdultOverlayFullscreen');
  const btnAdultWatchFullscreen = document.getElementById('btnAdultWatchFullscreen');
  const btnAdultExitFullscreen = document.getElementById('btnAdultExitFullscreen');
  if (btnAdultOverlayFullscreen) btnAdultOverlayFullscreen.onclick = () => window.toggleAdultFullscreen();
  if (btnAdultWatchFullscreen) btnAdultWatchFullscreen.onclick = () => window.toggleAdultFullscreen();
  if (btnAdultExitFullscreen) btnAdultExitFullscreen.onclick = () => window.toggleAdultFullscreen();

  const btnCancelUpNext = document.getElementById('btnCancelUpNext');
  const btnPlayUpNextNow = document.getElementById('btnPlayUpNextNow');
  if (btnCancelUpNext) btnCancelUpNext.onclick = () => window.cancelAdultUpNext();
  if (btnPlayUpNextNow) btnPlayUpNextNow.onclick = () => window.playNextAdultVideo();

  const targetUrl = item.url || item.link || '';

  // 1. Cinematic Transition Backdrop with Blur Poster (Section 9)
  const transitionBackdrop = document.getElementById('adultTransitionBackdrop');
  if (transitionBackdrop) {
    let cleanThumb = (item.thumbnail || item.poster || '').replace(/THUMBNUM/g, '1');
    if (cleanThumb.startsWith('//')) cleanThumb = 'https:' + cleanThumb;
    if (cleanThumb) {
      transitionBackdrop.style.backgroundImage = `url("${escapeHtml(cleanThumb)}")`;
      transitionBackdrop.classList.remove('hidden');
      transitionBackdrop.classList.add('active');
    }
  }

  const hideBackdrop = () => {
    if (transitionBackdrop) {
      transitionBackdrop.classList.remove('active');
      setTimeout(() => transitionBackdrop.classList.add('hidden'), 350);
    }
  };
  window.hideAdultTransitionBackdrop = hideBackdrop;

  adultHtmlVideoPlayer?.addEventListener('playing', hideBackdrop, { once: true });
  adultHtmlVideoPlayer?.addEventListener('canplay', hideBackdrop, { once: true });
  adultIframePlayer?.addEventListener('load', hideBackdrop, { once: true });
  setTimeout(hideBackdrop, 3500);

  if (adultWatchTitle) adultWatchTitle.textContent = item.title;
  if (adultWatchDuration) adultWatchDuration.innerHTML = `<i class="fa-solid fa-clock"></i> ${item.duration || 'HD'}`;
  if (adultWatchRating) adultWatchRating.innerHTML = `<i class="fa-solid fa-crown" style="color: #ffd700;"></i> ${item.quality || '1080p Full HD'}`;
  if (adultWatchSourceBadge) adultWatchSourceBadge.textContent = item.source || 'Adult Stream';

  if (adultHtmlVideoPlayer) {
    try { adultHtmlVideoPlayer.pause(); } catch (_) { }
    adultHtmlVideoPlayer.removeAttribute('src');
    try { adultHtmlVideoPlayer.load(); } catch (_) { }
    adultHtmlVideoPlayer.src = '';
    adultHtmlVideoPlayer.classList.add('hidden');
  }
  if (adultIframePlayer) {
    adultIframePlayer.src = 'about:blank';
    adultIframePlayer.removeAttribute('src');
    adultIframePlayer.classList.add('hidden');
  }

  if (adultQualityPills) {
    adultQualityPills.innerHTML = '<span style="color: var(--text-muted); font-size: 0.8rem;"><i class="fa-solid fa-spinner fa-spin"></i> Resolving stream...</span>';
  }

  if (btnAdultWatchSource) btnAdultWatchSource.href = targetUrl || '#';

  try {
    let resolved = null;
    const isNativeApp = !!(window.Capacitor?.isNativePlatform?.() || window.Capacitor?.Plugins?.NativeExtractor);

    // ⚡ 0. Check Zero-Latency Preload Cache (Instant 0ms Playback)
    if (adultPreloadCache.has(targetUrl)) {
      resolved = adultPreloadCache.get(targetUrl);
      window.showToast('⚡ Instant HD stream connected!', 'info');
    } else {
      window.showToast('Extracting direct HD video stream...', 'info');
    }

    // ⚡ 1. Direct Kotlin Native Extractor (Fast-Yield & Censorship-Bypassed)
    if (!resolved && window.Capacitor?.Plugins?.NativeExtractor?.resolveAdultStream) {
      try {
        const nRes = await window.Capacitor.Plugins.NativeExtractor.resolveAdultStream({ url: targetUrl });
        if (nRes) {
          resolved = nRes.resolved || nRes.stream || nRes;
        }
      } catch (err) {
        console.warn('[NativeExtractor] Error resolving stream:', err);
      }
    }

    // ⚡ 2. Standalone Client-Side Scraper Fallback
    if ((!resolved || (!resolved.streamUrl && !resolved.embedUrl)) && window.StandaloneEngine?.resolveAdultStream) {
      try {
        const sRes = await window.StandaloneEngine.resolveAdultStream(targetUrl);
        if (sRes && (sRes.streamUrl || sRes.embedUrl)) {
          resolved = sRes;
        }
      } catch (_) { }
    }

    // ⚡ 3. Resilient Direct Fallback - NEVER allows resolved to be null!
    if (!resolved || (!resolved.streamUrl && !resolved.embedUrl && (!resolved.qualities || resolved.qualities.length === 0))) {
      resolved = {
        title: item.title || 'Video Stream',
        streamUrl: targetUrl,
        downloadUrl: targetUrl,
        embedUrl: targetUrl,
        qualities: [{ quality: '1080p Full HD', label: '1080p Full HD', url: targetUrl }]
      };
    }

    // Cache resolved stream for future instant switches
    if (resolved && (resolved.streamUrl || resolved.embedUrl)) {
      adultPreloadCache.set(targetUrl, resolved);
    }

    // Silently pre-resolve the NEXT queued video in background
    scheduleNextAdultPreload();

    const streamUrl = resolved.streamUrl || resolved.embedUrl || targetUrl;
    const embedUrl = resolved.embedUrl || targetUrl;
    const qualities = resolved.qualities || [];
    let activeStreamUrl = streamUrl || (qualities.find(q => q.url && !q.isEmbed)?.url) || embedUrl;
    let activeDownloadUrl = resolved.downloadUrl || streamUrl || embedUrl;
    let activeQualityLabel = qualities[0]?.label || qualities[0]?.quality || '1080p Full HD';

    // Populate Quality Pills
    if (adultQualityPills) {
      adultQualityPills.innerHTML = '';

      const allQualitiesList = [...qualities];
      const hasDirectVideos = allQualitiesList.some(q => q.url && !q.url.includes('/embed') && !q.url.includes('xembed') && !q.isEmbed);
      if (hasDirectVideos && !allQualitiesList.some(q => (q.quality || '').toLowerCase().includes('auto'))) {
        const bestVideo = allQualitiesList.find(q => (q.quality || q.label || '').includes('1080')) || allQualitiesList[0];
        allQualitiesList.unshift({
          quality: 'Auto',
          label: '✨ Auto (Adaptive)',
          url: bestVideo ? bestVideo.url : (streamUrl || embedUrl),
          isAuto: true
        });
      }

      const switchQuality = (targetQ, pillElement) => {
        document.querySelectorAll('.quality-pill-yt').forEach(p => p.classList.remove('active'));
        if (pillElement) pillElement.classList.add('active');

        activeDownloadUrl = targetQ.url || resolved.downloadUrl || streamUrl;
        activeQualityLabel = targetQ.label || targetQ.quality;

        if (targetQ.url) {
          const directUrl = (targetQ.url.startsWith('http://') || targetQ.url.startsWith('https://')) ? targetQ.url : window.resolveApiUrl(targetQ.url);
          const savedPosition = (adultHtmlVideoPlayer && adultHtmlVideoPlayer.currentTime > 0 && Number.isFinite(adultHtmlVideoPlayer.currentTime))
            ? adultHtmlVideoPlayer.currentTime
            : 0;
          const wasPlaying = adultHtmlVideoPlayer ? (!adultHtmlVideoPlayer.paused && !adultHtmlVideoPlayer.ended) : true;

          playDirectAdultMediaUrl(directUrl, embedUrl, savedPosition, wasPlaying);

          if (savedPosition > 0) {
            const m = Math.floor(savedPosition / 60);
            const s = Math.floor(savedPosition % 60);
            const timeFormatted = `${m}:${s < 10 ? '0' : ''}${s}`;
            window.showToast(`🎬 Quality: ${targetQ.label || targetQ.quality} (Playing from ${timeFormatted})`, 'info');
          } else {
            window.showToast(`🎬 Quality: ${targetQ.label || targetQ.quality}`, 'info');
          }
        }
      };

      if (allQualitiesList.length > 0) {
        allQualitiesList.forEach((q, idx) => {
          const pill = document.createElement('button');
          pill.className = `quality-pill-yt ${idx === 0 ? 'active' : ''}`;
          pill.innerHTML = `<span>${q.label || q.quality || '1080p'}</span>`;
          pill.addEventListener('click', () => switchQuality(q, pill));
          adultQualityPills.appendChild(pill);
        });
      } else {
        adultQualityPills.innerHTML = `
          <button class="quality-pill-yt active">✨ Auto</button>
          <button class="quality-pill-yt">1080p Full HD</button>
          <button class="quality-pill-yt">720p HD</button>
          <button class="quality-pill-yt">480p SD</button>
        `;
      }
    }

    // Attach Action Buttons
    const btnAdultWatchNative = document.getElementById('btnAdultWatchNative');
    if (btnAdultWatchNative) {
      btnAdultWatchNative.onclick = async () => {
        const playUrl = activeDownloadUrl || streamUrl || embedUrl || targetUrl;
        if (window.Capacitor?.Plugins?.GalleryPlayer?.playOfflineVideo && playUrl) {
          try {
            await window.Capacitor.Plugins.GalleryPlayer.playOfflineVideo({
              path: playUrl,
              title: item.title,
              category: 'adult'
            });
            return;
          } catch (err) {
            console.warn('[GalleryPlayer] Fallback error:', err);
          }
        }
        if (window.Capacitor?.Plugins?.NativePlayer?.playVideo && playUrl) {
          try {
            await window.Capacitor.Plugins.NativePlayer.playVideo({
              streamUrl: playUrl,
              title: item.title,
              category: 'adult'
            });
            return;
          } catch (err) {
            console.warn('[NativePlayer] Fallback error:', err);
          }
        }
      };
    }

    if (btnAdultWatchDrive) {
      btnAdultWatchDrive.onclick = () => {
        if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
          if (window.showInApp404) {
            window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Google Drive upload pipeline is disabled or unauthorized under the active license. Please activate a PRO License Key.');
          }
          return;
        }
        recordAdultEvent('DOWNLOAD', item);
        if (window.startCloudTransfer) {
          window.startCloudTransfer({
            title: `${item.title} [${activeQualityLabel}]`,
            url: activeDownloadUrl,
            type: 'adult',
            quality: activeQualityLabel
          });
        }
      };
    }

    if (btnAdultWatchDownload) {
      btnAdultWatchDownload.onclick = () => {
        recordAdultEvent('DOWNLOAD', item);
        if (qualities && qualities.length > 1) {
          window.openResolutionPickerModal({
            title: item.title,
            qualities: qualities,
            defaultDownloadUrl: activeDownloadUrl
          });
        } else {
          window.triggerDirectDownload(activeDownloadUrl, `${item.title.replace(/[^a-zA-Z0-9]/g, '_')} [${activeQualityLabel}].mp4`, 'adult');
        }
      };
    }

    if (btnAdultWatchCopy) {
      btnAdultWatchCopy.onclick = () => {
        navigator.clipboard.writeText(activeDownloadUrl || window.location.href);
        window.showToast('Stream URL copied to clipboard!', 'success');
      };
    }

    // Start Playing active resolved stream
    const playTarget = activeStreamUrl || streamUrl || embedUrl;
    if (playTarget) {
      const directPlayUrl = (playTarget.startsWith('http://') || playTarget.startsWith('https://'))
        ? playTarget
        : window.resolveApiUrl(playTarget);
      playDirectAdultMediaUrl(directPlayUrl, embedUrl, resumePosition, true);
    } else if (embedUrl) {
      playDirectAdultMediaUrl(embedUrl, embedUrl, resumePosition, true);
    }
  } catch (e) {
    window.showToast('Failed to resolve direct video stream.', 'error');
  }

  loadAdultWatchRelatedFeed(item.title, item.source);
};

// ============================================================================
// SECTION 10: Up-Next & Contextual Smart Recommendations Feed
// ============================================================================

async function loadAdultWatchRelatedFeed(title, source) {
  const adultRelatedFeed = document.getElementById('adultRelatedFeed');
  if (!adultRelatedFeed) return;

  adultRelatedFeed.innerHTML = `
    <div style="color: var(--text-muted); font-size: 0.78rem; padding: 14px; display: flex; align-items: center; gap: 8px; grid-column: 1 / -1;">
      <div class="spinner adult-spinner" style="width: 18px; height: 18px; border-width: 2px;"></div>
      <span>Loading high-definition recommendations & up next queue...</span>
    </div>
  `;

  try {
    const rawWords = (title || 'popular')
      .toLowerCase()
      .replace(/[^a-z0-9 ]/g, ' ')
      .split(/\s+/)
      .filter(w => w.length >= 3 && !['video', 'full', 'watch', 'online', 'free', 'the', 'and', 'with', 'for', 'hd', 'mp4', 'stream', 'part', 'scene', 'clip'].includes(w));
    const cleanSearch = rawWords.slice(0, 2).join(' ') || 'trending';
    const currentTitleNorm = (title || '').toLowerCase().replace(/[^a-z0-9]/g, '');

    let items = [];

    // ⚡ 1. Parallel Multi-Page Native Extractor search (Pages 1 & 2)
    if (window.Capacitor?.Plugins?.NativeExtractor?.searchAdult) {
      try {
        const [nRes1, nRes2] = await Promise.allSettled([
          window.Capacitor.Plugins.NativeExtractor.searchAdult({ query: cleanSearch, page: 1, source: 'all' }),
          window.Capacitor.Plugins.NativeExtractor.searchAdult({ query: cleanSearch, page: 2, source: 'all' })
        ]);
        if (nRes1.status === 'fulfilled' && Array.isArray(nRes1.value?.results)) {
          items.push(...nRes1.value.results);
        }
        if (nRes2.status === 'fulfilled' && Array.isArray(nRes2.value?.results)) {
          items.push(...nRes2.value.results);
        }
      } catch (_) { }
    }

    // ⚡ 2. Tag & Source Supplement if candidate count is below 30
    if (items.length < 30 && rawWords[0] && rawWords[0] !== cleanSearch && window.Capacitor?.Plugins?.NativeExtractor?.searchAdult) {
      try {
        const tagRes = await window.Capacitor.Plugins.NativeExtractor.searchAdult({ query: rawWords[0], page: 1, source: source || 'all' });
        if (tagRes && Array.isArray(tagRes.results)) {
          items.push(...tagRes.results);
        }
      } catch (_) { }
    }

    // ⚡ 3. Standalone Client-Side Scraper Fallback
    if (items.length < 15 && window.StandaloneEngine?.searchAdult) {
      try {
        const sRes = await window.StandaloneEngine.searchAdult(cleanSearch, 1, 'all');
        if (Array.isArray(sRes)) items.push(...sRes);
      } catch (_) { }
    }



    // ⚡ 5. Fallback from current active results to ensure massive variety
    if (window.rawAdultResults && window.rawAdultResults.length > 0) {
      items.push(...window.rawAdultResults);
    }

    // Deduplicate & filter out the active video
    const seenIds = new Set();
    items = items.filter(relItem => {
      if (!relItem) return false;
      const relId = (relItem.url || relItem.link || relItem.title || '').trim();
      if (!relId || seenIds.has(relId)) return false;
      seenIds.add(relId);

      const relTitleNorm = (relItem.title || '').toLowerCase().replace(/[^a-z0-9]/g, '');
      return relTitleNorm !== currentTitleNorm && !relTitleNorm.includes(currentTitleNorm.slice(0, 20));
    });

    items = applySmartAlgorithm(items, 'for-you');
    window.currentAdultRelatedList = items;

    if (items.length === 0) {
      adultRelatedFeed.innerHTML = '<div style="color: var(--text-muted); font-size: 0.78rem; padding: 10px; grid-column: 1 / -1;">No related videos found for this stream.</div>';
      return;
    }

    adultRelatedFeed.innerHTML = '';

    let displayedCount = 0;
    const INITIAL_PAGE_SIZE = 30;

    const renderCard = (relItem, index) => {
      const card = document.createElement('div');
      card.className = 'yt-related-card-2026 adult-card-enter';
      card.style.animationDelay = `${(index % 12) * 0.035}s`;
      card.dataset.idx = index;

      const isCurrentActive = (currentAdultWatchSession && currentAdultWatchSession.item && (currentAdultWatchSession.item.url === relItem.url || currentAdultWatchSession.item.title === relItem.title));
      if (isCurrentActive) {
        card.classList.add('active-playing-related');
      }

      card.innerHTML = `
        <div class="yt-thumb-wrap">
          <img src="${relItem.thumbnail || 'data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22300%22%20height%3D%22200%22%20viewBox%3D%220%200%20300%20200%22%3E%3Crect%20fill%3D%22%23121526%22%20width%3D%22100%25%22%20height%3D%22100%25%22%2F%3E%3Ctext%20fill%3D%22%23ff007f%22%20font-family%3D%22sans-serif%22%20font-size%3D%2216%22%20font-weight%3D%22bold%22%20x%3D%2250%25%22%20y%3D%2250%25%22%20dominant-baseline%3D%22middle%22%20text-anchor%3D%22middle%22%3E18%2B%20STREAM%3C%2Ftext%3E%3C%2Fsvg%3E'}" alt="${escapeHtml(relItem.title || '')}" loading="lazy">
          <span class="yt-dur-pill">${relItem.duration || 'HD'}</span>
          <span class="yt-src-pill">${relItem.source || '18+'}</span>
          ${isCurrentActive ? '<span class="now-playing-pill"><i class="fa-solid fa-play"></i> Playing</span>' : ''}
        </div>
        <div class="yt-related-info-2026">
          <div class="yt-related-title-2026">${relItem.title}</div>
          <div class="yt-related-meta-2026">
            <span style="color: var(--accent-magenta); font-weight: 700;">${relItem.source || 'Stream'}</span>
            <span>•</span>
            <span style="color: #ffd700;"><i class="fa-solid fa-crown"></i> ${relItem.quality || '1080p FHD'}</span>
          </div>
        </div>
      `;

      card.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();

        document.querySelectorAll('.yt-related-card-2026').forEach(c => c.classList.remove('active-playing-related'));
        card.classList.add('active-playing-related');

        recordAdultEvent('CLICK', relItem);

        // Synchronize playlist queue to flow naturally from this selected stream
        if (window.currentAdultRelatedList && window.currentAdultRelatedList.length > 0) {
          const clickedIdx = window.currentAdultRelatedList.findIndex(r => (r.url && r.url === relItem.url) || (r.title && r.title === relItem.title));
          if (clickedIdx !== -1) {
            window.currentAdultPlaylist = [...window.currentAdultRelatedList];
            window.currentAdultIndex = clickedIdx;
          }
        }

        window.showToast(`▶ Now Playing: ${relItem.title.slice(0, 24)}...`, 'info');
        window.openAdultWatchMode(relItem);
      });

      return card;
    };

    const appendBatch = (count = 20) => {
      const slice = items.slice(displayedCount, displayedCount + count);
      slice.forEach((relItem, idx) => {
        const card = renderCard(relItem, displayedCount + idx);
        adultRelatedFeed.appendChild(card);
      });
      displayedCount += slice.length;

      // Manage Load More button
      const oldBtn = document.getElementById('btnLoadMoreRelatedAdult');
      if (oldBtn) oldBtn.remove();

      if (displayedCount < items.length) {
        const remaining = items.length - displayedCount;
        const loadMoreBtn = document.createElement('button');
        loadMoreBtn.id = 'btnLoadMoreRelatedAdult';
        loadMoreBtn.className = 'btn-load-more-related';
        loadMoreBtn.innerHTML = `<i class="fa-solid fa-angles-down"></i> <span>Show More Up Next (+${Math.min(20, remaining)})</span>`;
        loadMoreBtn.addEventListener('click', () => appendBatch(20));
        adultRelatedFeed.appendChild(loadMoreBtn);
      }
    };

    appendBatch(INITIAL_PAGE_SIZE);

  } catch (err) {
    if (adultRelatedFeed) {
      adultRelatedFeed.innerHTML = '<div style="color: var(--text-muted); font-size: 0.78rem; padding: 10px; grid-column: 1 / -1;">Could not load recommended feed.</div>';
    }
  }
}

// ============================================================================
// SECTION 11: Interactive Resolution Quality Picker Modal
// ============================================================================

window.openResolutionPickerModal = function (options = {}) {
  const modal = document.getElementById('qualityPickerModal');
  const titleEl = document.getElementById('qualityPickerTitle');
  const listEl = document.getElementById('qualityPickerList');
  const btnClose = document.getElementById('btnCloseQualityPicker');

  if (!modal || !listEl) return;

  const { title = '18+ Adult Video', qualities = [], defaultDownloadUrl } = options;
  if (titleEl) titleEl.textContent = 'Choose Download Resolution';

  listEl.innerHTML = '';

  const qualsToRender = qualities.length > 0 ? qualities : [
    { quality: '1080p', label: '1080p Full HD', url: defaultDownloadUrl },
    { quality: '720p', label: '720p HD', url: defaultDownloadUrl },
    { quality: '480p', label: '480p SD', url: defaultDownloadUrl }
  ];

  qualsToRender.forEach(q => {
    const row = document.createElement('div');
    row.className = 'quality-card-row';
    row.style.display = 'flex';
    row.style.alignItems = 'center';
    row.style.justifyContent = 'space-between';
    row.style.padding = '12px 14px';
    row.style.background = 'rgba(255, 255, 255, 0.04)';
    row.style.borderRadius = '12px';
    row.style.border = '1px solid rgba(255, 255, 255, 0.08)';

    let badgeColor = 'var(--accent-magenta)';
    if (q.quality?.includes('4k') || q.quality?.includes('2160')) badgeColor = '#fbbf24';
    else if (q.quality?.includes('1080')) badgeColor = '#00f2fe';
    else if (q.quality?.includes('720')) badgeColor = '#38ef7d';

    row.innerHTML = `
      <div style="display: flex; flex-direction: column; gap: 2px;">
        <span style="font-weight: 800; font-size: 0.94rem; color: ${badgeColor};">${q.label || q.quality}</span>
        <span style="font-size: 0.72rem; color: var(--text-muted);">${q.filesizeMB ? `~${q.filesizeMB} MB • ` : ''}⚡ High Speed Direct MP4</span>
      </div>
      <div style="display: flex; gap: 8px;">
        <button class="btn-action btn-dl-res" style="padding: 7px 15px; font-size: 0.8rem; background: linear-gradient(135deg, #ff0844, #ff4e50); color: #fff; border-radius: 20px; font-weight: 700; border: none; cursor: pointer;">
          <i class="fa-solid fa-download"></i> Download
        </button>
        <button class="btn-action btn-drive-res" style="padding: 7px 14px; font-size: 0.8rem; background: rgba(0, 242, 254, 0.15); color: var(--accent-cyan); border: 1px solid rgba(0, 242, 254, 0.4); border-radius: 20px; font-weight: 700; cursor: pointer;">
          <i class="fa-solid fa-cloud-arrow-up"></i> Drive
        </button>
      </div>
    `;

    row.querySelector('.btn-dl-res').addEventListener('click', () => {
      const cleanTitle = `${title.replace(/[/\\?%*:|"<>]/g, '_')} [${q.quality || q.label}].mp4`;
      window.triggerDirectDownload(q.url || defaultDownloadUrl, cleanTitle);
      if (window.closeModalWithHistory) window.closeModalWithHistory(modal);
      else modal.classList.add('hidden');
    });

    row.querySelector('.btn-drive-res').addEventListener('click', () => {
      if (window.startCloudTransfer) {
        window.startCloudTransfer({
          title: `${title} [${q.quality || q.label}]`,
          url: q.url || defaultDownloadUrl,
          type: 'adult',
          quality: q.quality || 'HD'
        });
      }
      if (window.closeModalWithHistory) window.closeModalWithHistory(modal);
      else modal.classList.add('hidden');
    });

    listEl.appendChild(row);
  });

  if (window.openModalWithHistory) window.openModalWithHistory(modal);
  else modal.classList.remove('hidden');

  if (btnClose) {
    btnClose.onclick = () => {
      if (window.closeModalWithHistory) window.closeModalWithHistory(modal);
      else modal.classList.add('hidden');
    };
  }
};

// ============================================================================
// SECTION 12: Double-Tap Seek Gestures, Auto-Rotate & Lifecycle Bootstrap
// ============================================================================

function initAdultDoubleTapSeek() {
  const theaterContainer = document.getElementById('adultTheaterContainer');
  const rippleLeft = document.getElementById('adultRippleLeft');
  const rippleRight = document.getElementById('adultRippleRight');
  if (!theaterContainer) return;

  theaterContainer.addEventListener('click', (e) => {
    if (e.target.closest('.adult-player-floating-bar') || e.target.closest('.adult-up-next-overlay')) return;

    const now = Date.now();
    const rect = theaterContainer.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const isLeft = x < rect.width * 0.40;
    const isRight = x > rect.width * 0.60;

    if (now - adultLastTapTime < 320 && (isLeft || isRight)) {
      if (adultHtmlVideoPlayer && Number.isFinite(adultHtmlVideoPlayer.duration)) {
        if (isLeft) {
          adultHtmlVideoPlayer.currentTime = Math.max(0, adultHtmlVideoPlayer.currentTime - 10);
          if (rippleLeft) {
            rippleLeft.classList.remove('hidden');
            setTimeout(() => rippleLeft.classList.add('hidden'), 650);
          }
        } else if (isRight) {
          adultHtmlVideoPlayer.currentTime = Math.min(adultHtmlVideoPlayer.duration, adultHtmlVideoPlayer.currentTime + 10);
          if (rippleRight) {
            rippleRight.classList.remove('hidden');
            setTimeout(() => rippleRight.classList.add('hidden'), 650);
          }
        }
      }
      adultLastTapTime = 0;
    } else {
      adultLastTapTime = now;
    }
  });
}

function initAdultAutoRotateDetection() {
  let debounceTimeout = null;
  const handleOrientation = () => {
    clearTimeout(debounceTimeout);
    debounceTimeout = setTimeout(() => {
      const isWatchViewVisible = adultWatchView && !adultWatchView.classList.contains('hidden');
      if (!isWatchViewVisible) return;

      const isLandscape = window.innerWidth > window.innerHeight;
      const container = document.getElementById('adultTheaterContainer');
      if (container) {
        if (isLandscape) {
          container.classList.add('is-fullscreen');
        } else {
          container.classList.remove('is-fullscreen');
        }
      }
    }, 150);
  };

  window.addEventListener('orientationchange', handleOrientation);
  window.addEventListener('resize', handleOrientation);
  if (window.screen?.orientation) {
    window.screen.orientation.addEventListener('change', handleOrientation);
  }
}

function initAdultTheaterSwipeGestures() {
  const theater = document.getElementById('adultTheaterContainer');
  const indicator = document.getElementById('adultSwipeIndicator');
  const indicatorText = document.getElementById('adultSwipeText');
  const indicatorIcon = document.getElementById('adultSwipeIcon');
  if (!theater) return;

  let touchStartX = 0;
  let touchStartY = 0;
  let touchStartTime = 0;
  let isSwiping = false;

  theater.addEventListener('touchstart', (e) => {
    if (e.touches.length !== 1) return;
    touchStartX = e.touches[0].clientX;
    touchStartY = e.touches[0].clientY;
    touchStartTime = Date.now();
    isSwiping = true;
  }, { passive: true });

  theater.addEventListener('touchend', (e) => {
    if (!isSwiping || e.changedTouches.length !== 1) return;
    isSwiping = false;
    const deltaX = e.changedTouches[0].clientX - touchStartX;
    const deltaY = e.changedTouches[0].clientY - touchStartY;
    const deltaTime = Date.now() - touchStartTime;

    if (deltaTime > 650) return;

    // Horizontal Swipe (Next / Prev Stream)
    if (Math.abs(deltaX) > 60 && Math.abs(deltaX) > Math.abs(deltaY) * 1.3) {
      if (deltaX < 0) {
        // Swipe Left -> Next Video
        if (indicator && indicatorText) {
          if (indicatorIcon) indicatorIcon.innerHTML = '<i class="fa-solid fa-forward-step"></i>';
          indicatorText.textContent = 'Next Stream ▶';
          indicator.classList.remove('hidden');
          indicator.classList.add('active');
          setTimeout(() => {
            indicator.classList.remove('active');
            setTimeout(() => indicator.classList.add('hidden'), 250);
          }, 600);
        }
        window.playNextAdultVideo();
      } else {
        // Swipe Right -> Prev Video
        if (indicator && indicatorText) {
          if (indicatorIcon) indicatorIcon.innerHTML = '<i class="fa-solid fa-backward-step"></i>';
          indicatorText.textContent = '◀ Previous Stream';
          indicator.classList.remove('hidden');
          indicator.classList.add('active');
          setTimeout(() => {
            indicator.classList.remove('active');
            setTimeout(() => indicator.classList.add('hidden'), 250);
          }, 600);
        }
        window.playPrevAdultVideo();
      }
      return;
    }

    // Vertical Swipe Down -> Smoothly return to feed
    if (deltaY > 90 && Math.abs(deltaY) > Math.abs(deltaX) * 1.5) {
      window.closeAdultWatchMode();
      window.showToast('Back to feed', 'info');
    }
  }, { passive: true });
}

function initAdultRecommendationSettingsModal() {
  const modal = document.getElementById('adultSettingsModal');
  const btnOpen = document.getElementById('btnAdultSettings');
  const btnClose = document.getElementById('btnCloseAdultSettingsModal');
  if (!modal || !btnOpen) return;

  const togglePers = document.getElementById('toggleAdultPersonalization');
  const toggleHist = document.getElementById('toggleAdultHistory');
  const toggleCont = document.getElementById('toggleAdultContinueWatching');
  const toggleSrc = document.getElementById('toggleAdultSourcePersonalization');
  const selTrend = document.getElementById('selectAdultTrendingInfluence');
  const selQual = document.getElementById('selectAdultQualityPref');
  const btnReset = document.getElementById('btnAdultResetRecommendations');
  const btnClearHist = document.getElementById('btnAdultClearHistory');

  function syncModalValues() {
    if (togglePers) togglePers.checked = localStorage.getItem('cdl_adult_personalization_enabled') !== 'false';
    if (toggleHist) toggleHist.checked = localStorage.getItem('cdl_adult_history_enabled') !== 'false';
    if (toggleCont) toggleCont.checked = localStorage.getItem('cdl_adult_continue_watching_enabled') !== 'false';
    if (toggleSrc) toggleSrc.checked = localStorage.getItem('cdl_adult_source_personalization_enabled') !== 'false';
    if (selTrend) selTrend.value = localStorage.getItem('cdl_adult_trending_influence') || 'balanced';
    if (selQual) selQual.value = localStorage.getItem('cdl_adult_quality_preference') || 'any';
  }

  btnOpen.addEventListener('click', () => {
    syncModalValues();
    modal.classList.remove('hidden');
  });

  btnClose?.addEventListener('click', () => {
    modal.classList.add('hidden');
  });

  modal.addEventListener('click', (e) => {
    if (e.target === modal) modal.classList.add('hidden');
  });

  togglePers?.addEventListener('change', () => {
    localStorage.setItem('cdl_adult_personalization_enabled', togglePers.checked ? 'true' : 'false');
    window.showToast(togglePers.checked ? 'Personalization enabled' : 'Personalization disabled (Standard feed)', 'info');
    if (window.rawAdultResults?.length) renderAdultGrid();
  });

  toggleHist?.addEventListener('change', () => {
    localStorage.setItem('cdl_adult_history_enabled', toggleHist.checked ? 'true' : 'false');
    window.showToast(toggleHist.checked ? 'Watch history tracking enabled' : 'Watch history paused', 'info');
  });

  toggleCont?.addEventListener('change', () => {
    localStorage.setItem('cdl_adult_continue_watching_enabled', toggleCont.checked ? 'true' : 'false');
    renderContinueWatchingRail();
    window.showToast(toggleCont.checked ? 'Continue watching rail enabled' : 'Continue watching rail hidden', 'info');
  });

  toggleSrc?.addEventListener('change', () => {
    localStorage.setItem('cdl_adult_source_personalization_enabled', toggleSrc.checked ? 'true' : 'false');
    window.showToast(toggleSrc.checked ? 'Source preferences enabled' : 'Source preferences disabled', 'info');
    if (window.rawAdultResults?.length) renderAdultGrid();
  });

  selTrend?.addEventListener('change', () => {
    localStorage.setItem('cdl_adult_trending_influence', selTrend.value);
    window.showToast(`Trending influence set to ${selTrend.value}`, 'info');
    if (window.rawAdultResults?.length) renderAdultGrid();
  });

  selQual?.addEventListener('change', () => {
    localStorage.setItem('cdl_adult_quality_preference', selQual.value);
    window.showToast(`Quality preference: ${selQual.options[selQual.selectedIndex]?.text || selQual.value}`, 'info');
    if (window.rawAdultResults?.length) renderAdultGrid();
  });

  btnReset?.addEventListener('click', () => {
    localStorage.removeItem('adult_taste_profile_v3');
    localStorage.removeItem('adult_taste_profile_v2');
    window.showToast('Recommendation taste profile reset to cold-start.', 'success');
    if (window.rawAdultResults?.length) renderAdultGrid();
  });

  btnClearHist?.addEventListener('click', () => {
    localStorage.removeItem('cdl_adult_events_v2');
    localStorage.removeItem('adult_watch_history');
    localStorage.removeItem('cdl_adult_continue_watching');
    localStorage.removeItem('cdl_adult_last_watched_item');
    renderContinueWatchingRail();
    renderBecauseYouWatchedRail();
    window.showToast('Watch history and continue watching cleared.', 'success');
  });
}

function initAdultPlayerFeatures() {
  initAdultDoubleTapSeek();
  initAdultTheaterSwipeGestures();
  initAdultAutoRotateDetection();
  setupAdultInfiniteScrollObserver();
  initAdultRecommendationSettingsModal();
  window.setAdultFitMode(adultFitMode);

  // Wire 18+ Age Gate Buttons (Section 3)
  const confirmBtn = document.getElementById('btnConfirmAgeGate') || btnConfirmAgeGate;
  confirmBtn?.addEventListener('click', () => {
    setAdultGateUnlocked(true);
    checkAdultGateState();
    window.showToast('🔞 Age verified. Welcome to 18+ Cinema.', 'success');
    if (!window.rawAdultResults || window.rawAdultResults.length === 0) {
      window.searchAdult(window.currentAdultAlgo || 'for-you', window.currentAdultSource || 'all');
    }
  });

  const exitBtn = document.getElementById('btnExitAgeGate') || btnExitAgeGate;
  exitBtn?.addEventListener('click', () => {
    if (window.switchTab) {
      window.switchTab('movies');
    }
  });

  // App visibility & backgrounding handling for active watch session
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      if (currentAdultWatchSession) {
        currentAdultWatchSession.isPaused = true;
      }
      if (adultHtmlVideoPlayer && !adultHtmlVideoPlayer.paused) {
        adultHtmlVideoPlayer.pause();
      }
    } else {
      if (currentAdultWatchSession && adultHtmlVideoPlayer && !adultHtmlVideoPlayer.paused) {
        currentAdultWatchSession.isPaused = false;
        currentAdultWatchSession.lastActiveTime = Date.now();
      }
    }
  });

  window.addEventListener('beforeunload', () => {
    finalizeAdultWatchSession();
  });

  window.addEventListener('cloud:active-tab-changed', (e) => {
    if (e.detail?.prevTab === 'adult' && e.detail?.tab !== 'adult') {
      finalizeAdultWatchSession();
    }
    if (e.detail?.tab === 'adult') {
      checkAdultGateState();
    }
  });

  // Check Age Gate state on launch
  checkAdultGateState();
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initAdultPlayerFeatures);
} else {
  initAdultPlayerFeatures();
}
