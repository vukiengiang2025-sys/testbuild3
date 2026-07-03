package com.example.data

object Templates {

    val LIST = listOf(
        TemplateItem(
            name = "Hello World",
            description = "Dự án xin chào cơ bản với khung nhập tên và hiển thị lời chào tương tác.",
            iconName = "waving_hand",
            accentColor = "#00E5FF",
            configJson = """
            {
              "appName": "Hello World App",
              "accentColor": "#00E5FF",
              "backgroundColor": "#12141C",
              "widgets": [
                { "type": "title", "text": "Xin Chào Thế Giới!", "size": 28, "color": "#00E5FF", "align": "center" },
                { "type": "spacer", "size": 16 },
                { "type": "text", "text": "Đây là ứng dụng Android đầu tiên của bạn, được xây dựng hoàn toàn ngoại tuyến ngay trên di động bằng APK Builder Studio!", "size": 16, "color": "#A0A5B5", "align": "center" },
                { "type": "spacer", "size": 32 },
                { "type": "text-field", "id": "user_name", "placeholder": "Nhập tên của bạn...", "label": "Tên của bạn" },
                { "type": "spacer", "size": 20 },
                { "type": "button", "text": "Gửi lời chào", "action": "toast:Xin chào {user_name}! Chúc bạn một ngày tốt lành!", "color": "#00E5FF" }
              ]
            }
            """.trimIndent(),
            codeKotlin = """
            package com.example.builtapk
            
            import android.os.Bundle
            import android.widget.Toast
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.unit.sp
            
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        var userName by remember { mutableStateOf("") }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF12141C))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Xin Chào Thế Giới!",
                                fontSize = 28.sp,
                                color = Color(0xFF00E5FF)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Đây là ứng dụng Android đầu tiên của bạn, được xây dựng hoàn toàn ngoại tuyến ngay trên điện thoại di động bằng APK Builder Studio!",
                                fontSize = 16.sp,
                                color = Color(0xFFA0A5B5)
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { userName = it },
                                label = { Text("Tên của bạn") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    Toast.makeText(this@MainActivity, "Xin chào " + userName + "! Chúc bạn một ngày tốt lành!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                            ) {
                                Text("Gửi lời chào", color = Color.Black)
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
            codeLayout = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_size"
                android:layout_height="match_size"
                android:orientation="vertical"
                android:background="#12141C"
                android:padding="24dp"
                android:gravity="center">
                
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Xin Chào Thế Giới!"
                    android:textSize="28sp"
                    android:textColor="#00E5FF" />
                    
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Đây là ứng dụng Android đầu tiên của bạn..."
                    android:textSize="16sp"
                    android:textColor="#A0A5B5" />
                    
                <EditText
                    android:id="@+id/user_name"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:hint="Nhập tên của bạn..." />
                    
                <Button
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Gửi lời chào"
                    android:backgroundTint="#00E5FF" />
            </LinearLayout>
            """.trimIndent()
        ),
        TemplateItem(
            name = "Bộ Đếm Clicker",
            description = "Trình đếm tương tác phản hồi phản ứng thời gian thực với các sự kiện tăng giảm dữ liệu.",
            iconName = "plus_one",
            accentColor = "#00FF88",
            configJson = """
            {
              "appName": "Clicker Pro",
              "accentColor": "#00FF88",
              "backgroundColor": "#0B0F19",
              "widgets": [
                { "type": "title", "text": "Trình Đếm Phản Hồi", "size": 24, "color": "#00FF88", "align": "center" },
                { "type": "spacer", "size": 24 },
                { "type": "text", "text": "Số lần nhấp hiện tại của bạn là:", "size": 16, "color": "#808A9F", "align": "center" },
                { "type": "spacer", "size": 12 },
                { "type": "text", "text": "{counter}", "size": 64, "color": "#00FF88", "align": "center", "weight": "bold" },
                { "type": "spacer", "size": 32 },
                { "type": "counter-buttons", "stateId": "counter", "color": "#00FF88" },
                { "type": "spacer", "size": 40 },
                { "type": "button", "text": "Đặt lại bộ đếm", "action": "reset:counter", "color": "#FF4444" }
              ]
            }
            """.trimIndent(),
            codeKotlin = """
            package com.example.builtapk
            
            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.unit.sp
            
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        var counter by remember { mutableStateOf(0) }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0B0F19))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Trình Đếm Phản Hồi",
                                fontSize = 24.sp,
                                color = Color(0xFF00FF88)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "Số lần nhấp hiện tại của bạn là:",
                                fontSize = 16.sp,
                                color = Color(0xFF808A9F)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = counter.toString(),
                                fontSize = 64.sp,
                                color = Color(0xFF00FF88),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = { if (counter > 0) counter-- },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88))
                                ) {
                                    Text("- Giảm", color = Color.Black)
                                }
                                Button(
                                    onClick = { counter++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88))
                                ) {
                                    Text("+ Tăng", color = Color.Black)
                                }
                            }
                            Spacer(modifier = Modifier.height(40.dp))
                            Button(
                                onClick = { counter = 0 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                            ) {
                                Text("Đặt lại bộ đếm", color = Color.White)
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
            codeLayout = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:background="#0B0F19"
                android:padding="24dp"
                android:gravity="center">
                
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Trình Đếm Phản Hồi"
                    android:textSize="24sp"
                    android:textColor="#00FF88" />
                ...
            </LinearLayout>
            """.trimIndent()
        ),
        TemplateItem(
            name = "Máy Tính Tuổi",
            description = "Ứng dụng tiện ích cho phép người dùng nhập năm sinh và tính toán tuổi thực tế offline.",
            iconName = "calendar_today",
            accentColor = "#FFB300",
            configJson = """
            {
              "appName": "Age Calculator",
              "accentColor": "#FFB300",
              "backgroundColor": "#1A1A1A",
              "widgets": [
                { "type": "title", "text": "Máy Tính Tuổi Offline", "size": 24, "color": "#FFB300", "align": "center" },
                { "type": "spacer", "size": 20 },
                { "type": "text-field", "id": "birth_year", "placeholder": "Ví dụ: 2000", "label": "Nhập năm sinh của bạn" },
                { "type": "spacer", "size": 20 },
                { "type": "button", "text": "Tính Tuổi Ngay", "action": "calculate_age:birth_year:age_result", "color": "#FFB300" },
                { "type": "spacer", "size": 32 },
                { "type": "text", "text": "Tuổi hiện tại của bạn là:", "size": 16, "color": "#CCCCCC", "align": "center" },
                { "type": "spacer", "size": 8 },
                { "type": "text", "text": "{age_result} tuổi", "size": 40, "color": "#FFB300", "align": "center", "weight": "bold" }
              ]
            }
            """.trimIndent(),
            codeKotlin = """
            package com.example.builtapk
            
            import android.os.Bundle
            import android.widget.Toast
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.unit.sp
            import java.util.Calendar
            
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        var birthYear by remember { mutableStateOf("") }
                        var ageResult by remember { mutableStateOf("0") }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF1A1A1A))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Máy Tính Tuổi Offline",
                                fontSize = 24.sp,
                                color = Color(0xFFFFB300)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedTextField(
                                value = birthYear,
                                onValueChange = { birthYear = it },
                                label = { Text("Nhập năm sinh của bạn") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    val year = birthYear.toIntOrNull()
                                    if (year != null && year > 1900 && year <= 2026) {
                                        ageResult = (2026 - year).toString()
                                    } else {
                                        Toast.makeText(this@MainActivity, "Năm sinh không hợp lệ!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                            ) {
                                Text("Tính Tuổi Ngay", color = Color.Black)
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                text = "Tuổi hiện tại của bạn là:",
                                fontSize = 16.sp,
                                color = Color(0xFFCCCCCC)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = ageResult + " tuổi",
                                fontSize = 40.sp,
                                color = Color(0xFFFFB300),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            """.trimIndent(),
            codeLayout = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:background="#1A1A1A"
                android:padding="24dp"
                android:gravity="center">
                ...
            </LinearLayout>
            """.trimIndent()
        ),
        TemplateItem(
            name = "Khảo Sát Phản Hồi",
            description = "Thu thập ý kiến đánh giá trực quan với thanh xếp hạng sao và gửi thông tin tương tác.",
            iconName = "poll",
            accentColor = "#FF5722",
            configJson = """
            {
              "appName": "User Survey",
              "accentColor": "#FF5722",
              "backgroundColor": "#121212",
              "widgets": [
                { "type": "title", "text": "Khảo Sát Khách Hàng", "size": 24, "color": "#FF5722", "align": "center" },
                { "type": "spacer", "size": 16 },
                { "type": "text", "text": "Vui lòng cho chúng tôi biết cảm nhận của bạn về sản phẩm:", "size": 14, "color": "#A0A5B5", "align": "left" },
                { "type": "spacer", "size": 16 },
                { "type": "text-field", "id": "comment", "placeholder": "Ý kiến đóng góp khác...", "label": "Đánh giá chi tiết" },
                { "type": "spacer", "size": 24 },
                { "type": "button", "text": "Gửi Ý Kiến Phản Hồi", "action": "toast:Cảm ơn đóng góp của bạn: {comment}!", "color": "#FF5722" }
              ]
            }
            """.trimIndent(),
            codeKotlin = """
            package com.example.builtapk
            
            import android.os.Bundle
            import android.widget.Toast
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.unit.sp
            
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        var comment by remember { mutableStateOf("") }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF121212))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Khảo Sát Khách Hàng",
                                fontSize = 24.sp,
                                color = Color(0xFFFF5722)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Vui lòng cho chúng tôi biết cảm nhận của bạn về sản phẩm:",
                                fontSize = 14.sp,
                                color = Color(0xFFA0A5B5)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = comment,
                                onValueChange = { comment = it },
                                label = { Text("Đánh giá chi tiết") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    Toast.makeText(this@MainActivity, "Cảm ơn đóng góp của bạn: " + comment + "!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                            ) {
                                Text("Gửi Ý Kiến Phản Hồi", color = Color.White)
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
            codeLayout = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:background="#121212"
                android:padding="24dp">
                ...
            </LinearLayout>
            """.trimIndent()
        ),
        TemplateItem(
            name = "Trình Duyệt Liên Kết",
            description = "Ứng dụng tương tác mở liên kết ngoài, minh họa cơ chế kiểm soát quyền (Capability-based Permissions) trong Sandbox.",
            iconName = "language",
            accentColor = "#00FF88",
            configJson = """
            {
              "appName": "Web Opener",
              "accentColor": "#00FF88",
              "backgroundColor": "#111422",
              "permissions": [
                "open_external_urls"
              ],
              "widgets": [
                { "type": "title", "text": "Cổng Liên Kết An Toàn", "size": 24, "color": "#00FF88", "align": "center" },
                { "type": "spacer", "size": 20 },
                { "type": "text", "text": "Ứng dụng này yêu cầu quyền 'open_external_urls' khai báo trong Schema permissions để mở các đường dẫn ngoài Sandbox an toàn.", "size": 14, "color": "#A0A5B5", "align": "center" },
                { "type": "spacer", "size": 32 },
                { "type": "button", "text": "Mở Google Search", "action": "open_url:https://google.com", "color": "#00FF88" },
                { "type": "spacer", "size": 16 },
                { "type": "button", "text": "Mở GitHub", "action": "open_url:https://github.com", "color": "#00E5FF" }
              ]
            }
            """.trimIndent(),
            codeKotlin = """
            package com.example.builtapk
            
            import android.os.Bundle
            import android.content.Intent
            import android.net.Uri
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.foundation.background
            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.*
            import androidx.compose.ui.Alignment
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.graphics.Color
            import androidx.compose.ui.unit.dp
            import androidx.compose.ui.unit.sp
            
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF111422))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Cổng Liên Kết An Toàn",
                                fontSize = 24.sp,
                                color = Color(0xFF00FF88)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Nhấp vào nút dưới đây để mở trang web ngoại tuyến an toàn.",
                                fontSize = 14.sp,
                                color = Color(0xFFA0A5B5)
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"))
                                    startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88))
                            ) {
                                Text("Mở Google Search", color = Color.Black)
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
            codeLayout = """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical"
                android:background="#111422"
                android:padding="24dp"
                android:gravity="center">
                <Button
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Mở Google Search"
                    android:backgroundTint="#00FF88" />
            </LinearLayout>
            """.trimIndent()
        )
    )
}

data class TemplateItem(
    val name: String,
    val description: String,
    val iconName: String,
    val accentColor: String,
    val configJson: String,
    val codeKotlin: String,
    val codeLayout: String
)
