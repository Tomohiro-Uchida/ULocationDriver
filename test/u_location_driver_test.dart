import 'dart:collection';

import 'package:flutter_test/flutter_test.dart';
import 'package:u_location_driver/u_location_driver_platform_interface.dart';
import 'package:u_location_driver/u_location_driver_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockULocationDriverPlatform
    with MockPlatformInterfaceMixin
    implements ULocationDriverPlatform {

  @override
  Future<String?> activateBackground() {
    // TODO: implement activateBackground
    throw UnimplementedError();
  }

  @override
  Future<String?> inactivate() {
    throw UnimplementedError();
  }

  @override
  Future<String?> activateForeground({HashMap<String, dynamic>? arguments}) {
    // TODO: implement activateForeground
    throw UnimplementedError();
  }

  @override
  Future<String?> registerBackgroundIsolate(HashMap<String, dynamic>? arguments) {
    // TODO: implement registerBackgroundIsolate
    throw UnimplementedError();
  }

  @override
  Future<String?> startBackgroundIsolate() {
    // TODO: implement startBackgroundIsolate
    throw UnimplementedError();
  }

}

void main() {
  final ULocationDriverPlatform initialPlatform = ULocationDriverPlatform.instance;

  test('$MethodChannelULocationDriver is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelULocationDriver>());
  });

}
