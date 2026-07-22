package com.revio.app.core.ui.tour

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revio.app.core.tour.TourStep
import com.revio.app.core.ui.components.GradientText
import com.revio.app.core.ui.theme.Poppins

private val ScrimColor = Color.Black.copy(alpha = 0.72f)
private val SpotlightAccent = Color(0xFF34D7C4)
private val OverlayMenuSurface = Color(0xFF1B1F33)
private val OverlayMenuBorder = Color(0x1FFFFFFF)

/**
 * Full-screen, contextual coach-mark for the first-time guided tour. Presentational and
 * stateless — [step] selects the copy, [spotlight] (in *window* coordinates, as reported by
 * [com.revio.app.core.ui.components.FloatingBottomNav]'s `onSlotBounds`) selects what gets cut
 * out of the scrim, and every tap is routed through [onAdvance] / [onPostCta]. Null [spotlight]
 * renders the scrim with no cutout rather than a misplaced one.
 *
 * Must be composed inside a screen's `AppScreenBackground(foreground = ...)`, *after*
 * `FloatingBottomNav`, so it sits above the nav's own event-consuming pointer input and can
 * intercept every tap on the screen. There is intentionally no Skip affordance.
 */
@Composable
fun TourOverlay(
    step: TourStep,
    spotlight: Rect?,
    onAdvance: () -> Unit,
    onPostCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = remember(step) { tourCopyFor(step) }
    val reducedMotion = rememberReducedMotion()

    // Bounds are reported in window space; convert to this overlay's own local space so the
    // cutout lines up regardless of systemBarsPadding()/navigationBarsPadding() consumed upstream.
    var overlayOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    val localSpotlight = spotlight?.translate(-overlayOriginInWindow.x, -overlayOriginInWindow.y)

    // First appearance of a target on a given screen snaps in place (animateFloatAsState's
    // initial value is the first target passed to it); only in-place target changes — the
    // Profile-avatar-to-"+" glide within the same screen — actually tween.
    val rectSpec: AnimationSpec<Float> = if (reducedMotion) snap() else tween(320, easing = FastOutSlowInEasing)
    val animatedLeft by animateFloatAsState(localSpotlight?.left ?: 0f, rectSpec, label = "tourSpotlightLeft")
    val animatedTop by animateFloatAsState(localSpotlight?.top ?: 0f, rectSpec, label = "tourSpotlightTop")
    val animatedRight by animateFloatAsState(localSpotlight?.right ?: 0f, rectSpec, label = "tourSpotlightRight")
    val animatedBottom by animateFloatAsState(localSpotlight?.bottom ?: 0f, rectSpec, label = "tourSpotlightBottom")
    val animatedSpotlight = localSpotlight?.let { Rect(animatedLeft, animatedTop, animatedRight, animatedBottom) }

    val pulseAlpha: Float = if (reducedMotion) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "tourPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "tourPulseAlpha",
        )
        alpha
    }

    val ctaLabel = if (step == TourStep.PostCta) "Post your first spot" else "Continue tour"

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOriginInWindow = it.positionInWindow() }
            .semantics(mergeDescendants = true) {
                contentDescription = "${copy.title} ${copy.body}"
                liveRegion = LiveRegionMode.Polite
                onClick(label = ctaLabel) {
                    if (step == TourStep.PostCta) onPostCta() else onAdvance()
                    true
                }
            }
            // Raw pointer input (not `clickable`) because step PostCta needs the tap offset to
            // hit-test against the spotlight; every other step advances on any tap. Either way
            // the tap is consumed here and never reaches FloatingBottomNav underneath.
            .pointerInput(step, animatedSpotlight) {
                detectTapGestures { offset ->
                    if (step == TourStep.PostCta) {
                        if (animatedSpotlight != null && animatedSpotlight.contains(offset)) {
                            onPostCta()
                        }
                    } else {
                        onAdvance()
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Offscreen compositing is required for BlendMode.Clear to punch a real hole
                // instead of drawing an opaque black circle.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawRect(ScrimColor)
                    animatedSpotlight?.let { rect ->
                        val radius = maxOf(rect.width, rect.height) / 2f + 14.dp.toPx()
                        drawCircle(
                            color = Color.Transparent,
                            radius = radius,
                            center = rect.center,
                            blendMode = BlendMode.Clear,
                        )
                        drawCircle(
                            color = SpotlightAccent.copy(alpha = pulseAlpha),
                            radius = radius,
                            center = rect.center,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 104.dp)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(OverlayMenuSurface)
                .border(1.dp, OverlayMenuBorder, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GradientText(
                text = copy.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = copy.body,
                color = Color.White.copy(alpha = 0.75f),
                fontFamily = Poppins,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
