package com.etfmonitor.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etfmonitor.ai.AIModel
import com.etfmonitor.ai.AIProvider
import com.etfmonitor.ui.screens.settings.ApiKeyTestState
import com.etfmonitor.ui.theme.FontScaleSettings

/**
 * Settings Screen - General Tab Card Components
 * Contains ThemeSettingCard, AIApiKeyCard, FontScaleCard and related components
 */

@Composable
fun ThemeSettingCard(
    isDarkTheme: Boolean?,
    onThemeChange: (Boolean?) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    when (isDarkTheme) {
                        true -> Icons.Default.DarkMode
                        false -> Icons.Default.LightMode
                        null -> Icons.Default.BrightnessAuto
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("테마 설정", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "앱의 테마를 변경합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 시스템 설정
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(null) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isDarkTheme == null,
                        onClick = { onThemeChange(null) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.BrightnessAuto, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("시스템 설정", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "기기의 테마 설정을 따릅니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 라이트 모드
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(false) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isDarkTheme == false,
                        onClick = { onThemeChange(false) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.LightMode, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("라이트 모드", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "밝은 테마를 사용합니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 다크 모드
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeChange(true) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isDarkTheme == true,
                        onClick = { onThemeChange(true) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.DarkMode, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("다크 모드", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "어두운 테마를 사용합니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AIApiKeyCard(
    selectedProvider: AIProvider,
    isClaudeConfigured: Boolean,
    isGeminiConfigured: Boolean,
    testState: ApiKeyTestState,
    claudeModels: List<AIModel>,
    geminiModels: List<AIModel>,
    selectedClaudeModel: String?,
    selectedGeminiModel: String?,
    isLoadingClaudeModels: Boolean,
    isLoadingGeminiModels: Boolean,
    onProviderSelected: (AIProvider) -> Unit,
    onSetClaudeApiKey: (String) -> Unit,
    onSetGeminiApiKey: (String) -> Unit,
    onClearClaudeApiKey: () -> Unit,
    onClearGeminiApiKey: () -> Unit,
    onTestConnection: () -> Unit,
    onClearTestState: () -> Unit,
    onLoadClaudeModels: () -> Unit,
    onLoadGeminiModels: () -> Unit,
    onSelectClaudeModel: (String) -> Unit,
    onSelectGeminiModel: (String) -> Unit
) {
    var showClaudeDialog by remember { mutableStateOf(false) }
    var showGeminiDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var expandedClaudeModels by remember { mutableStateOf(false) }
    var expandedGeminiModels by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("AI API 설정", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "AI 시장 분석 기능을 사용하려면 API 키가 필요합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            // AI 프로바이더 선택
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "AI 제공자 선택",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(8.dp))

                    // Claude 선택
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProviderSelected(AIProvider.CLAUDE) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProvider == AIProvider.CLAUDE,
                            onClick = { onProviderSelected(AIProvider.CLAUDE) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Claude (Anthropic)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (isClaudeConfigured) {
                                Text(
                                    "✓ API 키 설정됨",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Gemini 선택
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProviderSelected(AIProvider.GEMINI) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProvider == AIProvider.GEMINI,
                            onClick = { onProviderSelected(AIProvider.GEMINI) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Gemini (Google)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (isGeminiConfigured) {
                                Text(
                                    "✓ API 키 설정됨",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            val currentIsConfigured = when (selectedProvider) {
                AIProvider.CLAUDE -> isClaudeConfigured
                AIProvider.GEMINI -> isGeminiConfigured
            }

            // 선택된 프로바이더의 API 키 상태 표시
            Surface(
                color = if (currentIsConfigured)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (currentIsConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (currentIsConfigured)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "${selectedProvider.toDisplayName()} " +
                                    if (currentIsConfigured) "API 키 설정됨" else "API 키 미설정",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (currentIsConfigured)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // API 테스트 상태 표시
            ApiTestStateIndicator(testState)

            // 모델 선택 (API 키가 설정된 경우에만 표시)
            if (currentIsConfigured) {
                HorizontalDivider()

                // 현재 선택된 제공자에 따라 모델 선택 UI 표시
                when (selectedProvider) {
                    AIProvider.CLAUDE -> {
                        ModelSelectionSection(
                            providerName = "Claude",
                            models = claudeModels,
                            selectedModel = selectedClaudeModel,
                            isLoading = isLoadingClaudeModels,
                            expanded = expandedClaudeModels,
                            onExpandChanged = { expandedClaudeModels = it },
                            onLoadModels = onLoadClaudeModels,
                            onSelectModel = onSelectClaudeModel
                        )
                    }
                    AIProvider.GEMINI -> {
                        ModelSelectionSection(
                            providerName = "Gemini",
                            models = geminiModels,
                            selectedModel = selectedGeminiModel,
                            isLoading = isLoadingGeminiModels,
                            expanded = expandedGeminiModels,
                            onExpandChanged = { expandedGeminiModels = it },
                            onLoadModels = onLoadGeminiModels,
                            onSelectModel = onSelectGeminiModel
                        )
                    }
                }
            }

            // 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        when (selectedProvider) {
                            AIProvider.CLAUDE -> showClaudeDialog = true
                            AIProvider.GEMINI -> showGeminiDialog = true
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        if (currentIsConfigured) Icons.Default.Edit else Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (currentIsConfigured) "변경" else "설정")
                }

                if (currentIsConfigured) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        modifier = Modifier.weight(1f),
                        enabled = testState !is ApiKeyTestState.Testing
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("테스트")
                    }

                    IconButton(
                        onClick = { showClearConfirmDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 안내 문구
            ApiKeyInfoSection(selectedProvider)
        }
    }

    // Claude API 키 입력 다이얼로그
    if (showClaudeDialog) {
        ApiKeyInputDialog(
            title = "Claude API 키 설정",
            placeholder = "sk-ant-...",
            onDismiss = { showClaudeDialog = false },
            onConfirm = { apiKey ->
                onSetClaudeApiKey(apiKey)
                showClaudeDialog = false
                onClearTestState()
            }
        )
    }

    // Gemini API 키 입력 다이얼로그
    if (showGeminiDialog) {
        ApiKeyInputDialog(
            title = "Gemini API 키 설정",
            placeholder = "AIza...",
            onDismiss = { showGeminiDialog = false },
            onConfirm = { apiKey ->
                onSetGeminiApiKey(apiKey)
                showGeminiDialog = false
                onClearTestState()
            }
        )
    }

    // 삭제 확인 다이얼로그
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("API 키 삭제") },
            text = { Text("${selectedProvider.toDisplayName()} API 키를 삭제하시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        when (selectedProvider) {
                            AIProvider.CLAUDE -> onClearClaudeApiKey()
                            AIProvider.GEMINI -> onClearGeminiApiKey()
                        }
                        showClearConfirmDialog = false
                        onClearTestState()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun ApiTestStateIndicator(testState: ApiKeyTestState) {
    when (testState) {
        is ApiKeyTestState.Testing -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("API 연결 테스트 중...", style = MaterialTheme.typography.bodySmall)
            }
        }
        is ApiKeyTestState.Success -> {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "API 연결 성공!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
        is ApiKeyTestState.Error -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "연결 실패: ${testState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun ApiKeyInfoSection(selectedProvider: AIProvider) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            when (selectedProvider) {
                AIProvider.CLAUDE -> {
                    Text(
                        "Claude API 키 발급:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "https://console.anthropic.com/settings/keys",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                AIProvider.GEMINI -> {
                    Text(
                        "Gemini API 키 발급:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "https://aistudio.google.com/app/apikey",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun ApiKeyInputDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "API 키를 입력하세요",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text(placeholder) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "API 키는 안전하게 암호화되어 기기에 저장됩니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(apiKey) },
                enabled = apiKey.isNotBlank()
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
fun FontScaleCard(
    fontScaleSettings: FontScaleSettings,
    onDisplayScaleChange: (Float) -> Unit,
    onHeadlineScaleChange: (Float) -> Unit,
    onTitleScaleChange: (Float) -> Unit,
    onBodyScaleChange: (Float) -> Unit,
    onLabelScaleChange: (Float) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("폰트 크기", style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                "각 스타일별 폰트 크기를 조절합니다",
                style = MaterialTheme.typography.bodyMedium
            )

            // Display
            FontScaleSlider(
                label = "Display",
                description = "대형 헤더 (57sp, 45sp, 36sp)",
                currentScale = fontScaleSettings.displayScale,
                onScaleChange = onDisplayScaleChange
            )

            // Headline
            FontScaleSlider(
                label = "Headline",
                description = "섹션 헤더 (32sp, 28sp, 24sp)",
                currentScale = fontScaleSettings.headlineScale,
                onScaleChange = onHeadlineScaleChange
            )

            // Title
            FontScaleSlider(
                label = "Title",
                description = "카드/컴포넌트 제목 (22sp, 16sp, 14sp)",
                currentScale = fontScaleSettings.titleScale,
                onScaleChange = onTitleScaleChange
            )

            // Body
            FontScaleSlider(
                label = "Body",
                description = "본문/테이블 텍스트 (12sp ~ 20sp)",
                currentScale = fontScaleSettings.bodyScale,
                onScaleChange = onBodyScaleChange,
                minScale = 1.1f,
                maxScale = 1.8f,
                steps = 6
            )

            // Label
            FontScaleSlider(
                label = "Label",
                description = "버튼, 태그, 캡션 (14sp, 12sp, 11sp)",
                currentScale = fontScaleSettings.labelScale,
                onScaleChange = onLabelScaleChange
            )
        }
    }
}

@Composable
fun FontScaleSlider(
    label: String,
    description: String,
    currentScale: Float,
    onScaleChange: (Float) -> Unit,
    minScale: Float = 0.8f,
    maxScale: Float = 1.4f,
    steps: Int = 5
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${(currentScale * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = currentScale,
            onValueChange = {
                val rounded = (it * 10).toInt() / 10f
                onScaleChange(rounded)
            },
            valueRange = minScale..maxScale,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionSection(
    providerName: String,
    models: List<AIModel>,
    selectedModel: String?,
    isLoading: Boolean,
    expanded: Boolean,
    onExpandChanged: (Boolean) -> Unit,
    onLoadModels: () -> Unit,
    onSelectModel: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "$providerName 모델 선택",
            style = MaterialTheme.typography.labelLarge
        )

        // 모델 목록이 비어있으면 로드 버튼 표시
        if (models.isEmpty() && !isLoading) {
            OutlinedButton(
                onClick = onLoadModels,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("사용 가능한 모델 불러오기")
            }
        }

        // 로딩 중일 때
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("모델 목록 불러오는 중...", style = MaterialTheme.typography.bodySmall)
            }
        }

        // 모델 목록이 있을 때 드롭다운 표시
        if (models.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandChanged
            ) {
                OutlinedTextField(
                    value = selectedModel?.let { modelId ->
                        models.find { it.id == modelId }?.displayName() ?: modelId
                    } ?: "모델을 선택하세요",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("선택된 모델") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandChanged(false) }
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        model.displayName(),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (model.description != null) {
                                        Text(
                                            model.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (model.contextWindow != null || model.maxOutputTokens != null) {
                                        Text(
                                            buildString {
                                                if (model.contextWindow != null) {
                                                    append("입력: ${model.contextWindow}")
                                                }
                                                if (model.maxOutputTokens != null) {
                                                    if (model.contextWindow != null) append(" | ")
                                                    append("출력: ${model.maxOutputTokens}")
                                                }
                                                append(" 토큰")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectModel(model.id)
                                onExpandChanged(false)
                            },
                            leadingIcon = if (selectedModel == model.id) {
                                {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            // 새로고침 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onLoadModels,
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("새로고침", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
