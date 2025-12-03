/// Application-wide string constants for UI labels and messages.
/// All strings are in English.
class AppStrings {
  AppStrings._();

  // App Branding
  static const String appTitle = 'Deaf Less';

  // Home Page
  static const String settingsTooltip = 'Settings';
  static const String homeHeroMessage = 'Find out what\'s happening around you';
  static const String homeDescription =
      'Your hearing safety assistant that constantly monitors ambient sounds and alerts you when it detects important noises';

  // Home Page Features
  static const String feature247Title = '24/7 Monitoring';
  static const String feature247Subtitle =
      'Active listening in the background without interrupting your activities';

  static const String featureAlertsTitle = 'Smart Alerts';
  static const String featureAlertsSubtitle =
      'Instant notifications when we detect configured sounds';

  static const String featureCustomizableTitle = '100% Customizable';
  static const String featureCustomizableSubtitle =
      'Configure exactly which sounds you want to detect';

  // Home Page Buttons
  static const String startListeningButton = 'Start listening';
  static const String stopListeningButton = 'Stop listening';

  // Permission Messages
  static const String micPermissionRequired =
      'We need microphone permission to start listening.';
  static const String micPermissionDenied =
      'Microphone permission denied. Enable it in Settings to continue.';

  // Settings Page
  static const String settingsTitle = 'Settings';
  static const String settingsInfoMessage =
      'Activate only the sounds you consider important. You will receive notifications when they are detected.';
  static const String detectableSoundsHeader = 'Detectable Sounds';
}
