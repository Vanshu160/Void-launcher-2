package com.voidlauncher.app.data.remote

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object MojangVersionApi {
    const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

    /** Downloads the raw manifest JSON. Throws IOException if the request fails. */
    fun fetchManifestJson(): String {
        val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("Mojang returned HTTP $code")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
