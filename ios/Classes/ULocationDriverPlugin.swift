import Flutter
import UIKit
import CoreLocation
import SwiftUI

let inactive = 0
let activeForeground = 1
let activeBackground = 2
var locationMonitoringStatus: Int = 0

@available(iOS 17.0, *)
public class ULocationDriverPlugin: NSObject, FlutterPlugin, CLLocationManagerDelegate {
  
  var channel = FlutterMethodChannel()
  
  private static let backgroundSession = CLBackgroundActivitySession()
  private var clLocationManager = CLLocationManager()
  static var fromDartChannel = FlutterMethodChannel()
  static var toDartChannel = FlutterBasicMessageChannel()
  var isScreenActive = false

  override init() {
    super.init()
    self.clLocationManager.delegate = self
    locationMonitoringStatus = inactive
    
    // アプリがフォアグラウンドに入った時に呼ばれる
    NotificationCenter.default.addObserver(
        self,
        selector: #selector(viewWillEnterForeground(_:)),
        name: UIApplication.willEnterForegroundNotification,
        object: nil
    )
    // アプリがバックグラウンドに入った時に呼ばれる
    NotificationCenter.default.addObserver(
        self,
        selector: #selector(viewDidEnterBackground(_:)),
        name: UIApplication.didEnterBackgroundNotification,
        object: nil
    )
    
  }

  @objc func viewWillEnterForeground(_ notification: Notification?) {
    // 実行したい処理を記載(例：日付ラベルの更新)
    debugPrint("ULocationDriverPlugin() -> viewWillEnterForeground()")
    // UserDefaults.standard.set(activeForeground, forKey: "locationMonitoringStatus")
    locationMonitoringStatus = activeForeground
    stateMachine()
    debugPrint("ULocationDriverPlugin() -> viewWillEnterForeground() -> \(locationMonitoringStatus)")
  }
  @objc func viewDidEnterBackground(_ notification: Notification?) {
    // 実行したい処理を記載
    debugPrint("ULocationDriverPlugin() -> viewDidEnterBackground()")
    // UserDefaults.standard.set(activeBackground, forKey: "locationMonitoringStatus")
    locationMonitoringStatus = activeBackground
    stateMachine()
    debugPrint("ULocationDriverPlugin() -> viewWillEnterForeground() -> \(locationMonitoringStatus)")
  }
  
  public static func register(with registrar: FlutterPluginRegistrar) {
    fromDartChannel = FlutterMethodChannel(name: "com.jimdo.uchida001tmhr.u_location_driver/fromDart", binaryMessenger: registrar.messenger())
    toDartChannel = FlutterBasicMessageChannel(
      name: "com.jimdo.uchida001tmhr.u_location_driver/toDart",
      binaryMessenger: registrar.messenger(),
      codec: FlutterStringCodec.sharedInstance()
    )
    let instance = ULocationDriverPlugin()
    registrar.addMethodCallDelegate(instance, channel: fromDartChannel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "activate":
      debugPrint("ULocationDriverPlugin() -> handle() -> activate")
      // UserDefaults.standard.set(activeForeground, forKey: "locationMonitoringStatus")
      locationMonitoringStatus = activeForeground
      stateMachine()
    case "inactivate":
      debugPrint("ULocationDriverPlugin() -> handle() -> inactivate")
      // UserDefaults.standard.set(inactive, forKey: "locationMonitoringStatus")
      locationMonitoringStatus = activeBackground
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
    debugPrint("ULocationDriverPlugin() -> informLocationToDart() -> message -> \(message)")
    toDartChannel.sendMessage(message, reply: {reply in
      debugPrint("ULocationDriverPlugin() -> informLocationToDart() -> sendMessage() -> \(String(describing: reply))")
    })
  }
 
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
      // let locationMonitoringStatus = UserDefaults.standard.integer(forKey: "locationMonitoringStatus")
      switch (locationMonitoringStatus) {
      case activeForeground:
        debugPrint("ULocationDriverPlugin() -> stateMachine() -> activeForeground")
        pullLocation()
        break
      case activeBackground:
        debugPrint("ULocationDriverPlugin() -> stateMachine() -> activeBackground")
        backgroundMonitoring()
        break
      default:
        // UserDefaults.standard.set(inactive, forKey: "locationMonitoringStatus")
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
    switch(locationMonitoringStatus) {
      case activeForeground:
        stateMachine()
        break
      case activeBackground:
        stateMachine()
        break
      default:
        break
    }
  }
  
  func backgroundMonitoring() {
    // let locationMonitoringStatus = UserDefaults.standard.integer(forKey: "locationMonitoringStatus")
    if (locationMonitoringStatus == activeBackground) {
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
    debugPrint("ULocationDriverPlugin() -> locationManager()")
    if (locations.last != nil) {
      ULocationDriverPlugin.informLocationToDart(location: locations.last!)
      backgroundMonitoring()
    }
  }

  public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    // エラーが発生した際に実行したい処理
  }
  
  func pullLocation() {
    debugPrint("ULocationDriverPlugin() -> pullLocation()")
    let task = Task {
      for try await update in CLLocationUpdate.liveUpdates() {
        // let locationMonitoringStatus = UserDefaults.standard.integer(forKey: "locationMonitoringStatus")
        if (locationMonitoringStatus != activeForeground) {
          return
        }
        if (update.location != nil) {
          ULocationDriverPlugin.informLocationToDart(location: update.location!)
        }
        try? await Task.sleep(nanoseconds: 10_000_000_000)
      }
    }
    //  let locationMonitoringStatus = UserDefaults.standard.integer(forKey: "locationMonitoringStatus")
    if (locationMonitoringStatus != activeForeground) {
      task.cancel()
    }
  }
  
}
