package com.example.compiler

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

object ApkSigner {

    private fun getOrCreateSigningKey(): Pair<PrivateKey, X509Certificate> {
        val alias = "ApkBuilderSigningKey"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        
        if (!keyStore.containsAlias(alias)) {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 25) }
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setCertificateSubject(X500Principal("CN=APK Builder, O=On-Device Signing"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(start.time)
                .setCertificateNotAfter(end.time)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)
                .build()
            kpg.initialize(spec)
            kpg.generateKeyPair()
        }
        
        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        val cert = keyStore.getCertificate(alias) as X509Certificate
        return Pair(privateKey, cert)
    }

    fun signApk(unsignedApk: File, signedApk: File, onProgress: (String) -> Unit) {
        onProgress("Đang kết nối Android Keystore bảo mật trên thiết bị...")
        val (privateKey, cert) = try {
            getOrCreateSigningKey()
        } catch (e: Exception) {
            throw IllegalStateException("Không thể kết nối hoặc khởi tạo khóa ký mật mã từ Android Keystore cục bộ: ${e.localizedMessage}. Đảm bảo thiết bị của bạn hỗ trợ các thuật toán mã hóa bảo mật phần cứng.", e)
        }

        onProgress("Đang nạp thuật toán mật mã RSA-2048 + SHA-256 hoàn tất...")

        onProgress("Đang đọc các file trong APK không chữ ký...")
        val md = MessageDigest.getInstance("SHA-256")
        val digests = mutableMapOf<String, String>()

        val tempFile = File.createTempFile("unsigned_temp", ".apk")
        tempFile.deleteOnExit()

        ZipInputStream(FileInputStream(unsignedApk)).use { zis ->
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                var entry: ZipEntry? = zis.readEntity()
                while (entry != null) {
                    val name = entry.name
                    // Skip existing signature files
                    if (!name.startsWith("META-INF/")) {
                        val newEntry = ZipEntry(name)
                        zos.putNextEntry(newEntry)

                        val buffer = ByteArray(4096)
                        var len: Int
                        md.reset() // Ensure the message digest is fresh for each entry
                        while (zis.read(buffer).also { len = it } > 0) {
                            zos.write(buffer, 0, len)
                            md.update(buffer, 0, len)
                        }
                        zos.closeEntry()

                        val hashBytes = md.digest()
                        val hashB64 = Base64.encodeToString(hashBytes, Base64.NO_WRAP)
                        digests[name] = hashB64
                    }
                    entry = zis.readEntity()
                }

                onProgress("Đang tạo MANIFEST.MF...")
                val manifestBuilder = StringBuilder()
                manifestBuilder.append("Manifest-Version: 1.0\r\n")
                manifestBuilder.append("Created-By: 1.0 (Android APK Builder)\r\n")
                manifestBuilder.append("Built-By: APK Builder On-Device\r\n\r\n")

                for ((entryName, fileHash) in digests) {
                    manifestBuilder.append("Name: $entryName\r\n")
                    manifestBuilder.append("SHA-256-Digest: $fileHash\r\n\r\n")
                }

                val manifestBytes = manifestBuilder.toString().toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
                zos.write(manifestBytes)
                zos.closeEntry()

                onProgress("Đang tạo CERT.SF...")
                val sfBuilder = StringBuilder()
                sfBuilder.append("Signature-Version: 1.0\r\n")
                sfBuilder.append("Created-By: 1.0 (Android)\r\n")

                val manifestDigest = Base64.encodeToString(md.digest(manifestBytes), Base64.NO_WRAP)
                sfBuilder.append("SHA-256-Digest-Manifest: $manifestDigest\r\n\r\n")

                for ((entryName, fileHash) in digests) {
                    val manifestBlock = "Name: $entryName\r\nSHA-256-Digest: $fileHash\r\n\r\n"
                    val blockBytes = manifestBlock.toByteArray(Charsets.UTF_8)
                    val blockDigest = Base64.encodeToString(md.digest(blockBytes), Base64.NO_WRAP)
                    sfBuilder.append("Name: $entryName\r\n")
                    sfBuilder.append("SHA-256-Digest: $blockDigest\r\n\r\n")
                }

                val sfBytes = sfBuilder.toString().toByteArray(Charsets.UTF_8)
                zos.putNextEntry(ZipEntry("META-INF/CERT.SF"))
                zos.write(sfBytes)
                zos.closeEntry()

                onProgress("Đang ký số file CERT.SF bằng RSA...")
                val signature = Signature.getInstance("SHA256withRSA")
                signature.initSign(privateKey)
                signature.update(sfBytes)
                val signatureBytes = signature.sign()

                onProgress("Đang mã hóa khối chữ ký PKCS#7 CERT.RSA...")
                val certRsaBytes = generatePkcs7SignatureBlock(cert, signatureBytes)
                zos.putNextEntry(ZipEntry("META-INF/CERT.RSA"))
                zos.write(certRsaBytes)
                zos.closeEntry()
            }
        }

        // Overwrite or copy signed file
        if (signedApk.exists()) {
            signedApk.delete()
        }
        tempFile.copyTo(signedApk, overwrite = true)
        tempFile.delete()
        onProgress("Đã ký số thành công APK hoàn chỉnh!")
    }

    private fun ZipInputStream.readEntity(): ZipEntry? {
        return try {
            nextEntry
        } catch (e: Exception) {
            null
        }
    }

    private fun generatePkcs7SignatureBlock(cert: X509Certificate, signatureBytes: ByteArray): ByteArray {
        val digestAlgorithm = Der.sequence(Der.oid("2.16.840.1.101.3.4.2.1"), Der.nullValue()) // SHA-256
        val encryptionAlgorithm = Der.sequence(Der.oid("1.2.840.113549.1.1.1"), Der.nullValue()) // RSA
        
        val issuerAndSerial = Der.sequence(
            Der.raw(cert.issuerX500Principal.encoded),
            Der.integer(cert.serialNumber.toByteArray())
        )
        
        val signerInfo = Der.sequence(
            Der.integer(1), // version
            issuerAndSerial,
            digestAlgorithm,
            encryptionAlgorithm,
            Der.octetString(signatureBytes)
        )

        val digestAlgorithms = Der.set(digestAlgorithm)
        val contentInfo = Der.sequence(Der.oid("1.2.840.113549.1.7.1")) // PKCS7 data
        val certs = Der.explicitTag(0, cert.encoded) // implicit [0] for PKCS7 certificates
        val signerInfos = Der.set(signerInfo)

        val signedData = Der.sequence(
            Der.integer(1), // version
            digestAlgorithms,
            contentInfo,
            certs,
            signerInfos
        )

        return Der.sequence(
            Der.oid("1.2.840.113549.1.7.2"), // PKCS7 signedData
            Der.explicitTag(0, signedData.encode()) // EXPLICIT tag [0]
        ).encode()
    }

    private object Der {
        const val SEQUENCE: Byte = 0x30
        const val SET: Byte = 0x31
        const val INTEGER: Byte = 0x02
        const val OCTET_STRING: Byte = 0x04
        const val OBJECT_IDENTIFIER: Byte = 0x06
        const val NULL: Byte = 0x05

        class Element(val tag: Byte, val content: ByteArray) {
            fun encode(): ByteArray {
                val lenBytes = encodeLength(content.size)
                val result = ByteArray(1 + lenBytes.size + content.size)
                result[0] = tag
                System.arraycopy(lenBytes, 0, result, 1, lenBytes.size)
                System.arraycopy(content, 0, result, 1 + lenBytes.size, content.size)
                return result
            }
        }

        fun sequence(vararg elements: Element) = Element(SEQUENCE, concat(elements.map { it.encode() }))
        fun set(vararg elements: Element) = Element(SET, concat(elements.map { it.encode() }))
        fun raw(bytes: ByteArray) = Element(SEQUENCE, bytes) // Special fallback when we have already DER encoded sub-sequence
        
        fun integer(value: Int): Element {
            val bytes = java.math.BigInteger.valueOf(value.toLong()).toByteArray()
            return Element(INTEGER, bytes)
        }

        fun integer(bytes: ByteArray): Element {
            return Element(INTEGER, bytes)
        }

        fun octetString(bytes: ByteArray) = Element(OCTET_STRING, bytes)
        
        fun oid(oidString: String): Element {
            val parts = oidString.split(".").map { it.toInt() }
            val out = ByteArrayOutputStream()
            out.write(parts[0] * 40 + parts[1])
            for (i in 2 until parts.size) {
                var num = parts[i]
                val temp = ByteArrayOutputStream()
                temp.write(num and 0x7F)
                num = num ushr 7
                while (num > 0) {
                    temp.write((num and 0x7F) or 0x80)
                    num = num ushr 7
                }
                val tempBytes = temp.toByteArray()
                for (j in tempBytes.size - 1 downTo 0) {
                    out.write(tempBytes[j].toInt())
                }
            }
            return Element(OBJECT_IDENTIFIER, out.toByteArray())
        }

        fun nullValue() = Element(NULL, ByteArray(0))

        fun explicitTag(tagNumber: Int, contentBytes: ByteArray): Element {
            val tag = (0xA0 or tagNumber).toByte()
            return Element(tag, contentBytes)
        }

        private fun encodeLength(length: Int): ByteArray {
            if (length < 128) {
                return byteArrayOf(length.toByte())
            }
            var temp = length
            val bytes = ByteArrayOutputStream()
            while (temp > 0) {
                bytes.write(temp and 0xFF)
                temp = temp ushr 8
            }
            val lenBytes = bytes.toByteArray()
            val out = ByteArray(1 + lenBytes.size)
            out[0] = (0x80 or lenBytes.size).toByte()
            for (i in lenBytes.indices) {
                out[1 + i] = lenBytes[lenBytes.size - 1 - i]
            }
            return out
        }

        private fun concat(arrays: List<ByteArray>): ByteArray {
            val total = arrays.sumOf { it.size }
            val out = ByteArray(total)
            var pos = 0
            for (arr in arrays) {
                System.arraycopy(arr, 0, out, pos, arr.size)
                pos += arr.size
            }
            return out
        }
    }
}
