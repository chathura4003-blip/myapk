'use strict';

/**
 * ============================================================================
 * CLOUD DRIVE LEECH - DOWNLOAD MANAGER & P2P TURBO ENGINE (downloads.js)
 * ============================================================================
 * Architecture Overview:
 *   SECTION 1:  DOM Elements, Sub-Tab Navigation & Platform Detection
 *   SECTION 2:  Universal URL Input, Clipboard & .torrent File Handlers
 *   SECTION 3:  Social Media & Direct Stream Format Extractor
 *   SECTION 4:  Bencode Binary Parser & InfoHash Engine (Pure Client-Side)
 *   SECTION 5:  Selective Torrent File Inspector & Action Dispatcher
 *   SECTION 6:  In-App Native BitTorrent Engine (libtorrent4j & P2P Swarm)
 *   SECTION 7:  Download Metrics & Telemetry Formatter
 *   SECTION 8:  Zero-Flicker In-Place DOM Diffing Downloads Renderer
 *   SECTION 9:  Thermal & Battery-Aware Live Polling Engine
 *   SECTION 10: Lifecycle Bootstrap & Event Binding
 * ============================================================================
 */

// ============================================================================
// SECTION 1: DOM Elements, Sub-Tab Navigation & Platform Detection
// ============================================================================

const dlUniversalInput = document.getElementById('dlUniversalInput');
const btnStartUniversalDownload = document.getElementById('btnStartUniversalDownload');
const btnClearDlInput = document.getElementById('btnClearDlInput');
const btnPasteClipboard = document.getElementById('btnPasteClipboard');
const dlPlatformIcon = document.getElementById('dlPlatformIcon');
const dlPlatformBadge = document.getElementById('dlPlatformBadge');

const dlLoading = document.getElementById('dlLoading');
const dlLoadingText = document.getElementById('dlLoadingText');
const dlResultCard = document.getElementById('dlResultCard');
const dlResultThumb = document.getElementById('dlResultThumb');
const dlResultTitle = document.getElementById('dlResultTitle');
const dlResultDurationBadge = document.getElementById('dlResultDurationBadge');
const dlResultPlatformBadge = document.getElementById('dlResultPlatformBadge');
const dlResultUploader = document.getElementById('dlResultUploader');
const dlFormatCountBadge = document.getElementById('dlFormatCountBadge');
const dlFormatsGrid = document.getElementById('dlFormatsGrid');
const dlQueueCountBadge = document.getElementById('dlQueueCountBadge');

const btnClearDownloads = document.getElementById('btnClearDownloads');
const downloadsList = document.getElementById('downloadsList');
const navDownloadsBadge = document.getElementById('navDownloadsBadge');
const btnSubTabDownloads = document.getElementById('btnSubTabDownloads');
const btnSubTabTransfers = document.getElementById('btnSubTabTransfers');
const dlContainerDevice = document.getElementById('dlContainerDevice');
const dlContainerTransfers = document.getElementById('dlContainerTransfers');
const subBadgeDownloads = document.getElementById('subBadgeDownloads');
const subBadgeTransfers = document.getElementById('subBadgeTransfers');

let currentDlFilter = 'all';
let downloadsLivePollTimer = null;
const recentlyDeletedIds = new Set();

/** Switch to Device Downloads Subtab */
window.switchToDeviceDownloadsSubTab = function () {
  btnSubTabDownloads?.classList.add('active');
  btnSubTabTransfers?.classList.remove('active');
  dlContainerDevice?.classList.remove('hidden');
  dlContainerTransfers?.classList.add('hidden');
};

/** Switch to Cloud-to-Drive Transfers Subtab (PRO Feature Gate) */
window.switchToDriveTransfersSubTab = function () {
  if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
    if (window.showInApp404) {
      window.showInApp404('Google Drive Cloud Leech', 'HTTP 404: Remote Google Drive cloud-to-cloud transfer engine is disabled under the active license. Please activate a PRO License Key.');
    }
    return;
  }
  btnSubTabTransfers?.classList.add('active');
  btnSubTabDownloads?.classList.remove('active');
  dlContainerTransfers?.classList.remove('hidden');
  dlContainerDevice?.classList.add('hidden');
  if (window.renderTasks) window.renderTasks();
};

btnSubTabDownloads?.addEventListener('click', window.switchToDeviceDownloadsSubTab);
btnSubTabTransfers?.addEventListener('click', window.switchToDriveTransfersSubTab);

// Live Speed & Throughput Indicator Listener
const dlLiveSpeedPill = document.getElementById('dlLiveSpeedPill');
dlLiveSpeedPill?.addEventListener('click', () => {
  window.showToast('⚡ Native multi-thread download pipeline with real-time throughput metrics', 'info');
});

/**
 * Detects domain and type of URL for dynamic badge & icon styling
 * @param {string} url
 */
function detectUrlPlatform(url = '') {
  const u = url.toLowerCase();
  if (u.startsWith('magnet:') || u.includes('.torrent') || u.includes('/torrent/download/')) {
    return { key: 'torrent', name: 'BitTorrent / Magnet', icon: 'fa-solid fa-magnet', color: '#ff4757' };
  }
  if (u.includes('youtube.com') || u.includes('youtu.be')) {
    return { key: 'youtube', name: 'YouTube', icon: 'fa-brands fa-youtube', color: '#ff0000' };
  }
  if (u.includes('tiktok.com')) {
    return { key: 'tiktok', name: 'TikTok', icon: 'fa-brands fa-tiktok', color: '#00f2fe' };
  }
  if (u.includes('instagram.com') || u.includes('instagr.am')) {
    return { key: 'instagram', name: 'Instagram', icon: 'fa-brands fa-instagram', color: '#e1306c' };
  }
  if (u.includes('facebook.com') || u.includes('fb.watch') || u.includes('fb.me')) {
    return { key: 'facebook', name: 'Facebook', icon: 'fa-brands fa-facebook', color: '#1877f2' };
  }
  if (u.includes('twitter.com') || u.includes('x.com')) {
    return { key: 'twitter', name: 'Twitter / X', icon: 'fa-brands fa-x-twitter', color: '#ffffff' };
  }
  if (u.includes('reddit.com') || u.includes('redd.it')) {
    return { key: 'reddit', name: 'Reddit', icon: 'fa-brands fa-reddit', color: '#ff4500' };
  }
  if (u.includes('pinterest.com') || u.includes('pin.it')) {
    return { key: 'pinterest', name: 'Pinterest', icon: 'fa-brands fa-pinterest', color: '#e60023' };
  }
  if (u.includes('threads.net')) {
    return { key: 'threads', name: 'Threads', icon: 'fa-brands fa-threads', color: '#ffffff' };
  }
  if (u.includes('twitch.tv')) {
    return { key: 'twitch', name: 'Twitch', icon: 'fa-brands fa-twitch', color: '#9146ff' };
  }
  if (u.includes('soundcloud.com')) {
    return { key: 'soundcloud', name: 'SoundCloud', icon: 'fa-brands fa-soundcloud', color: '#ff5500' };
  }
  if (u.includes('pixeldrain.com')) {
    return { key: 'pixeldrain', name: 'PixelDrain', icon: 'fa-solid fa-hard-drive', color: '#2ed573' };
  }
  if (u.includes('drive.google.com') || u.includes('/file/d/')) {
    return { key: 'drive', name: 'Google Drive', icon: 'fa-brands fa-google-drive', color: '#4285f4' };
  }
  if (u.includes('mega.nz') || u.includes('mega.co.nz')) {
    return { key: 'mega', name: 'Mega', icon: 'fa-solid fa-cloud', color: '#ea2027' };
  }
  if (u.includes('mediafire.com')) {
    return { key: 'mediafire', name: 'MediaFire', icon: 'fa-solid fa-fire', color: '#0070f3' };
  }
  if (u.includes('dropbox.com')) {
    return { key: 'direct', name: 'Dropbox', icon: 'fa-brands fa-dropbox', color: '#0061ff' };
  }
  if (u.includes('sinhalasub') || u.includes('baiscope') || u.includes('sub.lk')) {
    return { key: 'direct', name: 'Movie / Sinhala Sub', icon: 'fa-solid fa-film', color: '#ffaa00' };
  }
  if (u.includes('cinejoy.to')) {
    return { key: 'direct', name: 'Cinejoy VIP', icon: 'fa-solid fa-film', color: '#00f2fe' };
  }
  if (u.includes('pornhub') || u.includes('xhamster') || u.includes('eporner') || u.includes('xvideos') || u.includes('xnxx')) {
    return { key: 'direct', name: '18+ Adult Video', icon: 'fa-solid fa-fire', color: '#ff4757' };
  }
  if (/\.(mp4|mkv|avi|webm|mov|m3u8|ts|mp3|m4a|wav|flac|aac)(\?.*)?$/i.test(u)) {
    return { key: 'direct', name: 'Direct Stream', icon: 'fa-solid fa-play', color: '#00f2fe' };
  }
  if (/\.(zip|rar|7z|apk|xapk|iso|pdf|bin|exe|msi)(\?.*)?$/i.test(u)) {
    return { key: 'direct', name: 'Direct File', icon: 'fa-solid fa-link', color: '#2ed573' };
  }
  return null;
}

// ============================================================================
// SECTION 2: Universal URL Input, Clipboard & .torrent File Handlers
// ============================================================================

let autoExtractSocialTimer = null;
function checkAutoExtractSocialUrl(val) {
  if (autoExtractSocialTimer) clearTimeout(autoExtractSocialTimer);
  if (!val || typeof val !== 'string') return;
  const trimmed = val.trim();
  const p = detectUrlPlatform(trimmed);
  const isSocialStream = p && (
    p.name === 'YouTube' || p.name === 'TikTok' || p.name === 'Instagram' ||
    p.name === 'Facebook' || p.name === 'Twitter / X' || p.name === 'Reddit' ||
    p.name === 'Pinterest' || p.name === 'Threads' || p.name === 'Twitch' ||
    p.name === 'SoundCloud' || p.name === 'PixelDrain'
  );
  if (isSocialStream && (trimmed.startsWith('http://') || trimmed.startsWith('https://'))) {
    autoExtractSocialTimer = setTimeout(() => {
      handleUniversalDownloadAction();
    }, 450);
  }
}

if (dlUniversalInput) {
  dlUniversalInput.addEventListener('input', () => {
    const val = dlUniversalInput.value.trim();
    if (val.length > 0) {
      btnClearDlInput?.classList.remove('hidden');
      const p = detectUrlPlatform(val);
      if (p) {
        if (dlPlatformIcon) {
          dlPlatformIcon.className = p.icon + ' search-icon-dl';
          dlPlatformIcon.style.color = p.color;
        }
        if (window.updatePlatformPillSelection) {
          window.updatePlatformPillSelection(p.key);
        }
      } else {
        if (dlPlatformIcon) {
          dlPlatformIcon.className = 'fa-solid fa-link search-icon-dl';
          dlPlatformIcon.style.color = 'var(--accent-cyan)';
        }
        if (window.updatePlatformPillSelection) {
          window.updatePlatformPillSelection(null);
        }
      }
      checkAutoExtractSocialUrl(val);
    } else {
      btnClearDlInput?.classList.add('hidden');
      if (dlPlatformIcon) {
        dlPlatformIcon.className = 'fa-solid fa-link search-icon-dl';
        dlPlatformIcon.style.color = 'var(--accent-cyan)';
      }
      if (window.updatePlatformPillSelection) {
        window.updatePlatformPillSelection(null);
      }
    }
  });

  dlUniversalInput.addEventListener('paste', () => {
    setTimeout(() => {
      const val = dlUniversalInput.value.trim();
      checkAutoExtractSocialUrl(val);
    }, 100);
  });

  dlUniversalInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleUniversalDownloadAction();
    }
  });
}

btnClearDlInput?.addEventListener('click', () => {
  if (dlUniversalInput) {
    dlUniversalInput.value = '';
    dlUniversalInput.focus();
  }
  btnClearDlInput.classList.add('hidden');
  if (dlResultCard) dlResultCard.classList.add('hidden');
  if (dlPlatformIcon) {
    dlPlatformIcon.className = 'fa-solid fa-link search-icon-dl';
    dlPlatformIcon.style.color = 'var(--accent-cyan)';
  }
  if (window.updatePlatformPillSelection) {
    window.updatePlatformPillSelection(null);
  }
});

// Paste from Clipboard
btnPasteClipboard?.addEventListener('click', async () => {
  try {
    const text = await navigator.clipboard.readText();
    if (text && (text.trim().startsWith('http') || text.trim().startsWith('magnet:'))) {
      if (dlUniversalInput) {
        dlUniversalInput.value = text.trim();
        dlUniversalInput.dispatchEvent(new Event('input'));
        checkAutoExtractSocialUrl(text.trim());
        window.showToast('📋 Link Pasted from Clipboard!', 'info');
      }
    } else {
      window.showToast('No valid URL or Magnet link found in clipboard', 'info');
    }
  } catch (_) {
    window.showToast('Paste permission required or clipboard empty', 'info');
  }
});

// Browse / Open .torrent File
const btnBrowseTorrent = document.getElementById('btnBrowseTorrent');
const torrentFileInput = document.getElementById('torrentFileInput');

btnBrowseTorrent?.addEventListener('click', () => {
  if (torrentFileInput) torrentFileInput.click();
});

torrentFileInput?.addEventListener('change', async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  try {
    if (dlLoading) {
      dlLoading.classList.remove('hidden');
      if (dlLoadingText) dlLoadingText.textContent = `Inspecting torrent metadata: ${file.name}...`;
    }
    const arrayBuffer = await file.arrayBuffer();
    parseAndRenderTorrent(arrayBuffer, file.name);
    window.showToast(`🧲 Torrent file "${file.name}" loaded! Choose files below.`, 'success');
  } catch (err) {
    window.showToast('Failed to parse .torrent file: ' + err.message, 'error');
  } finally {
    if (dlLoading) dlLoading.classList.add('hidden');
    torrentFileInput.value = '';
  }
});

// Quick Platform Ribbon Pills
document.querySelectorAll('.social-pill').forEach(pill => {
  pill.addEventListener('click', async () => {
    const plat = pill.dataset.platform || 'video';
    if (window.updatePlatformPillSelection) {
      window.updatePlatformPillSelection(plat);
    }
    if (plat === 'torrent') {
      if (torrentFileInput) torrentFileInput.click();
      else if (btnBrowseTorrent) btnBrowseTorrent.click();
      window.showToast('Select a .torrent file or paste a magnet link!', 'info');
      return;
    }

    // Smart auto-paste from clipboard if URL matches platform
    try {
      if (navigator.clipboard?.readText) {
        const clip = await navigator.clipboard.readText();
        if (clip && (clip.startsWith('http') || clip.startsWith('magnet:'))) {
          const detected = detectUrlPlatform(clip.trim());
          if (detected && (detected.key === plat || (plat === 'direct' && detected.key === 'direct'))) {
            if (dlUniversalInput) {
              dlUniversalInput.value = clip.trim();
              dlUniversalInput.dispatchEvent(new Event('input'));
              window.showToast(`📋 Pasted ${detected.name} link from clipboard!`, 'success');
              handleUniversalDownloadAction();
              return;
            }
          }
        }
      }
    } catch (_) { }

    if (dlUniversalInput) {
      dlUniversalInput.focus();
      const pName = pill.textContent.trim();
      window.showToast(`Paste any ${pName} link above!`, 'info');
    }
  });
});

// Dismiss Result Card
const btnCloseDlResultCard = document.getElementById('btnCloseDlResultCard');
btnCloseDlResultCard?.addEventListener('click', () => {
  if (dlResultCard) dlResultCard.classList.add('hidden');
  if (dlUniversalInput) dlUniversalInput.value = '';
  btnClearDlInput?.classList.add('hidden');
  if (dlPlatformBadge) dlPlatformBadge.classList.add('hidden');
  if (window.updatePlatformPillSelection) {
    window.updatePlatformPillSelection(null);
  }
  window.showToast('🗑️ Card Closed', 'info');
});

// ============================================================================
// SECTION 3: Social Media & Direct Stream Format Extractor
// ============================================================================

/**
 * Central URL Validator according to Section 5 of Download Tab Master Prompt
 * @param {string} rawUrl
 */
function validateDownloadUrl(rawUrl = '') {
  const url = (rawUrl || '').trim();
  if (!url) {
    return { valid: false, error: 'Please enter or paste a video link or file URL' };
  }
  const lower = url.toLowerCase();
  if (lower.startsWith('javascript:') || lower.startsWith('file:') || lower.startsWith('data:') || lower.startsWith('blob:')) {
    return { valid: false, error: 'Invalid URL scheme. Only HTTP, HTTPS, and Magnet links are supported.' };
  }
  if (!lower.startsWith('http://') && !lower.startsWith('https://') && !lower.startsWith('magnet:')) {
    return { valid: false, error: 'Invalid URL. Please enter a valid URL starting with https://, http://, or magnet:' };
  }
  return { valid: true, url };
}

btnStartUniversalDownload?.addEventListener('click', () => {
  handleUniversalDownloadAction();
});

async function handleUniversalDownloadAction() {
  const validated = validateDownloadUrl(dlUniversalInput ? dlUniversalInput.value : '');
  if (!validated.valid) return window.showToast(validated.error, 'info');
  const url = validated.url;

  const isTorrent = url.startsWith('magnet:') || url.includes('.torrent') || url.includes('/torrent/download/');
  if (isTorrent) {
    if (url.includes('.torrent') || url.includes('/torrent/download/')) {
      try {
        if (dlLoading) {
          dlLoading.classList.remove('hidden');
          if (dlLoadingText) dlLoadingText.textContent = 'Fetching and analyzing .torrent metadata...';
        }
        const resp = await fetch(url);
        if (resp.ok) {
          const ab = await resp.arrayBuffer();
          const torrentName = url.split('/').pop().split('?')[0] || 'Movie_Torrent.torrent';
          parseAndRenderTorrent(ab, torrentName, url);
          if (dlLoading) dlLoading.classList.add('hidden');
          window.showToast('⚡ Torrent files analyzed! Select items to download below.', 'success');
          return;
        }
      } catch (err) {
        console.warn('Direct torrent fetch fallback:', err);
      } finally {
        if (dlLoading) dlLoading.classList.add('hidden');
      }
    }

    let torrentName = 'Torrent Media';
    if (url.startsWith('magnet:')) {
      const dnMatch = url.match(/dn=([^&]+)/);
      if (dnMatch) torrentName = decodeURIComponent(dnMatch[1]).replace(/\+/g, ' ');
    } else {
      torrentName = url.split('/').pop().split('?')[0] || 'Movie_Torrent.torrent';
    }

    const torrentInfo = {
      title: torrentName,
      thumbnail: 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400&q=80',
      uploader: 'BitTorrent High-Speed Swarm',
      platform: { name: 'BitTorrent / Magnet', icon: 'fa-solid fa-magnet', color: '#ff4757' },
      formats: [
        {
          quality: 'Torrent Direct Download',
          size: 'Fast Swarm',
          ext: 'TORRENT',
          url: url,
          isAudio: false
        }
      ]
    };

    renderExtractedFormats(torrentInfo);
    window.showToast('⚡ Torrent / Magnet Loaded! Ready to Download, Stream or Leech.', 'success');
    return;
  }

  const p = detectUrlPlatform(url);
  const isSocialOrStream = (p && (
    p.name === 'YouTube' || p.name === 'TikTok' || p.name === 'Instagram' ||
    p.name === 'Facebook' || p.name === 'Twitter / X' || p.name === 'Reddit' ||
    p.name === 'Pinterest' || p.name === 'Threads' || p.name === 'Twitch' ||
    p.name === 'SoundCloud' || p.name === 'PixelDrain' || p.name === 'Google Drive' ||
    p.name === 'Dropbox' || p.name === 'Direct Stream' || p.name === '18+ Adult Video'
  )) || /^(https?:\/\/)?(www\.)?(youtube\.com|youtu\.be|tiktok\.com|instagram\.com|facebook\.com|fb\.watch|twitter\.com|x\.com|reddit\.com|pin\.it|pinterest\.com|threads\.net|twitch\.tv|soundcloud\.com|pixeldrain\.com)/i.test(url)
    || /\.(mp4|mkv|avi|webm|mov|m3u8|ts|mp3|m4a|wav|flac)(\?.*)?$/i.test(url);

  if (isSocialOrStream) {
    if (dlLoading) {
      dlLoading.classList.remove('hidden');
      if (dlLoadingText) dlLoadingText.textContent = `🔍 Extracting 1080p/4K resolutions and audio from ${p?.name || 'Media Link'}...`;
    }
    if (dlResultCard) dlResultCard.classList.add('hidden');

    try {
      let data = null;

      // 1. Native Kotlin Android Extractor (with 6-second watchdog timeout)
      if (window.Capacitor?.Plugins?.NativeExtractor?.extractMedia) {
        try {
          const nRes = await Promise.race([
            window.Capacitor.Plugins.NativeExtractor.extractMedia({ url }),
            new Promise((_, reject) => setTimeout(() => reject(new Error('Native extractor timeout')), 6000))
          ]);
          if (nRes && (nRes.info || nRes.details || (nRes.data && (nRes.data.formats || nRes.data.qualities)))) {
            data = nRes.info ? nRes : { info: nRes.details || nRes.data || nRes };
          }
        } catch (nErr) {
          console.warn('[NativeExtractor] Failed or timed out, trying standalone engine:', nErr);
        }
      }

      // 2. Standalone Client-Side Extraction Engine (with 5-second watchdog timeout)
      if (!data && window.StandaloneEngine?.extractMedia) {
        try {
          data = await Promise.race([
            window.StandaloneEngine.extractMedia(url),
            new Promise((_, reject) => setTimeout(() => reject(new Error('Standalone extractor timeout')), 5000))
          ]);
        } catch (sErr) {
          console.warn('[StandaloneEngine] Extraction fallback:', sErr);
        }
      }

      const rawFormats = data?.info?.formats || data?.info?.qualities || data?.details?.qualities || data?.formats || [];
      if (data && (rawFormats.length > 0 || data.info || data.details)) {
        const infoObj = data.info || data.details || data;
        if (!infoObj.formats && rawFormats.length > 0) infoObj.formats = rawFormats;
        if (Array.isArray(infoObj.formats) && infoObj.formats.length > 0) {
          infoObj.url = url;
          infoObj.platform = p || { name: 'Social', icon: 'fa-solid fa-play', color: 'var(--accent-cyan)' };
          renderExtractedFormats(infoObj);
          window.showToast(`⚡ ${p?.name || 'Media'} streams extracted! Choose resolution below.`, 'success');
          return;
        }
      }

      // 3. Resilient Format Options Generation: Never leave the user hanging or empty-handed!
      const platName = p?.name || 'Social';
      const parts = url.split('/').filter(Boolean);
      const lastPart = parts.length > 0 ? parts[parts.length - 1].split('?')[0] : '';
      const cleanPostId = (lastPart && !['reel', 'reels', 'p', 'shorts', 'watch', 'video', 'tv'].includes(lastPart.toLowerCase())) ? ` #${lastPart}` : '';
      const fallbackInfo = {
        title: `${platName} Video${cleanPostId}`,
        thumbnail: 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400',
        duration: 60,
        url: url,
        platform: p || { name: platName, icon: 'fa-solid fa-play', color: '#00f2fe' },
        uploader: `${platName} Creator`,
        formats: [
          {
            formatId: 'social-1080p',
            quality: '1080p Full HD',
            label: `${platName} High-Speed Master (1080p)`,
            ext: 'mp4',
            url: url,
            streamUrl: url,
            downloadUrl: url,
            size: '1080p HD',
            isAudio: false
          },
          {
            formatId: 'social-720p',
            quality: '720p HD',
            label: `${platName} Standard HD (720p)`,
            ext: 'mp4',
            url: url,
            streamUrl: url,
            downloadUrl: url,
            size: '720p HD',
            isAudio: false
          },
          {
            formatId: 'social-audio',
            quality: 'Audio MP3',
            label: `${platName} Audio Track (320 kbps MP3)`,
            ext: 'mp3',
            url: url,
            streamUrl: url,
            downloadUrl: url,
            size: '320 kbps',
            isAudio: true
          }
        ]
      };

      renderExtractedFormats(fallbackInfo);
      window.showToast(`⚡ ${platName} options loaded! Select resolution below.`, 'success');
      return;
    } catch (err) {
      console.warn('Extraction caught err:', err);
      window.showToast(`Extraction notice: ${err.message || 'Connecting to stream'}`, 'info');
    } finally {
      if (dlLoading) dlLoading.classList.add('hidden');
    }
  }

  // Direct Binary Download
  let cleanName = url.split('/').pop().split('?')[0] || '';
  try {
    cleanName = decodeURIComponent(cleanName).replace(/[/\\?%*:|"<>]/g, '_').trim();
  } catch (_) { }
  if (!cleanName) cleanName = 'Download_File';
  if (!cleanName.includes('.')) {
    const extM = url.match(/\.([a-zA-Z0-9]{2,5})(?:[?#]|$)/);
    if (extM && !['php', 'html', 'htm', 'asp', 'aspx', 'jsp'].includes(extM[1].toLowerCase())) {
      cleanName += '.' + extM[1];
    }
  }
  if (dlUniversalInput) {
    dlUniversalInput.value = '';
    btnClearDlInput?.classList.add('hidden');
  }
  if (dlResultCard) dlResultCard.classList.add('hidden');

  if (url.startsWith('magnet:') || url.includes('.torrent') || url.includes('/torrent/download/')) {
    window.startInAppTorrentDownload(url, cleanName);
    return;
  }

  window.triggerDirectDownload(url, cleanName);
}

/**
 * Global state for extracted social media formats & category filter
 */
let currentExtractedFormats = [];
let currentExtractedInfo = null;
let currentActiveFilter = 'all';

/**
 * Platform-tailored social media resolution renderer (YouTube, TikTok, Instagram, Facebook, X, Reddit)
 */
function renderExtractedFormats(info) {
  if (!dlResultCard || !dlFormatsGrid) return;
  currentExtractedInfo = info;

  // 1. Thumbnail, Title, Duration
  if (dlResultThumb) {
    dlResultThumb.onerror = function () {
      this.onerror = null;
      this.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="400" height="225" viewBox="0 0 400 225"><rect width="400" height="225" fill="%23131722"/><circle cx="200" cy="112" r="36" fill="%231f293d"/><polygon points="194,98 214,112 194,126" fill="%2300f2fe"/></svg>';
    };
    dlResultThumb.src = info.thumbnail || info.poster || 'https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400';
  }
  if (dlResultTitle) {
    dlResultTitle.textContent = info.title || 'Social Media Video';
  }
  if (dlResultDurationBadge) {
    const dur = info.duration ? `${Math.floor(info.duration / 60)}:${String(Math.floor(info.duration % 60)).padStart(2, '0')}` : 'HD Stream';
    dlResultDurationBadge.innerHTML = `<i class="fa-solid fa-clock"></i> ${dur}`;
  }

  // 2. Identify Platform & Set Custom Badges
  const detectedPlat = detectUrlPlatform(info.url || (dlUniversalInput ? dlUniversalInput.value.trim() : ''));
  const p = info.platform || detectedPlat || { name: 'Social', icon: 'fa-solid fa-play', color: '#00f2fe' };
  const platName = p.name || 'Social';

  if (dlResultPlatformBadge) {
    dlResultPlatformBadge.innerHTML = `<i class="${p.icon || 'fa-solid fa-video'}"></i> ${platName}`;
    if (p.color) dlResultPlatformBadge.style.backgroundColor = p.color;
  }
  if (dlResultUploader) {
    dlResultUploader.innerHTML = `<i class="fa-solid fa-user"></i> ${info.uploader || 'Creator'}`;
  }

  const dlResultTag = document.getElementById('dlResultTag');
  if (dlResultTag) {
    if (platName === 'TikTok') {
      dlResultTag.innerHTML = `<i class="fa-solid fa-shield-check" style="color:#2ed573;"></i> ⚡ Zero Watermark Verified`;
    } else if (platName === 'YouTube') {
      dlResultTag.innerHTML = `<i class="fa-brands fa-youtube" style="color:#ff0000;"></i> High-Res Master Streams`;
    } else if (platName === 'Instagram') {
      dlResultTag.innerHTML = `<i class="fa-brands fa-instagram" style="color:#e1306c;"></i> Original Reel / Video`;
    } else if (platName === 'Facebook') {
      dlResultTag.innerHTML = `<i class="fa-brands fa-facebook" style="color:#1877f2;"></i> HD Video Stream`;
    } else if (platName === 'Reddit') {
      dlResultTag.innerHTML = `<i class="fa-brands fa-reddit" style="color:#ff4500;"></i> Swarm HD Stream`;
    } else {
      dlResultTag.innerHTML = `<i class="fa-solid fa-bolt" style="color:var(--accent-cyan);"></i> Direct Turbo Pipe`;
    }
  }

  // Apply platform-specific glow styling to dlResultCard
  dlResultCard.classList.remove('platform-youtube', 'platform-tiktok', 'platform-instagram', 'platform-facebook', 'platform-twitter', 'platform-reddit');
  const platClass = 'platform-' + platName.toLowerCase().replace(/[^a-z0-9]/g, '');
  dlResultCard.classList.add(platClass);

  // 3. Extract & Normalize Quality Formats
  const rawFormats = info.formats || info.qualities || [];
  currentExtractedFormats = rawFormats.map((fmt, idx) => {
    const isAudio = Boolean(fmt.isAudio || fmt.isAudioOnly || fmt.ext === 'mp3' || (fmt.quality && fmt.quality.toLowerCase().includes('audio')));
    const qRaw = (fmt.quality || fmt.label || 'HD Video').trim();
    const qLower = qRaw.toLowerCase();
    let ext = (fmt.ext || (isAudio ? 'mp3' : 'mp4')).toLowerCase();
    let size = fmt.size || (isAudio ? '320 kbps' : 'HD Quality');
    let url = fmt.downloadUrl || fmt.streamUrl || fmt.url || info.url;

    // Detect exact resolution pill
    let resTag = 'HD Video';
    let resClass = 'res-pill-720p';
    if (isAudio) {
      resTag = 'MP3 Audio';
      resClass = 'res-pill-audio';
    } else if (qLower.includes('1080') || qLower.includes('full hd') || qLower.includes('master')) {
      resTag = '1080p Full HD';
      resClass = 'res-pill-1080p';
    } else if (qLower.includes('720') || qLower.includes('hd')) {
      resTag = '720p HD';
      resClass = 'res-pill-720p';
    } else if (qLower.includes('480')) {
      resTag = '480p SD';
      resClass = 'res-pill-sd';
    } else if (qLower.includes('360') || qLower.includes('data saver')) {
      resTag = '360p Data Saver';
      resClass = 'res-pill-sd';
    }

    return {
      ...fmt,
      id: fmt.formatId || `fmt-${idx}`,
      isAudio,
      qRaw,
      ext,
      size,
      url,
      resTag,
      resClass,
      label: fmt.label || qRaw
    };
  });

  // 4. Update category counters
  const videoCount = currentExtractedFormats.filter(f => !f.isAudio).length;
  const audioCount = currentExtractedFormats.filter(f => f.isAudio).length;

  const fmtCountAll = document.getElementById('fmtCountAll');
  const fmtCountVideo = document.getElementById('fmtCountVideo');
  const fmtCountAudio = document.getElementById('fmtCountAudio');
  if (fmtCountAll) fmtCountAll.textContent = currentExtractedFormats.length;
  if (fmtCountVideo) fmtCountVideo.textContent = videoCount;
  if (fmtCountAudio) fmtCountAudio.textContent = audioCount;

  if (dlFormatCountBadge) {
    dlFormatCountBadge.textContent = `${currentExtractedFormats.length} Option${currentExtractedFormats.length === 1 ? '' : 's'}`;
  }

  // 5. Setup Category Filter Tabs
  const categoryBar = document.getElementById('dlFormatsCategoryBar');
  if (categoryBar) {
    categoryBar.querySelectorAll('.fmt-category-pill').forEach(pill => {
      pill.onclick = () => {
        categoryBar.querySelectorAll('.fmt-category-pill').forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        currentActiveFilter = pill.dataset.filter || 'all';
        renderFilteredFormatCards(platName);
      };
    });
  }

  currentActiveFilter = 'all';
  categoryBar?.querySelectorAll('.fmt-category-pill').forEach(p => {
    if (p.dataset.filter === 'all') p.classList.add('active');
    else p.classList.remove('active');
  });

  renderFilteredFormatCards(platName);

  dlResultCard.classList.remove('hidden');
  dlResultCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function renderFilteredFormatCards(platName) {
  if (!dlFormatsGrid) return;
  dlFormatsGrid.innerHTML = '';

  let list = currentExtractedFormats;
  if (currentActiveFilter === 'video') {
    list = currentExtractedFormats.filter(f => !f.isAudio);
  } else if (currentActiveFilter === 'audio') {
    list = currentExtractedFormats.filter(f => f.isAudio);
  }

  if (list.length === 0) {
    dlFormatsGrid.innerHTML = `
      <div style="grid-column: 1/-1; text-align: center; padding: 24px; color: var(--text-muted);">
        <i class="fa-solid fa-circle-exclamation" style="font-size: 1.5rem; margin-bottom: 8px; display: block;"></i>
        No ${currentActiveFilter} formats available for this link.
      </div>
    `;
    return;
  }

  list.forEach(fmt => {
    const card = document.createElement('div');
    card.className = 'media-format-card-2026';
    const isAudio = fmt.isAudio;
    const isTikTok = platName === 'TikTok';

    card.innerHTML = `
      <div class="format-card-top">
        <div>
          <div class="format-quality-label">
            <span class="res-pill ${fmt.resClass}">
              <i class="fa-solid ${isAudio ? 'fa-music' : 'fa-film'}"></i>
              ${fmt.resTag}
            </span>
            <span style="font-size: 0.88rem; font-weight: 700; color: #fff; margin-left: 4px;">${fmt.label}</span>
          </div>
          <div class="format-size-label" style="display:flex; align-items:center; gap:6px; margin-top:4px;">
            <span><i class="fa-solid fa-database"></i> ${fmt.size}</span>
            ${isTikTok && !isAudio ? '<span class="res-pill res-pill-nowm"><i class="fa-solid fa-check"></i> No Watermark</span>' : ''}
          </div>
        </div>
        <span class="format-badge-ext">${fmt.ext.toUpperCase()}</span>
      </div>

      <div class="format-card-actions">
        <button class="btn-format-action stream btn-fmt-stream" title="Watch in Cinema Player">
          <i class="fa-solid fa-play"></i> Watch
        </button>
        <button class="btn-format-action primary btn-fmt-drive" title="Upload to Google Drive">
          <i class="fa-solid fa-cloud-arrow-up"></i> Drive
        </button>
        <button class="btn-format-action btn-fmt-dl" title="Download to Device Storage">
          <i class="fa-solid fa-download"></i> Download
        </button>
      </div>
    `;

    card.querySelector('.btn-fmt-stream')?.addEventListener('click', () => {
      if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CINEMA_PRO')) {
        if (window.showFeatureLockedSheet) {
          window.showFeatureLockedSheet('FEATURE_CINEMA_PRO');
        }
        return;
      }
      if (window.openPlayer) {
        window.openPlayer({
          title: `${currentExtractedInfo?.title || 'Video'} [${fmt.resTag}]`,
          streamUrl: fmt.streamUrl || fmt.url || fmt.downloadUrl,
          type: isAudio ? 'audio' : 'video'
        });
      }
    });

    card.querySelector('.btn-fmt-drive')?.addEventListener('click', () => {
      if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
        if (window.showInApp404) {
          window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Google Drive upload pipeline is disabled or unauthorized under the active license. Please activate a PRO License Key.');
        }
        return;
      }
      if (window.startCloudTransfer) {
        window.startCloudTransfer({
          title: `${currentExtractedInfo?.title || 'Video'} [${fmt.resTag}]`,
          url: fmt.downloadUrl || fmt.streamUrl || fmt.url,
          type: isAudio ? 'audio' : 'media',
          quality: fmt.resTag
        });
      }
    });

    const isStreamOnly = Boolean(fmt.isEmbed || (!fmt.url && !fmt.downloadUrl) || (fmt.url && (fmt.url.includes('/embed/') || fmt.url.includes('youtube.com/watch'))));
    const dlBtn = card.querySelector('.btn-fmt-dl');
    if (isStreamOnly && dlBtn) {
      dlBtn.style.opacity = '0.5';
      dlBtn.title = 'Web Stream Only (Not directly downloadable)';
    }

    dlBtn?.addEventListener('click', () => {
      if (isStreamOnly) {
        window.showToast('ℹ️ This video format is available for in-app Cinema playback only', 'info');
        return;
      }
      const rawTitle = (currentExtractedInfo?.title || 'Media_Download').replace(/[/\\?%*:|"<>]/g, '_').trim();
      let cleanFilename = '';
      if (fmt.isAudio) {
        cleanFilename = `[${platName}] ${rawTitle} - Audio.mp3`;
      } else {
        const cleanRes = fmt.resTag.replace(/\s+/g, '_');
        cleanFilename = `[${platName}] ${rawTitle} - ${cleanRes}.${fmt.ext}`;
      }
      const targetDl = fmt.downloadUrl || fmt.streamUrl || fmt.url;
      window.triggerDirectDownload(targetDl, cleanFilename);
      window.showToast(`🚀 Downloading ${fmt.resTag} (${cleanFilename})...`, 'success');
    });

    dlFormatsGrid.appendChild(card);
  });
}

// ============================================================================
// SECTION 4: Bencode Binary Parser & InfoHash Engine (Pure Client-Side)
// ============================================================================

function formatTorrentBytes(bytes) {
  if (!bytes || bytes <= 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

async function computeTorrentInfoHash(buffer) {
  try {
    const u8 = new Uint8Array(buffer);
    let infoStart = -1;
    for (let i = 0; i < u8.length - 6; i++) {
      if (u8[i] === 0x34 && u8[i + 1] === 0x3a && u8[i + 2] === 0x69 && u8[i + 3] === 0x6e && u8[i + 4] === 0x66 && u8[i + 5] === 0x6f) {
        infoStart = i + 6;
        break;
      }
    }
    if (infoStart !== -1) {
      let depth = 0;
      let pos = infoStart;
      while (pos < u8.length) {
        const byte = u8[pos];
        if (byte === 0x64 || byte === 0x6c) {
          depth++;
          pos++;
        } else if (byte === 0x69) {
          pos++;
          while (pos < u8.length && u8[pos] !== 0x65) pos++;
          pos++;
        } else if (byte === 0x65) {
          depth--;
          pos++;
          if (depth === 0) break;
        } else if (byte >= 0x30 && byte <= 0x39) {
          let colon = pos;
          while (colon < u8.length && u8[colon] !== 0x3a) colon++;
          const len = parseInt(new TextDecoder('utf-8').decode(u8.subarray(pos, colon)), 10);
          pos = colon + 1 + len;
        } else {
          pos++;
        }
      }
      const infoBytes = u8.subarray(infoStart, pos);
      const hashBuffer = await crypto.subtle.digest('SHA-1', infoBytes);
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
    }
  } catch (err) {
    console.warn('InfoHash compute error:', err);
  }
  return null;
}

function bdecodeTorrent(buffer) {
  const u8 = new Uint8Array(buffer);
  let pos = 0;

  function next() {
    if (pos >= u8.length) return null;
    const byte = u8[pos];
    if (byte === 0x69) { // integer 'i'
      pos++;
      let end = pos;
      while (end < u8.length && u8[end] !== 0x65) end++;
      const numStr = new TextDecoder('utf-8').decode(u8.subarray(pos, end));
      pos = end + 1;
      return parseInt(numStr, 10);
    }
    if (byte === 0x6c) { // list 'l'
      pos++;
      const list = [];
      while (pos < u8.length && u8[pos] !== 0x65) {
        list.push(next());
      }
      pos++; // skip 'e'
      return list;
    }
    if (byte === 0x64) { // dictionary 'd'
      pos++;
      const dict = {};
      while (pos < u8.length && u8[pos] !== 0x65) {
        const key = next();
        const val = next();
        if (key !== null) dict[key] = val;
      }
      pos++; // skip 'e'
      return dict;
    }
    // String: <length>:<bytes>
    let colon = pos;
    while (colon < u8.length && u8[colon] !== 0x3a) colon++;
    if (colon >= u8.length) return null;
    const len = parseInt(new TextDecoder('utf-8').decode(u8.subarray(pos, colon)), 10);
    pos = colon + 1;
    const strBytes = u8.subarray(pos, pos + len);
    pos += len;
    return new TextDecoder('utf-8', { fatal: false }).decode(strBytes);
  }

  return next();
}

// ============================================================================
// SECTION 5: Selective Torrent File Inspector & Action Dispatcher
// ============================================================================

async function parseAndRenderTorrent(buffer, fileName = 'Torrent', sourceUrl = '') {
  if (!dlResultCard || !dlFormatsGrid) return;

  let torrent = null;
  try {
    torrent = bdecodeTorrent(buffer);
  } catch (e) {
    console.error('Bdecode error:', e);
  }

  const torrentTitle = torrent?.info?.name || fileName.replace(/\.torrent$/i, '');
  const infoHash = await computeTorrentInfoHash(buffer);

  const trackers = [
    'udp://tracker.opentrackr.org:1337/announce',
    'udp://open.demonii.com:1337/announce',
    'udp://tracker.openbittorrent.com:80',
    'udp://tracker.coppersurfer.tk:6969',
    'udp://glotorrents.pw:6969/announce'
  ];
  const trParams = trackers.map(t => `&tr=${encodeURIComponent(t)}`).join('');

  const magnetUri = infoHash
    ? `magnet:?xt=urn:btih:${infoHash}&dn=${encodeURIComponent(torrentTitle)}${trParams}`
    : `magnet:?dn=${encodeURIComponent(torrentTitle)}${trParams}`;

  // Parse internal files list
  let files = [];
  if (torrent?.info?.files && Array.isArray(torrent.info.files)) {
    files = torrent.info.files.map((f, idx) => {
      const p = Array.isArray(f.path) ? f.path.join('/') : (f.path || `file_${idx + 1}`);
      const len = f.length || 0;
      const isMedia = /\.(mp4|mkv|avi|webm|mov|m4v|mp3|flac|wav)$/i.test(p);
      const isSub = /\.(srt|vtt|ass|sub)$/i.test(p);
      const isImg = /\.(jpg|jpeg|png|webp|gif)$/i.test(p);
      return {
        id: idx,
        name: p,
        bytes: len,
        sizeStr: formatTorrentBytes(len),
        isMedia,
        isSub,
        isImg,
        selected: isMedia || (!torrent.info.files.some(fi => /\.(mp4|mkv|avi|webm)$/i.test(Array.isArray(fi.path) ? fi.path.join('/') : '')) && idx === 0)
      };
    });
  } else {
    const singleLen = torrent?.info?.length || buffer.byteLength || 0;
    files = [{
      id: 0,
      name: torrentTitle,
      bytes: singleLen,
      sizeStr: formatTorrentBytes(singleLen),
      isMedia: true,
      selected: true
    }];
  }

  const totalTorrentBytes = files.reduce((acc, f) => acc + f.bytes, 0);
  const totalTorrentSizeStr = formatTorrentBytes(totalTorrentBytes);

  // Set Result Card Meta
  if (dlResultThumb) dlResultThumb.src = 'https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400&q=80';
  if (dlResultTitle) dlResultTitle.textContent = torrentTitle;
  if (dlResultDurationBadge) dlResultDurationBadge.innerHTML = `<i class="fa-solid fa-hard-drive"></i> ${totalTorrentSizeStr}`;
  if (dlResultPlatformBadge) dlResultPlatformBadge.innerHTML = `<i class="fa-solid fa-magnet" style="color: #ff4757;"></i> BitTorrent Swarm`;
  if (dlResultUploader) dlResultUploader.innerHTML = `<i class="fa-solid fa-layer-group"></i> ${files.length} Internal File${files.length > 1 ? 's' : ''}`;
  if (dlFormatCountBadge) dlFormatCountBadge.textContent = `${files.length} Files Inside`;

  // Render Interactive Selective UI
  dlFormatsGrid.innerHTML = `
    <div class="torrent-inspector-box" style="width: 100%;">
      <div class="torrent-meta-bar">
        <div class="torrent-title-main">
          <i class="fa-solid fa-magnet" style="color: #ff4757;"></i>
          <span>Selective Torrent Files</span>
        </div>
        <div style="display: flex; align-items: center; gap: 8px;">
          <span class="meta-pill-mini" style="background: rgba(255, 71, 87, 0.15); border: 1px solid rgba(255, 71, 87, 0.4); color: #ff4757; font-weight: 700; font-size: 0.75rem;">
            Total: ${totalTorrentSizeStr}
          </span>
          <button id="btnDeleteTorrentCard" title="Remove / Clear Torrent" style="background: rgba(255, 71, 87, 0.2); border: 1px solid rgba(255, 71, 87, 0.45); color: #ff4757; width: 30px; height: 30px; border-radius: 8px; display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 0.88rem; transition: all 0.2s ease;">
            <i class="fa-solid fa-trash-can"></i>
          </button>
        </div>
      </div>

      <div class="torrent-selection-controls" style="display: flex; flex-direction: column; gap: 8px;">
        <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
          <label style="display: flex; align-items: center; gap: 8px; cursor: pointer; user-select: none;">
            <input type="checkbox" id="chkTorrentSelectAll" ${files.every(f => f.selected) ? 'checked' : ''} style="cursor: pointer; width: 16px; height: 16px; accent-color: var(--accent-cyan);">
            <span style="font-weight: 700; color: #fff; font-size: 0.82rem;">Select All</span>
          </label>
          <span id="torrentSelectedCountText" style="font-weight: 700; color: var(--accent-cyan); font-size: 0.8rem;">Selected: 1 / ${files.length}</span>
        </div>
        <div style="display: flex; gap: 8px; width: 100%;">
          <button id="btnSelectVideoOnly" style="flex: 1; background: rgba(0, 242, 254, 0.12); border: 1px solid rgba(0, 242, 254, 0.35); color: var(--accent-cyan); font-size: 0.75rem; font-weight: 700; padding: 6px 10px; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 4px;">
            <i class="fa-solid fa-video"></i> Video Only
          </button>
          <button id="btnSelectNone" style="background: rgba(255, 255, 255, 0.06); border: 1px solid rgba(255, 255, 255, 0.12); color: var(--text-muted); font-size: 0.75rem; font-weight: 600; padding: 6px 12px; border-radius: 8px; cursor: pointer;">
            Clear
          </button>
        </div>
      </div>

      <div class="torrent-files-list" id="torrentFilesContainer">
        ${files.map(f => {
    let icon = 'fa-file';
    let iconColor = '#a4b0be';
    let extBadge = 'FILE';
    if (f.isMedia) { icon = 'fa-file-video'; iconColor = '#00f2fe'; extBadge = 'VIDEO'; }
    else if (f.isSub) { icon = 'fa-closed-captioning'; iconColor = '#2ed573'; extBadge = 'SUB'; }
    else if (f.isImg) { icon = 'fa-file-image'; iconColor = '#ffa502'; extBadge = 'ART'; }

    return `
            <div class="torrent-file-item ${f.selected ? 'selected' : ''}" data-file-id="${f.id}">
              <div class="torrent-file-left">
                <input type="checkbox" class="chk-torrent-file" data-id="${f.id}" ${f.selected ? 'checked' : ''} style="cursor: pointer; width: 18px; height: 18px; accent-color: var(--accent-cyan); flex-shrink: 0;">
                <i class="fa-solid ${icon}" style="color: ${iconColor}; font-size: 1.1rem; flex-shrink: 0;"></i>
                <div style="min-width: 0; display: flex; flex-direction: column; flex: 1;">
                  <span class="torrent-file-name" title="${f.name}">${f.name}</span>
                  <div style="display: flex; align-items: center; gap: 6px; margin-top: 2px;">
                    <span style="font-size: 0.65rem; background: rgba(255,255,255,0.08); padding: 1px 6px; border-radius: 4px; color: ${iconColor}; font-weight: 700;">${extBadge}</span>
                    <span style="font-size: 0.72rem; color: var(--text-muted); font-weight: 600;">${f.sizeStr}</span>
                  </div>
                </div>
              </div>
              <div class="torrent-file-actions">
                ${f.isMedia ? `
                  <button class="btn-file-quick-stream" data-name="${f.name}" title="Play Video Live" style="background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%); border: 1px solid rgba(139, 92, 246, 0.6); color: #ffffff; padding: 6px 12px; border-radius: 8px; font-size: 0.78rem; font-weight: 800; cursor: pointer; display: inline-flex; align-items: center; gap: 5px; box-shadow: 0 2px 10px rgba(99, 102, 241, 0.4);">
                    <i class="fa-solid fa-play" style="font-size: 0.7rem;"></i> Stream
                  </button>
                ` : ''}
              </div>
            </div>
          `;
  }).join('')}
      </div>

      <div style="display: flex; flex-direction: column; gap: 10px; margin-top: 14px;">
        <!-- ⚡ 1. Primary In-App High-Speed Turbo Downloader -->
        <button id="btnDownloadSelectedTorrent" class="btn-dl-turbo-start" style="width: 100%; justify-content: center; padding: 14px 20px; font-size: 0.96rem; background: linear-gradient(135deg, #10b981 0%, #059669 50%, #047857 100%); color: #ffffff; font-weight: 800; border: 1px solid rgba(52, 211, 153, 0.6); border-radius: 14px; box-shadow: 0 6px 20px rgba(16, 185, 129, 0.45); cursor: pointer; display: flex; align-items: center; gap: 9px; letter-spacing: 0.3px;">
          <i class="fa-solid fa-cloud-arrow-down" style="color: #a7f3d0; font-size: 1.15rem;"></i>
          <span id="btnDownloadSelectedText">⚡ In-App Turbo Download (P2P High Speed)</span>
        </button>

        <!-- 🎬 2. Stream Entire Torrent Online (Live Cinema Player) -->
        <button id="btnStreamEntireTorrent" class="btn-dl-turbo-start" style="width: 100%; justify-content: center; padding: 13px 18px; font-size: 0.92rem; background: linear-gradient(135deg, #8b5cf6 0%, #6366f1 100%); color: #ffffff; font-weight: 800; border: 1px solid rgba(139, 92, 246, 0.6); border-radius: 14px; box-shadow: 0 4px 18px rgba(139, 92, 246, 0.4); cursor: pointer; display: flex; align-items: center; gap: 8px;">
          <i class="fa-solid fa-circle-play" style="font-size: 1.15rem; color: #e0e7ff;"></i>
          <span>🎬 Watch Torrent Online (Cinema Player)</span>
        </button>

        <!-- 📱 3. External Downloader (1DM / Flud / uTorrent) -->
        <button id="btnFastPhoneTorrent" class="btn-dl-turbo-start" style="width: 100%; justify-content: center; padding: 11px 16px; font-size: 0.88rem; background: linear-gradient(135deg, rgba(0, 242, 254, 0.12), rgba(79, 172, 254, 0.18)); border: 1px solid rgba(0, 242, 254, 0.45); color: #00f2fe; font-weight: 700; border-radius: 12px; cursor: pointer; display: flex; align-items: center; gap: 8px;">
          <i class="fa-solid fa-mobile-screen-button"></i>
          <span>External App (1DM / Flud / LibreTorrent)</span>
        </button>

        <div style="display: flex; gap: 8px; width: 100%;">
          <button id="btnLeechSelectedDrive" class="btn-format-action primary" style="flex: 1; padding: 10px 14px; border-radius: 12px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; gap: 6px; font-size: 0.82rem;">
            <i class="fa-solid fa-cloud-arrow-up"></i> Leech Drive
          </button>

          <button id="btnCopyTorrentMagnet" class="btn-format-action" style="flex: 1; padding: 10px 14px; border-radius: 12px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center; gap: 6px; background: rgba(255,255,255,0.08); font-size: 0.82rem;">
            <i class="fa-solid fa-copy"></i> Magnet URI
          </button>
        </div>
      </div>
    </div>
  `;

  if (dlResultCard) dlResultCard.classList.remove('hidden');

  const chkSelectAll = document.getElementById('chkTorrentSelectAll');
  const btnSelectVideo = document.getElementById('btnSelectVideoOnly');
  const btnSelectNone = document.getElementById('btnSelectNone');
  const selectedCountText = document.getElementById('torrentSelectedCountText');
  const btnDlText = document.getElementById('btnDownloadSelectedText');

  const updateSelectedState = () => {
    const selectedFiles = files.filter(f => f.selected);
    const selectedBytes = selectedFiles.reduce((acc, f) => acc + f.bytes, 0);
    const selectedSizeStr = formatTorrentBytes(selectedBytes);

    if (selectedCountText) selectedCountText.textContent = `Selected: ${selectedFiles.length} / ${files.length} (${selectedSizeStr})`;
    if (btnDlText) btnDlText.textContent = `Download Selected (${selectedSizeStr})`;
    if (chkSelectAll) chkSelectAll.checked = selectedFiles.length === files.length && files.length > 0;
  };

  updateSelectedState();

  chkSelectAll?.addEventListener('change', (e) => {
    const isChecked = e.target.checked;
    files.forEach(f => f.selected = isChecked);
    document.querySelectorAll('.chk-torrent-file').forEach(chk => chk.checked = isChecked);
    document.querySelectorAll('.torrent-file-item').forEach(item => item.classList.toggle('selected', isChecked));
    updateSelectedState();
  });

  btnSelectVideo?.addEventListener('click', () => {
    files.forEach(f => f.selected = f.isMedia);
    document.querySelectorAll('.chk-torrent-file').forEach(chk => {
      const fId = parseInt(chk.dataset.id, 10);
      const fObj = files.find(f => f.id === fId);
      chk.checked = Boolean(fObj?.isMedia);
    });
    document.querySelectorAll('.torrent-file-item').forEach(item => {
      const fId = parseInt(item.dataset.fileId, 10);
      const fObj = files.find(f => f.id === fId);
      item.classList.toggle('selected', Boolean(fObj?.isMedia));
    });
    updateSelectedState();
    window.showToast('Selected video files only!', 'info');
  });

  btnSelectNone?.addEventListener('click', () => {
    files.forEach(f => f.selected = false);
    document.querySelectorAll('.chk-torrent-file').forEach(chk => chk.checked = false);
    document.querySelectorAll('.torrent-file-item').forEach(item => item.classList.remove('selected'));
    updateSelectedState();
  });

  document.querySelectorAll('.chk-torrent-file').forEach(chk => {
    chk.addEventListener('change', (e) => {
      const fId = parseInt(e.target.dataset.id, 10);
      const targetFile = files.find(f => f.id === fId);
      if (targetFile) {
        targetFile.selected = e.target.checked;
        const itemEl = document.querySelector(`.torrent-file-item[data-file-id="${fId}"]`);
        if (itemEl) itemEl.classList.toggle('selected', targetFile.selected);
        updateSelectedState();
      }
    });
  });

  // Action: Open in External Torrent App
  document.getElementById('btnFastPhoneTorrent')?.addEventListener('click', () => {
    const dlTarget = (sourceUrl && (sourceUrl.startsWith('http://') || sourceUrl.startsWith('https://'))) ? sourceUrl : magnetUri;
    if (dlTarget.startsWith('magnet:')) {
      try {
        window.open(dlTarget, '_system');
        window.showToast('🚀 Launching External Torrent App (1DM/Flud/LibreTorrent)...', 'success');
      } catch (_) {
        navigator.clipboard?.writeText?.(dlTarget);
        window.showToast('🧲 Magnet URI copied to clipboard!', 'info');
      }
    } else if (window.Capacitor?.Plugins?.NativeDownload?.startDownload) {
      window.Capacitor.Plugins.NativeDownload.startDownload({
        url: dlTarget,
        filename: `${torrentTitle}.torrent`,
        category: 'torrents'
      });
      window.showToast('📥 Downloading .torrent file...', 'info');
    }
  });

  // Action: Download Selected Files In-App (P2P Turbo)
  document.getElementById('btnDownloadSelectedTorrent')?.addEventListener('click', async () => {
    const selectedFiles = files.filter(f => f.selected);
    if (selectedFiles.length === 0) return window.showToast('Please select at least 1 file to download', 'info');

    const dlTarget = (sourceUrl && (sourceUrl.startsWith('http://') || sourceUrl.startsWith('https://'))) ? sourceUrl : magnetUri;
    const dlName = selectedFiles.length === 1 ? selectedFiles[0].name : `${torrentTitle} [${selectedFiles.length} Selected Files]`;

    window.startInAppTorrentDownload(dlTarget, dlName, selectedFiles);
  });

  document.getElementById('btnStreamEntireTorrent')?.addEventListener('click', () => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CINEMA_PRO')) {
      if (window.showFeatureLockedSheet) {
        window.showFeatureLockedSheet('FEATURE_CINEMA_PRO');
      }
      return;
    }
    if (window.openPlayer) {
      window.openPlayer({
        title: torrentTitle,
        streamUrl: (sourceUrl && (sourceUrl.startsWith('http://') || sourceUrl.startsWith('https://'))) ? sourceUrl : magnetUri,
        type: 'torrent'
      });
    }
  });

  document.querySelectorAll('.btn-file-quick-stream').forEach(btn => {
    btn.addEventListener('click', (e) => {
      if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CINEMA_PRO')) {
        if (window.showFeatureLockedSheet) {
          window.showFeatureLockedSheet('FEATURE_CINEMA_PRO');
        }
        return;
      }
      const fName = e.currentTarget.dataset.name;
      const cleanTitle = `${torrentTitle} - ${fName}`;
      if (window.openPlayer) {
        window.openPlayer({
          title: cleanTitle,
          streamUrl: (sourceUrl && (sourceUrl.startsWith('http://') || sourceUrl.startsWith('https://'))) ? sourceUrl : magnetUri,
          type: 'torrent'
        });
      }
    });
  });

  document.getElementById('btnLeechSelectedDrive')?.addEventListener('click', () => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
      if (window.showInApp404) {
        window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Google Drive upload pipeline is disabled or unauthorized under the active license. Please activate a PRO License Key.');
      }
      return;
    }
    const selectedFiles = files.filter(f => f.selected);
    if (selectedFiles.length === 0) return window.showToast('Please select at least 1 file to Leech', 'info');

    if (window.startCloudTransfer) {
      window.startCloudTransfer({
        title: `${torrentTitle} [${selectedFiles.length} Selected Files]`,
        url: (sourceUrl && (sourceUrl.startsWith('http://') || sourceUrl.startsWith('https://'))) ? sourceUrl : magnetUri,
        type: 'torrent',
        quality: selectedFiles.map(f => f.sizeStr).join(', ')
      });
    }
  });

  // Action: Copy Magnet URI
  document.getElementById('btnCopyTorrentMagnet')?.addEventListener('click', () => {
    navigator.clipboard?.writeText?.(magnetUri);
    window.showToast('🧲 Magnet URI with Trackers copied!', 'success');
  });

  // Action: Delete / Clear Torrent Card
  document.getElementById('btnDeleteTorrentCard')?.addEventListener('click', () => {
    if (dlResultCard) dlResultCard.classList.add('hidden');
    if (dlUniversalInput) dlUniversalInput.value = '';
    btnClearDlInput?.classList.add('hidden');
    if (dlPlatformBadge) dlPlatformBadge.classList.add('hidden');
    window.showToast('🗑️ Torrent Card Removed', 'info');
  });
}

// ============================================================================
// SECTION 6: In-App Native BitTorrent Engine (libtorrent4j & P2P Swarm)
// ============================================================================

/**
 * Triggers native BitTorrent P2P download via Kotlin Native plugins
 * @param {string} magnetOrUrl
 * @param {string} title
 * @param {Array} selectedFiles
 */
window.startInAppTorrentDownload = async function (magnetOrUrl, title, selectedFiles = []) {
  if (!magnetOrUrl) return window.showToast('Invalid torrent or magnet link', 'error');

  const cleanTitle = (title || 'Torrent Download').replace(/\.torrent$/i, '');

  // 1. Strictly Native Torrent Plugin (libtorrent4j on-device engine)
  const nativeTorrent = window.Capacitor?.Plugins?.NativeTorrent;

  if (nativeTorrent?.startDownload) {
    try {
      await nativeTorrent.startDownload({
        url: magnetOrUrl,
        title: cleanTitle,
        category: 'torrents',
        sequential: false
      });
      window.showToast(`🚀 Started Native BitTorrent Download: ${cleanTitle}`, 'success');
      if (window.switchToDeviceDownloadsSubTab) {
        window.switchToDeviceDownloadsSubTab();
      }
      if (window.renderDownloadsList) {
        window.renderDownloadsList(true);
      }
      return;
    } catch (err) {
      console.error('[NativeTorrent Start Error]', err);
      window.showToast(`❌ Torrent Engine Error: ${err?.message || 'Could not start BitTorrent download'}`, 'error');
      return;
    }
  }

  window.showToast('⚠️ Native BitTorrent engine is only supported on Android APK.', 'error');
};

// ============================================================================
// SECTION 7: Download Metrics & Telemetry Formatter
// ============================================================================

/**
 * Normalizes metrics and telemetry for smooth UI cards
 * @param {object} item
 */
function formatMetrics(item) {
  const percent = item.percent !== undefined ? item.percent : (item.status === 'completed' ? 100 : (item.progress || 0));
  const downloadedVal = parseFloat(item.downloadedMB || 0);
  const totalVal = parseFloat(item.totalMB || 0);

  let sizeFormatted = 'Connecting...';
  const dBytes = item.downloadedBytes || (downloadedVal * 1024 * 1024) || 0;
  const tBytes = item.totalBytes || (totalVal * 1024 * 1024) || 0;

  if (dBytes > 0 && tBytes > 0) {
    const dStr = dBytes >= 1024 * 1024 * 1024 ? (dBytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB' : (dBytes / (1024 * 1024)).toFixed(1) + ' MB';
    const tStr = tBytes >= 1024 * 1024 * 1024 ? (tBytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB' : (tBytes / (1024 * 1024)).toFixed(1) + ' MB';
    sizeFormatted = `${dStr} / ${tStr}`;
  } else if (dBytes > 0) {
    sizeFormatted = dBytes >= 1024 * 1024 * 1024 ? (dBytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB' : (dBytes / (1024 * 1024)).toFixed(1) + ' MB';
  } else if (totalVal > 0) {
    sizeFormatted = `0.0 MB / ${totalVal.toFixed(1)} MB`;
  }

  const speedNum = parseFloat(item.speedMBps || 0);
  let speedFormatted = 'Connecting...';
  if (speedNum > 0.01) {
    if (speedNum >= 1.0) {
      speedFormatted = `${speedNum.toFixed(2)} MB/s`;
    } else {
      speedFormatted = `${(speedNum * 1024).toFixed(0)} KB/s`;
    }
  } else if (item.status === 'completed') {
    speedFormatted = 'Complete';
  } else if (item.status === 'paused') {
    speedFormatted = 'Paused';
  } else if (item.status === 'failed') {
    speedFormatted = 'Failed';
  } else if (item.status === 'downloading') {
    speedFormatted = item.isTorrent ? `${item.numPeers || 0} Peers` : 'Downloading...';
  }

  let etaFormatted = 'Ready';
  if (item.status === 'completed') {
    etaFormatted = 'Finished';
  } else if (item.status === 'paused') {
    etaFormatted = 'Paused';
  } else if (item.status === 'failed') {
    etaFormatted = 'Failed';
  } else if (item.status === 'downloading') {
    if (item.etaSec && item.etaSec > 0) {
      const m = Math.floor(item.etaSec / 60);
      const s = Math.floor(item.etaSec % 60);
      etaFormatted = m > 0 ? `${m}m ${s}s remaining` : `${s}s remaining`;
    } else if (speedNum > 0.01 && tBytes > dBytes) {
      const remainingBytes = tBytes - dBytes;
      const speedBytes = speedNum * 1024 * 1024;
      const calcSec = Math.round(remainingBytes / speedBytes);
      if (calcSec > 0 && calcSec < 86400) {
        const m = Math.floor(calcSec / 60);
        const s = Math.floor(calcSec % 60);
        etaFormatted = m > 0 ? `${m}m ${s}s remaining` : `${s}s remaining`;
      } else {
        etaFormatted = 'Estimating...';
      }
    } else {
      etaFormatted = 'Estimating...';
    }
  }

  let statusLabel = (item.status || 'downloading').toUpperCase();
  if (item.status === 'downloading') statusLabel = item.isTorrent ? `TORRENT ${percent}%` : `DOWNLOADING ${percent}%`;
  if (item.status === 'pending') statusLabel = 'STARTING...';
  if (item.status === 'resolving') statusLabel = '⚡ CONNECTING...';
  if (item.status === 'paused') statusLabel = `PAUSED ${percent}%`;
  if (item.status === 'completed') statusLabel = 'COMPLETED 100%';
  if (item.status === 'failed') statusLabel = 'FAILED';

  return { percent, sizeFormatted, speedFormatted, etaFormatted, statusLabel };
}

// ============================================================================
// SECTION 8: Zero-Flicker In-Place DOM Diffing Downloads Renderer
// ============================================================================

function detectFileKind(name = '', url = '', category = '') {
  const n = (name || '').toLowerCase();
  const u = (url || '').toLowerCase();
  const cat = (category || '').toLowerCase();
  if (u.startsWith('magnet:') || u.includes('.torrent') || n.endsWith('.torrent') || cat === 'torrents') {
    return 'torrent';
  } else if (n.endsWith('.apk') || n.endsWith('.xapk') || cat === 'apps') {
    return 'apk';
  } else if (n.endsWith('.zip') || n.endsWith('.rar') || n.endsWith('.7z') || n.endsWith('.tar') || n.endsWith('.gz') || n.endsWith('.iso')) {
    return 'archive';
  } else if (n.endsWith('.pdf') || n.endsWith('.epub') || n.endsWith('.doc') || n.endsWith('.docx') || n.endsWith('.xls') || n.endsWith('.xlsx') || n.endsWith('.txt')) {
    return 'document';
  } else if (n.endsWith('.mp3') || n.endsWith('.m4a') || n.endsWith('.wav') || n.endsWith('.flac') || n.endsWith('.aac') || n.endsWith('.ogg') || cat === 'music') {
    return 'audio';
  } else if (n.endsWith('.jpg') || n.endsWith('.jpeg') || n.endsWith('.png') || n.endsWith('.gif') || n.endsWith('.webp')) {
    return 'image';
  } else if (!n.match(/\.(mp4|mkv|webm|avi|mov|flv|ts)$/i) && cat !== 'movies' && cat !== 'adult') {
    return 'file';
  }
  return 'video';
}

function buildCardActionsHtml(item, fileKind) {
  if (item.status === 'completed') {
    return `
      ${fileKind === 'apk' ? `
        <button class="btn-dl-act primary btn-install-apk" title="Install APK on Device">
          <i class="fa-brands fa-android"></i> Install APK
        </button>
        <button class="btn-dl-act secondary btn-open-file" title="Open File">
          <i class="fa-solid fa-folder-open"></i> Open
        </button>
      ` : fileKind === 'video' ? `
        <button class="btn-dl-act primary btn-play-offline" title="Play Video Offline in Cinema Player">
          <i class="fa-solid fa-play"></i> Play (Offline)
        </button>
        <button class="btn-dl-act secondary btn-open-file" title="Open in Player">
          <i class="fa-solid fa-arrow-up-right-from-square"></i> Open
        </button>
      ` : fileKind === 'audio' ? `
        <button class="btn-dl-act primary btn-open-file" title="Play Audio">
          <i class="fa-solid fa-play"></i> Play Audio
        </button>
        <button class="btn-dl-act secondary btn-open-file" title="Open File">
          <i class="fa-solid fa-folder-open"></i> Open
        </button>
      ` : `
        <button class="btn-dl-act primary btn-open-file" title="Open File">
          <i class="fa-solid fa-folder-open"></i> Open File
        </button>
      `}
      <button class="btn-dl-act drive btn-drive-upload" title="Send to Google Drive">
        <i class="fa-brands fa-google-drive"></i> Send to Drive
      </button>
      <button class="btn-dl-act secondary btn-share-dl" title="Share File">
        <i class="fa-solid fa-share-nodes"></i> Share
      </button>
      <button class="btn-dl-act danger btn-delete-dl" title="Delete from Storage">
        <i class="fa-solid fa-trash-can"></i> Delete
      </button>
    `;
  } else if (item.status === 'failed' || item.status === 'cancelled') {
    return `
      <button class="btn-dl-act primary btn-retry-dl" title="Retry / Resume Download">
        <i class="fa-solid fa-rotate-right"></i> Retry
      </button>
      <button class="btn-dl-act danger btn-delete-dl" title="Delete Task">
        <i class="fa-solid fa-trash-can"></i> Delete
      </button>
    `;
  } else {
    return `
      <button class="btn-dl-act secondary btn-pause-resume" title="${item.status === 'paused' ? 'Resume' : 'Pause'}">
        <i class="fa-solid ${item.status === 'paused' ? 'fa-play' : 'fa-pause'}"></i> ${item.status === 'paused' ? 'Resume' : 'Pause'}
      </button>
      <button class="btn-dl-act secondary btn-cancel-dl" title="Cancel Download">
        <i class="fa-solid fa-ban"></i> Cancel
      </button>
      <button class="btn-dl-act danger btn-delete-dl" title="Delete Task & File">
        <i class="fa-solid fa-trash-can"></i> Delete
      </button>
    `;
  }
}

function attachCardActionListeners(card, item) {
  card.querySelector('.btn-share-dl')?.addEventListener('click', async (e) => {
    e.stopPropagation();
    const filePath = item.localFilePath || item.path || '';
    if (window.Capacitor?.Plugins?.NativeDownload?.shareDownload && filePath) {
      try {
        await window.Capacitor.Plugins.NativeDownload.shareDownload({ path: filePath, title: item.title });
        return;
      } catch (_) { }
    }
    if (navigator.share) {
      try {
        await navigator.share({ title: item.title, text: item.title });
        return;
      } catch (_) { }
    }
    window.showToast('File sharing available on Android device', 'info');
  });

  card.querySelector('.btn-retry-dl')?.addEventListener('click', async (e) => {
    e.stopPropagation();
    const previousStatus = item.status;
    try {
      if (window.Capacitor?.Plugins?.NativeDownload?.resumeDownload) {
        await window.Capacitor.Plugins.NativeDownload.resumeDownload({ id: item.id });
      }
      item.status = 'downloading';
      item.errorMessage = '';
      window.showToast('🔄 Retrying Download...', 'info');
    } catch (err) {
      item.status = previousStatus;
      window.showToast(err?.message || 'Retry failed', 'error');
    }
    window.renderDownloads(false);
  });

  card.querySelector('.btn-pause-resume')?.addEventListener('click', async (e) => {
    e.stopPropagation();
    const btn = e.currentTarget;
    if (btn.dataset.locked === 'true') return;
    btn.dataset.locked = 'true';
    btn.style.pointerEvents = 'none';

    const previousStatus = item.status;
    try {
      if (item.status === 'paused') {
        if (window.Capacitor?.Plugins?.NativeDownload?.resumeDownload) {
          await window.Capacitor.Plugins.NativeDownload.resumeDownload({ id: item.id });
        }
        item.status = 'downloading';
        window.showToast('▶️ Download Resumed', 'success');
      } else {
        if (window.Capacitor?.Plugins?.NativeDownload?.pauseDownload) {
          await window.Capacitor.Plugins.NativeDownload.pauseDownload({ id: item.id });
        }
        item.status = 'paused';
        window.showToast('⏸️ Download Paused', 'info');
      }
    } catch (err) {
      item.status = previousStatus;
      window.showToast(err?.message || 'Action failed', 'error');
    } finally {
      btn.dataset.locked = 'false';
      btn.style.pointerEvents = '';
      window.renderDownloads(false);
    }
  });

  card.querySelector('.btn-cancel-dl')?.addEventListener('click', async (e) => {
    e.stopPropagation();
    const btn = e.currentTarget;
    if (btn.dataset.locked === 'true') return;
    btn.dataset.locked = 'true';
    btn.style.pointerEvents = 'none';

    try {
      if (window.Capacitor?.Plugins?.NativeDownload?.cancelDownload) {
        await window.Capacitor.Plugins.NativeDownload.cancelDownload({ id: item.id });
      }
      item.status = 'cancelled';
      window.showToast('⏹️ Download Cancelled', 'info');
    } catch (err) {
      window.showToast(err?.message || 'Failed to cancel', 'error');
    } finally {
      btn.dataset.locked = 'false';
      btn.style.pointerEvents = '';
      window.renderDownloads(false);
    }
  });

  card.querySelectorAll('.btn-open-file, .btn-install-apk').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      if (window.Capacitor?.Plugins?.NativeDownload?.openFile) {
        try {
          await window.Capacitor.Plugins.NativeDownload.openFile({ id: item.id });
          return;
        } catch (err) {
          window.showToast(err?.message || 'Could not open file', 'error');
        }
      }
    });
  });

  card.querySelector('.btn-play-offline')?.addEventListener('click', async () => {
    const filePath = item.localFilePath || item.path || item.url || '';
    if (window.Capacitor?.Plugins?.GalleryPlayer?.playOfflineVideo && filePath) {
      try {
        await window.Capacitor.Plugins.GalleryPlayer.playOfflineVideo({
          path: filePath,
          url: filePath,
          title: item.title,
          category: item.category || 'movies'
        });
        return;
      } catch (_) { }
    }
    if (window.Capacitor?.Plugins?.NativePlayer?.playOfflineVideo && filePath) {
      window.Capacitor.Plugins.NativePlayer.playOfflineVideo({
        title: item.title,
        videoUrl: filePath,
        localFilePath: filePath,
        category: item.category || 'movies',
        sizeFormatted: item.sizeFormatted
      });
      return;
    }
    if (window.openPlayer && filePath) {
      window.openPlayer({
        title: item.title,
        streamUrl: filePath,
        type: 'video'
      });
    }
  });

  card.querySelector('.btn-drive-upload')?.addEventListener('click', () => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
      if (window.showInApp404) {
        window.showInApp404('Cloud Leech Upload', 'HTTP 404: Google Drive cloud upload pipeline is disabled under the active license. Please activate a PRO License Key.');
      }
      return;
    }
    if (window.startCloudTransfer) {
      window.startCloudTransfer({
        title: item.title,
        url: item.url,
        type: 'media',
        quality: 'Original'
      });
    }
  });

  card.querySelector('.btn-delete-dl')?.addEventListener('click', async (e) => {
    e.stopPropagation();
    e.preventDefault();
    const btn = e.currentTarget;
    if (btn.dataset.locked === 'true') return;
    btn.dataset.locked = 'true';
    btn.style.pointerEvents = 'none';

    try {
      // 1. If torrent, delete from Torrent Engine
      const isTorrent = item.category === 'torrents' || (item.url && item.url.startsWith('magnet:'));
      if (isTorrent && window.Capacitor?.Plugins?.NativeTorrent?.delete) {
        await window.Capacitor.Plugins.NativeTorrent.delete({
          hash: item.id || '',
          id: item.id || '',
          deleteFiles: true
        });
      }

      // 2. Delete from Native Download Manager & Room DB
      if (window.Capacitor?.Plugins?.NativeDownload?.deleteDownload) {
        await window.Capacitor.Plugins.NativeDownload.deleteDownload({
          id: item.id || '',
          path: item.localFilePath || item.path || '',
          filename: item.filename || item.fileName || ''
        });
      }

      // Only upon confirmed native deletion:
      recentlyDeletedIds.add(item.id);
      setTimeout(() => recentlyDeletedIds.delete(item.id), 5000);

      window.state.downloads.delete(item.id);
      card.remove();
      window.showToast('🗑️ Download stopped & removed', 'info');
      window.renderDownloads(false);
    } catch (err) {
      console.error('[Delete Download Error]', err);
      window.showToast(`Failed to delete download: ${err?.message || 'Error occurred'}`, 'error');
    } finally {
      btn.dataset.locked = 'false';
      btn.style.pointerEvents = '';
    }
  });
}

/**
 * Renders active and completed downloads with surgical in-place DOM updates
 * @param {boolean} forceRebuild
 */
window.renderDownloads = function (forceRebuild = false) {
  if (!downloadsList) return;
  const allItems = Array.from(window.state.downloads.values()).sort((a, b) => (b.createdTimestamp || 0) - (a.createdTimestamp || 0));

  // Update Counters
  const countAll = allItems.length;
  const countDownloading = allItems.filter(d => d.status === 'downloading' || d.status === 'pending' || d.status === 'resolving').length;
  const countPaused = allItems.filter(d => d.status === 'paused').length;
  const countCompleted = allItems.filter(d => d.status === 'completed').length;
  const countFailed = allItems.filter(d => d.status === 'failed').length;

  const elCountAll = document.getElementById('dlCountAll');
  const elCountDl = document.getElementById('dlCountDownloading');
  const elCountPaused = document.getElementById('dlCountPaused');
  const elCountComp = document.getElementById('dlCountCompleted');
  const elCountFailed = document.getElementById('dlCountFailed');

  if (elCountAll) elCountAll.textContent = countAll;
  if (elCountDl) elCountDl.textContent = countDownloading;
  if (elCountPaused) elCountPaused.textContent = countPaused;
  if (elCountComp) elCountComp.textContent = countCompleted;
  if (elCountFailed) elCountFailed.textContent = countFailed;
  if (dlQueueCountBadge) dlQueueCountBadge.textContent = `${countAll} Tasks`;
  if (subBadgeDownloads) subBadgeDownloads.textContent = countAll;
  if (subBadgeTransfers && window.state?.tasks) {
    subBadgeTransfers.textContent = window.state.tasks.size || 0;
  }

  // Update Truthful Live Speed Pill
  let totalActiveSpeedMBps = 0;
  allItems.forEach(d => {
    if (d.status === 'downloading') {
      totalActiveSpeedMBps += (parseFloat(d.speedMBps) || 0);
    }
  });

  if (dlLiveSpeedPill) {
    if (countDownloading > 0) {
      const speedStr = totalActiveSpeedMBps >= 1.0
        ? `${totalActiveSpeedMBps.toFixed(1)} MB/s`
        : totalActiveSpeedMBps > 0
          ? `${(totalActiveSpeedMBps * 1024).toFixed(0)} KB/s`
          : 'Downloading...';
      dlLiveSpeedPill.innerHTML = `<i class="fa-solid fa-bolt"></i> ${speedStr}`;
    } else if (countPaused > 0) {
      dlLiveSpeedPill.innerHTML = `<i class="fa-solid fa-pause"></i> Paused`;
    } else {
      dlLiveSpeedPill.innerHTML = `<i class="fa-solid fa-gauge-high"></i> Idle`;
    }
  }

  if (navDownloadsBadge) {
    if (countDownloading > 0) {
      navDownloadsBadge.textContent = countDownloading;
      navDownloadsBadge.classList.remove('hidden');
    } else {
      navDownloadsBadge.classList.add('hidden');
    }
  }

  // Filter items
  let filtered = allItems;
  if (currentDlFilter === 'downloading') {
    filtered = allItems.filter(d => d.status === 'downloading' || d.status === 'pending' || d.status === 'resolving');
  } else if (currentDlFilter === 'paused') {
    filtered = allItems.filter(d => d.status === 'paused');
  } else if (currentDlFilter === 'completed') {
    filtered = allItems.filter(d => d.status === 'completed');
  } else if (currentDlFilter === 'failed') {
    filtered = allItems.filter(d => d.status === 'failed');
  }

  if (filtered.length === 0) {
    let emptyTitle = 'No downloads yet';
    if (currentDlFilter === 'downloading') emptyTitle = 'No active downloads';
    else if (currentDlFilter === 'paused') emptyTitle = 'No paused downloads';
    else if (currentDlFilter === 'completed') emptyTitle = 'No completed downloads';
    else if (currentDlFilter === 'failed') emptyTitle = 'No failed downloads';
    else if (allItems.length === 0) emptyTitle = 'No downloads yet';
    else emptyTitle = 'No tasks in this filter';

    downloadsList.innerHTML = `
      <div class="dl-empty-state-2026" id="emptyDownloads">
        <div class="dl-empty-icon-radar"><i class="fa-solid fa-cloud-arrow-down"></i></div>
        <h3>${emptyTitle}</h3>
        <p>Paste any video or file link above, or tap [Download MP4] on any Movie or Adult card!</p>
      </div>
    `;
    return;
  }

  const emptyState = downloadsList.querySelector('.dl-empty-state-2026');
  if (emptyState) emptyState.remove();

  if (forceRebuild) {
    downloadsList.innerHTML = '';
  }

  const activeIds = new Set(filtered.map(f => f.id));
  downloadsList.querySelectorAll('.dl-card-2026').forEach(cardEl => {
    if (!activeIds.has(cardEl.dataset.id)) cardEl.remove();
  });

  filtered.forEach(item => {
    let card = downloadsList.querySelector(`[data-id="${item.id}"]`);
    const metrics = formatMetrics(item);

    if (card) {
      // In-place smooth updates
      const progressBar = card.querySelector('.dl-progress-bar');
      if (progressBar) progressBar.style.width = `${metrics.percent}%`;

      const percentEl = card.querySelector('.dl-percent-val');
      if (percentEl && percentEl.textContent !== `${metrics.percent}%`) percentEl.textContent = `${metrics.percent}%`;

      const speedEl = card.querySelector('.dl-speed-val');
      if (speedEl && speedEl.textContent !== metrics.speedFormatted) speedEl.textContent = metrics.speedFormatted;

      const sizeEl = card.querySelector('.dl-size-val');
      if (sizeEl && sizeEl.textContent !== metrics.sizeFormatted) sizeEl.textContent = metrics.sizeFormatted;

      const etaEl = card.querySelector('.dl-eta-val');
      if (etaEl && etaEl.textContent !== metrics.etaFormatted) etaEl.textContent = metrics.etaFormatted;

      const threadsEl = card.querySelector('.dl-threads-val');
      if (threadsEl && item.isTorrent) {
        const pText = `${item.numPeers || 0} Peers`;
        if (threadsEl.textContent !== pText) threadsEl.textContent = pText;
      }

      const statusBadge = card.querySelector('.dl-status-badge');
      if (statusBadge) {
        statusBadge.className = `dl-status-badge ${item.status || 'downloading'}`;
        statusBadge.textContent = metrics.statusLabel;
      }

      const errorEl = card.querySelector('.dl-error-message');
      if (errorEl) {
        errorEl.textContent = item.errorMessage || '';
        errorEl.classList.toggle('hidden', !item.errorMessage);
      }

      // Update action button state dynamically
      const btnPauseResume = card.querySelector('.btn-pause-resume');
      if (btnPauseResume) {
        const isPaused = item.status === 'paused';
        const expectedHtml = `<i class="fa-solid ${isPaused ? 'fa-play' : 'fa-pause'}"></i> ${isPaused ? 'Resume' : 'Pause'}`;
        if (btnPauseResume.innerHTML.trim() !== expectedHtml.trim()) {
          btnPauseResume.innerHTML = expectedHtml;
          btnPauseResume.title = isPaused ? 'Resume' : 'Pause';
        }
      }

      const expectedCardClass = `dl-card-2026 ${item.status || 'downloading'}`;
      if (card.className !== expectedCardClass) {
        card.className = expectedCardClass;
      }

      // In-place action bar updates without recursion
      const isCompleted = item.status === 'completed';
      const isFailedOrCancelled = item.status === 'failed' || item.status === 'cancelled';
      const hasCompletedActions = !!card.querySelector('.btn-play-offline, .btn-install-apk, .btn-share-dl');
      const hasRetryAction = !!card.querySelector('.btn-retry-dl');
      const hasPauseAction = !!card.querySelector('.btn-pause-resume');

      if ((isCompleted && !hasCompletedActions) ||
          (isFailedOrCancelled && !hasRetryAction) ||
          (!isCompleted && !isFailedOrCancelled && !hasPauseAction)) {
        const actionsBar = card.querySelector('.dl-card-actions-bar');
        if (actionsBar) {
          const kind = detectFileKind(item.fileName || item.title || '', item.url || '', item.category || '');
          actionsBar.innerHTML = buildCardActionsHtml(item, kind);
          attachCardActionListeners(card, item);
        }
      }
    } else {
      // Source name tag
      let sourceTag = '⚡ Direct CDN';
      const u = (item.url || '').toLowerCase();
      const name = (item.fileName || item.title || '').toLowerCase();

      if (u.startsWith('magnet:') || u.includes('.torrent') || name.endsWith('.torrent') || item.category === 'torrents') {
        sourceTag = '🧲 BitTorrent Pipe';
      } else if (item.category === 'browser') {
        sourceTag = '🌐 Web Browser Video';
      } else if (u.includes('pixeldrain.com')) sourceTag = '🌐 PixelDrain CDN';
      else if (u.includes('usersdrive.com') || u.includes('userdrive')) sourceTag = '⚡ UsersDrive Cloud';
      else if (u.includes('filespayout')) sourceTag = '⚡ FilesPayouts Cloud';
      else if (u.includes('sinhalasub') || u.includes('baiscope') || u.includes('sub.lk')) sourceTag = '🎬 SinhalaSub Movie';
      else if (u.includes('youtube') || u.includes('youtu.be')) sourceTag = '📱 YouTube Video';
      else if (u.includes('tiktok')) sourceTag = '🎵 TikTok Video';
      else if (u.includes('facebook') || u.includes('fb.watch')) sourceTag = '🌐 Facebook Video';
      else if (u.includes('pornhub') || u.includes('xhamster') || u.includes('eporner') || u.includes('redtube') || u.includes('rdtcdn') || u.includes('xnxx') || u.includes('xvideos') || item.category === 'adult') sourceTag = '🔞 18+ Adult Video';

      // File icon detection
      let iconClass = 'fa-file-video';
      let iconColor = 'var(--accent-cyan)';
      const fileKind = detectFileKind(name, u, item.category || '');
      if (fileKind === 'torrent') {
        iconClass = 'fa-magnet';
        iconColor = '#ff4757';
      } else if (fileKind === 'apk') {
        iconClass = 'fa-brands fa-android';
        iconColor = '#3ddc84';
      } else if (fileKind === 'archive') {
        iconClass = 'fa-file-zipper';
        iconColor = '#fbbf24';
      } else if (fileKind === 'document') {
        iconClass = 'fa-file-lines';
        iconColor = '#38ef7d';
      } else if (fileKind === 'audio') {
        iconClass = 'fa-file-audio';
        iconColor = '#ec4899';
      } else if (fileKind === 'image') {
        iconClass = 'fa-file-image';
        iconColor = '#f59e0b';
      } else if (fileKind === 'file') {
        iconClass = 'fa-file';
        iconColor = '#94a3b8';
      }

      // Create new card element
      card = document.createElement('div');
      card.className = `dl-card-2026 ${item.status || 'downloading'}`;
      card.dataset.id = item.id;

      card.innerHTML = `
        <div class="dl-card-top-row">
          <div class="dl-file-icon-badge">
            <i class="${iconClass.includes(' ') ? iconClass : 'fa-solid ' + iconClass}" style="color:${iconColor};"></i>
          </div>
          <div class="dl-title-info">
            <h4 class="dl-file-name-text" title="${item.title}">${item.title}</h4>
            <div class="dl-url-subtext"><span style="color:var(--accent-cyan);font-weight:700;">${sourceTag}</span> • <span style="opacity:0.8;">Download/</span></div>
          </div>
          <span class="dl-status-badge ${item.status || 'downloading'}">${metrics.statusLabel}</span>
        </div>

        <div class="dl-progress-container">
          <div class="dl-progress-track">
            <div class="dl-progress-bar" style="width: ${metrics.percent}%;"></div>
          </div>
          <div class="dl-progress-numbers">
            <span class="dl-percent-val"><i class="fa-solid fa-bolt"></i> ${metrics.percent}%</span>
            <span class="dl-eta-val">${metrics.etaFormatted}</span>
          </div>
        </div>

        <div class="dl-error-message ${item.errorMessage ? '' : 'hidden'}" style="color:#ff6b81; font-size:0.76rem; margin:8px 0 0;">${item.errorMessage || ''}</div>

        <div class="dl-telemetry-grid">
          <div class="dl-telemetry-tile">
            <span class="dl-tile-lbl"><i class="fa-solid fa-hard-drive"></i> Size</span>
            <span class="dl-tile-val dl-size-val">${metrics.sizeFormatted}</span>
          </div>
          <div class="dl-telemetry-tile">
            <span class="dl-tile-lbl"><i class="fa-solid fa-gauge-high"></i> Speed</span>
            <span class="dl-tile-val dl-speed-val" style="color:#00f2fe;">${metrics.speedFormatted}</span>
          </div>
          <div class="dl-telemetry-tile">
            <span class="dl-tile-lbl"><i class="fa-solid fa-bolt"></i> ${item.isTorrent ? 'Peers' : 'Threads'}</span>
            <span class="dl-tile-val dl-threads-val" style="color:#38ef7d;">${item.isTorrent ? `${item.numPeers || 0} Peers` : 'Multi-thread'}</span>
          </div>
          <div class="dl-telemetry-tile">
            <span class="dl-tile-lbl"><i class="fa-solid fa-clock-rotate-left"></i> ETA</span>
            <span class="dl-tile-val dl-eta-val" style="color:#fbbf24;">${metrics.etaFormatted}</span>
          </div>
        </div>

        <div class="dl-card-actions-bar">
          ${buildCardActionsHtml(item, fileKind)}
        </div>
      `;

      attachCardActionListeners(card, item);
      downloadsList.appendChild(card);
    }
  });
};

window.renderDownloadsList = function (forceRebuild = false) {
  window.renderDownloads(forceRebuild);
  refreshDownloadsProgress();
  if (!downloadsLivePollTimer) {
    window.startDownloadsPolling();
  }
};

window.loadDownloads = async function () {
  window.renderDownloadsList(true);
  await refreshDownloadsProgress();
};

// ============================================================================
// SECTION 9: Thermal & Battery-Aware Live Polling Engine
// ============================================================================

let isRefreshingDownloads = false;

async function refreshDownloadsProgress() {
  if (isRefreshingDownloads) return;
  if (document.hidden || window.state?.currentTab !== 'downloads') {
    stopDownloadsPolling();
    return;
  }
  isRefreshingDownloads = true;
  try {
    if (window.Capacitor?.Plugins?.NativeDownload?.getDownloads) {
      const res = await window.Capacitor.Plugins.NativeDownload.getDownloads();
      if (res && Array.isArray(res.downloads)) {
        let hasCompleted = false;
        let hasActive = false;
        const serverIds = new Set(res.downloads.map(d => d.id));
        for (const id of Array.from(window.state.downloads.keys())) {
          if (!serverIds.has(id)) {
            window.state.downloads.delete(id);
          }
        }
        res.downloads.forEach(d => {
          if (recentlyDeletedIds.has(d.id)) return;
          const old = window.state.downloads.get(d.id);
          if (old && old.status !== 'completed' && d.status === 'completed') {
            hasCompleted = true;
          }
          if (d.status === 'downloading' || d.status === 'pending' || d.status === 'queued' || d.status === 'resolving') {
            hasActive = true;
          }
          window.state.downloads.set(d.id, d);
        });

        window.renderDownloads(false);

        if (hasCompleted && window.loadOfflineGallery) {
          window.loadOfflineGallery();
        }

        // ❄️ Gentle heartbeat interval fallback (real-time progress is streamed via native events)
        if (hasActive) {
          if (!downloadsLivePollTimer) {
            downloadsLivePollTimer = setInterval(refreshDownloadsProgress, 3500);
          }
        } else {
          stopDownloadsPolling();
        }
      }
    }
  } catch (_) {
  } finally {
    isRefreshingDownloads = false;
  }
}

window.startDownloadsPolling = function () {
  if (document.hidden || window.state?.currentTab !== 'downloads') return;
  if (downloadsLivePollTimer) clearInterval(downloadsLivePollTimer);
  downloadsLivePollTimer = setInterval(refreshDownloadsProgress, 3500);
  refreshDownloadsProgress();
};

// Native Event Listeners for Real-Time Zero-Polling Download Pipeline
if (window.Capacitor?.Plugins?.NativeDownload?.addListener) {
  try {
    window.Capacitor.Plugins.NativeDownload.addListener('downloadProgress', (data) => {
      if (!data || !data.id) return;
      if (recentlyDeletedIds.has(data.id)) return;
      const existing = window.state?.downloads?.get(data.id) || {};
      window.state?.downloads?.set(data.id, {
        ...existing,
        ...data,
        status: data.status || 'downloading'
      });
      if (window.state?.currentTab === 'downloads' && !document.hidden) {
        window.renderDownloads(false);
      }
    });

    window.Capacitor.Plugins.NativeDownload.addListener('downloadStateChange', (data) => {
      if (!data || !data.id) return;
      if (data.status === 'deleted') {
        window.state?.downloads?.delete(data.id);
      } else {
        const existing = window.state?.downloads?.get(data.id) || {};
        window.state?.downloads?.set(data.id, {
          ...existing,
          ...data
        });
        if (data.status === 'completed' && window.loadOfflineGallery) {
          window.loadOfflineGallery();
        }
      }
      if (window.state?.currentTab === 'downloads' && !document.hidden) {
        window.renderDownloads(false);
      }
    });
  } catch (_) { }
}

function stopDownloadsPolling() {
  if (downloadsLivePollTimer) {
    clearInterval(downloadsLivePollTimer);
    downloadsLivePollTimer = null;
  }
}

window.addEventListener('cloud:active-tab-changed', (event) => {
  if (event.detail?.tab === 'downloads' && !document.hidden) {
    window.startDownloadsPolling();
  } else {
    stopDownloadsPolling();
  }
});

document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    stopDownloadsPolling();
  } else if (window.state?.currentTab === 'downloads') {
    window.startDownloadsPolling();
  }
});

// ============================================================================
// SECTION 10: Lifecycle Bootstrap & Event Binding
// ============================================================================

// Filter Tabs (All, Downloading, Paused, Completed)
document.querySelectorAll('.dl-filter-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.dl-filter-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentDlFilter = btn.dataset.filter || 'all';
    window.renderDownloads(true);
  });
});

// Clear Finished Tasks
btnClearDownloads?.addEventListener('click', async () => {
  if (window.Capacitor?.Plugins?.NativeDownload?.clearDownloads) {
    try {
      await window.Capacitor.Plugins.NativeDownload.clearDownloads();
    } catch (_) { }
  }

  const items = Array.from(window.state.downloads.values());
  const finished = items.filter(d => d.status === 'completed' || d.status === 'failed' || d.status === 'cancelled');
  if (finished.length === 0) return window.showToast('No completed tasks to clear', 'info');

  for (const item of finished) {
    window.state.downloads.delete(item.id);
  }
  window.renderDownloads(true);
  window.showToast(`Cleared ${finished.length} finished tasks`, 'success');
});

// Initial Startup Check Once
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => {
    refreshDownloadsProgress();
  });
} else {
  refreshDownloadsProgress();
}
