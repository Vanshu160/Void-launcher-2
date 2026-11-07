package com.voidlauncher.app.data.remote

import com.voidlauncher.app.domain.model.AssetObject
import com.voidlauncher.app.domain.model.GameFile
import com.voidlauncher.app.domain.model.GameMetadata
import org.json.JSONArray
import org.json.JSONObject

object GameMetadataParser {

    /** The CPU this launcher runs on. Only 64-bit ARM phones are supported for now. */
    private const val DEVICE_ARCH = "arm64"

    /** Parses a version metadata JSON. Throws if the JSON is missing required fields. */
    fun parse(json: String): GameMetadata {
        val root = JSONObject(json)
        val id = root.getString("id")

        val clientJson = root.getJSONObject("downloads").getJSONObject("client")
        val client = GameFile(
            relativePath = "versions/$id/$id.jar",
            url = clientJson.getString("url"),
            sha1 = clientJson.optString("sha1", "").takeIf { it.isNotEmpty() },
        )

        val assetRef = root.getJSONObject("assetIndex")
        val assetIndexId = assetRef.getString("id")
        val assetIndex = GameFile(
            relativePath = "assets/indexes/$assetIndexId.json",
            url = assetRef.getString("url"),
            sha1 = assetRef.optString("sha1", "").takeIf { it.isNotEmpty() },
        )

        val javaMajor = root.optJSONObject("javaVersion")?.optInt("majorVersion", 8) ?: 8

        return GameMetadata(
            id = id,
            client = client,
            libraries = parseLibraries(root.getJSONArray("libraries")),
            assetIndex = assetIndex,
            assetIndexId = assetIndexId,
            javaMajorVersion = javaMajor,
        )
    }

    /** Reads the asset index JSON. Duplicate hashes are returned as-is; callers remove them. */
    fun parseAssetIndex(json: String): List<AssetObject> {
        val objects = JSONObject(json).getJSONObject("objects")
        val names = objects.keys()
        return buildList {
            while (names.hasNext()) {
                val entry = objects.getJSONObject(names.next())
                add(AssetObject(hash = entry.getString("hash"), size = entry.optLong("size")))
            }
        }
    }

    private fun parseLibraries(entries: JSONArray): List<GameFile> {
        val files = mutableListOf<GameFile>()
        for (i in 0 until entries.length()) {
            val library = entries.getJSONObject(i)
            if (!isAllowed(library.optJSONArray("rules"))) continue
            val downloads = library.optJSONObject("downloads") ?: continue

            downloads.optJSONObject("artifact")?.let { files += it.toLibraryFile() }

            // Older versions list native libraries as classifiers, one per OS.
            val classifier = library.optJSONObject("natives")?.optString("linux").orEmpty()
            if (classifier.isNotEmpty()) {
                downloads.optJSONObject("classifiers")?.optJSONObject(classifier)
                    ?.let { files += it.toLibraryFile() }
            }
        }
        return files
    }

    private fun JSONObject.toLibraryFile(): GameFile = GameFile(
        relativePath = "libraries/" + getString("path"),
        url = getString("url"),
        sha1 = optString("sha1", "").takeIf { it.isNotEmpty() },
    )

    /**
     * Mojang rules: the last rule that applies decides. A rule without an OS applies to every OS.
     * Rules gated by features (demo mode, custom resolution) never apply to a launcher install.
     * Android is treated as "linux", and the CPU must match [DEVICE_ARCH] when a rule names one.
     */
    private fun isAllowed(rules: JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.has("features")) continue
            val os = rule.optJSONObject("os")
            val applies = when {
                os == null -> true
                else -> {
                    val nameMatches = !os.has("name") || os.optString("name") == "linux"
                    val archMatches = !os.has("arch") || os.optString("arch") == DEVICE_ARCH
                    nameMatches && archMatches
                }
            }
            if (applies) allowed = rule.getString("action") == "allow"
        }
        return allowed
    }
}
