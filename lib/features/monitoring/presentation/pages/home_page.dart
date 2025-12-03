import 'package:deaf_less/features/monitoring/presentation/cubit/monitoring_cubit.dart';
import 'package:deaf_less/features/monitoring/presentation/widgets/item_card_widget.dart';
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:go_router/go_router.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:deaf_less/core/preferences/sound_preferences.dart';
import 'package:deaf_less/core/constants/app_strings.dart';
import 'package:permission_handler/permission_handler.dart';
import 'dart:io' show Platform;

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme;
    return BlocProvider(
      create: (_) => MonitoringCubit(),
      child: Scaffold(
        appBar: AppBar(
          title: const Text(AppStrings.appTitle),
          actions: [
            IconButton(
              icon: const Icon(Icons.settings_outlined),
              onPressed: () => context.push('/settings'),
              tooltip: AppStrings.settingsTooltip,
            ),
          ],
        ),
        body: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Container(
                    width: 60,
                    height: 60,
                    decoration: const BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: LinearGradient(
                        colors: [Color(0xFF7B3AED), Color(0xFFFF5E9C)],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                    ),
                    child: const Center(
                      child: Icon(
                        Icons.mic_none_rounded,
                        size: 40,
                        color: Colors.white,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      AppStrings.homeHeroMessage,
                      style: Theme.of(context).textTheme.titleLarge,
                      textAlign: TextAlign.left,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 30),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: Text(
                    AppStrings.homeDescription,
                    style: Theme.of(context).textTheme.bodyLarge,
                    textAlign: TextAlign.center,
                  ),
                ),
              ),
              const SizedBox(height: 12),
              ItemCardWidget(
                leading: Icon(Icons.shield_outlined, color: color.primary),
                title: AppStrings.feature247Title,
                subtitle: AppStrings.feature247Subtitle,
              ),
              const SizedBox(height: 12),
              ItemCardWidget(
                leading: Icon(
                  Icons.notifications_none_rounded,
                  color: color.primary,
                ),
                title: AppStrings.featureAlertsTitle,
                subtitle: AppStrings.featureAlertsSubtitle,
              ),
              const SizedBox(height: 12),
              ItemCardWidget(
                leading: Icon(Icons.volume_up_outlined, color: color.primary),
                title: AppStrings.featureCustomizableTitle,
                subtitle: AppStrings.featureCustomizableSubtitle,
              ),
            ],
          ),
        ),
        bottomNavigationBar: SafeArea(
          minimum: const EdgeInsets.fromLTRB(20, 8, 20, 16),
          child: ValueListenableBuilder(
            valueListenable: Hive.box(
              'preferences',
            ).listenable(keys: [PrefKeys.recordingActive]),
            builder: (context, Box box, _) {
              final bool isRecording =
                  (box.get(PrefKeys.recordingActive) as bool?) ?? false;
              return SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  icon: Icon(
                    size: 40,
                    isRecording
                        ? Icons.stop_circle_outlined
                        : Icons.mic_none_rounded,
                  ),
                  label: Text(
                    style: Theme.of(context).textTheme.titleLarge,
                    isRecording
                        ? AppStrings.stopListeningButton
                        : AppStrings.startListeningButton,
                  ),
                  onPressed: () async {
                    final cubit = context.read<MonitoringCubit>();
                    await cubit.start();
                    await box.put(PrefKeys.recordingActive, true);
                    if (!isRecording) {
                      // Solicitar directamente el permiso de micrófono
                      final micStatus = await Permission.microphone.request();

                      if (micStatus.isGranted) {
                        // Solicitar permiso de notificaciones solo en Android (para alertas) - no bloquea el inicio
                        if (Platform.isAndroid) {
                          await Permission.notification.request();
                        }
                        await cubit.start();
                        await box.put(PrefKeys.recordingActive, true);
                      } else if (micStatus.isDenied) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text(AppStrings.micPermissionRequired),
                          ),
                        );
                      } else if (micStatus.isPermanentlyDenied ||
                          micStatus.isRestricted ||
                          micStatus.isLimited) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text(AppStrings.micPermissionDenied),
                          ),
                        );
                        openAppSettings();
                      }
                    } else {
                      await cubit.stop();
                      await box.put(PrefKeys.recordingActive, false);
                    }
                  },
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 18),
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}
