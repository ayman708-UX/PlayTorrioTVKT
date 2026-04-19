package com.playtorrio.tv.ui.screens.stremio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playtorrio.tv.data.stremio.CatalogDeclaration
import com.playtorrio.tv.data.stremio.InstalledAddon
import com.playtorrio.tv.data.stremio.StremioAddonRepository
import com.playtorrio.tv.data.stremio.StremioMetaPreview
import com.playtorrio.tv.data.stremio.StremioService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class StremioCatalogUiState(
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val addonName: String = "",
    val catalogs: List<CatalogDeclaration> = emptyList(),
    val selectedCatalogIndex: Int = 0,
    val selectedExtras: Map<String, String> = emptyMap(),
    val items: List<StremioMetaPreview> = emptyList(),
    val hasMore: Boolean = true
)

class StremioCatalogViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(StremioCatalogUiState())
    val uiState: StateFlow<StremioCatalogUiState> = _uiState

    private var currentAddon: InstalledAddon? = null
    private var currentType: String = "movie"
    private var currentCatalogId: String = ""
    private var currentCatalogs: List<CatalogDeclaration> = emptyList()
    private var currentExtras: Map<String, String> = emptyMap()

    fun load(addonId: String, type: String, catalogId: String) {
        val addon = StremioAddonRepository.getAddons().firstOrNull { it.manifest.id == addonId }
            ?: run {
                _uiState.value = StremioCatalogUiState(
                    isLoading = false,
                    error = "Addon not found",
                    hasMore = false
                )
                return
            }

        currentAddon = addon
        val sameTypeCatalogs = addon.manifest.catalogs.filter { it.type.equals(type, ignoreCase = true) }
        currentCatalogs = if (sameTypeCatalogs.isNotEmpty()) sameTypeCatalogs else addon.manifest.catalogs

        if (currentCatalogs.isEmpty()) {
            _uiState.value = StremioCatalogUiState(
                isLoading = false,
                error = "No catalogs available",
                addonName = addon.manifest.name,
                hasMore = false
            )
            return
        }

        val initialIndex = currentCatalogs.indexOfFirst { it.id == catalogId }.let {
            if (it >= 0) it else 0
        }

        _uiState.value = StremioCatalogUiState(
            isLoading = true,
            addonName = addon.manifest.name,
            catalogs = currentCatalogs,
            selectedCatalogIndex = initialIndex
        )

        selectCatalog(initialIndex)
    }

    fun selectCatalog(index: Int) {
        val addon = currentAddon ?: return
        val catalog = currentCatalogs.getOrNull(index) ?: return

        currentType = catalog.type
        currentCatalogId = catalog.id
        currentExtras = buildDefaultExtras(catalog)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                selectedCatalogIndex = index,
                selectedExtras = currentExtras,
                items = emptyList(),
                hasMore = true
            )

            val first = StremioService.getCatalog(
                addon = addon,
                type = currentType,
                catalogId = currentCatalogId,
                extra = currentExtras
            )?.metas ?: emptyList()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                items = first,
                hasMore = first.isNotEmpty()
            )
        }
    }

    fun setExtraFilter(name: String, value: String?) {
        val addon = currentAddon ?: return
        val state = _uiState.value
        val selectedCatalog = state.catalogs.getOrNull(state.selectedCatalogIndex) ?: return

        val mutable = currentExtras.toMutableMap()
        if (value.isNullOrBlank()) mutable.remove(name) else mutable[name] = value
        currentExtras = mutable

        viewModelScope.launch {
            _uiState.value = state.copy(
                isLoading = true,
                selectedExtras = currentExtras,
                items = emptyList(),
                hasMore = true,
                error = null
            )

            val first = StremioService.getCatalog(
                addon = addon,
                type = selectedCatalog.type,
                catalogId = selectedCatalog.id,
                extra = currentExtras
            )?.metas ?: emptyList()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                items = first,
                hasMore = first.isNotEmpty()
            )
        }
    }

    fun loadMore() {
        val addon = currentAddon ?: return
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            val skip = state.items.size
            val page = StremioService.getCatalog(
                addon = addon,
                type = currentType,
                catalogId = currentCatalogId,
                extra = currentExtras + mapOf("skip" to skip.toString())
            )?.metas ?: emptyList()

            _uiState.value = _uiState.value.copy(
                isLoadingMore = false,
                items = state.items + page,
                hasMore = page.isNotEmpty()
            )
        }
    }

    private fun buildDefaultExtras(catalog: CatalogDeclaration): Map<String, String> {
        val requiredNames = buildSet {
            addAll(catalog.extra.filter { it.isRequired }.map { it.name })
            addAll(catalog.extraRequired.orEmpty())
        }

        return requiredNames.mapNotNull { name ->
            val option = catalog.extra.firstOrNull { it.name == name }?.options?.firstOrNull()
            option?.let { name to it }
        }.toMap()
    }
}
