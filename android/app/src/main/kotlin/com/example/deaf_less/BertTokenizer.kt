package com.example.deaf_less

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStream
import java.text.Normalizer

/**
 * Tokenizer for Sentence Transformer (BERT-based) models.
 * Implements WordPiece tokenization.
 */
class BertTokenizer(private val context: Context) {
    
    private var vocab: Map<String, Int> = emptyMap()
    private val CLS_TOKEN = "[CLS]"
    private val SEP_TOKEN = "[SEP]"
    private val PAD_TOKEN = "[PAD]"
    private val UNK_TOKEN = "[UNK]"
    
    private var clsTokenId = 101
    private var sepTokenId = 102
    private var padTokenId = 0
    private var unkTokenId = 100
    
    private val MAX_LENGTH = 128
    
    fun loadTokenizer(inputStream: InputStream) {
        try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            // Load vocabulary from model section
            val modelObj = jsonObject.getJSONObject("model")
            val vocabObj = modelObj.getJSONObject("vocab")
            
            val vocabMap = mutableMapOf<String, Int>()
            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val id = vocabObj.getInt(key)
                vocabMap[key] = id
            }
            vocab = vocabMap
            
            // Update special token IDs if present
            clsTokenId = vocab[CLS_TOKEN] ?: 101
            sepTokenId = vocab[SEP_TOKEN] ?: 102
            padTokenId = vocab[PAD_TOKEN] ?: 0
            unkTokenId = vocab[UNK_TOKEN] ?: 100
            
            Log.d("BertTokenizer", "Loaded tokenizer with ${vocab.size} vocab items")
        } catch (e: Exception) {
            Log.e("BertTokenizer", "Failed to load tokenizer", e)
            throw e
        }
    }
    
    /**
     * Encode text to input_ids, attention_mask, and token_type_ids.
     * Returns Triple<LongArray, LongArray, LongArray>
     */
    fun encode(text: String): Triple<LongArray, LongArray, LongArray> {
        val tokens = tokenize(text)
        
        // Convert tokens to IDs
        val tokenIds = mutableListOf<Long>()
        tokenIds.add(clsTokenId.toLong())
        
        for (token in tokens) {
            val id = vocab[token]?.toLong() ?: unkTokenId.toLong()
            tokenIds.add(id)
            if (tokenIds.size >= MAX_LENGTH - 1) break
        }
        
        tokenIds.add(sepTokenId.toLong())
        
        // Create attention mask (1 for real tokens, 0 for padding)
        val attentionMask = LongArray(MAX_LENGTH) { if (it < tokenIds.size) 1L else 0L }
        
        // Token type IDs (all 0 for single sentence)
        val tokenTypeIds = LongArray(MAX_LENGTH) { 0L }
        
        // Pad input_ids
        val inputIds = LongArray(MAX_LENGTH) { i ->
            if (i < tokenIds.size) tokenIds[i] else padTokenId.toLong()
        }
        
        return Triple(inputIds, attentionMask, tokenTypeIds)
    }
    
    private fun tokenize(text: String): List<String> {
        val cleanText = cleanText(text)
        val basicTokens = cleanText.split(Regex("\\s+"))
        val wordPieceTokens = mutableListOf<String>()
        
        for (token in basicTokens) {
            if (token.isEmpty()) continue
            
            var start = 0
            while (start < token.length) {
                var end = token.length
                var subToken: String? = null
                
                while (start < end) {
                    var substr = token.substring(start, end)
                    if (start > 0) {
                        substr = "##$substr"
                    }
                    
                    if (vocab.containsKey(substr)) {
                        subToken = substr
                        break
                    }
                    end--
                }
                
                if (subToken == null) {
                    wordPieceTokens.add(UNK_TOKEN)
                    break
                } else {
                    wordPieceTokens.add(subToken)
                    start = end
                }
            }
        }
        
        return wordPieceTokens
    }
    
    private fun cleanText(text: String): String {
        // Lowercase and normalize
        var normalized = text.lowercase()
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
        
        // Add spaces around punctuation
        normalized = normalized.replace(Regex("([.,!?;:])"), " $1 ")
        
        return normalized
    }
}
