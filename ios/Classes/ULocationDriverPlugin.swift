import Flutter
import UIKit
import CoreLocation
import SwiftUI

@available(iOS 17.0, *)
public class ULocationDriverPlugin: NSObject, FlutterPlugin, CLLocationManagerDelegate, UIWindowSceneDelegate {
  
  var channel = FlutterMethodChannel()
  
  let inactive = 0
  let activeForeground = 1
  let activebackground = 2

  private static let backgroundSession = CLBackgroundActivitySession()
  private var clLocationManager = CLLocationManager()
  static var fromDartChannel = FlutterMethodChannel()
  static var toDartChannelNameForegournd = FlutterMethodChannel()
  var isScreenActive = false

  override init() {
    super.init()
    self.clLocationManager.delegate = self
  }

  public static func register(with registrar: FlutterPluginRegistrar) {
    fromDartChannel = FlutterMethodChannel(name: "com.jimdo.uchida001tmhr.u_location_driver/fromDart", binaryMessenger: registrar.messenger())
    toDartChannelNameForegournd = FlutterMethodChannel(name: "com.jimdo.uchida001tmhr.u_location_driver/toDartForeground", binaryMessenger: registrar.messenger())
    let instance = ULocationDriverPlugin()
    registrar.addMethodCallDelegate(instance, channel: fromDartChannel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "activateForeground":
      UserDefaults.standard.set(activeForeground, forKey: "locationMonitoringStatus")
      stateMachine()
    case "activateBackground":
      UserDefaults.standard.set(activebackground, forKey: "locationMonitoringStatus")
      stateMachine()
    case "inactivate":
      UserDefaults.standard.set(inactive, forKey: "locationMonitoringStatus")
      stateMachine()
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
    toDartChannelNameForegournd.invokeMethod("informLocationToDartForeground", arguments: message)
  }
 
  /*
  func getLocationManager() -> CLLocationManager {
    return self.clLocationManager
  }
   */

  func stateMachine() {
    switch (clLocationManager.authorizationStatus) {
    case .notDetermined:
      clLocationManager.requestWhenInUseAuthorization()
      break
    case .restricted:
      break
    case .denied:
      break
    case .authorizedAlways:
      let locationMonitoringStatus = UserDefaults.standard.integer(forKey: "locationMonitoringStatus")
      switch (locationMonitoringStatus) {
      case activeForeground:
        pullLocation()
        break
      case activebackground:
        backgroundMonitoring()
        break
      default:
        UserDefaults.standard.set(inactive, forKey: "locationMonitoringStatus")
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
    stateMachine()
  }
  
  func backgroundMonitoring() {
    let locationMonitoringStatus = UserDefaults.standard.integer(forKey: "locationMonitoringStatus")
    if (locationMonitoringStatus == activebackground) {
      if (CLLocationManager.significantLocationChangeMonitoringAvailable()) {
        // allowsBackgroundLocationUpdates を true に設定することで、
        // バックグラウンドでの位置情報更新を有効にします。
        clLocationManager.allowsBackgroundLocationUpdates = true
        clLocationManager.startMonitoringSignificantLocationChanges() // 常に許可されたら監視を開始
      }
    } else {
      clLocationManager.stopMonitoringSignificantLocationChanges()
    }
  }
  

  public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    if (locations.last != nil) {
      ULocationDriverPlugin.informLocationToDart(location: locations.last!)
      backgroundMonitoring()
    }
  }

  public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    // エラーが発生した際に実行したい処理
  }
  
  func pullLocation() {
    let task = Task {
      for try await update in CLLocationUpdate.liveUpdates() {
        let locationMonitoringStatus = UserDefaults.standard.integer(forKey: "locationMonitoringStatus")
        if (locationMonitoringStatus != activeForeground) {
          return
        }
        if (update.location != nil) {
          ULocationDriverPlugin.informLocationToDart(location: update.location!)
        }
      }
    }
    let locationMonitoringStatus = UserDefaults.standard.integer(forKey: "locationMonitoringStatus")
    if (locationMonitoringStatus != activeForeground) {
      task.cancel()
    }
  }

}
