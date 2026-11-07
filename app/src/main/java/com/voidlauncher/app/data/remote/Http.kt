package com.voidlauncher.app.data.remote

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Http {
    // Modrinth requires an identifying User-Agent on every request.
    const val USER_AGENT = "VoidLauncher/0.4.0 (https://github.com/Vanshu160/Void-launcher-2)"

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        return connection
    }

    fun getText(url: String): String {
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code from $url")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Downloads a file to a temporary name, checks its SHA-1 if one is given,
     * and only then moves it into place. A bad download never replaces a good file.
     */
    fun download(url: String, destination: File, expectedSha1: String?) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".part")
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code while downloading ${destination.name}")

            val digest = MessageDigest.getInstance("SHA-1")
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha1 != null && !actual.equals(expectedSha1, ignoreCase = true)) {
                temp.delete()
                throw IOException("Checksum did not match for ${destination.name}")
            }

            if (destination.exists()) destination.delete()
            if (!temp.renameTo(destination)) {
                temp.delete()
                throw IOException("Could not save ${destination.name}")
            }
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    }
}
