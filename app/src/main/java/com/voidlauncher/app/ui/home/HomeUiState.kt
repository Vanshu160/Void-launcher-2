package com.voidlauncher.app.ui.home

data class HomeUiState(
    val selectedVersion: String? = null,
    val canPlay: Boolean = false,
    val statusMessage: String = "Choose a version to get started.",
)
