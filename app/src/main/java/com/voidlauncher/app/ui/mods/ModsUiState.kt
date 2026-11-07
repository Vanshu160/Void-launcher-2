package com.voidlauncher.app.ui.mods

import com.voidlauncher.app.domain.model.InstalledMod
import com.voidlauncher.app.domain.model.ModSearchResult

data class ModsUiState(
    val gameVersion: String? = null,
    val query: String = "",
    val results: List<ModSearchResult> = emptyList(),
    val installed: List<InstalledMod> = emptyList(),
    val isSearching: Boolean = false,
    val installingProjectId: String? = null,
    val message: String? = null,
)
