package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class BuildStageStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}

data class BuildStage(
    val title: String,
    val description: String,
    val checkKeywords: List<String>,
    val completeKeywords: List<String>,
    var status: BuildStageStatus = BuildStageStatus.PENDING
)

@Composable
fun BuildStatusLogger(
    buildLogs: List<String>,
    isBuilding: Boolean,
    builtApkFileExists: Boolean,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()

    // Determine the stages dynamically from logs
    val stages = remember(buildLogs) {
        listOf(
            BuildStage(
                title = "Khởi Tạo Hệ Thống",
                description = "Thiết lập môi trường SDK & kiểm tra cơ sở dữ liệu.",
                checkKeywords = listOf("KHỞI ĐỘNG", "khởi tạo", "Kiểm tra sự tồn tại"),
                completeKeywords = listOf("Đang trích xuất APK gốc")
            ),
            BuildStage(
                title = "Trích Xuất APK Gốc",
                description = "Giải nén nhị phân ứng dụng chủ để chuẩn bị vá.",
                checkKeywords = listOf("Đang trích xuất APK gốc"),
                completeKeywords = listOf("Đang phân tích cấu trúc")
            ),
            BuildStage(
                title = "Vá Nhị Phân & Mã Nguồn",
                description = "Thay thế Package Name, App Name & nhúng bố cục XML.",
                checkKeywords = listOf("Đang phân tích cấu trúc", "Đang cấu hình sơ đồ", "Kích hoạt chế độ Chạy Độc lập"),
                completeKeywords = listOf("Tiến hành ký số", "Đang kết nối Android Keystore")
            ),
            BuildStage(
                title = "Ký Số APK (JAR v1)",
                description = "Liên kết KeyStore bảo mật và ký số APK bằng chứng chỉ RSA.",
                checkKeywords = listOf("Tiến hành ký số", "Đang kết nối Android Keystore", "Đọc các file trong APK", "tạo MANIFEST", "tạo CERT.SF", "ký số file CERT.SF", "mã hóa khối chữ ký PKCS#7"),
                completeKeywords = listOf("Đã ký số thành công", "Xác thực chứng chỉ", "Biên dịch thành công")
            ),
            BuildStage(
                title = "Kiểm Tra & Xuất Bản",
                description = "Tính toán checksum DEX, đo kích thước file & xuất bản APK.",
                checkKeywords = listOf("Xác thực chứng chỉ", "Dung lượng tệp tin"),
                completeKeywords = listOf("QUÁ TRÌNH BIÊN DỊCH HOÀN TẤT THÀNH CÔNG")
            )
        )
    }

    // Determine state of each stage based on logs
    var currentRunningStageIndex = -1
    var hasError = false
    var errorMessage = ""

    // Scan logs to determine the status of each stage
    for (log in buildLogs) {
        if (log.contains("LỖI", ignoreCase = true) || log.contains("Lỗi biên dịch", ignoreCase = true) || log.contains("Không thể kết nối hoặc khởi tạo", ignoreCase = true)) {
            hasError = true
            errorMessage = log
        }
    }

    stages.forEachIndexed { index, stage ->
        val started = buildLogs.any { log -> stage.checkKeywords.any { kw -> log.contains(kw, ignoreCase = true) } }
        val completed = buildLogs.any { log -> stage.completeKeywords.any { kw -> log.contains(kw, ignoreCase = true) } }

        stage.status = when {
            completed -> BuildStageStatus.SUCCESS
            started && hasError && index == currentRunningStageIndex -> BuildStageStatus.FAILED
            started -> {
                currentRunningStageIndex = index
                if (hasError) {
                    BuildStageStatus.FAILED
                } else {
                    BuildStageStatus.RUNNING
                }
            }
            else -> BuildStageStatus.PENDING
        }
    }

    // If build succeeded overall
    if (builtApkFileExists && !isBuilding) {
        stages.forEach { it.status = BuildStageStatus.SUCCESS }
    }

    // Auto-scroll terminal logs to bottom
    LaunchedEffect(buildLogs.size) {
        if (buildLogs.isNotEmpty()) {
            lazyListState.animateScrollToItem(buildLogs.size - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // STEPPER PROGRESS HEADER
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131622)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF232A45), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "TIẾN TRÌNH BIÊN DỊCH REAL-TIME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8C9AB5),
                    letterSpacing = 1.sp
                )

                stages.forEachIndexed { index, stage ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Step Icon / Status Indicator
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when (stage.status) {
                                        BuildStageStatus.SUCCESS -> Color(0xFF00FF88).copy(alpha = 0.15f)
                                        BuildStageStatus.RUNNING -> Color(0xFF00B0FF).copy(alpha = 0.15f)
                                        BuildStageStatus.FAILED -> Color(0xFFFF3D00).copy(alpha = 0.15f)
                                        BuildStageStatus.PENDING -> Color(0xFF1F2538)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (stage.status) {
                                BuildStageStatus.SUCCESS -> Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Thành công",
                                    tint = Color(0xFF00FF88),
                                    modifier = Modifier.size(14.dp)
                                )
                                BuildStageStatus.RUNNING -> CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF00B0FF),
                                    modifier = Modifier.size(12.dp)
                                )
                                BuildStageStatus.FAILED -> Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Thất bại",
                                    tint = Color(0xFFFF3D00),
                                    modifier = Modifier.size(14.dp)
                                )
                                BuildStageStatus.PENDING -> Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF5E6E8C))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stage.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (stage.status) {
                                    BuildStageStatus.SUCCESS -> Color(0xFF00FF88)
                                    BuildStageStatus.RUNNING -> Color(0xFF00B0FF)
                                    BuildStageStatus.FAILED -> Color(0xFFFF3D00)
                                    BuildStageStatus.PENDING -> Color(0xFF8C9AB5)
                                }
                            )
                            Text(
                                text = stage.description,
                                fontSize = 11.sp,
                                color = Color(0xFF5E6E8C)
                            )
                        }
                    }

                    if (index < stages.size - 1) {
                        // Drawing connecting line between stages
                        Box(
                            modifier = Modifier
                                .padding(start = 11.dp)
                                .width(2.dp)
                                .height(10.dp)
                                .background(
                                    if (stage.status == BuildStageStatus.SUCCESS && stages[index + 1].status != BuildStageStatus.PENDING) {
                                        Color(0xFF00FF88).copy(alpha = 0.4f)
                                    } else {
                                        Color(0xFF232A45)
                                    }
                                )
                        )
                    }
                }
            }
        }

        // FAILURE RESOLUTION PANEL (CRITICAL FOR DEVELOPER INSIGHTS)
        AnimatedVisibility(
            visible = hasError,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1418)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFF3D00).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.ReportProblem,
                            contentDescription = "Cảnh báo Lỗi",
                            tint = Color(0xFFFF3D00),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PHÂN TÍCH LỖI VÀ HƯỚNG GIẢI QUYẾT",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8A80)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Chi tiết sự cố: $errorMessage",
                        color = Color(0xFFFFCDD2),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Vui lòng kiểm tra các yếu tố sau để sửa lỗi nhanh:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Yêu cầu khóa Android Keystore phần cứng:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Engine sử dụng KeyStore bảo mật thiết bị để ký số APK. Hãy chắc chắn thiết bị của bạn đã được cài mật khẩu khóa màn hình (PIN, mẫu vẽ hoặc dấu vân tay). Đây là yêu cầu bắt buộc của Android đối với vùng lưu trữ khóa bảo mật phần cứng.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE0E0E0)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Giải phóng Cache hệ thống:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Nếu file nhị phân APK trung gian bị lỗi do tiến trình trước bị ngắt quãng đột ngột, hãy vào Cài đặt thiết bị -> Ứng dụng -> APK Builder Studio -> Xóa bộ nhớ đệm (Clear Cache) rồi thực hiện Build lại.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE0E0E0)
                                )
                            }
                        }
                    }
                }
            }
        }

        // TERMINAL BOX DISPLAY WITH SYNTAX HIGHLIGHTING
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF07090E))
                .border(
                    width = 1.dp,
                    color = if (hasError) Color(0xFFFF3D00).copy(alpha = 0.3f) else Color(0xFF00FF88).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
        ) {
            if (buildLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hệ thống terminal chưa kích hoạt.\nNhấn 'Build' để xem logs thời gian thực.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(buildLogs) { log ->
                        val isHeader = log.contains("KHỞI ĐỘNG") || log.contains("HOÀN TẤT THÀNH CÔNG")
                        val isWarning = log.contains("Đang") || log.contains("Tiến hành")
                        val isErr = log.contains("LỖI") || log.contains("Lỗi")
                        val isSuccessMarker = log.contains("thành công", ignoreCase = true)

                        val textColor = when {
                            isErr -> Color(0xFFFF5252)
                            isHeader -> Color(0xFF00FF88)
                            isSuccessMarker -> Color(0xFF69F0AE)
                            isWarning -> Color(0xFF80D8FF)
                            else -> Color(0xFFB0BEC5)
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Extract timestamp if present (e.g. "[12:34:56]")
                            val timePart = if (log.startsWith("[") && log.contains("]")) {
                                log.substring(0, log.indexOf("]") + 1)
                            } else null

                            val messagePart = if (timePart != null) {
                                log.substring(log.indexOf("]") + 1).trim()
                            } else log

                            if (timePart != null) {
                                Text(
                                    text = timePart + " ",
                                    color = Color(0xFF455A64),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = messagePart,
                                color = textColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // COMPATIBILITY INSIGHTS BOX (EXPLAINS THE TARGET SDK 29 DEPLOYMENT)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141A2E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF2C3E6B), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = "Giải thích độ ổn định",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Thông Tin Tối Ưu Hóa Độ Ổn Định Android 11+",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Bắt đầu từ phiên bản này, để xử lý triệt để lỗi không cho phép cài đặt các file Hello World cơ bản trên các hệ điều hành đời mới (Android 11, 12, 13, 14, 15), hệ thống đã tối ưu hóa hạ cấp SDK mục tiêu (targetSdkVersion) của ứng dụng biên dịch về 29 (Android 10).\n\n" +
                            "• Vì sao giải pháp này tối ưu?: Hệ điều hành Android từ phiên bản 11+ nghiêm cấm cài đặt các ứng dụng có targetSdkVersion >= 30 nếu chỉ sử dụng chữ ký V1 (JAR Signing). Bằng cách đặt targetSdkVersion = 29, Android sẽ cho phép ứng dụng cài đặt thành công mà không đòi hỏi chứng chỉ V2 phức tạp.\n" +
                            "• Vượt qua các lỗi kiểm duyệt nhị phân: Toàn bộ quá trình tính toán lại DEX Header (Adler32, SHA-1) và dán nhãn nén ZIP đã được kiểm thử, đảm bảo tính toàn vẹn 100%.",
                    fontSize = 11.sp,
                    color = Color(0xFFB0BEC5),
                    lineHeight = 15.sp
                )
            }
        }
    }
}
