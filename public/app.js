'use strict';

// ================= Master Application Bootstrapper ================= //
document.addEventListener('DOMContentLoaded', () => {
  // 1. Check Google Drive & Server Status
  if (window.checkStatus) {
    window.checkStatus();
  }

  // 2. Check Active Tab based on License & Pro Status
  const isMovieUnlocked = typeof window.isFeatureAvailable === 'function' && window.isFeatureAvailable('TAB_MOVIES');

  // Default Landing Tab:
  // - PRO users with Movie unlocked -> 'movies'
  // - Free users -> 'downloads'
  let initialTab = isMovieUnlocked ? 'movies' : 'downloads';

  const urlHash = (window.location.hash || '').replace('#', '').trim();
  const validTabs = ['movies', 'downloads', 'adult', 'gallery', 'browser', 'drive', 'tasks', 'media'];

  if (urlHash && validTabs.includes(urlHash)) {
    initialTab = urlHash;
  }

  // If initial tab is locked, strictly fallback to downloads
  if (typeof window.isFeatureAvailable === 'function') {
    const featId = window.getFeatureIdForTab ? window.getFeatureIdForTab(initialTab) : null;
    if (featId && !window.isFeatureAvailable(featId)) {
      initialTab = 'downloads';
    }
  }

  // 3. OAuth Callback query check
  if (window.location.search.includes('gdrive_linked=true')) {
    window.showToast('🎉 Google Account Linked to Google Drive Successfully!', 'success');
    window.history.replaceState({}, document.title, window.location.pathname);
    initialTab = 'drive';
  }

  // 4. Switch to Initial Restored Tab & Trigger Load
  if (window.switchTab) {
    window.switchTab(initialTab, false);
  }
});

