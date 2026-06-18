import 'package:flutter/material.dart';

/// Temporary scaffold used for routes not yet implemented.
/// Replaced screen-by-screen as each Phase is built.
class PlaceholderScreen extends StatelessWidget {
  const PlaceholderScreen({super.key, required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Center(
        child: Text(
          '$title — coming soon',
          style: Theme.of(context).textTheme.titleMedium,
        ),
      ),
    );
  }
}
