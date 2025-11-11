import 'package:equatable/equatable.dart';

class SoundEvent extends Equatable {
  const SoundEvent({
    required this.id,
    required this.timestamp,
    required this.levelDb,
    this.type,
    this.source,
  });

  final String id;
  final DateTime timestamp;
  final double levelDb;
  final String? type;
  final String? source;

  @override
  List<Object?> get props => [id, timestamp, levelDb, type, source];
}
