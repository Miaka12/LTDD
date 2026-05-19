package com.example.ckapp.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

// ── DATA CLASSES ───────────────────────────────────────────────────────
data class UserAccount(val username: String, val email: String, val displayName: String)

data class AuthResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val token: String? = null,
    val user: UserAccount? = null
)

// Base URL của server (thay đổi nếu cần)
private const val BASE_URL = "http://10.0.2.2:3000"   // Emulator
// private const val BASE_URL = "http://192.168.x.x:3000" // Device thật

object AuthManager {
    var currentUser: UserAccount? by mutableStateOf(null)
    var authToken: String? by mutableStateOf(null)

    val isLoggedIn get() = currentUser != null

    private fun parseAuthResponse(jsonStr: String): AuthResponse {
        return try {
            val json = JSONObject(jsonStr)
            val userObj = json.optJSONObject("user")
            val user = if (userObj != null) {
                UserAccount(
                    username = userObj.optString("username"),
                    email = userObj.optString("email"),
                    displayName = userObj.optString("displayName")
                )
            } else null

            AuthResponse(
                success = json.optBoolean("success"),
                message = json.optString("message"),
                error = json.optString("error"),
                token = json.optString("token"),
                user = user
            )
        } catch (e: Exception) {
            AuthResponse(success = false, error = "Lỗi parse response")
        }
    }

    suspend fun register(username: String, email: String, password: String): AuthResponse {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/api/register")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }

                val body = JSONObject().apply {
                    put("username", username)
                    put("email", email)
                    put("password", password)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val responseCode = conn.responseCode
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }

                if (responseCode in 200..299) {
                    val resp = parseAuthResponse(responseBody)
                    if (resp.success) {
                        currentUser = resp.user
                        authToken = resp.token
                    }
                    resp
                } else {
                    parseAuthResponse(responseBody)
                }
            } catch (e: Exception) {
                AuthResponse(success = false, error = "Không thể kết nối server: ${e.message}")
            }
        }
    }

    suspend fun login(email: String, password: String): AuthResponse {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/api/login")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }

                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val responseCode = conn.responseCode
                val responseBody = if (responseCode in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                val resp = parseAuthResponse(responseBody)
                if (resp.success) {
                    currentUser = resp.user
                    authToken = resp.token
                }
                resp
            } catch (e: Exception) {
                AuthResponse(success = false, error = "Không thể kết nối server: ${e.message}")
            }
        }
    }

    fun logout() {
        currentUser = null
        authToken = null
    }
}

// ── UI (KHÔNG THAY ĐỔI) ───────────────────────────────────────────────────────
@Composable
fun AccountScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B))))
    ) {
        AnimatedContent(targetState = AuthManager.isLoggedIn, label = "AuthFlow") { loggedIn ->
            if (loggedIn) ProfileContent(AuthManager.currentUser!!) else AuthContent()
        }
    }
}

@Composable
private fun AuthContent() {
    var isLogin by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(80.dp))

        // Biểu tượng App Glow (giữ nguyên)
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Brush.linearGradient(listOf(Teal, CyanAcc)), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ShieldMoon, null, tint = Color.Black, modifier = Modifier.size(50.dp))
        }

        Spacer(Modifier.height(30.dp))
        Text(
            if (isLogin) "Chào mừng trở lại" else "Tạo tài khoản",
            fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White
        )
        Text(
            if (isLogin) "Đăng nhập để xem thông số" else "Tham gia cùng hệ thống IoT thông minh",
            fontSize = 14.sp, color = TextMuted
        )

        Spacer(Modifier.height(40.dp))

        // Form Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(0.03f))
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(32.dp))
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                var name by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                var pass by remember { mutableStateOf("") }

                if (!isLogin) {
                    AuthField(name, { name = it }, "Tên hiển thị", Icons.Default.Person)
                }
                AuthField(email, { email = it }, "Địa chỉ Email", Icons.Default.AlternateEmail)
                AuthField(pass, { pass = it }, "Mật khẩu", Icons.Default.VpnKey, isPassword = true)

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || pass.isBlank() || (!isLogin && name.isBlank())) {
                            errorMessage = "Vui lòng điền đầy đủ thông tin"
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null

                        CoroutineScope(Dispatchers.Main).launch {
                            val result = if (isLogin) {
                                AuthManager.login(email, pass)
                            } else {
                                AuthManager.register(name, email, pass)
                            }

                            isLoading = false
                            if (!result.success) {
                                errorMessage = result.error ?: "Đã xảy ra lỗi"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAcc),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !isLoading
                ) {
                    Text(
                        if (isLoading) "ĐANG XỬ LÝ..."
                        else if (isLogin) "ĐĂNG NHẬP NGAY" else "ĐĂNG KÝ & VÀO APP",
                        color = Color.Black, fontWeight = FontWeight.Black
                    )
                }

                errorMessage?.let {
                    Text(it, color = Color.Red, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { isLogin = !isLogin; errorMessage = null }) {
            Text(
                if (isLogin) "Chưa có tài khoản? Đăng ký ngay" else "Đã có tài khoản? Đăng nhập",
                color = CyanAcc, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ProfileContent(user: UserAccount) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(120.dp).background(Brush.linearGradient(listOf(Teal, CyanAcc)), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = Color.Black)
        }
        Spacer(Modifier.height(24.dp))
        Text(user.displayName, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(user.email, color = TextMuted)

        Spacer(Modifier.height(60.dp))

        Button(
            onClick = { AuthManager.logout() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedDanger.copy(0.2f)),
            border = BorderStroke(1.dp, RedDanger),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("ĐĂNG XUẤT", color = RedDanger, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthField(value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector, isPassword: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextMuted) },
        leadingIcon = { Icon(icon, null, tint = CyanAcc) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyanAcc, unfocusedBorderColor = Color.White.copy(0.1f),
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedContainerColor = Color.Black.copy(0.2f), unfocusedContainerColor = Color.Black.copy(0.2f)
        )
    )
}