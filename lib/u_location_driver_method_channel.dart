import 'dart:collection';

import 'package:flutter/services.dart';
import 'u_location_driver_platform_interface.dart';

/// An implementation of [ULocationDriverPlatform] that uses method channels.
class MethodChannelULocationDriver extends ULocationDriverPlatform {
  /// The method channel used to interact with the native platform.
  final methodChannel = const MethodChannel("com.jimdo.uchida001tmhr.u_location_driver/fromDart");

  @override
  Future<String?> registerBackgroundIsolate(HashMap<String, dynamic>? arguments) async {
    String? result = "";
    if (arguments != null) {
      result = await methodChannel.invokeMethod<String>("registerBackgroundIsolate", arguments);
    }
    return result;
  }

  @override
  Future<String?> activate() async {
    String? result = "";
    result = await methodChannel.invokeMethod<String>("activate");
    return result;
  }

  @override
  Future<String?> inactivate() async {
    final result = await methodChannel.invokeMethod<String>("inactivate");
    return result;
  }

}
