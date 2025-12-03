import 'package:deaf_less/features/settings/presentation/widgets/sount_tile_widget.dart';
import 'package:flutter/material.dart';
import 'package:hive_flutter/hive_flutter.dart';
import '../../../../core/preferences/sound_preferences.dart';
import '../../../../core/constants/app_strings.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late final SoundPreferencesRepository repo;

  @override
  void initState() {
    super.initState();
    repo = SoundPreferencesRepository(Hive.box('preferences'));
  }

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text(AppStrings.settingsTitle)),
      body: ValueListenableBuilder(
        valueListenable: Hive.box(
          'preferences',
        ).listenable(keys: [PrefKeys.soundsEnabled]),
        builder: (context, box, _) {
          final enabled = repo.enabledSoundIds;
          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
            children: [
              Container(
                decoration: BoxDecoration(
                  color: color.primary.withOpacity(0.05),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: color.primary.withOpacity(0.15)),
                ),
                padding: const EdgeInsets.all(16),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.info_outline, color: color.primary),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        AppStrings.settingsInfoMessage,
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              Text(
                AppStrings.detectableSoundsHeader,
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 12),
              for (final sound in SoundCatalog.all)
                SoundTileWidget(
                  sound: sound,
                  enabled: enabled.contains(sound.id),
                  onChanged: (v) async {
                    await repo.toggle(sound.id, v);
                  },
                  onDelete: () async {
                    await repo.removeSound(sound.id);
                  },
                ),
            ],
          );
        },
      ),
    );
  }
}
