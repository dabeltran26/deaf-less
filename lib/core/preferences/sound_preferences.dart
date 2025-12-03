import 'package:flutter/material.dart';
import 'package:hive/hive.dart';

/// Keys stored in Hive box 'preferences'.
class PrefKeys {
  static const soundsEnabled = 'sounds_enabled'; // List<String> of sound ids
  static const recordingActive = 'recording_active'; // bool
}

/// Enumeration of detectable sounds.
/// The id is used for persistence.
class DetectableSound {
  const DetectableSound({
    required this.id,
    required this.label,
    required this.icon,
    this.available = true,
  });
  final bool available;
  final String id;
  final String label;
  final IconData icon;
}

/// Central registry of sounds the app can detect.
class SoundCatalog {
  static final List<DetectableSound> all = [
    DetectableSound(
      id: 'dog_bark',
      label: 'Bark of a dog',
      icon: Icons.pets_outlined,
    ),
    DetectableSound(
      id: 'doorbell',
      label: 'Doorbell ringing',
      icon: Icons.doorbell_outlined,
    ),
    DetectableSound(
      id: 'baby_crying',
      label: 'Baby crying',
      icon: Icons.child_care_outlined,
    ),
    DetectableSound(
      id: 'glass_breaking',
      label: 'Glass breaking',
      icon: Icons.wine_bar_outlined,
    ),
    DetectableSound(
      id: 'car_horn',
      label: 'Car horn',
      icon: Icons.car_rental_outlined,
    ),
    DetectableSound(
      id: 'alarm_clock',
      label: 'Alarm clock',
      icon: Icons.alarm_outlined,
    ),
    DetectableSound(
      id: 'fire_alarm',
      label: 'Fire alarm',
      icon: Icons.local_fire_department_outlined,
    ),
    DetectableSound(
      id: 'door_closing',
      label: 'Door or window closing',
      icon: Icons.meeting_room_outlined,
    ),
    DetectableSound(
      id: 'door_opening',
      label: 'Door or window opening',
      icon: Icons.meeting_room_outlined,
    ),
    DetectableSound(
      id: 'stagger_swipe',
      label: 'Stagger or swipe',
      icon: Icons.warning_amber_outlined,
    ),
  ];
}

/// Repository wrapping Hive persistence for sound preferences.
class SoundPreferencesRepository {
  SoundPreferencesRepository(this._box);
  final Box _box;

  List<String> get enabledSoundIds {
    final List<dynamic>? raw =
        _box.get(PrefKeys.soundsEnabled) as List<dynamic>?;
    return raw?.cast<String>() ?? <String>[];
  }

  bool isEnabled(String soundId) => enabledSoundIds.contains(soundId);

  Future<void> toggle(String soundId, bool enabled) async {
    final current = enabledSoundIds;
    if (enabled) {
      if (!current.contains(soundId)) current.add(soundId);
    } else {
      current.remove(soundId);
    }
    await _box.put(PrefKeys.soundsEnabled, current);
  }

  Future<void> removeSound(String soundId) async {
    final current = enabledSoundIds;
    current.remove(soundId);
    await _box.put(PrefKeys.soundsEnabled, current);
  }
}
