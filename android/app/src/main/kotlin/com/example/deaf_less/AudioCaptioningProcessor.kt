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
        val timeDimension = embeddingShape[1]

        val attnLenData = longArrayOf(timeDimension)

        val generatedTokens = mutableListOf<Long>()
        generatedTokens.add(bosToken)

        var currentToken = bosToken

        val decInputNames = decoderSession.inputNames.toList()
        val nameIds = decInputNames[0]
        val nameEmb = decInputNames[1]
        val nameLen = decInputNames[2]

        for (step in 0 until maxTokens) {
            val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(longArrayOf(currentToken)), longArrayOf(1, 1))
            embeddingBuffer.rewind()
            val encoderHiddenStatesTensor = OnnxTensor.createTensor(env, embeddingBuffer, embeddingShape)
            val encoderAttnMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attnLenData), longArrayOf(1))
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