package com.voidlauncher.app.data.remote

import com.voidlauncher.app.domain.model.ModDependency
import com.voidlauncher.app.domain.model.ModSearchResult
import com.voidlauncher.app.domain.model.ModVersion
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object ModrinthApi {
    private const val BASE = "https://api.modrinth.com/v2"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** Searches mods that support the given Minecraft version and loader. */
    fun search(query: String, gameVersion: String, loader: String, limit: Int = 20): List<ModSearchResult> {
        val facets = """[["project_type:mod"],["versions:$gameVersion"],["categories:$loader"]]"""
        val url = "$BASE/search?query=${enc(query)}&facets=${enc(facets)}&limit=$limit"
        val hits = JSONObject(Http.getText(url)).getJSONArray("hits")
        return buildList {
            for (i in 0 until hits.length()) {
                val hit = hits.getJSONObject(i)
                add(
                    ModSearchResult(
                        projectId = hit.getString("project_id"),
                        title = hit.optString("title"),
                        description = hit.optString("description"),
                        iconUrl = hit.optString("icon_url").ifBlank { null },
                        downloads = hit.optLong("downloads"),
                    ),
                )
            }
        }
    }

    /** Lists a project's files for one Minecraft version and loader, newest first. */
    fun versionsFor(projectId: String, gameVersion: String, loader: String): List<ModVersion> {
        val loaders = enc("[\"$loader\"]")
        val versions = enc("[\"$gameVersion\"]")
        val url = "$BASE/project/$projectId/version?loaders=$loaders&game_versions=$versions"
        val array = JSONArray(Http.getText(url))
        return buildList {
            for (i in 0 until array.length()) {
                val version = array.getJSONObject(i)
                val files = version.getJSONArray("files")
                if (files.length() == 0) continue

                // Prefer the file Modrinth marks as primary; otherwise take the first.
                var chosen = files.getJSONObject(0)
                for (f in 0 until files.length()) {
                    val candidate = files.getJSONObject(f)
                    if (candidate.optBoolean("primary")) {
                        chosen = candidate
                        break
                    }
                }

                val dependencies = version.optJSONArray("dependencies") ?: JSONArray()
                val deps = buildList {
                    for (d in 0 until dependencies.length()) {
                        val dep = dependencies.getJSONObject(d)
                        if (dep.isNull("project_id")) continue
                        add(
                            ModDependency(
                                projectId = dep.getString("project_id"),
                                required = dep.optString("dependency_type") == "required",
                            ),
                        )
                    }
                }

                add(
                    ModVersion(
                        id = version.getString("id"),
                        fileName = chosen.getString("filename"),
                        fileUrl = chosen.getString("url"),
                        sha1 = chosen.optJSONObject("hashes")?.optString("sha1")?.ifBlank { null },
                        dependencies = deps,
                    ),
                )
            }
        }
    }
}
