import android.content.Context
import android.util.Log
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.io.FileOutputStream

class AudioModel(private val context: Context) {
    private var module: Module? = null

    private val TARGET_SAMPLES = 80000
    private val INPUT_SHAPE = longArrayOf(1, TARGET_SAMPLES.toLong())

    fun loadModel(modelPath: String) {
        try {
            module = Module.load(modelPath)
            Log.e("Result", "Cargo path del modelo")
        } catch (e: Exception) {
            Log.e("ExecuTorch", "Failed load", e)
        }
    }

    fun predict(rawPcmBytes: ByteArray): IntArray? {
        if (module == null) return null

        val floatInput = preprocess(rawPcmBytes)
        val inputTensor = Tensor.fromBlob(floatInput, INPUT_SHAPE)

        try {
            Log.e("Result", "Empezo predict")
            val result = module!!.forward(EValue.from(inputTensor))
            if (result.isNotEmpty() && result[0].isTensor) {
                val outputTensor = result[0].toTensor()
                val floatArray = outputTensor.dataAsFloatArray
                return floatArray.map { it.toInt() }.toIntArray()
                /*val numClasses = 4981 // número de tokens
                val timeSteps = floatArray.size / numClasses
                return IntArray(timeSteps) { t ->
                    val startIdx = t * numClasses
                    val endIdx = startIdx + numClasses
                    floatArray.sliceArray(startIdx until endIdx)
                        .indices.maxByOrNull { floatArray[startIdx + it] } ?: 0
                }*/
            }
        } catch (e: Exception) {
            Log.e("ExecuTorch", "Inference error", e)
        }
        /*try {
            Log.e("Result", "Empezo predict")
            val result = module!!.forward(EValue.from(inputTensor))
            if (result.isNotEmpty() && result[0].isTensor) {
                val outputTensor = result[0].toTensor()
                val longArray = outputTensor.dataAsLongArray
                return longArray.map { it.toInt() }.toIntArray()
            }
        } catch (e: Exception) {
            Log.e("ExecuTorch", "Inference error", e)
        }*/
        return null
    }

    private fun preprocess(pcm16: ByteArray): FloatArray {
        val shorts = ShortArray(pcm16.size / 2)
        ByteBuffer.wrap(pcm16).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        val floatInput = FloatArray(TARGET_SAMPLES)
        val count = minOf(shorts.size, TARGET_SAMPLES)
        for (i in 0 until count) floatInput[i] = shorts[i] / 32768.0f
        return floatInput
    }
}