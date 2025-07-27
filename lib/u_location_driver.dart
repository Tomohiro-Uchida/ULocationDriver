
import 'dart:collection';

import 'u_location_driver_platform_interface.dart';

class ULocationDriver {

  Future<String?> registerBackgroundIsolate(HashMap<String, dynamic>? arguments) {
    return ULocationDriverPlatform.instance.registerBackgroundIsolate(arguments);
  }

  Future<String?> activate() {
    return ULocationDriverPlatform.instance.activate();
  }

  Future<String?> inactivate() {
    return ULocationDriverPlatform.instance.inactivate();
  }

}
