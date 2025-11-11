package com.example.deaf_less

import android.os.Handler
import android.os.Looper
import android.content.Intent
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

	override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
		super.configureFlutterEngine(flutterEngine)

		MethodChannel(flutterEngine.dartExecutor.binaryMessenger, methodChannelName)
			.setMethodCallHandler { call: MethodCall, result: MethodChannel.Result ->
				when (call.method) {
					"startMonitoring" -> {
						startMockStream()
						// Start foreground service with persistent notification
						val startIntent = Intent(this, MonitoringForegroundService::class.java).apply {
							action = MonitoringForegroundService.ACTION_START
							putExtra(MonitoringForegroundService.EXTRA_CONTENT, "Escuchando...")
						}
						ContextCompat.startForegroundService(this, startIntent)
						result.success(true)
					}
					"stopMonitoring" -> {
						stopMockStream()
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
					if (isStarted) startMockStream()
				}

				override fun onCancel(arguments: Any?) {
					eventSink = null
					stopMockStream()
				}
			})
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
