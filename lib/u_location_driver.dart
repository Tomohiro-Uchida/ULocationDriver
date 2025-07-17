
import 'dart:collection';

import 'u_location_driver_platform_interface.dart';

class ULocationDriver {

  Future<String?> activateForeground({HashMap<String, dynamic>? arguments}) {
    if (arguments == null) {
      return ULocationDriverPlatform.instance.activateForeground();
    } else {
      return ULocationDriverPlatform.instance.activateForeground(arguments: arguments);
    }
  }

  Future<String?> activateBackground() {
    return ULocationDriverPlatform.instance.activateBackground();
  }

  Future<String?> inactivate() {
    return ULocationDriverPlatform.instance.inactivate();
  }

}
