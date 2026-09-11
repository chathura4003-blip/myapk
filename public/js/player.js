'use strict';

/**
 * ============================================================================
 * CloudDriveLeech - Master Cinema Pro Video Player (2026 Ultra Edition)
 * ============================================================================
 * 
 * Architecture & Responsibilities:
 *  - SECTION 1: DOM Elements & Interface Selectors
 *  - SECTION 2: State Variables & Configuration
 *  - SECTION 3: Unified URL Normalizers & Stream Helpers (DRY)
 *  - SECTION 4: HUD & Orientation Management
 *  - SECTION 5: Player Lifecycle & Modal Handlers (Open / Close)
 *  - SECTION 6: HTML5 Video & HLS.js Core Streaming Engine
 *  - SECTION 7: Web Embed & Cloud Cinema Loader
 *  - SECTION 8: Intelligent Failover & Anti-Loop Engine
 *  - SECTION 9: Scrubber & Media Controls
 *  - SECTION 10: Touch Gestures & Smooth Volume HUD
 *  - SECTION 11: Fullscreen & Native ExoPlayer Handover
 *  - SECTION 12: Keyboard Shortcuts & External Message Bridge
 * ============================================================================
 */

// ============================================================================
// SECTION 1: DOM Elements & Interface Selectors
// ============================================================================
const playerModal = document.getElementById('playerModal');
const btnClosePlayer = document.getElementById('btnClosePlayer');
const htmlVideoPlayer = document.getElementById('htmlVideoPlayer');
const iframeVideoPlayer = document.getElementById('iframeVideoPlayer');
const playerStageFrame = document.getElementById('playerStageFrame');
const playerTitle = document.getElementById('playerTitle');
const playerSourceInfo = document.getElementById('playerSourceInfo');
const playerAmbientGlow = document.getElementById('playerAmbientGlow');

// Header & Action Bar
const btnPlayerPip = document.getElementById('btnPlayerPip');
const btnPlayerRotateScreen = document.getElementById('btnPlayerRotateScreen');
const btnPlayerRotateScreenBottom = document.getElementById('btnPlayerRotateScreenBottom');
const btnPlayerFullscreenTop = document.getElementById('btnPlayerFullscreenTop');
const btnPlayerFullscreen = document.getElementById('btnPlayerFullscreen');
const btnPlayerLaunchPro = document.getElementById('btnPlayerLaunchPro');
const btnRewind10 = document.getElementById('btnRewind10');
const btnForward10 = document.getElementById('btnForward10');
const btnAspectFit = document.getElementById('btnAspectFit');
const btnCopyStreamLink = document.getElementById('btnCopyStreamLink');

// Player Controls & Progress Overlay
const playerCenterControls = document.getElementById('playerCenterControls');
const playerControlsOverlay = document.getElementById('playerControlsOverlay');
const btnPlayerCenterPlay = document.getElementById('btnPlayerCenterPlay');
const btnPlayerPlayPause = document.getElementById('btnPlayerPlayPause');
const playerTimeText = document.getElementById('playerTimeText');
const playerProgressTrack = document.getElementById('playerProgressTrack');
const playerProgressFill = document.getElementById('playerProgressFill');
const playerProgressHead = document.getElementById('playerProgressHead');
const btnPlayerSpeedToggle = document.getElementById('btnPlayerSpeedToggle');
const playerSpeedToggleText = document.getElementById('playerSpeedToggleText');
const btnPlayerMute = document.getElementById('btnPlayerMute');

// Badges & Metrics
const cinemaServerSelector = document.getElementById('cinemaServerSelector');
const playerMetricSpeed = document.getElementById('playerMetricSpeed');
const playerMetricRes = document.getElementById('playerMetricRes');
const playerMetricDuration = document.getElementById('playerMetricDuration');

// Action Buttons
const playerDirectDownload = document.getElementById('playerDirectDownload');
const playerUploadDriveBtn = document.getElementById('playerUploadDriveBtn');
const btnPlayerReturnToMovie = document.getElementById('btnPlayerReturnToMovie');

// Skip Indicators & Gesture HUD
const skipOverlayLeft = document.getElementById('skipOverlayLeft');
const skipOverlayRight = document.getElementById('skipOverlayRight');
const playerGestureHud = document.getElementById('playerGestureHud');
const playerGestureIcon = document.getElementById('playerGestureIcon');
const playerGestureFill = document.getElementById('playerGestureFill');
const playerGestureText = document.getElementById('playerGestureText');

// ZIP Archive Mode Elements
const playerZipBanner = document.getElementById('playerZipBanner');
const btnZipDirectDownload = document.getElementById('btnZipDirectDownload');
const btnZipLeechDrive = document.getElementById('btnZipLeechDrive');


// ============================================================================
// SECTION 2: State Variables & Configuration
// ============================================================================
let currentStreamUrl = '';
let currentVideoTitle = '';
let currentImdbId = '';
let currentTmdbId = '';
let currentSeason = 1;
let currentEpisode = 1;
let currentCleanTitle = '';
let currentPosterUrl = '';
let currentAllQualities = [];
let isSeriesMode = false;
let aspectMode = 'contain'; // 'contain' | 'cover' | 'fill'
const speedLevels = [0.75, 1.0, 1.25, 1.5, 2.0];
let currentSpeedIdx = 1;

let isDraggingScrubber = false;
let playerControlsTimer = null;
let currentHlsInstance = null;
let lastUiUpdateTime = 0;

// 🛑 Loop Prevention: Set of visited stream URLs in current session
const visitedFailoverUrls = new Set();


// ============================================================================
// SECTION 3: Unified URL Normalizers & Stream Helpers (DRY)
// ============================================================================

/**
 * Extracts PixelDrain file ID from any URL format (/u/, /l/, /api/file/, /e/)
 */
function getPixelDrainId(url) {
  if (!url || typeof url !== 'string') return null;
  return url.match(/pixeldrain\.com\/(?:u|l|api\/file|e)\/([a-zA-Z0-9_-]+)/)?.[1] || null;
}

/**
 * Returns direct inline video stream URL for PixelDrain
 */
function getPixelDrainApiUrl(url) {
  const pdId = getPixelDrainId(url);
  if (!pdId) return url;
  return `https://pixeldrain.com/api/file/${pdId}`;
}

/**
 * Returns web embed player URL for PixelDrain
 */
function getPixelDrainEmbedUrl(url) {
  const pdId = getPixelDrainId(url);
  if (!pdId) return url;
  return `https://pixeldrain.com/e/${pdId}`;
}

/**
 * Extracts numeric stream ID from Cineru VIP stream URLs
 */
function getCineruStreamId(url) {
  if (!url || typeof url !== 'string') return null;
  return url.match(/cinerustreams\.com\/(?:hlswatch|hlsstream|watch)?\/?(\d+)/)?.[1] || null;
}

/**
 * Returns master HLS (.m3u8) streaming URL for Cineru
 */
function getCineruHlsUrl(url) {
  const cId = getCineruStreamId(url);
  if (!cId) return url;
  return `https://bot.cinerustreams.com/hlsstream/${cId}/master.m3u8`;
}

/**
 * Returns universal Cloud Cinema web embed fallback (VidSrc2 Pro / MultiEmbed)
 */
function getCinemaCloudEmbedFallback() {
  if (currentImdbId && (currentImdbId.startsWith('tt') || /^\d+$/.test(currentImdbId))) {
    return {
      url: isSeriesMode ? `https://vidsrc2.ru/embed/tv/${currentImdbId}/1/1` : `https://vidsrc2.ru/embed/movie/${currentImdbId}`,
      provider: '🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio'
    };
  }
  return null;
}

/**
 * Dynamic storage key for localStorage playback progress
 */
function getPlaybackStorageKey() {
  return (currentVideoTitle || currentStreamUrl || '').replace(/\[.*?\]/g, '').trim().slice(0, 50);
}

/**
 * Formats time in seconds to HH:MM:SS or MM:SS string
 */
function formatPlayerTime(seconds) {
  if (isNaN(seconds) || seconds < 0) return '00:00';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) {
    return `${h}:${m < 10 ? '0' : ''}${m}:${s < 10 ? '0' : ''}${s}`;
  }
  return `${m < 10 ? '0' : ''}${m}:${s < 10 ? '0' : ''}${s}`;
}

function showPlayerLoading() {
  const spinner = document.getElementById('playerLoadingSpinner');
  if (spinner) spinner.classList.remove('hidden');
}

function hidePlayerLoading() {
  const spinner = document.getElementById('playerLoadingSpinner');
  if (spinner) spinner.classList.add('hidden');
}


// ============================================================================
// SECTION 4: HUD & Orientation Management
// ============================================================================

/**
 * Resets control bar visibility timer (auto-hides controls after 10s of inactivity)
 */
function resetPlayerControlsTimer() {
  const card = document.querySelector('.player-modal-pro-2026');
  if (!card) return;
  card.classList.remove('controls-hidden');
  clearTimeout(playerControlsTimer);
  const isPlayingHtml = htmlVideoPlayer && !htmlVideoPlayer.paused && !htmlVideoPlayer.classList.contains('hidden');
  const isPlayingIframe = iframeVideoPlayer && !iframeVideoPlayer.classList.contains('hidden');
  if (isPlayingHtml || isPlayingIframe) {
    playerControlsTimer = setTimeout(() => {
      card.classList.add('controls-hidden');
    }, 10000);
  }
}

/**
 * Toggles device orientation between Portrait and Landscape
 */
async function toggleScreenRotation(stageElem) {
  try {
    if (screen.orientation && screen.orientation.lock) {
      if (screen.orientation.type.includes('landscape')) {
        await screen.orientation.lock('portrait');
        window.showToast('📱 Portrait Mode (Orientation Locked)', 'info');
      } else {
        await screen.orientation.lock('landscape');
        window.showToast('🔄 Landscape Mode (Orientation Locked)', 'info');
      }
      return;
    }
  } catch (e) {
    console.warn('[Orientation] Screen orientation lock fallback:', e);
  }

  if (stageElem) {
    const isRot = stageElem.classList.toggle('screen-rotated-landscape');
    window.showToast(isRot ? '🔄 90° Landscape Rotated' : '📱 Portrait View', 'info');
  }
}

btnPlayerRotateScreen?.addEventListener('click', () => toggleScreenRotation(playerStageFrame));
btnPlayerRotateScreenBottom?.addEventListener('click', () => toggleScreenRotation(playerStageFrame));


// ============================================================================
// SECTION 5: Player Lifecycle & Modal Handlers (Open / Close)
// ============================================================================

/**
 * Closes the player modal and tears down all active streams/iframes/workers
 */
window.closePlayer = function () {
  clearTimeout(playerControlsTimer);

  // 1. Destroy HLS.js workers and buffers
  if (currentHlsInstance) {
    try {
      currentHlsInstance.stopLoad();
      currentHlsInstance.detachMedia();
      currentHlsInstance.destroy();
    } catch (_) { }
    currentHlsInstance = null;
  }

  // 2. Force Stop & Clean HTML5 Video Element
  const videoElem = document.getElementById('htmlVideoPlayer') || htmlVideoPlayer;
  if (videoElem) {
    videoElem.onerror = null;
    videoElem.onended = null;
    videoElem.ontimeupdate = null;
    videoElem.onloadstart = null;
    videoElem.onwaiting = null;
    videoElem.oncanplay = null;
    videoElem.onplaying = null;
    try {
      videoElem.pause();
      videoElem.currentTime = 0;
      videoElem.removeAttribute('src');
      videoElem.load();
    } catch (_) { }
    videoElem.src = '';
    videoElem.classList.add('hidden');
  }

  // 3. Force Stop & Clean Iframe
  const iframeElem = document.getElementById('iframeVideoPlayer') || iframeVideoPlayer;
  if (iframeElem) {
    try {
      iframeElem.onload = null;
      iframeElem.onerror = null;
      iframeElem.src = 'about:blank';
      iframeElem.removeAttribute('src');
      iframeElem.classList.add('hidden');
      iframeElem.remove();
    } catch (_) { }
  }
  const stage = document.getElementById('playerStageFrame') || playerStageFrame;
  if (stage && !document.getElementById('iframeVideoPlayer')) {
    const blankIfr = document.createElement('iframe');
    blankIfr.id = 'iframeVideoPlayer';
    blankIfr.className = 'player-iframe hidden';
    blankIfr.style.width = '100%';
    blankIfr.style.height = '100%';
    blankIfr.style.border = 'none';
    stage.appendChild(blankIfr);
  }

  // 4. Exit PiP and Purge Media
  if (document.pictureInPictureElement) {
    try { document.exitPictureInPicture().catch(() => { }); } catch (_) { }
  }

  const modal = document.getElementById('playerModal') || playerModal;
  if (modal) {
    modal.querySelectorAll('video, audio').forEach(media => {
      try {
        media.pause();
        media.currentTime = 0;
        media.removeAttribute('src');
        media.src = '';
        media.load();
      } catch (_) { }
    });
    modal.querySelectorAll('iframe').forEach(ifr => {
      try {
        ifr.src = 'about:blank';
        ifr.removeAttribute('src');
      } catch (_) { }
    });
  }

  // Global Audio/Video Guard: Ensure no rogue media element continues playing in background
  try {
    document.querySelectorAll('video, audio').forEach(media => {
      try {
        if (!media.closest('#downloadsTab') && !media.closest('#torrentTab')) {
          media.pause();
          media.currentTime = 0;
          media.removeAttribute('src');
          media.src = '';
          media.load();
        }
      } catch (_) { }
    });
  } catch (_) { }

  hidePlayerLoading();
  const existingFallback = document.getElementById('playerProFallbackBanner');
  if (existingFallback) existingFallback.remove();

  const cinemaSelector = document.getElementById('cinemaServerSelector') || cinemaServerSelector;
  if (cinemaSelector) cinemaSelector.classList.add('hidden');

  if (playerStageFrame) {
    playerStageFrame.classList.remove('screen-rotated-landscape');
  }

  // Purge state and failover recursion tracker
  visitedFailoverUrls.clear();
  currentAllQualities = [];
  currentStreamUrl = '';
  currentVideoTitle = '';
  currentCleanTitle = '';
  currentImdbId = '';
  currentTmdbId = '';
  currentSeason = 1;
  currentEpisode = 1;
  isSeriesMode = false;
  currentPosterUrl = '';
  if (playerDirectDownload) playerDirectDownload.onclick = null;
  if (playerUploadDriveBtn) playerUploadDriveBtn.onclick = null;

  // Close Modal and Restore Background Scrolling
  const pModal = document.getElementById('playerModal') || playerModal;
  if (pModal) {
    pModal.classList.add('hidden');
    if (window.modalStack) {
      window.modalStack = window.modalStack.filter(m => m !== pModal);
    }
  }

  const otherOpenModals = Array.from(document.querySelectorAll('.modal-overlay:not(.hidden)')).filter(m => m !== playerModal);
  if (otherOpenModals.length === 0) {
    document.body.classList.remove('modal-open');
    document.body.style.overflow = '';
    document.documentElement.style.overflow = '';
  }

  // Return to Movie Details Modal if user opened player from there
  if (window._activeMovieReturnContext && window.openMovieModal) {
    const returnCtx = window._activeMovieReturnContext;
    window._activeMovieReturnContext = null;
    setTimeout(() => {
      window.openMovieModal(returnCtx.movie, returnCtx.details);
      if (window.showToast) {
        window.showToast(`🎬 Returned to "${returnCtx.movie.title}"`, 'info');
      }
    }, 120);
  }
};

/**
 * Universal Entry Point: Opens Cinema Pro Player with provided options
 */
window.openPlayer = async function ({ title, streamUrl, embedUrl, downloadUrl, driveId, isDrive, type, isZip, allQualities, imdb, tmdb, season, episode, isTv, poster, provider } = {}) {
  console.log('[openPlayer] Opening media:', { title, streamUrl, embedUrl, downloadUrl, type, isZip, provider, tmdb, season, episode });

  const targetModal = document.getElementById('playerModal') || playerModal;
  if (targetModal) {
    targetModal.classList.remove('hidden');
    targetModal.style.display = 'flex';
    document.body.classList.add('modal-open');
    if (window.openModalWithHistory) window.openModalWithHistory(targetModal);
  }
  showPlayerLoading();

  try {
    visitedFailoverUrls.clear();
    const oldFallbackBanner = document.getElementById('playerProFallbackBanner');
    if (oldFallbackBanner) oldFallbackBanner.remove();

    if (navigator.serviceWorker && navigator.serviceWorker.getRegistrations) {
      navigator.serviceWorker.getRegistrations().then(regs => {
        for (const r of regs) { r.unregister(); }
      }).catch(() => {});
    }

    currentVideoTitle = title || 'Playing Media';
    currentStreamUrl = streamUrl || downloadUrl || embedUrl || '';
    currentPosterUrl = poster || '';
    currentTmdbId = tmdb || (currentStreamUrl.match(/\/(?:movie|tv)\/(\d+)/)?.[1]) || '';
    currentSeason = season || 1;
    currentEpisode = episode || 1;

    // 1. Direct HLS Conversion for Cineru VIP Streams
    if (currentStreamUrl.includes('cinerustreams.com') && !currentStreamUrl.includes('.m3u8')) {
      currentStreamUrl = getCineruHlsUrl(currentStreamUrl);
    }

    // 2. Direct API stream conversion for PixelDrain
    if (currentStreamUrl.includes('pixeldrain.com')) {
      currentStreamUrl = getPixelDrainApiUrl(currentStreamUrl);
    }

    // 3. In-Player Stream Resolution for UsersDrive & Portal URLs
    if ((currentStreamUrl.includes('usersdrive.com') || currentStreamUrl.includes('userdrive.org')) &&
      !currentStreamUrl.includes('userdrive.org:8443') &&
      !currentStreamUrl.includes('/d/') &&
      !currentStreamUrl.match(/\.(mp4|mkv|webm|zip|rar)(\?|$)/i)) {
      showPlayerLoading();
      if (playerSourceInfo) {
        playerSourceInfo.innerHTML = '<i class="fa-solid fa-magnifying-glass fa-beat" style="color:#00f2fe;"></i> Finding video...';
      }
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.resolveMovieStream) {
          const res = await window.Capacitor.Plugins.NativeExtractor.resolveMovieStream({ url: currentStreamUrl });
          if (res && (res.streamUrl || res.downloadUrl)) {
            if (playerSourceInfo) {
              playerSourceInfo.innerHTML = '<i class="fa-solid fa-link" style="color:#10b981;"></i> Video link found';
            }
            const resolvedUrl = res.streamUrl || res.downloadUrl;
            if (resolvedUrl && !resolvedUrl.includes('/links/') && resolvedUrl !== currentStreamUrl) {
              console.log('[openPlayer] UsersDrive resolved to direct link:', resolvedUrl);
              currentStreamUrl = resolvedUrl;
              streamUrl = resolvedUrl;
              downloadUrl = resolvedUrl;
              if (playerSourceInfo) {
                playerSourceInfo.innerHTML = '<i class="fa-solid fa-circle-check" style="color:#10b981;"></i> Ready to play';
              }
              if (res.isZip || resolvedUrl.toLowerCase().includes('.zip') || resolvedUrl.toLowerCase().includes('.rar')) {
                isZip = true;
              }
            }
          } else {
            if (playerSourceInfo) {
              playerSourceInfo.innerHTML = '<i class="fa-solid fa-circle-exclamation" style="color:#ef4444;"></i> Download link could not be found';
            }
          }
        }
      } catch (err) {
        console.warn('[openPlayer] UsersDrive resolution notice:', err);
        if (playerSourceInfo) {
          playerSourceInfo.innerHTML = '<i class="fa-solid fa-circle-exclamation" style="color:#ef4444;"></i> Download link could not be found';
        }
      }
    }

    // 3b. In-Player Stream Resolution for Direct Netflix URLs (Bypass if Cinejoy, VidSrc2 or already an embed)
    if ((currentStreamUrl.includes('netflix.com/title/') || currentStreamUrl.includes('netflix.com/watch/') || currentStreamUrl.includes('netmirror')) &&
      !currentStreamUrl.includes('cinejoy.to') &&
      !currentStreamUrl.includes('vidsrc2') &&
      !currentStreamUrl.includes('vidlink') &&
      !currentStreamUrl.includes('multiembed') &&
      !currentStreamUrl.match(/\.(mp4|mkv|m3u8)(\?|$)/i)) {
      showPlayerLoading();
      if (playerSourceInfo) {
        playerSourceInfo.innerHTML = '<i class="fa-solid fa-n fa-beat" style="color:#e50914;"></i> 🍿 Resolving Netflix VIP Stream...';
      }
      try {
        if (window.Capacitor?.Plugins?.NativeExtractor?.resolveNetflixStream) {
          const res = await window.Capacitor.Plugins.NativeExtractor.resolveNetflixStream({ url: currentStreamUrl });
          if (res && res.streamUrl) {
            console.log('[openPlayer] Stream resolved:', res.streamUrl);
            currentStreamUrl = res.streamUrl;
            streamUrl = res.streamUrl;
            embedUrl = res.streamUrl;
            if (res.type) type = res.type;
          }
        }
      } catch (err) {
        console.warn('[openPlayer] Stream resolution notice:', err);
      }
    }

    currentAllQualities = allQualities || [];
    currentImdbId = imdb || (currentStreamUrl.match(/tt\d{7,8}/)?.[0]) || '';
    isSeriesMode = (isTv === true) || (isTv !== false && /S\d+\s*E\d+|Episode|Season/i.test(currentVideoTitle) && !/movie/i.test(type || ''));
    currentCleanTitle = (currentVideoTitle || '')
      .replace(/\[.*?\]/g, '')
      .replace(/\(.*?\)/g, '')
      .replace(/\|.*$/g, '')
      .replace(/sinhala.*$/i, '')
      .replace(/[^a-zA-Z0-9 ]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();

    const activeIframe = document.getElementById('iframeVideoPlayer');

    const isZipArchive = isZip ||
      currentStreamUrl.toLowerCase().includes('.zip') ||
      currentStreamUrl.toLowerCase().includes('.rar') ||
      currentStreamUrl.toLowerCase().includes('.7z') ||
      currentVideoTitle.toLowerCase().includes('.zip') ||
      (type === 'zip');

    const providerStr = (typeof provider !== 'undefined' && provider) ? String(provider).toLowerCase() : '';
    const isTorrentStream = type === 'torrent' ||
      (currentStreamUrl && (
        currentStreamUrl.startsWith('magnet:') ||
        currentStreamUrl.includes('/torrent/download/') ||
        currentStreamUrl.toLowerCase().includes('.torrent')
      )) ||
      providerStr.includes('torrent');

    const isDirectNativeStream = currentStreamUrl &&
      !isZipArchive &&
      !isTorrentStream &&
      (currentStreamUrl.includes('_capacitor_file_') ||
        currentStreamUrl.includes('localhost') ||
        currentStreamUrl.startsWith('file://') ||
        currentStreamUrl.startsWith('/storage') ||
        currentStreamUrl.startsWith('content://') ||
        currentStreamUrl.includes('pixeldrain.com/api/file/') ||
        (currentStreamUrl.includes('cinerustreams.com') && currentStreamUrl.includes('.m3u8')) ||
        currentStreamUrl.includes('sinhalasub.net') ||
        currentStreamUrl.includes('ddl.sinhalasub.net') ||
        currentStreamUrl.includes('cdn.sinhalasub.net') ||
        currentStreamUrl.includes('userdrive.org') ||
        currentStreamUrl.includes('dl.usersdrive.com') ||
        currentStreamUrl.includes('sonic-cloud.online') ||
        currentStreamUrl.includes('shegu.st') ||
        currentStreamUrl.includes('workers.dev') ||
        currentStreamUrl.includes('cloudflarestorage.com') ||
        currentStreamUrl.includes('4khdhub') ||
        currentStreamUrl.includes('.m3u8') ||
        currentStreamUrl.includes('.mpd') ||
        currentStreamUrl.match(/\.(mp4|mkv|webm|mov|avi)(\?|$)/i) ||
        type === 'video') &&
      !currentStreamUrl.includes('/embed') &&
      !currentStreamUrl.includes('vidsrc') &&
      !currentStreamUrl.includes('mega.nz') &&
      !currentStreamUrl.includes('filespayout') &&
      !currentStreamUrl.includes('drive.google') &&
      !currentStreamUrl.includes('cinerustreams.com/watch') &&
      (!currentStreamUrl.includes('cinejoy.to') || currentStreamUrl.includes('.mp4') || currentStreamUrl.includes('.mkv') || type === 'video') &&
      (type !== 'embed' || currentStreamUrl.includes('.m3u8') || currentStreamUrl.includes('.mp4'));

    const activePortalBtn = document.querySelector('.portal-tab.active, .portal-chip.active, .tab-btn.active');
    const activePortalName = activePortalBtn ? (activePortalBtn.dataset?.portal || activePortalBtn.dataset?.source || activePortalBtn.textContent || '').toLowerCase() : '';
    const isCinejoyOrNetflix = (currentStreamUrl && (currentStreamUrl.includes('cinejoy') || currentStreamUrl.includes('vidsrc2') || currentStreamUrl.includes('vidlink') || currentStreamUrl.includes('netflix') || currentStreamUrl.includes('multiembed'))) ||
      (currentVideoTitle && (currentVideoTitle.toLowerCase().includes('cinejoy') || currentVideoTitle.toLowerCase().includes('netflix'))) ||
      (window._activeMovieReturnContext?.movie?.source?.toLowerCase() === 'netflix') ||
      activePortalName.includes('netflix') || activePortalName.includes('cinejoy');

    if (playerTitle) playerTitle.textContent = currentVideoTitle;
    if (playerMetricRes) {
      if (isZipArchive) {
        playerMetricRes.textContent = 'ZIP Archive';
      } else if (isTorrentStream) {
        playerMetricRes.textContent = 'P2P Live';
      } else {
        const matchingQual = (currentAllQualities || []).find(q => (q.streamUrl || q.downloadUrl) === currentStreamUrl);
        playerMetricRes.textContent = matchingQual?.quality || (isDirectNativeStream ? 'Auto' : '--');
      }
    }
    if (playerMetricSpeed) {
      if (isZipArchive) {
        playerMetricSpeed.textContent = 'Direct Download';
      } else if (isTorrentStream) {
        playerMetricSpeed.textContent = 'Torrent Swarm';
      } else if (currentHlsInstance) {
        playerMetricSpeed.textContent = 'HLS Adaptive';
      } else {
        playerMetricSpeed.textContent = isDirectNativeStream ? 'Direct Stream' : 'Cloud Stream';
      }
    }
    if (playerMetricDuration) playerMetricDuration.textContent = '--:--';

    if (btnPlayerReturnToMovie) {
      if (window._activeMovieReturnContext) {
        btnPlayerReturnToMovie.classList.remove('hidden');
        btnPlayerReturnToMovie.onclick = () => window.closePlayer();
      } else {
        btnPlayerReturnToMovie.classList.add('hidden');
      }
    }

    // Reset Video Element State
    if (htmlVideoPlayer) {
      htmlVideoPlayer.onerror = null;
      htmlVideoPlayer.playbackRate = 1.0;
      currentSpeedIdx = 1;
      if (playerSpeedToggleText) playerSpeedToggleText.textContent = '1.0x';
    }

    // Direct Download Hook
    if (playerDirectDownload) {
      playerDirectDownload.onclick = async (e) => {
        e.preventDefault();
        let targetDl = isDrive && driveId
          ? `https://www.googleapis.com/drive/v3/files/${driveId}?alt=media`
          : (downloadUrl || currentStreamUrl || streamUrl || '');

        const isEmbedStream = (type === 'embed' || targetDl.includes('vidsrc2') || targetDl.includes('vidlink') || targetDl.includes('multiembed') || targetDl.includes('vidsrc') || targetDl.includes('2embed') || targetDl.includes('autoembed'));

        if (isEmbedStream) {
          // Look for direct cloud or P2P download in current qualities
          const allQuals = currentAllQualities || [];
          const bestDirect = allQuals.find(x => x.downloadUrl && !x.downloadUrl.includes('vidsrc2') && !x.downloadUrl.includes('vidlink') && !x.downloadUrl.includes('multiembed') && !x.downloadUrl.includes('vidsrc') && !x.downloadUrl.includes('2embed') && !x.downloadUrl.includes('about:blank'));
          if (bestDirect) {
            targetDl = bestDirect.downloadUrl;
          } else if (window.resolveDirectDownloadsForNetflix && (currentImdbId || currentCleanTitle)) {
            if (window.showToast) window.showToast('🔍 Searching Direct Cloud & P2P download pipes...', 'info');
            const resolved = await window.resolveDirectDownloadsForNetflix(null, currentImdbId, currentCleanTitle);
            if (resolved && resolved.length > 0) {
              targetDl = resolved[0].downloadUrl;
              if (window.showToast) window.showToast(`⚡ Direct Pipe found: ${resolved[0].provider} (${resolved[0].quality})`, 'success');
            }
          }
        }

        const isStillEmbed = targetDl.includes('vidsrc2') || targetDl.includes('vidlink') || targetDl.includes('multiembed') || targetDl.includes('vidsrc') || targetDl.includes('2embed') || targetDl.includes('autoembed');

        if (targetDl && !isStillEmbed) {
          if (targetDl.includes('pixeldrain.com')) targetDl = getPixelDrainDownloadUrl(targetDl);
          if (targetDl.includes('drive.google')) targetDl = getGoogleDriveDownloadUrl(targetDl);
          const cleanExt = isZipArchive ? '.zip' : (targetDl.includes('.mkv') ? '.mkv' : (targetDl.startsWith('magnet:') ? '.mp4' : '.mp4'));
          window.triggerDirectDownload(targetDl, `${currentVideoTitle.replace(/[/\\?%*:|"<>]/g, '_')}${cleanExt}`);
        } else {
          if (window.showToast) window.showToast('ℹ️ No direct download file on free CDNs. Streaming is active across 5 VIP Servers!', 'info');
        }
      };
    }

    // Upload to Cloud Drive Hook
    if (playerUploadDriveBtn) {
      if (isDrive || type === 'embed') {
        playerUploadDriveBtn.classList.add('hidden');
      } else {
        playerUploadDriveBtn.classList.remove('hidden');
        playerUploadDriveBtn.onclick = () => {
          if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
            if (window.showInApp404) {
              window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Google Drive upload pipeline is disabled or unauthorized under the active license. Please activate a PRO License Key.');
            }
            return;
          }
          const targetDl = downloadUrl || currentStreamUrl || streamUrl || '';
          if (window.startCloudTransfer && targetDl) {
            window.startCloudTransfer({
              title: currentVideoTitle,
              url: targetDl,
              type: isZipArchive ? 'zip' : 'media',
              quality: isZipArchive ? 'ZIP' : 'HD'
            });
          }
        };
      }
    }

    // Case 0: ZIP Compressed Archive Mode
    if (isZipArchive) {
      hidePlayerLoading();
      if (playerZipBanner) playerZipBanner.classList.remove('hidden');
      if (activeIframe) {
        activeIframe.src = 'about:blank';
        activeIframe.classList.add('hidden');
      }
      if (htmlVideoPlayer) {
        htmlVideoPlayer.pause();
        htmlVideoPlayer.classList.add('hidden');
      }
      if (playerCenterControls) playerCenterControls.classList.add('hidden');
      if (playerControlsOverlay) playerControlsOverlay.classList.add('hidden');
      if (playerSourceInfo) playerSourceInfo.innerHTML = '<i class="fa-solid fa-file-zipper"></i> Compressed ZIP File';

      if (btnZipDirectDownload) {
        btnZipDirectDownload.onclick = () => {
          const targetDl = downloadUrl || currentStreamUrl || streamUrl || '';
          if (targetDl) {
            window.triggerDirectDownload(targetDl, `${currentVideoTitle.replace(/[/\\?%*:|"<>]/g, '_')}.zip`);
          } else {
            window.showToast('Download link not available for this ZIP file', 'error');
          }
        };
      }

      if (btnZipLeechDrive) {
        btnZipLeechDrive.onclick = () => {
          const targetDl = downloadUrl || currentStreamUrl || streamUrl || '';
          if (window.startCloudTransfer && targetDl) {
            window.startCloudTransfer({
              title: currentVideoTitle,
              url: targetDl,
              type: 'zip',
              quality: 'ZIP'
            });
          }
        };
      }
      return;
    }

    if (playerZipBanner) playerZipBanner.classList.add('hidden');

    // Case 1: Google Drive Pipe from Local App Drive
    if (isDrive && driveId) {
      if (activeIframe) {
        activeIframe.src = 'about:blank';
        activeIframe.classList.add('hidden');
      }
      if (playerCenterControls) playerCenterControls.classList.remove('hidden');
      if (playerControlsOverlay) playerControlsOverlay.classList.remove('hidden');

      const gdriveStreamUrl = `https://www.googleapis.com/drive/v3/files/${driveId}?alt=media`;
      if (playerSourceInfo) playerSourceInfo.innerHTML = '<i class="fa-brands fa-google-drive"></i> Google Drive Cloud Pipe';
      loadStreamIntoHtml5(gdriveStreamUrl);
    }
    // Case 2: Direct Video Stream (PixelDrain API, DLServer, HLS .m3u8, direct .mp4)
    else if (isDirectNativeStream) {
      if (activeIframe) {
        activeIframe.src = 'about:blank';
        activeIframe.classList.add('hidden');
      }
      if (playerCenterControls) playerCenterControls.classList.remove('hidden');
      if (playerControlsOverlay) playerControlsOverlay.classList.remove('hidden');

      const playUrl = currentStreamUrl || streamUrl || downloadUrl || '';
      if (playerSourceInfo) playerSourceInfo.innerHTML = '<i class="fa-solid fa-bolt"></i> ⚡ Direct Movie Stream';
      loadStreamIntoHtml5(playUrl);
    }
    // Case 2.5: Native BitTorrent Cinema Stream Mode (libtorrent4j Sequential Engine)
    else if (isTorrentStream) {
      const torrentTarget = (streamUrl && streamUrl.startsWith('magnet:')) ? streamUrl : (downloadUrl && downloadUrl.startsWith('magnet:') ? downloadUrl : streamUrl || downloadUrl || currentStreamUrl || '');
      if (playerSourceInfo) {
        playerSourceInfo.innerHTML = '<i class="fa-solid fa-magnet" style="color: #00f2fe;"></i> ⚡ Native BitTorrent Cinema Streamer';
      }

      if (window.Capacitor?.Plugins?.NativePlayer?.playTorrentStream) {
        window.closePlayer();
        window.showToast(`🍿 Opening Cinema Player for Torrent: ${currentVideoTitle}...`, 'info');
        window.Capacitor.Plugins.NativePlayer.playTorrentStream({
          streamUrl: torrentTarget,
          title: currentVideoTitle,
          poster: currentPosterUrl || ''
        }).catch(err => {
          console.warn('[NativeTorrent Player Error]', err);
          window.showToast(`Torrent streaming notice: ${err.message || err}`, 'warning');
        });
        return;
      }

      if (window.Capacitor?.Plugins?.NativeTorrent?.getStreamUrl) {
        window.Capacitor.Plugins.NativeTorrent.getStreamUrl({
          url: torrentTarget,
          title: currentVideoTitle
        }).then(res => {
          if (res?.streamUrl) {
            loadStreamIntoHtml5(res.streamUrl);
          } else {
            window.showToast('Torrent stream initialization in progress...', 'info');
          }
        }).catch(err => {
          console.warn('[Torrent Proxy Stream Error]', err);
        });
        return;
      }

      window.showToast('Native BitTorrent streaming requires Android APK', 'info');
      hidePlayerLoading();
    }
    // Case 3: Cloud Web Embed Stream (VidLink Pro, MultiEmbed, MEGA, Google Drive Preview)
    else {
      await loadWebEmbedPlayer(embedUrl || currentStreamUrl);
    }
  } catch (err) {
    console.error('[openPlayer] Execution error:', err);
    hidePlayerLoading();
    if (window.showToast) window.showToast('Player notice: ' + err.message, 'warning');
  }
};


// ============================================================================
// SECTION 6: HTML5 Video & HLS.js Core Streaming Engine
// ============================================================================

/**
 * Loads video stream into HTML5 Video player or initializes HLS.js pipeline
 */
async function loadStreamIntoHtml5(url) {
  const videoElem = document.getElementById('htmlVideoPlayer') || htmlVideoPlayer;
  if (!videoElem || !url) return;

  showPlayerLoading();

  // 1. PixelDrain Direct Clean Streaming URL (Force inline playback by removing ?download)
  if (url.includes('pixeldrain.com')) {
    url = getPixelDrainApiUrl(url).replace('?download', '').replace('&download', '');
  }

  // 2. Web Embed Host Interception (MEGA, Google Drive Preview, FilesPayout, Cineru Watch)
  if (url.includes('mega.nz') ||
    url.includes('mega.io') ||
    url.includes('mega.co.nz') ||
    url.includes('drive.google.com') ||
    url.includes('drive.usercontent.google.com') ||
    url.includes('filespayout') ||
    (url.includes('cinerustreams.com') && !url.includes('.m3u8'))) {
    await loadWebEmbedPlayer(url);
    return;
  }

  // 🚀 Selective Proxy Routing: PixelDrain, Sinhalasub (DDL/CDN/LK), UsersDrive, Google Drive Stream
  let finalPlayUrl = url;
  const needsProxy = url.includes('pixeldrain.com') ||
    url.includes('sinhalasub.net') ||
    url.includes('sinhalasub.lk') ||
    url.includes('ddl.sinhalasub.net') ||
    url.includes('cdn.sinhalasub.net') ||
    url.includes('dlserver') ||
    url.includes('cinerustreams.com') ||
    url.includes('cineru') ||
    url.includes('sub.lk') ||
    url.includes('usersdrive') ||
    url.includes('userdrive') ||
    url.includes('workers.dev') ||
    url.includes('shegu.st') ||
    url.includes('drive.google.com/uc?');

  if (needsProxy && window.Capacitor?.Plugins?.NativePlayer?.getProxiedStreamUrl && !url.includes('127.0.0.1') && !url.includes('blob:') && !url.includes('data:')) {
    try {
      const pRes = await window.Capacitor.Plugins.NativePlayer.getProxiedStreamUrl({ url });
      if (pRes && (pRes.url || pRes.proxiedUrl)) {
        finalPlayUrl = pRes.url || pRes.proxiedUrl;
      }
    } catch (_) { }
  }

  if (currentHlsInstance) {
    try { currentHlsInstance.destroy(); } catch (_) { }
    currentHlsInstance = null;
  }

  videoElem.removeAttribute('src');
  videoElem.classList.remove('hidden');
  videoElem.setAttribute('playsinline', 'true');
  videoElem.setAttribute('webkit-playsinline', 'true');
  videoElem.setAttribute('preload', 'auto');

  const isHls = finalPlayUrl.includes('.m3u8') || finalPlayUrl.includes('/hls/') || finalPlayUrl.includes('type=m3u8') || (finalPlayUrl.includes('cinerustreams.com') && finalPlayUrl.includes('.m3u8'));

  // Error listener with automatic unproxied retry before failover
  let hasTriedDirect = false;
  videoElem.onerror = function (err) {
    console.warn('[HTML5 Video Player Error]', err, videoElem.error);
    if (!hasTriedDirect && finalPlayUrl !== url && !isHls) {
      hasTriedDirect = true;
      console.log('[HTML5 Player] Retrying with direct unproxied stream URL:', url);
      videoElem.src = url;
      videoElem.load();
      videoElem.play().then(() => hidePlayerLoading()).catch(() => { });
      return;
    }
    hidePlayerLoading();
    handleStreamFailover(url);
  };

  videoElem.onloadstart = () => showPlayerLoading();
  videoElem.onwaiting = () => showPlayerLoading();
  videoElem.oncanplay = () => hidePlayerLoading();
  videoElem.onplaying = () => hidePlayerLoading();

  if (isHls) {
    try { await window.ensureHlsLoaded(); } catch (_) { }
  }

  if (isHls && window.Hls && Hls.isSupported()) {
    const hls = new Hls({
      enableWorker: true,
      lowLatencyMode: false, // Must be FALSE for VOD movies to unlock 100% Wi-Fi / 4G download speed!
      backBufferLength: 30, // 30s quick rewind buffer
      maxBufferLength: 90, // 1.5 min forward buffer (rock-solid zero-buffering)
      maxMaxBufferLength: 180, // Up to 3 min max
      maxBufferSize: 48 * 1024 * 1024, // 48 MB RAM buffer (optimized: saves 80MB RAM)
      maxLoadingDelay: 1, // Instant fragment fetching
      startFragPrefetch: true, // Immediately prefetch next fragment
      progressive: true,
      manifestLoadingTimeOut: 12000,
      manifestLoadingMaxRetry: 4,
      levelLoadingTimeOut: 12000,
      levelLoadingMaxRetry: 4,
      fragLoadingTimeOut: 20000,
      fragLoadingMaxRetry: 6,
      capLevelToPlayerSize: true
    });
    hls.loadSource(finalPlayUrl);
    hls.attachMedia(videoElem);
    currentHlsInstance = hls;

    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      hidePlayerLoading();
      videoElem.play().catch(() => { });
    });
    hls.on(Hls.Events.FRAG_BUFFERED, () => {
      hidePlayerLoading();
    });
    hls.on(Hls.Events.ERROR, (event, data) => {
      if (data && data.fatal) {
        switch (data.type) {
          case Hls.ErrorTypes.NETWORK_ERROR:
            console.warn('[Hls.js] Network hiccup, auto-recovering...');
            hls.startLoad();
            break;
          case Hls.ErrorTypes.MEDIA_ERROR:
            console.warn('[Hls.js] Media hiccup, auto-recovering...');
            hls.recoverMediaError();
            break;
          default:
            console.warn('[Hls.js] Fatal unrecoverable error:', data);
            hls.destroy();
            hidePlayerLoading();
            handleStreamFailover(url);
            break;
        }
      }
    });
  } else {
    videoElem.preload = 'auto';
    videoElem.src = finalPlayUrl;
    videoElem.load();
    const instantPlay = () => {
      hidePlayerLoading();
      if (videoElem.paused) videoElem.play().catch(() => { });
    };
    videoElem.addEventListener('loadeddata', instantPlay, { once: true });
    videoElem.addEventListener('canplay', instantPlay, { once: true });
    videoElem.play().then(() => hidePlayerLoading()).catch(e => {
      console.warn('[Video Autoplay Notice]', e);
    });
  }

  setupHtml5VideoEvents();
}


// ============================================================================
// SECTION 7: Web Embed & Cloud Cinema Loader
// ============================================================================

/**
 * Loads cloud video providers inside iframe player
 */
async function loadWebEmbedPlayer(rawEmbedUrl) {
  const activeIframe = document.getElementById('iframeVideoPlayer');
  if (currentHlsInstance) {
    try { currentHlsInstance.destroy(); } catch (_) { }
    currentHlsInstance = null;
  }
  if (htmlVideoPlayer) {
    htmlVideoPlayer.onerror = null;
    htmlVideoPlayer.pause();
    htmlVideoPlayer.classList.add('hidden');
    htmlVideoPlayer.removeAttribute('src');
    htmlVideoPlayer.src = '';
  }

  if (playerCenterControls) playerCenterControls.classList.add('hidden');
  if (playerControlsOverlay) playerControlsOverlay.classList.add('hidden');

  let targetEmbed = rawEmbedUrl || currentStreamUrl || '';
  const activePortalBtn = document.querySelector('.portal-tab.active, .portal-chip.active, .tab-btn.active');
  const activePortalName = activePortalBtn ? (activePortalBtn.dataset?.portal || activePortalBtn.dataset?.source || activePortalBtn.textContent || '').toLowerCase() : '';
  const isCinejoyOrNetflixEmbed = (targetEmbed && (targetEmbed.includes('cinejoy') || targetEmbed.includes('vidsrc2') || targetEmbed.includes('vidlink') || targetEmbed.includes('netflix') || targetEmbed.includes('multiembed'))) ||
    (currentVideoTitle && (currentVideoTitle.toLowerCase().includes('cinejoy') || currentVideoTitle.toLowerCase().includes('netflix'))) ||
    (window._activeMovieReturnContext?.movie?.source?.toLowerCase() === 'netflix') ||
    activePortalName.includes('netflix') || activePortalName.includes('cinejoy');
  let provName = isCinejoyOrNetflixEmbed ? '🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream' : 'VIP Cinema Player';

  // 1. Direct HLS Conversion for Cineru VIP Streams
  if (targetEmbed.includes('cinerustreams.com')) {
    if (targetEmbed.includes('.m3u8')) {
      // 🛑 Avoid ping-pong loop: If this stream already failed in HTML5, do not re-dispatch!
      if (!visitedFailoverUrls.has(targetEmbed)) {
        if (activeIframe) activeIframe.classList.add('hidden');
        loadStreamIntoHtml5(targetEmbed);
        return;
      }
    }
    provName = 'Cineru VIP Player';
  } else if (targetEmbed.includes('vidsrc2.ru') || targetEmbed.includes('vidlink.pro')) {
    provName = '🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio';
    const mediaId = currentTmdbId || currentImdbId || '';
    if (targetEmbed.includes('/tv/') || isSeriesMode) {
      const tvMatch = targetEmbed.match(/(?:vidsrc2\.ru\/embed\/tv|vidlink\.pro\/tv)\/([a-zA-Z0-9_-]+)(?:\/(\d+)\/(\d+))?/);
      let id = (mediaId && mediaId !== 'search') ? mediaId : (tvMatch ? tvMatch[1] : mediaId);
      const s = (tvMatch && tvMatch[2]) ? tvMatch[2] : (currentSeason || '1');
      const e = (tvMatch && tvMatch[3]) ? tvMatch[3] : (currentEpisode || '1');
      if (id) {
        targetEmbed = `https://vidsrc2.ru/embed/tv/${id}/${s}/${e}`;
      } else {
        targetEmbed = targetEmbed.replace('vidlink.pro/tv', 'vidsrc2.ru/embed/tv');
      }
    } else {
      const mMatch = targetEmbed.match(/(?:vidsrc2\.ru\/embed\/movie|vidlink\.pro\/movie)\/([a-zA-Z0-9_-]+)/);
      let id = (mediaId && mediaId !== 'search') ? mediaId : (mMatch ? mMatch[1] : mediaId);
      if (id) {
        targetEmbed = `https://vidsrc2.ru/embed/movie/${id}`;
      } else {
        targetEmbed = targetEmbed.replace('vidlink.pro/movie', 'vidsrc2.ru/embed/movie').split('?')[0];
      }
    }
  } else if (targetEmbed.includes('cinejoy.to')) {
    provName = '🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream';
    const isExplicitTvUrl = targetEmbed.includes('/tv/');
    const isExplicitMovieUrl = targetEmbed.includes('/movie/');
    const tmdbMatch = targetEmbed.match(/\/(?:movie|tv)\/([a-zA-Z0-9_-]+)(?:\/(\d+)\/(\d+))?/);
    let mediaId = tmdbMatch ? tmdbMatch[1] : (currentTmdbId || '');
    const isTv = isExplicitTvUrl || (!isExplicitMovieUrl && isSeriesMode);
    const s = (tmdbMatch && tmdbMatch[2]) ? tmdbMatch[2] : (currentSeason || '1');
    const e = (tmdbMatch && tmdbMatch[3]) ? tmdbMatch[3] : (currentEpisode || '1');

    // If mediaId is missing, non-numeric, 'search', or IMDb code ('tt...')
    if (!mediaId || mediaId === 'search' || !/^\d+$/.test(mediaId)) {
      let resolvedTmdb = (currentTmdbId && /^\d+$/.test(currentTmdbId)) ? currentTmdbId : '';
      const imdbToQuery = currentImdbId || (mediaId && mediaId.startsWith('tt') ? mediaId : '');
      if (!resolvedTmdb && imdbToQuery) {
        try {
          const findUrl = `https://api.themoviedb.org/3/find/${imdbToQuery}?api_key=8476a7ab80ad76f0936744df0430e67c&external_source=imdb_id`;
          const findRes = await fetch(findUrl, { signal: AbortSignal.timeout(3000) }).then(r => r.json()).catch(() => null);
          const found = isTv ? (findRes?.tv_results?.[0] || findRes?.movie_results?.[0]) : (findRes?.movie_results?.[0] || findRes?.tv_results?.[0]);
          if (found?.id) {
            resolvedTmdb = String(found.id);
            currentTmdbId = resolvedTmdb;
          }
        } catch (_) { }
      }
      if (!resolvedTmdb && currentCleanTitle) {
        try {
          const searchUrl = `https://api.themoviedb.org/3/search/multi?api_key=8476a7ab80ad76f0936744df0430e67c&query=${encodeURIComponent(currentCleanTitle)}`;
          const searchRes = await fetch(searchUrl, { signal: AbortSignal.timeout(3000) }).then(r => r.json()).catch(() => null);
          const found = searchRes?.results?.[0];
          if (found?.id) {
            resolvedTmdb = String(found.id);
            currentTmdbId = resolvedTmdb;
          }
        } catch (_) { }
      }
      if (resolvedTmdb) {
        mediaId = resolvedTmdb;
      }
    }

    if (mediaId && mediaId !== 'search' && /^\d+$/.test(mediaId)) {
      targetEmbed = isTv
        ? `https://cinejoy.to/watch/tv/${mediaId}/${s}/${e}`
        : `https://cinejoy.to/watch/movie/${mediaId}`;
    }
  } else if (targetEmbed.includes('multiembed.mov')) {
    provName = '🍿 Server 3: MultiEmbed VIP – 1080p / 4K UHD Fast Cloud Stream';
  } else if (targetEmbed.includes('vidsrc.pm') || targetEmbed.includes('vidsrc.net')) {
    provName = '🌟 Server 5: VidSrc PM – High-Speed Backup Cinema Mirror';
  } else if (targetEmbed.includes('vidsrc')) {
    provName = '🚀 Server 3: VidSrc VIP – Ultra-Fast 1080p High-Speed CDN';
  } else if (targetEmbed.includes('2embed')) {
    provName = '💎 Server 4: 2Embed CC – 1080p Global CDN Mirror';
  } else if (targetEmbed.includes('autoembed.co')) {
    provName = 'AutoEmbed Cloud Player';
  } else if (targetEmbed.includes('drive.google.com') || targetEmbed.includes('drive.usercontent.google.com')) {
    const gId = targetEmbed.match(/(?:file\/d\/|open\?id=|uc\?id=|download\?id=|id=)([-\w]{25,})/)?.[1] || targetEmbed.match(/[-\w]{25,}/)?.[0];
    if (gId) targetEmbed = `https://drive.google.com/file/d/${gId}/preview`;
    provName = 'Google Drive Cloud Player';
  } else if (targetEmbed.includes('mega.nz') || targetEmbed.includes('mega.co.nz') || targetEmbed.includes('mega.io')) {
    if (!targetEmbed.includes('/embed')) {
      const megaMatch = targetEmbed.match(/mega\.(?:nz|io|co\.nz)\/(?:file|embed)\/([a-zA-Z0-9#_-]+)/);
      if (megaMatch && megaMatch[1]) {
        targetEmbed = `https://mega.nz/embed/${megaMatch[1]}`;
      } else {
        targetEmbed = targetEmbed.replace('/file/', '/embed/').replace('/#!', '/embed#!').replace('/#', '/embed#!').replace('#!', 'embed#!');
      }
    }
    provName = 'MEGA Cloud Player';
  } else if (targetEmbed.includes('usersdrive.com') || targetEmbed.includes('userdrive')) {
    hidePlayerLoading();
    if (activeIframe) {
      activeIframe.src = 'about:blank';
      activeIframe.classList.add('hidden');
    }
    showInPlayerProFallback(targetEmbed);
    return;
  } else if (targetEmbed.includes('filespayout')) {
    const fpId = targetEmbed.match(/filespayouts?\.com\/(?:d\/|e\/|download\/)?([a-zA-Z0-9_-]+)/)?.[1];
    if (fpId) targetEmbed = `https://filespayouts.com/e/${fpId}`;
    provName = 'FilesPayouts Cloud Player';
  } else if (targetEmbed.includes('pixeldrain.com')) {
    targetEmbed = getPixelDrainEmbedUrl(targetEmbed);
    provName = 'PixelDrain Cloud Player';
  }

  // Guarantee fallback ONLY if targetEmbed is completely unknown / unhandled
  if (!targetEmbed.includes('cinejoy.to') &&
    !targetEmbed.includes('vidsrc2.ru') &&
    !targetEmbed.includes('vidlink.pro') &&
    !targetEmbed.includes('vidsrc') &&
    !targetEmbed.includes('autoembed') &&
    !targetEmbed.includes('2embed') &&
    !targetEmbed.includes('mega.nz') &&
    !targetEmbed.includes('mega.io') &&
    !targetEmbed.includes('mega.co.nz') &&
    !targetEmbed.includes('drive.google') &&
    !targetEmbed.includes('filespayout') &&
    !targetEmbed.includes('pixeldrain.com') &&
    !targetEmbed.includes('cinerustreams.com') &&
    !targetEmbed.includes('usersdrive.com')) {
    const fallback = getCinemaCloudEmbedFallback();
    if (fallback) {
      targetEmbed = fallback.url;
      provName = isCinejoyOrNetflixEmbed ? '🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream' : fallback.provider;
    }
  }

  // 🛡️ Strict In-App Ad Popup Defusal (Zero popups / ad redirections while player is active)
  if (!window.__origWindowOpen) {
    window.__origWindowOpen = window.open;
  }
  window.open = function (url, target, features) {
    const isPlayerOpen = document.getElementById('playerModal') && !document.getElementById('playerModal').classList.contains('hidden');
    if (isPlayerOpen) {
      console.warn('[AdBlock Shield] Completely blocked ad popup while player modal is active:', url);
      return null;
    }
    if (!url || typeof url !== 'string') return null;
    if (window.isKnownAdUrl && window.isKnownAdUrl(url)) {
      console.warn('[AdBlock Shield] Blocked known ad URL popup:', url);
      return null;
    }
    if (window.__origWindowOpen) {
      return window.__origWindowOpen.apply(window, arguments);
    }
    return null;
  };

  // Defuse any lingering fallback banner
  const lingeringFallback = document.getElementById('playerProFallbackBanner');
  if (lingeringFallback) lingeringFallback.remove();

  const finalIframeUrl = targetEmbed;
  showPlayerLoading();
  const spinnerTimer = setTimeout(() => hidePlayerLoading(), 2000);

  const currentIfr = document.getElementById('iframeVideoPlayer');
  if (currentIfr) {
    try {
      currentIfr.onload = null;
      currentIfr.onerror = null;
    } catch (_) { }

    const newIframe = document.createElement('iframe');
    newIframe.id = 'iframeVideoPlayer';
    newIframe.removeAttribute('sandbox');
    newIframe.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen');
    newIframe.setAttribute('allowfullscreen', 'true');
    newIframe.style.width = '100%';
    newIframe.style.height = '100%';
    newIframe.style.border = 'none';
    newIframe.classList.remove('hidden');

    newIframe.onload = () => {
      clearTimeout(spinnerTimer);
      hidePlayerLoading();
    };
    newIframe.onerror = (e) => {
      clearTimeout(spinnerTimer);
      hidePlayerLoading();
      console.warn('[Iframe Player] Load event notice (non-fatal):', e);
    };

    // Attach to DOM FIRST
    currentIfr.replaceWith(newIframe);

    // Set src AFTER attaching to DOM to initiate navigation cleanly without race conditions
    newIframe.src = finalIframeUrl;
  }
  if (playerSourceInfo) playerSourceInfo.innerHTML = `<i class="fa-solid fa-film"></i> ${provName}`;
}


// ============================================================================
// SECTION 8: Intelligent Failover & Anti-Loop Engine
// ============================================================================

/**
 * Handles failed streams gracefully by checking hardware ExoPlayer, alternate qualities, or web fallbacks
 */
function handleStreamFailover(failedUrl) {
  console.warn('[Player] Stream notice, attempting in-player option or web fallback:', failedUrl);

  // 🛑 Loop Prevention & Recursion Guard (Stops ping-pong fallback cycles)
  if (!failedUrl || visitedFailoverUrls.has(failedUrl) || visitedFailoverUrls.size >= 4) {
    console.warn('[Player Failover] Loop prevented or max failover attempts reached for:', failedUrl);
    showInPlayerProFallback(failedUrl);
    return;
  }
  visitedFailoverUrls.add(failedUrl);

  // 🧹 Auto-delete failed/broken stream link from disk cache immediately
  if (failedUrl && window.removeStreamDiskCache) {
    window.removeStreamDiskCache(failedUrl);
  }
  if (currentStreamUrl && window.removeStreamDiskCache) {
    window.removeStreamDiskCache(currentStreamUrl);
  }

  // 1. If MKV / HEVC, Cloudflare R2, Workers mirror, or PixelDrain video failed in HTML5 player, launch Android Hardware Cinema Player (ExoPlayer)
  const isMkvStream = (failedUrl && (failedUrl.includes('.mkv') || failedUrl.includes('workers.dev') || failedUrl.includes('cloudflarestorage.com') || failedUrl.includes('shegu.st') || failedUrl.includes('4khdhub') || failedUrl.includes('pixeldrain.com/api/file/'))) ||
    (currentStreamUrl && (currentStreamUrl.includes('.mkv') || currentStreamUrl.includes('workers.dev') || currentStreamUrl.includes('shegu.st'))) ||
    (currentVideoTitle && (currentVideoTitle.toLowerCase().includes('.mkv') || currentVideoTitle.toLowerCase().includes('cinejoy')));
  if (isMkvStream && window.Capacitor?.Plugins?.NativePlayer?.playVideo) {
    console.log('[Player] MKV container / HEVC detected. Launching Native Hardware Cinema Player:', failedUrl || currentStreamUrl);
    if (window.showToast) window.showToast('🍿 MKV requires Hardware Cinema Player. Launching Cinema Mode...', 'info');
    window.Capacitor.Plugins.NativePlayer.playVideo({
      url: failedUrl || currentStreamUrl,
      streamUrl: failedUrl || currentStreamUrl,
      title: currentVideoTitle,
      poster: currentPosterUrl || '',
      type: 'video'
    });
    window.closePlayer();
    return;
  }

  // 1b. If URL is PixelDrain on Web, fall back to official PixelDrain web embed player
  if (failedUrl && failedUrl.includes('pixeldrain')) {
    const pdEmbed = getPixelDrainEmbedUrl(failedUrl);
    if (pdEmbed && !visitedFailoverUrls.has(pdEmbed)) {
      console.log('[Player] Falling back to PixelDrain Web Embed Player:', pdEmbed);
      loadWebEmbedPlayer(pdEmbed);
      return;
    }
  }

  // 1c. If URL is an embed host (Google Drive preview, MEGA embed, FilesPayout, Cineru Watch)
  if (failedUrl && (failedUrl.includes('drive.google') || failedUrl.includes('mega.nz') || failedUrl.includes('mega.io') || failedUrl.includes('filespayout') || (failedUrl.includes('cinerustreams') && !failedUrl.includes('.m3u8')))) {
    loadWebEmbedPlayer(failedUrl);
    return;
  }

  // 2. Try alternate direct stream quality from available list if one exists (AND not already visited/failed!)
  const altQual = (currentAllQualities || []).find(q => {
    const qUrl = q.streamUrl || q.downloadUrl;
    return qUrl &&
      !visitedFailoverUrls.has(qUrl) &&
      !visitedFailoverUrls.has(q.downloadUrl) &&
      (qUrl.includes('pixeldrain') || qUrl.includes('cinerustreams') || qUrl.includes('sinhalasub.net') || qUrl.includes('workers.dev') || qUrl.includes('drive.google'));
  });
  if (altQual) {
    console.log('[Player] Switching to unvisited alternative stream quality:', altQual);
    if (window.showToast) window.showToast(`⚡ Auto-switching to ${altQual.provider || altQual.label || 'alternate stream'}...`, 'info');
    loadStreamIntoHtml5(altQual.streamUrl || altQual.downloadUrl);
    return;
  }

  // 3. Web Cinema Stream Fallback (VIP VidLink / MultiEmbed instant cinema stream)
  const cloudFallback = getCinemaCloudEmbedFallback();
  if (cloudFallback && !visitedFailoverUrls.has(cloudFallback.url)) {
    if (window.showToast) window.showToast('⚡ Connecting to VIP Cinema Cloud Player...', 'info');
    loadWebEmbedPlayer(cloudFallback.url);
    return;
  }

  // 4. Default In-Player Pro Fallback (Only if no web stream or embed exists)
  showInPlayerProFallback(failedUrl);
}

/**
 * Displays overlay banner allowing user to launch stream directly in Native Hardware Player (ExoPlayer)
 */
function showInPlayerProFallback(failedUrl) {
  hidePlayerLoading();
  const videoElem = document.getElementById('htmlVideoPlayer') || htmlVideoPlayer;
  if (videoElem) {
    try { videoElem.pause(); } catch (_) { }
    videoElem.classList.add('hidden');
  }

  const existingFallback = document.getElementById('playerProFallbackBanner');
  if (existingFallback) existingFallback.remove();

  // Find best available direct download link from all qualities
  const allQuals = currentAllQualities || [];
  const bestDlQual = allQuals.find(x => x.downloadUrl && (x.downloadUrl.includes('pixeldrain') || x.provider?.toLowerCase().includes('pixeldrain')))
    || allQuals.find(x => x.downloadUrl && (x.downloadUrl.includes('dlserver') || x.downloadUrl.includes('sinhalasub.net') || x.provider?.toLowerCase().includes('cdn') || x.provider?.toLowerCase().includes('dlserver')))
    || allQuals.find(x => x.downloadUrl && (x.downloadUrl.includes('workers.dev') || x.downloadUrl.includes('drive.google')))
    || allQuals.find(x => x.downloadUrl && (x.downloadUrl.includes('.mp4') || x.downloadUrl.includes('.mkv')))
    || allQuals.find(x => x.downloadUrl && !x.downloadUrl.includes('cinerustreams') && !x.downloadUrl.includes('vidsrc2') && !x.downloadUrl.includes('vidlink') && !x.downloadUrl.includes('vidsrc') && !x.downloadUrl.includes('usersdrive') && !x.downloadUrl.includes('filespayout'));

  let targetDl = bestDlQual?.downloadUrl || currentStreamUrl || failedUrl || '';
  if (targetDl.includes('pixeldrain.com')) targetDl = getPixelDrainDownloadUrl(targetDl);
  if (targetDl.includes('drive.google')) targetDl = getGoogleDriveDownloadUrl(targetDl);

  const hasValidDl = targetDl && !targetDl.includes('vidsrc2.ru') && !targetDl.includes('vidlink.pro') && !targetDl.includes('cinerustreams.com') && !targetDl.includes('about:blank');
  const dlLabel = bestDlQual?.quality || 'Full HD';

  const fallbackBanner = document.createElement('div');
  fallbackBanner.id = 'playerProFallbackBanner';
  fallbackBanner.style.cssText = 'position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; padding:24px; text-align:center; background: radial-gradient(circle, rgba(0, 242, 254, 0.14) 0%, rgba(10, 15, 29, 0.98) 100%); z-index:20;';
  fallbackBanner.innerHTML = `
    <div style="width: 64px; height: 64px; border-radius: 50%; background: rgba(0, 242, 254, 0.15); display: flex; align-items: center; justify-content: center; margin-bottom: 14px; border: 1px solid rgba(0, 242, 254, 0.4); box-shadow: 0 0 25px rgba(0, 242, 254, 0.3);">
      <i class="fa-solid fa-cloud-arrow-down" style="font-size: 1.8rem; color: #00f2fe;"></i>
    </div>
    <h4 style="font-size: 1.15rem; font-weight: 700; color: #fff; margin-bottom: 8px;">Online Stream Unavailable</h4>
    <p style="font-size: 0.84rem; color: #94a3b8; max-width: 320px; margin-bottom: 18px; line-height: 1.4;">Live playback is not supported on this server, but Direct High-Speed Download is ready.</p>
    <div style="display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; max-width: 380px;">
      ${hasValidDl ? `
      <button id="btnProDirectDlLaunch" style="background: linear-gradient(135deg, #00f2fe, #4facfe); color: #05070b; font-weight: 800; padding: 11px 22px; border-radius: 20px; font-size: 0.9rem; border: none; cursor: pointer; display: flex; align-items: center; gap: 8px; box-shadow: 0 4px 18px rgba(0, 242, 254, 0.35);">
        <i class="fa-solid fa-download"></i> <span>Direct Download (${dlLabel})</span>
      </button>
      <button id="btnProLeechDriveLaunch" style="background: rgba(0, 242, 254, 0.15); color: #00f2fe; font-weight: 700; padding: 10px 18px; border-radius: 20px; font-size: 0.85rem; border: 1px solid rgba(0, 242, 254, 0.35); cursor: pointer; display: flex; align-items: center; gap: 8px;">
        <i class="fa-solid fa-cloud-arrow-up"></i> <span>Leech to Drive (0 MB)</span>
      </button>
      ` : ''}
      <button id="btnManualProLaunch" style="background: rgba(255,255,255,0.08); color: #e2e8f0; padding: 10px 18px; border-radius: 20px; font-size: 0.85rem; border: 1px solid rgba(255,255,255,0.2); cursor: pointer; display: flex; align-items: center; gap: 6px;">
        <i class="fa-solid fa-play"></i> <span>Hardware Player</span>
      </button>
      <button id="btnManualProClose" style="background: rgba(255,255,255,0.05); color: #94a3b8; padding: 10px 16px; border-radius: 20px; font-size: 0.85rem; border: 1px solid rgba(255,255,255,0.1); cursor: pointer;">
        <span>Close</span>
      </button>
    </div>
  `;

  const stage = document.getElementById('playerStageFrame') || playerStageFrame;
  if (stage) stage.appendChild(fallbackBanner);

  document.getElementById('btnProDirectDlLaunch')?.addEventListener('click', () => {
    if (targetDl && window.triggerDirectDownload) {
      const ext = targetDl.includes('.mkv') ? '.mkv' : '.mp4';
      const cleanTitle = (currentVideoTitle || 'Movie').replace(/[/\\?%*:|"<>]/g, '_');
      window.triggerDirectDownload(targetDl, `${cleanTitle}_${dlLabel}${ext}`);
      window.closePlayer();
    }
  });

  document.getElementById('btnProLeechDriveLaunch')?.addEventListener('click', () => {
    if (targetDl && window.startCloudTransfer) {
      window.startCloudTransfer({
        title: `${currentVideoTitle} [${dlLabel}]`,
        url: targetDl,
        type: 'movie',
        quality: dlLabel
      });
      window.closePlayer();
    }
  });

  document.getElementById('btnManualProLaunch')?.addEventListener('click', () => {
    if (window.Capacitor?.Plugins?.NativePlayer?.playVideo) {
      window.Capacitor.Plugins.NativePlayer.playVideo({
        url: failedUrl || currentStreamUrl,
        streamUrl: failedUrl || currentStreamUrl,
        title: currentVideoTitle,
        poster: currentPosterUrl || '',
        type: 'video'
      });
      window.closePlayer();
    }
  });

  document.getElementById('btnManualProClose')?.addEventListener('click', () => {
    window.closePlayer();
  });
}


// ============================================================================
// SECTION 9: Scrubber & Media Controls
// ============================================================================

/**
 * Sets up playback event listeners for time tracking, duration, resume, and controls
 */
function setupHtml5VideoEvents() {
  if (!htmlVideoPlayer) return;

  let didResume = false;

  htmlVideoPlayer.onloadedmetadata = () => {
    const dur = htmlVideoPlayer.duration || 0;
    if (playerMetricDuration) playerMetricDuration.textContent = formatPlayerTime(dur);
    if (playerTimeText) playerTimeText.textContent = `00:00 / ${formatPlayerTime(dur)}`;

    if (playerMetricRes && htmlVideoPlayer.videoWidth > 0 && htmlVideoPlayer.videoHeight > 0) {
      const w = htmlVideoPlayer.videoWidth;
      const h = htmlVideoPlayer.videoHeight;
      if (w >= 3840 || h >= 2160) {
        playerMetricRes.textContent = '4K UHD';
      } else if (w >= 1920 || h >= 1080) {
        playerMetricRes.textContent = '1080p FHD';
      } else if (w >= 1280 || h >= 720) {
        playerMetricRes.textContent = '720p HD';
      } else {
        playerMetricRes.textContent = `${w}x${h}`;
      }
    }

    try {
      const storageKey = getPlaybackStorageKey();
      const history = JSON.parse(localStorage.getItem('cdl_playback_history') || '{}');
      const saved = history[storageKey];
      if (saved && saved.time > 10 && !didResume) {
        didResume = true;
        htmlVideoPlayer.currentTime = saved.time;
        if (window.showToast) {
          window.showToast(`⏱️ Resumed playback from ${formatPlayerTime(saved.time)}`, 'info');
        }
      }
    } catch (_) { }
  };

  htmlVideoPlayer.onprogress = () => {
    if (playerMetricSpeed && htmlVideoPlayer.buffered.length > 0) {
      const cur = htmlVideoPlayer.currentTime || 0;
      let forwardSec = 0;
      for (let i = 0; i < htmlVideoPlayer.buffered.length; i++) {
        if (htmlVideoPlayer.buffered.start(i) <= cur && cur <= htmlVideoPlayer.buffered.end(i)) {
          forwardSec = Math.round(htmlVideoPlayer.buffered.end(i) - cur);
          break;
        }
      }
      if (forwardSec > 0) {
        playerMetricSpeed.textContent = `Buffered: ${forwardSec}s`;
      }
    }
  };

  htmlVideoPlayer.ontimeupdate = () => {
    const now = Date.now();
    if (now - lastUiUpdateTime < 250) return; // ❄️ Throttle DOM reflows to 4fps (prevents CPU/GPU heating)
    lastUiUpdateTime = now;

    if (!isDraggingScrubber && htmlVideoPlayer.duration) {
      const cur = htmlVideoPlayer.currentTime || 0;
      const dur = htmlVideoPlayer.duration || 0;
      const pct = (cur / dur) * 100;
      if (playerProgressFill) playerProgressFill.style.width = `${pct}%`;
      if (playerProgressHead) playerProgressHead.style.left = `${pct}%`;
      if (playerTimeText) playerTimeText.textContent = `${formatPlayerTime(cur)} / ${formatPlayerTime(dur)}`;
    }

    const storageKey = getPlaybackStorageKey();
    if (htmlVideoPlayer.currentTime > 5 && storageKey) {
      try {
        const history = JSON.parse(localStorage.getItem('cdl_playback_history') || '{}');
        if (htmlVideoPlayer.duration && (htmlVideoPlayer.currentTime / htmlVideoPlayer.duration > 0.95)) {
          delete history[storageKey];
        } else {
          history[storageKey] = {
            time: Math.floor(htmlVideoPlayer.currentTime),
            duration: Math.floor(htmlVideoPlayer.duration || 0),
            updated: Date.now()
          };
        }
        localStorage.setItem('cdl_playback_history', JSON.stringify(history));
      } catch (_) { }
    }
  };

  htmlVideoPlayer.onplay = () => {
    updatePlayPauseIcons(true);
    resetPlayerControlsTimer();
  };

  htmlVideoPlayer.onpause = () => {
    updatePlayPauseIcons(false);
    resetPlayerControlsTimer();
  };

  htmlVideoPlayer.onended = () => {
    updatePlayPauseIcons(false);
  };
  // ⚡ NOTE: htmlVideoPlayer.onerror is deliberately managed by loadStreamIntoHtml5 to preserve smart direct unproxied retry and loop prevention!
}

function updatePlayPauseIcons(isPlaying) {
  if (btnPlayerPlayPause) {
    btnPlayerPlayPause.innerHTML = `<i class="fa-solid fa-${isPlaying ? 'pause' : 'play'}"></i>`;
  }
  if (btnPlayerCenterPlay) {
    btnPlayerCenterPlay.innerHTML = `<i class="fa-solid fa-${isPlaying ? 'pause' : 'play'}"></i>`;
  }
}

function togglePlayPause() {
  if (!htmlVideoPlayer || htmlVideoPlayer.classList.contains('hidden')) return;
  if (htmlVideoPlayer.paused) {
    htmlVideoPlayer.play().catch(() => { });
  } else {
    htmlVideoPlayer.pause();
  }
  resetPlayerControlsTimer();
}

btnPlayerCenterPlay?.addEventListener('click', togglePlayPause);
btnPlayerPlayPause?.addEventListener('click', togglePlayPause);

// Draggable Scrubber Progress Bar
if (playerProgressTrack) {
  function seekTo(e) {
    if (!htmlVideoPlayer || !htmlVideoPlayer.duration || htmlVideoPlayer.classList.contains('hidden')) return;
    const rect = playerProgressTrack.getBoundingClientRect();
    const clientX = e.clientX || (e.touches && e.touches[0]?.clientX) || 0;
    const pos = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    htmlVideoPlayer.currentTime = pos * htmlVideoPlayer.duration;
    if (playerProgressFill) playerProgressFill.style.width = `${pos * 100}%`;
    if (playerProgressHead) playerProgressHead.style.left = `${pos * 100}%`;
    resetPlayerControlsTimer();
  }

  playerProgressTrack.addEventListener('mousedown', (e) => {
    isDraggingScrubber = true;
    seekTo(e);
  });

  playerProgressTrack.addEventListener('touchstart', (e) => {
    isDraggingScrubber = true;
    seekTo(e);
  }, { passive: true });

  window.addEventListener('mousemove', (e) => {
    if (isDraggingScrubber) seekTo(e);
  });

  window.addEventListener('touchmove', (e) => {
    if (isDraggingScrubber) seekTo(e);
  }, { passive: true });

  window.addEventListener('mouseup', () => { isDraggingScrubber = false; });
  window.addEventListener('touchend', () => { isDraggingScrubber = false; });
}

// Playback Speed Selector (0.75x, 1.0x, 1.25x, 1.5x, 2.0x)
btnPlayerSpeedToggle?.addEventListener('click', () => {
  currentSpeedIdx = (currentSpeedIdx + 1) % speedLevels.length;
  const rate = speedLevels[currentSpeedIdx];
  if (htmlVideoPlayer) htmlVideoPlayer.playbackRate = rate;
  if (playerSpeedToggleText) playerSpeedToggleText.textContent = `${rate}x`;
  window.showToast(`Playback Speed: ${rate}x`, 'info');
  resetPlayerControlsTimer();
});

// Mute Toggle with HUD Feedback
btnPlayerMute?.addEventListener('click', () => {
  if (!htmlVideoPlayer) return;
  htmlVideoPlayer.muted = !htmlVideoPlayer.muted;
  btnPlayerMute.innerHTML = `<i class="fa-solid fa-${htmlVideoPlayer.muted ? 'volume-xmark' : 'volume-high'}"></i>`;
  showSmoothVolumeHud(htmlVideoPlayer.muted ? 0 : htmlVideoPlayer.volume);
  resetPlayerControlsTimer();
});

// Quick 10s Seek Jump
function jumpVideo(seconds) {
  if (htmlVideoPlayer && !htmlVideoPlayer.classList.contains('hidden')) {
    htmlVideoPlayer.currentTime = Math.max(0, htmlVideoPlayer.currentTime + seconds);

    if (seconds < 0 && skipOverlayLeft) {
      skipOverlayLeft.classList.remove('hidden');
      setTimeout(() => skipOverlayLeft.classList.add('hidden'), 500);
    } else if (seconds > 0 && skipOverlayRight) {
      skipOverlayRight.classList.remove('hidden');
      setTimeout(() => skipOverlayRight.classList.add('hidden'), 500);
    }
    resetPlayerControlsTimer();
  }
}

btnRewind10?.addEventListener('click', () => jumpVideo(-10));
btnForward10?.addEventListener('click', () => jumpVideo(10));


// ============================================================================
// SECTION 10: Touch Gestures & Smooth Volume HUD
// ============================================================================
let stageTouchStartY = 0;
let stageTouchStartX = 0;
let initialTouchVolume = 1;
let isStageVolumeDragging = false;
let stageVolumeHudTimeout = null;

function showSmoothVolumeHud(volumeFraction) {
  if (!playerGestureHud) return;
  clearTimeout(stageVolumeHudTimeout);
  const pct = Math.round(volumeFraction * 100);
  if (playerGestureFill) playerGestureFill.style.width = `${pct}%`;
  if (playerGestureText) playerGestureText.textContent = `Volume: ${pct}%`;
  if (playerGestureIcon) {
    playerGestureIcon.className = pct === 0 ? 'fa-solid fa-volume-xmark' : pct < 50 ? 'fa-solid fa-volume-low' : 'fa-solid fa-volume-high';
  }
  playerGestureHud.classList.remove('hidden');
  stageVolumeHudTimeout = setTimeout(() => {
    playerGestureHud.classList.add('hidden');
  }, 1200);
}

playerStageFrame?.addEventListener('touchstart', (e) => {
  if (e.touches.length !== 1) return;
  const touch = e.touches[0];
  stageTouchStartX = touch.clientX;
  stageTouchStartY = touch.clientY;
  isStageVolumeDragging = false;
  if (htmlVideoPlayer) {
    initialTouchVolume = htmlVideoPlayer.muted ? 0 : htmlVideoPlayer.volume;
  }
}, { passive: true });

playerStageFrame?.addEventListener('touchmove', (e) => {
  if (e.touches.length !== 1 || !htmlVideoPlayer || htmlVideoPlayer.classList.contains('hidden')) return;
  const touch = e.touches[0];
  const rect = playerStageFrame.getBoundingClientRect();
  const deltaX = touch.clientX - stageTouchStartX;
  const deltaY = touch.clientY - stageTouchStartY;

  // Only trigger volume swipe on right half of player stage
  if (!isStageVolumeDragging && stageTouchStartX > rect.left + rect.width * 0.45 && Math.abs(deltaY) > 20 && Math.abs(deltaY) > Math.abs(deltaX)) {
    isStageVolumeDragging = true;
  }

  if (isStageVolumeDragging) {
    resetPlayerControlsTimer();
    const deltaFraction = -deltaY / (rect.height * 0.65);
    const newVol = Math.max(0, Math.min(1, initialTouchVolume + deltaFraction));
    htmlVideoPlayer.muted = false;
    htmlVideoPlayer.volume = Math.round(newVol * 100) / 100;
    showSmoothVolumeHud(htmlVideoPlayer.volume);
    if (btnPlayerMute) {
      btnPlayerMute.innerHTML = `<i class="fa-solid fa-${htmlVideoPlayer.volume === 0 ? 'volume-xmark' : 'volume-high'}"></i>`;
    }
  }
}, { passive: true });

playerStageFrame?.addEventListener('touchend', () => {
  if (isStageVolumeDragging) {
    setTimeout(() => { isStageVolumeDragging = false; }, 80);
  }
}, { passive: true });

// Stage Tap for Auto-Hiding Controls
playerStageFrame?.addEventListener('click', (e) => {
  if (isStageVolumeDragging) return;
  if (e.target.closest('.btn-ctrl-micro') || e.target.closest('.popup-center-controls') || e.target.closest('.popup-progress-track')) return;
  const card = document.querySelector('.player-modal-pro-2026');
  if (card) {
    const isHidden = card.classList.toggle('controls-hidden');
    if (!isHidden) resetPlayerControlsTimer();
  }
});

// Picture-in-Picture (PiP) Hook
btnPlayerPip?.addEventListener('click', async () => {
  if (htmlVideoPlayer && !htmlVideoPlayer.classList.contains('hidden')) {
    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture();
      } else {
        await htmlVideoPlayer.requestPictureInPicture();
      }
    } catch (e) {
      window.showToast('Picture-in-Picture not supported on this device', 'info');
    }
  } else {
    window.showToast('PiP is available on direct video stream', 'info');
  }
});


// ============================================================================
// SECTION 11: Fullscreen & Native ExoPlayer Handover
// ============================================================================

/**
 * Launches Dedicated Fullscreen Hardware Player on Android (MovieCinemaPlayerActivity)
 */
async function togglePlayerFullscreen() {
  const isNative = !!(window.Capacitor?.Plugins?.NativePlayer?.playVideo || window.Capacitor?.Plugins?.GalleryPlayer?.playOfflineVideo);

  if (isNative && currentStreamUrl) {
    let currentPosSec = 0;
    if (htmlVideoPlayer && !isNaN(htmlVideoPlayer.currentTime) && htmlVideoPlayer.currentTime > 0) {
      currentPosSec = htmlVideoPlayer.currentTime;
    }
    const startPosMs = Math.round(currentPosSec * 1000);

    // Pause and hide popup player modal
    if (htmlVideoPlayer) {
      try { htmlVideoPlayer.pause(); } catch (_) { }
    }
    window._activeMovieReturnContext = null;
    if (playerModal) {
      playerModal.classList.add('hidden');
    }

    document.body.classList.remove('modal-open');
    document.body.style.overflow = '';
    document.documentElement.style.overflow = '';
    if (window.modalStack) {
      window.modalStack = window.modalStack.filter(m => m !== playerModal);
    }

    // Launch Dedicated Native Hardware Player (ExoPlayer)
    if (window.Capacitor?.Plugins?.NativePlayer?.playVideo) {
      try {
        const serversList = (currentAllQualities || []).map(item => ({
          quality: item.quality || '1080p',
          provider: item.provider || '',
          downloadUrl: item.downloadUrl || item.streamUrl || '',
          type: item.type || 'video'
        }));
        if (serversList.length === 0 && currentStreamUrl) {
          serversList.push({
            quality: '1080p FHD',
            provider: 'Direct Cloud Pipe',
            downloadUrl: currentStreamUrl,
            type: 'video'
          });
        }

        await window.Capacitor.Plugins.NativePlayer.playVideo({
          url: currentStreamUrl,
          streamUrl: currentStreamUrl,
          title: currentVideoTitle,
          poster: currentPosterUrl || '',
          startPositionMs: startPosMs,
          position: currentPosSec,
          currentTime: currentPosSec,
          category: 'movies',
          serversJson: JSON.stringify(serversList),
          servers: serversList
        });
        return;
      } catch (e) {
        console.warn('[NativePlayer] Fullscreen launch notice:', e);
      }
    }
    return;
  }

  // Web Fullscreen fallback
  const target = playerStageFrame || htmlVideoPlayer;
  if (target) {
    if (!document.fullscreenElement && !document.webkitFullscreenElement) {
      target.requestFullscreen?.() || target.webkitRequestFullscreen?.();
      try { screen.orientation?.lock?.('landscape').catch(() => { }); } catch (_) { }
    } else {
      document.exitFullscreen?.() || document.webkitExitFullscreen?.();
      try { screen.orientation?.unlock?.(); } catch (_) { }
    }
  }
}

btnPlayerLaunchPro?.addEventListener('click', togglePlayerFullscreen);
btnPlayerFullscreenTop?.addEventListener('click', togglePlayerFullscreen);
btnPlayerFullscreen?.addEventListener('click', togglePlayerFullscreen);

// 🚀 Open in Native Cinema Pro Player (ExoPlayer) Button
document.getElementById('btnPlayerOpenNativeCinema')?.addEventListener('click', () => {
  togglePlayerFullscreen();
});

// Aspect Ratio Fit Switcher (Contain / Cover / Fill)
btnAspectFit?.addEventListener('click', () => {
  if (!playerStageFrame) return;
  playerStageFrame.classList.remove('fit-cover', 'fit-fill');

  if (aspectMode === 'contain') {
    aspectMode = 'cover';
    playerStageFrame.classList.add('fit-cover');
    window.showToast('Aspect Fit: 16:9 Crop Cover', 'info');
  } else if (aspectMode === 'cover') {
    aspectMode = 'fill';
    playerStageFrame.classList.add('fit-fill');
    window.showToast('Aspect Fit: Full Stretch', 'info');
  } else {
    aspectMode = 'contain';
    window.showToast('Aspect Fit: Contain 100%', 'info');
  }
  resetPlayerControlsTimer();
});

// Copy Stream Link
btnCopyStreamLink?.addEventListener('click', async () => {
  if (!currentStreamUrl) return window.showToast('No stream URL available', 'info');
  try {
    await navigator.clipboard.writeText(currentStreamUrl);
    window.showToast('Stream link copied to clipboard! 📋', 'success');
  } catch (e) {
    window.showToast('Could not copy link', 'error');
  }
});

// Mobile Double-Tap Skip
let lastTap = 0;
playerStageFrame?.addEventListener('touchend', (e) => {
  const now = Date.now();
  if (now - lastTap < 300) {
    const rect = playerStageFrame.getBoundingClientRect();
    const touchX = e.changedTouches[0].clientX - rect.left;
    if (touchX < rect.width / 2) {
      jumpVideo(-10);
    } else {
      jumpVideo(10);
    }
  }
  lastTap = now;
});


// ============================================================================
// SECTION 12: Keyboard Shortcuts & External Message Bridge
// ============================================================================
document.addEventListener('keydown', (e) => {
  if (playerModal?.classList.contains('hidden')) return;
  if (['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) return;

  switch (e.key.toLowerCase()) {
    case 'escape':
      window.closePlayer();
      break;
    case ' ':
    case 'k':
      e.preventDefault();
      if (htmlVideoPlayer && !htmlVideoPlayer.classList.contains('hidden')) {
        htmlVideoPlayer.paused ? htmlVideoPlayer.play() : htmlVideoPlayer.pause();
      }
      break;
    case 'arrowleft':
    case 'j':
      e.preventDefault();
      jumpVideo(-10);
      break;
    case 'arrowright':
    case 'l':
      e.preventDefault();
      jumpVideo(10);
      break;
    case 'arrowup':
      e.preventDefault();
      if (htmlVideoPlayer && !htmlVideoPlayer.classList.contains('hidden')) {
        htmlVideoPlayer.muted = false;
        htmlVideoPlayer.volume = Math.min(1, Math.round((htmlVideoPlayer.volume + 0.05) * 100) / 100);
        showSmoothVolumeHud(htmlVideoPlayer.volume);
        resetPlayerControlsTimer();
      }
      break;
    case 'arrowdown':
      e.preventDefault();
      if (htmlVideoPlayer && !htmlVideoPlayer.classList.contains('hidden')) {
        htmlVideoPlayer.volume = Math.max(0, Math.round((htmlVideoPlayer.volume - 0.05) * 100) / 100);
        showSmoothVolumeHud(htmlVideoPlayer.volume);
        resetPlayerControlsTimer();
      }
      break;
    case 'm':
      btnPlayerMute?.click();
      break;
    case 'f':
      btnPlayerFullscreen?.click();
      break;
  }
});

btnClosePlayer?.addEventListener('click', window.closePlayer);
playerModal?.addEventListener('click', (e) => {
  if (e.target === playerModal) window.closePlayer();
});

// ⚡ WebTorrent External Player Bridge & Cloud Fallback
window.addEventListener('message', (e) => {
  if (!e || !e.data) return;
  if (e.data.type === 'VS_DEVTOOLS') {
    // Silence DevTools alert/kill from vidsrc2/cloudorchestranova
    e.stopImmediatePropagation?.();
    return;
  }
  if (e.data && e.data.action === 'playExternalTorrent') {
    const torrentUrl = e.data.url;
    const title = e.data.title || 'Torrent Video Stream';
    if (window.Capacitor?.Plugins?.NativePlayer?.playTorrentStream) {
      window.Capacitor.Plugins.NativePlayer.playTorrentStream({ url: torrentUrl, title });
    } else {
      window.showToast('Launching torrent in external video app...', 'info');
      window.location.href = torrentUrl;
    }
  } else if (e.data && e.data.action === 'playCloudCinemaFallback') {
    const title = e.data.title || 'Movie';
    const cleanQuery = title.replace(/\s*\(\d{4}\).*/, '').trim();
    if (window.showToast) window.showToast(`⚡ Switching to VIP Cloud Cinema Stream: ${cleanQuery}...`, 'info');
    const fallbackEmbed = `https://vidsrc2.ru/embed/movie/${encodeURIComponent(cleanQuery)}`;
    loadWebEmbedPlayer(fallbackEmbed);
  }
}, true);

// 🛡️ Global Click & Navigation Guard against Clickjackers and Popunders while Player is Active
document.addEventListener('click', (e) => {
  const isPlayerOpen = document.getElementById('playerModal') && !document.getElementById('playerModal').classList.contains('hidden');
  if (isPlayerOpen) {
    const anchor = e.target.closest('a');
    if (anchor) {
      const href = anchor.getAttribute('href') || '';
      if (href && (anchor.target === '_blank' || (!href.startsWith('#') && !href.startsWith('javascript:')))) {
        if ((window.isKnownAdUrl && window.isKnownAdUrl(href)) || (!href.includes('localhost') && !href.startsWith('/') && !href.startsWith('#'))) {
          e.preventDefault();
          e.stopPropagation();
          e.stopImmediatePropagation();
          console.warn('[AdBlock Shield] Prevented ad anchor click inside player modal:', href);
          return false;
        }
      }
    }
  }
}, true);

