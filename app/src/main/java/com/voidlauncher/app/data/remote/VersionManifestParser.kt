package com.voidlauncher.app.data.remote

import com.voidlauncher.app.domain.model.MinecraftVersion
import com.voidlauncher.app.domain.model.VersionType
import org.json.JSONObject

object VersionManifestParser {

    /** Turns the manifest JSON into a list. Throws if the JSON is not a valid manifest. */
    fun parse(json: String): List<MinecraftVersion> {
        val entries = JSONObject(json).getJSONArray("versions")
        return buildList {
            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)
                add(
                    MinecraftVersion(
                        id = entry.getString("id"),
                        type = when (entry.optString("type")) {
                            "release" -> VersionType.RELEASE
                            "snapshot" -> VersionType.SNAPSHOT
                            else -> VersionType.OTHER
                        },
                        releaseTime = entry.optString("releaseTime"),
                    ),
                )
            }
        }
    }
}
