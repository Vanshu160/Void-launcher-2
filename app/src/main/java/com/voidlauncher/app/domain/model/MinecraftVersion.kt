package com.voidlauncher.app.domain.model

enum class VersionType { RELEASE, SNAPSHOT, OTHER }

data class MinecraftVersion(
    val id: String,
    val type: VersionType,
    val releaseTime: String,
)

enum class VersionFilter(val label: String) {
    ALL("All"),
    RELEASE("Release"),
    SNAPSHOT("Snapshot");

    fun matches(version: MinecraftVersion): Boolean = when (this) {
        ALL -> true
        RELEASE -> version.type == VersionType.RELEASE
        SNAPSHOT -> version.type == VersionType.SNAPSHOT
    }
}
