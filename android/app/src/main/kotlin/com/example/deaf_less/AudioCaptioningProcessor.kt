package com.example.deaf_less
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Collections

class AudioCaptioningProcessor() {
    private val maxTokens = 80
    private val bosToken = 1L
    private val eosToken = 2L

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private lateinit var encoderSession: OrtSession
    private lateinit var decoderSession: OrtSession

    fun initializeModels(encoderBytes: ByteArray, decoderBytes: ByteArray) {
        encoderSession = env.createSession(encoderBytes)
        decoderSession = env.createSession(decoderBytes)
    }

    fun generateCaption(audioData: FloatArray): List<Long> {
        val inputShape = longArrayOf(1, audioData.size.toLong())
        val audioTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(audioData), inputShape)

        val encInputName = encoderSession.inputNames.iterator().next()
        val encResult = encoderSession.run(Collections.singletonMap(encInputName, audioTensor))

        val embeddingTensor = encResult[0] as OnnxTensor
        val embeddingBuffer = embeddingTensor.floatBuffer
        val embeddingShape = embeddingTensor.info.shape
        val timeDimension = embeddingShape[1].toInt()
        val featureDimension = embeddingShape[2].toInt()
        
        // CRITICAL FIX: Decoder requires exactly 16 time steps
        // If encoder outputs a different number, we need to downsample
        val targetTimeDim = 16
        val downsampledBuffer: FloatBuffer
        val finalTimeDim: Long
        
        if (timeDimension == targetTimeDim) {
            // No downsampling needed
            downsampledBuffer = embeddingBuffer
            finalTimeDim = timeDimension.toLong()
        } else {
            // Downsample by averaging adjacent time steps
            val downsampleFactor = timeDimension / targetTimeDim
            val downsampledArray = FloatArray(targetTimeDim * featureDimension)
            
            // Read all embeddings into array
            embeddingBuffer.rewind()
            val allEmbeddings = FloatArray(timeDimension * featureDimension)
            embeddingBuffer.get(allEmbeddings)
            
            // Average groups of time steps
            for (t in 0 until targetTimeDim) {
                for (f in 0 until featureDimension) {
                    var sum = 0f
                    for (i in 0 until downsampleFactor) {
                        val srcIdx = (t * downsampleFactor + i) * featureDimension + f
                        sum += allEmbeddings[srcIdx]
                    }
                    downsampledArray[t * featureDimension + f] = sum / downsampleFactor
                }
            }
            
            downsampledBuffer = FloatBuffer.wrap(downsampledArray)
            finalTimeDim = targetTimeDim.toLong()
            android.util.Log.d("AudioProcessor", "Downsampled from $timeDimension to $targetTimeDim time steps")
        }

        val attnLenData = longArrayOf(finalTimeDim)

        val generatedTokens = mutableListOf<Long>()
        generatedTokens.add(bosToken)

        var currentToken = bosToken

        val decInputNames = decoderSession.inputNames.toList()
        val nameIds = decInputNames[0]
        val nameEmb = decInputNames[1]
        val nameLen = decInputNames[2]

        for (step in 0 until maxTokens) {
            val tokenBuffer = LongBuffer.allocate(1)
            tokenBuffer.put(currentToken)
            tokenBuffer.rewind()
            val inputIdsTensor = OnnxTensor.createTensor(env, tokenBuffer, longArrayOf(1, 1))
            
            downsampledBuffer.rewind()
            val downsampledShape = longArrayOf(1, finalTimeDim, featureDimension.toLong())
            val encoderHiddenStatesTensor = OnnxTensor.createTensor(env, downsampledBuffer, downsampledShape)
            
            val attnLenBuffer = LongBuffer.allocate(1)
            attnLenBuffer.put(finalTimeDim)
            attnLenBuffer.rewind()
            val encoderAttnMaskTensor = OnnxTensor.createTensor(env, attnLenBuffer, longArrayOf(1))
            
            android.util.Log.d("DecoderDebug", "Step $step - inputIds shape: ${inputIdsTensor.info.shape.contentToString()}")
            android.util.Log.d("DecoderDebug", "Step $step - embeddings shape: ${encoderHiddenStatesTensor.info.shape.contentToString()}")
            android.util.Log.d("DecoderDebug", "Step $step - attn_len shape: ${encoderAttnMaskTensor.info.shape.contentToString()}")
            
            val inputs = mapOf(
                nameIds to inputIdsTensor,
                nameEmb to encoderHiddenStatesTensor,
                nameLen to encoderAttnMaskTensor
            )

            val decResult = decoderSession.run(inputs)

            val logitsTensor = decResult[0] as OnnxTensor
            val logitsBuffer = logitsTensor.floatBuffer

            val nextToken = argmax(logitsBuffer)

            decResult.close()
            inputIdsTensor.close()
            encoderHiddenStatesTensor.close()
            encoderAttnMaskTensor.close()
            generatedTokens.add(nextToken)
            currentToken = nextToken

            if (currentToken == eosToken) {
                break
            }
        }
        encResult.close()
        return generatedTokens
    }

    private fun argmax(buffer: FloatBuffer): Long {
        var maxVal = Float.NEGATIVE_INFINITY
        var maxIdx = 0L
        for (i in 0 until buffer.remaining()) {
            val f = buffer.get(i)
            if (f > maxVal) {
                maxVal = f
                maxIdx = i.toLong()
            }
        }
        return maxIdx
    }

    fun close() {
        encoderSession.close()
        decoderSession.close()
        env.close()
    }
}