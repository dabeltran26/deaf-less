package com.example.deaf_less

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.Collections

class AudioCaptioningProcessor() {
    private val maxTokens = 30 // Matches Python script default
    private val bosToken = 1L
    private val eosToken = 2L

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private lateinit var encoderSession: OrtSession
    private lateinit var decoderModule: Module

    fun initializeModels(encoderBytes: ByteArray, decoderPath: String) {
        encoderSession = env.createSession(encoderBytes)
        decoderModule = Module.load(decoderPath)
    }

    fun generateCaption(audioData: FloatArray): List<Long> {
        // 1. Run ONNX Encoder
        // Input: audio (1, 80000)
        val inputShape = longArrayOf(1, audioData.size.toLong())
        val audioTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(audioData), inputShape)

        val encInputName = encoderSession.inputNames.iterator().next()
        val encResult = encoderSession.run(Collections.singletonMap(encInputName, audioTensor))

        // Output: attn_emb (1, seq_len, 1408)
        val embeddingTensor = encResult[0] as OnnxTensor
        val embeddingBuffer = embeddingTensor.floatBuffer
        val embeddingShape = embeddingTensor.info.shape
        
        val timeDimension = embeddingShape[1].toInt()
        val featureDimension = embeddingShape[2].toInt()
        
        // CRITICAL FIX: Decoder requires exactly 16 time steps
        // If encoder outputs a different number (e.g. 32 due to 32kHz input), we need to downsample
        val targetTimeDim = 16
        val finalEmbeddingArray: FloatArray
        val finalTimeDim: Long
        
        if (timeDimension == targetTimeDim) {
            // No downsampling needed
            val embeddingSize = embeddingBuffer.remaining()
            finalEmbeddingArray = FloatArray(embeddingSize)
            embeddingBuffer.get(finalEmbeddingArray)
            finalTimeDim = timeDimension.toLong()
        } else {
            // Downsample by averaging adjacent time steps
            val downsampleFactor = timeDimension / targetTimeDim
            finalEmbeddingArray = FloatArray(targetTimeDim * featureDimension)
            
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
                    finalEmbeddingArray[t * featureDimension + f] = sum / downsampleFactor
                }
            }
            
            finalTimeDim = targetTimeDim.toLong()
            android.util.Log.d("AudioProcessor", "Downsampled from $timeDimension to $targetTimeDim time steps")
        }
        
        // Calculate attn_emb_len (seq_len - 1)
        val attnEmbLen = longArrayOf(finalTimeDim - 1)

        // 2. Run ExecuTorch Decoder Autoregressively
        val generatedTokens = mutableListOf<Long>()
        generatedTokens.add(bosToken)

        for (step in 0 until maxTokens) {
            // Prepare inputs
            // 1. word_ids: (1, current_seq_len)
            val currentTokensArray = generatedTokens.toLongArray()
            val wordIdsTensor = Tensor.fromBlob(
                currentTokensArray,
                longArrayOf(1, currentTokensArray.size.toLong())
            )

            // 2. attn_emb: (1, seq_len, 1408)
            val attnEmbTensor = Tensor.fromBlob(
                finalEmbeddingArray,
                longArrayOf(1, finalTimeDim, featureDimension.toLong())
            )

            // 3. attn_emb_len: (1,)
            val attnEmbLenTensor = Tensor.fromBlob(
                attnEmbLen,
                longArrayOf(1)
            )

            // Forward pass
            val result = decoderModule.forward(
                EValue.from(wordIdsTensor),
                EValue.from(attnEmbTensor),
                EValue.from(attnEmbLenTensor)
            )

            // Output: logits (1, current_seq_len, vocab_size)
            val logitsTensor = result[0].toTensor()
            val logitsData = logitsTensor.dataAsFloatArray
            val logitsShape = logitsTensor.shape() // [1, seq_len, vocab_size]
            
            val vocabSize = logitsShape[2].toInt()
            val currentSeqLen = logitsShape[1].toInt()
            
            // Get logits for the last token
            // Index start for last token's logits
            val lastTokenStartIndex = (currentSeqLen - 1) * vocabSize
            
            // Find argmax
            var maxVal = Float.NEGATIVE_INFINITY
            var nextToken = 0L
            
            for (i in 0 until vocabSize) {
                val valAtIdx = logitsData[lastTokenStartIndex + i]
                if (valAtIdx > maxVal) {
                    maxVal = valAtIdx
                    nextToken = i.toLong()
                }
            }

            generatedTokens.add(nextToken)

            if (nextToken == eosToken) {
                break
            }
        }
        
        encResult.close()
        audioTensor.close()
        
        return generatedTokens
    }

    fun close() {
        encoderSession.close()
        // decoderModule.destroy() // If available/needed
        env.close()
    }
}