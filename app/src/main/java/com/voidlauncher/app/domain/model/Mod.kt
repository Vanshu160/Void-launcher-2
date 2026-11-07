package com.voidlauncher.app.domain.model

data class ModSearchResult(
    val projectId: String,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val downloads: Long,
)

data class ModDependency(
    val projectId: String,
    val required: Boolean,
)

data class ModVersion(
    val id: String,
    val fileName: String,
    val fileUrl: String,
    val sha1: String?,
    val dependencies: List<ModDependency>,
)

data class InstalledMod(
    val fileName: String,
    val sizeKb: Long,
)
