package com.example.ckapp.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ckapp.model.SensorData
import com.example.ckapp.network.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==================== HOMESCREEN CHÍNH ====================

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val api = RetrofitClient.instance
    val scope = rememberCoroutineScope()

    // State dữ liệu cảm biến
    var sensorData by remember {
        mutableStateOf(
            SensorData(
                gas = 0,
                flame = false,
                temperature = 0.0,
                humidity = 0.0,
                pressure = 0.0,
                light = 0,
                uvIndex = 0.0,
                time = "Đang tải..."
            )
        )
    }

    var isLoading by remember { mutableStateOf(true) }

    // Ngưỡng cảnh báo gas do người dùng đặt (mặc định 300 ppm)
    var gasThreshold by remember { mutableStateOf(300f) }
    var showThreshold by remember { mutableStateOf(false) }

    // State hiển thị dialog cảnh báo vượt ngưỡng
    var showGasAlertDialog by remember { mutableStateOf(false) }
    // Lưu giá trị gas đã trigger dialog (tránh dialog bật liên tục)
    var lastAlertedGas by remember { mutableStateOf(-1) }

    // Hàm fetch dữ liệu từ server
    suspend fun fetchData() {
        try {
            val data = api.getLatestSensor()
            sensorData = data
            isLoading = false

            // Kiểm tra ngưỡng gas → hiện dialog nếu vượt ngưỡng VÀ chưa alert cho giá trị này
            if (data.gas >= gasThreshold.toInt() && data.gas != lastAlertedGas) {
                showGasAlertDialog = true
                lastAlertedGas = data.gas
            }
            // Reset lastAlertedGas khi gas về an toàn
            if (data.gas < gasThreshold.toInt()) {
                lastAlertedGas = -1
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (isLoading) {
                // Chỉ toast lần đầu
                Toast.makeText(context, "Lỗi kết nối server. Sử dụng dữ liệu mẫu.", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
        }
    }

    // Polling tự động mỗi 5 giây
    LaunchedEffect(Unit) {
        while (true) {
            fetchData()
            delay(5_000L)
        }
    }

    // Trạng thái tổng hợp hệ thống
    val status = when {
        sensorData.flame -> "DANGER"
        sensorData.gas >= 600 -> "DANGER"
        sensorData.gas >= gasThreshold.toInt() -> "WARNING"
        else -> "SAFE"
    }

    val alertActive = sensorData.flame || sensorData.gas >= gasThreshold.toInt()

    // ==================== DIALOG CẢNH BÁO VƯỢT NGƯỠNG ====================
    if (showGasAlertDialog) {
        GasAlertDialog(
            gasValue = sensorData.gas,
            threshold = gasThreshold.toInt(),
            isDanger = sensorData.gas >= 600,
            onDismiss = { showGasAlertDialog = false }
        )
    }

    // ==================== LAYOUT CHÍNH ====================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // Header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Bottom) {
            Column {
                Text(
                    "IoT Monitor",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPri,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Giám sát thời gian thực • ${sensorData.time}",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (alertActive) {
                    // Icon cảnh báo nhấp nháy
                    val infiniteTransition = rememberInfiniteTransition(label = "blink")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f, targetValue = 0.2f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                        label = "blink"
                    )
                    Icon(
                        Icons.Default.NotificationsActive,
                        null,
                        tint = AmberWarn.copy(alpha = alpha),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { showGasAlertDialog = true }
                    )
                }
                // Nút refresh thủ công
                TextButton(
                    onClick = { scope.launch { fetchData() } },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(
                                    if (isLoading) AmberWarn else GreenSafe,
                                    androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            if (isLoading) "..." else "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLoading) AmberWarn else GreenSafe,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Banner cảnh báo tổng hợp
        AnimatedVisibility(visible = alertActive, enter = fadeIn() + slideInVertically()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AmberWarn.copy(alpha = 0.12f))
                    .border(1.dp, AmberWarn.copy(0.35f), RoundedCornerShape(12.dp))
                    .clickable { showGasAlertDialog = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = AmberWarn, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when {
                                sensorData.flame && sensorData.gas >= gasThreshold.toInt() ->
                                    "⚠ Phát hiện lửa + Khí gas vượt ngưỡng!"
                                sensorData.flame -> "⚠ Phát hiện lửa! Sơ tán ngay."
                                else -> "⚠ Khí gas vượt ngưỡng ${gasThreshold.toInt()} ppm (hiện: ${sensorData.gas} ppm)"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AmberWarn
                        )
                        Text(
                            "Nhấn để xem chi tiết cảnh báo",
                            fontSize = 10.sp,
                            color = AmberWarn.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Mini stats row
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            MiniStatCard("🌡", "${sensorData.temperature}°", "Nhiệt độ", Modifier.weight(1f))
            MiniStatCard("💧", "${sensorData.humidity}%", "Độ ẩm", Modifier.weight(1f))
            MiniStatCard("☀️", "UV ${sensorData.uvIndex}", "Chỉ số UV", Modifier.weight(1f))
        }

        SystemStatusCard(status)

        // Gas card - truyền thêm threshold để hiển thị đường ngưỡng
        GasCardModern(
            gas = sensorData.gas,
            threshold = gasThreshold.toInt(),
            onAlertClick = { showGasAlertDialog = true }
        )

        FlameCard(sensorData.flame)
        TempHumidCard(sensorData.temperature, sensorData.humidity)
        EnvCard(sensorData.pressure, sensorData.light, sensorData.uvIndex)

        // Control ngưỡng cảnh báo gas
        ThresholdControl(
            gasThreshold = gasThreshold,
            currentGas = sensorData.gas,
            showThreshold = showThreshold,
            onShowChange = { showThreshold = it },
            onValueChange = {
                gasThreshold = it
                // Reset alert khi người dùng thay đổi ngưỡng
                lastAlertedGas = -1
            }
        )

        Spacer(Modifier.height(30.dp))
    }
}

// ==================== DIALOG CẢNH BÁO GAS ====================

@Composable
private fun GasAlertDialog(
    gasValue: Int,
    threshold: Int,
    isDanger: Boolean,
    onDismiss: () -> Unit
) {
    val alertColor = if (isDanger) RedDanger else AmberWarn
    val alertTitle = if (isDanger) "🔴 NGUY HIỂM - Khí Gas Cực Cao!" else "⚠ CẢNH BÁO - Khí Gas Vượt Ngưỡng"
    val alertMsg = if (isDanger)
        "Nồng độ khí gas đạt mức NGUY HIỂM ($gasValue ppm ≥ 600 ppm)!\n\nHãy tắt nguồn gas, mở cửa thông gió và sơ tán khỏi khu vực ngay lập tức!"
    else
        "Nồng độ khí gas đang ở mức $gasValue ppm, vượt ngưỡng cảnh báo bạn đặt ($threshold ppm).\n\nKiểm tra nguồn gas và đảm bảo thông gió tốt."

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BgCard)
                .border(2.dp, alertColor.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Icon lớn nhấp nháy
                val infiniteTransition = rememberInfiniteTransition(label = "alertPulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f, targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label = "alertPulse"
                )
                Text(
                    text = if (isDanger) "🚨" else "⚠️",
                    fontSize = (48 * scale).sp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = alertTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    color = alertColor,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(Modifier.height(16.dp))

                // Hiển thị giá trị gas nổi bật
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(alertColor.copy(alpha = 0.1f))
                        .border(1.dp, alertColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nồng độ hiện tại", fontSize = 12.sp, color = TextMuted)
                        Text(
                            "$gasValue ppm",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black,
                            color = alertColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "Ngưỡng đặt: $threshold ppm  |  Vượt: +${gasValue - threshold} ppm",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = alertMsg,
                    fontSize = 13.sp,
                    color = TextPri,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
                    // Nút đóng
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BgStroke)
                    ) {
                        Text("Đã hiểu")
                    }
                    // Nút xác nhận hành động
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = alertColor)
                    ) {
                        Text("Xử lý ngay", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== GAS CARD ====================

@Composable
private fun GasCardModern(
    gas: Int,
    threshold: Int,
    onAlertClick: () -> Unit
) {
    val isDanger = gas >= 600
    val isWarning = gas >= threshold && !isDanger
    val isNormal = gas < threshold

    val statusColor = when {
        isDanger -> RedDanger
        isWarning -> AmberWarn
        else -> GreenSafe
    }

    val progress = (gas / 1000f).coerceIn(0f, 1f)
    val thresholdFraction = (threshold / 1000f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .then(
                if (isWarning || isDanger)
                    Modifier.border(1.5.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                else Modifier
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, null, tint = Teal, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Cảm biến Khí Gas",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPri
                )
                Spacer(Modifier.weight(1f))
                // Indicator trạng thái
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(statusColor, androidx.compose.foundation.shape.CircleShape)
                )
            }

            Text(
                "Nồng độ khí gas trong không khí",
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Giá trị PPM lớn
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$gas",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isNormal) TextPri else statusColor,
                    letterSpacing = (-1.5).sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "ppm",
                    fontSize = 18.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Thanh progress + vạch ngưỡng
            Box(Modifier.fillMaxWidth()) {
                // Nền thanh
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(BgRaised)
                )
                // Thanh giá trị thực tế
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(statusColor)
                )
                // Vạch ngưỡng người dùng đặt
                Box(
                    modifier = Modifier
                        .fillMaxWidth(thresholdFraction)
                        .height(12.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .background(CyanAcc)
                    )
                }
            }

            // Nhãn thước đo
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0", fontSize = 11.sp, color = TextMuted)
                Text(
                    "⚑ $threshold",
                    fontSize = 11.sp,
                    color = CyanAcc,
                    fontWeight = FontWeight.Bold
                )
                Text("600", fontSize = 11.sp, color = if (gas >= 600) RedDanger else TextMuted)
                Text("1000", fontSize = 11.sp, color = TextMuted)
            }

            Spacer(Modifier.height(16.dp))

            // Badge trạng thái + nút xem cảnh báo
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = when {
                            isDanger -> "🔴 Nguy hiểm"
                            isWarning -> "⚠ Vượt ngưỡng"
                            else -> "✅ An toàn"
                        },
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (isWarning || isDanger) {
                    TextButton(
                        onClick = onAlertClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = statusColor)
                    ) {
                        Icon(Icons.Default.Warning, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Xem cảnh báo", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==================== CÁC CARD KHÁC ====================

@Composable
private fun SystemStatusCard(status: String) {
    val (color, text) = when (status) {
        "DANGER" -> RedDanger to "⛔ NGUY HIỂM"
        "WARNING" -> AmberWarn to "⚠ CẢNH BÁO"
        else -> GreenSafe to "✅ AN TOÀN"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(2.dp, color.copy(0.3f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(color, androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun FlameCard(flame: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .then(
                if (flame) Modifier.border(1.5.dp, RedDanger.copy(0.4f), RoundedCornerShape(20.dp))
                else Modifier
            )
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Cảm biến Lửa", fontSize = 14.sp, color = TextMuted)
                Text(
                    if (flame) "PHÁT HIỆN LỬA!" else "KHÔNG CÓ LỬA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (flame) RedDanger else GreenSafe
                )
            }
            Text(if (flame) "🔥" else "✓", fontSize = 48.sp)
        }
    }
}

@Composable
private fun TempHumidCard(temperature: Double, humidity: Double) {
    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .padding(16.dp)
        ) {
            Column {
                Text("Nhiệt độ", color = TextMuted, fontSize = 13.sp)
                Text("${temperature}°C", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPri)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .padding(16.dp)
        ) {
            Column {
                Text("Độ ẩm", color = TextMuted, fontSize = 13.sp)
                Text("${humidity}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPri)
            }
        }
    }
}

@Composable
private fun EnvCard(pressure: Double, light: Int, uvIndex: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .padding(20.dp)
    ) {
        Column {
            Text("Môi trường", fontSize = 14.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Column {
                    Text("Áp suất", color = TextMuted, fontSize = 12.sp)
                    Text("${pressure} hPa", fontWeight = FontWeight.Bold, color = TextPri)
                }
                Column {
                    Text("Ánh sáng", color = TextMuted, fontSize = 12.sp)
                    Text("$light lux", fontWeight = FontWeight.Bold, color = TextPri)
                }
                Column {
                    Text("UV", color = TextMuted, fontSize = 12.sp)
                    Text("$uvIndex", fontWeight = FontWeight.Bold, color = TextPri)
                }
            }
        }
    }
}

// ==================== THRESHOLD CONTROL ====================

@Composable
private fun ThresholdControl(
    gasThreshold: Float,
    currentGas: Int,
    showThreshold: Boolean,
    onShowChange: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit
) {
    val thresholdInt = gasThreshold.toInt()
    val exceedBy = currentGas - thresholdInt
    val isExceeding = currentGas >= thresholdInt
    val borderColor = if (isExceeding) AmberWarn else BgStroke

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable { onShowChange(!showThreshold) }
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text("Ngưỡng cảnh báo Gas", fontSize = 15.sp, color = TextPri)
                    if (isExceeding) {
                        Text(
                            "⚠ Đang vượt +$exceedBy ppm",
                            fontSize = 11.sp,
                            color = AmberWarn,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    if (showThreshold) "▲ Thu gọn" else "▼ Chỉnh sửa",
                    fontSize = 12.sp,
                    color = CyanAcc
                )
            }

            if (showThreshold) {
                Spacer(Modifier.height(16.dp))

                // Giá trị ngưỡng
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$thresholdInt",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAcc,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("ppm", fontSize = 14.sp, color = TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                }

                Text(
                    text = when {
                        thresholdInt < 200 -> "⚠ Ngưỡng thấp — cảnh báo thường xuyên"
                        thresholdInt < 400 -> "✅ Ngưỡng an toàn tiêu chuẩn"
                        thresholdInt < 700 -> "ℹ Ngưỡng cao — ít nhạy cảm hơn"
                        else -> "🔴 Ngưỡng rất cao — chỉ cảnh báo nguy hiểm"
                    },
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Slider(
                    value = gasThreshold,
                    onValueChange = onValueChange,
                    valueRange = 100f..900f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanAcc,
                        activeTrackColor = CyanAcc,
                        inactiveTrackColor = BgRaised
                    )
                )

                // Gợi ý mức preset
                Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                    listOf(200 to "Nhạy", 300 to "Chuẩn", 500 to "Cao", 700 to "Rất cao").forEach { (preset, label) ->
                        OutlinedButton(
                            onClick = { onValueChange(preset.toFloat()) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (thresholdInt == preset) CyanAcc else TextMuted
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (thresholdInt == preset) CyanAcc else BgStroke
                            )
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, fontSize = 10.sp)
                                Text("$preset", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== MINI STAT CARD ====================

@Composable
private fun MiniStatCard(emoji: String, value: String, label: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, BgStroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPri,
                fontFamily = FontFamily.Monospace
            )
            Text(label, fontSize = 9.sp, color = TextMuted)
        }
    }
}