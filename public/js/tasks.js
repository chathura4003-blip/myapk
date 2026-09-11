'use strict';

/**
 * ============================================================================
 * CLOUD DRIVE LEECH - CLOUD-TO-DRIVE TRANSFER MANAGER (tasks.js)
 * ============================================================================
 * Architecture Overview:
 *   SECTION 1:  DOM Elements & State Management
 *   SECTION 2:  Cloud Transfer Dispatcher (Native & Remote)
 *   SECTION 3:  Transfer Telemetry & Size/Speed Formatters
 *   SECTION 4:  Zero-Flicker Tasks Card Renderer
 *   SECTION 5:  Task Lifecycle Event Handlers (Cancel, Delete, Clear)
 * ============================================================================
 */

// ============================================================================
// SECTION 1: DOM Elements & State Management
// ============================================================================

const tasksList = document.getElementById('tasksList');
const emptyTasks = document.getElementById('emptyTasks');
const btnClearTasks = document.getElementById('btnClearTasks');
const navTaskBadge = document.getElementById('navTaskBadge');

// ============================================================================
// SECTION 2: Cloud Transfer Dispatcher (Native & Remote)
// ============================================================================

/**
 * Initiates a 0 MB data usage cloud transfer directly into Google Drive
 * Supports both Native direct stream and Remote Cloud Runner (Colab / Cloudflare)
 * @param {object} param0
 */
window.startCloudTransfer = async function ({ title, url, type, quality }) {
  // 0. License Verification
  if (typeof window.isFeatureAvailable === 'function' && !window.isFeatureAvailable('FEATURE_CLOUD_UPLOAD')) {
    if (window.showInApp404) {
      window.showInApp404('Cloud Leech Upload', 'HTTP 404: Remote Google Drive upload pipeline is disabled or unauthorized under the active license. Please activate a PRO License Key.');
    }
    return;
  }

  const isTorrent = !!(url && (url.startsWith('magnet:') || url.includes('.torrent')));
  const workerUrl = (localStorage.getItem('cdl_cloud_worker_url') || window.CLOUD_WORKER_URL || '').replace(/\/+$/, '');

  // 1. Ensure Google Drive is linked
  if (!window.state?.driveConnected) {
    window.showToast('Please link your Google Drive Account first!', 'warning');
    if (window.openSettingsModal) window.openSettingsModal();
    return;
  }

  // 2. If Remote Cloud Runner is configured: route to Cloud Runner for 0 MB phone data (torrents & direct media)
  if (workerUrl) {
    const isTorrentType = isTorrent || (type === 'torrent');
    window.showToast(`🚀 Queued on Cloud Runner: "${title}"`, 'info');
    window.switchTab('downloads');
    if (window.switchToDriveTransfersSubTab) window.switchToDriveTransfersSubTab();

    try {
      const res = await fetch(`${workerUrl}/api/transfer/start`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, url, type: type || (isTorrentType ? 'torrent' : 'media'), quality })
      });
      const data = await res.json();
      if (data.success && data.task) {
        if (window.state?.tasks) {
          window.state.tasks.set(data.task.id, data.task);
        }
        window.renderTasks();
        startTasksPolling();
      }
      return;
    } catch (err) {
      window.showToast('Transfer dispatch failed: ' + err.message, 'error');
      return;
    }
  }

  // 3. If Torrent / Magnet without Cloud Runner:
  if (isTorrent) {
    window.showToast('⚡ Torrent-to-Drive cloud leeching requires an optional Cloud Runner in Settings, or download torrent directly to device.', 'info');
    if (window.openSettingsModal) window.openSettingsModal();
    return;
  }

  // 4. For Direct Media Links: Execute Native On-Device Google Drive Transfer
  if (typeof window.executeNativeDriveTransfer === 'function') {
    return window.executeNativeDriveTransfer({ title, url, type, quality });
  }

  // 5. Offline fallback to local device download
  if (window.triggerDirectDownload) {
    window.showToast('Google Drive transfer is unavailable; downloading to device instead.', 'info');
    await window.triggerDirectDownload(url, title, type === 'adult' ? 'adult' : 'movies');
    return;
  }
};

// ============================================================================
// SECTION 3: Transfer Telemetry & Size/Speed Formatters
// ============================================================================

function normalizeTaskStatus(rawStatus) {
  const s = (rawStatus || '').toLowerCase();
  if (s === 'transferring' || s === 'downloading' || s === 'uploading' || s === 'resolving' || s === 'running') return 'running';
  if (s === 'completed' || s === 'done' || s === 'success') return 'completed';
  if (s === 'failed' || s === 'error') return 'failed';
  if (s === 'cancelled' || s === 'canceled' || s === 'aborted') return 'cancelled';
  return 'queued';
}
window.normalizeTaskStatus = normalizeTaskStatus;

function formatTransferSize(uploadedMB, totalMB) {
  const up = parseFloat(uploadedMB) || 0;
  const tot = parseFloat(totalMB) || 0;

  if (tot > 0) {
    if (tot >= 1024) {
      return `${(up / 1024).toFixed(2)} GB / ${(tot / 1024).toFixed(2)} GB`;
    }
    return `${up.toFixed(1)} MB / ${tot.toFixed(1)} MB`;
  }

  if (up > 0) {
    if (up >= 1024) return `${(up / 1024).toFixed(2)} GB`;
    return `${up.toFixed(1)} MB`;
  }

  return 'Pending...';
}
window.formatTransferSize = formatTransferSize;

function formatTransferSpeed(speedMBps, status) {
  if (status && status !== 'running') return '-';
  const s = parseFloat(speedMBps) || 0;
  if (s <= 0) return 'Connecting...';
  return `${s.toFixed(1)} MB/s`;
}
window.formatTransferSpeed = formatTransferSpeed;

// ============================================================================
// SECTION 4: Zero-Flicker Tasks Card Renderer
// ============================================================================

window.renderTasks = function () {
  if (!tasksList) return;
  const tasksArr = window.state?.tasks ? Array.from(window.state.tasks.values()).reverse() : [];

  const activeCount = tasksArr.filter(t => {
    const st = normalizeTaskStatus(t.status);
    return st === 'running' || st === 'queued';
  }).length;
  const subBadgeTransfers = document.getElementById('subBadgeTransfers');
  if (subBadgeTransfers) {
    subBadgeTransfers.textContent = activeCount;
  }
  if (navTaskBadge) {
    if (activeCount > 0) {
      navTaskBadge.textContent = activeCount;
      navTaskBadge.classList.remove('hidden');
    } else {
      navTaskBadge.classList.add('hidden');
    }
  }

  if (tasksArr.length === 0) {
    tasksList.innerHTML = `
      <div class="empty-state" id="emptyTasks">
        <i class="fa-solid fa-cloud-arrow-up"></i>
        <h3>No Active Cloud Transfers</h3>
        <p>Search movies or paste media links to initiate instant transfers.</p>
      </div>
    `;
    return;
  }

  tasksList.innerHTML = '';
  tasksArr.forEach(task => {
    const canonicalStatus = normalizeTaskStatus(task.status);
    const card = document.createElement('div');
    card.className = `task-card ${canonicalStatus}`;

    let statusText = canonicalStatus.toUpperCase();
    if (canonicalStatus === 'running') {
      statusText = `RUNNING (${task.percent || 0}%)`;
    }

    const isRemote = Boolean(localStorage.getItem('cdl_cloud_worker_url') || window.CLOUD_WORKER_URL);
    const modeText = isRemote ? '0 MB Phone Data (Remote Cloud Runner)' : 'Direct Mobile Stream Pipe';

    card.innerHTML = `
      <div class="task-top">
        <div class="task-title">${task.title || 'Cloud Transfer'}</div>
        <span class="task-badge ${canonicalStatus}">${statusText}</span>
      </div>

      <div class="progress-track">
        <div class="progress-fill" style="width: ${task.percent || 0}%;"></div>
      </div>

      <div class="task-metrics">
        <span><i class="fa-solid fa-bolt-lightning" style="color: #00f2fe;"></i> Speed: <strong>${formatTransferSpeed(task.speedMBps, canonicalStatus)}</strong></span>
        <span><i class="fa-solid fa-hard-drive" style="color: #38ef7d;"></i> Size: <strong>${formatTransferSize(task.uploadedMB, task.totalMB)}</strong></span>
        <span><i class="fa-solid fa-clock" style="color: #fbbf24;"></i> ETA: <strong>${canonicalStatus === 'completed' ? 'Done' : (task.etaSec ? `${task.etaSec}s` : 'Estimating...')}</strong></span>
      </div>

      <div class="task-mode-tag" style="font-size: 0.72rem; color: #2ed573; margin-top: 4px; display: flex; align-items: center; gap: 6px;">
        <i class="fa-solid fa-shield-halved"></i> ${modeText}
      </div>

      ${task.driveResult ? `
        <div class="task-result-box" style="margin-top: 10px; padding: 10px; background: rgba(0, 242, 254, 0.08); border-radius: var(--radius-sm); border: 1px solid rgba(0, 242, 254, 0.2);">
          <div style="font-size: 0.8rem; font-weight: 700; color: var(--accent-cyan); margin-bottom: 6px;">
            <i class="fa-solid fa-circle-check"></i> Stored in Google Drive
          </div>
          <div style="display: flex; gap: 8px; flex-wrap: wrap;">
            <button class="btn-action btn-play-task" style="padding: 6px 12px; font-size: 0.78rem; background: var(--grad-primary); color: #000;">
              <i class="fa-solid fa-play"></i> Watch Online
            </button>
            <a href="${task.driveResult.directDownloadUrl || task.driveResult.webContentLink}" class="btn-action" style="padding: 6px 12px; font-size: 0.78rem;" target="_blank">
              <i class="fa-solid fa-download"></i> Direct Download
            </a>
            <a href="${task.driveResult.webViewLink}" class="btn-secondary" style="padding: 6px 12px; font-size: 0.78rem;" target="_blank">
              <i class="fa-brands fa-google-drive"></i> Open in Drive
            </a>
          </div>
        </div>
      ` : ''}

      ${task.status === 'failed' ? `
        <div style="color: var(--accent-red); font-size: 0.8rem; margin-top: 8px;">
          <i class="fa-solid fa-triangle-exclamation"></i> ${task.error || 'Transfer failed.'}
        </div>
      ` : ''}

      <div class="task-actions" style="margin-top: 10px; display: flex; gap: 8px;">
        ${task.status === 'transferring' || task.status === 'resolving' || task.status === 'queued' ? `
          <button class="btn-task-action secondary btn-cancel-task" style="color: var(--accent-red);">
            <i class="fa-solid fa-xmark"></i> Cancel Transfer
          </button>
        ` : `
          <button class="btn-task-action secondary btn-delete-task" style="color: var(--text-muted);">
            <i class="fa-solid fa-trash-can"></i> Remove
          </button>
        `}
      </div>
    `;

    card.querySelector('.btn-play-task')?.addEventListener('click', () => {
      if (window.openPlayer && task.driveResult) {
        window.openPlayer({
          title: task.title,
          driveId: task.driveResult.fileId,
          isDrive: true
        });
      }
    });

    card.querySelector('.btn-cancel-task')?.addEventListener('click', async (e) => {
      e.stopPropagation();
      task.status = 'cancelled';
      window.renderTasks();

      if (task.source === 'native' || String(task.id).startsWith('native-transfer-')) {
        try {
          await window.Capacitor?.Plugins?.NativeGdrive?.cancelCloudTransferNative?.();
        } catch (_) { }
      }

      const workerUrl = (localStorage.getItem('cdl_cloud_worker_url') || window.CLOUD_WORKER_URL || '').replace(/\/+$/, '');
      if (workerUrl) {
        try {
          await fetch(`${workerUrl}/api/transfer/cancel`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ taskId: task.id })
          });
        } catch (_) { }
      }
      window.showToast('Transfer cancelled.', 'info');
    });

    card.querySelector('.btn-delete-task')?.addEventListener('click', async (e) => {
      e.stopPropagation();
      if (window.state?.tasks) {
        window.state.tasks.delete(task.id);
      }
      window.renderTasks();
      const workerUrl = (localStorage.getItem('cdl_cloud_worker_url') || window.CLOUD_WORKER_URL || '').replace(/\/+$/, '');
      if (workerUrl) {
        try {
          await fetch(`${workerUrl}/api/transfer/delete`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ taskId: task.id })
          });
        } catch (_) { }
      }
      window.showToast('Task removed.', 'info');
    });

    tasksList.appendChild(card);
  });
};

// ============================================================================
// SECTION 5: Task Lifecycle Event Handlers (Cancel, Delete, Clear)
// ============================================================================

if (btnClearTasks) {
  btnClearTasks.addEventListener('click', async () => {
    // Only remove completed, failed, or cancelled tasks; leave active transfers running
    if (window.state?.tasks) {
      for (const [id, task] of window.state.tasks) {
        if (task.status === 'completed' || task.status === 'failed' || task.status === 'cancelled') {
          window.state.tasks.delete(id);
        }
      }
    }
    window.renderTasks();
    const workerUrl = (localStorage.getItem('cdl_cloud_worker_url') || window.CLOUD_WORKER_URL || '').replace(/\/+$/, '');
    if (workerUrl) {
      try {
        await fetch(`${workerUrl}/api/transfer/clear`, { method: 'POST' });
      } catch (_) { }
    }
    window.showToast('Cleared completed tasks', 'info');
  });
}

// ============================================================================
// SECTION 6: Active Cloud Telemetry Polling (Cloud Runner)
// ============================================================================

let tasksPollingInterval = null;

async function pollActiveCloudTasks() {
  const workerUrl = (localStorage.getItem('cdl_cloud_worker_url') || window.CLOUD_WORKER_URL || '').replace(/\/+$/, '');
  if (!workerUrl) return; // Do not poll dead localhost endpoints when no remote runner is configured

  try {
    const res = await fetch(`${workerUrl}/api/transfer/tasks`, { signal: AbortSignal.timeout(4000) });
    const data = await res.json();
    if (data.success && Array.isArray(data.tasks)) {
      let hasActive = false;
      let stateChanged = false;

      data.tasks.forEach(remoteTask => {
        const taskId = remoteTask.id || remoteTask.taskId;
        if (!taskId) return;

        const localTask = window.state?.tasks?.get(taskId);
        if (!localTask) {
          if (window.state?.tasks) {
            window.state.tasks.set(taskId, remoteTask);
            stateChanged = true;
          }
        } else {
          if (localTask.percent !== remoteTask.percent ||
              localTask.status !== remoteTask.status ||
              localTask.speedMBps !== remoteTask.speedMBps ||
              localTask.uploadedMB !== remoteTask.uploadedMB) {
            Object.assign(localTask, remoteTask);
            stateChanged = true;
          }
        }

        if (remoteTask.status === 'transferring' || remoteTask.status === 'queued' || remoteTask.status === 'resolving') {
          hasActive = true;
        }

        if (remoteTask.status === 'completed' && !remoteTask._notified) {
          remoteTask._notified = true;
          window.showToast(`🎉 Stored in Google Drive: "${remoteTask.title || 'Media'}"`, 'success');
          if (window.loadDriveFiles) window.loadDriveFiles();
          if (window.loadDriveAccountProfile) window.loadDriveAccountProfile();
        }
      });

      if (stateChanged) {
        window.renderTasks();
      }

      if (!hasActive && window.state?.currentTab !== 'downloads') {
        stopTasksPolling();
      }
    }
  } catch (_) { }
}

function startTasksPolling() {
  if (document.hidden || tasksPollingInterval) return;
  pollActiveCloudTasks();
  tasksPollingInterval = setInterval(pollActiveCloudTasks, 3000);
}
window.startTasksPolling = startTasksPolling;

function stopTasksPolling() {
  if (tasksPollingInterval) {
    clearInterval(tasksPollingInterval);
    tasksPollingInterval = null;
  }
}
window.stopTasksPolling = stopTasksPolling;

// Pause polling when app is in background to save battery and reduce CPU/network churn
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    stopTasksPolling();
  } else if (window.state?.currentTab === 'downloads') {
    startTasksPolling();
  }
});

// Start polling when switching to Downloads tab or on init
window.addEventListener('cloud:active-tab-changed', (e) => {
  if (e?.detail?.tab === 'downloads') {
    startTasksPolling();
  } else {
    stopTasksPolling();
  }
});
window.addEventListener('tabChanged', (e) => {
  if (e?.detail?.tab === 'downloads') {
    startTasksPolling();
  } else {
    stopTasksPolling();
  }
});

// Initial kick-off if tasks are already pending
setTimeout(() => {
  if (!document.hidden && window.state?.tasks && Array.from(window.state.tasks.values()).some(t => t.status === 'transferring' || t.status === 'queued')) {
    startTasksPolling();
  }
}, 1500);
