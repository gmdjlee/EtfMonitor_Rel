package com.etfmonitor.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.etfmonitor.ui.theme.*
import kotlin.math.roundToInt

// 확장된 색상 팔레트 - Windows 스타일 색상 선택기와 유사
// Row 1: 밝은 색상
private val colorPaletteRow1 = listOf(
    Color(0xFFFF8080), Color(0xFFFFFF80), Color(0xFF80FF80), Color(0xFF00FF80),
    Color(0xFF80FFFF), Color(0xFF0080FF), Color(0xFFFF80C0), Color(0xFFFF80FF)
)
// Row 2: 기본 밝은 색상
private val colorPaletteRow2 = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF80FF00), Color(0xFF00FF40),
    Color(0xFF00FFFF), Color(0xFF0080C0), Color(0xFF8080C0), Color(0xFFFF00FF)
)
// Row 3: 중간 색상
private val colorPaletteRow3 = listOf(
    Color(0xFF804040), Color(0xFFFF8040), Color(0xFF00FF00), Color(0xFF008080),
    Color(0xFF004080), Color(0xFF8080FF), Color(0xFF800040), Color(0xFFFF0080)
)
// Row 4: 어두운 색상
private val colorPaletteRow4 = listOf(
    Color(0xFF800000), Color(0xFFFF8000), Color(0xFF008000), Color(0xFF008040),
    Color(0xFF0000FF), Color(0xFF0000A0), Color(0xFF800080), Color(0xFF8000FF)
)
// Row 5: 진한 색상
private val colorPaletteRow5 = listOf(
    Color(0xFF400000), Color(0xFF804000), Color(0xFF004000), Color(0xFF004040),
    Color(0xFF000080), Color(0xFF000040), Color(0xFF400040), Color(0xFF400080)
)
// Row 6: 그레이스케일 + 추가 색상
private val colorPaletteRow6 = listOf(
    Color(0xFF000000), Color(0xFF404040), Color(0xFF808080), Color(0xFFA0A0A0),
    Color(0xFFC0C0C0), Color(0xFFD4D4D4), Color(0xFFE8E8E8), Color(0xFFFFFFFF)
)

// 차트 기본 색상 (빠른 선택용)
private val chartDefaultColors = listOf(
    ChartPrimary,
    ChartSecondary,
    ChartTertiary,
    ChartGreen,
    ChartRed,
    ChartBlue,
    ChartOrange,
    ChartPurple,
    ChartCyan,
    ChartPink
)

/**
 * 필수 색상 선택 행
 * 현재 색상을 표시하고 클릭하면 색상 선택 다이얼로그를 엽니다.
 */
@Composable
fun ColorPickerRow(
    label: String,
    currentColor: Int,
    onColorSelected: (Int) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 현재 색상 표시
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(currentColor))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showColorPicker = true }
            )

            IconButton(onClick = { showColorPicker = true }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "색상 변경",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = currentColor,
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker = false
            }
        )
    }
}

/**
 * 선택적 색상 선택 행
 * null일 경우 테마 기본값을 사용하고, 초기화 버튼을 제공합니다.
 */
@Composable
fun OptionalColorPickerRow(
    label: String,
    currentColor: Int?,
    onColorSelected: (Int) -> Unit,
    onReset: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentColor != null) {
                // 현재 색상 표시
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(currentColor))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { showColorPicker = true }
                )

                // 초기화 버튼
                IconButton(onClick = onReset) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "기본값으로",
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                // 기본값 사용 중
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "테마 기본값",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(onClick = { showColorPicker = true }) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "색상 변경",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            currentColor = currentColor ?: ChartTextLight.toArgb(),
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                onColorSelected(color)
                showColorPicker = false
            }
        )
    }
}

/**
 * 색상 선택 다이얼로그
 * Windows 스타일의 확장된 색상 팔레트, RGB 슬라이더, Hex 입력을 제공합니다.
 */
@Composable
fun ColorPickerDialog(
    currentColor: Int,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }
    var redValue by remember { mutableFloatStateOf(((currentColor shr 16) and 0xFF).toFloat()) }
    var greenValue by remember { mutableFloatStateOf(((currentColor shr 8) and 0xFF).toFloat()) }
    var blueValue by remember { mutableFloatStateOf((currentColor and 0xFF).toFloat()) }
    var hexInput by remember {
        mutableStateOf(String.format("%06X", currentColor and 0xFFFFFF))
    }
    var isHexInputError by remember { mutableStateOf(false) }

    // RGB 값이 변경되면 selectedColor 업데이트
    LaunchedEffect(redValue, greenValue, blueValue) {
        val newColor = (0xFF shl 24) or
                      (redValue.roundToInt() shl 16) or
                      (greenValue.roundToInt() shl 8) or
                      blueValue.roundToInt()
        selectedColor = newColor
        hexInput = String.format("%06X", newColor and 0xFFFFFF)
        isHexInputError = false
    }

    // 팔레트에서 색상 선택시 RGB 슬라이더도 업데이트
    fun updateFromColor(color: Int) {
        selectedColor = color
        redValue = ((color shr 16) and 0xFF).toFloat()
        greenValue = ((color shr 8) and 0xFF).toFloat()
        blueValue = (color and 0xFF).toFloat()
        hexInput = String.format("%06X", color and 0xFFFFFF)
        isHexInputError = false
    }

    // Hex 입력 파싱
    fun parseHexInput(input: String) {
        val cleanInput = input.replace("#", "").uppercase()
        if (cleanInput.length == 6 && cleanInput.all { it in '0'..'9' || it in 'A'..'F' }) {
            try {
                val parsedColor = (0xFF shl 24) or cleanInput.toLong(16).toInt()
                updateFromColor(parsedColor)
            } catch (e: Exception) {
                isHexInputError = true
            }
        } else {
            isHexInputError = input.isNotEmpty()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("색상 선택") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 미리보기: 새 색상 / 현재 색상
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "새 색상",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                                .background(Color(selectedColor))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                )
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "현재 색상",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                                .background(Color(currentColor))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                )
                        )
                    }
                }

                HorizontalDivider()

                // 차트 기본 색상 (빠른 선택)
                Text(
                    "차트 기본 색상",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chartDefaultColors.size) { index ->
                        val color = chartDefaultColors[index]
                        val isSelected = color.toArgb() == selectedColor
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { updateFromColor(color.toArgb()) }
                        )
                    }
                }

                HorizontalDivider()

                // 확장 색상 팔레트 (그리드)
                Text(
                    "색상 팔레트",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 8열 그리드 색상 팔레트
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        colorPaletteRow1,
                        colorPaletteRow2,
                        colorPaletteRow3,
                        colorPaletteRow4,
                        colorPaletteRow5,
                        colorPaletteRow6
                    ).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { color ->
                                val isSelected = color.toArgb() == selectedColor
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 2.dp else 0.5.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable { updateFromColor(color.toArgb()) }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // RGB 슬라이더
                Text(
                    "RGB 조절",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 빨강 슬라이더
                ColorSliderRow(
                    label = "R",
                    value = redValue,
                    onValueChange = { redValue = it },
                    color = Color.Red
                )

                // 초록 슬라이더
                ColorSliderRow(
                    label = "G",
                    value = greenValue,
                    onValueChange = { greenValue = it },
                    color = Color.Green
                )

                // 파랑 슬라이더
                ColorSliderRow(
                    label = "B",
                    value = blueValue,
                    onValueChange = { blueValue = it },
                    color = Color.Blue
                )

                HorizontalDivider()

                // Hex 코드 입력
                Text(
                    "Hex 코드",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        val filtered = input.filter { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }
                            .take(6)
                            .uppercase()
                        hexInput = filtered
                        if (filtered.length == 6) {
                            parseHexInput(filtered)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("#") },
                    singleLine = true,
                    isError = isHexInputError,
                    supportingText = if (isHexInputError) {
                        { Text("6자리 16진수를 입력하세요 (예: FF5500)") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(selectedColor) }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * RGB 슬라이더 행
 */
@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(20.dp),
            color = color
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
        Text(
            text = value.roundToInt().toString(),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(32.dp)
        )
    }
}
