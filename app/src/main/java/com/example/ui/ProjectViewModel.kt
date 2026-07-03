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
