package com.playtorrio.tv.server

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fi.iki.elonen.NanoHTTPD
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Embedded HTTP server that lets a phone browser manage PlayTorrio TV settings.
 *
 * Endpoints:
 *  GET  /             → Web UI (HTML page)
 *  GET  /api/state    → JSON snapshot of current settings
 *  POST /api/settings → Propose a change (phone sends desired state)
 *  GET  /api/status/{id} → Poll for TV confirmation
 */
class SettingsConfigServer(
    private val stateProvider: () -> SettingsState,
    private val onChangeProposed: (PendingChange) -> Unit,
    port: Int = 7979
) : NanoHTTPD(port) {

    // ── Data models ──────────────────────────────────────────────────────────

    data class AddonInfo(val url: String, val name: String, val description: String?)

    data class SettingsState(
      val addons: List<AddonInfo>,
      val streamingMode: Boolean,
      val debridEnabled: Boolean = false,
      val debridProvider: String = "realdebrid",
      val realDebridApiKey: String = "",
      val torboxApiKey: String = "",
      val torrentPreset: String = "balanced",
      val torrentCacheSizeMb: Int = 256,
      val torrentPreloadPercent: Int = 1,
      val torrentReadAheadPercent: Int = 86,
      val torrentConnectionsLimit: Int = 140,
      val torrentResponsiveMode: Boolean = true,
      val torrentDisableUpload: Boolean = true,
      val torrentDisableIpv6: Boolean = true,
      val trailerAutoplay: Boolean = true,
      val trailerDelaySec: Int = 3
    )

    data class PendingChange(
      val id: String = UUID.randomUUID().toString(),
      val proposedAddons: List<String>,
      val proposedStreamingMode: Boolean,
      val proposedDebridEnabled: Boolean = false,
      val proposedDebridProvider: String = "realdebrid",
      val proposedRealDebridApiKey: String = "",
      val proposedTorboxApiKey: String = "",
      val proposedTorrentPreset: String = "balanced",
      val proposedTorrentCacheSizeMb: Int = 256,
      val proposedTorrentPreloadPercent: Int = 1,
      val proposedTorrentReadAheadPercent: Int = 86,
      val proposedTorrentConnectionsLimit: Int = 140,
      val proposedTorrentResponsiveMode: Boolean = true,
      val proposedTorrentDisableUpload: Boolean = true,
      val proposedTorrentDisableIpv6: Boolean = true,
      val proposedTrailerAutoplay: Boolean = true,
      val proposedTrailerDelaySec: Int = 3,
      var status: ChangeStatus = ChangeStatus.PENDING
    )

    enum class ChangeStatus { PENDING, CONFIRMED, REJECTED }

    // ── State ────────────────────────────────────────────────────────────────

    private val gson = Gson()
    private val pendingChanges = ConcurrentHashMap<String, PendingChange>()

    fun confirmChange(id: String) { pendingChanges[id]?.status = ChangeStatus.CONFIRMED }
    fun rejectChange(id: String)  { pendingChanges[id]?.status = ChangeStatus.REJECTED  }

    // ── HTTP routing ─────────────────────────────────────────────────────────

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        return when {
            method == Method.GET  && uri == "/"                       -> serveWebPage()
            method == Method.GET  && uri == "/api/state"              -> serveState()
            method == Method.POST && uri == "/api/settings"           -> handleChange(session)
            method == Method.GET  && uri.startsWith("/api/status/")   -> serveStatus(uri)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveWebPage(): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", buildHtml())

    private fun serveState(): Response {
        val json = gson.toJson(stateProvider())
        return jsonResponse(json)
    }

    private fun handleChange(session: IHTTPSession): Response {
        // Auto-reject stale pending changes first
        pendingChanges.values
            .filter { it.status == ChangeStatus.PENDING }
            .forEach { it.status = ChangeStatus.REJECTED }

        return try {
            val bodyMap = HashMap<String, String>()
            session.parseBody(bodyMap)
            val body = bodyMap["postData"] ?: ""
            val parsed = gson.fromJson<Map<String, Any>>(
                body, object : TypeToken<Map<String, Any>>() {}.type
            )

            val current = stateProvider()
            val urls = parseStringList(parsed["urls"])
            val streamingMode = (parsed["streamingMode"] as? Boolean) ?: current.streamingMode
            val debridEnabled = (parsed["debridEnabled"] as? Boolean) ?: current.debridEnabled
            val debridProvider = (parsed["debridProvider"] as? String) ?: current.debridProvider
            val realDebridApiKey = (parsed["realDebridApiKey"] as? String) ?: current.realDebridApiKey
            val torboxApiKey = (parsed["torboxApiKey"] as? String) ?: current.torboxApiKey
            val torrentPreset = (parsed["torrentPreset"] as? String) ?: current.torrentPreset
            val torrentCacheSizeMb = (parsed["torrentCacheSizeMb"] as? Number)?.toInt() ?: current.torrentCacheSizeMb
            val torrentPreloadPercent = (parsed["torrentPreloadPercent"] as? Number)?.toInt() ?: current.torrentPreloadPercent
            val torrentReadAheadPercent = (parsed["torrentReadAheadPercent"] as? Number)?.toInt() ?: current.torrentReadAheadPercent
            val torrentConnectionsLimit = (parsed["torrentConnectionsLimit"] as? Number)?.toInt() ?: current.torrentConnectionsLimit
            val torrentResponsiveMode = (parsed["torrentResponsiveMode"] as? Boolean) ?: current.torrentResponsiveMode
            val torrentDisableUpload = (parsed["torrentDisableUpload"] as? Boolean) ?: current.torrentDisableUpload
            val torrentDisableIpv6 = (parsed["torrentDisableIpv6"] as? Boolean) ?: current.torrentDisableIpv6
            val trailerAutoplay = (parsed["trailerAutoplay"] as? Boolean) ?: current.trailerAutoplay
            val trailerDelaySec = (parsed["trailerDelaySec"] as? Number)?.toInt() ?: current.trailerDelaySec

            val change = PendingChange(
              proposedAddons = urls,
              proposedStreamingMode = streamingMode,
              proposedDebridEnabled = debridEnabled,
              proposedDebridProvider = debridProvider,
              proposedRealDebridApiKey = realDebridApiKey,
              proposedTorboxApiKey = torboxApiKey,
              proposedTorrentPreset = torrentPreset,
              proposedTorrentCacheSizeMb = torrentCacheSizeMb,
              proposedTorrentPreloadPercent = torrentPreloadPercent,
              proposedTorrentReadAheadPercent = torrentReadAheadPercent,
              proposedTorrentConnectionsLimit = torrentConnectionsLimit,
              proposedTorrentResponsiveMode = torrentResponsiveMode,
              proposedTorrentDisableUpload = torrentDisableUpload,
              proposedTorrentDisableIpv6 = torrentDisableIpv6,
              proposedTrailerAutoplay = trailerAutoplay,
              proposedTrailerDelaySec = trailerDelaySec
            )
            pendingChanges[change.id] = change
            onChangeProposed(change)

            jsonResponse(gson.toJson(mapOf("status" to "pending_confirmation", "id" to change.id)))
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json; charset=utf-8",
                gson.toJson(mapOf("error" to "Invalid request body"))
            )
        }
    }

    private fun serveStatus(uri: String): Response {
        val id = uri.removePrefix("/api/status/")
        val change = pendingChanges[id]
        val status = change?.status?.name?.lowercase() ?: "not_found"
        return jsonResponse(gson.toJson(mapOf("status" to status)))
    }

    private fun jsonResponse(json: String) =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json)

    private fun parseStringList(raw: Any?): List<String> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { (it as? String)?.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    // ── HTML Web UI ──────────────────────────────────────────────────────────

    private fun buildHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>PlayTorrio TV — Settings</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
<style>
*{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}
*:focus{outline:none}
body{font-family:'Inter',-apple-system,sans-serif;background:#000;color:#fff;min-height:100vh;line-height:1.5}
.page{max-width:600px;margin:0 auto;padding:0 1.5rem 6rem}
.header{text-align:center;padding:3rem 0 2.5rem;border-bottom:1px solid rgba(255,255,255,0.05);margin-bottom:2.5rem}
.header h1{font-size:1.5rem;font-weight:700;letter-spacing:-0.02em}
.header p{font-size:0.875rem;font-weight:300;color:rgba(255,255,255,0.4);margin-top:0.3rem}
.tabs{display:flex;gap:0.5rem;margin-bottom:2rem;border-bottom:1px solid rgba(255,255,255,0.07);padding-bottom:0}
.tab{background:none;border:none;color:rgba(255,255,255,0.4);font-family:inherit;font-size:0.875rem;font-weight:500;padding:0.75rem 1rem;cursor:pointer;border-bottom:2px solid transparent;margin-bottom:-1px;transition:all 0.2s}
.tab.active{color:#fff;border-bottom-color:#818cf8}
.section-label{font-size:0.7rem;font-weight:600;color:rgba(255,255,255,0.3);letter-spacing:0.1em;text-transform:uppercase;margin-bottom:1rem}
.add-row{display:flex;gap:0.75rem;margin-bottom:0.75rem}
.add-row input{flex:1;background:transparent;border:1px solid rgba(255,255,255,0.12);border-radius:100px;padding:0.875rem 1.25rem;color:#fff;font-family:inherit;font-size:0.9rem;transition:border-color 0.3s}
.add-row input:focus{border-color:rgba(255,255,255,0.4)}
.add-row input::placeholder{color:rgba(255,255,255,0.2)}
.btn{display:inline-flex;align-items:center;justify-content:center;gap:0.5rem;background:transparent;border:1px solid rgba(255,255,255,0.2);border-radius:100px;padding:0.875rem 1.5rem;color:#fff;font-family:inherit;font-size:0.875rem;font-weight:500;cursor:pointer;transition:all 0.3s;white-space:nowrap}
.btn:hover{background:#fff;color:#000;border-color:#fff}
.btn:active{transform:scale(0.97)}
.btn-save{width:100%;padding:1rem;font-size:0.95rem;font-weight:600;margin-top:2rem}
.btn-save:disabled{opacity:0.2;cursor:not-allowed;pointer-events:none}
.btn-remove{border-color:rgba(207,102,121,0.3);color:rgba(207,102,121,0.8);padding:0.5rem 1rem;font-size:0.75rem}
.btn-remove:hover{background:rgba(207,102,121,0.15);border-color:rgba(207,102,121,0.5);color:#CF6679}
.addon-list{list-style:none}
.addon-item{border-top:1px solid rgba(255,255,255,0.06);padding:1rem 0;display:flex;align-items:center;gap:0.75rem}
.addon-item:last-child{border-bottom:1px solid rgba(255,255,255,0.06)}
.addon-info{flex:1;min-width:0}
.addon-name{font-size:0.95rem;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.addon-url{font-size:0.75rem;color:rgba(255,255,255,0.25);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:0.1rem}
.addon-actions{flex-shrink:0}
.empty-state{text-align:center;color:rgba(255,255,255,0.2);padding:3rem 0;font-size:0.875rem;font-weight:300;display:none}
.tab-content{display:none}
.tab-content.active{display:block}
.toggle-row{display:flex;align-items:center;justify-content:space-between;padding:1rem 0;border-bottom:1px solid rgba(255,255,255,0.06)}
.toggle-info{flex:1;min-width:0;padding-right:1rem}
.toggle-title{font-size:0.95rem;font-weight:600}
.toggle-desc{font-size:0.78rem;color:rgba(255,255,255,0.4);margin-top:0.2rem}
.toggle{position:relative;width:48px;height:26px;flex-shrink:0}
.toggle input{display:none}
.toggle-track{display:block;width:100%;height:100%;background:rgba(255,255,255,0.15);border-radius:100px;transition:background 0.3s;cursor:pointer}
.toggle input:checked+.toggle-track{background:#818cf8}
.toggle-thumb{position:absolute;top:3px;left:3px;width:20px;height:20px;background:#fff;border-radius:50%;transition:transform 0.3s;pointer-events:none}
.toggle input:checked~.toggle-thumb{transform:translateX(22px)}
.status-msg{display:none;text-align:center;padding:0.75rem 1rem;border-radius:12px;font-size:0.875rem;font-weight:500;margin-top:1rem}
.status-msg.success{background:rgba(74,222,128,0.12);color:#4ade80;border:1px solid rgba(74,222,128,0.2)}
.status-msg.error{background:rgba(248,113,113,0.12);color:#f87171;border:1px solid rgba(248,113,113,0.2)}
.status-msg.pending{background:rgba(129,140,248,0.12);color:#818cf8;border:1px solid rgba(129,140,248,0.2)}
.add-error{color:rgba(207,102,121,0.9);font-size:0.8rem;margin-top:0.5rem;display:none}
.provider-row{display:flex;gap:0.625rem;margin-top:0.75rem}
.provider-btn{flex:1;background:transparent;border:1px solid rgba(255,255,255,0.12);border-radius:12px;padding:0.75rem 0;color:rgba(255,255,255,0.5);font-family:inherit;font-size:0.875rem;font-weight:500;cursor:pointer;transition:all 0.2s;text-align:center}
.provider-btn.active{background:rgba(129,140,248,0.15);border-color:rgba(129,140,248,0.5);color:#818cf8;font-weight:600}
.provider-btn:hover:not(.active){border-color:rgba(255,255,255,0.3);color:#fff}
.api-key-input{width:100%;background:transparent;border:1px solid rgba(255,255,255,0.12);border-radius:12px;padding:0.875rem 1.25rem;color:#fff;font-family:inherit;font-size:0.875rem;transition:border-color 0.3s;margin-top:0.75rem}
.api-key-input:focus{border-color:rgba(129,140,248,0.5)}
.api-key-input::placeholder{color:rgba(255,255,255,0.2)}
.debrid-section{display:none;margin-top:1rem}
.debrid-section.visible{display:block}
.global-actions{position:sticky;bottom:1rem;background:rgba(0,0,0,0.85);backdrop-filter:blur(6px);padding-top:1rem;margin-top:2rem}
</style>
</head>
<body>
<div class="page">
  <div class="header">
    <h1>PlayTorrio TV</h1>
    <p>Remote Settings</p>
  </div>

  <div class="tabs">
    <button class="tab active" onclick="switchTab('addons')">Addons</button>
    <button class="tab" onclick="switchTab('streaming')">Streaming</button>
    <button class="tab" onclick="switchTab('torrent')">Torrent</button>
    <button class="tab" onclick="switchTab('debrid')">Debrid</button>
    <button class="tab" onclick="switchTab('trailers')">Trailers</button>
  </div>

  <!-- ADDONS TAB -->
  <div class="tab-content active" id="tab-addons">
    <div class="section-label">Add Addon</div>
    <div class="add-row">
      <input type="url" id="addonUrl" placeholder="Paste addon manifest URL…" autocomplete="off" autocapitalize="off" spellcheck="false">
      <button class="btn" onclick="addAddon()">Add</button>
    </div>
    <div class="add-error" id="addError"></div>
    <div class="section-label" style="margin-top:2rem">Installed Addons</div>
    <ul class="addon-list" id="addonList"></ul>
    <div class="empty-state" id="emptyState">No addons installed</div>
  </div>

  <!-- STREAMING TAB -->
  <div class="tab-content" id="tab-streaming">
    <div class="section-label">Playback</div>
    <div class="toggle-row">
      <div class="toggle-info">
        <div class="toggle-title">Streaming Mode</div>
        <div class="toggle-desc">Stream torrents in real-time without waiting for full download</div>
      </div>
      <label class="toggle">
        <input type="checkbox" id="streamingModeToggle">
        <span class="toggle-track"></span>
        <span class="toggle-thumb"></span>
      </label>
    </div>
  </div>

  <!-- TORRENT TAB -->
  <div class="tab-content" id="tab-torrent">
    <div class="section-label">Torrent Engine</div>
    <div class="toggle-row">
      <div class="toggle-info">
        <div class="toggle-title">Preset</div>
        <div class="toggle-desc">All presets prioritize fast initial playback</div>
      </div>
    </div>
    <div class="provider-row">
      <button class="provider-btn" id="presetSafe" onclick="selectPreset('safe')">Safe</button>
      <button class="provider-btn" id="presetBalanced" onclick="selectPreset('balanced')">Balanced</button>
      <button class="provider-btn" id="presetTurbo" onclick="selectPreset('turbo')">Turbo</button>
      <button class="provider-btn" id="presetExtreme" onclick="selectPreset('extreme')">Extreme</button>
    </div>
    <div class="section-label" style="margin-top:1.5rem">Cache Size</div>
    <div class="provider-row">
      <button class="provider-btn" id="cache128" onclick="selectCache('128')">128 MB</button>
      <button class="provider-btn" id="cache256" onclick="selectCache('256')">256 MB</button>
      <button class="provider-btn" id="cache384" onclick="selectCache('384')">384 MB</button>
      <button class="provider-btn" id="cache512" onclick="selectCache('512')">512 MB</button>
    </div>
    <div class="section-label" style="margin-top:1.5rem">Startup Buffer</div>
    <div class="provider-row">
      <button class="provider-btn" id="preload1" onclick="selectPreload('1')">1%</button>
      <button class="provider-btn" id="preload2" onclick="selectPreload('2')">2%</button>
      <button class="provider-btn" id="preload4" onclick="selectPreload('4')">4%</button>
      <button class="provider-btn" id="preload8" onclick="selectPreload('8')">8%</button>
    </div>
    <div class="section-label" style="margin-top:1.5rem">Read Ahead</div>
    <div class="provider-row">
      <button class="provider-btn" id="ahead78" onclick="selectReadAhead('78')">78%</button>
      <button class="provider-btn" id="ahead86" onclick="selectReadAhead('86')">86%</button>
      <button class="provider-btn" id="ahead92" onclick="selectReadAhead('92')">92%</button>
      <button class="provider-btn" id="ahead94" onclick="selectReadAhead('94')">94%</button>
    </div>
    <div class="section-label" style="margin-top:1.5rem">Connections</div>
    <div class="provider-row">
      <button class="provider-btn" id="conn70" onclick="selectConnections('70')">70</button>
      <button class="provider-btn" id="conn140" onclick="selectConnections('140')">140</button>
      <button class="provider-btn" id="conn260" onclick="selectConnections('260')">260</button>
      <button class="provider-btn" id="conn400" onclick="selectConnections('400')">400</button>
    </div>
    <div class="toggle-row">
      <div class="toggle-info">
        <div class="toggle-title">Responsive Mode</div>
        <div class="toggle-desc">Start playback as soon as pieces arrive</div>
      </div>
      <label class="toggle">
        <input type="checkbox" id="torrentResponsiveToggle">
        <span class="toggle-track"></span>
        <span class="toggle-thumb"></span>
      </label>
    </div>
    <div class="toggle-row">
      <div class="toggle-info">
        <div class="toggle-title">Disable Upload</div>
        <div class="toggle-desc">Use bandwidth for playback instead of seeding</div>
      </div>
      <label class="toggle">
        <input type="checkbox" id="torrentUploadToggle">
        <span class="toggle-track"></span>
        <span class="toggle-thumb"></span>
      </label>
    </div>
    <div class="toggle-row">
      <div class="toggle-info">
        <div class="toggle-title">Disable IPv6</div>
        <div class="toggle-desc">Useful when IPv6 peers are slow or unstable</div>
      </div>
      <label class="toggle">
        <input type="checkbox" id="torrentIpv6Toggle">
        <span class="toggle-track"></span>
        <span class="toggle-thumb"></span>
      </label>
    </div>
  </div>

  <!-- DEBRID TAB -->
  <div class="tab-content" id="tab-debrid">
    <div class="section-label">Debrid Service</div>
    <div class="toggle-row">
      <div class="toggle-info">
        <div class="toggle-title">Use Debrid for Streams</div>
        <div class="toggle-desc">Resolve magnets instantly via debrid (cached torrents only)</div>
      </div>
      <label class="toggle">
        <input type="checkbox" id="debridEnabledToggle" onchange="onDebridToggle()">
        <span class="toggle-track"></span>
        <span class="toggle-thumb"></span>
      </label>
    </div>
    <div class="debrid-section" id="debridSection">
      <div class="section-label" style="margin-top:1.5rem">Provider</div>
      <div class="provider-row">
        <button class="provider-btn active" id="btnRd" onclick="selectProvider('realdebrid')">Real-Debrid</button>
        <button class="provider-btn" id="btnTb" onclick="selectProvider('torbox')">TorBox</button>
      </div>
      <div id="rdKeyRow">
        <div class="section-label" style="margin-top:1.5rem">Real-Debrid API Key</div>
        <input class="api-key-input" type="password" id="rdApiKey" placeholder="Paste your Real-Debrid API key…" autocomplete="off" spellcheck="false">
      </div>
      <div id="tbKeyRow" style="display:none">
        <div class="section-label" style="margin-top:1.5rem">TorBox API Key</div>
        <input class="api-key-input" type="password" id="tbApiKey" placeholder="Paste your TorBox API key…" autocomplete="off" spellcheck="false">
      </div>
    </div>
  </div>

  <!-- TRAILERS TAB -->
  <div class="tab-content" id="tab-trailers">
    <div class="section-label">Trailer Playback</div>
    <div class="toggle-row">
      <div class="toggle-info">
        <div class="toggle-title">Autoplay Trailers</div>
        <div class="toggle-desc">Automatically play trailers on the home screen when hovering</div>
      </div>
      <label class="toggle">
        <input type="checkbox" id="trailerAutoplayToggle" onchange="onTrailerAutoplayToggle()">
        <span class="toggle-track"></span>
        <span class="toggle-thumb"></span>
      </label>
    </div>
    <div id="trailerDelaySection" style="display:none">
      <div class="section-label" style="margin-top:1.5rem">Delay Before Playback</div>
      <div style="display:flex;align-items:center;gap:1rem;padding:0.75rem 0">
        <input type="range" id="trailerDelaySlider" min="3" max="10" value="3" style="flex:1;accent-color:#818cf8" oninput="onTrailerDelayChange(this.value)">
        <span id="trailerDelayValue" style="font-size:1.1rem;font-weight:600;color:#818cf8;min-width:2.5rem;text-align:center">3s</span>
      </div>
    </div>
  </div>

  <div class="global-actions">
    <button class="btn btn-save" id="saveBtn" onclick="saveChanges()" disabled>Apply Settings on TV</button>
    <div class="status-msg" id="statusMsgGlobal"></div>
  </div>
</div>

<script>
var TAB_NAMES = ['addons','streaming','torrent','debrid','trailers'];
var state = {addons:[],streamingMode:true,debridEnabled:false,debridProvider:'realdebrid',realDebridApiKey:'',torboxApiKey:'',torrentPreset:'balanced',torrentCacheSizeMb:256,torrentPreloadPercent:1,torrentReadAheadPercent:86,torrentConnectionsLimit:140,torrentResponsiveMode:true,torrentDisableUpload:true,torrentDisableIpv6:true,trailerAutoplay:true,trailerDelaySec:3};
var pendingAddons = [];
var dirty = false;

function updateSaveButton() {
  var saveBtn = document.getElementById('saveBtn');
  if (saveBtn) saveBtn.disabled = !dirty;
}

function switchTab(name) {
  document.querySelectorAll('.tab').forEach(function(t,i){
    t.classList.toggle('active', TAB_NAMES[i] === name);
  });
  document.querySelectorAll('.tab-content').forEach(function(c,i){
    c.classList.toggle('active', 'tab-'+TAB_NAMES[i] === 'tab-'+name);
  });
}

function onDebridToggle() {
  var enabled = document.getElementById('debridEnabledToggle').checked;
  document.getElementById('debridSection').classList.toggle('visible', enabled);
}

function onTrailerAutoplayToggle() {
  var enabled = document.getElementById('trailerAutoplayToggle').checked;
  state.trailerAutoplay = enabled;
  document.getElementById('trailerDelaySection').style.display = enabled ? '' : 'none';
  dirty = true;
  updateSaveButton();
}

function onTrailerDelayChange(v) {
  var val_ = parseInt(v, 10);
  state.trailerDelaySec = val_;
  document.getElementById('trailerDelayValue').textContent = val_ + 's';
  dirty = true;
  updateSaveButton();
}

function selectProvider(id) {
  state.debridProvider = id;
  dirty = true;
  updateSaveButton();
  document.getElementById('btnRd').classList.toggle('active', id === 'realdebrid');
  document.getElementById('btnTb').classList.toggle('active', id === 'torbox');
  document.getElementById('rdKeyRow').style.display = id === 'realdebrid' ? '' : 'none';
  document.getElementById('tbKeyRow').style.display = id === 'torbox' ? '' : 'none';
}

function selectPreset(id) {
  state.torrentPreset = id;
  dirty = true;
  updateSaveButton();
  ['safe','balanced','turbo','extreme'].forEach(function(v){
    var btn = document.getElementById('preset' + v.charAt(0).toUpperCase() + v.slice(1));
    if (btn) btn.classList.toggle('active', v === id);
  });
}

function selectCache(v) {
  state.torrentCacheSizeMb = parseInt(v, 10);
  dirty = true;
  updateSaveButton();
  ['128','256','384','512'].forEach(function(x){ document.getElementById('cache'+x).classList.toggle('active', x === v); });
}

function selectPreload(v) {
  state.torrentPreloadPercent = parseInt(v, 10);
  dirty = true;
  updateSaveButton();
  ['1','2','4','8'].forEach(function(x){ document.getElementById('preload'+x).classList.toggle('active', x === v); });
}

function selectReadAhead(v) {
  state.torrentReadAheadPercent = parseInt(v, 10);
  dirty = true;
  updateSaveButton();
  ['78','86','92','94'].forEach(function(x){ document.getElementById('ahead'+x).classList.toggle('active', x === v); });
}

function selectConnections(v) {
  state.torrentConnectionsLimit = parseInt(v, 10);
  dirty = true;
  updateSaveButton();
  ['70','140','260','400'].forEach(function(x){ document.getElementById('conn'+x).classList.toggle('active', x === v); });
}

function escHtml(s) {
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function renderAddons() {
  var list = document.getElementById('addonList');
  var empty = document.getElementById('emptyState');
  list.innerHTML = '';
  if (pendingAddons.length === 0) {
    empty.style.display = 'block';
  } else {
    empty.style.display = 'none';
    pendingAddons.forEach(function(a,i){
      var li = document.createElement('li');
      li.className = 'addon-item';
      li.innerHTML = '<div class="addon-info"><div class="addon-name">'+escHtml(a.name||a.url)+'</div><div class="addon-url">'+escHtml(a.url)+'</div></div><div class="addon-actions"><button class="btn btn-remove" onclick="removeAddon('+i+')">Remove</button></div>';
      list.appendChild(li);
    });
  }
  updateSaveButton();
}

function addAddon() {
  var input = document.getElementById('addonUrl');
  var url = input.value.trim();
  var errEl = document.getElementById('addError');
  errEl.style.display = 'none';
  if (!url) return;
  var exists = pendingAddons.some(function(a){
    return a.url === url || a.url === url.replace(/\/manifest\.json$/, '') || a.url+'/manifest.json' === url;
  });
  if (exists) { errEl.textContent='This addon is already installed'; errEl.style.display='block'; return; }
  pendingAddons.push({ url: url.replace(/\/manifest\.json$/,''), name: url.replace(/\/manifest\.json$/,'').split('/').pop() || url, description: null });
  input.value = '';
  dirty = true;
  updateSaveButton();
  renderAddons();
}

function removeAddon(i) {
  pendingAddons.splice(i, 1);
  dirty = true;
  updateSaveButton();
  renderAddons();
}

async function loadState() {
  try {
    var r = await fetch('/api/state');
    state = await r.json();
    pendingAddons = state.addons.map(function(a){ return { url: a.url, name: a.name, description: a.description }; });
    dirty = false;
    renderAddons();
    document.getElementById('streamingModeToggle').checked = !!state.streamingMode;
    document.getElementById('streamingModeToggle').onchange = function() { dirty = true; updateSaveButton(); };
    document.getElementById('debridEnabledToggle').checked = !!state.debridEnabled;
    document.getElementById('debridEnabledToggle').onchange = function() { onDebridToggle(); dirty = true; updateSaveButton(); };
    document.getElementById('debridSection').classList.toggle('visible', !!state.debridEnabled);
    selectProvider(state.debridProvider || 'realdebrid');
    document.getElementById('rdApiKey').value = state.realDebridApiKey || '';
    document.getElementById('tbApiKey').value = state.torboxApiKey || '';
    document.getElementById('rdApiKey').oninput = function() { dirty = true; updateSaveButton(); };
    document.getElementById('tbApiKey').oninput = function() { dirty = true; updateSaveButton(); };

    selectPreset(state.torrentPreset || 'balanced');
    selectCache(String(state.torrentCacheSizeMb || 256));
    selectPreload(String(state.torrentPreloadPercent || 1));
    selectReadAhead(String(state.torrentReadAheadPercent || 86));
    selectConnections(String(state.torrentConnectionsLimit || 140));
    document.getElementById('torrentResponsiveToggle').checked = !!state.torrentResponsiveMode;
    document.getElementById('torrentUploadToggle').checked = !!state.torrentDisableUpload;
    document.getElementById('torrentIpv6Toggle').checked = !!state.torrentDisableIpv6;
    document.getElementById('torrentResponsiveToggle').onchange = function() { dirty = true; updateSaveButton(); };
    document.getElementById('torrentUploadToggle').onchange = function() { dirty = true; updateSaveButton(); };
    document.getElementById('torrentIpv6Toggle').onchange = function() { dirty = true; updateSaveButton(); };

    document.getElementById('trailerAutoplayToggle').checked = state.trailerAutoplay !== false;
    document.getElementById('trailerDelaySlider').value = state.trailerDelaySec || 3;
    document.getElementById('trailerDelayValue').textContent = (state.trailerDelaySec || 3) + 's';
    document.getElementById('trailerDelaySection').style.display = state.trailerAutoplay !== false ? '' : 'none';
    dirty = false;
    updateSaveButton();
  } catch(e) {
    showStatusMsg('statusMsgGlobal', 'Could not connect to TV', 'error');
  }
}

async function saveChanges() {
  var statusId = 'statusMsgGlobal';
  showStatusMsg(statusId, 'Waiting for confirmation on TV\u2026', 'pending');
  var payload = {
    urls: pendingAddons.map(function(a){ return a.url; }),
    streamingMode: document.getElementById('streamingModeToggle').checked,
    debridEnabled: document.getElementById('debridEnabledToggle').checked,
    debridProvider: state.debridProvider || 'realdebrid',
    realDebridApiKey: document.getElementById('rdApiKey').value.trim(),
    torboxApiKey: document.getElementById('tbApiKey').value.trim(),
    torrentPreset: state.torrentPreset || 'balanced',
    torrentCacheSizeMb: state.torrentCacheSizeMb || 256,
    torrentPreloadPercent: state.torrentPreloadPercent || 1,
    torrentReadAheadPercent: state.torrentReadAheadPercent || 86,
    torrentConnectionsLimit: state.torrentConnectionsLimit || 140,
    torrentResponsiveMode: document.getElementById('torrentResponsiveToggle').checked,
    torrentDisableUpload: document.getElementById('torrentUploadToggle').checked,
    torrentDisableIpv6: document.getElementById('torrentIpv6Toggle').checked,
    trailerAutoplay: document.getElementById('trailerAutoplayToggle').checked,
    trailerDelaySec: parseInt(document.getElementById('trailerDelaySlider').value, 10)
  };
  try {
    var r = await fetch('/api/settings', {
      method: 'POST',
      headers: {'Content-Type':'application/json'},
      body: JSON.stringify(payload)
    });
    var data = await r.json();
    if (!data.id) throw new Error('No change ID returned');
    pollStatus(data.id, statusId);
  } catch(e) {
    showStatusMsg(statusId, 'Failed to send changes: ' + e.message, 'error');
  }
}

async function pollStatus(id, statusId) {
  for (var i = 0; i < 60; i++) {
    await new Promise(function(res){ setTimeout(res, 1000); });
    try {
      var r = await fetch('/api/status/' + id);
      var data = await r.json();
      if (data.status === 'confirmed') {
        showStatusMsg(statusId, '\u2713 Applied on TV!', 'success');
        dirty = false;
        updateSaveButton();
        await loadState();
        return;
      } else if (data.status === 'rejected') {
        showStatusMsg(statusId, 'Rejected on TV', 'error');
        return;
      }
    } catch(e) { /* retry */ }
  }
  showStatusMsg(statusId, 'Timed out waiting for TV', 'error');
}

function showStatusMsg(id, text, type) {
  var el = document.getElementById(id);
  el.textContent = text;
  el.className = 'status-msg '+type;
  el.style.display = 'block';
}

loadState();
</script>
</body>
</html>
""".trimIndent()

    // ── Factory ──────────────────────────────────────────────────────────────

    companion object {
        fun startOnAvailablePort(
            stateProvider: () -> SettingsState,
            onChangeProposed: (PendingChange) -> Unit,
            startPort: Int = 7979,
            maxAttempts: Int = 10
        ): SettingsConfigServer? {
            for (port in startPort until startPort + maxAttempts) {
                try {
                    val server = SettingsConfigServer(
                        stateProvider = stateProvider,
                        onChangeProposed = onChangeProposed,
                        port = port
                    )
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (e: Exception) {
                    // Port in use, try next
                }
            }
            return null
        }
    }
}
