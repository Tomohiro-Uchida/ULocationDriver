import 'package:flutter_test/flutter_test.dart';
import 'package:u_location_driver/u_location_driver_platform_interface.dart';
import 'package:u_location_driver/u_location_driver_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockULocationDriverPlatform
    with MockPlatformInterfaceMixin
    implements ULocationDriverPlatform {

  @override
  Future<String?> activate() {
    // TODO: implement activate
    throw UnimplementedError();
  }

  @override
  Future<String?> inactivate() {
    throw UnimplementedError();
  }

}

void main() {
  final ULocationDriverPlatform initialPlatform = ULocationDriverPlatform.instance;

  test('$MethodChannelULocationDriver is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelULocationDriver>());
  });

}
