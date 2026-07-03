package com.example.compiler

import android.content.Context
import com.example.data.Project
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ApkBuilderEngine {

    private const val ORIGINAL_PACKAGE_NAME = "com.aistudio.apkbuilder.xypqrs"
    private const val ORIGINAL_APP_NAME = "APK Builder Studio Workspace Pro"

    fun buildApk(
        context: Context,
        project: Project,
        customPackageName: String,
        customAppName: String,
        onProgress: (String) -> Unit
    ): File? {
        try {
            onProgress("Đang khởi tạo trình biên dịch offline...")
            
            // Format package name to exactly 30 characters
            var paddedPkg = customPackageName.trim()
            if (paddedPkg.length > 30) {
                paddedPkg = paddedPkg.substring(0, 30)
            } else if (paddedPkg.length < 30) {
                paddedPkg = paddedPkg.padEnd(30, '0') // Pad with '0' to be a valid package character
            }
            
            // Format app name to exactly 32 characters
            var paddedAppName = customAppName.trim()
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
                            val newEntry = ZipEntry(name)
                            zos.putNextEntry(newEntry)

                            // Read content
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
                                    // Perform binary replacements of Package Name
                                    entryData = replaceBytes(entryData, origPkgBytesUtf8, repPkgBytesUtf8)
                                    entryData = replaceBytes(entryData, origPkgBytesUtf16, repPkgBytesUtf16)

                                    // Perform binary replacements of App Name
                                    entryData = replaceBytes(entryData, origNameBytesUtf8, repNameBytesUtf8)
                                    entryData = replaceBytes(entryData, origNameBytesUtf16, repNameBytesUtf16)
                                }
                            }

                            zos.write(entryData)
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
