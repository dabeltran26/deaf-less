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
      id: 'fire_alarm',
      label: 'Alarma de incendio',
      icon: Icons.campaign_outlined,
    ),
    DetectableSound(
      id: 'baby_cry',
      label: 'Llanto de bebé',
      icon: Icons.child_care_outlined,
    ),
    DetectableSound(
      id: 'glass_break',
      label: 'Rotura de vidrio',
      icon: Icons.wine_bar_outlined,
    ),
    DetectableSound(
      id: 'dog_bark',
      label: 'Ladrido de perro',
      icon: Icons.pets_outlined,
    ),
    DetectableSound(
      id: 'loud_bang',
      label: 'Golpe fuerte',
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
