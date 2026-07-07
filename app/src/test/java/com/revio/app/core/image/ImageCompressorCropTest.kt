package com.revio.app.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Pure-JVM tests for [ImageCompressor.computeCropRect].
 *
 * Two test groups:
 *  - [ImageCompressorCropTest] — 4:5 container (CarParams, 1080×1350).
 *  - [ImageCompressorSquareCropTest] — 1:1 container (ProfileParams avatar, 384×384).
 */
class ImageCompressorCropTest {

    // Container dimensions in pixels (arbitrary but with the correct 4:5 aspect ratio).
    private val cw = 1080f
    private val ch = 1350f

    // Target aspect ratio for assertions (same as CarParams).
    private val targetAr = cw / ch

    private fun crop(
        imgW: Int, imgH: Int,
        scale: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ) = ImageCompressor.computeCropRect(imgW, imgH, cw, ch, scale, offsetX, offsetY)

    // Helpers -----------------------------------------------------------

    private fun assertAspectRatioApprox(rect: CropRect, tolerance: Float = 0.02f) {
        val ar = rect.width.toFloat() / rect.height.toFloat()
        assertTrue(
            "Expected crop AR ≈ $targetAr but was $ar (rect=$rect)",
            abs(ar - targetAr) <= tolerance,
        )
    }

    // Tests -------------------------------------------------------------

    /**
     * Identity: no user interaction on a landscape image (wider than 4:5).
     * Expected: horizontal center-crop matching the container AR.
     */
    @Test
    fun `identity on landscape image matches center crop`() {
        val imgW = 4000; val imgH = 3000   // 4:3 landscape
        val rect = crop(imgW, imgH)

        // Center of crop must be close to image center horizontally.
        val cropCenterX = (rect.left + rect.right) / 2f
        assertTrue("Crop should be horizontally centered", abs(cropCenterX - imgW / 2f) < 2f)
        // Height should span the full image height (image is shorter proportionally).
        assertEquals(imgH, rect.height)
        // Width should match: imgH * targetAr.
        val expectedWidth = (imgH * targetAr).toInt()
        assertTrue("Width diff > 2px: expected ~$expectedWidth, got ${rect.width}", abs(rect.width - expectedWidth) <= 2)
        assertAspectRatioApprox(rect)
    }

    /**
     * Identity: no user interaction on a portrait image taller than 4:5.
     * Expected: vertical center-crop matching the container AR.
     */
    @Test
    fun `identity on tall portrait image crops height`() {
        val imgW = 3000; val imgH = 4500   // 2:3 portrait, taller than 4:5
        val rect = crop(imgW, imgH)

        // Center of crop must be close to image center vertically.
        val cropCenterY = (rect.top + rect.bottom) / 2f
        assertTrue("Crop should be vertically centered", abs(cropCenterY - imgH / 2f) < 2f)
        // Width should span the full image width.
        assertEquals(imgW, rect.width)
        // Height should match: imgW / targetAr.
        val expectedHeight = (imgW / targetAr).toInt()
        assertTrue("Height diff > 2px: expected ~$expectedHeight, got ${rect.height}", abs(rect.height - expectedHeight) <= 2)
        assertAspectRatioApprox(rect)
    }

    /**
     * Identity on an image that already has the exact container AR — crop == entire image.
     */
    @Test
    fun `identity on perfectly matching aspect ratio returns full image`() {
        val imgW = 1080; val imgH = 1350
        val rect = crop(imgW, imgH)

        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(imgW, rect.right)
        assertEquals(imgH, rect.bottom)
    }

    /**
     * Scale = 2, no offset: user zoomed in 2×. The visible region in image pixels should be
     * exactly half the size of the scale=1 crop (centered).
     */
    @Test
    fun `scale 2 no offset gives half-size centered crop`() {
        val imgW = 4000; val imgH = 3000
        val base = crop(imgW, imgH, scale = 1f)
        val zoomed = crop(imgW, imgH, scale = 2f)

        // The zoomed crop should be half the size.
        assertTrue("Width should halve at 2×", abs(zoomed.width  - base.width  / 2) <= 2)
        assertTrue("Height should halve at 2×", abs(zoomed.height - base.height / 2) <= 2)
        // And remain centered.
        val centerX = (zoomed.left + zoomed.right) / 2f
        val centerY = (zoomed.top + zoomed.bottom) / 2f
        assertTrue("Zoomed crop should be centered X", abs(centerX - imgW / 2f) < 2f)
        assertTrue("Zoomed crop should be centered Y", abs(centerY - imgH / 2f) < 2f)
        assertAspectRatioApprox(zoomed)
    }

    /**
     * Positive offsetX (pan right): the visible window in image space should shift left
     * (smaller left boundary values), i.e. we see more of the left side of the image.
     */
    @Test
    fun `positive offsetX shifts crop window to image left`() {
        val imgW = 4000; val imgH = 3000
        val base   = crop(imgW, imgH, scale = 2f, offsetX = 0f)
        val panned = crop(imgW, imgH, scale = 2f, offsetX = 200f)

        assertTrue("Panning right should move crop window left in image space", panned.left < base.left)
        assertAspectRatioApprox(panned)
    }

    /**
     * Positive offsetY (pan down): the visible window shifts up in image space.
     */
    @Test
    fun `positive offsetY shifts crop window upward in image space`() {
        val imgW = 3000; val imgH = 4000
        val base   = crop(imgW, imgH, scale = 2f, offsetY = 0f)
        val panned = crop(imgW, imgH, scale = 2f, offsetY = 200f)

        assertTrue("Panning down should move crop window up in image space", panned.top < base.top)
        assertAspectRatioApprox(panned)
    }

    /**
     * Result must always be within image bounds regardless of extreme input.
     */
    @Test
    fun `result is always clamped to image bounds`() {
        val imgW = 2000; val imgH = 2500
        // Extreme offset that would push the window outside the image.
        val rect = crop(imgW, imgH, scale = 1f, offsetX = 100_000f, offsetY = 100_000f)

        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.right <= imgW)
        assertTrue(rect.bottom <= imgH)
        assertTrue(rect.width >= 1)
        assertTrue(rect.height >= 1)
    }

    /**
     * The crop aspect ratio stays close to the container AR across a variety of images
     * and transform states.
     */
    @Test
    fun `aspect ratio is preserved across different images and transforms`() {
        // offsets must be within the clamp range that EditableImageContainer enforces,
        // otherwise the top/left boundary hits 0 and the AR check is meaningless.
        val cases = listOf(
            Triple(5000, 3000, Triple(1f,   0f,   0f)),
            Triple(3000, 5000, Triple(1.5f, 50f, -30f)),
            Triple(1080, 1350, Triple(2f,   0f,   0f)),
            // For 4032×3024 at scale=1.2: drawnH≈1350, maxOffsetY=(1350*1.2-1350)/2=135 → use 100
            Triple(4032, 3024, Triple(1.2f, 100f, 100f)),
        )
        for ((imgW, imgH, t) in cases) {
            val (scale, ox, oy) = t
            val rect = crop(imgW, imgH, scale, ox, oy)
            assertAspectRatioApprox(rect)
        }
    }
}

/**
 * Pure-JVM tests for [ImageCompressor.computeCropRect] with a **1:1 square container**,
 * matching [ImageCompressor.ProfileParams] (384×384 px avatar).
 *
 * Key invariants for the avatar case:
 *  - Output crop must always be square (width == height within ±2 px).
 *  - At scale=1 with no offset the crop must be centered in the image.
 *  - At scale=2 the visible region halves on both axes.
 *  - Panning in any direction shifts the crop window in the expected direction.
 *  - Result is always clamped to image bounds with a non-zero size.
 */
class ImageCompressorSquareCropTest {

    // Square container matching ProfileParams output size.
    private val cw = 384f
    private val ch = 384f

    private fun crop(
        imgW: Int, imgH: Int,
        scale: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ) = ImageCompressor.computeCropRect(imgW, imgH, cw, ch, scale, offsetX, offsetY)

    private fun assertSquare(rect: CropRect, tolerance: Int = 2) {
        assertTrue(
            "Crop must be square: width=${rect.width}, height=${rect.height}",
            abs(rect.width - rect.height) <= tolerance,
        )
    }

    private fun assertCentered(rect: CropRect, imgW: Int, imgH: Int, tolerance: Float = 2f) {
        val cx = (rect.left + rect.right) / 2f
        val cy = (rect.top + rect.bottom) / 2f
        assertTrue("Crop center X should be ≈ ${imgW / 2}: was $cx", abs(cx - imgW / 2f) <= tolerance)
        assertTrue("Crop center Y should be ≈ ${imgH / 2}: was $cy", abs(cy - imgH / 2f) <= tolerance)
    }

    // --- identity / no user interaction ---

    @Test
    fun `square image identity - full image returned`() {
        val imgW = 1080; val imgH = 1080
        val rect = crop(imgW, imgH)

        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(imgW, rect.right)
        assertEquals(imgH, rect.bottom)
        assertSquare(rect)
    }

    @Test
    fun `landscape image identity - horizontal center crop`() {
        val imgW = 4000; val imgH = 3000   // wider than square
        val rect = crop(imgW, imgH)

        // Full height used (portrait axis constrains), width trimmed to match height.
        assertEquals(imgH, rect.height)
        assertTrue("Width should equal height for 1:1 crop", abs(rect.width - imgH) <= 2)
        assertSquare(rect)
        assertCentered(rect, imgW, imgH)
    }

    @Test
    fun `portrait image identity - vertical center crop`() {
        val imgW = 3000; val imgH = 4000   // taller than square
        val rect = crop(imgW, imgH)

        // Full width used, height trimmed to match width.
        assertEquals(imgW, rect.width)
        assertTrue("Height should equal width for 1:1 crop", abs(rect.height - imgW) <= 2)
        assertSquare(rect)
        assertCentered(rect, imgW, imgH)
    }

    @Test
    fun `EXIF rotated image - dimensions swapped still produces square crop`() {
        // Simulates a 90°-rotated image where oriented W < oriented H.
        val imgW = 3024; val imgH = 4032   // portrait after EXIF rotation
        val rect = crop(imgW, imgH)

        assertSquare(rect)
        assertCentered(rect, imgW, imgH)
    }

    // --- scale (pinch zoom) ---

    @Test
    fun `scale 2 centered - crop is half size and stays centered`() {
        val imgW = 4000; val imgH = 4000
        val base   = crop(imgW, imgH, scale = 1f)
        val zoomed = crop(imgW, imgH, scale = 2f)

        assertTrue("Width should halve at 2×",  abs(zoomed.width  - base.width  / 2) <= 2)
        assertTrue("Height should halve at 2×", abs(zoomed.height - base.height / 2) <= 2)
        assertSquare(zoomed)
        assertCentered(zoomed, imgW, imgH)
    }

    @Test
    fun `scale 3 centered on landscape image - crop stays square and centered`() {
        val imgW = 5000; val imgH = 3000
        val rect = crop(imgW, imgH, scale = 3f)

        assertSquare(rect)
        assertCentered(rect, imgW, imgH)
        assertTrue("Crop should be smaller than base", rect.width < imgH)
    }

    // --- pan offset ---

    // For a 4000×4000 image in a 384×384 container at scale=2:
    //   drawnSize = 4000 × 0.096 = 384px; scaledSize = 768px
    //   maxOffset = (768 - 384) / 2 = 192px
    // Offsets of ±100px are safely within bounds, so no clamping occurs
    // and the crop remains square.

    @Test
    fun `positive offsetX pans crop window left in image space`() {
        val imgW = 4000; val imgH = 4000
        val base   = crop(imgW, imgH, scale = 2f, offsetX = 0f)
        val panned = crop(imgW, imgH, scale = 2f, offsetX = 100f)

        assertTrue("Pan right → crop window shifts left", panned.left < base.left)
        assertTrue("Pan right → crop window right also shifts left", panned.right < base.right)
        assertSquare(panned)
    }

    @Test
    fun `negative offsetX pans crop window right in image space`() {
        val imgW = 4000; val imgH = 4000
        val base   = crop(imgW, imgH, scale = 2f, offsetX = 0f)
        val panned = crop(imgW, imgH, scale = 2f, offsetX = -100f)

        assertTrue("Pan left → crop window shifts right", panned.left > base.left)
        assertSquare(panned)
    }

    @Test
    fun `positive offsetY pans crop window upward in image space`() {
        val imgW = 4000; val imgH = 4000
        val base   = crop(imgW, imgH, scale = 2f, offsetY = 0f)
        val panned = crop(imgW, imgH, scale = 2f, offsetY = 100f)

        assertTrue("Pan down → crop window shifts up", panned.top < base.top)
        assertSquare(panned)
    }

    @Test
    fun `negative offsetY pans crop window downward in image space`() {
        val imgW = 4000; val imgH = 4000
        val base   = crop(imgW, imgH, scale = 2f, offsetY = 0f)
        val panned = crop(imgW, imgH, scale = 2f, offsetY = -100f)

        assertTrue("Pan up → crop window shifts down", panned.top > base.top)
        assertSquare(panned)
    }

    // --- boundary clamping ---

    @Test
    fun `extreme positive offset clamps to image bounds`() {
        val imgW = 1080; val imgH = 1080
        val rect = crop(imgW, imgH, scale = 1f, offsetX = 100_000f, offsetY = 100_000f)

        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.right <= imgW)
        assertTrue(rect.bottom <= imgH)
        assertTrue(rect.width >= 1)
        assertTrue(rect.height >= 1)
    }

    @Test
    fun `extreme negative offset clamps to image bounds`() {
        val imgW = 1080; val imgH = 1080
        val rect = crop(imgW, imgH, scale = 1f, offsetX = -100_000f, offsetY = -100_000f)

        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.right <= imgW)
        assertTrue(rect.bottom <= imgH)
        assertTrue(rect.width >= 1)
        assertTrue(rect.height >= 1)
    }

    @Test
    fun `minimum image size 1x1 does not crash`() {
        val rect = crop(1, 1)

        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(1, rect.right)
        assertEquals(1, rect.bottom)
    }

    // --- ProfileParams output size consistency ---

    @Test
    fun `profile params aspect ratio is exactly 1 to 1`() {
        val params = ImageCompressor.ProfileParams
        assertEquals("ProfileParams must be square", params.maxWidthPx, params.maxHeightPx)
    }

    @Test
    fun `profile params output fits avatar display sizes with headroom`() {
        val params = ImageCompressor.ProfileParams
        // Dashboard avatar: 80dp × 4x density = 320px — well under 384.
        // Feed avatar: 37dp × 4x density = 148px — well under 384.
        assertTrue("ProfileParams width should be ≥ 320px for 4x screens", params.maxWidthPx >= 320)
        // Also shouldn't be wastefully large (original 512 was overkill).
        assertTrue("ProfileParams width should be ≤ 512px", params.maxWidthPx <= 512)
    }

    // --- aspect ratio stability across transforms ---

    @Test
    fun `square crop is preserved across varied images and transforms`() {
        val cases = listOf(
            Triple(5000, 5000, Triple(1f,    0f,    0f)),
            Triple(4000, 3000, Triple(1f,    0f,    0f)),
            Triple(3000, 4000, Triple(1f,    0f,    0f)),
            Triple(4032, 3024, Triple(2f,    0f,    0f)),
            Triple(4032, 3024, Triple(2f,  100f,    0f)),
            Triple(4032, 3024, Triple(2f,    0f,  100f)),
            Triple(1080, 1920, Triple(1.5f,  0f,    0f)),
        )
        for ((imgW, imgH, t) in cases) {
            val (scale, ox, oy) = t
            val rect = crop(imgW, imgH, scale, ox, oy)
            assertSquare(rect)
        }
    }
}
