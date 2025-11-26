package com.example.deaf_less

import android.content.Context
import android.util.Log
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import kotlin.math.sqrt

/**
 * Granite embedding model using ExecuTorch .pte format.
 * Generates 384-dimensional embeddings from tokenized text.
 */
class GraniteEmbeddingModel(private val context: Context) {
    
    private var module: Module? = null
    private val EMBEDDING_DIM = 384
    
    /**
     * Load the .pte model from file path
     */
    fun loadModel(modelPath: String) {
        try {
            module = Module.load(modelPath)
            Log.d("GraniteEmbedding", "Model loaded successfully from: $modelPath")
        } catch (e: Exception) {
            Log.e("GraniteEmbedding", "Failed to load model", e)
        }
    }
    
    /**
     * Generate normalized embedding from tokenized input.
     * @param inputIds Token IDs (padded to 128)
     * @param attentionMask Attention mask (1 for real tokens, 0 for padding)
     * @return Normalized 384-dimensional embedding as FloatArray
     */
    fun generateEmbedding(inputIds: LongArray, attentionMask: LongArray): FloatArray? {
        if (module == null) {
            Log.e("GraniteEmbedding", "Model not loaded")
            return null
        }
        
        try {
            // Create tensors for input
            val inputIdsTensor = Tensor.fromBlob(
                inputIds,
                longArrayOf(1, inputIds.size.toLong())
            )
            
            val attentionMaskTensor = Tensor.fromBlob(
                attentionMask,
                longArrayOf(1, attentionMask.size.toLong())
            )
            
            // Run inference
            val result = module!!.forward(
                EValue.from(inputIdsTensor),
                EValue.from(attentionMaskTensor)
            )
            
            if (result.isNotEmpty() && result[0].isTensor) {
                val outputTensor = result[0].toTensor()
                val embedding = outputTensor.dataAsFloatArray
                
                // Extract first EMBEDDING_DIM values (in case output is larger)
                val extractedEmbedding = if (embedding.size >= EMBEDDING_DIM) {
                    embedding.sliceArray(0 until EMBEDDING_DIM)
                } else {
                    embedding
                }
                
                // Normalize the embedding
                val normalized = normalizeEmbedding(extractedEmbedding)
                
                Log.d("GraniteEmbedding", "Generated embedding of size ${normalized.size}")
                return normalized
            } else {
                Log.e("GraniteEmbedding", "Invalid output from model")
                return null
            }
        } catch (e: Exception) {
            Log.e("GraniteEmbedding", "Inference error", e)
            return null
        }
    }
    
    /**
     * Normalize embedding to unit length (L2 normalization)
     */
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        val norm = sqrt(embedding.sumOf { (it * it).toDouble() }.toFloat())
        
        return if (norm > 1e-9f) {
            embedding.map { it / norm }.toFloatArray()
        } else {
            embedding
        }
    }
}
