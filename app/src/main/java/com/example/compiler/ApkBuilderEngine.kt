package com.example.compiler

import android.content.Context
import com.example.data.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ApkBuilderEngine {

    private const val ORIGINAL_PACKAGE_NAME = "com.aistudio.apkbuilder.xypqrs"
    private const val ORIGINAL_APP_NAME = "APK Builder Studio Workspace Pro"

    fun formatAndValidatePackageName(input: String): String {
        var pkg = input.trim().lowercase()
        
        // Default package if empty or completely invalid
        if (pkg.isEmpty()) {
            pkg = "com.aistudio.generated"
        }
        
        // Ensure a dot exists to follow Android package standards
        if (!pkg.contains(".")) {
            pkg = "com.aistudio.$pkg"
        }
        
        // Replace invalid symbols with underscores (only letters, numbers, dots, and underscores allowed)
        pkg = pkg.replace(Regex("[^a-z0-9_.]"), "_")
        
        // Validate starts of individual sections
        val parts = pkg.split(".")
        val cleanedParts = parts.map { part ->
            var cleaned = part
            if (cleaned.isEmpty()) {
                cleaned = "pkg"
            }
            if (!cleaned[0].isLetter()) {
                cleaned = "a$cleaned"
            }
            cleaned
        }
        pkg = cleanedParts.joinToString(".")
        
        // Guarantee exactly 30 characters in length (must match ORIGINAL_PACKAGE_NAME length exactly)
        if (pkg.length > 30) {
            pkg = pkg.substring(0, 30)
            if (pkg.endsWith(".")) {
                pkg = pkg.substring(0, 29) + "a"
            }
        } else if (pkg.length < 30) {
            pkg = pkg.padEnd(30, 'a') // Pad with 'a' which is always valid at the end
        }
        
        // Verify final compliance
        val regex = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")
        if (!regex.matches(pkg)) {
            pkg = "com.aistudio.generated.appaaaa" // Safe fallback matching exactly 30 characters
        }
        
        return pkg
    }

    private fun recalculateDexHeader(dexBytes: ByteArray): ByteArray {
        if (dexBytes.size < 40) return dexBytes
        try {
            // 1. Calculate SHA-1 signature from offset 32 to end of file
            val md = MessageDigest.getInstance("SHA-1")
            md.update(dexBytes, 32, dexBytes.size - 32)
            val sha1 = md.digest()
            System.arraycopy(sha1, 0, dexBytes, 12, 20)
            
            // 2. Calculate Adler32 checksum from offset 12 to end of file
            val adler = java.util.zip.Adler32()
            adler.update(dexBytes, 12, dexBytes.size - 12)
            val checksum = adler.value
            
            // Write Adler32 checksum (little-endian) to header at offset 8
            dexBytes[8] = (checksum and 0xFF).toByte()
            dexBytes[9] = ((checksum ushr 8) and 0xFF).toByte()
            dexBytes[10] = ((checksum ushr 16) and 0xFF).toByte()
            dexBytes[11] = ((checksum ushr 24) and 0xFF).toByte()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return dexBytes
    }

    fun buildApk(
        context: Context,
        project: Project,
        customPackageName: String,
        customAppName: String,
        onProgress: (String) -> Unit
    ): File? {
        try {
            onProgress("Đang khởi tạo trình biên dịch offline...")
            
            val paddedPkg = formatAndValidatePackageName(customPackageName)
            
            // Format app name to exactly 32 characters
            var paddedAppName = customAppName.trim()
            if (paddedAppName.isEmpty()) {
                paddedAppName = "My Custom App"
            }
            if (paddedAppName.length > 32) {
                paddedAppName = paddedAppName.substring(0, 32)
            } else if (paddedAppName.length < 32) {
                paddedAppName = paddedAppName.padEnd(32, ' ') // Pad with space
            }

            onProgress("Package ID mục tiêu: $paddedPkg")
            onProgress("Tên ứng dụng mục tiêu: $paddedAppName")

            val originalApkPath = context.packageCodePath
            val originalApkFile = File(originalApkPath)
            if (!originalApkFile.exists()) {
                onProgress("Lỗi: Không tìm thấy APK gốc của hệ thống.")
                return null
            }

            onProgress("Đang trích xuất APK gốc (${originalApkFile.length() / 1024 / 1024} MB)...")
            val cacheDir = context.cacheDir
            val unsignedApk = File(cacheDir, "unsigned_project_${project.id}.apk")
            val signedApk = File(cacheDir, "build_project_${project.id}.apk")

            if (unsignedApk.exists()) unsignedApk.delete()
            if (signedApk.exists()) signedApk.delete()

            // Prepare replacement byte arrays
            val origPkgBytesUtf8 = ORIGINAL_PACKAGE_NAME.toByteArray(StandardCharsets.UTF_8)
            val origPkgBytesUtf16 = ORIGINAL_PACKAGE_NAME.toByteArray(StandardCharsets.UTF_16LE)
            val repPkgBytesUtf8 = paddedPkg.toByteArray(StandardCharsets.UTF_8)
            val repPkgBytesUtf16 = paddedPkg.toByteArray(StandardCharsets.UTF_16LE)

            // Prepare slashed package name replacements for class definitions inside DEX
            val originalSlashedPkg = ORIGINAL_PACKAGE_NAME.replace('.', '/')
            val paddedSlashedPkg = paddedPkg.replace('.', '/')
            val origSlashedPkgBytesUtf8 = originalSlashedPkg.toByteArray(StandardCharsets.UTF_8)
            val origSlashedPkgBytesUtf16 = originalSlashedPkg.toByteArray(StandardCharsets.UTF_16LE)
            val repSlashedPkgBytesUtf8 = paddedSlashedPkg.toByteArray(StandardCharsets.UTF_8)
            val repSlashedPkgBytesUtf16 = paddedSlashedPkg.toByteArray(StandardCharsets.UTF_16LE)

            val origNameBytesUtf8 = ORIGINAL_APP_NAME.toByteArray(StandardCharsets.UTF_8)
            val origNameBytesUtf16 = ORIGINAL_APP_NAME.toByteArray(StandardCharsets.UTF_16LE)
            val repNameBytesUtf8 = paddedAppName.toByteArray(StandardCharsets.UTF_8)
            val repNameBytesUtf16 = paddedAppName.toByteArray(StandardCharsets.UTF_16LE)

            onProgress("Đang phân tích cấu trúc nhị phân của tệp tài nguyên...")
            ZipInputStream(FileInputStream(originalApkFile)).use { zis ->
                ZipOutputStream(FileOutputStream(unsignedApk)).use { zos ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        
                        // Skip any existing signatures in the template
                        if (!name.startsWith("META-INF/")) {
                            val shouldPatch = name == "assets/config.json" ||
                                              name == "assets/is_built_app.txt" ||
                                              name == "assets/source_code/MainActivity.kt" ||
                                              name == "assets/source_code/layout.xml" ||
                                              name == "AndroidManifest.xml" ||
                                              name == "resources.arsc" ||
                                              name.endsWith(".dex")

                            val originalMethod = entry.method

                            if (shouldPatch) {
                                // Buffer in RAM only the small metadata files that require patching
                                val buffer = ByteArray(4096)
                                var len: Int
                                val byteOut = ByteArrayOutputStream()
                                while (zis.read(buffer).also { len = it } > 0) {
                                    byteOut.write(buffer, 0, len)
                                }
                                var entryData = byteOut.toByteArray()

                                // Patch specific files
                                when {
                                    name == "assets/config.json" -> {
                                        onProgress("Đang cấu hình sơ đồ bố trí widget (assets/config.json)...")
                                        entryData = project.configJson.toByteArray(StandardCharsets.UTF_8)
                                    }
                                    name == "assets/is_built_app.txt" -> {
                                        onProgress("Kích hoạt chế độ Chạy Độc lập (Standalone Mode)...")
                                        entryData = "true".toByteArray(StandardCharsets.UTF_8)
                                    }
                                    name == "assets/source_code/MainActivity.kt" -> {
                                        entryData = project.codeKotlin.toByteArray(StandardCharsets.UTF_8)
                                    }
                                    name == "assets/source_code/layout.xml" -> {
                                        entryData = project.codeLayout.toByteArray(StandardCharsets.UTF_8)
                                    }
                                    name == "AndroidManifest.xml" || name == "resources.arsc" || name.endsWith(".dex") -> {
                                        // Perform binary replacements of Package Name (dot-separated)
                                        entryData = replaceBytes(entryData, origPkgBytesUtf8, repPkgBytesUtf8)
                                        entryData = replaceBytes(entryData, origPkgBytesUtf16, repPkgBytesUtf16)

                                        // Perform binary replacements of Package Name (slash-separated class references)
                                        entryData = replaceBytes(entryData, origSlashedPkgBytesUtf8, repSlashedPkgBytesUtf8)
                                        entryData = replaceBytes(entryData, origSlashedPkgBytesUtf16, repSlashedPkgBytesUtf16)

                                        // Perform binary replacements of App Name
                                        entryData = replaceBytes(entryData, origNameBytesUtf8, repNameBytesUtf8)
                                        entryData = replaceBytes(entryData, origNameBytesUtf16, repNameBytesUtf16)

                                        if (name.endsWith(".dex")) {
                                            entryData = recalculateDexHeader(entryData)
                                        }
                                    }
                                }

                                val newEntry = ZipEntry(name).apply {
                                    method = ZipEntry.DEFLATED
                                }
                                zos.putNextEntry(newEntry)
                                zos.write(entryData)
                            } else {
                                // Stream non-patched resources directly to avoid bloating memory (O(1) memory complexity)
                                val newEntry = ZipEntry(name).apply {
                                    method = ZipEntry.DEFLATED
                                }
                                zos.putNextEntry(newEntry)

                                val buffer = ByteArray(4096)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    zos.write(buffer, 0, len)
                                }
                            }
                            zos.closeEntry()
                        }
                        entry = zis.nextEntry
                    }

                    // Dynamically inject files if they didn't exist in the host APK
                    // Just in case, write the config and marker files explicitly at the end
                    writeExtraFile(zos, "assets/config.json", project.configJson.toByteArray(StandardCharsets.UTF_8))
                    writeExtraFile(zos, "assets/is_built_app.txt", "true".toByteArray(StandardCharsets.UTF_8))
                    writeExtraFile(zos, "assets/source_code/MainActivity.kt", project.codeKotlin.toByteArray(StandardCharsets.UTF_8))
                    writeExtraFile(zos, "assets/source_code/layout.xml", project.codeLayout.toByteArray(StandardCharsets.UTF_8))
                }
            }

            onProgress("Tiến hành ký số gói tin APK...")
            ApkSigner.signApk(unsignedApk, signedApk) { step ->
                onProgress(step)
            }

            // Cleanup
            if (unsignedApk.exists()) {
                unsignedApk.delete()
            }

            onProgress("Biên dịch thành công! APK đã sẵn sàng để cài đặt.")
            return signedApk
        } catch (e: Exception) {
            onProgress("Lỗi biên dịch: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun writeExtraFile(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        try {
            val entry = ZipEntry(name)
            zos.putNextEntry(entry)
            zos.write(bytes)
            zos.closeEntry()
        } catch (e: Exception) {
            // Already written, ignore
        }
    }

    private fun replaceBytes(data: ByteArray, target: ByteArray, replacement: ByteArray): ByteArray {
        if (target.size != replacement.size || data.isEmpty()) return data
        val out = data.clone()
        var i = 0
        val targetSize = target.size
        val dataSize = out.size
        while (i <= dataSize - targetSize) {
            var match = true
            for (j in 0 until targetSize) {
                if (out[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                for (j in 0 until targetSize) {
                    out[i + j] = replacement[j]
                }
                i += targetSize
            } else {
                i++
            }
        }
        return out
    }
}
