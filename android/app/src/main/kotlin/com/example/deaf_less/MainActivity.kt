package com.example.deaf_less

import AudioCapsTokenizer
import AudioModel
import android.os.Handler
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

	//private lateinit var audioModel: AudioModel
	//private lateinit var tokenizer: AudioCapsTokenizer

	private val methodChannelName = "sound_guardian/audio"
	private val eventChannelName = "sound_guardian/audioStream"

	private var isStarted = false
	private var handler: Handler? = null
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
/*		audioModel = AudioModel(this)
		val inputStream = assets.open("flutter_assets/assets/tokenizer.json")
		tokenizer = AudioCapsTokenizer(this, inputStream)
		val destFile = File(context.filesDir, "modelo.pte")
		assets.open("flutter_assets/assets/modelo.pte").use { input ->
			FileOutputStream(destFile).use { output ->
				input.copyTo(output)
			}
		}
		audioModel.loadModel(destFile.absolutePath)
        scanAudio()*/

		newScan()

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

	/*private fun scanAudio() {
		Thread {
			try {
				val inputStream = assets.open("flutter_assets/assets/sample_audio.wav")
				val rawBytes = inputStream.readBytes().also { inputStream.close() }
				val pcmBytes = if (rawBytes.size > 44) rawBytes.copyOfRange(44, rawBytes.size) else rawBytes
				val tokenIds = audioModel.predict(pcmBytes)
				if (tokenIds != null) {
					val caption = tokenizer.decode(tokenIds)
					runOnUiThread {
						Log.d("Result", "IDs: ${tokenIds.joinToString()}")
						Log.d("Result", "Caption: $caption")
					}
				} else {
					Log.e("Result", "tokenIds nulos al procesar sample_audio.wav")
				}
			} catch (e: Exception) {
				Log.e("Result", "Error leyendo asset sample_audio.wav: ${e.message}")
			}
		}.start()
	}*/

	private fun newScan(){
		val encoderBytes = assets.open("flutter_assets/assets/effb2_encoder_preprocess.onnx").readBytes()
		val decoderBytes = assets.open("flutter_assets/assets/effb2_decoder_step.onnx").readBytes()
		audioCaptioningProcessor.initializeModels(encoderBytes, decoderBytes)

		//val audioFloatArray = FloatArray(16000 * 3) { 0.0f }
		val inputStream = assets.open("flutter_assets/assets/doorbell.wav")
		val audioData = AudioUtils.loadWavFile(inputStream)

		Thread {
			try {
				val tokenIds = audioCaptioningProcessor.generateCaption(audioData)
				println("Generated Token IDs: $tokenIds")
				// 4. Convert IDs to Text
				// You will need a simple helper to map these IDs back to words
				// using your tokenizer.json
				// val sentence = Tokenizer.decode(tokenIds)
			} catch (e: Exception) {
				e.printStackTrace()
			}
		}.start()
	}

	object AudioUtils {
		// Tu método existente
		fun loadWavFile(file: File): FloatArray {
			val bytes = file.readBytes()
			return processWavBytes(bytes)
		}

		// NUEVO: Método para InputStream (para assets)
		fun loadWavFile(inputStream: InputStream): FloatArray {
			val bytes = inputStream.readBytes()
			return processWavBytes(bytes)
		}

		// Lógica compartida
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
