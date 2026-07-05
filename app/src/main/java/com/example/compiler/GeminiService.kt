package com.example.compiler

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeminiFixResult(
    val success: Boolean,
    val codeKotlin: String? = null,
    val codeLayout: String? = null,
    val configJson: String? = null,
    val explanation: String? = null,
    val errorMessage: String? = null
)

object GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun fixErrorsWithGemini(
        apiKey: String,
        appName: String,
        packageName: String,
        configJson: String,
        codeKotlin: String,
        codeLayout: String,
        buildLogs: List<String>,
        userPrompt: String
    ): GeminiFixResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext GeminiFixResult(
                success = false,
                errorMessage = "API Key không được để trống. Vui lòng cấu hình API Key của bạn."
            )
        }

        // Limit the build logs size to avoid token overflow
        val logsJoined = buildLogs.takeLast(40).joinToString("\n")

        val prompt = """
            Bạn là một lập trình viên Android chuyên nghiệp. Nhiệm vụ của bạn là phân tích mã nguồn hiện tại của một dự án Android nhỏ và sửa lỗi biên dịch/logic hoặc cải tiến mã nguồn dựa trên nhật ký lỗi (build logs) và mô tả yêu cầu của người dùng.

            Dưới đây là thông tin chi tiết của dự án:
            Tên ứng dụng: $appName
            Package Name: $packageName

            1. Tệp tin config.json (Sơ đồ cấu trúc các widget của ứng dụng):
            $configJson

            2. Tệp tin MainActivity.kt (Mã nguồn Jetpack Compose để vẽ giao diện và xử lý logic):
            $codeKotlin

            3. Tệp tin activity_main.xml (Mã thiết kế bố cục XML):
            $codeLayout

            Nhật ký biên dịch / lỗi hiện tại:
            $logsJoined

            Yêu cầu sửa đổi / Mô tả lỗi từ người dùng (nếu có):
            $userPrompt

            HÃY PHÂN TÍCH VÀ SỬA CÁC LỖI TRÊN. Nếu không có lỗi cụ thể nào, hãy tối ưu hóa mã nguồn hiện tại cho sạch hơn và chạy tốt hơn dựa trên yêu cầu của người dùng.

            Hãy trả về kết quả dưới định dạng JSON có cấu trúc như sau (chú ý: không viết bất kỳ ký tự nào khác bên ngoài JSON này):
            {
              "codeKotlin": "Toàn bộ mã nguồn Kotlin của MainActivity.kt đã sửa/cải tiến. Đảm bảo mã nguồn đầy đủ, không bị cắt bớt và đúng cú pháp.",
              "codeLayout": "Toàn bộ mã nguồn XML của activity_main.xml đã sửa/cải tiến. Đảm bảo đúng cú pháp XML.",
              "configJson": "Toàn bộ cấu hình config.json đã sửa/cải tiến. Đảm bảo định dạng JSON hợp lệ.",
              "explanation": "Giải thích chi tiết về các lỗi bạn đã sửa và những cải tiến bạn đã thực hiện bằng tiếng Việt."
            }
        """.trimIndent()

        try {
            // Build standard Gemini request body
            val partObj = JSONObject().put("text", prompt)
            val partsArr = org.json.JSONArray().put(partObj)
            val contentObj = JSONObject().put("parts", partsArr)
            val contentsArr = org.json.JSONArray().put(contentObj)

            // Request structured output using responseSchema for stability
            val responseSchemaObj = JSONObject()
                .put("type", "OBJECT")
                .put("properties", JSONObject()
                    .put("codeKotlin", JSONObject().put("type", "STRING"))
                    .put("codeLayout", JSONObject().put("type", "STRING"))
                    .put("configJson", JSONObject().put("type", "STRING"))
                    .put("explanation", JSONObject().put("type", "STRING"))
                )
                .put("required", org.json.JSONArray().put("codeKotlin").put("codeLayout").put("configJson").put("explanation"))

            val genConfigObj = JSONObject()
                .put("responseMimeType", "application/json")
                .put("responseSchema", responseSchemaObj)

            val payloadObj = JSONObject()
                .put("contents", contentsArr)
                .put("generationConfig", genConfigObj)

            val requestBody = payloadObj.toString().toRequestBody("application/json".toMediaType())
            
            // Using recommended gemini-2.5-flash
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    val parsedError = try {
                        JSONObject(errorBody).getJSONObject("error").getString("message")
                    } catch (e: Exception) {
                        errorBody
                    }
                    return@withContext GeminiFixResult(
                        success = false,
                        errorMessage = "Yêu cầu API thất bại (Mã lỗi: ${response.code}). Chi tiết: $parsedError"
                    )
                }

                val responseBodyStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseBodyStr)
                
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext GeminiFixResult(
                        success = false,
                        errorMessage = "Không nhận được phản hồi phù hợp từ Gemini API."
                    )
                }

                val firstCandidate = candidates.getJSONObject(0)
                val textResponse = firstCandidate.getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                // Parse the structured text back to JSON
                val fixedCodeJson = JSONObject(textResponse)
                
                val fixedKotlin = fixedCodeJson.getString("codeKotlin")
                val fixedLayout = fixedCodeJson.getString("codeLayout")
                val fixedConfig = fixedCodeJson.getString("configJson")
                val fixedExplanation = fixedCodeJson.getString("explanation")

                GeminiFixResult(
                    success = true,
                    codeKotlin = fixedKotlin,
                    codeLayout = fixedLayout,
                    configJson = fixedConfig,
                    explanation = fixedExplanation
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            GeminiFixResult(
                success = false,
                errorMessage = "Lỗi kết nối Gemini API: ${e.localizedMessage ?: e.message}"
            )
        }
    }
}
