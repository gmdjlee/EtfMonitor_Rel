package com.etfmonitor.feature.home.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.etfmonitor.core.ui.theme.*

/**
 * API 키 입력 다이얼로그
 *
 * 앱 첫 실행 시 통합 초기화 다이얼로그보다 먼저 표시됩니다.
 * KIS Open API, FRED API, AI API 키를 입력받습니다.
 *
 * - KIS / FRED: 필수 입력 아님 (나중에 설정 가능)
 * - AI API: 체크박스로 사용 여부를 선택, 사용 시 키 입력
 *
 * @param onConfirm 등록 버튼 클릭 시 호출
 * @param onDismiss 나중에 버튼 클릭 시 호출
 */
@Composable
internal fun ApiKeyInputDialog(
    onConfirm: (
        kisAppKey: String,
        kisAppSecret: String,
        fredApiKey: String,
        aiProvider: String?,
        aiApiKey: String?,
        kiwoomAppKey: String,
        kiwoomSecretKey: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var kisAppKey by remember { mutableStateOf("") }
    var kisAppSecret by remember { mutableStateOf("") }
    var fredApiKey by remember { mutableStateOf("") }

    var useAiApi by remember { mutableStateOf(false) }
    var selectedAiProvider by remember { mutableStateOf("CLAUDE") }
    var aiApiKey by remember { mutableStateOf("") }

    var kiwoomAppKey by remember { mutableStateOf("") }
    var kiwoomSecretKey by remember { mutableStateOf("") }

    // 최소 하나의 키가 입력되었거나, 아무 것도 입력하지 않아도 등록 가능 (나중에 설정 가능)
    val hasAnyInput = kisAppKey.trim().isNotBlank() ||
            kisAppSecret.trim().isNotBlank() ||
            fredApiKey.trim().isNotBlank() ||
            (useAiApi && aiApiKey.trim().isNotBlank()) ||
            kiwoomAppKey.trim().isNotBlank() ||
            kiwoomSecretKey.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "API 키 설정",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        text = "앱에서 사용하는 API 키를 등록합니다. " +
                                "모든 키는 선택 사항이며, 나중에 설정 화면에서도 등록할 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // ── KIS API Section ──
                Text(
                    text = "KIS Open API",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "재무 정보 조회에 사용됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = kisAppKey,
                    onValueChange = { kisAppKey = it },
                    label = { Text("앱키 (App Key)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kisAppSecret,
                    onValueChange = { kisAppSecret = it },
                    label = { Text("앱시크릿 (App Secret)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ── FRED API Section ──
                Text(
                    text = "FRED API",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Blood Indicator(하이일드 스프레드) 조회에 사용됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fredApiKey,
                    onValueChange = { fredApiKey = it },
                    label = { Text("FRED API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ── Kiwoom API Section ──
                Text(
                    text = "Kiwoom Open API",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "실시간 순위 데이터 조회에 사용됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = kiwoomAppKey,
                    onValueChange = { kiwoomAppKey = it },
                    label = { Text("앱키 (App Key)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kiwoomSecretKey,
                    onValueChange = { kiwoomSecretKey = it },
                    label = { Text("시크릿키 (Secret Key)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ── AI API Section (Optional) ──
                Text(
                    text = "AI API (선택)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = useAiApi,
                        onCheckedChange = { useAiApi = it }
                    )
                    Text(
                        text = "AI 분석 기능 사용",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                if (useAiApi) {
                    Text(
                        text = "AI 시장 분석 및 종목 분석에 사용됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // AI Provider Selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedAiProvider == "CLAUDE",
                                onClick = { selectedAiProvider = "CLAUDE" }
                            )
                            Text(
                                text = "Claude",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedAiProvider == "GEMINI",
                                onClick = { selectedAiProvider = "GEMINI" }
                            )
                            Text(
                                text = "Gemini",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = aiApiKey,
                        onValueChange = { aiApiKey = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.extendedShapes.card
                ) {
                    Text(
                        text = "모든 키는 기기 내 암호화 저장소에 안전하게 보관됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(
                        kisAppKey.trim(),
                        kisAppSecret.trim(),
                        fredApiKey.trim(),
                        if (useAiApi && aiApiKey.trim().isNotBlank()) selectedAiProvider else null,
                        if (useAiApi) aiApiKey.trim().takeIf { it.isNotBlank() } else null,
                        kiwoomAppKey.trim(),
                        kiwoomSecretKey.trim()
                    )
                },
                shape = MaterialTheme.extendedShapes.button
            ) {
                Text(if (hasAnyInput) "등록" else "건너뛰기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("나중에")
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}
