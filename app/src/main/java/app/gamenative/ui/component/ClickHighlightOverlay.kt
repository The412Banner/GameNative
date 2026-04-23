package app.gamenative.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class HighlightPoint(val x: Float, val y: Float, val alpha: Animatable<Float, *>)

@Composable
fun ClickHighlightOverlay(
    points: SnapshotStateList<HighlightPoint>,
) {
    // Clear any orphaned points when composable is disposed
    DisposableEffect(Unit) {
        onDispose { points.clear() }
    }

    // Animate each point's alpha from 0.5 → 0 over 300ms, then remove
    for (i in points.indices) {
        val point = points.getOrNull(i) ?: continue
        LaunchedEffect(point) {
            point.alpha.animateTo(0f, animationSpec = tween(300))
            points.remove(point)
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val current = points.toList()
        for (point in current) {
            if (point.alpha.value > 0f) {
                drawCircle(
                    color = Color.White.copy(alpha = point.alpha.value),
                    radius = 24f,
                    center = Offset(point.x, point.y),
                )
            }
        }
    }
}
