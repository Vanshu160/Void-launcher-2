package com.voidlauncher.app.ui.versions

import com.voidlauncher.app.domain.model.MinecraftVersion
import com.voidlauncher.app.domain.model.VersionFilter

data class VersionsUiState(
    val versions: List<MinecraftVersion> = emptyList(),
    val filter: VersionFilter = VersionFilter.ALL,
    val isLoading: Boolean = false,
    val fromCache: Boolean = false,
    val errorMessage: String? = null,
) {
    val visibleVersions: List<MinecraftVersion>
        get() = versions.filter { filter.matches(it) }
}
