
import 'u_location_driver_platform_interface.dart';

class ULocationDriver {

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
