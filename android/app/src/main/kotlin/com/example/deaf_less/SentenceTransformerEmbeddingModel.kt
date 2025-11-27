package com.example.deaf_less

import android.content.Context
import android.util.Log
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import kotlin.math.sqrt

/**
 * Sentence Transformer embedding model using ExecuTorch .pte format.
 * Generates embeddings from tokenized text.
 */
class SentenceTransformerEmbeddingModel(private val context: Context) {
    
    private var module: Module? = null
    private val EMBEDDING_DIM = 384
    
    fun loadModel(modelPath: String) {
        try {
            module = Module.load(modelPath)
            Log.d("SentenceTransformer", "Model loaded successfully from: $modelPath")
        } catch (e: Exception) {
            Log.e("SentenceTransformer", "Failed to load model", e)
        }
    }
    
    /**
     * Generate normalized embedding from tokenized input.
     */
    fun generateEmbedding(inputIds: LongArray, attentionMask: LongArray): FloatArray? {
        if (module == null) {
            Log.e("SentenceTransformer", "Model not loaded")
            return null
        }
        
        try {
            // Create tensors
            val inputIdsTensor = Tensor.fromBlob(
                inputIds,
                longArrayOf(1, inputIds.size.toLong())
            )
            
            val attentionMaskTensor = Tensor.fromBlob(
                attentionMask,
                longArrayOf(1, attentionMask.size.toLong())
            )
            
            // Forward pass
            // Model expects only input_ids and attention_mask (2 inputs)
            val result = module!!.forward(
                EValue.from(inputIdsTensor),
                EValue.from(attentionMaskTensor)
            )
            
            if (result.isNotEmpty() && result[0].isTensor) {
                val outputTensor = result[0].toTensor()
                val embedding = outputTensor.dataAsFloatArray
                
                // Assuming the model outputs the pooled embedding directly or [CLS] token embedding
                // If it outputs (batch, seq, hidden), we might need to mean pool here.
                // For now, assume the export handles pooling or returns [CLS].
                
                // Check size
                val extractedEmbedding = if (embedding.size >= EMBEDDING_DIM) {
                    embedding.sliceArray(0 until EMBEDDING_DIM)
                } else {
                    embedding
                }
                
                return normalizeEmbedding(extractedEmbedding)
            } else {
                Log.e("SentenceTransformer", "Invalid output from model")
                return null
            }
        } catch (e: Exception) {
            Log.e("SentenceTransformer", "Inference error", e)
            return null
        }
    }
    
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        val norm = sqrt(embedding.sumOf { (it * it).toDouble() }.toFloat())
        return if (norm > 1e-9f) {
            embedding.map { it / norm }.toFloatArray()
        } else {
            embedding
        }
    }
}
