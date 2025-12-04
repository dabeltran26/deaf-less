package com.example.deaf_less

import AudioCapsTokenizer
import android.content.ContentValues
import android.content.Intent
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.provider.MediaStore
import android.media.AudioFormat
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : FlutterActivity() {

    private lateinit var audioCaptioningProcessor: AudioCaptioningProcessor

    private val methodChannelName = "sound_guardian/audio"
    private val eventChannelName = "sound_guardian/audioStream"

    private var isStarted = false
    private var eventSink: EventChannel.EventSink? = null
    private var enabledSoundIds: List<String> = emptyList()

    private var tokenizer: AudioCapsTokenizer? = null
    private var bertTokenizer: BertTokenizer? = null
    private var embeddingModel: SentenceTransformerEmbeddingModel? = null
    private var categoryMatcher: CategoryMatcher? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, methodChannelName)
            .setMethodCallHandler { call: MethodCall, result: MethodChannel.Result ->
                when (call.method) {
                    "startMonitoring" -> {
                        val started = startAudioStream()
                        if (started) {
                            val startIntent = Intent(this, MonitoringForegroundService::class.java).apply {
                                action = MonitoringForegroundService.ACTION_START
                                putExtra(MonitoringForegroundService.EXTRA_CONTENT, "Nothing")
                            }
                            ContextCompat.startForegroundService(this, startIntent)
                            result.success(true)
                        } else {
                            result.error("NO_PERMISSION", "RECORD_AUDIO permission not granted", null)
                        }
                    }
                    "stopMonitoring" -> {
                        stopAudioStream()
                        val stopIntent = Intent(this, MonitoringForegroundService::class.java).apply {
                            action = MonitoringForegroundService.ACTION_STOP
                        }
                        startService(stopIntent)
                        result.success(true)
                    }
                    "updateNotification" -> {
                        val content = call.argument<String>("content") ?: ""
                        val updateIntent = Intent(this, MonitoringForegroundService::class.java).apply {
                            action = MonitoringForegroundService.ACTION_UPDATE
                            putExtra(MonitoringForegroundService.EXTRA_CONTENT, content)
                        }
                        startService(updateIntent)
                        result.success(true)
                    }
                    "setEnabledSoundIds" -> {
                        val ids = call.argument<List<String>>("ids") ?: emptyList()
                        enabledSoundIds = ids
                        Log.d("MainActivity", "EnabledSoundIds updated: $enabledSoundIds")
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, eventChannelName)
            .setStreamHandler(object : EventChannel.StreamHandler {
                @RequiresPermission(Manifest.permission.RECORD_AUDIO)
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    Log.d("EventChannel", "onListen called")
                    eventSink = events
                }

                override fun onCancel(arguments: Any?) {
                    Log.d("EventChannel", "onCancel called")
                    eventSink = null
                }
            })
    }

    private fun hasAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioStream(): Boolean {
        if (isStarted) {
            Log.d("Audio", "Audio stream already started")
            return true
        }
        audioCaptioningProcessor = AudioCaptioningProcessor()
        if (!hasAudioPermission()) return false

        isStarted = true 
        Log.d("Audio", "isStarted set to TRUE")
        startAudioAnalyzer()
        return true
    }

    private fun stopAudioStream() {
        Log.d("Audio", "stopAudioStream called, setting isStarted to FALSE")
        isStarted = false
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioAnalyzer() {
        Log.d("Audio", "startAudioAnalyzer called, isStarted = $isStarted")
        coroutineScope.launch(Dispatchers.IO) {
            try {
                initializeModels()
                initializeTokenizers()

                withContext(Dispatchers.Main) {
                    Log.d("Audio", "Models initialized, starting analyzerAudio loop, isStarted = $isStarted")
                    analyzerAudio()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error initializing models", e)
            }
        }
    }

    private fun initializeTokenizers() {
        if (tokenizer == null) {
            assets.open("flutter_assets/assets/tokenizer-effb2.json").use { tokenizerInputStream ->
                tokenizer = AudioCapsTokenizer(this, tokenizerInputStream)
            }
            Log.d("Tokenizer", "AudioCaps tokenizer loaded successfully.")
        }

        if (bertTokenizer == null) {
            bertTokenizer = BertTokenizer(this)
            assets.open("flutter_assets/assets/tokenizer-sentence_transformers.json")
                .use { bertTokenizerStream ->
                    bertTokenizer?.loadTokenizer(bertTokenizerStream)
                }
            Log.d("Tokenizer", "BERT tokenizer loaded successfully.")
        }

        if (embeddingModel == null) {
            embeddingModel = SentenceTransformerEmbeddingModel(this)
            val embeddingModelFile = File(filesDir, "sentence_transformers_minilm.pte")
            if (!embeddingModelFile.exists()) {
                assets.open("flutter_assets/assets/sentence_transformers_minilm.pte").use { input ->
                    FileOutputStream(embeddingModelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            embeddingModel?.loadModel(embeddingModelFile.absolutePath)
            Log.d("Model", "Embedding model loaded successfully.")
        }

        if (categoryMatcher == null) {
            categoryMatcher = CategoryMatcher(this)
            assets.open("flutter_assets/assets/category_embeddings.json").use { categoryStream ->
                categoryMatcher?.loadCategories(categoryStream)
            }
            Log.d("Category", "Category matcher loaded successfully.")
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun analyzerAudio() {
        if (!isStarted) {
            Log.d("Audio", "analyzerAudio called but isStarted is false, stopping")
            return
        }

        Log.d("Audio", "Starting audio analysis cycle")

        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (tokenizer == null || bertTokenizer == null || embeddingModel == null || categoryMatcher == null) {
                    Log.e("Audio", "Models not initialized yet, waiting...")
                    delay(1000)
                    withContext(Dispatchers.Main) {
                        if (isStarted) analyzerAudio()
                    }
                    return@launch
                }

                val recordedWav = recordFiveSecondsWav()
                if (recordedWav == null) {
                    Log.e("Audio", "No se pudo grabar el audio, reintentando...")
                    delay(500)
                    withContext(Dispatchers.Main) {
                        if (isStarted) analyzerAudio()
                    }
                    return@launch
                }

                Log.d("Audio", "Audio recorded successfully, processing...")

                val audioData = recordedWav.inputStream().use { audioInputStream ->
                    AudioUtils.loadWavFile(audioInputStream)
                }

                val tokenIds = audioCaptioningProcessor.generateCaption(audioData)
                val caption = tokenizer!!.decode(tokenIds)

                Log.d("MainActivity", "Generated Caption: '$caption'")

                val (inputIds, attentionMask, _) = bertTokenizer!!.encode(caption)
                val embedding = embeddingModel!!.generateEmbedding(inputIds, attentionMask)

                if (embedding != null) {
                    val topMatches = categoryMatcher!!.findTopMatches(embedding, topN = 3)
                    val firstMatch = topMatches.firstOrNull()

                    // Get the category ID from the match
                    val detectedCategoryId = if (firstMatch != null && firstMatch.score >= 0.6) {
                        firstMatch.category.id
                    } else {
                        null
                    }

                    val detectedCategoryLabel = if (firstMatch != null && firstMatch.score >= 0.6) {
                        firstMatch.category.label
                    } else {
                        null
                    }

                    val bestCategoryId = if (detectedCategoryId != null && enabledSoundIds.contains(detectedCategoryId)) {
                        detectedCategoryId
                    } else {
                        "Nothing"
                    }

                    Log.d("MainActivity", "Top match score: ${firstMatch?.score}, Selected: $bestCategoryId")
                    Log.d("MainActivity", "Top matches: '$topMatches'")

                    val updateIntent = Intent(this@MainActivity, MonitoringForegroundService::class.java).apply {
                        action = MonitoringForegroundService.ACTION_UPDATE
                        putExtra(MonitoringForegroundService.EXTRA_CONTENT, detectedCategoryLabel)
                    }
                    withContext(Dispatchers.Main) {
                        startService(updateIntent)
                    }
                } else {
                    Log.e("Category", "Failed to generate embedding")
                }

                Log.d("Audio", "Analysis complete, scheduling next cycle")

                withContext(Dispatchers.Main) {
                    if (isStarted) {
                        analyzerAudio()
                    } else {
                        Log.d("Audio", "isStarted is false, stopping loop")
                    }
                }
            } catch (e: Exception) {
                Log.e("Audio", "Error in analysis cycle", e)
                e.printStackTrace()

                delay(500)
                withContext(Dispatchers.Main) {
                    if (isStarted) {
                        Log.d("Audio", "Retrying after error...")
                        analyzerAudio()
                    }
                }
            }
        }
    }

    private fun initializeModels() {
        val decoderFile = File(filesDir, "effb2_decoder_5sec.pte")
        if (!decoderFile.exists()) {
            try {
                assets.open("flutter_assets/assets/effb2_decoder_5sec.pte").use { input ->
                    FileOutputStream(decoderFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error copying decoder model", e)
            }
        }
        val encoderBytes = assets.open("flutter_assets/assets/effb2_encoder_preprocess.onnx").readBytes()
        audioCaptioningProcessor.initializeModels(encoderBytes, decoderFile.absolutePath)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun recordFiveSecondsWav(): File? {
        if (!hasAudioPermission()) return null

        val targetSampleRate = 32000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(targetSampleRate, channelConfig, audioFormat)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            targetSampleRate,
            channelConfig,
            audioFormat,
            minBuffer
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.e("Audio", "AudioRecord not initialized")
            return null
        }

        val durationMs = 5000
        val totalSamples = targetSampleRate * durationMs / 1000
        val pcmData = ByteArray(totalSamples * 2) // 16-bit mono -> 2 bytes per sample

        rec.startRecording()
        var bytesWritten = 0
        val tempBuffer = ByteArray(minBuffer)
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < durationMs) {
            val read = rec.read(tempBuffer, 0, tempBuffer.size)
            if (read > 0) {
                val remaining = pcmData.size - bytesWritten
                val toCopy = if (read > remaining) remaining else read
                if (toCopy <= 0) break
                System.arraycopy(tempBuffer, 0, pcmData, bytesWritten, toCopy)
                bytesWritten += toCopy
            }
        }
        rec.stop()
        rec.release()

        val outFile = File(filesDir, "output_audio.wav")
        FileOutputStream(outFile).use { fos ->
            writeWavHeader(
                fos,
                pcmDataSize = bytesWritten,
                sampleRate = targetSampleRate,
                channels = 1,
                bitsPerSample = 16
            )
            fos.write(pcmData, 0, bytesWritten)
        }

        return outFile
    }

    private fun writeWavHeader(
        outputStream: FileOutputStream,
        pcmDataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmDataSize + 36

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalDataLen)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmDataSize)

        outputStream.write(header, 0, 44)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    object AudioUtils {
        fun loadWavFile(inputStream: InputStream): FloatArray {
            val bytes = inputStream.readBytes()
            return processWavBytes(bytes)
        }

        private fun processWavBytes(bytes: ByteArray): FloatArray {
            val headerSize = 44
            if (bytes.size <= headerSize) return FloatArray(0)

            val shortBuffer = ByteBuffer.wrap(bytes, headerSize, bytes.size - headerSize)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer()

            val shortArray = ShortArray(shortBuffer.remaining())
            shortBuffer.get(shortArray)

            return FloatArray(shortArray.size) { i ->
                shortArray[i] / 32768.0f
            }
        }
    }

}