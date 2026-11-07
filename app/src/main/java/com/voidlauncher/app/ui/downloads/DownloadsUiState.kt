package com.voidlauncher.app.ui.downloads

import com.voidlauncher.app.data.download.DownloadProgress

data class DownloadsUiState(
    val selectedId: String? = null,
    val isDownloading: Boolean = false,
    val progress: DownloadProgress? = null,
    val message: String? = null,
    val isError: Boolean = false,
)
