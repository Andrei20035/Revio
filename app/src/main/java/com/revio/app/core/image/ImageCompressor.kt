package com.revio.app.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCompressor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    data class Params(
        val maxWidthPx: Int,
        val maxHeightPx: Int,
        val jpegQuality: Int,
    ) {
        val aspectRatio: Float
            get() = maxWidthPx.toFloat() / maxHeightPx.toFloat()

        init {
            require(maxWidthPx > 0) { "maxWidthPx must be greater than 0" }
            require(maxHeightPx > 0) { "maxHeightPx must be greater than 0" }
            require(jpegQuality in 0..100) { "jpegQuality must be between 0 and 100" }
        }
    }

    data class CompressedImage(
        val bytes: ByteArray,
        val mimeType: String = JPEG_MIME_TYPE,
    )

    /**
     * Decodes, fixes EXIF orientation, downsizes and JPEG-compresses the image at [uri].
     *
     * The work runs on [Dispatchers.IO]. Invalid or unreadable images throw
     * [ImageCompressionException] with a user-safe message instead of crashing with a low-level
     * decoder error.
     */
    suspend fun compress(uri: Uri, params: Params): CompressedImage = withContext(Dispatchers.IO) {
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var cropped: Bitmap? = null
        var resized: Bitmap? = null

        try {
            val bounds = decodeBounds(uri)
            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxWidthPx = params.maxWidthPx,
                    maxHeightPx = params.maxHeightPx,
                )
            }

            decoded = decodeBitmap(uri, options)
            oriented = applyExifOrientation(decoded, readExifOrientation(uri))
            cropped = centerCropToAspectRatio(oriented, params.aspectRatio)
            resized = resizeIfNeeded(cropped, params.maxWidthPx, params.maxHeightPx)
            val bytes = compressToJpeg(resized, params.jpegQuality)

            CompressedImage(bytes = bytes)
        } catch (exception: ImageCompressionException) {
            throw exception
        } catch (exception: Exception) {
            throw ImageCompressionException("Failed to compress image", exception)
        } finally {
            recycleIfDifferent(decoded, oriented, cropped, resized)
        }
    }

    suspend fun compressProfileImage(uri: Uri): CompressedImage =
        compress(uri, ProfileParams)

    suspend fun compressCarImage(uri: Uri): CompressedImage =
        compress(uri, CarParams)

    /**
     * Like [compress], but honours the user's pinch/pan transform captured from the preview card.
     *
     * The crop region is derived from [transform] via [computeCropRect], which inverts the same
     * ContentScale.Crop + graphicsLayer mapping used by EditableImageContainer. The oriented
     * bitmap dimensions must match the image dimensions that Coil reported to the UI — both apply
     * EXIF correction, so they agree by construction.
     *
     * Falls back to a plain center-crop [compress] if [transform] carries no valid image size.
     */
    suspend fun compressWithCrop(uri: Uri, params: Params, transform: CropTransform): CompressedImage =
        withContext(Dispatchers.IO) {
            var decoded: Bitmap? = null
            var oriented: Bitmap? = null
            var cropped: Bitmap? = null
            var resized: Bitmap? = null

            try {
                val bounds = decodeBounds(uri)

                // Derive the crop rect in oriented-image coordinates using the preview transform.
                // We use the raw (full-resolution) oriented dimensions for the math, then scale
                // the resulting rect by the inSampleSize factor so it maps to the decoded bitmap.
                val orientedW: Int
                val orientedH: Int
                val exifOrientation = readExifOrientation(uri)
                val swapDimensions = exifOrientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                    exifOrientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                    exifOrientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                    exifOrientation == ExifInterface.ORIENTATION_TRANSVERSE
                if (swapDimensions) {
                    orientedW = bounds.outHeight
                    orientedH = bounds.outWidth
                } else {
                    orientedW = bounds.outWidth
                    orientedH = bounds.outHeight
                }

                val cropRect = computeCropRect(
                    imgW = orientedW,
                    imgH = orientedH,
                    containerW = transform.containerW,
                    containerH = transform.containerH,
                    scale = transform.scale,
                    offsetX = transform.offsetX,
                    offsetY = transform.offsetY,
                )

                // Choose inSampleSize based on the crop size (not the full image), so we don't
                // lose quality when the user has zoomed in on a small region.
                val options = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(
                        width = cropRect.width,
                        height = cropRect.height,
                        maxWidthPx = params.maxWidthPx,
                        maxHeightPx = params.maxHeightPx,
                    )
                }

                decoded = decodeBitmap(uri, options)
                oriented = applyExifOrientation(decoded, exifOrientation)

                // Scale the crop rect from full-resolution to the decoded (possibly sub-sampled)
                // bitmap. oriented dimensions may differ from orientedW/H by inSampleSize.
                val scaleX = oriented.width.toFloat() / orientedW
                val scaleY = oriented.height.toFloat() / orientedH
                val x = (cropRect.left  * scaleX).toInt().coerceIn(0, oriented.width  - 1)
                val y = (cropRect.top   * scaleY).toInt().coerceIn(0, oriented.height - 1)
                val w = (cropRect.width  * scaleX).toInt().coerceIn(1, oriented.width  - x)
                val h = (cropRect.height * scaleY).toInt().coerceIn(1, oriented.height - y)

                cropped = Bitmap.createBitmap(oriented, x, y, w, h)
                resized = resizeIfNeeded(cropped, params.maxWidthPx, params.maxHeightPx)
                val bytes = compressToJpeg(resized, params.jpegQuality)

                CompressedImage(bytes = bytes)
            } catch (exception: ImageCompressionException) {
                throw exception
            } catch (exception: Exception) {
                throw ImageCompressionException("Failed to compress image", exception)
            } finally {
                recycleIfDifferent(decoded, oriented, cropped, resized)
            }
        }

    private fun decodeBounds(uri: Uri): BitmapFactory.Options {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw ImageCompressionException("Selected file is not a valid image")
        }
        return options
    }

    private fun decodeBitmap(uri: Uri, options: BitmapFactory.Options): Bitmap {
        return openInputStream(uri).use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw ImageCompressionException("Unable to decode selected image")
    }

    private fun readExifOrientation(uri: Uri): Int {
        return openInputStream(uri).use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun centerCropToAspectRatio(bitmap: Bitmap, targetAspectRatio: Float): Bitmap {
        val currentAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (abs(currentAspectRatio - targetAspectRatio) < ASPECT_RATIO_EPSILON) {
            return bitmap
        }

        return if (currentAspectRatio > targetAspectRatio) {
            val cropWidth = (bitmap.height * targetAspectRatio)
                .roundToInt()
                .coerceIn(1, bitmap.width)
            val cropX = (bitmap.width - cropWidth) / 2
            Bitmap.createBitmap(bitmap, cropX, 0, cropWidth, bitmap.height)
        } else {
            val cropHeight = (bitmap.width / targetAspectRatio)
                .roundToInt()
                .coerceIn(1, bitmap.height)
            val cropY = (bitmap.height - cropHeight) / 2
            Bitmap.createBitmap(bitmap, 0, cropY, bitmap.width, cropHeight)
        }
    }

    private fun resizeIfNeeded(bitmap: Bitmap, maxWidthPx: Int, maxHeightPx: Int): Bitmap {
        if (bitmap.width <= maxWidthPx && bitmap.height <= maxHeightPx) return bitmap

        val scale = minOf(
            maxWidthPx.toFloat() / bitmap.width.toFloat(),
            maxHeightPx.toFloat() / bitmap.height.toFloat(),
        )
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun compressToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val compressed = output.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)
        }
        if (!compressed) {
            throw ImageCompressionException("Unable to compress selected image")
        }
        return output.toByteArray()
    }

    private fun openInputStream(uri: Uri) =
        context.contentResolver.openInputStream(uri)
            ?: throw ImageCompressionException("Unable to open selected image")

    private fun recycleIfDifferent(vararg bitmaps: Bitmap?) {
        bitmaps.filterNotNull().distinctBy { System.identityHashCode(it) }.forEach { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Int {
        var inSampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2

        while (
            halfWidth / inSampleSize >= maxWidthPx &&
            halfHeight / inSampleSize >= maxHeightPx
        ) {
            inSampleSize *= 2
        }

        return inSampleSize
    }

    companion object {
        const val JPEG_MIME_TYPE = "image/jpeg"
        private const val ASPECT_RATIO_EPSILON = 0.01f

        /**
         * Pure math, no Android deps — safe to unit-test on JVM.
         *
         * Reproduces the visible region of [EditableImageContainer] given the user's pinch/pan
         * transform. The container renders the image with ContentScale.Crop (cover), centered,
         * then applies a graphicsLayer scale [scale] + [offsetX]/[offsetY] translation around the
         * container center. This inverts that mapping to compute the source rectangle (in oriented
         * bitmap pixels) that fills the container viewport.
         *
         * @param imgW    oriented bitmap width  (px) — after EXIF rotation
         * @param imgH    oriented bitmap height (px) — after EXIF rotation
         * @param containerW  preview container width  (px, same density as offset)
         * @param containerH  preview container height (px, same density as offset)
         * @param scale   user pinch scale (≥ 1)
         * @param offsetX user pan offset X (container-space px, positive = right)
         * @param offsetY user pan offset Y (container-space px, positive = down)
         */
        internal fun computeCropRect(
            imgW: Int,
            imgH: Int,
            containerW: Float,
            containerH: Float,
            scale: Float,
            offsetX: Float,
            offsetY: Float,
        ): CropRect {
            // Base cover scale: the factor applied by ContentScale.Crop to fill the container.
            val s0 = maxOf(containerW / imgW, containerH / imgH)
            // Effective scale in image-space (container px per image px).
            val eff = s0 * scale

            // Invert: left/top/right/bottom in oriented image pixels.
            val left   = imgW / 2f + (0f         - containerW / 2f - offsetX) / eff
            val right  = imgW / 2f + (containerW  - containerW / 2f - offsetX) / eff
            val top    = imgH / 2f + (0f         - containerH / 2f - offsetY) / eff
            val bottom = imgH / 2f + (containerH  - containerH / 2f - offsetY) / eff

            // Clamp to bitmap bounds and ensure non-zero size.
            val l = left.toInt().coerceIn(0, imgW - 1)
            val t = top.toInt().coerceIn(0, imgH - 1)
            val r = right.toInt().coerceIn(l + 1, imgW)
            val b = bottom.toInt().coerceIn(t + 1, imgH)
            return CropRect(l, t, r, b)
        }

        val ProfileParams = Params(
            maxWidthPx = 384,
            maxHeightPx = 384,
            jpegQuality = 80
        )

        val CarParams = Params(
            maxWidthPx = 1080,
            maxHeightPx = 1350,
            jpegQuality = 82
        )
    }
}

/** Axis-aligned crop rectangle in oriented bitmap pixels. All values are pixel-exact integers. */
data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int  get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Captures the user's pinch/pan transform from the preview card, plus the geometry needed
 * to invert it back to image coordinates.
 *
 * All pixel values are in the same unit — container/screen pixels at the display density.
 *
 * @param scale      graphicsLayer scale applied by EditableImageContainer (≥ 1)
 * @param offsetX    graphicsLayer translationX (px), positive = image shifted right
 * @param offsetY    graphicsLayer translationY (px), positive = image shifted down
 * @param containerW preview container width  in px (same density as offset)
 * @param containerH preview container height in px (same density as offset)
 */
data class CropTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val containerW: Float,
    val containerH: Float,
)

class ImageCompressionException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
