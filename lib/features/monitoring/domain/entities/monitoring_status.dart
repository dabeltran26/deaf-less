class MonitoringStatus {
  const MonitoringStatus({
    required this.isActive,
    required this.permissionGranted,
    this.lastEventDb,
  });

  final bool isActive;
  final bool permissionGranted;
  final double? lastEventDb;

  MonitoringStatus copyWith({
    bool? isActive,
    bool? permissionGranted,
    double? lastEventDb,
  }) => MonitoringStatus(
    isActive: isActive ?? this.isActive,
    permissionGranted: permissionGranted ?? this.permissionGranted,
    lastEventDb: lastEventDb ?? this.lastEventDb,
  );
}
