import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class AudioCapsTokenizer(context: Context, input: InputStream) {

    private val idToTokenMap = mutableMapOf<Int, String>()

    companion object {
        const val PAD_TOKEN_ID = 0
        const val BOS_TOKEN_ID = 1 // <start>
        const val EOS_TOKEN_ID = 2 // <end>
        const val UNK_TOKEN_ID = 3 // <unk>
    }

    init {
        loadFromAssets(context, input)
    }

    private fun loadFromAssets(context: Context, input: InputStream) {
        try {

            val jsonString = BufferedReader(InputStreamReader(input)).use { it.readText() }
            val rootObject = JSONObject(jsonString)
            val modelObject = rootObject.optJSONObject("model")
            val vocabObject = modelObject?.optJSONObject("vocab")

            if (vocabObject != null) {
                val keys = vocabObject.keys()
                while (keys.hasNext()) {
                    val word = keys.next()
                    val id = vocabObject.getInt(word)
                    idToTokenMap[id] = word
                }
                Log.d("Tokenizer", "Loaded ${idToTokenMap.size} tokens successfully.")
            } else {
                Log.e("Tokenizer", "Invalid JSON structure: 'model' or 'vocab' field missing.")
            }

        } catch (e: Exception) {
            Log.e("Tokenizer", "Failed to load tokenizer: ${e.message}")
            e.printStackTrace()
        }
    }

    fun decode(tokenIds: IntArray): String {
        val sentence = StringBuilder()

        for (id in tokenIds) {
            if (id == EOS_TOKEN_ID) break
            if (id == BOS_TOKEN_ID || id == PAD_TOKEN_ID || id == UNK_TOKEN_ID) continue
            val word = idToTokenMap[id]

            if (word != null) {
                if (sentence.isNotEmpty()) {
                    sentence.append(" ")
                }
                sentence.append(word)
            }
        }

        return sentence.toString()
    }
}