import 'dart:ui';

import 'package:flutter/services.dart';

import 'u_location_driver_platform_interface.dart';

/// An implementation of [ULocationDriverPlatform] that uses method channels.
class MethodChannelULocationDriver extends ULocationDriverPlatform {
  /// The method channel used to interact with the native platform.
  final methodChannel = const MethodChannel("com.jimdo.uchida001tmhr.u_location_driver/fromDart");

  @override
  Future<String?> activateForeground() async {
    final result = await methodChannel.invokeMethod<String>("activateForeground");
    return result;
  }

  @override
  Future<String?> activateBackground() async {
    final result = await methodChannel.invokeMethod<String>("activateBackground");
    return result;
  }

  @override
  Future<String?> inactivate() async {
    final result = await methodChannel.invokeMethod<String>("inactivate");
    return result;
  }

}
