import Flutter
import UIKit

class AudioStreamHandler: NSObject, FlutterStreamHandler {
  private var timer: Timer? = nil
  private var isStarted = false
  private var sink: FlutterEventSink? = nil

  func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
    sink = events
    if isStarted { startTimer() }
    return nil
  }

  func onCancel(withArguments arguments: Any?) -> FlutterError? {
    sink = nil
    stopTimer()
    return nil
  }

  func setStarted(_ value: Bool) {
    isStarted = value
    if value { startTimer() } else { stopTimer() }
  }

  private func startTimer() {
    guard isStarted, timer == nil, sink != nil else { return }
    timer = Timer.scheduledTimer(withTimeInterval: 0.8, repeats: true) { [weak self] _ in
      guard let self = self, let sink = self.sink else { return }
      let db = 30 + Double.random(in: 0...50)
      sink(db)
    }
  }

  private func stopTimer() {
    timer?.invalidate()
    timer = nil
  }
}

@main
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)

    let controller : FlutterViewController = window?.rootViewController as! FlutterViewController
    let methodChannel = FlutterMethodChannel(name: "sound_guardian/audio", binaryMessenger: controller.binaryMessenger)
    let eventChannel = FlutterEventChannel(name: "sound_guardian/audioStream", binaryMessenger: controller.binaryMessenger)
    let streamHandler = AudioStreamHandler()
    eventChannel.setStreamHandler(streamHandler)

    methodChannel.setMethodCallHandler { (call: FlutterMethodCall, result: FlutterResult) in
      switch call.method {
      case "startMonitoring":
        streamHandler.setStarted(true)
        result(true)
      case "stopMonitoring":
        streamHandler.setStarted(false)
        result(true)
      default:
        result(FlutterMethodNotImplemented)
      }
    }

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
