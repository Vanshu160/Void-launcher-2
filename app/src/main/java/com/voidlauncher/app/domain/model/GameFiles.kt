package com.voidlauncher.app.domain.model

/** A file the game needs, stored at [relativePath] inside the Minecraft folder. */
data class GameFile(
    val relativePath: String,
    val url: String,
    val sha1: String?,
)

/** What one Minecraft version needs, read from its metadata JSON. */
data class GameMetadata(
    val id: String,
    val client: GameFile,
    val libraries: List<GameFile>,
    val assetIndex: GameFile,
    val assetIndexId: String,
    val javaMajorVersion: Int,
)

/** One entry of the asset index. Assets are stored by hash, not by name. */
data class AssetObject(val hash: String, val size: Long)
