package com.etfmonitor.ui.screens.prediction

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.etfmonitor.R
import com.etfmonitor.database.entities.EnhancedPrediction
import com.etfmonitor.database.entities.EnhancedTrainingResult
import com.etfmonitor.ui.components.ErrorCard
import com.etfmonitor.ui.components.LoadingCard
import com.etfmonitor.ui.theme.*

/**
 * ML 기반 주가 상승 예측 화면
 *
 * 기능:
 * 1. ETF 구성 변화 데이터 기반 ML 예측 실행
 * 2. 상승 예상 종목 리스트 표시
 * 3. 예측 파라미터 설정
 * 4. 학습 결과 (정확도, 피처 중요도) 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionScreen(
    onNavigateBack: () -> Unit,
    viewModel: PredictionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val predictions by viewModel.predictions.collectAsState()
    val trainingResult by viewModel.trainingResult.collectAsState()
    val daysAfter by viewModel.daysAfter.collectAsState()
    val priceThreshold by viewModel.priceThreshold.collectAsState()
    val minConfidence by viewModel.minConfidence.collectAsState()
    val modelType by viewModel.modelType.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showTrainingResultDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // 성공/오류 메시지 표시
    LaunchedEffect(state) {
        when (val s = state) {
            is PredictionState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearState()
            }
            is PredictionState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.clearState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.prediction_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            stringResource(R.string.prediction_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = {
                    // 설정 버튼
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    }
                    // 학습 결과 버튼 (결과가 있을 때만)
                    if (trainingResult != null) {
                        IconButton(onClick = { showTrainingResultDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = stringResource(R.string.prediction_learning_result)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 예측 실행 버튼
            val isLoading = state is PredictionState.Loading
            ExtendedFloatingActionButton(
                text = {
                    Text(if (isLoading) stringResource(R.string.learning_in_progress) else stringResource(R.string.prediction_run))
                },
                icon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    }
                },
                onClick = { viewModel.runPrediction() },
                expanded = !isLoading,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val s = state) {
            is PredictionState.Initial -> {
                LoadingCard(
                    message = stringResource(R.string.data_loading),
                    modifier = Modifier.padding(paddingValues).padding(MaterialTheme.spacing.medium)
                )
            }
            is PredictionState.Loading -> {
                LoadingScreen(
                    message = s.message,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is PredictionState.NoPredictions -> {
                NoPredictionsContent(
                    modifier = Modifier.padding(paddingValues),
                    daysAfter = daysAfter,
                    priceThreshold = priceThreshold,
                    minConfidence = minConfidence
                )
            }
            else -> {
                PredictionContent(
                    predictions = predictions,
                    trainingResult = trainingResult,
                    daysAfter = daysAfter,
                    priceThreshold = priceThreshold,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }

    // 설정 다이얼로그
    if (showSettingsDialog) {
        PredictionSettingsDialog(
            daysAfter = daysAfter,
            priceThreshold = priceThreshold,
            minConfidence = minConfidence,
            modelType = modelType,
            onDaysAfterChange = viewModel::setDaysAfter,
            onPriceThresholdChange = viewModel::setPriceThreshold,
            onMinConfidenceChange = viewModel::setMinConfidence,
            onModelTypeChange = viewModel::setModelType,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // 학습 결과 다이얼로그
    trainingResult?.let { result ->
        if (showTrainingResultDialog) {
            TrainingResultDialog(
                result = result,
                onDismiss = { showTrainingResultDialog = false }
            )
        }
    }
}

@Composable
private fun LoadingScreen(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(MaterialTheme.spacing.large),
            shape = MaterialTheme.extendedShapes.cardLarge,
            elevation = CardDefaults.elevatedCardElevation(
                defaultElevation = MaterialTheme.elevation.level3
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large),
                modifier = Modifier.padding(MaterialTheme.spacing.extraLarge)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.prediction_loading_message),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoPredictionsContent(
    modifier: Modifier = Modifier,
    daysAfter: Int,
    priceThreshold: Double,
    minConfidence: Double
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
    ) {
        // 안내 카드
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.extendedShapes.cardLarge,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.prediction_what_is),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    stringResource(R.string.prediction_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider()

                Text(
                    stringResource(R.string.prediction_current_settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SettingChip("${daysAfter}일 후 예측")
                    SettingChip("${priceThreshold}% 이상 상승")
                    SettingChip("${(minConfidence * 100).toInt()}% 신뢰도")
                }
            }
        }

        // 사용 방법 카드
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.extendedShapes.card
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
            ) {
                Text(
                    stringResource(R.string.prediction_how_to_use),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                UsageStep(1, stringResource(R.string.prediction_step_1))
                UsageStep(2, stringResource(R.string.prediction_step_2))
                UsageStep(3, stringResource(R.string.prediction_step_3))
                UsageStep(4, stringResource(R.string.prediction_step_4))
            }
        }

        // 주의 사항
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            shape = MaterialTheme.extendedShapes.card,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(MaterialTheme.spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    stringResource(R.string.prediction_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun SettingChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun UsageStep(step: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PredictionContent(
    predictions: List<EnhancedPrediction>,
    trainingResult: EnhancedTrainingResult?,
    daysAfter: Int,
    priceThreshold: Double,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        contentPadding = PaddingValues(bottom = 88.dp) // FAB 공간 확보
    ) {
        // 요약 카드
        item {
            SummaryCard(
                predictedCount = predictions.size,
                accuracy = trainingResult?.cvAccuracy,
                f1Score = trainingResult?.cvF1,
                daysAfter = daysAfter,
                priceThreshold = priceThreshold
            )
        }

        // 예측 결과 헤더
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.spacing.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.format_expected_stocks),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.format_n_stocks, predictions.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 예측 결과 리스트
        items(predictions) { prediction ->
            PredictionCard(prediction = prediction)
        }
    }
}

@Composable
private fun SummaryCard(
    predictedCount: Int,
    accuracy: Double?,
    f1Score: Double? = null,
    daysAfter: Int,
    priceThreshold: Double
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.extendedShapes.cardLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "예측 결과",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${daysAfter}일 후 ${priceThreshold}% 이상 상승 예상",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 예측 종목 수
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.medium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = predictedCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            "종목",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // 학습 정확도 & F1 표시
            if (accuracy != null || f1Score != null) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (accuracy != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(accuracy * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = getAccuracyColor(accuracy)
                            )
                            Text(
                                "정확도",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (f1Score != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(f1Score * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = getAccuracyColor(f1Score)
                            )
                            Text(
                                "F1 Score",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PredictionCard(prediction: EnhancedPrediction) {
    val confidencePercent = (prediction.confidence * 100).toInt()
    val riskLevel = when {
        prediction.riskScore <= 0.3 -> "낮음"
        prediction.riskScore <= 0.6 -> "중간"
        else -> "높음"
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.extendedShapes.card
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 종목 정보
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    Text(
                        text = prediction.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusChip(status = prediction.status)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
                ) {
                    Text(
                        text = prediction.ticker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "위험도: $riskLevel",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            prediction.riskScore <= 0.3 -> MaterialTheme.extendedColors.statusIncrease
                            prediction.riskScore <= 0.6 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            // 신뢰도 표시
            ConfidenceIndicator(confidence = prediction.confidence)
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (text, color) = when (status) {
        "NEW" -> stringResource(R.string.status_new) to MaterialTheme.extendedColors.statusNew
        "INCREASED" -> stringResource(R.string.status_increase) to MaterialTheme.extendedColors.statusIncrease
        "DECREASED" -> stringResource(R.string.status_decrease) to MaterialTheme.extendedColors.statusDecrease
        else -> status to MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color
        )
    }
}

@Composable
private fun ConfidenceIndicator(confidence: Double) {
    val percent = (confidence * 100).toInt()
    val color = when {
        confidence >= 0.8 -> MaterialTheme.extendedColors.statusIncrease
        confidence >= 0.7 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = stringResource(R.string.advanced_confidence),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun getAccuracyColor(accuracy: Double): Color {
    return when {
        accuracy >= 0.7 -> MaterialTheme.extendedColors.statusIncrease
        accuracy >= 0.5 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
}

// ========== 다이얼로그들 ==========

@Composable
private fun PredictionSettingsDialog(
    daysAfter: Int,
    priceThreshold: Double,
    minConfidence: Double,
    modelType: String,
    onDaysAfterChange: (Int) -> Unit,
    onPriceThresholdChange: (Double) -> Unit,
    onMinConfidenceChange: (Double) -> Unit,
    onModelTypeChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val daysOptions = listOf(3, 5, 7, 10, 14)
    val thresholdOptions = listOf(2.0, 3.0, 5.0, 7.0, 10.0)
    val confidenceOptions = listOf(0.5, 0.6, 0.7, 0.8)
    val modelOptions = listOf(
        "voting" to "앙상블 (추천)",
        "xgboost" to "XGBoost",
        "lightgbm" to "LightGBM",
        "random_forest" to "Random Forest",
        "gradient_boosting" to "Gradient Boosting"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("예측 설정") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                // 예측 기간
                Text(
                    "예측 기간",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    daysOptions.forEach { days ->
                        FilterChip(
                            selected = daysAfter == days,
                            onClick = { onDaysAfterChange(days) },
                            label = { Text("${days}일") }
                        )
                    }
                }

                HorizontalDivider()

                // 상승 기준
                Text(
                    "상승 판단 기준",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    thresholdOptions.forEach { threshold ->
                        FilterChip(
                            selected = priceThreshold == threshold,
                            onClick = { onPriceThresholdChange(threshold) },
                            label = { Text("${threshold.toInt()}%") }
                        )
                    }
                }

                HorizontalDivider()

                // 최소 신뢰도
                Text(
                    "최소 신뢰도",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    confidenceOptions.forEach { conf ->
                        FilterChip(
                            selected = minConfidence == conf,
                            onClick = { onMinConfidenceChange(conf) },
                            label = { Text("${(conf * 100).toInt()}%") }
                        )
                    }
                }

                HorizontalDivider()

                // 모델 타입
                Text(
                    "ML 모델 (28개 Feature)",
                    style = MaterialTheme.typography.labelLarge
                )
                modelOptions.forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = modelType == type,
                                onClick = { onModelTypeChange(type) }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = modelType == type,
                            onClick = { onModelTypeChange(type) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

@Composable
private fun TrainingResultDialog(
    result: EnhancedTrainingResult,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("학습 결과 (28 Features)") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)
            ) {
                // 모델 정보
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("모델 타입", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when (result.modelType) {
                            "voting" -> "앙상블"
                            "xgboost" -> "XGBoost"
                            "lightgbm" -> "LightGBM"
                            "random_forest" -> "Random Forest"
                            "gradient_boosting" -> "Gradient Boosting"
                            else -> result.modelType
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("학습 샘플 수", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${result.sampleCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Feature 수", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${result.featureCount}개",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                // 성능 지표 (CV)
                Text(
                    "성능 지표 (교차검증)",
                    style = MaterialTheme.typography.labelLarge
                )

                MetricRow("정확도 (Accuracy)", result.cvAccuracy)
                MetricRow("정밀도 (Precision)", result.cvPrecision)
                MetricRow("재현율 (Recall)", result.cvRecall)
                MetricRow("F1 Score", result.cvF1)

                // Top Features
                if (result.topFeatures.isNotEmpty()) {
                    HorizontalDivider()

                    Text(
                        "핵심 Feature (Top 5)",
                        style = MaterialTheme.typography.labelLarge
                    )

                    result.topFeatures.take(5).forEachIndexed { index, feature ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                feature,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // 학습 시간
                if (result.trainingTimeMs > 0) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("학습 시간", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${result.trainingTimeMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        },
        shape = MaterialTheme.extendedShapes.cardLarge
    )
}

@Composable
private fun MetricRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = getAccuracyColor(value)
        )
    }
}
