package com.example.deaf_less

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Data class representing a category
 */
data class Category(
    val id: String,
    val label: String,
    val description: String,
    val textEmbedded: String
)

/**
 * Data class representing a category match with similarity score
 */
data class CategoryMatch(
    val category: Category,
    val score: Float
)

/**
 * Matches embeddings against pre-computed category embeddings.
 * Loads category_embeddings.json and performs cosine similarity matching.
 */
class CategoryMatcher(private val context: Context) {
    
    private var categories: List<Category> = emptyList()
    private var categoryEmbeddings: List<FloatArray> = emptyList()
    
    /**
     * Load categories and embeddings from JSON file
     */
    fun loadCategories(inputStream: InputStream) {
        try {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            // Parse categories
            val categoriesArray = jsonObject.getJSONArray("categories")
            val categoryList = mutableListOf<Category>()
            
            for (i in 0 until categoriesArray.length()) {
                val catObj = categoriesArray.getJSONObject(i)
                val category = Category(
                    id = catObj.getString("id"),
                    label = catObj.getString("label"),
                    description = catObj.getString("description"),
                    textEmbedded = catObj.getString("text_embedded")
                )
                categoryList.add(category)
            }
            categories = categoryList
            
            // Parse embeddings
            val embeddingsArray = jsonObject.getJSONArray("embeddings")
            val embeddingsList = mutableListOf<FloatArray>()
            
            for (i in 0 until embeddingsArray.length()) {
                val embArray = embeddingsArray.getJSONArray(i)
                val embedding = FloatArray(embArray.length()) { j ->
                    embArray.getDouble(j).toFloat()
                }
                embeddingsList.add(embedding)
            }
            categoryEmbeddings = embeddingsList
            
            Log.d("CategoryMatcher", "Loaded ${categories.size} categories with embeddings")
        } catch (e: Exception) {
            Log.e("CategoryMatcher", "Failed to load categories", e)
        }
    }
    
    /**
     * Find top N matching categories for the given embedding.
     * @param embedding The query embedding (384-dim, normalized)
     * @param topN Number of top matches to return
     * @return List of CategoryMatch objects sorted by score (descending)
     */
    fun findTopMatches(embedding: FloatArray, topN: Int = 3): List<CategoryMatch> {
        if (categories.isEmpty() || categoryEmbeddings.isEmpty()) {
            Log.w("CategoryMatcher", "Categories not loaded")
            return emptyList()
        }
        
        // Calculate cosine similarity for each category
        val matches = mutableListOf<CategoryMatch>()
        
        for (i in categories.indices) {
            val similarity = cosineSimilarity(embedding, categoryEmbeddings[i])
            matches.add(CategoryMatch(categories[i], similarity))
        }
        
        // Sort by score descending and take top N
        return matches.sortedByDescending { it.score }.take(topN)
    }
    
    /**
     * Calculate cosine similarity between two embeddings.
     * Assumes both embeddings are already normalized.
     */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) {
            Log.w("CategoryMatcher", "Embedding dimension mismatch: ${a.size} vs ${b.size}")
            return 0f
        }
        
        // Dot product (since vectors are normalized, this is cosine similarity)
        return a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
    }
}
