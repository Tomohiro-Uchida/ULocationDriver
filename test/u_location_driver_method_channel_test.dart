import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:u_location_driver/u_location_driver_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelULocationDriver _ = MethodChannelULocationDriver();
  const MethodChannel channel = MethodChannel('u_location_driver');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

}
