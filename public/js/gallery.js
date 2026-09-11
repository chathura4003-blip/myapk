'use strict';

/**
 * ============================================================================
 * CLOUD DRIVE LEECH - OFFLINE VIDEO VAULT & GALLERY (gallery.js)
 * ============================================================================
 * Architecture Overview:
 *   SECTION 1:  DOM Elements, Layout & Sort Controllers
 *   SECTION 2:  Offline Vault Storage Loader & Category Detector
 *   SECTION 3:  Local Favorites Store Management
 *   SECTION 4:  Master Gallery Renderer (Grid Cinema & List View)
 *   SECTION 5:  Item Share & Storage Deletion Engine
 *   SECTION 6:  Mini Popup Cinema Player Modal Controller
 *   SECTION 7:  Playback Telemetry & Progress Scrubbing Engine
 *   SECTION 8:  Screen Rotation & Video Speed Controller
 *   SECTION 9:  Fullscreen Pro Player & PiP Dispatcher
 *   SECTION 10: Lifecycle Hooks & Vault Startup Auto-Scan
 * ============================================================================
 */

// ============================================================================
// SECTION 1: DOM Elements, Layout & Sort Controllers
// ============================================================================

const galleryGrid = document.getElementById('galleryGrid');
const gallerySearchInput = document.getElementById('gallerySearchInput');
const btnClearGallerySearch = document.getElementById('btnClearGallerySearch');
const btnRefreshGallery = document.getElementById('btnRefreshGallery');
const galleryTotalStorageText = document.getElementById('galleryTotalStorageText');
const galleryFileCountBadge = document.getElementById('galleryFileCountBadge');
const gallerySortSelect = document.getElementById('gallerySortSelect');
const btnLayoutGrid = document.getElementById('btnLayoutGrid');
const btnLayoutList = document.getElementById('btnLayoutList');

let galleryFiles = [];
let currentGalleryFilter = 'all';
let currentGallerySort = 'newest';
let currentGalleryLayout = localStorage.getItem('gallery_layout_mode') || 'grid';

/**
 * Applies and persists gallery display mode ('grid' or 'list')
 * @param {'grid'|'list'} mode
 */
function applyLayoutMode(mode) {
  currentGalleryLayout = mode;
  try {
    localStorage.setItem('gallery_layout_mode', mode);
  } catch (_) { }

  if (galleryGrid) {
    galleryGrid.className = `gallery-grid-2026 ${mode === 'list' ? 'list-view' : 'grid-view'}`;
  }

  btnLayoutGrid?.classList.toggle('active', mode === 'grid');
  btnLayoutList?.classList.toggle('active', mode === 'list');
  renderGallery();
}

btnLayoutGrid?.addEventListener('click', () => applyLayoutMode('grid'));
btnLayoutList?.addEventListener('click', () => applyLayoutMode('list'));

// Sorting Dropdown
gallerySortSelect?.addEventListener('change', () => {
  currentGallerySort = gallerySortSelect.value || 'newest';
  renderGallery();
});

// Search Input
if (gallerySearchInput) {
  gallerySearchInput.addEventListener('input', () => {
    const val = gallerySearchInput.value.trim();
    btnClearGallerySearch?.classList.toggle('hidden', val.length === 0);
    renderGallery();
  });

  btnClearGallerySearch?.addEventListener('click', () => {
    gallerySearchInput.value = '';
    btnClearGallerySearch.classList.add('hidden');
    gallerySearchInput.focus();
    renderGallery();
  });
}

// Refresh Storage Scan
btnRefreshGallery?.addEventListener('click', () => {
  btnRefreshGallery.querySelector('i')?.classList.add('fa-spin');
  window.loadOfflineGallery().finally(() => {
    setTimeout(() => {
      btnRefreshGallery.querySelector('i')?.classList.remove('fa-spin');
    }, 400);
  });
});

// Category Filter Segmented Buttons
document.querySelectorAll('.gallery-filter-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.gallery-filter-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentGalleryFilter = btn.dataset.filter || 'all';
    renderGallery();
  });
});

// ============================================================================
// SECTION 2: Offline Vault Storage Loader & Category Detector
// ============================================================================

/**
 * Scans offline device storage via Kotlin Native plugin or in-memory downloads
 */
window.loadOfflineGallery = async function () {
  if (window.Capacitor?.Plugins?.NativeDownload?.getOfflineGallery) {
    try {
      const res = await window.Capacitor.Plugins.NativeDownload.getOfflineGallery();
      if (res && res.success) {
        window._galleryPermissionDenied = Boolean(res.permissionDenied);
        galleryFiles = Array.isArray(res.files) ? res.files : [];
        if (galleryTotalStorageText) {
          galleryTotalStorageText.textContent = res.totalFormatted || '0 MB';
        }
        renderGallery();
        return;
      }
    } catch (e) {
      console.warn('[Gallery] Native scan fallback:', e);
    }
  }

  // Fallback to in-memory completed downloads
  if (window.state?.downloads) {
    galleryFiles = Array.from(window.state.downloads.values()).filter(d => d.status === 'completed');
  }
  renderGallery();
};

/**
 * Classifies media items into 'adult', 'movies', or 'media'
 * @param {object} item
 */
function detectCategory(item) {
  if (item.category === 'adult' || item.category === 'movies') return item.category;
  const t = (item.title || item.filename || item.name || '').toLowerCase();
  const u = (item.url || '').toLowerCase();
  if (u.includes('porn') || u.includes('hamster') || u.includes('eporner') || u.includes('xvideos') ||
      u.includes('xnxx') || u.includes('redtube') || u.includes('spankbang') || u.includes('xhcdn') ||
      u.includes('phncdn') || t.includes('porn') || t.includes('hamster') || t.includes('eporner') ||
      t.includes('xvideos') || t.includes('xnxx') || t.includes('18+') || t.includes('cuckold') ||
      t.includes('jav') || t.includes('waka') || t.includes('hentai') || t.includes('creampie') ||
      t.includes('milf') || t.includes('blowjob') || t.includes('stepmom') || t.includes('step mom') ||
      t.includes('teen') || t.includes('sex') || t.includes('fetish') || t.includes('threesome')) {
    return 'adult';
  }
  if (u.includes('sinhala') || u.includes('baiscope') || u.includes('piratelk') || u.includes('sub') ||
      u.includes('movie') || t.includes('sinhala') || t.includes('baiscope') || t.includes('sub') ||
      t.includes('bluray') || t.includes('web-dl') || t.includes('season') || t.includes('s01') ||
      t.includes('e01')) {
    return 'movies';
  }
  return item.category || 'media';
}

// ============================================================================
// SECTION 3: Local Favorites Store Management
// ============================================================================

let galleryFavoritesSet = new Set(JSON.parse(localStorage.getItem('gallery_favorites') || '[]'));

function toggleGalleryFavorite(itemPath) {
  if (galleryFavoritesSet.has(itemPath)) {
    galleryFavoritesSet.delete(itemPath);
  } else {
    galleryFavoritesSet.add(itemPath);
  }
  localStorage.setItem('gallery_favorites', JSON.stringify(Array.from(galleryFavoritesSet)));
  renderGallery();
}

// ============================================================================
// SECTION 4: Master Gallery Renderer (Grid Cinema & List View)
// ============================================================================

function renderGallery() {
  if (!galleryGrid) return;
  galleryGrid.innerHTML = '';

  const q = gallerySearchInput ? gallerySearchInput.value.trim().toLowerCase() : '';
  const sectionHeading = document.getElementById('gallerySectionHeading');

  const matchesQuery = (item) => {
    if (!q) return true;
    const itemTitle = (item.title || item.filename || item.name || '').toLowerCase();
    return itemTitle.includes(q);
  };

  let filtered = [];

  if (currentGalleryFilter === 'folders') {
    // Folders Grouping Mode
    if (sectionHeading) sectionHeading.innerHTML = '<i class="fa-solid fa-folder-open" style="color: #0084FF;"></i> Video Folders';
    const folderMap = new Map();
    galleryFiles.forEach(item => {
      const p = item.path || item.uri || '';
      const parts = p.split(/[/\\]/);
      const folderName = parts.length > 2 ? parts[parts.length - 2] : 'Downloads';
      if (!folderMap.has(folderName)) folderMap.set(folderName, []);
      folderMap.get(folderName).push(item);
    });

    if (folderMap.size === 0) {
      galleryGrid.innerHTML = `
        <div class="gallery-empty-state">
          <div class="gallery-empty-icon"><i class="fa-solid fa-folder"></i></div>
          <h3>No Video Folders Found</h3>
          <p>Folders with downloaded videos will appear here.</p>
        </div>
      `;
      if (galleryFileCountBadge) galleryFileCountBadge.textContent = '0 Folders';
      return;
    }

    if (galleryFileCountBadge) galleryFileCountBadge.textContent = `${folderMap.size} Folders`;

    folderMap.forEach((vids, folderName) => {
      const card = document.createElement('div');
      card.className = 'gallery-cinema-card folder-card';
      const firstThumb = vids.find(v => v.thumbnail)?.thumbnail || '';
      card.innerHTML = `
        <div class="cinema-poster-frame">
          ${firstThumb
            ? `<img src="${firstThumb}" alt="${folderName}" loading="lazy" decoding="async" class="cinema-poster-img">`
            : `<div style="width:100%;height:100%;background:linear-gradient(135deg,#0d1b2a,#1b263b);display:flex;align-items:center;justify-content:center;"><i class="fa-solid fa-folder" style="font-size:3rem;color:#0084FF;"></i></div>`}
          <div class="cinema-poster-vignette"></div>
          <div class="badge-res-corner">
            <span class="cinema-dur-pill"><i class="fa-solid fa-film"></i> ${vids.length} Videos</span>
          </div>
        </div>
        <div class="cinema-card-body">
          <div class="cinema-video-title">${folderName}</div>
          <div class="cinema-meta-row">
            <span><i class="fa-solid fa-folder-open"></i> Local Folder</span>
            <span><i class="fa-solid fa-layer-group"></i> ${vids.length} items</span>
          </div>
        </div>
      `;
      card.addEventListener('click', () => {
        currentGalleryFilter = 'all';
        document.querySelectorAll('.gallery-filter-btn').forEach(b => b.classList.remove('active'));
        document.querySelector('.gallery-filter-btn[data-filter="all"]')?.classList.add('active');
        if (gallerySearchInput) gallerySearchInput.value = folderName;
        renderGallery();
      });
      galleryGrid.appendChild(card);
    });
    return;
  } else if (currentGalleryFilter === 'movies') {
    if (sectionHeading) sectionHeading.innerHTML = '<i class="fa-solid fa-film" style="color: #0084FF;"></i> 🎬 Movie Downloads';
    filtered = galleryFiles.filter(item => detectCategory(item) === 'movies' && matchesQuery(item));
  } else if (currentGalleryFilter === 'media') {
    if (sectionHeading) sectionHeading.innerHTML = '<i class="fa-solid fa-bolt" style="color: #00f2fe;"></i> 📱 Social Media Videos';
    filtered = galleryFiles.filter(item => detectCategory(item) === 'media' && matchesQuery(item));
  } else if (currentGalleryFilter === 'adult') {
    if (sectionHeading) sectionHeading.innerHTML = '<i class="fa-solid fa-fire" style="color: #ff4757;"></i> 🔞 18+ Vault Videos';
    filtered = galleryFiles.filter(item => detectCategory(item) === 'adult' && matchesQuery(item));
  } else if (currentGalleryFilter === 'favorites') {
    if (sectionHeading) sectionHeading.innerHTML = '<i class="fa-solid fa-star" style="color: #fbbf24;"></i> ⭐ Favorite Videos';
    filtered = galleryFiles.filter(item => {
      const p = item.path || item.uri || '';
      return galleryFavoritesSet.has(p) && matchesQuery(item);
    });
  } else {
    if (sectionHeading) sectionHeading.innerHTML = '<i class="fa-solid fa-layer-group" style="color: #0084FF;"></i> All Offline Videos';
    filtered = galleryFiles.filter(matchesQuery);
  }

  // Sort
  filtered.sort((a, b) => {
    if (currentGallerySort === 'newest') return (b.lastModified || b.createdTimestamp || 0) - (a.lastModified || a.createdTimestamp || 0);
    if (currentGallerySort === 'name') return (a.title || a.filename || '').localeCompare(b.title || b.filename || '');
    if (currentGallerySort === 'largest') return (b.sizeBytes || 0) - (a.sizeBytes || 0);
    if (currentGallerySort === 'duration') return (b.durationSeconds || 0) - (a.durationSeconds || 0);
    return 0;
  });

  if (galleryFileCountBadge) {
    galleryFileCountBadge.textContent = `${filtered.length} Video${filtered.length === 1 ? '' : 's'}`;
  }

  if (filtered.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'gallery-empty-state';
    if (window._galleryPermissionDenied) {
      empty.innerHTML = `
        <div class="gallery-empty-icon" style="color: #f59e0b;"><i class="fa-solid fa-folder-lock"></i></div>
        <h3>Storage Permission Required</h3>
        <p>Grant storage access so CloudDrive Leech can scan your downloaded videos and media library.</p>
        <button id="btnGrantGalleryStorage" class="btn btn-primary" style="margin-top: 14px; padding: 10px 20px; display: inline-flex; align-items: center; gap: 8px; border-radius: 8px; background: var(--grad-primary); color: #000; font-weight: 700;">
          <i class="fa-solid fa-shield-check"></i> Grant Storage Permission
        </button>
      `;
      empty.querySelector('#btnGrantGalleryStorage')?.addEventListener('click', async () => {
        if (window.Capacitor?.Plugins?.NativeDownload?.requestStoragePermission) {
          try {
            await window.Capacitor.Plugins.NativeDownload.requestStoragePermission();
            setTimeout(() => window.loadOfflineGallery(), 1000);
          } catch (_) {}
        }
      });
    } else {
      empty.innerHTML = `
        <div class="gallery-empty-icon"><i class="fa-solid fa-film"></i></div>
        <h3>No Videos in this section</h3>
        <p>Downloaded videos appear here automatically for offline hardware-accelerated playback.</p>
      `;
    }
    galleryGrid.appendChild(empty);
    return;
  }

  filtered.forEach(item => {
    const card = document.createElement('div');
    const p = item.path || item.uri || '';
    const isFav = galleryFavoritesSet.has(p);
    const cat = detectCategory(item);
    const catName = cat === 'adult' ? '🔞 18+ Vault' : cat === 'movies' ? '🎬 Movie' : '📱 Social Media';
    const catColor = cat === 'adult' ? '#ff4757' : cat === 'movies' ? '#0084FF' : '#00f2fe';
    const duration = item.duration || '12:45';
    const resolution = item.resolution || '1080p';
    const sizeStr = item.sizeFormatted || '62MB';
    const hasThumb = item.thumbnail && item.thumbnail.length > 50;
    const titleText = item.title || item.filename || item.name || 'Offline Video';

    if (currentGalleryLayout === 'list') {
      card.className = 'gallery-list-card';
      card.innerHTML = `
        <div class="list-thumb-frame" title="Preview Video in Popup">
          ${hasThumb
            ? `<img src="${item.thumbnail}" alt="${titleText}" loading="lazy" decoding="async" class="list-thumb-img">`
            : `<div style="width:100%;height:100%;background:#091222;display:flex;align-items:center;justify-content:center;"><i class="fa-solid fa-film" style="color:#0084FF;"></i></div>`}
          <div class="list-play-mini"><i class="fa-solid fa-play"></i></div>
        </div>

        <div class="list-info-col">
          <div class="list-video-title" title="${titleText}">${titleText}</div>
          <div class="list-meta-row">
            <span style="color:${catColor};font-weight:700;">${catName}</span>
            <span>•</span>
            <span>${sizeStr}</span>
            <span>•</span>
            <span>${duration}</span>
          </div>
        </div>

        <div class="list-actions-col">
          <button class="btn-dl-act btn-fav-gallery ${isFav ? 'active' : ''}" title="Favorite">
            <i class="fa-${isFav ? 'solid' : 'regular'} fa-star" style="${isFav ? 'color:#fbbf24;' : ''}"></i>
          </button>
          <button class="btn-dl-act btn-share-gallery" title="Share Video">
            <i class="fa-solid fa-share-nodes"></i>
          </button>
          <button class="btn-dl-act primary btn-play-gallery" title="Preview Video in Popup">
            <i class="fa-solid fa-play"></i>
          </button>
          <button class="btn-dl-act danger btn-delete-gallery" title="Delete">
            <i class="fa-solid fa-trash-can"></i>
          </button>
        </div>
      `;
    } else {
      card.className = 'gallery-cinema-card';
      card.innerHTML = `
        <div class="cinema-poster-frame" title="Tap to Open Popup Player">
          ${hasThumb
            ? `<img src="${item.thumbnail}" alt="${titleText}" loading="lazy" decoding="async" class="cinema-poster-img">`
            : `<div style="width:100%;height:100%;background:linear-gradient(135deg,#0d1b2a,#1b263b);display:flex;align-items:center;justify-content:center;"><i class="fa-solid fa-film" style="font-size:3rem;color:rgba(0,132,255,0.4);"></i></div>`}
          <div class="cinema-poster-vignette"></div>
          
          <span class="badge-category-corner" style="border-color:${catColor}; color:${catColor};">${catName}</span>

          <div class="badge-res-corner">
            <span class="cinema-dur-pill">${duration}</span>
          </div>

          <div class="cinema-play-overlay">
            <i class="fa-solid fa-play"></i>
          </div>
        </div>

        <div class="cinema-card-body">
          <div class="cinema-video-title" title="${titleText}">${titleText}</div>
          
          <div class="cinema-meta-row" style="display:flex; justify-content:space-between; align-items:center;">
            <span>${resolution} • ${sizeStr}</span>
            <div style="display:flex; gap:6px; align-items:center;">
              <button class="btn-share-gallery" title="Share Video" style="background:none;border:none;color:#00f2fe;padding:4px;cursor:pointer;font-size:0.9rem;">
                <i class="fa-solid fa-share-nodes"></i>
              </button>
              <button class="btn-card-more-menu" title="More Options" style="background:none;border:none;color:#aaa;padding:4px;cursor:pointer;">
                <i class="fa-solid fa-ellipsis-vertical"></i>
              </button>
            </div>
          </div>
        </div>
      `;
    }

    // Card click opens Popup Preview Player Modal
    card.addEventListener('click', (e) => {
      if (e.target.closest('.btn-fav-gallery') || e.target.closest('.btn-share-gallery') ||
          e.target.closest('.btn-delete-gallery') || e.target.closest('.btn-card-more-menu')) return;
      if (window.openGalleryMiniPlayer) {
        window.openGalleryMiniPlayer(item);
      }
    });

    // Share
    card.querySelector('.btn-share-gallery')?.addEventListener('click', (e) => {
      e.stopPropagation();
      window.shareGalleryItem(item);
    });

    // Favorite Toggle
    card.querySelector('.btn-fav-gallery')?.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleGalleryFavorite(p);
    });

    // More Options Menu
    card.querySelector('.btn-card-more-menu')?.addEventListener('click', (e) => {
      e.stopPropagation();
      if (window.openGalleryMiniPlayer) {
        window.openGalleryMiniPlayer(item);
      }
    });

    // Delete in List View
    card.querySelector('.btn-delete-gallery')?.addEventListener('click', async (e) => {
      e.stopPropagation();
      e.preventDefault();

      card.style.opacity = '0.5';
      card.style.pointerEvents = 'none';

      if (window.Capacitor?.Plugins?.NativeDownload?.deleteDownload) {
        try {
          await window.Capacitor.Plugins.NativeDownload.deleteDownload({
            id: item.id || '',
            path: item.path || item.uri || '',
            filename: item.filename || ''
          });
        } catch (_) { }
      }

      galleryFiles = galleryFiles.filter(f => f.id !== item.id && f.path !== item.path);
      renderGallery();
      window.showToast('🗑️ Video file permanently deleted', 'info');
    });

    galleryGrid.appendChild(card);
  });
}

// ============================================================================
// SECTION 5: Item Share & Storage Deletion Engine
// ============================================================================

window.shareGalleryItem = async function (item) {
  const filePath = item.path || item.localFilePath || item.uri || '';
  const title = item.title || item.filename || 'Video';
  if (!filePath) {
    window.showToast('No local file available to share', 'warning');
    return;
  }

  if (window.Capacitor?.Plugins?.NativeDownload?.shareDownload) {
    try {
      await window.Capacitor.Plugins.NativeDownload.shareDownload({ path: filePath, title: title });
      return;
    } catch (e) {
      console.warn('[Share] Native error:', e);
    }
  }

  if (navigator.share) {
    try {
      await navigator.share({ title: title, text: `Sharing ${title}` });
    } catch (_) { }
  } else {
    window.showToast('Sharing is only supported on mobile devices.', 'info');
  }
};

// ============================================================================
// SECTION 6: Mini Popup Cinema Player Modal Controller
// ============================================================================

const galleryMiniModal = document.getElementById('galleryMiniModal');
const btnCloseGalleryMini = document.getElementById('btnCloseGalleryMini');
const btnPopupFullscreenTop = document.getElementById('btnPopupFullscreenTop');
const galleryMiniCatBadge = document.getElementById('galleryMiniCatBadge');
const galleryMiniTitle = document.getElementById('galleryMiniTitle');
const galleryPopupStage = document.getElementById('galleryPopupStage');
const galleryMiniVideo = document.getElementById('galleryMiniVideo');
const btnPopupRewind10 = document.getElementById('btnPopupRewind10');
const btnPopupCenterPlay = document.getElementById('btnPopupCenterPlay');
const btnPopupForward10 = document.getElementById('btnPopupForward10');
const btnPopupPlayPause = document.getElementById('btnPopupPlayPause');
const popupTimeText = document.getElementById('popupTimeText');
const popupProgressTrack = document.getElementById('popupProgressTrack');
const popupProgressFill = document.getElementById('popupProgressFill');
const popupProgressHead = document.getElementById('popupProgressHead');
const btnPopupSpeed = document.getElementById('btnPopupSpeed');
const popupSpeedText = document.getElementById('popupSpeedText');
const btnPopupMute = document.getElementById('btnPopupMute');
const btnPopupPipAction = document.getElementById('btnPopupPipAction');
const btnPopupRotateScreenTop = document.getElementById('btnPopupRotateScreenTop');
const btnPopupRotateScreenBottom = document.getElementById('btnPopupRotateScreenBottom');
const btnGalleryMiniFullscreen = document.getElementById('btnGalleryMiniFullscreen');
const btnPopupLaunchFull = document.getElementById('btnPopupLaunchFull');
const galleryMiniSizeChip = document.getElementById('galleryMiniSizeChip');
const galleryMiniDurationChip = document.getElementById('galleryMiniDurationChip');
const galleryMiniResChip = document.getElementById('galleryMiniResChip');
const galleryMiniStorageChip = document.getElementById('galleryMiniStorageChip');
const btnGalleryMiniDrive = document.getElementById('btnGalleryMiniDrive');
const btnGalleryMiniDelete = document.getElementById('btnGalleryMiniDelete');

let activeMiniGalleryItem = null;
const miniSpeedLevels = [0.75, 1.0, 1.25, 1.5, 2.0];
let currentMiniSpeedIdx = 1;
let popupControlsTimer = null;

function resetPopupControlsTimer() {
  const card = document.querySelector('.gallery-popup-card');
  if (!card) return;
  card.classList.remove('controls-hidden');
  clearTimeout(popupControlsTimer);
  if (galleryMiniVideo && !galleryMiniVideo.paused) {
    popupControlsTimer = setTimeout(() => {
      card.classList.add('controls-hidden');
    }, 10000);
  }
}

window.closeGalleryMiniPlayer = function () {
  clearTimeout(popupControlsTimer);
  const card = document.querySelector('.gallery-popup-card');
  if (card) card.classList.remove('controls-hidden');

  if (document.pictureInPictureElement) {
    document.exitPictureInPicture().catch(() => { });
  }

  if (galleryMiniVideo) {
    try {
      galleryMiniVideo.pause();
      galleryMiniVideo.currentTime = 0;
      galleryMiniVideo.removeAttribute('src');
      galleryMiniVideo.src = '';
      galleryMiniVideo.load();
    } catch (_) { }
  }
  if (galleryMiniModal) {
    galleryMiniModal.classList.add('hidden');
  }
  document.body.classList.remove('modal-open');
  document.body.style.overflow = '';
  activeMiniGalleryItem = null;
};

btnCloseGalleryMini?.addEventListener('click', window.closeGalleryMiniPlayer);
galleryMiniModal?.addEventListener('click', (e) => {
  if (e.target === galleryMiniModal) window.closeGalleryMiniPlayer();
});

function toggleMiniPlay() {
  if (!galleryMiniVideo) return;
  if (galleryMiniVideo.paused) {
    const p = galleryMiniVideo.play();
    if (p !== undefined) {
      p.catch(err => {
        console.warn('Mini popup play fallback:', err);
      });
    }
  } else {
    galleryMiniVideo.pause();
  }
}

galleryPopupStage?.addEventListener('click', (e) => {
  if (e.target.closest('.popup-video-controls-overlay') || e.target.closest('.btn-popup-icon-act') || e.target.closest('.btn-skip-circ')) return;
  const card = document.querySelector('.gallery-popup-card');
  if (card?.classList.contains('controls-hidden')) {
    resetPopupControlsTimer();
  } else {
    toggleMiniPlay();
    resetPopupControlsTimer();
  }
});

galleryPopupStage?.addEventListener('dblclick', () => {
  launchFullscreenProPlayer();
});

btnPopupCenterPlay?.addEventListener('click', (e) => {
  e.stopPropagation();
  toggleMiniPlay();
  resetPopupControlsTimer();
});

btnPopupPlayPause?.addEventListener('click', (e) => {
  e.stopPropagation();
  toggleMiniPlay();
  resetPopupControlsTimer();
});

// 10s Rewind & Forward
btnPopupRewind10?.addEventListener('click', (e) => {
  e.stopPropagation();
  if (galleryMiniVideo) {
    galleryMiniVideo.currentTime = Math.max(0, (galleryMiniVideo.currentTime || 0) - 10);
  }
  resetPopupControlsTimer();
});

btnPopupForward10?.addEventListener('click', (e) => {
  e.stopPropagation();
  if (galleryMiniVideo && galleryMiniVideo.duration) {
    galleryMiniVideo.currentTime = Math.min(galleryMiniVideo.duration, (galleryMiniVideo.currentTime || 0) + 10);
  }
  resetPopupControlsTimer();
});

// ============================================================================
// SECTION 7: Playback Telemetry & Progress Scrubbing Engine
// ============================================================================

function formatSec(s) {
  if (isNaN(s) || s < 0) return '00:00';
  const m = Math.floor(s / 60);
  const sec = Math.floor(s % 60);
  return `${m < 10 ? '0' : ''}${m}:${sec < 10 ? '0' : ''}${sec}`;
}

if (galleryMiniVideo) {
  galleryMiniVideo.addEventListener('play', () => {
    btnPopupCenterPlay?.classList.add('playing');
    if (btnPopupPlayPause) btnPopupPlayPause.innerHTML = '<i class="fa-solid fa-pause"></i>';
    resetPopupControlsTimer();
  });

  galleryMiniVideo.addEventListener('pause', () => {
    btnPopupCenterPlay?.classList.remove('playing');
    if (btnPopupPlayPause) btnPopupPlayPause.innerHTML = '<i class="fa-solid fa-play"></i>';
    const card = document.querySelector('.gallery-popup-card');
    if (card) card.classList.remove('controls-hidden');
    clearTimeout(popupControlsTimer);
  });

  galleryMiniVideo.addEventListener('loadedmetadata', () => {
    const dur = galleryMiniVideo.duration || 0;
    if (dur > 0 && galleryMiniDurationChip) {
      galleryMiniDurationChip.textContent = formatSec(dur);
    }
  });

  galleryMiniVideo.addEventListener('timeupdate', () => {
    const cur = galleryMiniVideo.currentTime || 0;
    const dur = galleryMiniVideo.duration || 0;
    if (popupTimeText) {
      popupTimeText.textContent = `${formatSec(cur)} / ${formatSec(dur)}`;
    }
    if (dur > 0) {
      const pct = (cur / dur) * 100;
      if (popupProgressFill) popupProgressFill.style.width = `${pct}%`;
      if (popupProgressHead) popupProgressHead.style.left = `${pct}%`;
    }
  });
}

function seekFromEvent(e) {
  if (!galleryMiniVideo || !galleryMiniVideo.duration || !popupProgressTrack) return;
  const rect = popupProgressTrack.getBoundingClientRect();
  const clientX = e.touches ? e.touches[0].clientX : e.clientX;
  const clickX = clientX - rect.left;
  const pct = Math.max(0, Math.min(1, clickX / rect.width));
  galleryMiniVideo.currentTime = pct * galleryMiniVideo.duration;
}

popupProgressTrack?.addEventListener('click', (e) => {
  e.stopPropagation();
  seekFromEvent(e);
});

popupProgressTrack?.addEventListener('touchmove', (e) => {
  e.stopPropagation();
  seekFromEvent(e);
});

// Mute Toggle
btnPopupMute?.addEventListener('click', (e) => {
  e.stopPropagation();
  if (!galleryMiniVideo) return;
  galleryMiniVideo.muted = !galleryMiniVideo.muted;
  if (btnPopupMute) {
    btnPopupMute.innerHTML = galleryMiniVideo.muted
      ? '<i class="fa-solid fa-volume-xmark" style="color:#ff4757;"></i>'
      : '<i class="fa-solid fa-volume-high"></i>';
  }
});

// ============================================================================
// SECTION 8: Screen Rotation & Video Speed Controller
// ============================================================================

async function toggleGalleryScreenRotation() {
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
    console.warn('[Orientation] Gallery orientation lock fallback:', e);
  }

  if (galleryPopupStage) {
    const isRot = galleryPopupStage.classList.toggle('screen-rotated-landscape');
    window.showToast(isRot ? '🔄 90° Landscape Rotated' : '📱 Portrait View', 'info');
  }
}

btnPopupRotateScreenTop?.addEventListener('click', toggleGalleryScreenRotation);
btnPopupRotateScreenBottom?.addEventListener('click', toggleGalleryScreenRotation);

// Speed Cycle
btnPopupSpeed?.addEventListener('click', (e) => {
  e.stopPropagation();
  if (!galleryMiniVideo) return;
  currentMiniSpeedIdx = (currentMiniSpeedIdx + 1) % miniSpeedLevels.length;
  const spd = miniSpeedLevels[currentMiniSpeedIdx];
  galleryMiniVideo.playbackRate = spd;
  if (popupSpeedText) popupSpeedText.textContent = `${spd}x`;
  resetPopupControlsTimer();
});

// ============================================================================
// SECTION 9: Fullscreen Pro Player & PiP Dispatcher
// ============================================================================

window.openGalleryMiniPlayer = function (item) {
  if (!item) return;
  activeMiniGalleryItem = item;

  const videoPath = item.path || item.uri || item.localFilePath || '';
  let playSrc = videoPath;
  if (window.Capacitor?.convertFileSrc) {
    if (playSrc.startsWith('/') || playSrc.startsWith('file://')) {
      playSrc = window.Capacitor.convertFileSrc(playSrc);
    }
  }

  if (galleryMiniTitle) {
    const title = item.title || item.filename || item.name || 'Offline Video';
    galleryMiniTitle.textContent = title;
    galleryMiniTitle.title = title;
  }

  if (galleryMiniCatBadge) {
    const cat = (item.category || 'media').toLowerCase();
    if (cat === 'movies') {
      galleryMiniCatBadge.textContent = '🎬 Movie';
      galleryMiniCatBadge.className = 'gallery-popup-cat-badge movies';
    } else if (cat === 'adult') {
      galleryMiniCatBadge.textContent = '🔞 18+ Vault';
      galleryMiniCatBadge.className = 'gallery-popup-cat-badge adult';
    } else {
      galleryMiniCatBadge.textContent = '📱 Media';
      galleryMiniCatBadge.className = 'gallery-popup-cat-badge media';
    }
  }

  if (galleryMiniSizeChip) {
    galleryMiniSizeChip.textContent = item.sizeFormatted || (item.sizeBytes ? (item.sizeBytes / (1024 * 1024)).toFixed(1) + ' MB' : 'Offline Video');
  }

  if (galleryMiniResChip) {
    galleryMiniResChip.textContent = item.resolution || '1080p HD';
  }

  if (galleryMiniStorageChip) {
    galleryMiniStorageChip.textContent = item.path ? item.path.split('/').pop() : 'Internal Storage';
  }

  if (galleryMiniDurationChip) {
    galleryMiniDurationChip.textContent = item.durationFormatted || '--:--';
  }

  if (galleryMiniVideo) {
    galleryMiniVideo.src = playSrc;
    galleryMiniVideo.load();
    const playPromise = galleryMiniVideo.play();
    if (playPromise !== undefined) {
      playPromise.catch(err => {
        console.warn('[GalleryMini] Autoplay prevented or error:', err);
      });
    }
  }

  if (galleryMiniModal) {
    galleryMiniModal.classList.remove('hidden');
    document.body.classList.add('modal-open');
  }

  resetPopupControlsTimer();
};

async function launchFullscreenProPlayer() {
  if (!activeMiniGalleryItem) return;
  const item = activeMiniGalleryItem;
  const currentPosSec = (galleryMiniVideo && !galleryMiniVideo.paused && !isNaN(galleryMiniVideo.currentTime)) ? galleryMiniVideo.currentTime : (galleryMiniVideo?.currentTime || 0);
  const startPosMs = Math.floor(currentPosSec * 1000);

  const videoPath = item.path || item.uri || item.localFilePath || '';
  const playlist = galleryFiles.map(f => f.path || f.uri || f.localFilePath || '').filter(Boolean);
  const titles = galleryFiles.map(f => f.title || f.filename || 'Video');
  const index = galleryFiles.findIndex(f => (f.path || f.uri || f.id) === (item.path || item.uri || item.id));

  // 1. Native GalleryPlayer Plugin
  if (window.Capacitor?.Plugins?.GalleryPlayer?.playOfflineVideo && videoPath) {
    try {
      window.closeGalleryMiniPlayer();
      await window.Capacitor.Plugins.GalleryPlayer.playOfflineVideo({
        path: videoPath,
        url: videoPath,
        title: item.title || item.filename || 'Offline Video',
        startPositionMs: startPosMs,
        playlist: playlist.length > 0 ? playlist : [videoPath],
        titles: titles.length > 0 ? titles : [item.title || item.filename || 'Offline Video'],
        index: index >= 0 ? index : 0
      });
      return;
    } catch (e) {
      console.warn('[Gallery] Offline player launch error:', e);
    }
  }

  // 2. Fallback to Pro Web Cinema Player
  if (window.openPlayer && videoPath) {
    window.closeGalleryMiniPlayer();
    window.openPlayer({
      title: item.title || item.filename || 'Offline Video',
      streamUrl: videoPath,
      type: 'video'
    });
    return;
  }

  // 3. Fallback to HTML5 Video Fullscreen
  if (galleryMiniVideo) {
    if (galleryMiniVideo.requestFullscreen) {
      galleryMiniVideo.requestFullscreen().catch(() => { });
    } else if (galleryMiniVideo.webkitRequestFullscreen) {
      galleryMiniVideo.webkitRequestFullscreen();
    }
  }
}

btnGalleryMiniFullscreen?.addEventListener('click', launchFullscreenProPlayer);
btnPopupLaunchFull?.addEventListener('click', launchFullscreenProPlayer);
btnPopupFullscreenTop?.addEventListener('click', launchFullscreenProPlayer);

// Floating PiP Mode
btnPopupPipAction?.addEventListener('click', async (e) => {
  e.stopPropagation();
  if (galleryMiniVideo && document.pictureInPictureEnabled) {
    try {
      await galleryMiniVideo.requestPictureInPicture();
      return;
    } catch (_) { }
  }
  if (activeMiniGalleryItem) {
    window.showToast('🚀 Launching Native PiP Cinema Player...', 'info');
    await launchFullscreenProPlayer();
  }
});

// Save to Google Drive
btnGalleryMiniDrive?.addEventListener('click', () => {
  if (!activeMiniGalleryItem) return;
  const item = activeMiniGalleryItem;
  window.closeGalleryMiniPlayer();
  if (window.startCloudTransfer) {
    window.startCloudTransfer({
      title: item.title,
      url: item.path || item.uri || '',
      type: 'media',
      quality: 'Original'
    });
  }
});

// Delete File
btnGalleryMiniDelete?.addEventListener('click', async () => {
  if (!activeMiniGalleryItem) return;
  const item = activeMiniGalleryItem;
  window.closeGalleryMiniPlayer();

  if (window.Capacitor?.Plugins?.NativeDownload?.deleteDownload) {
    try {
      await window.Capacitor.Plugins.NativeDownload.deleteDownload({
        id: item.id || '',
        path: item.path || item.uri || '',
        filename: item.filename || ''
      });
    } catch (_) { }
  }
  if (window.state?.downloads) {
    window.state.downloads.delete(item.id);
  }
  galleryFiles = galleryFiles.filter(f => f.id !== item.id && f.path !== item.path);
  renderGallery();
  window.showToast('🗑️ Video file deleted from storage', 'info');
  if (window.loadOfflineGallery) window.loadOfflineGallery();
});

// ============================================================================
// SECTION 10: Lifecycle Hooks & Vault Startup Auto-Scan
// ============================================================================

window.addEventListener('focus', () => {
  if (galleryMiniModal && galleryMiniModal.classList.contains('hidden')) {
    document.body.classList.remove('modal-open');
    document.body.style.overflow = '';
  }
});

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => {
    applyLayoutMode(currentGalleryLayout);
    setTimeout(window.loadOfflineGallery, 800);
  });
} else {
  applyLayoutMode(currentGalleryLayout);
  setTimeout(window.loadOfflineGallery, 800);
}
