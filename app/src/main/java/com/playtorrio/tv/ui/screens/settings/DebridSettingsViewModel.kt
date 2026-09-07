package com.playtorrio.tv.ui.screens.settings

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.BuildConfig
import com.playtorrio.tv.R
import com.playtorrio.tv.core.debrid.DebridDeviceAuthorization
import com.playtorrio.tv.core.debrid.DebridDeviceAuthorizationTokenResult
import com.playtorrio.tv.core.debrid.DebridProviderCapability
import com.playtorrio.tv.core.debrid.DebridProviders
import com.playtorrio.tv.core.debrid.supports
import com.playtorrio.tv.core.qr.QrCodeGenerator
import com.playtorrio.tv.core.server.DebridFormatterConfigServer
import com.playtorrio.tv.core.server.DebridFormatterSettings
import com.playtorrio.tv.core.server.DeviceIpAddress
import com.playtorrio.tv.data.local.DebridSettingsDataStore
import com.playtorrio.tv.data.remote.dto.PremiumizeDeviceTokenDto
import com.playtorrio.tv.data.remote.dto.TorboxDeviceTokenDto
import com.playtorrio.tv.data.remote.dto.TorboxDeviceTokenRequestDto
import com.playtorrio.tv.data.remote.dto.TorboxEnvelopeDto
import com.playtorrio.tv.data.remote.api.PremiumizeApi
import com.playtorrio.tv.data.remote.api.RealDebridApi
import com.playtorrio.tv.data.remote.api.TorboxApi
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Named
import com.playtorrio.tv.domain.model.DEBRID_PREPARE_INSTANT_PLAYBACK_DEFAULT_LIMIT
import com.playtorrio.tv.domain.model.DebridSettings
import com.playtorrio.tv.domain.model.DebridStreamCodecFilter
import com.playtorrio.tv.domain.model.DebridStreamFeatureFilter
import com.playtorrio.tv.domain.model.DebridStreamMinimumQuality
import com.playtorrio.tv.domain.model.DebridStreamPreferences
import com.playtorrio.tv.domain.model.DebridStreamSortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebridSettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DebridSettingsDataStore,
    private val torboxApi: TorboxApi,
    private val premiumizeApi: PremiumizeApi,
    private val realDebridApi: RealDebridApi,
    @Named("directDebrid") private val okHttpClient: OkHttpClient
) : ViewModel() {
    private var formatterServer: DebridFormatterConfigServer? = null
    private var logoBytes: ByteArray? = null

    private val _uiState = MutableStateFlow(DebridSettingsUiState())
    val uiState: StateFlow<DebridSettingsUiState> = _uiState.asStateFlow()

    private val _validating = MutableStateFlow(false)
    val validating: StateFlow<Boolean> = _validating.asStateFlow()

    private val _validationError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val validationError: SharedFlow<String> = _validationError.asSharedFlow()

    init {
        loadLogoBytes()
        viewModelScope.launch {
            dataStore.settings.collectLatest { settings ->
                _uiState.update { it.fromSettings(settings) }
            }
        }
    }

    private fun loadLogoBytes() {
        try {
            val inputStream = context.resources.openRawResource(R.drawable.app_logo_wordmark)
            logoBytes = inputStream.use { it.readBytes() }
        } catch (_: Exception) { }
    }

    fun onEvent(event: DebridSettingsEvent) {
        when (event) {
            is DebridSettingsEvent.ToggleEnabled -> {
                if (event.enabled && !_uiState.value.hasResolverProvider) return
                update { dataStore.setEnabled(event.enabled) }
            }
        }
    }

    fun setCloudLibraryEnabled(enabled: Boolean) {
        update { dataStore.setCloudLibraryEnabled(enabled) }
    }

    fun setPreferredResolverProviderId(providerId: String) {
        update { dataStore.setPreferredResolverProviderId(providerId) }
    }

    fun startFormatterQrMode() {
        val ip = DeviceIpAddress.get(context)
        if (ip == null) {
            _uiState.update { it.copy(serverError = context.getString(R.string.error_network_required)) }
            return
        }
        stopFormatterServer()
        formatterServer = DebridFormatterConfigServer.startOnAvailablePort(
            currentSettingsProvider = {
                val state = _uiState.value
                DebridFormatterSettings(
                    nameTemplate = state.streamNameTemplate,
                    descriptionTemplate = state.streamDescriptionTemplate,
                    streamPreferences = state.streamPreferences,
                    enabled = state.enabled,
                    preferredResolverProviderId = state.activeResolverProvider?.id.orEmpty(),
                    realDebridApiKey = state.realDebridApiKey,
                    allDebridApiKey = state.allDebridApiKey,
                    debridLinkApiKey = state.debridLinkApiKey,
                    torboxApiKey = state.torboxApiKey,
                    premiumizeApiKey = state.premiumizeApiKey
                )
            },
            onSettingsChanged = { settings ->
                _uiState.update { it.copy(
                    enabled = settings.enabled,
                    preferredResolverProviderId = settings.preferredResolverProviderId,
                    realDebridApiKey = settings.realDebridApiKey,
                    allDebridApiKey = settings.allDebridApiKey,
                    debridLinkApiKey = settings.debridLinkApiKey,
                    torboxApiKey = settings.torboxApiKey,
                    premiumizeApiKey = settings.premiumizeApiKey,
                    streamNameTemplate = settings.nameTemplate,
                    streamDescriptionTemplate = settings.descriptionTemplate,
                    streamPreferences = settings.streamPreferences
                ) }
                viewModelScope.launch {
                    dataStore.setEnabled(settings.enabled)
                    if (settings.preferredResolverProviderId.isNotBlank()) {
                        dataStore.setPreferredResolverProviderId(settings.preferredResolverProviderId)
                    }
                    dataStore.setRealDebridApiKey(settings.realDebridApiKey)
                    dataStore.setAllDebridApiKey(settings.allDebridApiKey)
                    dataStore.setDebridLinkApiKey(settings.debridLinkApiKey)
                    dataStore.setTorboxApiKey(settings.torboxApiKey)
                    dataStore.setPremiumizeApiKey(settings.premiumizeApiKey)
                    dataStore.setStreamTemplates(
                        nameTemplate = settings.nameTemplate,
                        descriptionTemplate = settings.descriptionTemplate
                    )
                    dataStore.setStreamPreferences(settings.streamPreferences)
                }
            },
            context = context,
            logoProvider = { logoBytes }
        )
        val server = formatterServer
        if (server == null) {
            _uiState.update { it.copy(serverError = context.getString(R.string.error_server_ports_unavailable)) }
            return
        }
        val url = "http://$ip:${server.listeningPort}"
        _uiState.update {
            it.copy(
                isFormatterQrModeActive = true,
                formatterQrCodeBitmap = QrCodeGenerator.generate(url, 512),
                formatterServerUrl = url,
                serverError = null
            )
        }
    }

    fun stopFormatterQrMode() {
        stopFormatterServer()
        _uiState.update {
            it.copy(
                isFormatterQrModeActive = false,
                formatterQrCodeBitmap = null,
                formatterServerUrl = null
            )
        }
    }

    fun resetFormatterTemplates() {
        update { dataStore.resetStreamTemplates() }
    }

    fun setInstantPlaybackPreparationEnabled(enabled: Boolean) {
        val nextLimit = if (enabled) {
            DEBRID_PREPARE_INSTANT_PLAYBACK_DEFAULT_LIMIT
        } else {
            0
        }
        update { dataStore.setInstantPlaybackPreparationLimit(nextLimit) }
    }

    fun setInstantPlaybackPreparationLimit(limit: Int) {
        update { dataStore.setInstantPlaybackPreparationLimit(limit) }
    }

    fun setStreamMaxResults(maxResults: Int) {
        update { dataStore.setStreamMaxResults(maxResults) }
    }

    fun setStreamSortMode(mode: DebridStreamSortMode) {
        update { dataStore.setStreamSortMode(mode) }
    }

    fun setStreamMinimumQuality(quality: DebridStreamMinimumQuality) {
        update { dataStore.setStreamMinimumQuality(quality) }
    }

    fun setStreamDolbyVisionFilter(filter: DebridStreamFeatureFilter) {
        update { dataStore.setStreamDolbyVisionFilter(filter) }
    }

    fun setStreamHdrFilter(filter: DebridStreamFeatureFilter) {
        update { dataStore.setStreamHdrFilter(filter) }
    }

    fun setStreamCodecFilter(filter: DebridStreamCodecFilter) {
        update { dataStore.setStreamCodecFilter(filter) }
    }

    fun setStreamPreferences(preferences: DebridStreamPreferences) {
        update { dataStore.setStreamPreferences(preferences) }
    }

    fun saveProviderCredential(providerId: String, value: String) {
        update { dataStore.setProviderApiKey(providerId, value.trim()) }
    }

    private fun sanitizeApiKey(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("Bearer ", ignoreCase = true)) {
            s = s.substring(7).trim()
        }
        if ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith('\'') && s.endsWith('\''))) {
            if (s.length >= 2) {
                s = s.substring(1, s.length - 1).trim()
            }
        }
        return s
    }

    fun validateAndSaveTorboxApiKey(value: String, onSuccess: () -> Unit) {
        validateAndSaveProviderApiKey(DebridProviders.TORBOX_ID, value, onSuccess)
    }

    fun validateAndSaveProviderApiKey(providerId: String, value: String, onSuccess: () -> Unit) {
        val trimmed = sanitizeApiKey(value)
        if (trimmed.isBlank()) {
            viewModelScope.launch { dataStore.setProviderApiKey(providerId, "") }
            onSuccess()
            return
        }
        viewModelScope.launch {
            _validating.value = true
            val valid = validateProviderApiKey(providerId, trimmed)
            _validating.value = false
            if (valid) {
                dataStore.setProviderApiKey(providerId, trimmed)
                onSuccess()
            } else {
                _validationError.tryEmit(context.getString(R.string.debrid_key_invalid))
            }
        }
    }

    private suspend fun validateProviderApiKey(providerId: String, apiKey: String): Boolean {
        return try {
            when (DebridProviders.byId(providerId)?.id) {
                DebridProviders.TORBOX_ID -> {
                    val response = torboxApi.getUser("Bearer $apiKey")
                    val isSuccess = response.isSuccessful
                    response.body()?.close()
                    response.errorBody()?.close()
                    isSuccess
                }
                DebridProviders.PREMIUMIZE_ID -> {
                    val response = premiumizeApi.accountInfo(authorization = "Bearer $apiKey", apikey = apiKey)
                    response.errorBody()?.close()
                    response.isSuccessful && !response.body()?.status.equals("error", ignoreCase = true)
                }
                DebridProviders.REAL_DEBRID_ID -> {
                    val response = realDebridApi.getUser("Bearer $apiKey")
                    response.body()?.close()
                    response.errorBody()?.close()
                    response.isSuccessful
                }
                DebridProviders.ALL_DEBRID_ID -> {
                    val req = Request.Builder()
                        .url("https://api.alldebrid.com/v4/user?agent=PlayTorrio&apikey=$apiKey")
                        .build()
                    val res = okHttpClient.newCall(req).execute()
                    val success = res.isSuccessful && res.body?.string()?.contains("\"success\"") == true
                    success
                }
                DebridProviders.DEBRID_LINK_ID -> {
                    val req = Request.Builder()
                        .url("https://debrid-link.com/api/v2/account/infos")
                        .header("Authorization", "Bearer $apiKey")
                        .build()
                    val res = okHttpClient.newCall(req).execute()
                    val success = res.isSuccessful && res.body?.string()?.contains("\"success\":true") == true
                    success
                }
                else -> false
            }
        } catch (error: Exception) {
            false
        }
    }

    suspend fun startDeviceAuthorization(providerId: String): DebridDeviceAuthorization? {
        return when (DebridProviders.byId(providerId)?.id) {
            DebridProviders.TORBOX_ID -> {
                val response = torboxApi.startDeviceAuthorization("PlayTorrio")
                val data = response.body()?.takeIf { response.isSuccessful && it.success != false }?.data
                    ?: return null
                val deviceCode = data.deviceCode?.takeIf { it.isNotBlank() } ?: return null
                val userCode = data.code?.takeIf { it.isNotBlank() } ?: return null
                val verificationUrl = data.verificationUrl?.takeIf { it.isNotBlank() } ?: return null
                DebridDeviceAuthorization(
                    providerId = DebridProviders.TORBOX_ID,
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUrl = verificationUrl,
                    friendlyVerificationUrl = data.friendlyVerificationUrl?.takeIf { it.isNotBlank() } ?: verificationUrl,
                    intervalSeconds = data.interval?.coerceAtLeast(1) ?: 5,
                    expiresAt = data.expiresAt?.takeIf { it.isNotBlank() }
                )
            }
            DebridProviders.PREMIUMIZE_ID -> {
                val clientId = premiumizeClientIdOrThrow()
                val response = premiumizeApi.startDeviceAuthorization(clientId = clientId)
                val data = response.body()?.takeIf { response.isSuccessful } ?: return null
                val deviceCode = data.deviceCode?.takeIf { it.isNotBlank() } ?: return null
                val userCode = data.userCode?.takeIf { it.isNotBlank() } ?: return null
                val verificationUrl = data.verificationUri?.takeIf { it.isNotBlank() } ?: return null
                DebridDeviceAuthorization(
                    providerId = DebridProviders.PREMIUMIZE_ID,
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUrl = verificationUrl,
                    friendlyVerificationUrl = data.verificationUriComplete?.takeIf { it.isNotBlank() } ?: verificationUrl,
                    intervalSeconds = data.interval?.coerceAtLeast(1) ?: 5,
                    expiresAt = data.expiresIn?.takeIf { it > 0 }?.let { "${it}s" }
                )
            }
            else -> null
        }
    }

    suspend fun redeemDeviceAuthorization(providerId: String, deviceCode: String): DebridDeviceAuthorizationTokenResult {
        val normalized = deviceCode.trim()
        if (normalized.isBlank()) return DebridDeviceAuthorizationTokenResult.Failed(null)
        return when (DebridProviders.byId(providerId)?.id) {
            DebridProviders.TORBOX_ID -> {
                val response = torboxApi.redeemDeviceAuthorization(TorboxDeviceTokenRequestDto(normalized))
                torboxDeviceAuthorizationTokenResult(response)
            }
            DebridProviders.PREMIUMIZE_ID -> {
                val clientId = premiumizeClientIdOrThrow()
                val response = premiumizeApi.redeemDeviceAuthorization(
                    deviceCode = normalized,
                    clientId = clientId
                )
                premiumizeDeviceAuthorizationTokenResult(response)
            }
            else -> DebridDeviceAuthorizationTokenResult.Unsupported
        }
    }

    private fun premiumizeClientIdOrThrow(): String =
        BuildConfig.PREMIUMIZE_CLIENT_ID.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Premiumize sign-in is missing PREMIUMIZE_CLIENT_ID.")

    private fun torboxDeviceAuthorizationTokenResult(
        response: retrofit2.Response<TorboxEnvelopeDto<TorboxDeviceTokenDto>>
    ): DebridDeviceAuthorizationTokenResult {
        val envelope = response.body()
        val accessToken = envelope
            ?.takeIf { response.isSuccessful && it.success != false }
            ?.data
            ?.accessToken
            ?.takeIf { it.isNotBlank() }
        if (accessToken != null) {
            return DebridDeviceAuthorizationTokenResult.Authorized(accessToken)
        }
        val message = listOfNotNull(envelope?.error, envelope?.detail, response.errorBody()?.string())
            .joinToString(" ")
            .lowercase()
        return when {
            message.contains("pending") ||
                message.contains("not authorized") ||
                message.contains("not been used") ||
                message.contains("not used yet") ||
                message.contains("scan the code") ->
                DebridDeviceAuthorizationTokenResult.Pending
            message.contains("expired") ->
                DebridDeviceAuthorizationTokenResult.Expired
            response.code() == 404 || response.code() == 409 || response.code() == 425 ->
                DebridDeviceAuthorizationTokenResult.Pending
            response.code() == 410 ->
                DebridDeviceAuthorizationTokenResult.Expired
            else ->
                DebridDeviceAuthorizationTokenResult.Failed(envelope?.detail ?: envelope?.error)
        }
    }

    private fun premiumizeDeviceAuthorizationTokenResult(
        response: retrofit2.Response<PremiumizeDeviceTokenDto>
    ): DebridDeviceAuthorizationTokenResult {
        val body = response.body()
        body?.accessToken?.takeIf { response.isSuccessful && it.isNotBlank() }?.let { accessToken ->
            return DebridDeviceAuthorizationTokenResult.Authorized(accessToken)
        }
        return when (body?.error?.lowercase()) {
            "authorization_pending", "slow_down" -> DebridDeviceAuthorizationTokenResult.Pending
            "invalid_grant", "expired_token" -> DebridDeviceAuthorizationTokenResult.Expired
            "access_denied" -> DebridDeviceAuthorizationTokenResult.Failed(body.errorDescription)
            else -> {
                if (response.code() == 400 && body?.error.isNullOrBlank()) {
                    DebridDeviceAuthorizationTokenResult.Pending
                } else {
                    DebridDeviceAuthorizationTokenResult.Failed(body?.errorDescription ?: body?.error ?: response.errorBody()?.string())
                }
            }
        }
    }

    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch { action() }
    }

    private fun stopFormatterServer() {
        formatterServer?.stop()
        formatterServer = null
    }

    override fun onCleared() {
        stopFormatterServer()
        super.onCleared()
    }
}

data class DebridSettingsUiState(
    val enabled: Boolean = false,
    val cloudLibraryEnabled: Boolean = true,
    val torboxApiKey: String = "",
    val premiumizeApiKey: String = "",
    val realDebridApiKey: String = "",
    val allDebridApiKey: String = "",
    val debridLinkApiKey: String = "",
    val preferredResolverProviderId: String = "",
    val instantPlaybackPreparationLimit: Int = 0,
    val streamMaxResults: Int = 0,
    val streamSortMode: DebridStreamSortMode = DebridStreamSortMode.DEFAULT,
    val streamMinimumQuality: DebridStreamMinimumQuality = DebridStreamMinimumQuality.ANY,
    val streamDolbyVisionFilter: DebridStreamFeatureFilter = DebridStreamFeatureFilter.ANY,
    val streamHdrFilter: DebridStreamFeatureFilter = DebridStreamFeatureFilter.ANY,
    val streamCodecFilter: DebridStreamCodecFilter = DebridStreamCodecFilter.ANY,
    val streamPreferences: DebridStreamPreferences = DebridStreamPreferences(),
    val streamNameTemplate: String = "",
    val streamDescriptionTemplate: String = "",
    val isFormatterQrModeActive: Boolean = false,
    val formatterQrCodeBitmap: Bitmap? = null,
    val formatterServerUrl: String? = null,
    val serverError: String? = null
) {
    val providerApiKeys: Map<String, String>
        get() = mapOf(
            DebridProviders.TORBOX_ID to torboxApiKey,
            DebridProviders.PREMIUMIZE_ID to premiumizeApiKey,
            DebridProviders.REAL_DEBRID_ID to realDebridApiKey,
            DebridProviders.ALL_DEBRID_ID to allDebridApiKey,
            DebridProviders.DEBRID_LINK_ID to debridLinkApiKey
        )

    val hasAnyApiKey: Boolean
        get() = DebridProviders.visible().any { provider -> apiKeyFor(provider.id).isNotBlank() }

    val resolverProviders: List<com.playtorrio.tv.core.debrid.DebridProvider>
        get() = DebridProviders.visible()
            .filter { provider ->
                apiKeyFor(provider.id).isNotBlank() &&
                    (provider.supports(DebridProviderCapability.ClientResolve) ||
                        provider.supports(DebridProviderCapability.LocalTorrentResolve))
            }

    val activeResolverProvider: com.playtorrio.tv.core.debrid.DebridProvider?
        get() = resolverProviders.firstOrNull { it.id == preferredResolverProviderId }
            ?: resolverProviders.firstOrNull()

    val hasResolverProvider: Boolean
        get() = activeResolverProvider != null

    val cloudLibraryProviders: List<com.playtorrio.tv.core.debrid.DebridProvider>
        get() = DebridProviders.visible()
            .filter { provider ->
                apiKeyFor(provider.id).isNotBlank() &&
                    provider.supports(DebridProviderCapability.CloudLibrary)
            }

    val hasCloudLibraryProvider: Boolean
        get() = cloudLibraryProviders.isNotEmpty()

    val canResolvePlayableLinks: Boolean
        get() = enabled && hasResolverProvider

    val canUseCloudLibrary: Boolean
        get() = cloudLibraryEnabled && hasCloudLibraryProvider

    fun apiKeyFor(providerId: String): String =
        providerApiKeys[providerId].orEmpty()

    fun fromSettings(settings: DebridSettings): DebridSettingsUiState = copy(
        enabled = settings.enabled,
        cloudLibraryEnabled = settings.cloudLibraryEnabled,
        torboxApiKey = settings.torboxApiKey,
        premiumizeApiKey = settings.premiumizeApiKey,
        realDebridApiKey = settings.realDebridApiKey,
        allDebridApiKey = settings.allDebridApiKey,
        debridLinkApiKey = settings.debridLinkApiKey,
        preferredResolverProviderId = settings.preferredResolverProviderId,
        instantPlaybackPreparationLimit = settings.instantPlaybackPreparationLimit,
        streamMaxResults = settings.streamMaxResults,
        streamSortMode = settings.streamSortMode,
        streamMinimumQuality = settings.streamMinimumQuality,
        streamDolbyVisionFilter = settings.streamDolbyVisionFilter,
        streamHdrFilter = settings.streamHdrFilter,
        streamCodecFilter = settings.streamCodecFilter,
        streamPreferences = settings.streamPreferences,
        streamNameTemplate = settings.streamNameTemplate,
        streamDescriptionTemplate = settings.streamDescriptionTemplate
    )
}

sealed class DebridSettingsEvent {
    data class ToggleEnabled(val enabled: Boolean) : DebridSettingsEvent()
}
