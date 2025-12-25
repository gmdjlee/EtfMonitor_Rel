package com.etfmonitor.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.etfmonitor.R
import com.etfmonitor.core.network.ai.AIModel
import com.etfmonitor.core.network.ai.AIProvider
import com.etfmonitor.ui.screens.settings.ApiKeyTestState
import com.etfmonitor.core.ui.theme.FontScaleSettings

/**
 * Settings Screen - General Tab Card Components
 * Contains ThemeSettingCard, AIApiKeyCard, FontScaleCard and related components
 */

@Composable
fun QuickChartAnalysisCard(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
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
                    Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(stringResource(R.string.settings_quick_chart_analysis_title), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_quick_chart_analysis_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEnabledChange(!isEnabled) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_quick_chart_analysis_enable),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.settings_quick_chart_analysis_enable_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }
    }
}

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
                Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_theme_desc),
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
                        Text(stringResource(R.string.settings_theme_system), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_theme_system_desc),
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
                        Text(stringResource(R.string.settings_theme_light), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_theme_light_desc),
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
                        Text(stringResource(R.string.settings_theme_dark), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.settings_theme_dark_desc),
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
                Text(stringResource(R.string.settings_ai_api_title), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_ai_api_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            // AI 프로바이더 선택
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.settings_ai_provider_select),
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
                                    stringResource(R.string.settings_api_key_set_check),
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
                                    stringResource(R.string.settings_api_key_set_check),
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
                                    if (currentIsConfigured) stringResource(R.string.settings_api_key_set) else stringResource(R.string.settings_api_key_not_set),
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
                    Text(if (currentIsConfigured) stringResource(R.string.settings_action_change) else stringResource(R.string.settings_action_set))
                }

                if (currentIsConfigured) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        modifier = Modifier.weight(1f),
                        enabled = testState !is ApiKeyTestState.Testing
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.settings_action_test))
                    }

                    IconButton(
                        onClick = { showClearConfirmDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.settings_action_delete),
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
            title = stringResource(R.string.settings_api_key_claude_title),
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
            title = stringResource(R.string.settings_api_key_gemini_title),
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
        val providerName = selectedProvider.toDisplayName()
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text(stringResource(R.string.settings_api_key_delete)) },
            text = { Text(stringResource(R.string.settings_api_key_delete_confirm, providerName)) },
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
                    Text(stringResource(R.string.settings_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
                Text(stringResource(R.string.settings_api_testing), style = MaterialTheme.typography.bodySmall)
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
                        stringResource(R.string.settings_api_success),
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
                        stringResource(R.string.settings_api_fail, testState.message),
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
                        stringResource(R.string.settings_api_key_claude_url),
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
                        stringResource(R.string.settings_api_key_gemini_url),
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
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.settings_api_key_enter),
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text(placeholder) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                )

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        stringResource(R.string.settings_api_key_secure),
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
                Text(stringResource(R.string.settings_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
                Text(stringResource(R.string.settings_font_scale), style = MaterialTheme.typography.titleMedium)
            }

            HorizontalDivider()

            Text(
                stringResource(R.string.settings_font_scale_desc),
                style = MaterialTheme.typography.bodyMedium
            )

            // Display
            FontScaleSlider(
                label = "Display",
                description = stringResource(R.string.settings_font_display),
                currentScale = fontScaleSettings.displayScale,
                onScaleChange = onDisplayScaleChange
            )

            // Headline
            FontScaleSlider(
                label = "Headline",
                description = stringResource(R.string.settings_font_headline),
                currentScale = fontScaleSettings.headlineScale,
                onScaleChange = onHeadlineScaleChange
            )

            // Title
            FontScaleSlider(
                label = "Title",
                description = stringResource(R.string.settings_font_title),
                currentScale = fontScaleSettings.titleScale,
                onScaleChange = onTitleScaleChange
            )

            // Body
            FontScaleSlider(
                label = "Body",
                description = stringResource(R.string.settings_font_body),
                currentScale = fontScaleSettings.bodyScale,
                onScaleChange = onBodyScaleChange,
                minScale = 1.1f,
                maxScale = 1.8f,
                steps = 6
            )

            // Label
            FontScaleSlider(
                label = "Label",
                description = stringResource(R.string.settings_font_label),
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
            stringResource(R.string.settings_model_select, providerName),
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
                Text(stringResource(R.string.settings_model_load))
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
                Text(stringResource(R.string.settings_model_loading), style = MaterialTheme.typography.bodySmall)
            }
        }

        val modelSelectPlaceholder = stringResource(R.string.settings_model_select_placeholder)
        val selectedModelLabel = stringResource(R.string.settings_model_selected)
        // 모델 목록이 있을 때 드롭다운 표시
        if (models.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandChanged
            ) {
                OutlinedTextField(
                    value = selectedModel?.let { modelId ->
                        models.find { it.id == modelId }?.displayName() ?: modelId
                    } ?: modelSelectPlaceholder,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(selectedModelLabel) },
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
                                        val inputLabel = stringResource(R.string.settings_model_input, model.contextWindow ?: "-")
                                        val outputLabel = stringResource(R.string.settings_model_output, model.maxOutputTokens ?: "-")
                                        val tokenLabel = stringResource(R.string.settings_model_tokens)
                                        Text(
                                            buildString {
                                                if (model.contextWindow != null) {
                                                    append(inputLabel)
                                                }
                                                if (model.maxOutputTokens != null) {
                                                    if (model.contextWindow != null) append(" | ")
                                                    append(outputLabel)
                                                }
                                                append(" $tokenLabel")
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
                    Text(stringResource(R.string.nav_refresh), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
