import Flutter
import UIKit
import CoreLocation

@available(iOS 17.0, *)
public class ULocationDriverPlugin: NSObject, FlutterPlugin, CLLocationManagerDelegate, @unchecked Sendable {
 
  public let stopped = 0
  public let activeForeground = 1
  public let activeBackground = 2
  public let activeTerminated = 3
  public var locationMonitoringStatus: Int = 0
  
  public static var shared = ULocationDriverPlugin()
  public let clLocationManager = CLLocationManager() // アクセス可能にする
  
  var channel = FlutterMethodChannel()
  
  // private static let backgroundSession = CLBackgroundActivitySession()
  // private let clLocationManager = CLLocationManager()
  static var fromDartChannel = FlutterMethodChannel()
  static var toDartChannel = FlutterMethodChannel()

  var backgroundLocation: CLLocation?
  
  static var callbackHandler: String = ""
  static var flutterEngineGroup: FlutterEngineGroup!

  private override init() {
    super.init()
    self.clLocationManager.delegate = self
    locationMonitoringStatus = stopped
  }
  
  public static func register(with registrar: FlutterPluginRegistrar) {
    fromDartChannel = FlutterMethodChannel(name: "com.jimdo.uchida001tmhr.u_location_driver/fromDart", binaryMessenger: registrar.messenger())
    toDartChannel = FlutterMethodChannel(name: "com.jimdo.uchida001tmhr.u_location_driver/toDart", binaryMessenger: registrar.messenger())
    let instance = ULocationDriverPlugin.shared
    registrar.addMethodCallDelegate(instance, channel: fromDartChannel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "activate":
      debugPrint("ULocationDriverPlugin() -> handle() -> activate")
      locationMonitoringStatus = activeForeground
      stateMachine(startLocationUpdate: true)
      result("ACK")
    case "inactivate":
      debugPrint("ULocationDriverPlugin() -> handle() -> inactivate")
      locationMonitoringStatus = stopped
      stateMachine()
      result("ACK")
    default:
      result(FlutterMethodNotImplemented)
    }
  }
  
  static func informLocationToDart(location: CLLocation) {
    /// DateFomatterクラスのインスタンス生成
    let dateFormatter = DateFormatter()
     
    /// カレンダー、ロケール、タイムゾーンの設定（未指定時は端末の設定が採用される）
    dateFormatter.calendar = Calendar(identifier: .gregorian)
    dateFormatter.locale = Locale(identifier: "ja_JP")
    dateFormatter.timeZone = TimeZone(identifier:  "Asia/Tokyo")
     
    /// 変換フォーマット定義（未設定の場合は自動フォーマットが採用される）
    dateFormatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
     
    /// データ変換（Date→テキスト）
    let dateString = dateFormatter.string(from: Date())
    let message = "\(dateString),\(location.coordinate.latitude),\(location.coordinate .longitude)"
    debugPrint("ULocationDriverPlugin() -> informLocationToDart() -> message -> \(message)")
    toDartChannel.invokeMethod("location", arguments: message)
  }
 
  func stateMachine(startLocationUpdate: Bool = false) {
    switch (clLocationManager.authorizationStatus) {
    case .notDetermined:
      clLocationManager.requestWhenInUseAuthorization()
      break
    case .restricted:
      break
    case .denied:
      break
    case .authorizedAlways:
      switch (locationMonitoringStatus) {
      case stopped:
        clLocationManager.stopUpdatingLocation()
        clLocationManager.stopMonitoringSignificantLocationChanges()
        break;
      case activeForeground, activeBackground, activeTerminated:
        debugPrint("ULocationDriverPlugin() -> stateMachine() -> activeForeground/activeBackground/activeTerminated")
        locationMonitoring(startLocationUpdate: startLocationUpdate)
        break
      default:
        break
      }
      break
    case .authorizedWhenInUse:
      clLocationManager.requestAlwaysAuthorization()
      break
    @unknown default:
      break
    }
  }
  
  public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    debugPrint("ULocationDriverPlugin() -> locationManagerDidChangeAuthorization() : locationMonitoringStatus = \(locationMonitoringStatus)")
    stateMachine(startLocationUpdate: true)
  }
  
  public func locationMonitoring(startLocationUpdate: Bool = false) {
    switch (locationMonitoringStatus) {
    case stopped:
      break
    case activeForeground:
      clLocationManager.delegate = self
      // clLocationManager.distanceFilter = kCLDistanceFilterNone
      clLocationManager.distanceFilter  = 10.0
      // clLocationManager.desiredAccuracy = kCLLocationAccuracyBest
      // clLocationManager.desiredAccuracy = kCLLocationAccuracyReduced
      clLocationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
      if (startLocationUpdate) {
        clLocationManager.startUpdatingLocation()
        debugPrint("ULocationDriverPlugin() -> startUpdatingLocation")
      }
      break
    case activeBackground, activeTerminated:
      clLocationManager.delegate = self
      clLocationManager.allowsBackgroundLocationUpdates = true
      clLocationManager.pausesLocationUpdatesAutomatically = false
      // clLocationManager.distanceFilter = kCLDistanceFilterNone
      clLocationManager.distanceFilter  = 10.0
      // clLocationManager.desiredAccuracy = kCLLocationAccuracyBest
      // clLocationManager.desiredAccuracy = kCLLocationAccuracyReduced
      clLocationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
      if (CLLocationManager.significantLocationChangeMonitoringAvailable()) {
        clLocationManager.startMonitoringSignificantLocationChanges()
        debugPrint("ULocationDriverPlugin() -> startMonitoringSignificantLocationChanges()")
      }
      if (startLocationUpdate) {
        clLocationManager.startUpdatingLocation()
        debugPrint("ULocationDriverPlugin() -> startUpdatingLocation")
      }
      break
    default:
      break
    }
  }

  public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    debugPrint("ULocationDriverPlugin() -> locationManager()")
    if (locations.last != nil) {
      ULocationDriverPlugin.informLocationToDart(location: locations.last!)
      locationMonitoring()
    }
  }

  public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    // エラーが発生した際に実行したい処理
  }
  
}
  
