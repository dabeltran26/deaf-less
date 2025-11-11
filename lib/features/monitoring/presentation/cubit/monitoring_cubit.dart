import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';
import '../../../../core/platform/audio/audio_channel.dart';
import '../../../monitoring/domain/entities/monitoring_status.dart';

class MonitoringState {
  const MonitoringState(this.status);
  final MonitoringStatus status;
}

class MonitoringCubit extends Cubit<MonitoringState> {
  MonitoringCubit()
    : super(
        const MonitoringState(
          MonitoringStatus(isActive: false, permissionGranted: true),
        ),
      );

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
          'Nivel actual: ${db.toStringAsFixed(1)} dB',
        );
      }
    });
    // Ensure initial notification
    await AudioChannel.updateNotification('Escuchando...');
  }

  Future<void> stop() async {
    await AudioChannel.stop();
    await _sub?.cancel();
    _lastNotifAt = null;
    // Optionally inform stopped state
    await AudioChannel.updateNotification('Monitoreo detenido');
    emit(MonitoringState(state.status.copyWith(isActive: false)));
  }

  @override
  Future<void> close() {
    _sub?.cancel();
    return super.close();
  }
}
