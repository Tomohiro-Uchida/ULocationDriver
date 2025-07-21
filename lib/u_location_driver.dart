
import 'dart:collection';

import 'u_location_driver_platform_interface.dart';

class ULocationDriver {

  Future<String?> registerBackgroundIsolate(HashMap<String, dynamic>? arguments) {
    return ULocationDriverPlatform.instance.registerBackgroundIsolate(arguments);
  }

  Future<String?> startBackgroundIsolate() {
    return ULocationDriverPlatform.instance.startBackgroundIsolate();
  }

  Future<String?> activateForeground() {
    return ULocationDriverPlatform.instance.activateForeground();
  }

  Future<String?> activateBackground() {
    return ULocationDriverPlatform.instance.activateBackground();
  }

  Future<String?> inactivate() {
    return ULocationDriverPlatform.instance.inactivate();
  }

}
