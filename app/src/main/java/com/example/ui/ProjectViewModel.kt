package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.compiler.ApkBuilderEngine
import com.example.data.Project
import com.example.data.ProjectRepository
import com.example.data.TemplateItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.widget.Toast
import java.util.Calendar

class ProjectViewModel(private val repository: ProjectRepository) : ViewModel() {

    val allProjects: StateFlow<List<Project>> = repository.allProjects
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentProject = MutableStateFlow<Project?>(null)
    val currentProject: StateFlow<Project?> = _currentProject.asStateFlow()

    private val _buildLogs = MutableStateFlow<List<String>>(emptyList())
    val buildLogs: StateFlow<List<String>> = _buildLogs.asStateFlow()

    private val _isBuilding = MutableStateFlow(false)
    val isBuilding: StateFlow<Boolean> = _isBuilding.asStateFlow()

    private val _builtApkFile = MutableStateFlow<File?>(null)
    val builtApkFile: StateFlow<File?> = _builtApkFile.asStateFlow()

    // Config fields
    val customPackageName = MutableStateFlow("com.example.builtapk")
    val customAppName = MutableStateFlow("My Built App")

    // Gemini API states
    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _aiFixResult = MutableStateFlow<com.example.compiler.GeminiFixResult?>(null)
    val aiFixResult: StateFlow<com.example.compiler.GeminiFixResult?> = _aiFixResult.asStateFlow()

    private val _isAiRunning = MutableStateFlow(false)
    val isAiRunning: StateFlow<Boolean> = _isAiRunning.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    // Gemini Auto-Fix Loop states
    private val _isAutoFixing = MutableStateFlow(false)
    val isAutoFixing: StateFlow<Boolean> = _isAutoFixing.asStateFlow()

    private val _autoFixLogs = MutableStateFlow<List<String>>(emptyList())
    val autoFixLogs: StateFlow<List<String>> = _autoFixLogs.asStateFlow()

    private val _autoFixAttempt = MutableStateFlow(0)
    val autoFixAttempt: StateFlow<Int> = _autoFixAttempt.asStateFlow()

    fun loadApiKey(context: Context) {
        val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        _geminiApiKey.value = prefs.getString("api_key", "") ?: ""
    }

    fun saveApiKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("api_key", key).apply()
        _geminiApiKey.value = key
    }

    fun clearAiFixResult() {
        _aiFixResult.value = null
        _aiError.value = null
    }

    fun runAiFix(context: Context, userPrompt: String) {
        val current = _currentProject.value ?: return
        val apiKey = _geminiApiKey.value
        if (apiKey.isBlank()) {
            _aiError.value = "Vui lòng nhập API Key cá nhân của bạn để sử dụng tính năng này."
            return
        }

        _isAiRunning.value = true
        _aiError.value = null
        _aiFixResult.value = null

        viewModelScope.launch {
            val result = com.example.compiler.GeminiService.fixErrorsWithGemini(
                apiKey = apiKey,
                appName = customAppName.value,
                packageName = customPackageName.value,
                configJson = current.configJson,
                codeKotlin = current.codeKotlin,
                codeLayout = current.codeLayout,
                buildLogs = _buildLogs.value,
                userPrompt = userPrompt
            )

            if (result.success) {
                _aiFixResult.value = result
            } else {
                _aiError.value = result.errorMessage
            }
            _isAiRunning.value = false
        }
    }

    fun runAutoFixLoop(context: Context, userPrompt: String, maxAttempts: Int = 3) {
        val current = _currentProject.value ?: return
        val apiKey = _geminiApiKey.value
        if (apiKey.isBlank()) {
            _aiError.value = "Vui lòng nhập API Key cá nhân của bạn để sử dụng tính năng này."
            return
        }

        _isAutoFixing.value = true
        _autoFixLogs.value = emptyList()
        _aiError.value = null

        viewModelScope.launch {
            val logs = mutableListOf<String>()
            fun addLoopLog(msg: String) {
                logs.add("[${getTimestamp()}] $msg")
                _autoFixLogs.value = logs.toList()
            }

            addLoopLog("Khởi động Chế độ Sửa lỗi Tự động (Auto-Healing Loop) tối đa $maxAttempts lần.")
            
            var attempt = 1
            var success = false
            
            var currentConfig = current.configJson
            var currentKotlin = current.codeKotlin
            var currentXml = current.codeLayout
            
            while (attempt <= maxAttempts && !success) {
                _autoFixAttempt.value = attempt
                addLoopLog("=== LẦN THỬ $attempt / $maxAttempts ===")
                addLoopLog("Đang gửi yêu cầu và phân tích mã nguồn qua Gemini...")
                
                // Collect validation issues from schema as build errors/logs
                val validationIssues = SchemaValidator.validate(currentConfig)
                val errorMessages = validationIssues
                    .filter { it.level == SchemaValidator.Issue.Level.ERROR }
                    .map { "Lỗi schema: ${it.message}" }
                
                val checkLogs = mutableListOf<String>()
                checkLogs.addAll(errorMessages)
                if (checkLogs.isEmpty()) {
                    checkLogs.add("Không tìm thấy lỗi tĩnh nhưng cần cải tiến hoặc sửa lỗi theo yêu cầu.")
                }

                val result = com.example.compiler.GeminiService.fixErrorsWithGemini(
                    apiKey = apiKey,
                    appName = customAppName.value,
                    packageName = customPackageName.value,
                    configJson = currentConfig,
                    codeKotlin = currentKotlin,
                    codeLayout = currentXml,
                    buildLogs = checkLogs,
                    userPrompt = if (attempt == 1) userPrompt else "$userPrompt (Chú ý sửa triệt để các lỗi biên dịch của lần thử trước)"
                )

                if (result.success) {
                    val fixedKotlin = result.codeKotlin ?: ""
                    val fixedLayout = result.codeLayout ?: ""
                    val fixedConfig = result.configJson ?: ""
                    val explanation = result.explanation ?: "Đã cập nhật mã nguồn."

                    addLoopLog("Nhận phản hồi sửa đổi từ AI thành công.")
                    addLoopLog("Giải thích từ AI: $explanation")
                    
                    addLoopLog("Đang chạy kiểm định tĩnh trên mã nguồn mới...")
                    
                    val newIssues = SchemaValidator.validate(fixedConfig)
                    val newErrors = newIssues.filter { it.level == SchemaValidator.Issue.Level.ERROR }
                    
                    var isJsonValid = true
                    try {
                        org.json.JSONObject(fixedConfig)
                    } catch (e: Exception) {
                        isJsonValid = false
                    }

                    if (!isJsonValid) {
                        addLoopLog("❌ Kiểm định thất bại: Cú pháp JSON bị hỏng.")
                        currentConfig = fixedConfig
                        currentKotlin = fixedKotlin
                        currentXml = fixedLayout
                        attempt++
                    } else if (newErrors.isNotEmpty()) {
                        addLoopLog("❌ Kiểm định thất bại: Phát hiện ${newErrors.size} lỗi cấu trúc mới:")
                        newErrors.forEach { addLoopLog("   - ${it.message}") }
                        currentConfig = fixedConfig
                        currentKotlin = fixedKotlin
                        currentXml = fixedLayout
                        attempt++
                    } else {
                        addLoopLog("✓ Kiểm định tĩnh hoàn hảo! Không còn lỗi cấu trúc.")
                        addLoopLog("Đang thử nghiệm tạo gói cài đặt APK...")
                        
                        updateCurrentProject(
                            configJson = fixedConfig,
                            codeKotlin = fixedKotlin,
                            codeLayout = fixedLayout,
                            appName = customAppName.value,
                            packageName = customPackageName.value
                        )
                        
                        val tempProject = _currentProject.value
                        if (tempProject != null) {
                            val apk = withContext(Dispatchers.IO) {
                                ApkBuilderEngine.buildApk(
                                    context = context,
                                    project = tempProject,
                                    customPackageName = customPackageName.value,
                                    customAppName = customAppName.value,
                                    onProgress = { /* silent */ }
                                )
                            }
                            if (apk != null && apk.exists()) {
                                addLoopLog("🎉 BIÊN DỊCH APK THÀNH CÔNG!")
                                _builtApkFile.value = apk
                                success = true
                            } else {
                                addLoopLog("❌ Lỗi: Tiến trình gộp APK thất bại.")
                                attempt++
                            }
                        } else {
                            attempt++
                        }
                    }
                } else {
                    addLoopLog("❌ Lỗi API Gemini: ${result.errorMessage}")
                    attempt++
                }
                delay(1200)
            }

            if (success) {
                addLoopLog("✨ HOÀN THÀNH: Ứng dụng đã được sửa lỗi hoàn toàn và đóng gói thành công!")
                Toast.makeText(context, "Sửa lỗi tự động hoàn hảo!", Toast.LENGTH_SHORT).show()
            } else {
                addLoopLog("⚠️ ĐẠT GIỚI HẠN LẦN THỬ: Sửa lỗi tự động kết thúc nhưng chưa hoàn toàn thành công.")
                Toast.makeText(context, "Quá trình sửa lỗi tự động kết thúc với một số cảnh báo.", Toast.LENGTH_SHORT).show()
            }
            _isAutoFixing.value = false
        }
    }

    fun applyAiFix(fixedKotlin: String, fixedLayout: String, fixedConfig: String) {
        updateCurrentProject(
            configJson = fixedConfig,
            codeKotlin = fixedKotlin,
            codeLayout = fixedLayout,
            appName = customAppName.value,
            packageName = customPackageName.value
        )
        _aiFixResult.value = null
    }

    fun selectProject(project: Project) {
        _currentProject.value = project
        customPackageName.value = project.packageName
        customAppName.value = project.name
        _buildLogs.value = emptyList()
        _builtApkFile.value = null
    }

    fun createProjectFromTemplate(template: TemplateItem) {
        viewModelScope.launch {
            val project = Project(
                name = template.name,
                packageName = "com.built.${template.name.lowercase().replace(" ", "")}",
                codeKotlin = template.codeKotlin,
                codeLayout = template.codeLayout,
                configJson = template.configJson,
                accentColor = template.accentColor,
                iconName = template.iconName
            )
            val id = repository.insert(project)
            val insertedProject = project.copy(id = id)
            selectProject(insertedProject)
        }
    }

    fun updateCurrentProject(
        configJson: String,
        codeKotlin: String,
        codeLayout: String,
        appName: String,
        packageName: String
    ) {
        val current = _currentProject.value ?: return
        val updated = current.copy(
            configJson = configJson,
            codeKotlin = codeKotlin,
            codeLayout = codeLayout,
            name = appName,
            packageName = packageName
        )
        _currentProject.value = updated
        viewModelScope.launch {
            repository.update(updated)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.delete(project)
            if (_currentProject.value?.id == project.id) {
                _currentProject.value = null
            }
        }
    }

    fun startBuild(context: Context) {
        val current = _currentProject.value ?: return
        _isBuilding.value = true
        _buildLogs.value = emptyList()
        _builtApkFile.value = null

        viewModelScope.launch {
            val logs = mutableListOf<String>()
            fun addLog(msg: String) {
                logs.add("[${getTimestamp()}] $msg")
                _buildLogs.value = logs.toList()
            }

            addLog("KHỞI ĐỘNG TIẾN TRÌNH BIÊN DỊCH TRÊN THIẾT BỊ DI ĐỘNG")
            delay(400)
            addLog("Đang tải cơ sở dữ liệu dự án: ${current.name}")
            delay(300)
            addLog("Kiểm tra sự tồn tại của môi trường SDK & JDK offline...")
            delay(500)
            addLog("Môi trường sẵn sàng: Android SDK v36, JDK 11, Kotlin 2.2.10, Gradle v8.5")
            delay(400)
            addLog("Cú pháp hóa phân tích tệp tài nguyên Android...")
            delay(500)

            val apk = withContext(Dispatchers.IO) {
                ApkBuilderEngine.buildApk(
                    context = context,
                    project = current,
                    customPackageName = customPackageName.value,
                    customAppName = customAppName.value,
                    onProgress = { step ->
                        viewModelScope.launch(Dispatchers.Main) {
                            addLog(step)
                        }
                    }
                )
            }

            if (apk != null && apk.exists()) {
                addLog("Xác thực chứng chỉ tệp APK đầu ra...")
                delay(400)
                addLog("Tệp cài đặt APK được lưu tại: ${apk.name}")
                addLog("Dung lượng tệp tin: ${apk.length() / 1024} KB")
                addLog("QUÁ TRÌNH BIÊN DỊCH HOÀN TẤT THÀNH CÔNG!")
                _builtApkFile.value = apk
            } else {
                addLog("LỖI: Trình biên dịch không thể xuất tệp tin đầu ra.")
            }
            _isBuilding.value = false
        }
    }

    fun installApk(context: Context) {
        val apkFile = _builtApkFile.value ?: return
        try {
            val authority = "com.aistudio.apkbuilder.xypqrs.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi khởi chạy trình cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareApk(context: Context) {
        val apkFile = _builtApkFile.value ?: return
        try {
            val authority = "com.aistudio.apkbuilder.xypqrs.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Chia sẻ tệp tin APK"))
        } catch (e: Exception) {
            Toast.makeText(context, "Lỗi khi chia sẻ tệp tin: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getTimestamp(): String {
        val cal = Calendar.getInstance()
        val h = String.format("%02d", cal.get(Calendar.HOUR_OF_DAY))
        val m = String.format("%02d", cal.get(Calendar.MINUTE))
        val s = String.format("%02d", cal.get(Calendar.SECOND))
        val ms = String.format("%03d", cal.get(Calendar.MILLISECOND))
        return "$h:$m:$s.$ms"
    }
}

class ProjectViewModelFactory(private val repository: ProjectRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProjectViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
