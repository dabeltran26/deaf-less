package com.example.deaf_less

import android.os.Handler
import android.os.Looper
import android.content.Intent
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioFormat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlin.random.Random

class MainActivity : FlutterActivity() {
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

	private fun startAudioStream(): Boolean {
		if (isStarted) return true
		if (!hasAudioPermission()) return false
		isStarted = true
		stopMockStream()
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
					val rms = kotlin.math.sqrt(sum / read)
					val db = if (rms > 0) 20 * kotlin.math.log10(rms / 32767.0) + 90 else 0.0
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

	private fun startMockStream() {
		if (isStarted) return
		isStarted = true
		if (handler == null) handler = Handler(Looper.getMainLooper())
		handler?.post(object : Runnable {
			override fun run() {
				if (!isStarted) return
				// Emit a random dB value between 30 and 80
				val db = 30 + Random.nextDouble() * 50
				eventSink?.success(db)
				handler?.postDelayed(this, 800)
			}
		})
	}

	private fun stopMockStream() {
		isStarted = false
		handler?.removeCallbacksAndMessages(null)
	}
}
