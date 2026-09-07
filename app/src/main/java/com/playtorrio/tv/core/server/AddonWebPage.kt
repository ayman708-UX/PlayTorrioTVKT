package com.playtorrio.tv.core.server

import android.content.Context
import android.content.res.Configuration
import com.playtorrio.tv.R
import java.util.Locale

object AddonWebPage {

    fun getHtml(
        baseContext: Context,
        webConfigMode: AddonWebConfigMode = AddonWebConfigMode.ADDONS_ONLY
    ): String {
        val tag = baseContext.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .getString("locale_tag", null)
        val context = if (!tag.isNullOrEmpty()) {
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(Locale.forLanguageTag(tag))
            baseContext.createConfigurationContext(config)
        } else baseContext

        val pageTitle = context.getString(R.string.web_manage_addons_title)
        val pageSubtitle = context.getString(R.string.web_manage_addons_only_subtitle)
        val successStatusMessage = context.getString(R.string.web_status_msg_addon_updated)

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<title>PlayTorrio - $pageTitle</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>
  :root {
    --bg-primary: #070A13;
    --bg-card: #0F172A;
    --bg-card-hover: #131E35;
    --border-color: rgba(56, 189, 248, 0.16);
    --border-focus: #38BDF8;
    --accent-blue: #0284C7;
    --accent-cyan: #38BDF8;
    --accent-glow: rgba(56, 189, 248, 0.25);
    --text-primary: #FFFFFF;
    --text-secondary: rgba(255, 255, 255, 0.7);
    --text-muted: rgba(255, 255, 255, 0.4);
    --danger: #EF4444;
    --danger-bg: rgba(239, 68, 68, 0.12);
    --success: #10B981;
    --success-bg: rgba(16, 185, 129, 0.12);
  }

  * {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
    -webkit-tap-highlight-color: transparent;
  }
  *:focus, *:active { outline: none !important; }

  body {
    font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    background: var(--bg-primary);
    background-image: 
      radial-gradient(circle at 50% 0%, rgba(2, 132, 199, 0.18) 0%, transparent 60%),
      radial-gradient(circle at 10% 30%, rgba(14, 165, 233, 0.08) 0%, transparent 40%);
    background-attachment: fixed;
    color: var(--text-primary);
    min-height: 100vh;
    line-height: 1.5;
    padding-bottom: 6rem;
  }

  .container {
    max-width: 580px;
    margin: 0 auto;
    padding: 0 1.25rem;
  }

  /* Header */
  .header {
    text-align: center;
    padding: 2.75rem 0 2rem;
    position: relative;
  }
  .header-logo {
    height: 48px;
    width: auto;
    margin-bottom: 0.75rem;
    filter: drop-shadow(0 4px 16px rgba(56, 189, 248, 0.3));
  }
  .header-title {
    font-size: 1.5rem;
    font-weight: 800;
    letter-spacing: -0.02em;
    background: linear-gradient(135deg, #FFFFFF 30%, #38BDF8 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    margin-bottom: 0.25rem;
  }
  .header-subtitle {
    font-size: 0.875rem;
    font-weight: 400;
    color: var(--text-muted);
    letter-spacing: 0.01em;
  }

  /* Add Section */
  .card {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 16px;
    padding: 1.25rem;
    margin-bottom: 1.5rem;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
  }

  .card-label {
    display: block;
    font-size: 0.75rem;
    font-weight: 700;
    color: var(--accent-cyan);
    letter-spacing: 0.06em;
    text-transform: uppercase;
    margin-bottom: 0.75rem;
  }

  .input-group {
    display: flex;
    gap: 0.5rem;
  }
  .input-url {
    flex: 1;
    background: rgba(7, 10, 19, 0.8);
    border: 1px solid rgba(255, 255, 255, 0.12);
    border-radius: 10px;
    padding: 0.75rem 1rem;
    color: var(--text-primary);
    font-family: inherit;
    font-size: 0.875rem;
    transition: all 0.2s ease;
  }
  .input-url:focus {
    border-color: var(--border-focus);
    box-shadow: 0 0 0 3px var(--accent-glow);
    background: rgba(7, 10, 19, 0.95);
  }
  .input-url::placeholder {
    color: var(--text-muted);
  }

  /* Buttons */
  .btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.4rem;
    font-family: inherit;
    font-weight: 600;
    font-size: 0.875rem;
    border-radius: 10px;
    cursor: pointer;
    transition: all 0.2s ease;
    border: none;
    white-space: nowrap;
  }
  .btn-primary {
    background: linear-gradient(135deg, #0284C7 0%, #0369A1 100%);
    color: #fff;
    padding: 0.75rem 1.25rem;
    box-shadow: 0 2px 10px rgba(2, 132, 199, 0.35);
  }
  .btn-primary:hover {
    background: linear-gradient(135deg, #0369A1 0%, #0284C7 100%);
    filter: brightness(1.1);
  }
  .btn-primary:active { transform: scale(0.97); }

  .error-box {
    background: var(--danger-bg);
    border: 1px solid rgba(239, 68, 68, 0.3);
    color: #FCA5A5;
    font-size: 0.8rem;
    padding: 0.6rem 0.85rem;
    border-radius: 8px;
    margin-top: 0.75rem;
    display: none;
  }

  /* Section Title */
  .section-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 0.75rem;
    padding: 0 0.25rem;
  }
  .section-title {
    font-size: 0.8rem;
    font-weight: 700;
    color: var(--text-secondary);
    letter-spacing: 0.05em;
    text-transform: uppercase;
  }
  .addon-count-badge {
    background: rgba(56, 189, 248, 0.15);
    color: var(--accent-cyan);
    font-size: 0.75rem;
    font-weight: 700;
    padding: 2px 8px;
    border-radius: 12px;
  }

  /* Add-on List */
  .addon-list {
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: 0.6rem;
  }
  .addon-item {
    background: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 14px;
    padding: 0.9rem 1rem;
    display: flex;
    align-items: center;
    gap: 0.75rem;
    transition: all 0.2s ease;
  }
  .addon-item:hover {
    background: var(--bg-card-hover);
    border-color: rgba(56, 189, 248, 0.3);
  }

  .order-controls {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex-shrink: 0;
  }
  .btn-arrow {
    width: 26px;
    height: 22px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.06);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 6px;
    color: var(--text-secondary);
    cursor: pointer;
    transition: all 0.15s ease;
  }
  .btn-arrow:hover:not(:disabled) {
    background: rgba(56, 189, 248, 0.2);
    color: var(--accent-cyan);
    border-color: var(--accent-cyan);
  }
  .btn-arrow:disabled {
    opacity: 0.15;
    cursor: not-allowed;
  }

  .addon-details {
    flex: 1;
    min-width: 0;
  }
  .addon-name-row {
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }
  .addon-name {
    font-size: 0.925rem;
    font-weight: 700;
    color: #fff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .badge-new {
    background: var(--accent-cyan);
    color: #070A13;
    font-size: 0.625rem;
    font-weight: 800;
    padding: 1px 6px;
    border-radius: 6px;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    flex-shrink: 0;
  }
  .addon-desc {
    font-size: 0.775rem;
    color: var(--text-secondary);
    margin-top: 2px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .addon-url {
    font-size: 0.725rem;
    color: var(--text-muted);
    margin-top: 2px;
    font-family: monospace;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .addon-actions {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    flex-shrink: 0;
  }
  .btn-delete {
    width: 34px;
    height: 34px;
    border-radius: 8px;
    background: var(--danger-bg);
    border: 1px solid rgba(239, 68, 68, 0.25);
    color: #F87171;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.2s ease;
  }
  .btn-delete:hover {
    background: rgba(239, 68, 68, 0.25);
    border-color: #EF4444;
    color: #fff;
  }

  /* Empty State */
  .empty-state {
    text-align: center;
    padding: 3rem 1.5rem;
    background: var(--bg-card);
    border: 1px dashed var(--border-color);
    border-radius: 16px;
    color: var(--text-muted);
    display: none;
  }
  .empty-icon {
    width: 48px;
    height: 48px;
    margin-bottom: 0.75rem;
    color: var(--accent-cyan);
    opacity: 0.6;
  }
  .empty-title {
    font-size: 0.95rem;
    font-weight: 700;
    color: var(--text-primary);
    margin-bottom: 0.25rem;
  }
  .empty-subtitle {
    font-size: 0.8rem;
    color: var(--text-muted);
  }

  /* Bottom Save Bar */
  .save-bar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    background: rgba(7, 10, 19, 0.92);
    border-top: 1px solid var(--border-color);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    padding: 0.9rem 1.25rem;
    z-index: 100;
  }
  .save-bar-content {
    max-width: 580px;
    margin: 0 auto;
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }
  .btn-save-main {
    flex: 1;
    padding: 0.85rem;
    font-size: 0.95rem;
    font-weight: 700;
    border-radius: 12px;
    background: linear-gradient(135deg, #0284C7 0%, #0369A1 100%);
    color: #fff;
    box-shadow: 0 4px 16px rgba(2, 132, 199, 0.4);
  }
  .btn-save-main:hover:not(:disabled) {
    filter: brightness(1.15);
    box-shadow: 0 6px 20px rgba(2, 132, 199, 0.6);
  }
  .btn-save-main:disabled {
    opacity: 0.35;
    cursor: not-allowed;
    box-shadow: none;
  }

  /* Status Modal Overlay */
  .status-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.85);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
    z-index: 500;
    display: none;
    align-items: center;
    justify-content: center;
    padding: 1.5rem;
  }
  .status-overlay.visible { display: flex; }
  .status-modal {
    background: #0F172A;
    border: 1px solid var(--border-color);
    border-radius: 20px;
    padding: 2rem 1.75rem;
    width: 100%;
    max-width: 420px;
    text-align: center;
    box-shadow: 0 16px 40px rgba(0, 0, 0, 0.6);
  }
  .spinner {
    width: 44px;
    height: 44px;
    border: 3px solid rgba(56, 189, 248, 0.2);
    border-top-color: var(--accent-cyan);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    margin: 0 auto 1.25rem;
  }
  @keyframes spin { to { transform: rotate(360deg); } }

  .status-icon-svg {
    width: 48px;
    height: 48px;
    margin: 0 auto 1.25rem;
  }
  .status-title {
    font-size: 1.15rem;
    font-weight: 700;
    margin-bottom: 0.5rem;
  }
  .status-desc {
    font-size: 0.85rem;
    color: var(--text-secondary);
    line-height: 1.4;
  }

  /* Disconnect banner */
  .disconnect-banner {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    background: var(--danger);
    color: #fff;
    font-size: 0.8rem;
    font-weight: 600;
    padding: 0.5rem 1rem;
    text-align: center;
    z-index: 1000;
    display: none;
  }
  .disconnect-banner.visible { display: block; }
</style>
</head>
<body>

<div class="disconnect-banner" id="disconnectBanner">${context.getString(R.string.web_connection_lost)}</div>

<div class="container">
  <!-- Header -->
  <div class="header">
    <img src="/logo.png" alt="PlayTorrio" class="header-logo">
    <h1 class="header-title">${context.getString(R.string.app_name)}</h1>
    <p class="header-subtitle">$pageSubtitle</p>
  </div>

  <!-- Add Addon Card -->
  <div class="card">
    <label class="card-label">${context.getString(R.string.web_add_addon_url)}</label>
    <div class="input-group">
      <input type="url" id="addonUrl" class="input-url" placeholder="${context.getString(R.string.web_placeholder_url)}" autocomplete="off" autocapitalize="off" spellcheck="false">
      <button class="btn btn-primary" id="addBtn" onclick="addAddon()">${context.getString(R.string.web_btn_add)}</button>
    </div>
    <div class="error-box" id="addError"></div>
  </div>

  <!-- Installed Addons Section -->
  <div class="section-header">
    <span class="section-title">${context.getString(R.string.web_installed_addons)}</span>
    <span class="addon-count-badge" id="addonCount">0</span>
  </div>

  <ul class="addon-list" id="addonList"></ul>

  <div class="empty-state" id="emptyState">
    <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <rect x="2" y="2" width="20" height="8" rx="2" ry="2"/>
      <rect x="2" y="14" width="20" height="8" rx="2" ry="2"/>
      <line x1="6" y1="6" x2="6.01" y2="6"/>
      <line x1="6" y1="18" x2="6.01" y2="18"/>
    </svg>
    <div class="empty-title">${context.getString(R.string.web_no_addons)}</div>
    <div class="empty-subtitle">${context.getString(R.string.web_manage_addons_only_subtitle)}</div>
  </div>
</div>

<!-- Bottom Save Bar -->
<div class="save-bar">
  <div class="save-bar-content">
    <button class="btn btn-save-main" id="saveBtn" onclick="saveChanges()">${context.getString(R.string.web_btn_save)}</button>
  </div>
</div>

<!-- Status Overlay Modal -->
<div class="status-overlay" id="statusOverlay">
  <div class="status-modal" id="statusModal"></div>
</div>

<script>
var addons = [];
var originalAddons = [];
var pollTimer = null;
var pollStartTime = 0;
var POLL_TIMEOUT = 90000;
var POLL_INTERVAL = 1200;

function escapeHtml(str) {
  if (!str) return '';
  var div = document.createElement('div');
  div.appendChild(document.createTextNode(str));
  return div.innerHTML;
}

function fetchWithTimeout(url, options, timeout) {
  timeout = timeout || 8000;
  return new Promise(function(resolve, reject) {
    var timer = setTimeout(function() {
      reject(new Error('Request timed out'));
    }, timeout);
    fetch(url, options).then(function(res) {
      clearTimeout(timer);
      resolve(res);
    }).catch(function(err) {
      clearTimeout(timer);
      reject(err);
    });
  });
}

function checkConnection() {
  fetchWithTimeout('/api/state', { method: 'GET' }, 4000)
    .then(function(res) {
      if (res.ok) document.getElementById('disconnectBanner').classList.remove('visible');
      else document.getElementById('disconnectBanner').classList.add('visible');
    })
    .catch(function() {
      document.getElementById('disconnectBanner').classList.add('visible');
    });
}
setInterval(checkConnection, 10000);

async function loadState() {
  try {
    var res = await fetchWithTimeout('/api/state', { method: 'GET' });
    var data = await res.json();
    addons = data.addons || [];
    originalAddons = JSON.parse(JSON.stringify(addons));
    renderAddons();
    document.getElementById('disconnectBanner').classList.remove('visible');
  } catch (e) {
    console.error('Failed to load state', e);
    document.getElementById('disconnectBanner').classList.add('visible');
  }
}

function renderAddons() {
  var list = document.getElementById('addonList');
  var empty = document.getElementById('emptyState');
  var countBadge = document.getElementById('addonCount');
  if (!list || !empty) return;

  list.innerHTML = '';
  countBadge.textContent = addons.length;

  if (addons.length === 0) {
    empty.style.display = 'block';
    return;
  }

  empty.style.display = 'none';
  addons.forEach(function(addon, i) {
    var li = document.createElement('li');
    li.className = 'addon-item';

    var isFirst = (i === 0);
    var isLast = (i === addons.length - 1);

    var orderHtml = 
      '<div class="order-controls">' +
        '<button class="btn-arrow" onclick="moveAddon(' + i + ',-1)"' + (isFirst ? ' disabled' : '') + ' title="Move Up">' +
          '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M18 15l-6-6-6 6"/></svg>' +
        '</button>' +
        '<button class="btn-arrow" onclick="moveAddon(' + i + ',1)"' + (isLast ? ' disabled' : '') + ' title="Move Down">' +
          '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M6 9l6 6 6-6"/></svg>' +
        '</button>' +
      '</div>';

    var descHtml = addon.description ? '<div class="addon-desc">' + escapeHtml(addon.description) + '</div>' : '';
    var newBadgeHtml = addon.isNew ? '<span class="badge-new">${context.getString(R.string.web_badge_new).replace("'", "\\'")}</span>' : '';

    li.innerHTML =
      orderHtml +
      '<div class="addon-details">' +
        '<div class="addon-name-row">' +
          '<span class="addon-name">' + escapeHtml(addon.name || addon.url) + '</span>' +
          newBadgeHtml +
        '</div>' +
        descHtml +
        '<div class="addon-url">' + escapeHtml(addon.url) + '</div>' +
      '</div>' +
      '<div class="addon-actions">' +
        '<button class="btn-delete" onclick="removeAddon(' + i + ')" title="Remove">' +
          '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2M10 11v6M14 11v6"/></svg>' +
        '</button>' +
      '</div>';

    list.appendChild(li);
  });
}

function moveAddon(index, direction) {
  var newIndex = index + direction;
  if (newIndex < 0 || newIndex >= addons.length) return;
  var item = addons.splice(index, 1)[0];
  addons.splice(newIndex, 0, item);
  renderAddons();
}

function removeAddon(index) {
  addons.splice(index, 1);
  renderAddons();
}

function addAddon() {
  var input = document.getElementById('addonUrl');
  var errorEl = document.getElementById('addError');
  var url = (input.value || '').trim();
  if (!url) return;

  if (url.startsWith('stremio://')) {
    url = url.replace(/^stremio:\/\//, 'https://');
  }
  if (!url.startsWith('http://') && !url.startsWith('https://')) {
    url = 'https://' + url;
  }
  if (url.endsWith('/manifest.json')) {
    url = url.replace(/\/manifest\.json$/, '');
  }
  url = url.replace(/\/+$/, '');

  if (addons.some(function(a) { return a.url === url; })) {
    errorEl.textContent = '${context.getString(R.string.web_error_addon_exists).replace("'", "\\'")}';
    errorEl.style.display = 'block';
    setTimeout(function() { errorEl.style.display = 'none'; }, 3500);
    return;
  }

  errorEl.style.display = 'none';
  addons.push({ url: url, name: url, description: null, isNew: true });
  input.value = '';
  renderAddons();
}

async function saveChanges() {
  var saveBtn = document.getElementById('saveBtn');
  saveBtn.disabled = true;

  var urls = addons.map(function(a) { return a.url; });
  try {
    var res = await fetchWithTimeout('/api/addons', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify({ urls: urls })
    }, 8000);
    var data = await res.json();

    if (data.status === 'pending_confirmation') {
      showPendingStatus();
      pollStatus(data.id);
    } else if (data.error) {
      showErrorStatus(data.error);
      saveBtn.disabled = false;
    }
  } catch (e) {
    showErrorStatus('${context.getString(R.string.web_error_failed_save).replace("'", "\\'")}');
    saveBtn.disabled = false;
  }
}

function showPendingStatus() {
  var overlay = document.getElementById('statusOverlay');
  var modal = document.getElementById('statusModal');
  modal.innerHTML =
    '<div class="spinner"></div>' +
    '<div class="status-title">${context.getString(R.string.web_status_waiting_tv).replace("'", "\\'")}</div>' +
    '<div class="status-desc">${context.getString(R.string.web_status_msg_waiting_tv).replace("'", "\\'")}</div>';
  overlay.classList.add('visible');
}

function showSuccessStatus() {
  var modal = document.getElementById('statusModal');
  modal.innerHTML =
    '<div class="status-icon-svg" style="color:var(--success)">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M20 6L9 17l-5-5"/></svg>' +
    '</div>' +
    '<div class="status-title" style="color:var(--success)">${context.getString(R.string.web_status_changes_applied).replace("'", "\\'")}</div>' +
    '<div class="status-desc">' + escapeHtml('${successStatusMessage.replace("'", "\\'")}') + '</div>';
  setTimeout(dismissStatus, 2200);
}

function showRejectedStatus() {
  var modal = document.getElementById('statusModal');
  modal.innerHTML =
    '<div class="status-icon-svg" style="color:var(--danger)">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M18 6L6 18M6 6l12 12"/></svg>' +
    '</div>' +
    '<div class="status-title" style="color:var(--danger)">${context.getString(R.string.web_status_changes_rejected).replace("'", "\\'")}</div>' +
    '<div class="status-desc">${context.getString(R.string.web_status_msg_changes_rejected).replace("'", "\\'")}</div>';
  setTimeout(function() {
    addons = JSON.parse(JSON.stringify(originalAddons));
    renderAddons();
    dismissStatus();
  }, 2200);
}

function showErrorStatus(msg) {
  var overlay = document.getElementById('statusOverlay');
  var modal = document.getElementById('statusModal');
  modal.innerHTML =
    '<div class="status-icon-svg" style="color:var(--danger)">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg>' +
    '</div>' +
    '<div class="status-title">${context.getString(R.string.web_status_error).replace("'", "\\'")}</div>' +
    '<div class="status-desc">' + escapeHtml(msg) + '</div>' +
    '<button class="btn btn-primary" style="margin-top:1.25rem;width:100%" onclick="dismissStatus()">${context.getString(R.string.web_btn_dismiss).replace("'", "\\'")}</div>';
  overlay.classList.add('visible');
}

function dismissStatus() {
  document.getElementById('statusOverlay').classList.remove('visible');
  document.getElementById('saveBtn').disabled = false;
  if (pollTimer) clearTimeout(pollTimer);
}

function pollStatus(id) {
  pollStartTime = Date.now();
  function check() {
    if (Date.now() - pollStartTime > POLL_TIMEOUT) {
      showErrorStatus('${context.getString(R.string.web_status_msg_timeout).replace("'", "\\'")}');
      return;
    }
    fetchWithTimeout('/api/status/' + id, { method: 'GET' }, 3000)
      .then(function(res) { return res.json(); })
      .then(function(data) {
        if (data.status === 'confirmed') {
          showSuccessStatus();
          originalAddons = JSON.parse(JSON.stringify(addons));
        } else if (data.status === 'rejected') {
          showRejectedStatus();
        } else {
          pollTimer = setTimeout(check, POLL_INTERVAL);
        }
      })
      .catch(function() {
        pollTimer = setTimeout(check, POLL_INTERVAL);
      });
  }
  pollTimer = setTimeout(check, 800);
}

var addonUrlInput = document.getElementById('addonUrl');
if (addonUrlInput) {
  addonUrlInput.addEventListener('keydown', function(e) {
    if (e.key === 'Enter') addAddon();
  });
}

loadState();
</script>
</body>
</html>
""".trimIndent()
    }
}
