package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: ProjectViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val project by viewModel.currentProject.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Giao Diện", "Mã Nguồn", "Xem Trước", "Biên Dịch")

    if (project == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF12141C)),
            contentAlignment = Alignment.Center
        ) {
            Text("Không tìm thấy dự án được chọn.", color = Color.White)
        }
        return
    }

    // Editable text states
    var configText by remember(project?.id) { mutableStateOf(project?.configJson ?: "") }
    var kotlinText by remember(project?.id) { mutableStateOf(project?.codeKotlin ?: "") }
    var xmlText by remember(project?.id) { mutableStateOf(project?.codeLayout ?: "") }
    
    val currentAppName by viewModel.customAppName.collectAsState()
    val currentPkgName by viewModel.customPackageName.collectAsState()

    // Sync state back to viewmodel
    fun syncState() {
        val cur = project ?: return
        viewModel.updateCurrentProject(
            configJson = configText,
            codeKotlin = kotlinText,
            codeLayout = xmlText,
            appName = currentAppName,
            packageName = currentPkgName
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = project!!.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = currentPkgName,
                            fontSize = 11.sp,
                            color = Color(0xFFA0A5B5)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        syncState()
                        onBack()
                    }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            syncState()
                            selectedTab = 3 // Switch to build tab
                            viewModel.startBuild(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88), contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Build", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF12141C)
                )
            )
        },
        containerColor = Color(0xFF12141C),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1C1F2B),
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            syncState()
                            selectedTab = index
                        },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Rounded.Palette
                                    1 -> Icons.Rounded.Code
                                    2 -> Icons.Rounded.TabletAndroid
                                    else -> Icons.Rounded.Terminal
                                },
                                contentDescription = title
                            )
                        },
                        label = { Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF00FF88),
                            selectedTextColor = Color(0xFF00FF88),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0xFF00FF88).copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: DESIGNER / CONFIG EDITOR
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F2B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Thuộc Tính Ứng Dụng",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                OutlinedTextField(
                                    value = currentAppName,
                                    onValueChange = { viewModel.customAppName.value = it },
                                    label = { Text("Tên hiển thị APK") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00FF88),
                                        focusedLabelColor = Color(0xFF00FF88)
                                    )
                                )

                                OutlinedTextField(
                                    value = currentPkgName,
                                    onValueChange = { viewModel.customPackageName.value = it },
                                    label = { Text("Package Name mục tiêu") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00FF88),
                                        focusedLabelColor = Color(0xFF00FF88)
                                    )
                                )
                            }
                        }

                        // JSON Schema Direct Editor
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F2B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Cấu hình JSON Widgets",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF00FF88).copy(alpha = 0.15f))
                                            .padding(vertical = 4.dp, horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "config.json",
                                            color = Color(0xFF00FF88),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = configText,
                                    onValueChange = {
                                        configText = it
                                        syncState()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color(0xFF00FF88)
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00E5FF),
                                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: KOTLIN & XML CODE EDITOR
                    var codeTab by remember { mutableStateOf(0) } // 0: Kotlin, 1: XML
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // File switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1C1F2B))
                                .padding(4.dp)
                        ) {
                            Button(
                                onClick = { codeTab = 0 },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (codeTab == 0) Color(0xFF12141C) else Color.Transparent,
                                    contentColor = if (codeTab == 0) Color(0xFF00FF88) else Color.Gray
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                elevation = null
                            ) {
                                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("MainActivity.kt", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { codeTab = 1 },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (codeTab == 1) Color(0xFF12141C) else Color.Transparent,
                                    contentColor = if (codeTab == 1) Color(0xFF00E5FF) else Color.Gray
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                elevation = null
                            ) {
                                Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("activity_main.xml", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Code Editor Terminal Card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F2B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (codeTab == 0) "Mã nguồn Jetpack Compose (Kotlin)" else "Mã thiết kế cổ điển (XML/AAPT2)",
                                    fontSize = 13.sp,
                                    color = Color.LightGray,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedTextField(
                                    value = if (codeTab == 0) kotlinText else xmlText,
                                    onValueChange = {
                                        if (codeTab == 0) {
                                            kotlinText = it
                                        } else {
                                            xmlText = it
                                        }
                                        syncState()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = if (codeTab == 0) Color(0xFFE0E0E0) else Color(0xFFFFB300)
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = if (codeTab == 0) Color(0xFF00FF88) else Color(0xFF00E5FF),
                                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: LIVE PREVIEW SIMULATOR
                    Box(modifier = Modifier.fillMaxSize()) {
                        DynamicLayoutRenderer(
                            configJson = configText,
                            modifier = Modifier.fillMaxSize(),
                            isPreviewMode = true
                        )
                    }
                }

                3 -> {
                    // TAB 3: COMPILER TERMINAL & OUTPUT ACTIONS
                    val buildLogs by viewModel.buildLogs.collectAsState()
                    val isBuilding by viewModel.isBuilding.collectAsState()
                    val builtApk by viewModel.builtApkFile.collectAsState()
                    var isArchExpanded by remember { mutableStateOf(false) }

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        BuildStatusLogger(
                            buildLogs = buildLogs,
                            isBuilding = isBuilding,
                            builtApkFileExists = builtApk != null && builtApk!!.exists()
                        )

                        // System Architecture Insights (Expandable Panel)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F2B)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isArchExpanded = !isArchExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Analytics,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Phân Tích Kiến Trúc & An Toàn",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isArchExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                }

                                AnimatedVisibility(visible = isArchExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.15f))
                                        
                                        // Dex limitations
                                        Column {
                                            Text(
                                                text = "1. Sửa Đổi Nhị Phân (Binary Patching & Padding)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFB300)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Gói công cụ áp dụng kỹ thuật vá nhị phân trực tiếp trên String Pool của tệp 'classes.dex' và 'AndroidManifest.xml'. Để tránh làm xáo trộn các bảng offset nhạy cảm (string_ids, type_ids, method_ids) gây ra VerifyError hoặc lọt lưới phân tích AXML, engine buộc phải tự động đệm độ dài chuỗi (package name cố định 30 kí tự, app name cố định 32 kí tự). Kỹ thuật này giảm thiểu rủi ro nhưng vẫn tồn tại nguy cơ lỗi ngữ nghĩa nếu các lớp sinh tự động (R.java, BuildConfig) tham chiếu chéo ngoài tầm quét.",
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                lineHeight = 15.sp
                                            )
                                        }

                                        // Signing mechanisms
                                        Column {
                                            Text(
                                                text = "2. Cơ Chế Ký Số APK (Signing & Cryptography)",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00FF88)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Hệ thống triển khai thuật toán băm tệp SHA-256 và ký số RSA-2048 chuẩn (SHA256withRSA) trực tiếp trên điện thoại để tạo ra các khối chữ ký PKCS#7 (MANIFEST.MF, CERT.SF, CERT.RSA). Do chữ ký V1 chỉ bảo vệ tính toàn vẹn của tệp Zip riêng lẻ và có thể bị từ chối trên một số thiết bị HyperOS/OneUI tùy biến sâu yêu cầu V2/V3 Alignment, chúng tôi khuyến cáo sử dụng tệp chữ ký này cho mục đích thử nghiệm sideload cục bộ hoặc phát triển cá nhân.",
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                lineHeight = 15.sp
                                            )
                                        }

                                        // Dynamic Runtime advantages
                                        Column {
                                            Text(
                                                text = "3. Giải Pháp Tối Ưu: Dynamic Sandbox Runtime",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF00E5FF)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Để khắc phục triệt để rủi ro từ việc vá nhị phân, chúng tôi phát triển mô hình Runtime Engine 3 lớp: Schema Validator (kiểm định cú pháp) -> Action Sandbox (cách ly hành vi độc hại/phản chiếu) -> Compose Renderer (hiển thị động). Giải pháp này chạy trực tiếp trên máy ảo cục bộ, giúp giảm thiểu tối đa rủi ro bảo mật qua cơ chế sandbox đa tầng, kiểm soát hạn ngạch và giới hạn thực thi nghiêm ngặt, có khả năng mở rộng vô hạn qua JSON Schema mà không chịu giới hạn của hệ thống biên dịch nặng nề.",
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Loader
                        if (isBuilding) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = Color(0xFF00FF88), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Đang biên dịch tệp APK offline...",
                                    color = Color(0xFF00FF88),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Installation Actions
                        AnimatedVisibility(visible = builtApk != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F2B)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF00FF88),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Gói APK đã sẵn sàng để xuất và cài đặt trực tiếp trên thiết bị của bạn!",
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.installApk(context) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88), contentColor = Color.Black),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Icon(Icons.Rounded.Android, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Cài Đặt APK", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    Button(
                                        onClick = { viewModel.shareApk(context) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Chia Sẻ APK", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
