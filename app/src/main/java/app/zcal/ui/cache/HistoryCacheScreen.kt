package app.zcal.ui.cache

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import app.zcal.data.cache.FoodHistoryCacheManager
import app.zcal.domain.model.CachedFoodHistoryItem
import app.zcal.ui.localization.LocalZCalStrings
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes
import app.zcal.ui.theme.ZCalTypeScale
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class HistoryCacheViewModel @Inject constructor(
    private val cacheManager: FoodHistoryCacheManager
) : ViewModel() {

    val cachedItems: StateFlow<List<CachedFoodHistoryItem>> = cacheManager.cachedHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun daysUntilNextPurge(): Long = cacheManager.daysUntilNextMonthlyAutoEmpty()

    fun getCacheSizeKb(): Long = cacheManager.getCacheSizeKb()

    fun emptyCacheNow(onDone: () -> Unit) {
        viewModelScope.launch {
            cacheManager.emptyEntireCacheNow()
            onDone()
        }
    }
}

/**
 * 30-Day / Monthly Food Image & Detail History Cache Screen:
 * Displays cached food photos and detailed macro breakdowns (kcal, kJ, Protein, Fat, Carbs, CLA)
 * for the current month and automatically empties every month to keep phone storage lean.
 */
@Composable
fun HistoryCacheScreen(viewModel: HistoryCacheViewModel) {
    val colors = LocalZCalColors.current
    val strings = LocalZCalStrings.current
    val items by viewModel.cachedItems.collectAsState()
    var cacheSizeKb by remember(items) { mutableLongStateOf(viewModel.getCacheSizeKb()) }
    val daysRemaining = remember { viewModel.daysUntilNextPurge() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .displayCutoutPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.imageCacheTitle, style = ZCalTypeScale.Headline, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                strings.autoEmptiesIn.format(daysRemaining),
                style = ZCalTypeScale.BodySmall,
                color = colors.textSecondary
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ZCalShapes.Card,
                color = colors.surfaceVariant,
                border = BorderStroke(1.dp, colors.accent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AutoDelete, contentDescription = null, tint = colors.accent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                strings.autoEmptiesIn.format(daysRemaining),
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            strings.cachedMealsCount.format(items.size, cacheSizeKb),
                            style = ZCalTypeScale.Label,
                            color = colors.textSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.emptyCacheNow {
                                cacheSizeKb = 0L
                            }
                        },
                        shape = ZCalShapes.Pill
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.emptyNowBtn)
                    }
                }
            }
        }

        if (items.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ZCalShapes.Card,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Text(
                        text = strings.emptyCacheMsg,
                        style = ZCalTypeScale.Body,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        } else {
            items(items, key = { it.id }) { cached ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ZCalShapes.Card,
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(ZCalShapes.Thumbnail)
                                .background(colors.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            val file = cached.photoPath.takeIf { it.isNotBlank() }?.let { File(it) }
                            if (file != null && file.exists()) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = cached.mealName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp)
                                )
                            } else {
                                Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = colors.textSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${cached.mealName} (${cached.localDate})",
                                style = ZCalTypeScale.Title,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${cached.caloriesKcal} kcal • ${cached.energyKj} kJ",
                                style = ZCalTypeScale.BodySmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.accent
                            )
                            Text(
                                text = "${strings.protein} ${cached.proteinG}g • ${strings.fat} ${cached.fatG}g • ${strings.carbs} ${cached.carbsG}g • ${strings.cla} ${cached.claMg.roundToInt()}mg",
                                style = ZCalTypeScale.Label,
                                color = colors.textPrimary
                            )
                            Text(
                                text = cached.detailsSummary,
                                style = ZCalTypeScale.Label,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
