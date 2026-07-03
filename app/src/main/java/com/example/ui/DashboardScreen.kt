package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Project
import com.example.data.Templates
import com.example.data.TemplateItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ProjectViewModel,
    onProjectSelected: () -> Unit
) {
    val projects by viewModel.allProjects.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Android,
                            contentDescription = null,
                            tint = Color(0xFF00FF88),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "APK Builder Studio",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF12141C)
                )
            )
        },
        containerColor = Color(0xFF12141C)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1C1F2B), Color(0xFF10121A))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF00FF88).copy(alpha = 0.15f))
                                    .padding(vertical = 4.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "OFFLINE COMPILER ENGINE v2.0",
                                    color = Color(0xFF00FF88),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Biên Dịch APK Trực Tiếp",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Biên dịch mã nguồn XML, Kotlin thành APK cài đặt trực tiếp không cần kết nối Internet.",
                                fontSize = 13.sp,
                                color = Color(0xFFA0A5B5)
                            )
                        }

                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00FF88),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Dự Án Mới", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Quick Status Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F2B)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF00E5FF).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Môi trường biên dịch độc lập",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Hoàn toàn bảo mật và riêng tư. Dữ liệu biên dịch được đóng gói an toàn cục bộ.",
                                fontSize = 12.sp,
                                color = Color(0xFFA0A5B5)
                            )
                        }
                    }
                }
            }

            // Section: Templates
            item {
                Column {
                    Text(
                        text = "Chọn Mẫu Dự Án",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(Templates.LIST) { template ->
                            val color = Color(android.graphics.Color.parseColor(template.accentColor))
                            
                            Box(
                                modifier = Modifier
                                    .width(160.dp)
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF1C1F2B))
                                    .clickable {
                                        viewModel.createProjectFromTemplate(template)
                                        onProjectSelected()
                                    }
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(color.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when(template.iconName) {
                                                "waving_hand" -> Icons.Rounded.PlayArrow
                                                "plus_one" -> Icons.Rounded.Build
                                                "calendar_today" -> Icons.Rounded.Palette
                                                else -> Icons.Rounded.Settings
                                            },
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    Column {
                                        Text(
                                            text = template.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = template.description,
                                            fontSize = 11.sp,
                                            color = Color(0xFFA0A5B5),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section: Projects List
            item {
                Text(
                    text = "Dự Án Đang Phát Triển (${projects.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (projects.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Chưa có dự án nào. Hãy chọn một mẫu bên trên để bắt đầu!",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                items(projects) { project ->
                    val dateStr = remember(project.createdAt) {
                        try {
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            sdf.format(Date(project.createdAt))
                        } catch (e: Exception) {
                            ""
                        }
                    }

                    val color = remember(project.accentColor) {
                        try {
                            Color(android.graphics.Color.parseColor(project.accentColor))
                        } catch (e: Exception) {
                            Color(0xFF00FF88)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F2B)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectProject(project)
                                onProjectSelected()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(color.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Android,
                                        contentDescription = null,
                                        tint = color,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = project.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = project.packageName,
                                        fontSize = 11.sp,
                                        color = Color(0xFFA0A5B5)
                                    )
                                    Text(
                                        text = "Tạo lúc: $dateStr",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.deleteProject(project) }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Xóa dự án",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // Modal Create Dialog
    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        var pkgInput by remember { mutableStateOf("com.example.") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Tạo dự án mới", color = Color.White, fontWeight = FontWeight.Bold) },
            containerColor = Color(0xFF1C1F2B),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Tên ứng dụng") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF88),
                            focusedLabelColor = Color(0xFF00FF88)
                        )
                    )

                    OutlinedTextField(
                        value = pkgInput,
                        onValueChange = { pkgInput = it },
                        label = { Text("Mã gói (Package ID)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF88),
                            focusedLabelColor = Color(0xFF00FF88)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            val emptyTemplate = TemplateItem(
                                name = nameInput,
                                description = "Dự án trống",
                                iconName = "android",
                                accentColor = "#00FF88",
                                configJson = """
                                {
                                  "appName": "$nameInput",
                                  "accentColor": "#00FF88",
                                  "backgroundColor": "#12141C",
                                  "widgets": [
                                    { "type": "title", "text": "$nameInput", "size": 26, "color": "#00FF88", "align": "center" },
                                    { "type": "spacer", "size": 16 },
                                    { "type": "text", "text": "Chào mừng bạn đến với ứng dụng mới của mình!", "size": 16, "color": "#FFFFFF", "align": "center" }
                                  ]
                                }
                                """.trimIndent(),
                                codeKotlin = "",
                                codeLayout = ""
                            )
                            viewModel.createProjectFromTemplate(emptyTemplate)
                            showCreateDialog = false
                            onProjectSelected()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88), contentColor = Color.Black)
                ) {
                    Text("Tạo", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Hủy", color = Color.Gray)
                }
            }
        )
    }
}
