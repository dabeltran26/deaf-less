import 'dart:async';
import 'package:flutter/services.dart';

class AudioChannel {
  static const MethodChannel _method = MethodChannel('sound_guardian/audio');
  static const EventChannel _events = EventChannel(
    'sound_guardian/audioStream',
  );

  static Stream<double> decibelStream() {
    return _events.receiveBroadcastStream().map((event) {
      if (event is double) return event;
      if (event is int) return event.toDouble();
      return 0.0;
    });
  }

  static Future<void> start() async {
    await _method.invokeMethod('startMonitoring');
  }

  static Future<void> stop() async {
    await _method.invokeMethod('stopMonitoring');
  }
}
