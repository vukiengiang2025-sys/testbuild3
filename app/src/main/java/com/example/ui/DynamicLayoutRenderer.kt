package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

// =========================================================================
// LAYER 1: ADVANCED SCHEMA, DEPENDENCY, & SEMANTIC VALIDATOR
// =========================================================================
object SchemaValidator {
    data class Issue(val level: Level, val message: String) {
        enum class Level { INFO, WARNING, ERROR }
    }

    // TẦNG 1: STATIC SEMANTIC VALIDATION (PHÂN TÍCH TĨNH)
    fun validate(configJson: String): List<Issue> {
        val issues = mutableListOf<Issue>()
        try {
            if (configJson.isBlank()) {
                issues.add(Issue(Issue.Level.ERROR, "Mã JSON trống rỗng."))
                return issues
            }
            val json = JSONObject(configJson)
            val appName = json.optString("appName")
            if (appName.isNullOrBlank()) {
                issues.add(Issue(Issue.Level.WARNING, "Trường 'appName' bị thiếu hoặc trống. Sử dụng tên mặc định."))
            }

            val widgets = json.optJSONArray("widgets")
            if (widgets == null) {
                issues.add(Issue(Issue.Level.ERROR, "Thiếu mảng 'widgets' cấu hình gốc."))
                return issues
            }

            if (widgets.length() == 0) {
                issues.add(Issue(Issue.Level.WARNING, "Không tìm thấy thành phần (widgets) nào được khai báo."))
            }

            val definedStateIds = mutableSetOf<String>()
            val declaredTypes = mutableMapOf<String, String>() // id -> type
            
            // First pass: collect all state definitions, IDs and types
            collectStatesAndTypes(widgets, definedStateIds, declaredTypes, issues)

            // Second pass: static semantic checks & action dependency analysis
            analyzeSemanticDependencies(widgets, definedStateIds, declaredTypes, issues)

            // Third pass: Static Circular Dependency Graph cycle checking (Dependency Graph Resolver)
            checkCircularDependencies(widgets, issues)

        } catch (e: Exception) {
            issues.add(Issue(Issue.Level.ERROR, "Lỗi cú pháp JSON: ${e.localizedMessage}"))
        }
        return issues
    }

    private fun collectStatesAndTypes(
        widgets: JSONArray,
        definedStateIds: MutableSet<String>,
        declaredTypes: MutableMap<String, String>,
        issues: MutableList<Issue>
    ) {
        for (i in 0 until widgets.length()) {
            val widget = widgets.optJSONObject(i) ?: continue
            val type = widget.optString("type")
            val id = widget.optString("id")
            val stateId = widget.optString("stateId")

            if (type.isNotEmpty()) {
                val allowedTypes = listOf(
                    "title", "text", "spacer", "text-field", "button", 
                    "counter-buttons", "card", "divider", "badge", "switch", "row"
                )
                if (type !in allowedTypes) {
                    issues.add(Issue(Issue.Level.ERROR, "Mục $i: Loại thành phần '$type' không được hỗ trợ bởi Dynamic Engine."))
                }
            }

            if (id.isNotEmpty()) {
                if (declaredTypes.containsKey(id)) {
                    issues.add(Issue(Issue.Level.ERROR, "Mục $i: Trùng lặp mã định danh ID '$id' trong sơ đồ."))
                } else {
                    declaredTypes[id] = type
                    definedStateIds.add(id)
                }
            }

            if (stateId.isNotEmpty()) {
                if (definedStateIds.contains(stateId) && id != stateId) {
                    issues.add(Issue(Issue.Level.WARNING, "Mục $i: Trạng thái phản ứng 'stateId' ($stateId) trùng khớp với một mã ID đã khai báo."))
                } else {
                    definedStateIds.add(stateId)
                    declaredTypes[stateId] = "state_holder"
                }
            }

            // Recurse into nested container widgets (e.g. Card, Row)
            if (type == "card" || type == "row") {
                val nestedChildren = widget.optJSONArray("children")
                if (nestedChildren != null) {
                    collectStatesAndTypes(nestedChildren, definedStateIds, declaredTypes, issues)
                }
            }
        }
    }

    private fun analyzeSemanticDependencies(
        widgets: JSONArray,
        definedStateIds: Set<String>,
        declaredTypes: Map<String, String>,
        issues: MutableList<Issue>
    ) {
        for (i in 0 until widgets.length()) {
            val widget = widgets.optJSONObject(i) ?: continue
            val type = widget.optString("type")
            val text = widget.optString("text")

            // Check placeholders {state_id}
            if (text.isNotEmpty()) {
                val placeholders = findPlaceholders(text)
                for (ph in placeholders) {
                    if (ph !in definedStateIds && ph != "counter") {
                        issues.add(Issue(
                            Issue.Level.WARNING,
                            "Thành phần '$type' tham chiếu tới trạng thái không tồn tại: '{$ph}'"
                        ))
                    }
                }
            }

            // Check Action Semantic Target
            if (type == "button") {
                val action = widget.optString("action")
                if (action.isNotEmpty()) {
                    val safety = ActionSandbox.checkSafety(action)
                    if (!safety.isSafe) {
                        issues.add(Issue(Issue.Level.ERROR, "Sự kiện nút bấm không an toàn: ${safety.reason}"))
                    }

                    // Numeric checking on math operations
                    if (action.startsWith("increment:") || action.startsWith("decrement:")) {
                        val targetState = action.split(":")[1].trim()
                        val targetType = declaredTypes[targetState]
                        if (targetState in definedStateIds && targetType != "counter-buttons" && targetType != "state_holder" && targetType != "text-field") {
                            issues.add(Issue(
                                Issue.Level.INFO,
                                "Tác vụ toán học ($action) nhắm vào '$targetState' (kiểu: $targetType) vốn không chuyên dùng làm biến số."
                            ))
                        }
                    }

                    // Check age calculation bindings
                    if (action.startsWith("calculate_age:")) {
                        val parts = action.split(":")
                        if (parts.size >= 3) {
                            val inputId = parts[1].trim()
                            val outputId = parts[2].trim()
                            if (inputId !in definedStateIds) {
                                issues.add(Issue(Issue.Level.ERROR, "Tác vụ tính tuổi: Không tìm thấy trường nhập năm sinh '$inputId' trong sơ đồ."))
                            }
                            if (outputId !in definedStateIds) {
                                issues.add(Issue(Issue.Level.WARNING, "Tác vụ tính tuổi: Biến kết quả đầu ra '$outputId' chưa được liên kết hiển thị."))
                            }
                        }
                    }
                }
            }

            // Nested container validations
            if (type == "card" || type == "row") {
                val nestedChildren = widget.optJSONArray("children")
                if (nestedChildren != null) {
                    analyzeSemanticDependencies(nestedChildren, definedStateIds, declaredTypes, issues)
                }
            }
        }
    }

    // LỚP KIỂM TRA PHỤ THUỘC TUẦN HOÀN TĨNH (Dependency Graph Resolver)
    private fun checkCircularDependencies(widgets: JSONArray, issues: MutableList<Issue>) {
        val adjList = mutableMapOf<String, MutableSet<String>>()

        // Traverse widgets to build edges
        fun buildGraph(arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val widget = arr.optJSONObject(i) ?: continue
                val type = widget.optString("type")
                
                if (type == "button") {
                    val action = widget.optString("action")
                    if (action.startsWith("calculate_age:")) {
                        val parts = action.split(":")
                        if (parts.size >= 3) {
                            val inputId = parts[1].trim()
                            val outputId = parts[2].trim()
                            adjList.getOrPut(inputId) { mutableSetOf() }.add(outputId)
                        }
                    } else if (action.startsWith("set_state:")) {
                        val parts = action.split(":")
                        if (parts.size >= 3) {
                            val targetId = parts[1].trim()
                            val value = parts.drop(2).joinToString(":").trim()
                            val placeholders = findPlaceholders(value)
                            for (ph in placeholders) {
                                adjList.getOrPut(ph) { mutableSetOf() }.add(targetId)
                            }
                        }
                    }
                }

                if (type == "card" || type == "row") {
                    val children = widget.optJSONArray("children")
                    if (children != null) {
                        buildGraph(children)
                    }
                }
            }
        }

        buildGraph(widgets)

        // Find cycles using DFS
        val visited = mutableMapOf<String, Int>() // 0: unvisited, 1: visiting, 2: visited
        val cyclePath = mutableListOf<String>()

        fun dfs(node: String): Boolean {
            visited[node] = 1
            cyclePath.add(node)
            val neighbors = adjList[node] ?: emptySet()
            for (neighbor in neighbors) {
                val state = visited[neighbor] ?: 0
                if (state == 1) {
                    cyclePath.add(neighbor)
                    return true
                } else if (state == 0) {
                    if (dfs(neighbor)) return true
                }
            }
            visited[node] = 2
            cyclePath.removeAt(cyclePath.size - 1)
            return false
        }

        for (node in adjList.keys) {
            if ((visited[node] ?: 0) == 0) {
                cyclePath.clear()
                if (dfs(node)) {
                    val cycleStr = cyclePath.joinToString(" -> ")
                    issues.add(Issue(
                        Issue.Level.ERROR,
                        "Phát hiện liên kết tuần hoàn tĩnh (Circular Dependency): $cycleStr. Hãy rà soát lại liên kết biến để tránh vòng lặp vô tận."
                    ))
                    break // Report first cycle found
                }
            }
        }
    }

    private fun findPlaceholders(input: String): List<String> {
        val list = mutableListOf<String>()
        var i = 0
        while (i < input.length) {
            val start = input.indexOf('{', i)
            if (start == -1) break
            val end = input.indexOf('}', start)
            if (end == -1) break
            val ph = input.substring(start + 1, end).trim()
            if (ph.isNotEmpty()) {
                list.add(ph)
            }
            i = end + 1
        }
        return list
    }
}

// =========================================================================
// LAYER 2: SECURITY ACTION SANDBOX
// =========================================================================
object ActionSandbox {
    data class SafetyResult(val isSafe: Boolean, val reason: String? = null)

    fun checkSafety(actionStr: String): SafetyResult {
        val lower = actionStr.lowercase().trim()

        // Sandbox strict restrictions to reduce exploit surface
        val dangerousKeywords = listOf(
            "class.", "java.", "reflect", "system.", "runtime", "process", 
            "exec", "file://", "content://", "sh ", "bash", "su ", "root",
            "loadlibrary", "getmethod", "getconstructor", "declaredfield",
            "android.os", "dex", "apk", "permission", "package", "database", "sqlite",
            "../", "private", "thread", "handler", "looper"
        )

        for (kw in dangerousKeywords) {
            if (lower.contains(kw)) {
                return SafetyResult(false, "Từ khóa bị cấm trong Sandbox an toàn: '$kw'")
            }
        }

        // Deep link redirection protocol safety check
        if (lower.startsWith("open_url:")) {
            val url = actionStr.substring(9).trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return SafetyResult(false, "Giao thức URL không được hỗ trợ. Chỉ chấp nhận các kết nối web an toàn (http/https).")
            }
        }

        return SafetyResult(true)
    }
}

// =========================================================================
// LAYER 3: LOW-CODE ACTION ENGINE (WITH EXECUTION POLICY & RATE LIMITS)
// =========================================================================
object ActionEngine {
    private const val MAX_CALL_DEPTH = 8
    private const val MAX_FLOW_STEPS = 25
    private const val MAX_FLOW_DURATION_MS = 500
    private const val MAX_KEY_MUTATIONS = 3

    private var currentExecutionDepth = 0
    private var lastExecutionTime = 0L
    private var currentFlowSteps = 0
    private var currentFlowStartTime = 0L
    private val modifiedKeysInCurrentFlow = mutableListOf<String>()

    fun execute(
        context: Context,
        actionStr: String,
        stateMap: MutableMap<String, String>,
        resolveText: (String) -> String,
        onLog: (String) -> Unit,
        onStateDelta: (String, String, String) -> Unit,
        declaredPermissions: Set<String> = emptySet(),
        isContinuous: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val limit = if (isContinuous) 5L else 150L
        if (now - lastExecutionTime < limit) {
            if (!isContinuous) {
                onLog("[HỆ THỐNG] Chặn sự kiện: Thao tác quá nhanh (Rate limit ${limit}ms active).")
            }
            return
        }
        lastExecutionTime = now

        // Initialize execution context on flow entry
        if (currentExecutionDepth == 0) {
            currentFlowSteps = 0
            currentFlowStartTime = now
            modifiedKeysInCurrentFlow.clear()
        }

        currentFlowSteps++

        // Execution step limit guard
        if (currentFlowSteps > MAX_FLOW_STEPS) {
            onLog("[CẢNH BÁO] Ngắt tiến trình: Vượt giới hạn các bước xử lý ($MAX_FLOW_STEPS bước) trong cùng một luồng.")
            Toast.makeText(context, "Hệ thống ngắt luồng xử lý do vượt định ngạch tối đa!", Toast.LENGTH_SHORT).show()
            return
        }

        // Execution duration limit guard
        if (now - currentFlowStartTime > MAX_FLOW_DURATION_MS) {
            onLog("[CẢNH BÁO] Ngắt tiến trình: Thời gian thực thi vượt quá ${MAX_FLOW_DURATION_MS}ms.")
            return
        }

        // Depth guard
        if (currentExecutionDepth >= MAX_CALL_DEPTH) {
            onLog("[CẢNH BÁO] Phát hiện vòng lặp vô hạn! Ngắt luồng xử lý do vượt quá độ sâu callstack ($MAX_CALL_DEPTH).")
            Toast.makeText(context, "Phát hiện vòng lặp vô hạn. Đã ngắt tiến trình!", Toast.LENGTH_SHORT).show()
            return
        }

        currentExecutionDepth++
        val resolvedAction = resolveText(actionStr).trim()
        if (resolvedAction.isEmpty()) {
            currentExecutionDepth--
            return
        }

        // Dynamic loop & recursive variable mutation protection (Circular mutation)
        var mutatedKey: String? = null
        if (resolvedAction.startsWith("set_state:")) {
            val parts = resolvedAction.split(":")
            if (parts.size >= 3) {
                mutatedKey = parts[1].trim()
            }
        } else if (resolvedAction.startsWith("increment:") || resolvedAction.startsWith("decrement:")) {
            mutatedKey = resolvedAction.substring(resolvedAction.indexOf(":") + 1).trim()
        } else if (resolvedAction.startsWith("calculate_age:")) {
            val parts = resolvedAction.split(":")
            if (parts.size >= 3) {
                mutatedKey = parts[2].trim()
            }
        }

        if (mutatedKey != null) {
            val occurrences = modifiedKeysInCurrentFlow.count { it == mutatedKey }
            if (occurrences >= MAX_KEY_MUTATIONS) {
                onLog("[BẢO MẬT] Ngắt tiến trình: Phát hiện vòng lặp biến số tuần hoàn trên biến '$mutatedKey'.")
                Toast.makeText(context, "Ngăn chặn vòng lặp tuần hoàn trên biến '$mutatedKey'!", Toast.LENGTH_SHORT).show()
                currentExecutionDepth--
                return
            }
            modifiedKeysInCurrentFlow.add(mutatedKey)
        }

        onLog("[ENGINE] Thực thi lệnh: $resolvedAction")
        
        val safetyResult = ActionSandbox.checkSafety(resolvedAction)
        if (!safetyResult.isSafe) {
            val blockMsg = "Khối Sandbox ngăn chặn hành vi không an toàn: ${safetyResult.reason}"
            onLog("[BẢO MẬT] $blockMsg")
            Toast.makeText(context, blockMsg, Toast.LENGTH_LONG).show()
            currentExecutionDepth--
            return
        }

        try {
            when {
                resolvedAction.startsWith("toast:") -> {
                    val message = resolvedAction.substring(6).trim()
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    onLog("[TOAST] Hiển thị: \"$message\"")
                }
                
                resolvedAction.startsWith("reset:") -> {
                    val stateId = resolvedAction.substring(6).trim()
                    val old = stateMap[stateId] ?: "Chưa tạo"
                    stateMap[stateId] = "0"
                    onStateDelta(stateId, old, "0")
                    onLog("[STATE] Khởi tạo lại '$stateId' = '0'")
                }

                resolvedAction.startsWith("set_state:") -> {
                    val parts = resolvedAction.split(":")
                    if (parts.size >= 3) {
                        val key = parts[1].trim()
                        val value = parts.drop(2).joinToString(":").trim()
                        val old = stateMap[key] ?: "Chưa tạo"
                        stateMap[key] = value
                        onStateDelta(key, old, value)
                        onLog("[STATE] Gán '$key' = '$value'")
                    }
                }

                resolvedAction.startsWith("increment:") -> {
                    val stateId = resolvedAction.substring(10).trim()
                    val old = stateMap[stateId] ?: "0"
                    val cur = old.toIntOrNull()
                    
                    // TẦNG 2: DYNAMIC SEMANTIC VALIDATION (Dữ liệu phi số)
                    if (cur == null) {
                        onLog("[LỖI ĐỘNG - TYPE MISMATCH] Không thể tăng biến số '$stateId' vì giá trị hiện tại là phi số: '$old'")
                        Toast.makeText(context, "Lỗi kiểu dữ liệu động trên biến '$stateId'!", Toast.LENGTH_SHORT).show()
                    } else {
                        val newVal = (cur + 1).toString()
                        stateMap[stateId] = newVal
                        onStateDelta(stateId, old, newVal)
                        onLog("[STATE] Tăng biến số '$stateId': $cur -> $newVal")
                    }
                }

                resolvedAction.startsWith("decrement:") -> {
                    val stateId = resolvedAction.substring(10).trim()
                    val old = stateMap[stateId] ?: "0"
                    val cur = old.toIntOrNull()
                    
                    // TẦNG 2: DYNAMIC SEMANTIC VALIDATION (Dữ liệu phi số)
                    if (cur == null) {
                        onLog("[LỖI ĐỘNG - TYPE MISMATCH] Không thể giảm biến số '$stateId' vì giá trị hiện tại là phi số: '$old'")
                        Toast.makeText(context, "Lỗi kiểu dữ liệu động trên biến '$stateId'!", Toast.LENGTH_SHORT).show()
                    } else {
                        val newVal = (cur - 1).toString()
                        stateMap[stateId] = newVal
                        onStateDelta(stateId, old, newVal)
                        onLog("[STATE] Giảm biến số '$stateId': $cur -> $newVal")
                    }
                }

                resolvedAction.startsWith("open_url:") -> {
                    // Capability-Based Permission Guard
                    if (!declaredPermissions.contains("open_external_urls")) {
                        onLog("[BẢO MẬT] Ngăn chặn tác vụ: Mở liên kết ngoài yêu cầu quyền 'open_external_urls' trong Schema.")
                        Toast.makeText(context, "Thiếu quyền 'open_external_urls' để thực hiện hành động này!", Toast.LENGTH_LONG).show()
                    } else {
                        val url = resolvedAction.substring(9).trim()
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        onLog("[BẢO MẬT] Đã mở URL an toàn: $url")
                    }
                }

                resolvedAction.startsWith("calculate_age:") -> {
                    val parts = resolvedAction.split(":")
                    if (parts.size >= 3) {
                        val inputId = parts[1].trim()
                        val outputId = parts[2].trim()
                        val inputValStr = stateMap[inputId] ?: ""
                        val inputVal = inputValStr.toIntOrNull()
                        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                        val oldOutput = stateMap[outputId] ?: "0"
                        
                        if (inputVal != null) {
                            if (inputVal in 1900..currentYear) {
                                val age = currentYear - inputVal
                                stateMap[outputId] = age.toString()
                                onStateDelta(outputId, oldOutput, age.toString())
                                onLog("[LOGIC] Tính tuổi: Năm sinh $inputVal -> $age tuổi.")
                            } else {
                                stateMap[outputId] = "N/A"
                                onStateDelta(outputId, oldOutput, "N/A")
                                onLog("[LỖI ĐỘNG - VALUE OUT OF RANGE] Năm sinh $inputVal nằm ngoài dải hợp lệ (1900..$currentYear).")
                                Toast.makeText(context, "Năm sinh ngoài dải hợp lệ!", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            stateMap[outputId] = "N/A"
                            onStateDelta(outputId, oldOutput, "N/A")
                            onLog("[LỖI ĐỘNG - TYPE MISMATCH] Trường nhập năm sinh '$inputId' chứa giá trị phi số: '$inputValStr'")
                            Toast.makeText(context, "Năm sinh phải là một số!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                
                else -> {
                    onLog("[ENGINE] Lệnh chưa được định nghĩa: $resolvedAction")
                }
            }
        } catch (e: Exception) {
            val err = "Lỗi trong quá trình xử lý Action: ${e.localizedMessage}"
            onLog("[THẤT BẠI] $err")
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        } finally {
            currentExecutionDepth--
        }
    }
}

// =========================================================================
// DATA STRUCTURE FOR DYNAMIC STATE TIME-TRAVEL TRAIL
// =========================================================================
data class StateDelta(
    val timestamp: String,
    val key: String,
    val oldValue: String,
    val newValue: String
)

// =========================================================================
// MAIN RENDER WINDOW WITH MULTI-LAYER DEBUGGER & COMPONENT ENGINE
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicLayoutRenderer(
    configJson: String,
    modifier: Modifier = Modifier,
    isPreviewMode: Boolean = false
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 1. Reactive Global State Table (Reactive Database Local)
    val stateMap = remember { mutableStateMapOf<String, String>() }
    
    // 2. High-Fidelity Execution Logs Trace
    val consoleLogs = remember { mutableStateListOf<String>() }
    
    // 3. Low-Memory State Delta Trace (Time-Travel Trail)
    val deltaTrace = remember { mutableStateListOf<StateDelta>() }
    
    // 4. Panel UI Expansion States
    var isDiagnosticsExpanded by remember { mutableStateOf(false) }

    // Parse dynamic JSON layout tree
    val config = remember(configJson) {
        try {
            JSONObject(configJson)
        } catch (e: Exception) {
            null
        }
    }

    // Static Analysis Validation Pipeline
    val validationIssues = remember(configJson) {
        SchemaValidator.validate(configJson)
    }

    if (config == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF12141C))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Lỗi Cú Pháp JSON Hoặc Mã Nguồn Bị Hỏng",
                    fontSize = 18.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hãy kiểm tra lại định dạng dấu phẩy, đóng ngoặc móc '{}' hoặc cấu trúc mảng 'widgets' trong tab Mã Nguồn.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val appName = config.optString("appName", "Ứng dụng của tôi")
    val accentColorHex = config.optString("accentColor", "#00FF88")
    val backgroundColorHex = config.optString("backgroundColor", "#12141C")

    val accentColor = remember(accentColorHex) {
        try { Color(android.graphics.Color.parseColor(accentColorHex)) } catch (e: Exception) { Color(0xFF00FF88) }
    }

    val backgroundColor = remember(backgroundColorHex) {
        try { Color(android.graphics.Color.parseColor(backgroundColorHex)) } catch (e: Exception) { Color(0xFF12141C) }
    }

    val widgetsArray = remember(config) {
        try { config.optJSONArray("widgets") } catch (e: Exception) { null }
    }

    // 5. Capability-Based Permissions List parsed from Schema
    val permissionsList = remember(configJson) {
        val list = mutableSetOf<String>()
        try {
            val arr = JSONObject(configJson).optJSONArray("permissions")
            if (arr != null) {
                for (idx in 0 until arr.length()) {
                    list.add(arr.getString(idx).trim())
                }
            }
        } catch (e: Exception) {}
        list
    }

    // Dynamic variable resolver template helper
    fun resolveText(rawText: String): String {
        var text = rawText
        stateMap.forEach { (key, value) ->
            text = text.replace("{$key}", value)
        }
        return text
    }

    // Upgraded Delta Tracking with Compaction & Quotas (Time-Travel RAM guard)
    fun addDeltaTrace(key: String, old: String, new: String) {
        val calendar = Calendar.getInstance()
        val ts = String.format(Locale.getDefault(), "%02d:%02d:%02d.%03d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND),
            calendar.get(Calendar.MILLISECOND)
        )
        
        // Delta Compaction: aggregate consecutive changes on the same key
        if (deltaTrace.isNotEmpty() && deltaTrace.last().key == key) {
            val lastItem = deltaTrace.last()
            deltaTrace[deltaTrace.size - 1] = StateDelta(ts, key, lastItem.oldValue, new)
        } else {
            deltaTrace.add(StateDelta(ts, key, old, new))
        }

        // Capacity quota to prevent OOM
        if (deltaTrace.size > 50) {
            deltaTrace.removeAt(0)
        }
    }

    // Safe logging with truncation limit (Resource Quota Manager)
    val appendLog = remember {
        { msg: String ->
            consoleLogs.add(msg)
            if (consoleLogs.size > 100) {
                consoleLogs.removeAt(0)
            }
            Unit
        }
    }

    // Auto-discover state mappings from Schema Parse Tree configurations
    LaunchedEffect(configJson) {
        stateMap.clear()
        deltaTrace.clear()
        
        fun initializeStates(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val widget = arr.optJSONObject(i) ?: continue
                val type = widget.optString("type")
                val id = widget.optString("id")
                val stateId = widget.optString("stateId")

                if (type == "text-field" && id.isNotEmpty()) {
                    stateMap[id] = ""
                }
                if (type == "switch" && id.isNotEmpty()) {
                    stateMap[id] = "false"
                }
                if (type == "counter-buttons" && stateId.isNotEmpty()) {
                    stateMap[stateId] = "0"
                }
                
                // Nesting card/container check
                if (type == "card" || type == "row") {
                    val childArray = widget.optJSONArray("children")
                    initializeStates(childArray)
                }
            }
        }
        
        initializeStates(widgetsArray)
        if (!stateMap.containsKey("counter")) stateMap["counter"] = "0"
        if (!stateMap.containsKey("age_result")) stateMap["age_result"] = "0"
        
        consoleLogs.clear()
        consoleLogs.add("[SYSTEM] Khởi động dynamic engine runtime...")
        consoleLogs.add("[SYSTEM] Nạp thành công app: $appName")
        consoleLogs.add("[SYSTEM] Khai báo quyền: ${if (permissionsList.isEmpty()) "Không" else permissionsList.joinToString(", ")}")
        consoleLogs.add("[SYSTEM] Phát hiện ${validationIssues.filter { it.level == SchemaValidator.Issue.Level.ERROR }.size} lỗi và ${validationIssues.filter { it.level == SchemaValidator.Issue.Level.WARNING }.size} cảnh báo cấu trúc.")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = appName,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor.copy(alpha = 0.95f)
                )
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundColor)
        ) {
            // Main Renderer Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    if (isPreviewMode) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = accentColor.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .border(1.dp, accentColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.TabletAndroid,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "GIẢ LẬP TRỰC TIẾP",
                                        color = accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Chế độ Sandbox",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Render dynamic elements
                    if (widgetsArray == null || widgetsArray.length() == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Không tìm thấy thành phần giao diện. Hãy nạp các thẻ 'widgets' trong mã nguồn JSON.",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        RenderWidgetsList(
                            widgetsArray = widgetsArray,
                            stateMap = stateMap,
                            accentColor = accentColor,
                            accentColorHex = accentColorHex,
                            resolveText = ::resolveText,
                            onLog = appendLog,
                            onStateDelta = ::addDeltaTrace,
                            context = context,
                            declaredPermissions = permissionsList
                        )
                    }
                }
            }

            // Upgraded Advanced Diagnostics with Sơ đồ / Logs / Time-Travel
            if (isPreviewMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0D14)),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1F2232), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Diagnostic Toggle bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDiagnosticsExpanded = !isDiagnosticsExpanded }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (validationIssues.any { it.level == SchemaValidator.Issue.Level.ERROR }) Icons.Rounded.Cancel else Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = if (validationIssues.any { it.level == SchemaValidator.Issue.Level.ERROR }) Color(0xFFFF5252) else Color(0xFF00FF88),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Bảng Điều Khiển Engine & Sandbox State",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (validationIssues.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${validationIssues.size} phát hiện",
                                            color = Color(0xFFFFB300),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Icon(
                                    imageVector = if (isDiagnosticsExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }

                        // Expanded Diagnostic Hub
                        AnimatedVisibility(visible = isDiagnosticsExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                var selectedSubTab by remember { mutableStateOf(0) } // 0: AST, 1: Sandbox Logs, 2: State Store Delta

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    AssistChip(
                                        onClick = { selectedSubTab = 0 },
                                        label = { Text("Kiểm Định Sơ Đồ") },
                                        leadingIcon = { Icon(Icons.Rounded.FactCheck, null, modifier = Modifier.size(13.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            labelColor = if (selectedSubTab == 0) Color(0xFF00FF88) else Color.Gray
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AssistChip(
                                        onClick = { selectedSubTab = 1 },
                                        label = { Text("Sandbox Trace") },
                                        leadingIcon = { Icon(Icons.Rounded.Terminal, null, modifier = Modifier.size(13.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            labelColor = if (selectedSubTab == 1) Color(0xFF00FF88) else Color.Gray
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AssistChip(
                                        onClick = { selectedSubTab = 2 },
                                        label = { Text("State Store Delta (${deltaTrace.size})") },
                                        leadingIcon = { Icon(Icons.Rounded.History, null, modifier = Modifier.size(13.dp)) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            labelColor = if (selectedSubTab == 2) Color(0xFF00FF88) else Color.Gray
                                        )
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF05060A))
                                        .border(1.dp, Color(0xFF1B1E29), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    when (selectedSubTab) {
                                        0 -> {
                                            // Schema Tree / Dependency Check
                                            if (validationIssues.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "✓ Không phát hiện lỗi tĩnh hay xung đột trạng thái.",
                                                        color = Color(0xFF00FF88),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            } else {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .verticalScroll(rememberScrollState()),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    validationIssues.forEach { issue ->
                                                        val color = when (issue.level) {
                                                            SchemaValidator.Issue.Level.ERROR -> Color(0xFFFF5252)
                                                            SchemaValidator.Issue.Level.WARNING -> Color(0xFFFFB300)
                                                            SchemaValidator.Issue.Level.INFO -> Color(0xFF00E5FF)
                                                        }
                                                        Row(verticalAlignment = Alignment.Top) {
                                                            Text(
                                                                text = "[${issue.level}] ",
                                                                color = color,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 10.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                            Text(
                                                                text = issue.message,
                                                                color = Color.LightGray,
                                                                fontSize = 10.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        1 -> {
                                            // Logs Trace
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                consoleLogs.forEach { log ->
                                                    val color = when {
                                                        log.contains("[BẢO MẬT]") || log.contains("[LỖI]") || log.contains("[THẤT BẠI]") -> Color(0xFFFF5252)
                                                        log.contains("[STATE]") -> Color(0xFFCE93D8)
                                                        log.contains("[LOGIC]") -> Color(0xFF80DEEA)
                                                        else -> Color(0xFFA0A5B5)
                                                    }
                                                    Text(
                                                        text = log,
                                                        color = color,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                        2 -> {
                                            // Low-memory Delta Trace Tab
                                            if (deltaTrace.isEmpty()) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Nhật ký State Store rỗng. Hãy thay đổi trạng thái của các widgets.",
                                                        color = Color.Gray,
                                                        fontSize = 11.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            } else {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .verticalScroll(rememberScrollState()),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    deltaTrace.forEach { delta ->
                                                        Text(
                                                            text = "[${delta.timestamp}] state '${delta.key}': ${delta.oldValue} -> ${delta.newValue}",
                                                            color = Color(0xFF00FF88),
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// RECURSIVE LAYOUT BUILDER & RENDERING PIPELINE
// =========================================================================
@Composable
fun RenderWidgetsList(
    widgetsArray: JSONArray,
    stateMap: MutableMap<String, String>,
    accentColor: Color,
    accentColorHex: String,
    resolveText: (String) -> String,
    onLog: (String) -> Unit,
    onStateDelta: (String, String, String) -> Unit,
    context: Context,
    declaredPermissions: Set<String> = emptySet()
) {
    for (i in 0 until widgetsArray.length()) {
        val widget = widgetsArray.optJSONObject(i) ?: continue
        val type = widget.optString("type")

        when (type) {
            "title" -> {
                val text = widget.optString("text", "Tiêu đề")
                val size = widget.optDouble("size", 24.0).toFloat()
                val colorHex = widget.optString("color", accentColorHex)
                val alignStr = widget.optString("align", "left")

                val textColor = remember(colorHex) {
                    try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color.White }
                }
                val align = when (alignStr) {
                    "center" -> TextAlign.Center
                    "right" -> TextAlign.Right
                    else -> TextAlign.Left
                }

                Text(
                    text = resolveText(text),
                    fontSize = size.sp,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = align,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            "text" -> {
                val text = widget.optString("text", "")
                val size = widget.optDouble("size", 14.0).toFloat()
                val colorHex = widget.optString("color", "#A0A5B5")
                val alignStr = widget.optString("align", "left")
                val isBold = widget.optString("weight") == "bold"

                val textColor = remember(colorHex) {
                    try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color.LightGray }
                }
                val align = when (alignStr) {
                    "center" -> TextAlign.Center
                    "right" -> TextAlign.Right
                    else -> TextAlign.Left
                }

                Text(
                    text = resolveText(text),
                    fontSize = size.sp,
                    color = textColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = align,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                )
            }

            "spacer" -> {
                val size = widget.optInt("size", 16)
                Spacer(modifier = Modifier.height(size.dp))
            }

            "divider" -> {
                val colorHex = widget.optString("color", "#252A3A")
                val thickness = widget.optInt("thickness", 1)
                val resolvedColor = remember(colorHex) {
                    try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { Color.Gray }
                }
                HorizontalDivider(
                    thickness = thickness.dp,
                    color = resolvedColor,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            "badge" -> {
                val label = widget.optString("text", "BADGE")
                val colorHex = widget.optString("color", "#00FF88")
                val resolvedColor = remember(colorHex) {
                    try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { accentColor }
                }
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(resolvedColor.copy(alpha = 0.15f))
                        .border(1.dp, resolvedColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = resolveText(label).uppercase(Locale.getDefault()),
                        color = resolvedColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            "text-field" -> {
                val id = widget.optString("id", "input")
                val label = widget.optString("label", "Nhập dữ liệu")
                val placeholder = widget.optString("placeholder", "")

                val textValue = stateMap[id] ?: ""

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newValue ->
                        val old = stateMap[id] ?: ""
                        stateMap[id] = newValue
                        onStateDelta(id, old, newValue)
                    },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        focusedLabelColor = accentColor,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            "switch" -> {
                val id = widget.optString("id")
                val label = widget.optString("label", "Tùy chọn")
                val valStr = stateMap[id] ?: "false"
                val isChecked = valStr.toBoolean()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = label, color = Color.White, fontSize = 14.sp)
                    Switch(
                        checked = isChecked,
                        onCheckedChange = { checkState ->
                            stateMap[id] = checkState.toString()
                            onStateDelta(id, isChecked.toString(), checkState.toString())
                            onLog("[STATE] Thay đổi Switch '$id' = $checkState")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = accentColor,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }

            "button" -> {
                val text = widget.optString("text", "Bấm nút")
                val action = widget.optString("action", "")
                val colorHex = widget.optString("color", accentColorHex)

                val buttonColor = remember(colorHex) {
                    try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { accentColor }
                }

                Button(
                    onClick = {
                        ActionEngine.execute(context, action, stateMap, resolveText, onLog, onStateDelta, declaredPermissions)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = if (buttonColor == Color.White || buttonColor == Color(0xFF00FF88) || buttonColor == Color(0xFF00E5FF)) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = resolveText(text),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            "counter-buttons" -> {
                val stateId = widget.optString("stateId", "counter")
                val colorHex = widget.optString("color", accentColorHex)

                val btnColor = remember(colorHex) {
                    try { Color(android.graphics.Color.parseColor(colorHex)) } catch (e: Exception) { accentColor }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val curVal = stateMap[stateId] ?: "0"
                            val cur = curVal.toIntOrNull() ?: 0
                            val newVal = (cur - 1).toString()
                            stateMap[stateId] = newVal
                            onStateDelta(stateId, curVal, newVal)
                            onLog("[STATE] Giảm '$stateId': $cur -> $newVal")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = btnColor, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text("- Giảm", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val curVal = stateMap[stateId] ?: "0"
                            val cur = curVal.toIntOrNull() ?: 0
                            val newVal = (cur + 1).toString()
                            stateMap[stateId] = newVal
                            onStateDelta(stateId, curVal, newVal)
                            onLog("[STATE] Tăng '$stateId': $cur -> $newVal")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = btnColor, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text("+ Tăng", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            "card" -> {
                val cardColorHex = widget.optString("color", "#1C1F2B")
                val children = widget.optJSONArray("children")
                val resolvedCardColor = remember(cardColorHex) {
                    try { Color(android.graphics.Color.parseColor(cardColorHex)) } catch (e: Exception) { Color(0xFF1C1F2B) }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = resolvedCardColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        if (children != null) {
                            RenderWidgetsList(
                                widgetsArray = children,
                                stateMap = stateMap,
                                accentColor = accentColor,
                                accentColorHex = accentColorHex,
                                resolveText = resolveText,
                                onLog = onLog,
                                onStateDelta = onStateDelta,
                                context = context,
                                declaredPermissions = declaredPermissions
                            )
                        }
                    }
                }
            }

            "row" -> {
                val children = widget.optJSONArray("children")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (children != null) {
                        for (idx in 0 until children.length()) {
                            val child = children.optJSONObject(idx) ?: continue
                            Box(modifier = Modifier.weight(1f)) {
                                val singleChildArray = remember { JSONArray().apply { put(child) } }
                                RenderWidgetsList(
                                    widgetsArray = singleChildArray,
                                    stateMap = stateMap,
                                    accentColor = accentColor,
                                    accentColorHex = accentColorHex,
                                    resolveText = resolveText,
                                    onLog = onLog,
                                    onStateDelta = onStateDelta,
                                    context = context,
                                    declaredPermissions = declaredPermissions
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
