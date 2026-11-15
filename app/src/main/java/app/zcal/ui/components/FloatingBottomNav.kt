package app.zcal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes

enum class BottomNavTab(val route: String, val label: String, val icon: ImageVector) {
    CAMERA("camera", "Camera", Icons.Outlined.CameraAlt),
    HOME("home", "Dashboard", Icons.Outlined.Home),
    CACHE("cache", "History Cache", Icons.Outlined.History),
    PROGRESS("progress", "Progress", Icons.Outlined.Assessment),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings)
}

@Composable
fun FloatingBottomNav(
    currentRoute: String?,
    onTabSelected: (BottomNavTab) -> Unit,
    onScanClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalZCalColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = ZCalShapes.Pill,
            color = colors.surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, colors.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavIconItem(
                    tab = BottomNavTab.HOME,
                    selected = currentRoute == BottomNavTab.HOME.route,
                    onClick = { onTabSelected(BottomNavTab.HOME) }
                )
                NavIconItem(
                    tab = BottomNavTab.CACHE,
                    selected = currentRoute == BottomNavTab.CACHE.route,
                    onClick = { onTabSelected(BottomNavTab.CACHE) }
                )

                // Center Pre-Built Camera Main Button
                FloatingActionButton(
                    onClick = onScanClicked,
                    modifier = Modifier.size(56.dp),
                    shape = ZCalShapes.Pill,
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = "Main Camera Screen"
                    )
                }

                NavIconItem(
                    tab = BottomNavTab.PROGRESS,
                    selected = currentRoute == BottomNavTab.PROGRESS.route,
                    onClick = { onTabSelected(BottomNavTab.PROGRESS) }
                )
                NavIconItem(
                    tab = BottomNavTab.SETTINGS,
                    selected = currentRoute == BottomNavTab.SETTINGS.route,
                    onClick = { onTabSelected(BottomNavTab.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun NavIconItem(
    tab: BottomNavTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalZCalColors.current
    val tint = if (selected) colors.accent else colors.textSecondary
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint
        )
    }
}
