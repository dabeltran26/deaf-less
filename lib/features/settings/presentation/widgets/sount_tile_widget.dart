import 'package:deaf_less/core/preferences/sound_preferences.dart';
import 'package:flutter/material.dart';

class SoundTileWidget extends StatelessWidget {
  const SoundTileWidget({
    super.key,
    required this.sound,
    required this.enabled,
    required this.onChanged,
    required this.onDelete,
  });

  final DetectableSound sound;
  final bool enabled;
  final ValueChanged<bool> onChanged;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme;
    final disabled = !sound.available;
    return Card(
      child: ListTile(
        leading: Icon(
          sound.icon,
          color: enabled && !disabled ? color.primary : color.onSurfaceVariant,
        ),
        title: Text(
          sound.label,
          style: disabled
              ? Theme.of(context).textTheme.titleMedium?.copyWith(
                  // ignore: deprecated_member_use
                  color: color.onSurface.withOpacity(0.5),
                )
              : null,
        ),
        trailing: Switch(
          value: enabled,
          onChanged: disabled ? null : onChanged,
        ),
      ),
    );
  }
}
