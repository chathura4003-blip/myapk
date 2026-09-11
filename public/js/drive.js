'use strict';

/**
 * ============================================================================
 * CLOUD DRIVE LEECH - GOOGLE DRIVE EXPLORER & CLOUD PIPELINE (drive.js)
 * ============================================================================
 * Architecture Overview:
 *   SECTION 1:  DOM Elements & State Management
 *   SECTION 2:  Utility Formatters (Bytes & User Display Name)
 *   SECTION 3:  Search & Category Filter Navigation
 *   SECTION 4:  Account Profile & Storage Quota Loader (Real Google API v3)
 *   SECTION 5:  Drive Files Explorer & Modern Card Renderer
 *   SECTION 6:  Video Streaming & File Details Modal
 *   SECTION 7:  Safe Public Share & File Rename Modals
 *   SECTION 8:  Native Device Download & File Deletion
 *   SECTION 9:  Folder Creation & Native File Upload
 *   SECTION 10: 0 MB Cloud Leech Pipeline & Progress Banner
 *   SECTION 11: Real-Time Event Listeners & Google OAuth Manager
 *   SECTION 12: Lifecycle Synchronization & Initialization
 * ============================================================================
 */

(function () {
  // ============================================================================
  // SECTION 1: DOM Elements & State Management
  // ============================================================================

  // Explorer & Dashboard
  const driveFilesGrid = document.getElementById('driveFilesGrid');
  const driveLoading = document.getElementById('driveLoading');
  const driveDisconnectedView = document.getElementById('driveDisconnectedView');
  const driveConnectedView = document.getElementById('driveConnectedView');
  const driveAvatar = document.getElementById('driveAvatar');
  const driveUserName = document.getElementById('driveUserName');
  const driveUserEmail = document.getElementById('driveUserEmail');
  const driveStorageText = document.getElementById('driveStorageText');
  const driveStorageFill = document.getElementById('driveStorageFill');
  const driveFileCountBadge = document.getElementById('driveFileCountBadge');

  const btnRefreshDrive = document.getElementById('btnRefreshDrive');
  const btnDisconnectDrive = document.getElementById('btnDisconnectDrive');
  const btnGoogleSignInDirect = document.getElementById('btnGoogleSignInDirect');
  const txtGoogleSignInLabel = document.getElementById('txtGoogleSignInLabel');
  const btnSwitchGoogleAccount = document.getElementById('btnSwitchGoogleAccount');

  // Safe Unlink Modal Elements
  const driveUnlinkModal = document.getElementById('driveUnlinkModal');
  const unlinkModalEmail = document.getElementById('unlinkModalEmail');
  const btnCancelDriveUnlink = document.getElementById('btnCancelDriveUnlink');
  const btnConfirmDriveUnlink = document.getElementById('btnConfirmDriveUnlink');

  // Action Buttons
  const btnUploadToDrive = document.getElementById('btnUploadToDrive');
  const btnQuickTransferToDrive = document.getElementById('btnQuickTransferToDrive');
  const btnCreateDriveFolder = document.getElementById('btnCreateDriveFolder');

  // Search & Filters
  const driveFileSearchInput = document.getElementById('driveFileSearchInput');
  const btnClearDriveSearch = document.getElementById('btnClearDriveSearch');

  // Progress Banner
  const driveTransferProgressBanner = document.getElementById('driveTransferProgressBanner');
  const driveTransferFileName = document.getElementById('driveTransferFileName');
  const driveTransferPercentBadge = document.getElementById('driveTransferPercentBadge');
  const driveTransferProgressBar = document.getElementById('driveTransferProgressBar');
  const driveTransferMetaText = document.getElementById('driveTransferMetaText');
  const driveTransferSpeedText = document.getElementById('driveTransferSpeedText');

  // Auth Modal
  const gdriveAuthModal = document.getElementById('gdriveAuthModal');
  const btnCloseGdriveAuthModal = document.getElementById('btnCloseGdriveAuthModal');
  const btnLaunchOfficialGoogleOAuth = document.getElementById('btnLaunchOfficialGoogleOAuth');

  // Details Modal
  const driveFileDetailsModal = document.getElementById('driveFileDetailsModal');
  const btnCloseDriveDetailsModal = document.getElementById('btnCloseDriveDetailsModal');
  const detailFileName = document.getElementById('detailFileName');
  const detailFileSubtext = document.getElementById('detailFileSubtext');
  const detailFileSize = document.getElementById('detailFileSize');
  const detailFileMime = document.getElementById('detailFileMime');
  const detailFileCreated = document.getElementById('detailFileCreated');
  const detailFileModified = document.getElementById('detailFileModified');
  const detailFileId = document.getElementById('detailFileId');
  const btnDetailStream = document.getElementById('btnDetailStream');
  const btnDetailDownload = document.getElementById('btnDetailDownload');
  const btnDetailShare = document.getElementById('btnDetailShare');
  const btnDetailRename = document.getElementById('btnDetailRename');
  const btnDetailDelete = document.getElementById('btnDetailDelete');

  // Rename Modal
  const driveRenameModal = document.getElementById('driveRenameModal');
  const driveRenameInput = document.getElementById('driveRenameInput');
  const btnCancelDriveRename = document.getElementById('btnCancelDriveRename');
  const btnSaveDriveRename = document.getElementById('btnSaveDriveRename');

  // Safe Share Modal
  const driveShareConfirmModal = document.getElementById('driveShareConfirmModal');
  const driveShareTargetName = document.getElementById('driveShareTargetName');
  const btnCancelDriveShare = document.getElementById('btnCancelDriveShare');
  const btnConfirmDriveShare = document.getElementById('btnConfirmDriveShare');

  // Create Folder Modal
  const driveCreateFolderModal = document.getElementById('driveCreateFolderModal');
  const driveNewFolderName = document.getElementById('driveNewFolderName');
  const btnCancelCreateFolder = document.getElementById('btnCancelCreateFolder');
  const btnConfirmCreateFolder = document.getElementById('btnConfirmCreateFolder');

  // 0 MB Cloud Leech Modal
  const driveTransferModal = document.getElementById('driveTransferModal');
  const inputDriveTransferUrl = document.getElementById('inputDriveTransferUrl');
  const inputDriveTransferName = document.getElementById('inputDriveTransferName');
  const btnCancelDriveTransfer = document.getElementById('btnCancelDriveTransfer');
  const btnConfirmDriveTransfer = document.getElementById('btnConfirmDriveTransfer');

  // Delete Confirmation Modal Elements
  const driveDeleteModal = document.getElementById('driveDeleteModal');
  const driveDeleteFileName = document.getElementById('driveDeleteFileName');
  const btnCancelDriveDelete = document.getElementById('btnCancelDriveDelete');
  const btnConfirmDriveDelete = document.getElementById('btnConfirmDriveDelete');
  let pendingDeleteFile = null;

  // State
  window.rawDriveFiles = [];
  window.activeDriveCategory = 'all';
  let activeTargetFile = null;
  let isNativeListenersRegistered = false;

  function openModal(el) {
    if (!el) return;
    if (typeof window.openModalWithHistory === 'function') {
      window.openModalWithHistory(el);
    } else {
      el.classList.remove('hidden');
    }
  }

  function closeModal(el) {
    if (!el) return;
    if (typeof window.closeModalWithHistory === 'function') {
      window.closeModalWithHistory(el);
    } else {
      el.classList.add('hidden');
    }
  }

  // ============================================================================
  // SECTION 2: Utility Formatters (Bytes & User Display Name)
  // ============================================================================

  function formatBytes(bytes, decimals = 2) {
    if (!+bytes) return '0 B';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
  }

  function formatNameFromEmail(email) {
    try {
      const localPart = email.substring(0, email.indexOf('@'));
      const words = localPart.split(/[._-]/);
      return words.map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' ');
    } catch (_) {
      return 'Google Account';
    }
  }

  // ============================================================================
  // SECTION 3: Search & Category Filter Navigation
  // ============================================================================

  if (driveFileSearchInput) {
    driveFileSearchInput.addEventListener('input', () => {
      const val = driveFileSearchInput.value.trim();
      if (val.length > 0) {
        btnClearDriveSearch?.classList.remove('hidden');
      } else {
        btnClearDriveSearch?.classList.add('hidden');
      }
      window.renderFilteredDriveFiles();
    });
  }

  btnClearDriveSearch?.addEventListener('click', () => {
    if (driveFileSearchInput) driveFileSearchInput.value = '';
    btnClearDriveSearch?.classList.add('hidden');
    window.renderFilteredDriveFiles();
  });

  document.querySelectorAll('.drive-filter-pill').forEach(pill => {
    pill.addEventListener('click', async () => {
      document.querySelectorAll('.drive-filter-pill').forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      window.activeDriveCategory = pill.dataset.category || 'all';
      await window.loadDriveFiles();
    });
  });

  // ============================================================================
  // SECTION 4: Account Profile & Storage Quota Loader (Real Google API v3)
  // ============================================================================

  window.loadDriveAccountProfile = async function () {
    try {
      let savedAcc = null;

      if (window.Capacitor?.Plugins?.NativeGdrive?.getActiveAccount) {
        try {
          const nativeAcc = await window.Capacitor.Plugins.NativeGdrive.getActiveAccount();
          
          // Process one-time auth feedback toasts
          if (nativeAcc?.lastAuthStatus) {
            if (nativeAcc.lastAuthStatus === 'success') {
              window.showToast('Google account linked successfully! 🎉', 'success');
            } else if (nativeAcc.lastAuthStatus === 'switched') {
              window.showToast(`Switched Google account to: ${nativeAcc.email || ''} ✅`, 'success');
            } else if (nativeAcc.lastAuthStatus === 'unlinked') {
              window.showToast('Google account unlinked safely', 'info');
            } else if (nativeAcc.lastAuthStatus === 'cancelled') {
              window.showToast('Google sign-in was cancelled', 'warning');
            } else if (nativeAcc.lastAuthStatus === 'error') {
              window.showToast('Unable to connect Google account. Please try again.', 'error');
            }
          }

          if (nativeAcc && nativeAcc.connected && nativeAcc.driveScopeGranted === false) {
            window.showToast('⚠️ Notice: Drive permission box was not checked during login. Full delete/upload may be blocked until re-linked with permission checked.', 'warning', 6000);
          }

          // Reset direct sign-in button state
          if (btnGoogleSignInDirect) {
            btnGoogleSignInDirect.classList.remove('loading');
            if (txtGoogleSignInLabel) txtGoogleSignInLabel.textContent = 'Sign in with Google';
          }

          if (nativeAcc && nativeAcc.connected && nativeAcc.email) {
            const usageBytes = nativeAcc.quotaUsage || 0;
            const limitBytes = nativeAcc.quotaLimit || (15 * 1024 * 1024 * 1024);
            const pct = limitBytes > 0 ? Math.min(100, ((usageBytes / limitBytes) * 100).toFixed(1)) : 0;

            savedAcc = {
              user: {
                displayName: nativeAcc.displayName || formatNameFromEmail(nativeAcc.email),
                emailAddress: nativeAcc.email,
                photoLink: nativeAcc.photo || null
              },
              storageQuota: {
                usage: formatBytes(usageBytes),
                limit: formatBytes(limitBytes),
                usageBytes: usageBytes,
                limitBytes: limitBytes,
                usagePercent: pct
              }
            };
          }
        } catch (e) {
          console.warn('[Drive Profile] getActiveAccount error:', e);
        }
      }

      if (savedAcc && savedAcc.user && savedAcc.user.emailAddress) {
        const email = savedAcc.user.emailAddress;
        const name = savedAcc.user.displayName || formatNameFromEmail(email);

        if (driveUserName) driveUserName.textContent = name;
        if (driveUserEmail) driveUserEmail.textContent = email;

        if (driveAvatar) {
          if (savedAcc.user.photoLink) {
            driveAvatar.innerHTML = `<img src="${savedAcc.user.photoLink}" style="width: 100%; height: 100%; border-radius: 50%; object-fit: cover;" alt="Avatar">`;
          } else {
            const initial = (name.charAt(0) || email.charAt(0) || 'G').toUpperCase();
            driveAvatar.innerHTML = `<span style="font-weight: 800; font-size: 1.1rem; color: #fff;">${initial}</span>`;
            driveAvatar.style.background = 'linear-gradient(135deg, #4285F4, #34A853)';
          }
        }

        if (driveStorageText) {
          const q = savedAcc.storageQuota;
          driveStorageText.textContent = `${q.usage} of ${q.limit} (${q.usagePercent}% used)`;
        }
        if (driveStorageFill) {
          driveStorageFill.style.width = `${savedAcc.storageQuota.usagePercent}%`;
        }

        window.state.driveConnected = true;
        window.state.driveUser = savedAcc.user;

        if (driveDisconnectedView) driveDisconnectedView.classList.add('hidden');
        if (driveConnectedView) driveConnectedView.classList.remove('hidden');
      } else {
        window.state.driveConnected = false;
        window.state.driveUser = null;
        if (driveDisconnectedView) driveDisconnectedView.classList.remove('hidden');
        if (driveConnectedView) driveConnectedView.classList.add('hidden');
      }

      if (typeof window.checkStatus === 'function') window.checkStatus();
      if (typeof window.syncBrowserGoogleAccount === 'function') window.syncBrowserGoogleAccount();
    } catch (err) {
      console.warn('[Drive Profile] Exception loading profile:', err);
    }
  };

  // ============================================================================
  // SECTION 5: Drive Files Explorer & Modern Card Renderer
  // ============================================================================

  window.loadDriveFiles = async function () {
    if (!driveFilesGrid) return;

    const hasExisting = driveFilesGrid && driveFilesGrid.children.length > 0;
    if (!hasExisting && driveLoading) driveLoading.classList.remove('hidden');

    try {
      let realFiles = [];
      const isFolderOnly = window.activeDriveCategory === 'appFolder';

      if (window.Capacitor?.Plugins?.NativeGdrive?.fetchRealGoogleDriveData) {
        try {
          const categoryParam = isFolderOnly ? 'all' : window.activeDriveCategory;
          const searchParam = driveFileSearchInput?.value?.trim() || '';

          const res = await window.Capacitor.Plugins.NativeGdrive.fetchRealGoogleDriveData({
            folderOnly: isFolderOnly,
            category: categoryParam,
            searchQuery: searchParam
          });

          if (res && res.success && Array.isArray(res.files)) {
            realFiles = res.files;
          }

          if (res?.storageQuota && res?.user) {
            const usageBytes = res.storageQuota.usage || 0;
            const limitBytes = res.storageQuota.limit || (15 * 1024 * 1024 * 1024);
            const pct = limitBytes > 0 ? Math.min(100, ((usageBytes / limitBytes) * 100).toFixed(1)) : 0;
            if (driveStorageText) {
              driveStorageText.textContent = `${formatBytes(usageBytes)} of ${formatBytes(limitBytes)} (${pct}% used)`;
            }
            if (driveStorageFill) {
              driveStorageFill.style.width = `${pct}%`;
            }
          }
        } catch (nativeErr) {
          console.warn('[Drive Files] Native fetch error:', nativeErr);
        }
      }

      window.rawDriveFiles = realFiles;
      if (driveLoading) driveLoading.classList.add('hidden');
      window.renderFilteredDriveFiles();
    } catch (err) {
      if (driveLoading) driveLoading.classList.add('hidden');
      console.warn('[Drive Files] Error loading files:', err);
    }
  };

  window.renderFilteredDriveFiles = function () {
    if (!driveFilesGrid) return;
    driveFilesGrid.innerHTML = '';

    const searchQ = (driveFileSearchInput?.value || '').toLowerCase().trim();
    const cat = window.activeDriveCategory;

    const filtered = (window.rawDriveFiles || []).filter(file => {
      const nameMatch = !searchQ || (file.name || '').toLowerCase().includes(searchQ);
      const catMatch = (cat === 'all' || cat === 'appFolder') ? true : (file.category === cat);
      return nameMatch && catMatch;
    });

    if (driveFileCountBadge) {
      driveFileCountBadge.textContent = `${filtered.length} File${filtered.length === 1 ? '' : 's'}`;
    }

    if (filtered.length === 0) {
      const isFolder = cat === 'appFolder';
      driveFilesGrid.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1; padding: 40px 20px; text-align: center;">
          <i class="fa-solid fa-folder-open" style="font-size: 3.2rem; color: #4285F4; margin-bottom: 12px; opacity: 0.9;"></i>
          <h3 style="font-size: 1.1rem; color: #fff; margin-bottom: 6px;">
            ${isFolder ? 'Cloud Drive Leech Folder is Empty' : 'No Drive Files Found'}
          </h3>
          <p style="font-size: 0.82rem; color: #94a3b8; max-width: 340px; margin: 0 auto 16px auto; line-height: 1.4;">
            ${searchQ
              ? `No files match "${searchQ}".`
              : isFolder
                ? 'Your "Cloud Drive Leech" root folder is clean. Tap "Upload" to upload local media or "Transfer Link" for 0 MB direct streaming!'
                : 'Connect your Google Drive account to view and stream all your media.'}
          </p>
        </div>
      `;
      return;
    }

    filtered.forEach(file => {
      const card = document.createElement('div');
      card.className = 'drive-file-card-modern';

      const isVideo = file.mimeType?.includes('video') || /\.(mp4|mkv|avi|webm|mov|flv|m4v)$/i.test(file.name);
      const isImage = file.mimeType?.includes('image') || /\.(jpg|jpeg|png|webp|gif)$/i.test(file.name);
      const isArchive = /\.(zip|rar|7z|tar|gz)$/i.test(file.name);
      const sizeStr = file.size ? formatBytes(file.size) : 'Cloud File';
      const dateStr = file.createdTime ? new Date(file.createdTime).toLocaleDateString() : 'Recent';

      let iconClass = 'fa-file file';
      if (isVideo) iconClass = 'fa-film video';
      else if (isImage) iconClass = 'fa-image file';
      else if (isArchive) iconClass = 'fa-file-zipper archive';

      card.innerHTML = `
        <div class="drive-file-header-modern">
          <div class="drive-icon-badge ${isVideo ? 'video' : (isArchive ? 'archive' : 'file')}">
            ${file.thumbnailLink
              ? `<img src="${file.thumbnailLink}" style="width: 100%; height: 100%; object-fit: cover; border-radius: 8px;" alt="thumb">`
              : `<i class="fa-solid ${iconClass}"></i>`}
          </div>
          <div class="drive-file-meta-col">
            <div class="drive-file-title-modern" title="${file.name}">${file.name}</div>
            <div class="drive-file-sub-row">
              <span class="drive-size-tag">${sizeStr}</span>
              <span>•</span>
              <span>${dateStr}</span>
            </div>
          </div>
        </div>

        <div class="drive-card-actions-modern">
          ${isVideo ? `
            <button class="btn-drive-action primary btn-play-drive" title="Watch in Native Cinema (ExoPlayer)">
              <i class="fa-solid fa-play"></i> Stream
            </button>
          ` : `
            <button class="btn-drive-action btn-details-drive" title="View File Details">
              <i class="fa-solid fa-circle-info"></i> Details
            </button>
          `}
          <button class="btn-drive-micro btn-share-drive" title="Share Public Link">
            <i class="fa-solid fa-share-nodes"></i>
          </button>
          <button class="btn-drive-micro btn-dl-drive" title="Download to Device (/Download)">
            <i class="fa-solid fa-download"></i>
          </button>
          <button class="btn-drive-micro btn-rename-drive" title="Rename File">
            <i class="fa-solid fa-pen-to-square"></i>
          </button>
          <button class="btn-drive-micro danger btn-delete-drive" title="Delete from Drive">
            <i class="fa-solid fa-trash-can"></i>
          </button>
        </div>
      `;

      // 1. Play / Details
      if (isVideo) {
        card.querySelector('.btn-play-drive')?.addEventListener('click', () => {
          playDriveVideo(file);
        });
      } else {
        card.querySelector('.btn-details-drive')?.addEventListener('click', () => {
          openFileDetails(file);
        });
      }

      // 2. Safe Share Dialog
      card.querySelector('.btn-share-drive')?.addEventListener('click', () => {
        openShareModal(file);
      });

      // 3. Native Download to Device
      card.querySelector('.btn-dl-drive')?.addEventListener('click', () => {
        downloadDriveFile(file);
      });

      // 4. Rename File Dialog
      card.querySelector('.btn-rename-drive')?.addEventListener('click', () => {
        openRenameModal(file);
      });

      // 5. Delete File with Confirmation
      card.querySelector('.btn-delete-drive')?.addEventListener('click', () => {
        deleteDriveFile(file);
      });

      driveFilesGrid.appendChild(card);
    });
  };

  // ============================================================================
  // SECTION 6: Video Streaming & File Details Modal
  // ============================================================================

  async function playDriveVideo(file) {
    if (!file?.id) {
      window.showToast('Invalid file ID for streaming', 'error');
      return;
    }

    const directApiUrl = `https://www.googleapis.com/drive/v3/files/${file.id}?alt=media`;
    let streamUrl = directApiUrl;

    if (window.Capacitor?.Plugins?.NativePlayer?.getProxiedStreamUrl) {
      try {
        const pRes = await window.Capacitor.Plugins.NativePlayer.getProxiedStreamUrl({ url: directApiUrl });
        if (pRes && (pRes.url || pRes.proxiedUrl)) {
          streamUrl = pRes.url || pRes.proxiedUrl;
        }
      } catch (_) { }
    }

    if (window.Capacitor?.Plugins?.NativePlayer?.playVideo) {
      window.Capacitor.Plugins.NativePlayer.playVideo({
        streamUrl: streamUrl,
        url: streamUrl,
        title: file.name,
        category: 'movies'
      });
    } else if (typeof window.openPlayer === 'function') {
      window.openPlayer({
        title: file.name,
        streamUrl: streamUrl,
        type: 'video',
        isDrive: true,
        driveId: file.id
      });
    } else {
      window.open(streamUrl, '_blank');
    }
  }

  function openFileDetails(file) {
    activeTargetFile = file;
    if (detailFileName) detailFileName.textContent = file.name || 'File';
    if (detailFileSubtext) detailFileSubtext.textContent = file.category ? `Category: ${file.category}` : 'Google Drive';
    if (detailFileSize) detailFileSize.textContent = formatBytes(file.size || 0);
    if (detailFileMime) detailFileMime.textContent = file.mimeType || 'unknown';
    if (detailFileCreated) detailFileCreated.textContent = file.createdTime ? new Date(file.createdTime).toLocaleString() : '-';
    if (detailFileModified) detailFileModified.textContent = file.modifiedTime ? new Date(file.modifiedTime).toLocaleString() : '-';
    if (detailFileId) detailFileId.textContent = file.id || '-';

    const isVideo = file.mimeType?.includes('video') || /\.(mp4|mkv|avi|webm|mov|flv|m4v)$/i.test(file.name);
    if (btnDetailStream) {
      btnDetailStream.style.display = isVideo ? 'flex' : 'none';
    }

    openModal(driveFileDetailsModal);
  }

  btnCloseDriveDetailsModal?.addEventListener('click', () => {
    closeModal(driveFileDetailsModal);
  });

  btnDetailStream?.addEventListener('click', () => {
    if (activeTargetFile) {
      closeModal(driveFileDetailsModal);
      playDriveVideo(activeTargetFile);
    }
  });

  btnDetailDownload?.addEventListener('click', () => {
    if (activeTargetFile) {
      closeModal(driveFileDetailsModal);
      downloadDriveFile(activeTargetFile);
    }
  });

  btnDetailShare?.addEventListener('click', () => {
    if (activeTargetFile) {
      closeModal(driveFileDetailsModal);
      openShareModal(activeTargetFile);
    }
  });

  btnDetailRename?.addEventListener('click', () => {
    if (activeTargetFile) {
      closeModal(driveFileDetailsModal);
      openRenameModal(activeTargetFile);
    }
  });

  btnDetailDelete?.addEventListener('click', () => {
    if (activeTargetFile) {
      closeModal(driveFileDetailsModal);
      deleteDriveFile(activeTargetFile);
    }
  });

  // ============================================================================
  // SECTION 7: Safe Public Share & File Rename Modals
  // ============================================================================

  function openShareModal(file) {
    activeTargetFile = file;
    if (driveShareTargetName) {
      driveShareTargetName.textContent = file.name || 'Selected File';
    }
    openModal(driveShareConfirmModal);
  }

  btnCancelDriveShare?.addEventListener('click', () => {
    closeModal(driveShareConfirmModal);
  });

  btnConfirmDriveShare?.addEventListener('click', async () => {
    if (!activeTargetFile?.id) return;
    const file = activeTargetFile;
    closeModal(driveShareConfirmModal);

    window.showToast('Generating public share link 🔗...', 'info');

    let shareUrl = `https://drive.google.com/file/d/${file.id}/view?usp=sharing`;
    if (window.Capacitor?.Plugins?.NativeGdrive?.makeFilePublicAndGetShareLink) {
      try {
        const res = await window.Capacitor.Plugins.NativeGdrive.makeFilePublicAndGetShareLink({
          fileId: file.id,
          makePublic: true
        });
        if (res?.shareLink) shareUrl = res.shareLink;
      } catch (err) {
        console.warn('Share error:', err);
      }
    }

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(shareUrl);
      }
    } catch (_) { }

    if (navigator.share) {
      try {
        await navigator.share({
          title: file.name,
          text: `Watch or download "${file.name}" on Google Drive:`,
          url: shareUrl
        });
        return;
      } catch (_) { }
    }

    window.showToast('🔗 Public link generated & copied to clipboard!', 'success');
  });

  function openRenameModal(file) {
    activeTargetFile = file;
    if (driveRenameInput) {
      driveRenameInput.value = file.name || '';
      setTimeout(() => driveRenameInput.focus(), 150);
    }
    openModal(driveRenameModal);
  }

  btnCancelDriveRename?.addEventListener('click', () => {
    closeModal(driveRenameModal);
  });

  btnSaveDriveRename?.addEventListener('click', async () => {
    if (!activeTargetFile?.id) return;
    const newName = driveRenameInput?.value?.trim();
    if (!newName) {
      window.showToast('Please enter a valid file name', 'warning');
      return;
    }

    const file = activeTargetFile;
    closeModal(driveRenameModal);
    window.showToast('Renaming file in Google Drive ⏳...', 'info');

    if (window.Capacitor?.Plugins?.NativeGdrive?.renameFile) {
      try {
        const res = await window.Capacitor.Plugins.NativeGdrive.renameFile({
          fileId: file.id,
          newName: newName
        });
        if (res?.success) {
          window.showToast(`Renamed to "${newName}" ✅`, 'success');
          await window.loadDriveFiles();
          return;
        }
      } catch (err) {
        window.showToast('Failed to rename file: ' + err.message, 'error');
        return;
      }
    }
    window.showToast('Rename operation completed', 'info');
    await window.loadDriveFiles();
  });

  // ============================================================================
  // SECTION 8: Native Device Download & File Deletion
  // ============================================================================

  async function downloadDriveFile(file) {
    if (!file?.id) {
      window.showToast('File ID missing for download', 'error');
      return;
    }

    window.showToast(`Downloading "${file.name}" to device Downloads ⏳...`, 'info');

    if (window.Capacitor?.Plugins?.NativeGdrive?.downloadFile) {
      try {
        const res = await window.Capacitor.Plugins.NativeGdrive.downloadFile({
          fileId: file.id,
          fileName: file.name
        });
        if (res?.success) {
          window.showToast(`Saved to /storage/emulated/0/Download/${res.fileName} ✅`, 'success');
          return;
        }
      } catch (err) {
        console.warn('Native download failed, falling back:', err);
      }
    }

    // Direct binary stream endpoint fallback
    const directApiUrl = `https://www.googleapis.com/drive/v3/files/${file.id}?alt=media`;
    let dlUrl = directApiUrl;
    if (window.Capacitor?.Plugins?.NativePlayer?.getProxiedStreamUrl) {
      try {
        const pRes = await window.Capacitor.Plugins.NativePlayer.getProxiedStreamUrl({ url: directApiUrl });
        if (pRes && (pRes.url || pRes.proxiedUrl)) {
          dlUrl = pRes.url || pRes.proxiedUrl;
        }
      } catch (_) { }
    }
    if (typeof window.triggerDirectDownload === 'function') {
      window.triggerDirectDownload(dlUrl, file.name, 'drive');
    } else {
      window.open(dlUrl, '_blank');
    }
  }

  function deleteDriveFile(file) {
    if (!file?.id) return;
    pendingDeleteFile = file;
    if (driveDeleteFileName) {
      driveDeleteFileName.textContent = file.name || 'Selected File';
    }
    openModal(driveDeleteModal);
  }

  btnCancelDriveDelete?.addEventListener('click', () => {
    pendingDeleteFile = null;
    closeModal(driveDeleteModal);
  });

  btnConfirmDriveDelete?.addEventListener('click', async () => {
    if (!pendingDeleteFile?.id) return;
    const file = pendingDeleteFile;
    pendingDeleteFile = null;
    closeModal(driveDeleteModal);

    window.showToast(`Deleting "${file.name}" from Google Drive ⏳...`, 'info');

    if (window.Capacitor?.Plugins?.NativeGdrive?.deleteFile) {
      try {
        const res = await window.Capacitor.Plugins.NativeGdrive.deleteFile({ fileId: file.id });
        if (res?.trashed) {
          window.showToast(`"${file.name}" moved to Google Drive Trash ✅`, 'success');
        } else {
          window.showToast(`"${file.name}" deleted permanently ✅`, 'success');
        }
        await window.loadDriveAccountProfile();
        await window.loadDriveFiles();
      } catch (err) {
        window.showToast('Could not delete file: ' + (err.message || err), 'error');
      }
    } else {
      window.showToast('Native Google Drive plugin not available', 'error');
    }
  });

  // ============================================================================
  // SECTION 9: Folder Creation & Native File Upload
  // ============================================================================

  btnCreateDriveFolder?.addEventListener('click', () => {
    if (!window.state.driveConnected) {
      window.showToast('Please link your Google Account first', 'warning');
      openModal(gdriveAuthModal);
      return;
    }
    if (driveNewFolderName) driveNewFolderName.value = '';
    openModal(driveCreateFolderModal);
    setTimeout(() => driveNewFolderName?.focus(), 150);
  });

  btnCancelCreateFolder?.addEventListener('click', () => {
    closeModal(driveCreateFolderModal);
  });

  btnConfirmCreateFolder?.addEventListener('click', async () => {
    const folderName = driveNewFolderName?.value?.trim();
    if (!folderName) {
      window.showToast('Please enter a folder name', 'warning');
      return;
    }

    closeModal(driveCreateFolderModal);
    window.showToast(`Creating folder "${folderName}" ⏳...`, 'info');

    if (window.Capacitor?.Plugins?.NativeGdrive?.createFolder) {
      try {
        const res = await window.Capacitor.Plugins.NativeGdrive.createFolder({ name: folderName, folderName });
        if (res?.success) {
          window.showToast(`Folder "${folderName}" created in Drive ✅`, 'success');
          await window.loadDriveFiles();
          return;
        }
      } catch (err) {
        window.showToast('Failed to create folder: ' + err.message, 'error');
        return;
      }
    }
  });

  btnUploadToDrive?.addEventListener('click', async (e) => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
      e?.preventDefault?.();
      e?.stopPropagation?.();
      if (window.showInApp404) {
        window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Google Drive upload pipeline is disabled or unauthorized under the active license. Please activate a PRO License Key.');
      }
      return;
    }
    if (!window.state.driveConnected) {
      window.showToast('Please link your Google Account first', 'warning');
      openModal(gdriveAuthModal);
      return;
    }

    if (window.Capacitor?.Plugins?.NativeGdrive?.uploadFile) {
      try {
        window.showToast('Choose a file to upload into Google Drive...', 'info');
        showProgressBanner('Uploading file to Google Drive...');
        const res = await window.Capacitor.Plugins.NativeGdrive.uploadFile();
        if (res?.success) {
          window.showToast(`"${res.name}" uploaded to Google Drive ✅`, 'success');
          setTimeout(hideProgressBanner, 2000);
          await window.loadDriveAccountProfile();
          await window.loadDriveFiles();
        } else if (res?.cancelled) {
          hideProgressBanner();
        }
      } catch (err) {
        hideProgressBanner();
        window.showToast('Upload error: ' + err.message, 'error');
      }
    } else {
      window.showToast('Native file upload plugin not available on this platform', 'warning');
    }
  });

  // ============================================================================
  // SECTION 10: 0 MB Cloud Leech Pipeline & Progress Banner
  // ============================================================================

  btnQuickTransferToDrive?.addEventListener('click', (e) => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
      e?.preventDefault?.();
      e?.stopPropagation?.();
      if (window.showInApp404) {
        window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Google Drive upload pipeline is disabled or unauthorized under the active license. Please activate a PRO License Key.');
      }
      return;
    }
    if (!window.state.driveConnected) {
      window.showToast('Please link your Google Account first', 'warning');
      openModal(gdriveAuthModal);
      return;
    }
    if (inputDriveTransferUrl) inputDriveTransferUrl.value = '';
    if (inputDriveTransferName) inputDriveTransferName.value = '';
    openModal(driveTransferModal);
    setTimeout(() => inputDriveTransferUrl?.focus(), 150);
  });

  btnCancelDriveTransfer?.addEventListener('click', () => {
    closeModal(driveTransferModal);
  });

  btnConfirmDriveTransfer?.addEventListener('click', (e) => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
      e?.preventDefault?.();
      e?.stopPropagation?.();
      if (window.showInApp404) {
        window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Google Drive upload pipeline is disabled or unauthorized under the active license. Please activate a PRO License Key.');
      }
      return;
    }
    const rawUrl = inputDriveTransferUrl?.value?.trim();
    if (!rawUrl || !rawUrl.startsWith('http')) {
      window.showToast('Please enter a valid media URL (http/https)', 'warning');
      return;
    }

    let fileName = inputDriveTransferName?.value?.trim();
    if (!fileName) {
      try {
        const u = new URL(rawUrl);
        fileName = u.pathname.split('/').pop() || 'Cloud_Transfer_' + Date.now();
      } catch (_) {
        fileName = 'Cloud_Transfer_' + Date.now();
      }
    }

    closeModal(driveTransferModal);
    window.startCloudTransfer({
      url: rawUrl,
      title: fileName,
      type: 'movies'
    });
  });

  /**
   * Native Google Drive Cloud Transfer Handler
   * Used by Movie Tab, Downloader Tab, and Player
   */
  window.executeNativeDriveTransfer = async function (item) {
    if (!window.state.driveConnected) {
      window.showToast('Please link your Google Account first to use 0 MB Cloud Transfers', 'warning');
      openModal(gdriveAuthModal);
      return;
    }

    let fileUrl = item?.url || '';
    let title = (item?.title || 'Cloud_Media').replace(/[/\\?%*:|"<>]/g, '_');

    if (!fileUrl) {
      window.showToast('Source stream URL is empty', 'error');
      return;
    }

    const workerUrl = (localStorage.getItem('cdl_cloud_worker_url') || window.CLOUD_WORKER_URL || '').replace(/\/+$/, '');

    // 1. If Remote Cloud Runner is configured: ALWAYS route to Cloud Runner for 0 MB Phone Data
    if (workerUrl) {
      return window.startCloudTransfer({ title, url: fileUrl, type: item?.type || (fileUrl.startsWith('magnet:') || fileUrl.includes('.torrent') ? 'torrent' : 'movie'), quality: item?.quality });
    }

    // 2. If torrent/magnet link without Cloud Runner:
    if (fileUrl.startsWith('magnet:') || fileUrl.includes('.torrent')) {
      window.showToast('⚡ Torrent / Magnet transfers require the Remote Cloud Runner (0 MB Phone Data). Please set your Colab URL in Settings.', 'warning');
      if (window.openSettingsModal) window.openSettingsModal();
      return;
    }

    showProgressBanner(title);

    const nativeTaskId = 'native-transfer-' + Date.now();
    const taskObj = {
      id: nativeTaskId,
      title: title,
      status: 'transferring',
      percent: 0,
      speedMBps: 0,
      uploadedMB: 0,
      totalMB: 0,
      etaSec: null,
      source: 'native'
    };
    if (window.state?.tasks) {
      window.state.tasks.set(nativeTaskId, taskObj);
      if (window.renderTasks) window.renderTasks();
    }

    let progressSub = null;
    if (window.Capacitor?.Plugins?.NativeGdrive?.addListener) {
      try {
        window.Capacitor.Plugins.NativeGdrive.addListener('cloudTransferProgress', (data) => {
          if (data) {
            const pct = parseInt(data.progress, 10) || 0;
            taskObj.percent = pct;
            taskObj.uploadedMB = data.transferred ? (data.transferred / (1024 * 1024)).toFixed(1) : 0;
            taskObj.totalMB = data.total ? (data.total / (1024 * 1024)).toFixed(1) : 0;
            updateProgressBanner(pct, data.transferred, data.total);
            if (window.renderTasks) window.renderTasks();
          }
        }).then(sub => { progressSub = sub; }).catch(() => {});
      } catch (_) {}
    }

    if (window.Capacitor?.Plugins?.NativeGdrive?.startCloudTransferNative) {
      try {
        window.showToast(`Transferring to Google Drive: "${title}" 📱`, 'info');
        const res = await window.Capacitor.Plugins.NativeGdrive.startCloudTransferNative({
          url: fileUrl,
          title: title,
          type: item.type || 'movie'
        });

        if (res?.success) {
          hideProgressBanner();
          if (progressSub?.remove) { progressSub.remove(); progressSub = null; }
          taskObj.status = 'completed';
          taskObj.percent = 100;
          taskObj.driveResult = {
            fileId: res.id,
            name: res.name,
            webViewLink: res.webViewLink || `https://drive.google.com/file/d/${res.id}/view`
          };
          if (window.renderTasks) window.renderTasks();
          window.showToast(`🚀 Transferred to "Cloud Drive Leech": "${res.name}"`, 'success');
          await window.loadDriveAccountProfile();
          if (window.state.currentTab === 'drive') {
            await window.loadDriveFiles();
          }
          return;
        }
      } catch (err) {
        hideProgressBanner();
        if (progressSub?.remove) { progressSub.remove(); progressSub = null; }
        taskObj.status = 'failed';
        taskObj.error = err.message || 'Transfer failed';
        if (window.renderTasks) window.renderTasks();

        if (err.message && (err.message.includes('Cloud Runner') || err.message.includes('10 Gbps'))) {
          const workerUrl = (localStorage.getItem('cdl_cloud_worker_url') || window.CLOUD_WORKER_URL || '').replace(/\/+$/, '');
          if (workerUrl) {
            return window.startCloudTransfer({ title, url: fileUrl, type: item?.type || 'movie', quality: item?.quality });
          } else {
            window.showToast(err.message, 'warning');
            if (window.openSettingsModal) window.openSettingsModal();
            return;
          }
        }
        window.showToast('Cloud Transfer failed: ' + err.message, 'error');
        return;
      }
    }

    hideProgressBanner();
    if (progressSub?.remove) { progressSub.remove(); progressSub = null; }
    taskObj.status = 'failed';
    taskObj.error = 'Native Cloud Transfer plugin not available';
    if (window.renderTasks) window.renderTasks();
    window.showToast('Native Cloud Transfer plugin not available', 'warning');
  };

  // Assign fallback only if not already assigned by tasks.js
  if (!window.startCloudTransfer) {
    window.startCloudTransfer = window.executeNativeDriveTransfer;
  }

  function showProgressBanner(filename) {
    if (!driveTransferProgressBanner) return;
    if (driveTransferFileName) driveTransferFileName.textContent = filename || 'Streaming to Drive...';
    if (driveTransferPercentBadge) driveTransferPercentBadge.textContent = '0%';
    if (driveTransferProgressBar) driveTransferProgressBar.style.width = '0%';
    if (driveTransferMetaText) driveTransferMetaText.textContent = 'Connecting...';
    if (driveTransferSpeedText) driveTransferSpeedText.textContent = 'Streaming...';
    driveTransferProgressBanner.classList.remove('hidden');
  }

  function updateProgressBanner(data) {
    if (!driveTransferProgressBanner) return;
    driveTransferProgressBanner.classList.remove('hidden');

    const pct = data.progress !== undefined ? data.progress : 0;
    const transferred = data.transferred || 0;
    const total = data.total || 0;
    const filename = data.filename || '';

    if (driveTransferFileName && filename) driveTransferFileName.textContent = filename;
    if (driveTransferPercentBadge) driveTransferPercentBadge.textContent = `${pct}%`;
    if (driveTransferProgressBar) driveTransferProgressBar.style.width = `${pct}%`;

    if (driveTransferMetaText) {
      if (total > 0) {
        driveTransferMetaText.textContent = `${formatBytes(transferred)} / ${formatBytes(total)}`;
      } else {
        driveTransferMetaText.textContent = `${formatBytes(transferred)} transferred`;
      }
    }
  }

  function hideProgressBanner() {
    if (!driveTransferProgressBanner) return;
    driveTransferProgressBanner.classList.add('hidden');
  }

  // ============================================================================
  // SECTION 11: Real-Time Event Listeners & Google OAuth Manager
  // ============================================================================

  function registerNativeListeners() {
    if (isNativeListenersRegistered) return;
    if (window.Capacitor?.Plugins?.NativeGdrive?.addListener) {
      try {
        window.Capacitor.Plugins.NativeGdrive.addListener('driveUploadProgress', (data) => {
          updateProgressBanner(data);
          if (data.progress >= 100) {
            setTimeout(hideProgressBanner, 2000);
          }
        });

        window.Capacitor.Plugins.NativeGdrive.addListener('cloudTransferProgress', (data) => {
          updateProgressBanner(data);
          if (data.progress >= 100) {
            setTimeout(hideProgressBanner, 2000);
          }
        });

        isNativeListenersRegistered = true;
      } catch (e) {
        console.warn('Could not attach NativeGdrive listeners:', e);
      }
    }
  }

  // Direct 1-Click Google Sign-In with In-App Partial Sheet
  btnGoogleSignInDirect?.addEventListener('click', async (e) => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_ACCOUNT_LINK')) {
      e?.preventDefault?.();
      e?.stopPropagation?.();
      if (window.showInApp404) {
        window.showInApp404('Google Account Link', 'HTTP 404: Cloud Account Synchronization & Google OAuth pipeline is restricted under this installation. Please activate a PRO License Key.');
      }
      return;
    }
    if (btnGoogleSignInDirect.classList.contains('loading')) return;
    btnGoogleSignInDirect.classList.add('loading');
    if (txtGoogleSignInLabel) txtGoogleSignInLabel.textContent = 'Connecting...';

    if (window.Capacitor?.Plugins?.NativeGdrive?.launchGoogleOAuthBrowser) {
      try {
        await window.Capacitor.Plugins.NativeGdrive.launchGoogleOAuthBrowser({ switchAccount: false });
        setTimeout(() => {
          if (btnGoogleSignInDirect) {
            btnGoogleSignInDirect.classList.remove('loading');
            if (txtGoogleSignInLabel) txtGoogleSignInLabel.textContent = 'Sign in with Google';
          }
        }, 8000);
      } catch (err) {
        if (btnGoogleSignInDirect) {
          btnGoogleSignInDirect.classList.remove('loading');
          if (txtGoogleSignInLabel) txtGoogleSignInLabel.textContent = 'Sign in with Google';
        }
        window.showToast('Could not launch Google Sign-In: ' + err.message, 'error');
      }
    } else {
      if (btnGoogleSignInDirect) {
        btnGoogleSignInDirect.classList.remove('loading');
        if (txtGoogleSignInLabel) txtGoogleSignInLabel.textContent = 'Sign in with Google';
      }
      openModal(gdriveAuthModal);
    }
  });

  // Switch Google Account with Google Multi-Account Selection Prompt
  btnSwitchGoogleAccount?.addEventListener('click', async (e) => {
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
        window.showToast('Opening Google Account Chooser...', 'info');
        await window.Capacitor.Plugins.NativeGdrive.launchGoogleOAuthBrowser({ switchAccount: true });
      } catch (err) {
        window.showToast('Could not switch account: ' + err.message, 'error');
      }
    } else {
      openModal(gdriveAuthModal);
    }
  });

  // Launch from secondary settings modal if opened
  btnLaunchOfficialGoogleOAuth?.addEventListener('click', async (e) => {
    if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_ACCOUNT_LINK')) {
      e?.preventDefault?.();
      e?.stopPropagation?.();
      if (window.showInApp404) {
        window.showInApp404('Google Account Link', 'HTTP 404: Cloud Account Synchronization & Google OAuth pipeline is restricted under this installation. Please activate a PRO License Key.');
      }
      return;
    }
    window.closeGdriveAuthModal();
    if (window.Capacitor?.Plugins?.NativeGdrive?.launchGoogleOAuthBrowser) {
      try {
        await window.Capacitor.Plugins.NativeGdrive.launchGoogleOAuthBrowser({ switchAccount: false });
      } catch (err) {
        window.showToast('Could not launch Google Sign-In: ' + err.message, 'error');
      }
    }
  });

  window.openGdriveAuthModal = function () {
    openModal(gdriveAuthModal);
  };

  window.closeGdriveAuthModal = function () {
    closeModal(gdriveAuthModal);
  };

  btnCloseGdriveAuthModal?.addEventListener('click', window.closeGdriveAuthModal);

  // Sync / Refresh Drive Files
  btnRefreshDrive?.addEventListener('click', async () => {
    const icon = btnRefreshDrive.querySelector('i');
    if (icon) icon.classList.add('fa-spin');
    try {
      await window.loadDriveAccountProfile();
      await window.loadDriveFiles();
      window.showToast('Google Drive Synced ✅', 'success');
    } catch (err) {
      window.showToast('Drive sync failed: ' + (err.message || err), 'error');
    } finally {
      if (icon) icon.classList.remove('fa-spin');
    }
  });

  // Safe Unlink Trigger
  btnDisconnectDrive?.addEventListener('click', () => {
    if (driveUnlinkModal) {
      if (unlinkModalEmail) {
        unlinkModalEmail.textContent = window.state.driveUser?.emailAddress || 'Your Google Account';
      }
      openModal(driveUnlinkModal);
    } else if (confirm('Are you sure you want to unlink this Google account?')) {
      performUnlink();
    }
  });

  btnCancelDriveUnlink?.addEventListener('click', () => {
    closeModal(driveUnlinkModal);
  });

  btnConfirmDriveUnlink?.addEventListener('click', async () => {
    closeModal(driveUnlinkModal);
    await performUnlink();
  });

  async function performUnlink() {
    if (window.Capacitor?.Plugins?.NativeGdrive?.unlinkAccount) {
      try {
        await window.Capacitor.Plugins.NativeGdrive.unlinkAccount();
      } catch (_) { }
    } else if (window.Capacitor?.Plugins?.NativeGdrive?.disconnectAccount) {
      try {
        await window.Capacitor.Plugins.NativeGdrive.disconnectAccount();
      } catch (_) { }
    }
    window.rawDriveFiles = [];
    window.state.driveConnected = false;
    window.state.driveUser = null;

    window.showToast('Google account unlinked safely', 'info');
    await window.loadDriveAccountProfile();
    await window.loadDriveFiles();
  }

  // ============================================================================
  // SECTION 12: Lifecycle Synchronization & Initialization
  // ============================================================================

  document.addEventListener('visibilitychange', async () => {
    if (!document.hidden && window.state?.currentTab === 'drive') {
      await window.loadDriveAccountProfile();
      await window.loadDriveFiles();
    }
  });

  window.addEventListener('focus', async () => {
    if (window.state?.currentTab === 'drive') {
      await window.loadDriveAccountProfile();
      await window.loadDriveFiles();
    }
  });

  window.addEventListener('DOMContentLoaded', () => {
    registerNativeListeners();
    window.loadDriveAccountProfile();
    window.loadDriveFiles();
  });

  registerNativeListeners();
})();
