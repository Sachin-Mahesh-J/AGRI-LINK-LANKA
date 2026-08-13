package com.example.agriscout.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ImageClassificationResult(
    val label: String,
    val confidence: Int,
    val labelInfo: ImageLabelMapping.LabelInfo
)

class ImageDiseaseClassifier(context: Context) {
    private val interpreter: Interpreter?
    private val labels: List<String>

    init {
        val loaded = runCatching {
            val assetManager = context.assets
            val loadedLabels = assetManager.open(LABELS_ASSET).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.toList()
            }
            val modelBuffer = loadModelFile(context, MODEL_ASSET)
            loadedLabels to Interpreter(modelBuffer)
        }.onFailure { error ->
            Log.e(TAG, "Image disease model unavailable; falling back to text detection", error)
        }.getOrNull()

        if (loaded == null) {
            labels = emptyList()
            interpreter = null
        } else {
            labels = loaded.first
            interpreter = loaded.second
        }
    }

    val isReady: Boolean
        get() = interpreter != null && labels.isNotEmpty()

    fun classify(bitmap: Bitmap): ImageClassificationResult? {
        val activeInterpreter = interpreter ?: return null
        if (labels.isEmpty()) return null
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = preprocess(resized)
        val output = Array(1) { FloatArray(labels.size) }
        activeInterpreter.run(inputBuffer, output)

        var bestIndex = 0
        var bestScore = output[0][0]
        for (index in 1 until labels.size) {
            val score = output[0][index]
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }

        val label = labels[bestIndex]
        val labelInfo = ImageLabelMapping.resolve(label) ?: return null
        val confidence = (bestScore * 100f).toInt().coerceIn(0, 99)
        return ImageClassificationResult(label = label, confidence = confidence, labelInfo = labelInfo)
    }

    fun classify(uri: Uri, contentResolver: android.content.ContentResolver): ImageClassificationResult? {
        if (!isReady) return null
        val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null
        return classify(bitmap)
    }

    fun close() {
        interpreter?.close()
    }

    /**
     * Model graph includes MobileNetV3 preprocess_input, so feed raw 0–255 RGB floats.
     */
    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF).toFloat())
            buffer.putFloat(((pixel shr 8) and 0xFF).toFloat())
            buffer.putFloat((pixel and 0xFF).toFloat())
        }
        buffer.rewind()
        return buffer
    }

    companion object {
        private const val TAG = "ImageDiseaseClassifier"
        private const val MODEL_ASSET = "models/plant_disease.tflite"
        private const val LABELS_ASSET = "models/labels.txt"
        private const val INPUT_SIZE = 224

        private fun loadModelFile(context: Context, assetPath: String): MappedByteBuffer {
            context.assets.openFd(assetPath).use { assetFileDescriptor ->
                FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
                    val fileChannel = inputStream.channel
                    return fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        assetFileDescriptor.startOffset,
                        assetFileDescriptor.declaredLength
                    )
                }
            }
        }
    }
}
