package com.example.deaf_less

import AudioCapsTokenizer
import android.content.Intent
import android.media.AudioRecord
import android.media.MediaRecorder
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.log10
import kotlin.math.sqrt
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : FlutterActivity() {

	private lateinit var audioCaptioningProcessor: AudioCaptioningProcessor

	private val methodChannelName = "sound_guardian/audio"
	private val eventChannelName = "sound_guardian/audioStream"

	private var isStarted = false
	private var eventSink: EventChannel.EventSink? = null

	private var audioRecord: AudioRecord? = null
	private var recordingThread: Thread? = null

	private val sampleRate = 44100
	private val bufferSize = AudioRecord.getMinBufferSize(
		sampleRate,
		AudioFormat.CHANNEL_IN_MONO,
		AudioFormat.ENCODING_PCM_16BIT
	)

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
								putExtra(MonitoringForegroundService.EXTRA_CONTENT, "Escuchando...")
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
						val content = call.argument<String>("content") ?: "Escuchando..."
						val updateIntent = Intent(this, MonitoringForegroundService::class.java).apply {
							action = MonitoringForegroundService.ACTION_UPDATE
							putExtra(MonitoringForegroundService.EXTRA_CONTENT, content)
						}
						startService(updateIntent)
						result.success(true)
					}
					else -> result.notImplemented()
				}
			}

		EventChannel(flutterEngine.dartExecutor.binaryMessenger, eventChannelName)
			.setStreamHandler(object : EventChannel.StreamHandler {
				@RequiresPermission(Manifest.permission.RECORD_AUDIO)
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
					eventSink = events
					if (isStarted) startAudioStream()
				}

				override fun onCancel(arguments: Any?) {
					eventSink = null
					stopAudioStream()
				}
			})
	}

	private fun hasAudioPermission(): Boolean {
		return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
	}

	@RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioStream(): Boolean {
		if (isStarted) return true
		audioCaptioningProcessor = AudioCaptioningProcessor()
		audioAnalyzer()
		if (!hasAudioPermission()) return false
		isStarted = true
		stopAudioStream()
		audioRecord = AudioRecord(
			MediaRecorder.AudioSource.MIC,
			sampleRate,
			AudioFormat.CHANNEL_IN_MONO,
			AudioFormat.ENCODING_PCM_16BIT,
			bufferSize
		)
		audioRecord?.startRecording()
		recordingThread = Thread {
			val buffer = ShortArray(bufferSize)
			while (isStarted && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
				val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
				if (read > 0) {
					var sum = 0.0
					for (i in 0 until read) {
						val v = buffer[i].toDouble()
						sum += v * v
					}
					val rms = sqrt(sum / read)
					val db = if (rms > 0) 20 * log10(rms / 32767.0) + 90 else 0.0
					eventSink?.success(db)
				}
				try {
					Thread.sleep(500)
				} catch (_: InterruptedException) { }
			}
		}
		recordingThread?.start()
		return true
	}

	private fun stopAudioStream() {
		isStarted = false
		audioRecord?.stop()
		audioRecord?.release()
		audioRecord = null
		recordingThread?.interrupt()
		recordingThread = null
	}

	private fun audioAnalyzer(){
		// Copy ExecuTorch Decoder to filesDir
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

		//AUDIO QUE RECIBE DE FLUTTER ( 5 sec ) .. formato wav .. 32khz .. mono stereo
		val audioInputStream = assets.open("flutter_assets/assets/dog.wav")
		val audioData = AudioUtils.loadWavFile(audioInputStream)

		val tokenizerInputStream = assets.open("flutter_assets/assets/tokenizer-effb2.json")
		val tokenizer = AudioCapsTokenizer(this, tokenizerInputStream)

		Thread {
			try {
				val tokenIds = audioCaptioningProcessor.generateCaption(audioData)
				val caption = tokenizer.decode(tokenIds)
				try {
					val bertTokenizer = BertTokenizer(this)
					val embeddingModel = SentenceTransformerEmbeddingModel(this)
					val categoryMatcher = CategoryMatcher(this)
					
					val bertTokenizerStream = assets.open("flutter_assets/assets/tokenizer-sentence_transformers.json")
					bertTokenizer.loadTokenizer(bertTokenizerStream)

					val embeddingModelFile = File(filesDir, "sentence_transformers_minilm.pte")
					if (!embeddingModelFile.exists()) {
						assets.open("flutter_assets/assets/sentence_transformers_minilm.pte").use { input ->
							FileOutputStream(embeddingModelFile).use { output ->
								input.copyTo(output)
							}
						}
					}
					embeddingModel.loadModel(embeddingModelFile.absolutePath)

					val categoryStream = assets.open("flutter_assets/assets/category_embeddings.json")
					categoryMatcher.loadCategories(categoryStream)

					Log.d("MainActivity", "Generated Caption: '$caption'")

					val (inputIds, attentionMask, _) = bertTokenizer.encode(caption)
					val embedding = embeddingModel.generateEmbedding(inputIds, attentionMask)
					
					if (embedding != null) {
						val topMatches = categoryMatcher.findTopMatches(embedding, topN = 3)
						Log.d("Caption", "'$caption'")
						topMatches.forEachIndexed { index, match ->
							Log.d("Category", "  ${index + 1}. ${match.category.id} (${String.format("%.4f", match.score)}) - ${match.category.label}")
						}
						Log.d("Category", "Best match: ${topMatches.firstOrNull()?.category?.id ?: "none"}")
						topMatches.forEachIndexed { index, match ->
							println("  ${index + 1}. ${match.category.id} (${String.format("%.4f", match.score)}) - ${match.category.label}")
						}
						//RETORNAR A FLUTTER COMO ALERTA
						println("Best match: ${topMatches.firstOrNull()?.category?.id ?: "none"}")
					} else {
						Log.e("Category", "Failed to generate embedding")
					}
				} catch (e: Exception) {
					Log.e("Category", "Categorization error", e)
					e.printStackTrace()
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}.start()
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
