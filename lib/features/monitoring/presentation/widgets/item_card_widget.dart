import 'package:flutter/material.dart';

class ItemCardWidget extends StatelessWidget {
  const ItemCardWidget({
    super.key,
    required this.leading,
    required this.title,
    required this.subtitle,
  });

  final Widget leading;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: leading,
        title: Text(title, style: Theme.of(context).textTheme.titleLarge),
        subtitle: Text(subtitle),
      ),
    );
  }
}
