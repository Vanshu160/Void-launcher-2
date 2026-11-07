package com.voidlauncher.app.data.runtime

import java.io.File
import java.io.InputStream
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory

/** Checks SHA256withRSA signatures against the publisher's certificate. */
class SignatureVerifier(private val publicKey: PublicKey) {

    fun verify(file: File, signature: ByteArray): Boolean {
        val check = Signature.getInstance("SHA256withRSA")
        check.initVerify(publicKey)
        file.inputStream().use { input ->
            val buffer = ByteArray(65535)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                check.update(buffer, 0, read)
            }
        }
        return check.verify(signature)
    }

    companion object {
        fun fromPem(stream: InputStream): SignatureVerifier {
            val certificate = CertificateFactory.getInstance("X.509").generateCertificate(stream)
            return SignatureVerifier(certificate.publicKey)
        }
    }
}

/** Reads the "file:base64-signature" lines that the runtime host publishes. */
object RuntimeSignatures {
    fun decodeBundle(text: String): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        for (line in text.lines()) {
            val parts = line.trim().split(":")
            if (parts.size != 2) continue
            val bytes = try {
                java.util.Base64.getMimeDecoder().decode(parts[1])
            } catch (e: IllegalArgumentException) {
                continue
            }
            // An RSA-4096 signature is always 512 bytes.
            if (bytes.size == 512) result[parts[0]] = bytes
        }
        return result
    }
}
