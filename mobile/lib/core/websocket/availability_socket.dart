import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:web_socket_channel/web_socket_channel.dart';
import '../api/endpoints.dart';
import '../../features/map/domain/spot_model.dart';

/// Emits [SpotStateEvent] for every `spot.state.changed` message received
/// from the availability-service WebSocket.
///
/// Reconnects automatically after 3 seconds on any connection error or
/// clean close. Disposed when the last listener unsubscribes.
final availabilitySocketProvider =
    StreamProvider.autoDispose<SpotStateEvent>((ref) async* {
  while (true) {
    WebSocketChannel? channel;
    try {
      channel = WebSocketChannel.connect(Uri.parse(kWsUrl));
      await channel.ready;

      await for (final message in channel.stream) {
        if (message is! String) continue;
        final json = jsonDecode(message) as Map<String, dynamic>;
        if (json['eventType'] == 'spot.state.changed') {
          yield SpotStateEvent.fromJson(
              json['payload'] as Map<String, dynamic>);
        }
      }
    } catch (_) {
      // swallow — reconnect below
    } finally {
      await channel?.sink.close();
    }
    // Brief pause before reconnecting to avoid tight-loop on persistent errors
    await Future<void>.delayed(const Duration(seconds: 3));
  }
});
