package app.zcal.ui.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.zcal.ui.localization.LocalZCalStrings
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.zcal.data.local.MealTypeDistributionRow
import app.zcal.data.local.TopFoodRow
import app.zcal.data.local.WeekdayAverageRow
import app.zcal.data.repo.MealRepository
import app.zcal.data.repo.ProfileRepository
import app.zcal.data.repo.WeightRepository
import app.zcal.domain.model.DaySummary
import app.zcal.domain.model.UserProfile
import app.zcal.domain.model.WeightEntry
import app.zcal.domain.usecase.AdaptiveTdeeUseCase
import app.zcal.ui.components.DailyCaloriesBarChart
import app.zcal.ui.components.MacroSplitDonutCard
import app.zcal.ui.components.MonthCalendarHeatMap
import app.zcal.ui.components.WeightTrendChartCard
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import app.zcal.util.DateHelpers
import app.zcal.util.ExportHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val mealRepository: MealRepository,
    private val profileRepository: ProfileRepository,
    private val weightRepository: WeightRepository,
    val adaptiveTdeeUseCase: AdaptiveTdeeUseCase
) : ViewModel() {

    private val _selectedYearMonth = MutableStateFlow(YearMonth.now())
    val selectedYearMonth: StateFlow<YearMonth> = _selectedYearMonth.asStateFlow()

    val userProfile: StateFlow<UserProfile> = profileRepository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val calmPalette: StateFlow<Boolean> = profileRepository.calmPaletteFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val forgiveOneDay: StateFlow<Boolean> = profileRepository.forgiveOneDayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideNumbers: StateFlow<Boolean> = profileRepository.hideNumbersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val monthSummaries: StateFlow<List<DaySummary>> = _selectedYearMonth
        .flatMapLatest { ym ->
            val start = ym.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val end = ym.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)
            mealRepository.observeDaySummariesInRange(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prevMonthSummaries: StateFlow<List<DaySummary>> = _selectedYearMonth
        .flatMapLatest { ym ->
            val prev = ym.minusMonths(1)
            val start = prev.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val end = prev.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)
            mealRepository.observeDaySummariesInRange(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekdayPattern: StateFlow<List<WeekdayAverageRow>> = _selectedYearMonth
        .flatMapLatest { ym ->
            val start = ym.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val end = ym.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)
            mealRepository.observeWeekdayPattern(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topFoodsByCalories: StateFlow<List<TopFoodRow>> = _selectedYearMonth
        .flatMapLatest { ym ->
            val start = ym.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val end = ym.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)
            mealRepository.observeTopFoodsByCalories(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topFoodsByProtein: StateFlow<List<TopFoodRow>> = _selectedYearMonth
        .flatMapLatest { ym ->
            val start = ym.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val end = ym.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)
            mealRepository.observeTopFoodsByProtein(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mealTypeDistribution: StateFlow<List<MealTypeDistributionRow>> = _selectedYearMonth
        .flatMapLatest { ym ->
            val start = ym.atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val end = ym.atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE)
            mealRepository.observeMealTypeDistribution(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWeights: StateFlow<List<WeightEntry>> = weightRepository.allWeightsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun shiftMonth(deltaMonths: Long) {
        _selectedYearMonth.value = _selectedYearMonth.value.plusMonths(deltaMonths)
    }

    fun toggleDayComplete(localDate: String, complete: Boolean) {
        viewModelScope.launch {
            mealRepository.toggleDayComplete(localDate, complete)
        }
    }

    fun logWeightToday(weightKg: Double) {
        viewModelScope.launch {
            weightRepository.logWeightForDate(DateHelpers.todayLocalDateString(), weightKg)
        }
    }

    fun exportCsv(context: android.content.Context) {
        viewModelScope.launch {
            val meals = mealRepository.getAllMealsForExport()
            val file = ExportHelper.exportMealsToCsvFile(context, meals)
            ExportHelper.shareExportFile(context, file, "text/csv")
        }
    }
}

/**
 * Section 11 Page 18–21 Full 11-Block Month Tracking & Cal AI-Style Insights Screen:
 * 1. Header: Month & Year, previous/next arrows, "N of D days logged" chip
 * 2. Calendar Heat Map: 7-column grid, Default vs. Calm palette, tap for Day Sheet with mark-day-complete
 * 3. Six Summary Cards: Avg Calories, Avg Protein, Days Logged, Goal Days, Best Streak, Weight Change (+ vs last month chips)
 * 4. Calories Chart: Daily bars, dashed goal line, 7D trend
 * 5. Macro Split Donut: Protein, Carbs, Fat share of calories
 * 6. Weekday Pattern: Monday to Sunday average calories (makes weekend drift visible)
 * 7. Meal Distribution: Breakfast, Lunch, Dinner, Snack average calories
 * 8. Top 5 Foods: Toggle between Top 5 by Calories and Top 5 by Protein
 * 9. Auto-Written Highlights: Best day, protein goal hit on N of D days, longest streak, weekend gap
 * 10. Weight Log & 7D EWMA Line Chart + Adaptive TDEE Recommendation
 * 11. Share & Export (CSV / JSON)
 */
@Composable
fun ProgressScreen(viewModel: ProgressViewModel) {
    val colors = LocalZCalColors.current
    val strings = LocalZCalStrings.current
    val context = LocalContext.current

    val yearMonth by viewModel.selectedYearMonth.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    val calmPalette by viewModel.calmPalette.collectAsState()
    val forgiveOneDay by viewModel.forgiveOneDay.collectAsState()
    val hideNumbers by viewModel.hideNumbers.collectAsState()
    val summaries by viewModel.monthSummaries.collectAsState()
    val prevSummaries by viewModel.prevMonthSummaries.collectAsState()
    val weekdayRows by viewModel.weekdayPattern.collectAsState()
    val topCalFoods by viewModel.topFoodsByCalories.collectAsState()
    val topProtFoods by viewModel.topFoodsByProtein.collectAsState()
    val mealDistRows by viewModel.mealTypeDistribution.collectAsState()
    val weights by viewModel.allWeights.collectAsState()

    var selectedDayForSheet by remember { mutableStateOf<String?>(null) }
    var showTopByProtein by remember { mutableStateOf(false) }
    var weightInputText by remember { mutableStateOf("") }

    // Section 11 Page 20 Honest Numbers Rules:
    // "Averages divide by days with at least one meal, never by calendar days"
    val loggedDays = summaries.filter { it.mealCount > 0 }
    val prevLoggedDays = prevSummaries.filter { it.mealCount > 0 }
    val avgKcal = if (loggedDays.isNotEmpty()) loggedDays.map { it.kcal }.average().roundToInt() else 0
    val prevAvgKcal = if (prevLoggedDays.isNotEmpty()) prevLoggedDays.map { it.kcal }.average().roundToInt() else 0
    val avgProt = if (loggedDays.isNotEmpty()) loggedDays.map { it.proteinG }.average() else 0.0
    val prevAvgProt = if (prevLoggedDays.isNotEmpty()) prevLoggedDays.map { it.proteinG }.average() else 0.0
    val avgCarbs = if (loggedDays.isNotEmpty()) loggedDays.map { it.carbsG }.average() else 0.0
    val avgFat = if (loggedDays.isNotEmpty()) loggedDays.map { it.fatG }.average() else 0.0

    // "A day counts toward goal days only when it is complete. Calorie goal hit: within 10% of goal."
    val completeDays = loggedDays.filter { it.complete }
    val goalHitDays = completeDays.count {
        abs(it.kcal - profile.calorieGoal) <= (profile.calorieGoal * 0.10)
    }
    val proteinHitDays = completeDays.count {
        it.proteinG >= (profile.proteinGoalG * 0.90)
    }
    val bestStreak = DateHelpers.computeBestStreak(loggedDays, forgiveOneDay)

    val smoothedWeights = remember(weights) {
        viewModel.adaptiveTdeeUseCase.computeSmoothedWeightSeries(weights)
    }
    val weightChangeMonth = if (smoothedWeights.size >= 2) {
        smoothedWeights.last().ewmaWeightKg - smoothedWeights.first().ewmaWeightKg
    } else 0.0

    val summariesByDate = remember(summaries) { summaries.associateBy { it.localDate } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .displayCutoutPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Block 1: Month Header & Logged Days Chip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.shiftMonth(-1) }) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous Month")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = DateHelpers.formatYearMonth(yearMonth),
                        style = ZCalTypeScale.Headline,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${loggedDays.size} of ${yearMonth.lengthOfMonth()} days logged",
                        style = ZCalTypeScale.Label,
                        color = colors.accent
                    )
                }
                IconButton(onClick = { viewModel.shiftMonth(1) }) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Next Month")
                }
            }
        }

        // Block 2: 7-Column Calendar Heat Map
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MonthCalendarHeatMap(
                        yearMonth = yearMonth,
                        summariesByDate = summariesByDate,
                        calorieGoal = profile.calorieGoal,
                        calmPalette = calmPalette,
                        onDayClicked = { dateStr -> selectedDayForSheet = dateStr }
                    )
                }
            }
        }

        // Block 3 & 10: Six Summary Cards with Versus-Last-Month Delta Chips
        if (!hideNumbers) {
            item {
                val kcalDelta = if (prevAvgKcal > 0) avgKcal - prevAvgKcal else null
                val protDelta = if (prevAvgProt > 0) (avgProt - prevAvgProt).roundToInt() else null

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MonthMetricCard(
                            title = "Avg Calories",
                            value = "$avgKcal kcal",
                            deltaChip = kcalDelta?.let { "${if (it >= 0) "+" else ""}$it kcal vs last mo" },
                            modifier = Modifier.weight(1f)
                        )
                        MonthMetricCard(
                            title = "Avg Protein",
                            value = "${avgProt.roundToInt()} g",
                            deltaChip = protDelta?.let { "${if (it >= 0) "+" else ""}$it g vs last mo" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MonthMetricCard(
                            title = "Goal Days",
                            value = "$goalHitDays / ${completeDays.size}",
                            deltaChip = "Complete days within 10%",
                            modifier = Modifier.weight(1f)
                        )
                        MonthMetricCard(
                            title = "Best Streak",
                            value = "$bestStreak days",
                            deltaChip = "${loggedDays.size} total logged",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MonthMetricCard(
                            title = "Protein Goal Hit",
                            value = "$proteinHitDays days",
                            deltaChip = "≥90% of ${profile.proteinGoalG}g target",
                            modifier = Modifier.weight(1f)
                        )
                        MonthMetricCard(
                            title = "Weight Change",
                            value = String.format("%+.1f kg", weightChangeMonth),
                            deltaChip = "7-day smoothed trend",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Block 4: Daily Calories Chart
            item {
                DailyCaloriesBarChart(
                    summaries = loggedDays,
                    calorieGoal = profile.calorieGoal
                )
            }

            // Block 5: Macro Split Donut
            item {
                MacroSplitDonutCard(
                    avgProteinG = avgProt,
                    avgCarbsG = avgCarbs,
                    avgFatG = avgFat
                )
            }

            // Block 6 & 7: Weekday Pattern (Weekend Drift) & Meal Type Distribution
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ZCalShapes.Card,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(strings.weekdayPatternTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        if (weekdayRows.isEmpty()) {
                            Text(strings.weekdayPatternDesc, style = ZCalTypeScale.BodySmall, color = colors.textSecondary)
                        } else {
                            weekdayRows.forEach { row ->
                                val dayName = dayNames.getOrElse(row.weekday) { "Day" }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(dayName, style = ZCalTypeScale.BodySmall, color = colors.textSecondary)
                                    Text("${row.avgKcal.roundToInt()} kcal avg", style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                                }
                            }
                        }

                        if (mealDistRows.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            mealDistRows.forEach { mRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(mRow.mealType, style = ZCalTypeScale.Label, color = colors.textSecondary)
                                    Text("${mRow.avgKcal.roundToInt()} kcal (${mRow.meals} logged)", style = ZCalTypeScale.Label, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }

            // Block 8: Top 5 Foods by Calories & Protein
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ZCalShapes.Card,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(strings.topFoodsTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = ZCalShapes.Pill,
                                    color = if (!showTopByProtein) colors.accent else colors.surfaceVariant,
                                    border = BorderStroke(1.dp, if (!showTopByProtein) colors.accent else colors.outline),
                                    onClick = { showTopByProtein = false }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.LocalFireDepartment,
                                            contentDescription = null,
                                            tint = if (!showTopByProtein) colors.onAccent else colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            strings.byCaloriesLabel,
                                            style = ZCalTypeScale.Label.copy(
                                                fontSize = 12.sp,
                                                fontWeight = if (!showTopByProtein) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (!showTopByProtein) colors.onAccent else colors.textPrimary
                                        )
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = ZCalShapes.Pill,
                                    color = if (showTopByProtein) colors.accent else colors.surfaceVariant,
                                    border = BorderStroke(1.dp, if (showTopByProtein) colors.accent else colors.outline),
                                    onClick = { showTopByProtein = true }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.FitnessCenter,
                                            contentDescription = null,
                                            tint = if (showTopByProtein) colors.onAccent else colors.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            strings.byProteinLabel,
                                            style = ZCalTypeScale.Label.copy(
                                                fontSize = 12.sp,
                                                fontWeight = if (showTopByProtein) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (showTopByProtein) colors.onAccent else colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        val activeList = if (showTopByProtein) topProtFoods else topCalFoods
                        if (activeList.isEmpty()) {
                            Text(strings.frequentFoodsEmpty, style = ZCalTypeScale.BodySmall, color = colors.textSecondary)
                        } else {
                            activeList.forEachIndexed { i, foodRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${i + 1}. ${foodRow.food.replaceFirstChar { it.uppercase() }} (${foodRow.times}x)",
                                        style = ZCalTypeScale.BodySmall,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        if (showTopByProtein) "${foodRow.protein.roundToInt()}g P" else "${foodRow.kcal} kcal",
                                        style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.accent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Block 9: Auto-Written Monthly Highlights
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ZCalShapes.Card,
                    color = colors.surfaceVariant,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(strings.monthlyHighlightsTitle, style = ZCalTypeScale.Title, color = colors.textPrimary)
                        Text(strings.proteinHitFormat.format(proteinHitDays, completeDays.size), style = ZCalTypeScale.BodySmall, color = colors.textPrimary)
                        Text(strings.longestStreakFormat.format(bestStreak), style = ZCalTypeScale.BodySmall, color = colors.textPrimary)
                        val bestDay = completeDays.minByOrNull { abs(it.kcal - profile.calorieGoal) }
                        if (bestDay != null) {
                            Text(strings.closestDayFormat.format(bestDay.localDate, bestDay.kcal), style = ZCalTypeScale.BodySmall, color = colors.textPrimary)
                        }
                    }
                }
            }
        }

        // Block 10: Weight Log & 7D EWMA Chart
        item {
            WeightTrendChartCard(
                points = smoothedWeights,
                targetWeightKg = profile.targetWeightKg
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = weightInputText,
                    onValueChange = { weightInputText = it },
                    label = { Text(strings.todaysWeightLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = ZCalShapes.Input,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        weightInputText.toDoubleOrNull()?.let { w ->
                            viewModel.logWeightToday(w)
                            weightInputText = ""
                        }
                    },
                    shape = ZCalShapes.Pill,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(strings.logWeightBtn, color = colors.onAccent)
                }
            }
        }

        // Block 11: Share & Export (CSV / System Share Sheet)
        item {
            OutlinedButton(
                onClick = { viewModel.exportCsv(context) },
                shape = ZCalShapes.Pill,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Outlined.IosShare, contentDescription = null)
                Text(
                    strings.exportCsvBtn,
                    modifier = Modifier.padding(start = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ZCalTypeScale.Label.copy(fontSize = 13.sp)
                )
            }
        }
    }

    // Day Sheet when tapping a Calendar Heat Map square (Section 11 Page 18 & 20)
    selectedDayForSheet?.let { dateStr ->
        val daySum = summariesByDate[dateStr]
        ModalBottomSheet(
            onDismissRequest = { selectedDayForSheet = null },
            shape = ZCalShapes.SheetTop,
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(DateHelpers.formatFriendlyDate(dateStr), style = ZCalTypeScale.Title, color = colors.textPrimary)
                if (daySum == null || daySum.mealCount == 0) {
                    Text(strings.noMealsOnDay, style = ZCalTypeScale.Body, color = colors.textSecondary)
                } else {
                    Text(
                        "${daySum.kcal} kcal across ${daySum.mealCount} meal(s) • P ${daySum.proteinG.roundToInt()}g • C ${daySum.carbsG.roundToInt()}g • F ${daySum.fatG.roundToInt()}g",
                        style = ZCalTypeScale.Body,
                        color = colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.markDayCompleteTitle, style = ZCalTypeScale.Body, color = colors.textPrimary)
                            Text(strings.markDayCompleteDesc, style = ZCalTypeScale.Label, color = colors.textSecondary)
                        }
                        Switch(
                            checked = daySum.complete,
                            onCheckedChange = { checked -> viewModel.toggleDayComplete(dateStr, checked) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MonthMetricCard(
    title: String,
    value: String,
    deltaChip: String?,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current
    Surface(
        modifier = modifier,
        shape = ZCalShapes.Card,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = ZCalTypeScale.Label, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = ZCalTypeScale.Title, color = colors.textPrimary)
            if (deltaChip != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(deltaChip, style = ZCalTypeScale.Label, color = colors.accent)
            }
        }
    }
}
