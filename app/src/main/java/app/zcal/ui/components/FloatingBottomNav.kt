package app.zcal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zcal.ui.localization.LocalZCalStrings
import app.zcal.ui.theme.LocalZCalColors
import app.zcal.ui.theme.ZCalShapes

enum class BottomNavTab(
    val route: String,
    val label: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
) {
    CAMERA("camera", "Scan", Icons.Outlined.CameraAlt, Icons.Filled.CameraAlt),
    HOME("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    CACHE("cache", "History", Icons.Outlined.History, Icons.Filled.History),
    PROGRESS("progress", "Progress", Icons.Outlined.Assessment, Icons.Filled.Assessment),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings, Icons.Filled.Settings);

    val icon: ImageVector get() = outlinedIcon
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
                .height(68.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = ZCalShapes.Pill,
                    spotColor = colors.accentGlow,
                    ambientColor = colors.accentGlow
                ),
            shape = ZCalShapes.Pill,
            color = colors.surfaceElevated,
            border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home
                InteractiveNavIconItem(
                    tab = BottomNavTab.HOME,
                    selected = currentRoute == BottomNavTab.HOME.route,
                    onClick = { onTabSelected(BottomNavTab.HOME) }
                )

                // History
                InteractiveNavIconItem(
                    tab = BottomNavTab.CACHE,
                    selected = currentRoute == BottomNavTab.CACHE.route,
                    onClick = { onTabSelected(BottomNavTab.CACHE) }
                )

                // Center AI Camera Scan Button
                CenterCameraFab(
                    onClick = onScanClicked,
                    isSelected = currentRoute == BottomNavTab.CAMERA.route
                )

                // Progress
                InteractiveNavIconItem(
                    tab = BottomNavTab.PROGRESS,
                    selected = currentRoute == BottomNavTab.PROGRESS.route,
                    onClick = { onTabSelected(BottomNavTab.PROGRESS) }
                )

                // Settings
                InteractiveNavIconItem(
                    tab = BottomNavTab.SETTINGS,
                    selected = currentRoute == BottomNavTab.SETTINGS.route,
                    onClick = { onTabSelected(BottomNavTab.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun CenterCameraFab(
    onClick: () -> Unit,
    isSelected: Boolean
) {
    val colors = LocalZCalColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "fabScale"
    )

    val gradient = Brush.linearGradient(
        colors = listOf(
            colors.accent,
            colors.accent.copy(alpha = 0.85f)
        )
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(54.dp)
            .shadow(
                elevation = 8.dp,
                shape = ZCalShapes.Pill,
                spotColor = colors.accentGlow,
                ambientColor = colors.accentGlow
            )
            .clip(ZCalShapes.Pill)
            .background(gradient)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt,
            contentDescription = "Main Camera Screen",
            tint = colors.onAccent,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun InteractiveNavIconItem(
    tab: BottomNavTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalZCalColors.current
    val strings = LocalZCalStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val labelText = when (tab) {
        BottomNavTab.HOME -> strings.dashboard
        BottomNavTab.CACHE -> strings.history
        BottomNavTab.CAMERA -> strings.scan
        BottomNavTab.PROGRESS -> strings.progress
        BottomNavTab.SETTINGS -> strings.settings
    }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else if (selected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "navItemScale"
    )

    val iconTint by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = spring(),
        label = "navIconTint"
    )

    Column(
        modifier = Modifier
            .scale(scale)
            .clip(ZCalShapes.Pill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp, 30.dp)
                .clip(ZCalShapes.Pill)
                .background(
                    if (selected) colors.accent.copy(alpha = 0.14f) else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                contentDescription = tab.label,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = labelText,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.accent else colors.textSecondary
        )
    }
}
