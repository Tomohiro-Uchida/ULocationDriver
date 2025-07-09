
import 'u_location_driver_platform_interface.dart';

class ULocationDriver {

  Future<String?> activateForeground() {
    return ULocationDriverPlatform.instance.activateForeground();
  }

  Future<String?> activateBackground(Function backgroundLocationMain) {
    return ULocationDriverPlatform.instance.activateBackground(backgroundLocationMain);
  }

  Future<String?> inactivate() {
    return ULocationDriverPlatform.instance.inactivate();
  }

}
