import Flutter
import u_location_driver
import UIKit
import CoreLocation

@main
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)
    
    if launchOptions?[.location] != nil {
      print("App relaunched due to location event")

      // locationManager を適切に再セットアップ
      //  Pluginインスタンスにアクセス
      let plugin = ULocationDriverPlugin.shared
      plugin.locationManager(plugin.clLocationManager, didUpdateLocations: launchOptions?[.location] as! [CLLocation])
      /*
      plugin.clLocationManager.delegate = plugin
      plugin.clLocationManager.allowsBackgroundLocationUpdates = true
      plugin.clLocationManager.pausesLocationUpdatesAutomatically = false
      // plugin.clLocationManager.distanceFilter = kCLLocationAccuracyKilometer
      plugin.clLocationManager.distanceFilter = kCLLocationAccuracyHundredMeters
      // plugin.clLocationManager.distanceFilter = kCLDistanceFilterNone
      plugin.clLocationManager.startMonitoringSignificantLocationChanges()
       */
      
    }
    
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
