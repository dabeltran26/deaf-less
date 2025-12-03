import 'dart:async';
import 'dart:io';

import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:hive/hive.dart';
import '../../../../core/platform/audio/audio_channel.dart';
import '../../../monitoring/domain/entities/monitoring_status.dart';
import '../../../../core/preferences/sound_preferences.dart';

class MonitoringState {
  const MonitoringState(this.status, {this.isSupported = true});
  final MonitoringStatus status;
  final bool isSupported;
}

class MonitoringCubit extends Cubit<MonitoringState> {
  MonitoringCubit()
    : super(
        const MonitoringState(
          MonitoringStatus(isActive: false, permissionGranted: true),
          isSupported: true,
        ),
      );

  List<String> enabledSoundIds = SoundPreferencesRepository(
    Hive.box('preferences'),
  ).enabledSoundIds;

  StreamSubscription<double>? _sub;
  DateTime? _lastNotifAt;

  Future<void> start() async {
    if (state.status.isActive) return;
    await AudioChannel.start();
    _sub?.cancel();
    _sub = AudioChannel.decibelStream().listen((db) {
      emit(
        MonitoringState(state.status.copyWith(isActive: true, lastEventDb: db)),
      );
      final now = DateTime.now();
      if (_lastNotifAt == null ||
          now.difference(_lastNotifAt!) > const Duration(seconds: 2)) {
        _lastNotifAt = now;
        AudioChannel.updateNotification(
          'Aca se alerta : ${db.toStringAsFixed(1)} dB',
        );
      }
    });
    await AudioChannel.updateNotification('No hay novedades :D');
  }

  Future<void> stop() async {
    await AudioChannel.stop();
    await _sub?.cancel();
    _lastNotifAt = null;
    emit(
      MonitoringState(
        state.status.copyWith(isActive: false),
        isSupported: state.isSupported,
      ),
    );
  }

  @override
  Future<void> close() {
    _sub?.cancel();
    return super.close();
  }

  Future<void> checkDeviceArchitecture() async {
    if (Platform.isAndroid) {
      final deviceInfo = DeviceInfoPlugin();
      final androidInfo = await deviceInfo.androidInfo;
      List<String> supportedAbis = androidInfo.supportedAbis;
      bool isArm = supportedAbis.any(
        (abi) => abi.contains('arm') || abi.contains('ARM'),
      );
      emit(MonitoringState(state.status, isSupported: isArm));
    } else {
      emit(MonitoringState(state.status, isSupported: false));
    }
  }

  Future<void> sendEnabledSoundIds(List<String> ids) async {
    // Send enabled sound IDs to Android before starting monitoring
    try {
      await AudioChannel.setEnabledSoundIds(enabledSoundIds);
    } catch (_) {}
  }
}
