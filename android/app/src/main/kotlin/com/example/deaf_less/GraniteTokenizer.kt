package com.example.deaf_less

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStream

/**
 * Tokenizer for the Granite embedding model.
 * Handles text tokenization compatible with RoBERTa-based BPE tokenizer.
 */
class GraniteTokenizer(private val context: Context) {
    
    private var vocab: Map<String, Int> = emptyMap()
    private var merges: List<Pair<String, String>> = emptyList()
    private val BOS_TOKEN = 0L  // <s>
    private val PAD_TOKEN = 1L  // <pad>
    private val EOS_TOKEN = 2L  // </s>
    private val UNK_TOKEN = 3L  // <unk>
    
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
                vocabMap[key] = vocabObj.getInt(key)
            }
            vocab = vocabMap
            try {
                if (modelObj.has("merges")) {
                    val mergesArray = modelObj.getJSONArray("merges")
                    val mergesList = mutableListOf<Pair<String, String>>()
                    for (i in 0 until mergesArray.length()) {
                        val merge = mergesArray.getString(i).split(" ")
                        if (merge.size == 2) {
                            mergesList.add(Pair(merge[0], merge[1]))
                        }
                    }
                    merges = mergesList
                    Log.d("GraniteTokenizer", "Loaded tokenizer with ${vocab.size} vocab items and ${merges.size} merges")
                } else {
                    Log.d("GraniteTokenizer", "Loaded tokenizer with ${vocab.size} vocab items (no merges)")
                }
            } catch (e: Exception) {
                Log.w("GraniteTokenizer", "Could not load merges, using simplified tokenization", e)
            }
        } catch (e: Exception) {
            Log.e("GraniteTokenizer", "Failed to load tokenizer", e)
            throw e
        }
    }
    
    /**
     * Encode text to input_ids and attention_mask.
     * Returns Pair<LongArray, LongArray> of (input_ids, attention_mask)
     */
    fun encode(text: String): Pair<LongArray, LongArray> {
        // Simplified tokenization approach
        // For production, this should use full BPE algorithm
        
        // Byte-level pre-tokenization (simplified)
        val tokens = simplifiedTokenize(text.lowercase().trim())
        
        // Convert tokens to IDs
        val tokenIds = mutableListOf<Long>()
        tokenIds.add(BOS_TOKEN)  // Add <s> at start
        
        for (token in tokens) {
            val id = vocab[token]?.toLong() ?: UNK_TOKEN
            tokenIds.add(id)
            if (tokenIds.size >= MAX_LENGTH - 1) break
        }
        
        tokenIds.add(EOS_TOKEN)  // Add </s> at end
        
        // Create attention mask (1 for real tokens, 0 for padding)
        val attentionMask = LongArray(MAX_LENGTH) { if (it < tokenIds.size) 1L else 0L }
        
        // Pad to MAX_LENGTH
        val inputIds = LongArray(MAX_LENGTH) { i ->
            if (i < tokenIds.size) tokenIds[i] else PAD_TOKEN
        }
        
        return Pair(inputIds, attentionMask)
    }
    
    /**
     * Simplified tokenization - splits on whitespace and punctuation.
     * For production use, implement full BPE algorithm.
     */
    private fun simplifiedTokenize(text: String): List<String> {
        // Split on whitespace and basic punctuation
        val words = text.split(Regex("[\\s,.!?;:]+")).filter { it.isNotEmpty() }
        
        // Try to match vocab tokens
        val tokens = mutableListOf<String>()
        for (word in words) {
            // Look for the word with "Ġ" prefix (GPT-2 byte-level encoding)
            val prefixedWord = "Ġ$word"
            if (vocab.containsKey(prefixedWord)) {
                tokens.add(prefixedWord)
            } else if (vocab.containsKey(word)) {
                tokens.add(word)
            } else {
                // Fallback: split into characters
                word.forEach { char ->
                    val charToken = char.toString()
                    if (vocab.containsKey(charToken)) {
                        tokens.add(charToken)
                    }
                }
            }
        }
        
        return tokens
    }
}
