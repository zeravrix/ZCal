package app.zcal.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.zcal.MainActivity
import app.zcal.data.local.ZCalDao
import app.zcal.util.DateHelpers
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Section 5 & Section 12 Page 25 Glance Home Screen Widget:
 * Shows remaining calories for today, eaten P/C/F macros, and launches ZCal on tap.
 */
class ZCalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ZCalGlanceWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun dao(): ZCalDao
}

class ZCalGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).dao()

        val today = DateHelpers.todayLocalDateString()
        val summary = runCatching { dao.getDaySummaryOnce(today) }.getOrNull()
        val eaten = summary?.kcal ?: 0
        val p = summary?.proteinG?.toInt() ?: 0
        val c = summary?.carbsG?.toInt() ?: 0
        val f = summary?.fatG?.toInt() ?: 0

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.surface)
                        .padding(14.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "ZCal Today",
                                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
                            )
                            Text(
                                text = "$eaten kcal eaten",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.onSurface
                                )
                            )
                        }
                        Text(
                            text = "+ Scan",
                            style = TextStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.primary
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Text(
                        text = "P ${p}g  •  C ${c}g  •  F ${f}g",
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            }
        }
    }
}
