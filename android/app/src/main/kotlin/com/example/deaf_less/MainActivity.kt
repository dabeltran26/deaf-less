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
								putExtra(MonitoringForegroundService.EXTRA_CONTENT, "No a pasado nada :D")
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
		if (!hasAudioPermission()) return false
		audioAnalyzer()
		isStarted = true
		return true
	}

	private fun stopAudioStream() {
		isStarted = false
	}

	@RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun audioAnalyzer(){

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
		//val audioInputStream = assets.open("flutter_assets/assets/dog.wav")
		val recordedWav = recordFiveSecondsWav()
		if (recordedWav == null) {
			Log.e("Audio", "No se pudo grabar el audio")
			return
		}
		val audioInputStream = recordedWav.inputStream()
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
						println("Best match: ${topMatches.firstOrNull()?.category?.id ?: "none"}")

						val bestCategoryId = topMatches.firstOrNull()?.category?.id ?: "none"
						val updateIntent = Intent(this, MonitoringForegroundService::class.java).apply {
							action = MonitoringForegroundService.ACTION_UPDATE
							putExtra(MonitoringForegroundService.EXTRA_CONTENT, bestCategoryId)
						}
						startService(updateIntent)

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
			Log.e("Audio", "AudioRecord no inicializado")
			return null
		}

		val durationMs = 5000
		val totalSamples = targetSampleRate * durationMs / 1000
		val pcmData = ByteArray(totalSamples * 2) // 16-bit mono -> 2 bytes por muestra

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
		bitsPerSample: Int) {
		val byteRate = sampleRate * channels * bitsPerSample / 8
		val blockAlign = channels * bitsPerSample / 8
		val totalDataLen = pcmDataSize + 36

		val header = ByteArray(44)
		val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

		// ChunkID "RIFF"
		buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
		// ChunkSize
		buffer.putInt(totalDataLen)
		// Format "WAVE"
		buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
		// Subchunk1ID "fmt "
		buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
		// Subchunk1Size 16 for PCM
		buffer.putInt(16)
		// AudioFormat 1 for PCM
		buffer.putShort(1)
		// NumChannels
		buffer.putShort(channels.toShort())
		// SampleRate
		buffer.putInt(sampleRate)
		// ByteRate
		buffer.putInt(byteRate)
		// BlockAlign
		buffer.putShort(blockAlign.toShort())
		// BitsPerSample
		buffer.putShort(bitsPerSample.toShort())
		// Subchunk2ID "data"
		buffer.put("data".toByteArray(Charsets.US_ASCII))
		// Subchunk2Size
		buffer.putInt(pcmDataSize)

		outputStream.write(header, 0, 44)
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
