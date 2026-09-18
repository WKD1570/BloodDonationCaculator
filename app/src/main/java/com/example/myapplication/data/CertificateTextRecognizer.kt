package com.example.myapplication.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.myapplication.domain.BloodDonationInfo
import com.example.myapplication.domain.parseBloodDonationInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A fresh cache-backed file for a freshly captured certificate photo, e.g. as a CameraX [androidx.camera.core.ImageCapture] output target. */
fun createCertificatePhotoFile(context: Context): File {
    val dir = File(context.cacheDir, "certificate_photos").apply { mkdirs() }
    return File(dir, "certificate_${System.currentTimeMillis()}.jpg")
}

/** The [content://][Uri] that other apps/components (e.g. ML Kit's file-based APIs) can read [file] through. */
fun certificatePhotoUriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

/**
 * The capture-guide screen (see [com.example.myapplication.ui.BloodDonationCalculatorScreen])
 * draws its guide frame at this same width fraction and aspect ratio, so [cropToGuideFrame] below
 * crops to the region the user was told to align the certificate within. The system camera app
 * owns its own live preview surface, so this is the closest we can get to a true camera overlay -
 * a consistent guide shown just before capture, then honored as the crop region afterward.
 */
const val GUIDE_FRAME_WIDTH_FRACTION = 0.92f
const val GUIDE_FRAME_ASPECT_RATIO = 3f / 4f

/**
 * [rawText] is ML Kit's unmodified recognized text and [info] is it run through
 * [parseBloodDonationInfo]. Callers should show [rawText] to the user (at least on a parse
 * mismatch) - since the parser only ever reads label-adjacent text, the most useful diagnostic
 * for "a field filled in wrong" is always what OCR actually saw, not another guess at the regex.
 */
data class CertificateScanResult(val rawText: String, val info: BloodDonationInfo)

/**
 * Runs on-device OCR (ML Kit, Korean script model) over a certificate photo. On successful
 * recognition, the recognized [Text][com.google.mlkit.vision.text.Text]'s `text` block is parsed
 * into a [BloodDonationInfo] immediately, so callers get structured fields rather than raw OCR text.
 *
 * The image is decoded upright (EXIF rotation applied), optionally cropped to the guide-frame
 * region when [applyRoiCrop] is true (camera captures only - a gallery pick has no guide frame to
 * crop to), then converted to high-contrast grayscale before recognition, which makes the printed
 * text stand out from the certificate's background and improves recognition accuracy.
 */
suspend fun recognizeCertificateInfo(
    context: Context,
    imageUri: Uri,
    applyRoiCrop: Boolean
): CertificateScanResult {
    val upright = decodeUprightBitmap(context, imageUri)
    val roi = if (applyRoiCrop) cropToGuideFrame(upright) else upright
    val processed = toGrayscaleHighContrast(roi)
    val image = InputImage.fromBitmap(processed, 0)
    val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    return suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { result ->
                continuation.resume(CertificateScanResult(result.text, parseBloodDonationInfo(result.text)))
            }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
}

/** Decodes [uri] and rotates it per its EXIF orientation tag, so pixel-based cropping below always operates on an upright image. */
private fun decodeUprightBitmap(context: Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    val bitmap = resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        ?: throw IllegalArgumentException("Unable to decode image: $uri")
    val rotationDegrees = resolver.openInputStream(uri)?.use { stream ->
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } ?: 0
    if (rotationDegrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Center-crops [bitmap] to the same width fraction and aspect ratio as the on-screen guide frame. */
private fun cropToGuideFrame(bitmap: Bitmap): Bitmap {
    val frameWidth = bitmap.width * GUIDE_FRAME_WIDTH_FRACTION
    val frameHeight = (frameWidth / GUIDE_FRAME_ASPECT_RATIO).coerceAtMost(bitmap.height.toFloat())
    val clampedWidth = (frameHeight * GUIDE_FRAME_ASPECT_RATIO).coerceAtMost(bitmap.width.toFloat())
    val left = ((bitmap.width - clampedWidth) / 2f).toInt().coerceAtLeast(0)
    val top = ((bitmap.height - frameHeight) / 2f).toInt().coerceAtLeast(0)
    return Bitmap.createBitmap(bitmap, left, top, clampedWidth.toInt(), frameHeight.toInt())
}

/**
 * Converts [bitmap] to grayscale and boosts contrast by [contrast] (2.0-2.5 makes printed text
 * stand out clearly from the certificate's background for OCR). Grayscale and contrast commute
 * here since the luminance weights sum to 1, so a single combined [ColorMatrix] is equivalent to
 * applying either step first.
 */
private fun toGrayscaleHighContrast(bitmap: Bitmap, contrast: Float = 2.2f): Bitmap {
    val translate = (-0.5f * contrast + 0.5f) * 255f
    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
    colorMatrix.postConcat(contrastMatrix)

    val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) })
    return output
}
