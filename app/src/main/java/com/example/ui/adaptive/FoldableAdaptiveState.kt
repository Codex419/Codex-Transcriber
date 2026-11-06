package com.example.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DevicesFold
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.example.ui.theme.ElectricAmethyst
import com.example.ui.theme.ElectricAmethystContainer
import com.example.ui.theme.ElectricAmethystLight
import com.example.ui.theme.SnapdragonCyan
import com.example.ui.theme.StatusCompletedGreen
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.WorkspaceBottomTab

/**
 * Standard Material 3 Window Width Size Classes evaluated dynamically from container BoxWithConstraints.
 */
enum class WindowWidthSizeClass {
    COMPACT,  // < 600.dp (Folded outer cover screen / phone portrait)
    MEDIUM,   // 600.dp .. 839.dp (Unfolded inner foldable portrait/square, e.g., Pixel Fold / Galaxy Z Fold)
    EXPANDED  // >= 840.dp (Unfolded inner foldable landscape / tablet / desktop)
}

/**
 * Standard Material 3 Window Height Size Classes evaluated dynamically from container BoxWithConstraints.
 */
enum class WindowHeightSizeClass {
    COMPACT,  // < 480.dp
    MEDIUM,   // 480.dp .. 899.dp
    EXPANDED  // >= 900.dp
}

/**
 * Resolved Foldable Posture & Layout Mode for Codex Transcriber.
 */
enum class FoldablePosture(
    val badgeTitle: String,
    val shortBadge: String,
    val description: String
) {
    COMPACT_COVER(
        badgeTitle = "Cover Screen (Compact)",
        shortBadge = "Cover",
        description = "Single-column handheld stack optimized for outer foldable cover displays"
    ),
    FLAT_DUAL_PANE(
        badgeTitle = "Unfolded Dual-Pane",
        shortBadge = "Unfolded",
        description = "Navigation Rail + canonical two-pane studio layout for unfolded inner displays"
    ),
    BOOK_POSTURE(
        badgeTitle = "Book Posture (Vertical Hinge)",
        shortBadge = "Book Fold",
        description = "Two-page book layout separating DSP/Spectrogram and Queue/Lyrics across the vertical fold crease"
    ),
    TABLETOP_POSTURE(
        badgeTitle = "Tabletop / Flex Mode (Horizontal Hinge)",
        shortBadge = "Tabletop Flex",
        description = "Visualizer & NPU telemetry above the horizontal fold • Interactive controls & queue on the flat lower half"
    )
}

/**
 * Allows automatic Jetpack WindowManager FoldingFeature detection or user-selected foldable posture override.
 */
enum class FoldablePostureMode(
    val label: String,
    val subtitle: String
) {
    AUTO_HARDWARE(
        label = "Auto (Hinge Sensor & Window Size)",
        subtitle = "Automatically adapts to FoldingFeature (Book / Tabletop / Unfolded / Cover)"
    ),
    BOOK_POSTURE(
        label = "Book Posture (Vertical Hinge)",
        subtitle = "Two-page left/right layout with hinge gutter avoidance"
    ),
    TABLETOP_POSTURE(
        label = "Tabletop / Flex Mode (Horizontal Hinge)",
        subtitle = "Top half visualizer above fold • Bottom half controls & queue"
    ),
    UNFOLDED_DUAL_PANE(
        label = "Unfolded Inner Screen (Dual-Pane + Rail)",
        subtitle = "Navigation Rail + side-by-side studio panes"
    ),
    COVER_COMPACT(
        label = "Cover Screen (Compact Single-Pane)",
        subtitle = "Compact vertical layout for folded outer cover screen"
    );

    fun nextCycledMode(): FoldablePostureMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

/**
 * Snapshot of current Window Size Class and Jetpack WindowManager FoldingFeature metrics.
 */
data class FoldableAdaptiveState(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val posture: FoldablePosture,
    val postureMode: FoldablePostureMode,
    val hasHardwareFoldingFeature: Boolean,
    val isHalfOpened: Boolean,
    val isSeparating: Boolean,
    val isOccludingHinge: Boolean,
    val hingeOrientation: FoldingFeature.Orientation?,
    val hingeSpanDp: Dp,
    val containerWidthDp: Dp,
    val containerHeightDp: Dp
) {
    val showNavigationRail: Boolean
        get() = posture == FoldablePosture.FLAT_DUAL_PANE ||
            (posture == FoldablePosture.BOOK_POSTURE && containerWidthDp >= 700.dp)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Observes Jetpack WindowManager's [WindowInfoTracker] for real-time [FoldingFeature] changes
 * (hinge angle HALF_OPENED vs FLAT, HORIZONTAL tabletop vs VERTICAL book orientation, hinge bounds)
 * combined with container [maxWidth] / [maxHeight] from `BoxWithConstraints`.
 */
@Composable
fun rememberFoldableAdaptiveState(
    maxWidth: Dp,
    maxHeight: Dp,
    postureMode: FoldablePostureMode
): FoldableAdaptiveState {
    val context = LocalContext.current
    val density = LocalDensity.current
    val activity = remember(context) { context.findActivity() }

    val windowLayoutInfo by produceState<WindowLayoutInfo?>(initialValue = null, activity) {
        if (activity != null) {
            runCatching {
                WindowInfoTracker.getOrCreate(activity)
                    .windowLayoutInfo(activity)
                    .collect { info ->
                        value = info
                    }
            }
        }
    }

    return remember(maxWidth, maxHeight, postureMode, windowLayoutInfo, density) {
        val widthClass = when {
            maxWidth >= 840.dp -> WindowWidthSizeClass.EXPANDED
            maxWidth >= 600.dp -> WindowWidthSizeClass.MEDIUM
            else -> WindowWidthSizeClass.COMPACT
        }
        val heightClass = when {
            maxHeight >= 900.dp -> WindowHeightSizeClass.EXPANDED
            maxHeight >= 480.dp -> WindowHeightSizeClass.MEDIUM
            else -> WindowHeightSizeClass.COMPACT
        }

        val foldingFeature = windowLayoutInfo?.displayFeatures
            ?.filterIsInstance<FoldingFeature>()
            ?.firstOrNull()

        val hasHardwareFold = foldingFeature != null
        val isHalfOpened = foldingFeature?.state == FoldingFeature.State.HALF_OPENED
        val isSeparating = foldingFeature?.isSeparating == true
        val isOccluding = foldingFeature?.occlusionType == FoldingFeature.OcclusionType.FULL
        val orientation = foldingFeature?.orientation

        val rawHingePx = if (foldingFeature != null) {
            if (foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL) {
                foldingFeature.bounds.width()
            } else {
                foldingFeature.bounds.height()
            }
        } else 0

        val measuredHingeDp = with(density) { rawHingePx.toDp() }.coerceIn(12.dp, 36.dp)

        val resolvedPosture = when (postureMode) {
            FoldablePostureMode.BOOK_POSTURE -> FoldablePosture.BOOK_POSTURE
            FoldablePostureMode.TABLETOP_POSTURE -> FoldablePosture.TABLETOP_POSTURE
            FoldablePostureMode.UNFOLDED_DUAL_PANE -> FoldablePosture.FLAT_DUAL_PANE
            FoldablePostureMode.COVER_COMPACT -> FoldablePosture.COMPACT_COVER
            FoldablePostureMode.AUTO_HARDWARE -> {
                when {
                    // 1. Hardware Tabletop / Flex posture (half-opened or separating horizontal fold)
                    foldingFeature != null &&
                        (isHalfOpened || isSeparating) &&
                        orientation == FoldingFeature.Orientation.HORIZONTAL -> {
                        FoldablePosture.TABLETOP_POSTURE
                    }
                    // 2. Hardware Book posture (half-opened or separating vertical fold)
                    foldingFeature != null &&
                        (isHalfOpened || isSeparating) &&
                        orientation == FoldingFeature.Orientation.VERTICAL -> {
                        FoldablePosture.BOOK_POSTURE
                    }
                    // 3. Unfolded Foldable Inner Display (Medium 600dp..839dp or Expanded >= 840dp)
                    widthClass == WindowWidthSizeClass.MEDIUM || widthClass == WindowWidthSizeClass.EXPANDED -> {
                        // Square-ish unfolded foldable aspect ratios (0.82 .. 1.28) work great in Dual-Pane
                        FoldablePosture.FLAT_DUAL_PANE
                    }
                    // 4. Outer Cover Display / Standard Handheld Portrait
                    else -> FoldablePosture.COMPACT_COVER
                }
            }
        }

        val effectiveHingeSpanDp = when {
            hasHardwareFold && rawHingePx > 0 -> measuredHingeDp
            resolvedPosture == FoldablePosture.BOOK_POSTURE -> 18.dp
            resolvedPosture == FoldablePosture.TABLETOP_POSTURE -> 16.dp
            else -> 12.dp
        }

        FoldableAdaptiveState(
            widthSizeClass = widthClass,
            heightSizeClass = heightClass,
            posture = resolvedPosture,
            postureMode = postureMode,
            hasHardwareFoldingFeature = hasHardwareFold,
            isHalfOpened = isHalfOpened || postureMode == FoldablePostureMode.BOOK_POSTURE || postureMode == FoldablePostureMode.TABLETOP_POSTURE,
            isSeparating = isSeparating || resolvedPosture == FoldablePosture.BOOK_POSTURE || resolvedPosture == FoldablePosture.TABLETOP_POSTURE,
            isOccludingHinge = isOccluding,
            hingeOrientation = orientation ?: when (resolvedPosture) {
                FoldablePosture.BOOK_POSTURE -> FoldingFeature.Orientation.VERTICAL
                FoldablePosture.TABLETOP_POSTURE -> FoldingFeature.Orientation.HORIZONTAL
                else -> null
            },
            hingeSpanDp = effectiveHingeSpanDp,
            containerWidthDp = maxWidth,
            containerHeightDp = maxHeight
        )
    }
}

/**
 * Canonical Material 3 NavigationRail for Unfolded Foldable & Large Screen Displays.
 */
@Composable
fun StudioNavigationRail(
    activeTab: WorkspaceBottomTab,
    queueCount: Int,
    finalizedCount: Int,
    logCount: Int,
    posture: FoldablePosture,
    onSelectTab: (WorkspaceBottomTab) -> Unit,
    onOpenSettings: () -> Unit,
    onCyclePostureMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, StudioBorder)
            .testTag("foldable_navigation_rail"),
        containerColor = StudioSurface,
        contentColor = TextPrimary,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricAmethystContainer)
                        .border(1.dp, ElectricAmethyst, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DevicesFold,
                        contentDescription = posture.badgeTitle,
                        tint = SnapdragonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = posture.shortBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = SnapdragonCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) {
        val railColors = NavigationRailItemDefaults.colors(
            selectedIconColor = ElectricAmethystLight,
            selectedTextColor = ElectricAmethystLight,
            indicatorColor = ElectricAmethystContainer,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
        )

        NavigationRailItem(
            selected = activeTab == WorkspaceBottomTab.FILE_QUEUE,
            onClick = { onSelectTab(WorkspaceBottomTab.FILE_QUEUE) },
            icon = {
                BadgedBox(
                    badge = {
                        if (queueCount > 0) {
                            Badge(containerColor = ElectricAmethyst) {
                                Text(queueCount.toString(), color = TextPrimary)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = "File Queue"
                    )
                }
            },
            label = { Text("Queue", style = MaterialTheme.typography.labelSmall) },
            colors = railColors,
            modifier = Modifier.testTag("rail_tab_queue")
        )

        NavigationRailItem(
            selected = activeTab == WorkspaceBottomTab.FINALIZED_FILES,
            onClick = { onSelectTab(WorkspaceBottomTab.FINALIZED_FILES) },
            icon = {
                BadgedBox(
                    badge = {
                        if (finalizedCount > 0) {
                            Badge(containerColor = StatusCompletedGreen) {
                                Text(finalizedCount.toString(), color = Color.Black)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Finalized Files"
                    )
                }
            },
            label = { Text("Finalized", style = MaterialTheme.typography.labelSmall) },
            colors = railColors,
            modifier = Modifier.testTag("rail_tab_finalized")
        )

        NavigationRailItem(
            selected = activeTab == WorkspaceBottomTab.EXECUTION_LOG,
            onClick = { onSelectTab(WorkspaceBottomTab.EXECUTION_LOG) },
            icon = {
                BadgedBox(
                    badge = {
                        if (logCount > 0) {
                            Badge(containerColor = StudioSurfaceVariant) {
                                Text(logCount.toString(), color = SnapdragonCyan)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = "Log View"
                    )
                }
            },
            label = { Text("Logs", style = MaterialTheme.typography.labelSmall) },
            colors = railColors,
            modifier = Modifier.testTag("rail_tab_logs")
        )

        Spacer(modifier = Modifier.weight(1f))

        NavigationRailItem(
            selected = false,
            onClick = onCyclePostureMode,
            icon = {
                Icon(
                    imageVector = Icons.Filled.DevicesFold,
                    contentDescription = "Switch Foldable Posture",
                    tint = SnapdragonCyan
                )
            },
            label = { Text("Posture", style = MaterialTheme.typography.labelSmall) },
            colors = railColors,
            modifier = Modifier.testTag("rail_switch_posture")
        )

        NavigationRailItem(
            selected = false,
            onClick = onOpenSettings,
            icon = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings Menu",
                    tint = ElectricAmethystLight
                )
            },
            label = { Text("Settings", style = MaterialTheme.typography.labelSmall) },
            colors = railColors,
            modifier = Modifier.testTag("rail_open_settings")
        )
    }
}

/**
 * Visual & structural Vertical Hinge Gutter used in Book Posture (`FoldingFeature.Orientation.VERTICAL`)
 * so interactive buttons and sliders are never placed directly inside the physical fold crease.
 */
@Composable
fun FoldableHingeGutterVertical(
    hingeWidthDp: Dp,
    isHalfOpened: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(hingeWidthDp.coerceAtLeast(14.dp))
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0C12))
            .border(
                width = 1.dp,
                color = if (isHalfOpened) SnapdragonCyan.copy(alpha = 0.45f) else StudioBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .testTag("foldable_vertical_hinge_gutter"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(42.dp)
                    .background(if (isHalfOpened) SnapdragonCyan.copy(alpha = 0.6f) else StudioBorder)
            )
            Icon(
                imageVector = Icons.Filled.DevicesFold,
                contentDescription = "Book Fold Hinge",
                tint = if (isHalfOpened) SnapdragonCyan else TextMuted,
                modifier = Modifier.size(12.dp)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(42.dp)
                    .background(if (isHalfOpened) SnapdragonCyan.copy(alpha = 0.6f) else StudioBorder)
            )
        }
    }
}

/**
 * Visual & structural Horizontal Hinge Crease Divider used in Tabletop / Flex Posture
 * (`FoldingFeature.Orientation.HORIZONTAL`) to separate the top viewing half from the bottom control half.
 */
@Composable
fun FoldableHingeCreaseHorizontal(
    hingeHeightDp: Dp,
    postureLabel: String = "Tabletop Flex Hinge • Top: Visualizer | Bottom: Controls & Queue",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(hingeHeightDp.coerceAtLeast(22.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0C12))
            .border(1.dp, SnapdragonCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp)
            .testTag("foldable_horizontal_hinge_crease"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.DevicesFold,
                contentDescription = "Horizontal Tabletop Hinge",
                tint = SnapdragonCyan,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = postureLabel,
                style = MaterialTheme.typography.labelSmall,
                color = SnapdragonCyan,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
