package com.voidlauncher.app.domain.model

enum class VersionType { RELEASE, SNAPSHOT, OTHER }

data class MinecraftVersion(
    val id: String,
    val type: VersionType,
    val releaseTime: String,
    /** Link to this version's metadata JSON, from the Mojang manifest. */
    val metadataUrl: String = "",
    /** SHA-1 of the metadata JSON, used to check the download. */
    val metadataSha1: String? = null,
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
