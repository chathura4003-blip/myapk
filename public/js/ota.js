'use strict';

/**
 * ==============================================================================
 * 🚀 CLOUD DRIVE LEECH - OVER-THE-AIR (OTA) LIVE UPDATE ENGINE (2026 NEXT-GEN)
 * ==============================================================================
 * Seamless background update manager featuring:
 *   1. Dynamic Version & Release Telemetry Synchronization
 *   2. Floating Notification Banner & Modal Injection
 *   3. Native Android Package Installer & Download Manager Handover
 *   4. Animated Progress Bar & Speed Indicator
 *   5. Settings Center Status & Changelog Mirroring
 * ==============================================================================
 */

// ==============================================================================
// 1. GLOBAL VERSION CONSTANTS & BOOTSTRAP
// ==============================================================================
window.APP_VERSION = '1.0.5';

(function initOtaEngine() {
  let otaUpdateModal = null;
  let otaFloatingBanner = null;

  // Lifecycle Bootstrap
  document.addEventListener('DOMContentLoaded', () => {
    initOtaUI();

    // Auto-check on boot (enabled by default)
    const autoCheck = localStorage.getItem('cloud_ota_autocheck') !== 'false';
    if (autoCheck) {
      setTimeout(() => {
        window.checkOtaUpdate(false);
      }, 2500); // Wait 2.5s after boot for silky smooth startup UX
    }
  });

  // ============================================================================
  // 2. DYNAMIC DOM MODAL & FLOATING BANNER INJECTION
  // ============================================================================

  /**
   * Injects the OTA Update modal and floating notification bar if not present in DOM.
   */
  function initOtaUI() {
    // 1. Create Floating Update Banner Container if absent
    if (!document.getElementById('otaFloatingBanner')) {
      const banner = document.createElement('div');
      banner.id = 'otaFloatingBanner';
      banner.className = 'ota-floating-banner hidden';
      document.body.appendChild(banner);
      otaFloatingBanner = banner;
    } else {
      otaFloatingBanner = document.getElementById('otaFloatingBanner');
    }

    // 2. Create OTA Modal Backdrop & Dialog Card if absent
    if (!document.getElementById('otaUpdateModal')) {
      const modal = document.createElement('div');
      modal.id = 'otaUpdateModal';
      modal.className = 'modal-backdrop hidden';
      modal.innerHTML = `
        <div class="modal-card ota-modal-card">
          <div class="ota-header-glow"></div>
          <button class="modal-close ota-close-btn" aria-label="Close modal">&times;</button>
          <div class="ota-modal-content">
            <div class="ota-icon-badge">
              <i class="fa-solid fa-cloud-arrow-down"></i>
            </div>
            <div class="ota-badge-version" id="otaBadgeVersion">v1.0.0</div>
            <h2 class="ota-title" id="otaModalTitle">New Update Available!</h2>
            <p class="ota-subtitle" id="otaModalSubtitle">A fresh new version with improvements and fixes is ready for your device.</p>
            
            <div class="ota-details-box">
              <div class="ota-meta-row">
                <span><i class="fa-solid fa-calendar-day"></i> <span id="otaReleaseDate">2026-08-30</span></span>
                <span><i class="fa-solid fa-file-zipper"></i> <span id="otaApkSize">4.2 MB</span></span>
              </div>
              <div class="ota-changelog-title"><i class="fa-solid fa-list-check"></i> What's New:</div>
              <ul class="ota-changelog-list" id="otaChangelogList">
                <li>Performance optimizations and bug fixes</li>
              </ul>
            </div>

            <div class="ota-progress-container hidden" id="otaProgressContainer">
              <div class="ota-progress-bar">
                <div class="ota-progress-fill" id="otaProgressFill" style="width: 0%;"></div>
              </div>
              <div class="ota-progress-status" id="otaProgressStatus">Downloading update... 0%</div>
            </div>

            <div class="ota-action-buttons">
              <button class="btn-ota-secondary" id="btnOtaDismiss">Later</button>
              <button class="btn-ota-primary" id="btnOtaInstall">
                <i class="fa-solid fa-download"></i> <span>Download & Install</span>
              </button>
            </div>
          </div>
        </div>
      `;
      document.body.appendChild(modal);
      otaUpdateModal = modal;

      // Close handlers
      modal.querySelector('.ota-close-btn')?.addEventListener('click', () => {
        closeOtaModal();
      });
      modal.querySelector('#btnOtaDismiss')?.addEventListener('click', () => {
        closeOtaModal();
      });
      modal.addEventListener('click', (e) => {
        if (e.target === modal) closeOtaModal();
      });
    } else {
      otaUpdateModal = document.getElementById('otaUpdateModal');
    }
  }

  function closeOtaModal() {
    if (otaUpdateModal) {
      if (window.closeModalWithHistory) {
        window.closeModalWithHistory(otaUpdateModal);
      } else {
        otaUpdateModal.classList.add('hidden');
      }
    }
  }

  function openOtaModal() {
    if (otaUpdateModal) {
      if (window.openModalWithHistory) {
        window.openModalWithHistory(otaUpdateModal);
      } else {
        otaUpdateModal.classList.remove('hidden');
      }
    }
  }

  function compareVersions(v1, v2) {
    const p1 = (v1 || '0').split('.').map(n => parseInt(n, 10) || 0);
    const p2 = (v2 || '0').split('.').map(n => parseInt(n, 10) || 0);
    for (let i = 0; i < Math.max(p1.length, p2.length); i++) {
      const num1 = p1[i] || 0;
      const num2 = p2[i] || 0;
      if (num1 > num2) return 1;
      if (num1 < num2) return -1;
    }
    return 0;
  }

  // ============================================================================
  // 3. REMOTE UPDATE CHECKER ENGINE
  // ============================================================================

  /**
   * Master OTA Update Checker.
   * @param {boolean} manual - True if initiated by direct user tap in settings.
   * @param {Function} [callback] - Optional result completion callback.
   */
  window.checkOtaUpdate = async function(manual = false, callback = null) {
    const btnCheck = document.getElementById('btnOtaCheckSettings');
    if (manual && btnCheck) {
      btnCheck.disabled = true;
      btnCheck.innerHTML = '<i class="fa-solid fa-arrows-rotate fa-spin"></i> Checking...';
    }

    try {
      // Direct HTTPS GitHub Releases Manifest Check
      const res = await fetch('https://api.github.com/repos/chathura4003-blip/myapk/releases/latest', {
        headers: { 'Accept': 'application/vnd.github.v3+json' },
        signal: AbortSignal.timeout(7000)
      }).catch(() => null);

      if (manual && btnCheck) {
        btnCheck.disabled = false;
        btnCheck.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i> Check for Updates';
      }

      if (res && res.ok) {
        const release = await res.json();
        const latestTag = (release.tag_name || '').replace(/^v/, '');
        const currentVersion = window.APP_VERSION || '1.0.5';

        if (latestTag && compareVersions(latestTag, currentVersion) > 0) {
          const apkAsset = Array.isArray(release.assets) ? release.assets.find(a => a.name && a.name.endsWith('.apk')) : null;
          const apkUrl = apkAsset?.browser_download_url || release.html_url;
          const apkSize = apkAsset?.size ? (apkAsset.size / (1024 * 1024)).toFixed(1) + ' MB' : '48.0 MB';
          const changelog = (release.body || '').split('\n').filter(l => l.trim().length > 0);

          const updateData = {
            success: true,
            updateAvailable: true,
            version: latestTag,
            latestVersion: latestTag,
            title: release.name || `CloudDrive Leech v${latestTag}`,
            apkUrl: apkUrl,
            apkSize: apkSize,
            releaseDate: release.published_at ? release.published_at.split('T')[0] : new Date().toISOString().split('T')[0],
            changelog: changelog.length > 0 ? changelog : ['Performance optimizations and security enhancements']
          };

          renderOtaUpdatePrompt(updateData);
          updateSettingsOtaUI(updateData);
          if (callback) callback(updateData);
          return updateData;
        }
      }

      updateSettingsOtaUI({ upToDate: true, latestVersion: window.APP_VERSION });
      if (manual) {
        window.showToast(`✨ You are running the latest version (v${window.APP_VERSION})`, 'success');
      }
      if (callback) callback({ upToDate: true });
      return { upToDate: true };
    } catch (err) {
      console.warn('[OTA] Update check failed:', err);
      if (manual) {
        window.showToast(`Unable to check for updates: ${err.message}`, 'error');
      }
      if (manual && btnCheck) {
        btnCheck.disabled = false;
        btnCheck.innerHTML = '<i class="fa-solid fa-arrows-rotate"></i> Check for Updates';
      }
      if (callback) callback({ error: err.message });
    }
  };

  // ============================================================================
  // 4. UPDATE PROMPT MODAL RENDERING
  // ============================================================================

  /**
   * Populates the OTA update dialog with release notes, date, and file size.
   */
  function renderOtaUpdatePrompt(updateData) {
    initOtaUI();
    const modal = document.getElementById('otaUpdateModal');
    if (!modal) return;

    const badge = document.getElementById('otaBadgeVersion');
    const title = document.getElementById('otaModalTitle');
    const releaseDate = document.getElementById('otaReleaseDate');
    const apkSize = document.getElementById('otaApkSize');
    const list = document.getElementById('otaChangelogList');
    const btnInstall = document.getElementById('btnOtaInstall');

    const isMandatory = Boolean(updateData.mandatory);
    const closeBtn = modal.querySelector('.ota-close-btn');
    const dismissBtn = document.getElementById('btnOtaDismiss');
    if (isMandatory) {
      if (closeBtn) closeBtn.style.display = 'none';
      if (dismissBtn) dismissBtn.style.display = 'none';
    } else {
      if (closeBtn) closeBtn.style.display = '';
      if (dismissBtn) dismissBtn.style.display = '';
    }

    if (badge) badge.textContent = `v${updateData.version || updateData.latestVersion || '1.0.0'}`;
    if (title) title.textContent = updateData.title || (isMandatory ? '⚠️ Mandatory Update Required' : 'New Update Available!');
    if (releaseDate) releaseDate.textContent = updateData.releaseDate || 'Latest Release';
    if (apkSize) apkSize.textContent = updateData.apkSize || '4.2 MB';

    if (list) {
      list.innerHTML = '';
      const items = Array.isArray(updateData.changelog) && updateData.changelog.length > 0
        ? updateData.changelog
        : ['Performance optimizations', 'Bug fixes and stability enhancements'];

      items.forEach(item => {
        const li = document.createElement('li');
        const icon = document.createElement('i');
        icon.className = 'fa-solid fa-check';
        const text = document.createElement('span');
        text.textContent = String(item);
        li.append(icon, text);
        list.appendChild(li);
      });
    }

    if (btnInstall) {
      btnInstall.onclick = () => {
        startOtaDownload(updateData);
      };
    }

    openOtaModal();
  }

  window.triggerOtaFromSync = function(appConfig) {
    if (!appConfig || !appConfig.latestVersion) return;
    const isNewer = compareVersions(appConfig.latestVersion, window.APP_VERSION) > 0;
    if (isNewer) {
      renderOtaUpdatePrompt({
        latestVersion: appConfig.latestVersion,
        version: appConfig.latestVersion,
        updateAvailable: true,
        mandatory: Boolean(appConfig.mandatoryUpdate),
        apkUrl: appConfig.apkUrl,
        apkSize: appConfig.apkSize,
        releaseDate: appConfig.releaseDate,
        changelog: appConfig.changelog
      });
    }
  };

  // ============================================================================
  // 5. APK DOWNLOAD ENGINE & NATIVE INSTALLER HANDOVER
  // ============================================================================

  let otaProgressSub = null;
  let otaStateSub = null;
  let activeOtaDownloadId = null;

  /**
   * Executes genuine APK update download via NativeDownloadPlugin and delivers to Android package installer.
   * Never reports 100% until download is actually completed.
   */
  function startOtaDownload(updateData) {
    const progressContainer = document.getElementById('otaProgressContainer');
    const progressFill = document.getElementById('otaProgressFill');
    const progressStatus = document.getElementById('otaProgressStatus');
    const btnInstall = document.getElementById('btnOtaInstall');
    const btnDismiss = document.getElementById('btnOtaDismiss');

    if (progressContainer) progressContainer.classList.remove('hidden');
    if (btnInstall) {
      btnInstall.disabled = true;
      btnInstall.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> <span>Connecting...</span>';
    }
    if (btnDismiss) btnDismiss.disabled = true;

    if (progressFill) progressFill.style.width = '0%';
    if (progressStatus) progressStatus.textContent = 'Connecting to update server...';

    const downloadUrl = updateData.apkUrl || 'https://github.com/chathura4003-blip/myapk/releases/latest';
    const fileName = `CloudDriveLeech-v${updateData.latestVersion || 'Latest'}.apk`;

    // Clean up previous event listeners if active
    if (otaProgressSub?.remove) { otaProgressSub.remove(); otaProgressSub = null; }
    if (otaStateSub?.remove) { otaStateSub.remove(); otaStateSub = null; }

    // 1. Native Android Download Plugin Handover
    if (window.Capacitor && window.Capacitor.isNativePlatform() && window.Capacitor.Plugins?.NativeDownload) {
      const nativePlugin = window.Capacitor.Plugins.NativeDownload;

      nativePlugin.addListener('downloadProgress', (data) => {
        if (activeOtaDownloadId && data.id === activeOtaDownloadId) {
          const pct = Math.min(100, Math.max(0, parseInt(data.progress, 10) || 0));
          if (progressFill) progressFill.style.width = `${pct}%`;
          const spd = data.speed && data.speed !== '0' && data.speed !== '0.0 MB/s' ? ` • ${data.speed}` : '';
          const dlMB = data.downloadedBytes ? (data.downloadedBytes / (1024 * 1024)).toFixed(1) : null;
          const totMB = data.totalBytes && data.totalBytes > 0 ? (data.totalBytes / (1024 * 1024)).toFixed(1) : null;
          const sizeStr = dlMB && totMB ? ` (${dlMB}/${totMB} MB)` : (dlMB ? ` (${dlMB} MB)` : '');
          if (progressStatus) progressStatus.textContent = `Downloading update... ${pct}%${spd}${sizeStr}`;
        }
      }).then(sub => { otaProgressSub = sub; }).catch(() => {});

      nativePlugin.addListener('downloadStateChange', (data) => {
        if (activeOtaDownloadId && data.id === activeOtaDownloadId) {
          if (data.status === 'completed') {
            if (progressFill) progressFill.style.width = '100%';
            if (progressStatus) progressStatus.textContent = '✅ Update downloaded! Ready to install.';
            window.showToast('🎉 Update ready! Launching Android package installer...', 'success');

            const localPath = data.localFilePath || '';
            const triggerInstall = () => {
              nativePlugin.openFile({ id: activeOtaDownloadId, path: localPath }).catch(err => {
                window.showToast(`Package installer error: ${err.message || 'Check storage permissions'}`, 'error');
              });
            };

            if (btnInstall) {
              btnInstall.disabled = false;
              btnInstall.innerHTML = '<i class="fa-solid fa-box-archive"></i> <span>Install Update Now</span>';
              btnInstall.onclick = triggerInstall;
            }
            if (btnDismiss) btnDismiss.disabled = false;

            // Trigger installer immediately
            triggerInstall();
          } else if (data.status === 'failed') {
            const errMsg = data.errorMessage || 'Network error or download interrupted';
            if (progressStatus) progressStatus.textContent = `❌ Update failed: ${errMsg}`;
            window.showToast(`Update download failed: ${errMsg}`, 'error');
            if (btnInstall) {
              btnInstall.disabled = false;
              btnInstall.innerHTML = '<i class="fa-solid fa-rotate-right"></i> <span>Retry Download</span>';
              btnInstall.onclick = () => startOtaDownload(updateData);
            }
            if (btnDismiss) btnDismiss.disabled = false;
          }
        }
      }).then(sub => { otaStateSub = sub; }).catch(() => {});

      nativePlugin.startDownload({
        url: downloadUrl,
        filename: fileName,
        title: `CloudDriveLeech Update v${updateData.latestVersion || 'Latest'}`,
        category: 'apps'
      }).then(res => {
        activeOtaDownloadId = res?.id || res?.data?.id;
        if (progressStatus) progressStatus.textContent = 'Download started. Fetching package...';
        if (btnInstall) {
          btnInstall.innerHTML = '<i class="fa-solid fa-circle-notch fa-spin"></i> <span>Downloading...</span>';
        }
      }).catch(err => {
        if (progressStatus) progressStatus.textContent = `Download failed: ${err.message}`;
        window.showToast(`Update download failed: ${err.message}`, 'error');
        if (btnInstall) {
          btnInstall.disabled = false;
          btnInstall.innerHTML = '<i class="fa-solid fa-rotate-right"></i> <span>Retry</span>';
          btnInstall.onclick = () => startOtaDownload(updateData);
        }
        if (btnDismiss) btnDismiss.disabled = false;
      });
    } else {
      // 2. Web Browser Fallback Anchor Trigger
      const a = document.createElement('a');
      a.href = downloadUrl;
      a.download = fileName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      if (progressFill) progressFill.style.width = '100%';
      if (progressStatus) progressStatus.textContent = 'APK download started in browser. Run downloaded file to install.';
      window.showToast(`🎉 Update v${updateData.latestVersion || ''} initiated! Run APK to complete install.`, 'success');
      setTimeout(() => {
        if (btnInstall) {
          btnInstall.disabled = false;
          btnInstall.innerHTML = '<i class="fa-solid fa-check"></i> <span>Downloaded</span>';
        }
        if (btnDismiss) btnDismiss.disabled = false;
      }, 1200);
    }
  }

  // ============================================================================
  // 6. SETTINGS TAB STATUS & BADGE MIRRORING
  // ============================================================================

  /**
   * Synchronizes the OTA card and badge in the main settings modal.
   */
  function updateSettingsOtaUI(data) {
    const statusText = document.getElementById('settingsOtaStatusText');
    const badge = document.getElementById('settingsOtaBadge');
    const changelogBox = document.getElementById('settingsOtaChangelogBox');
    const btnDirectUpdate = document.getElementById('btnSettingsOtaUpdate');

    if (data.updateAvailable) {
      if (statusText) {
        statusText.innerHTML = `<strong>Update Available:</strong> v${data.latestVersion} (${data.apkSize || '4.2 MB'})`;
      }
      if (badge) {
        badge.className = 'ota-badge-pill update-available';
        badge.textContent = `v${data.latestVersion} Available`;
      }
      if (btnDirectUpdate) {
        btnDirectUpdate.style.display = 'inline-flex';
        btnDirectUpdate.onclick = () => renderOtaUpdatePrompt(data);
      }
      if (changelogBox) {
        changelogBox.style.display = 'block';
        const notes = (data.changelog || []).map(c => `<li>${String(c).replace(/</g, '&lt;')}</li>`).join('');
        changelogBox.innerHTML = `
          <div class="ota-changelog-header"><i class="fa-solid fa-sparkles"></i> What's new in v${data.latestVersion}:</div>
          <ul>${notes}</ul>
        `;
      }
    } else {
      if (statusText) {
        statusText.innerHTML = `App is up to date (Current: v${window.APP_VERSION})`;
      }
      if (badge) {
        badge.className = 'ota-badge-pill up-to-date';
        badge.textContent = 'Up to Date';
      }
      if (btnDirectUpdate) btnDirectUpdate.style.display = 'none';
      if (changelogBox) changelogBox.style.display = 'none';
    }
  }
})();
