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
  
  private static let backgroundSession = CLBackgroundActivitySession()
  // private let clLocationManager = CLLocationManager()
  static var fromDartChannel = FlutterMethodChannel()
  static var toDartChannel = FlutterBasicMessageChannel()

  var backgroundLocation: CLLocation?
  
  static var callbackHandler: String = ""
  static var flutterEngineGroup: FlutterEngineGroup!

  private override init() {
    super.init()
    self.clLocationManager.delegate = self
    locationMonitoringStatus = stopped
    
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
  
  func startBackgroundIsolate(callbackHandle: String) {
  
    let flutterEngineBackground = ULocationDriverPlugin.flutterEngineGroup.makeEngine(
      withEntrypoint: "backgroundEntryPoint",
      // libraryURI: "package:u_location_driver_example/main.dart"
      libraryURI: nil
    )
    
    ULocationDriverPlugin.toDartChannel = FlutterBasicMessageChannel(
      name: "com.jimdo.uchida001tmhr.u_location_driver/toDart",
      binaryMessenger: flutterEngineBackground.binaryMessenger,
      codec: FlutterStringCodec.sharedInstance()
    )
    
    let result = flutterEngineBackground.run()
    
    debugPrint("startBackgroundIsolate: Background isolate started: result = \(result)")
  }

  func stopBackgroundIsolate() {
    debugPrint("ULocationDriverPlugin: stopBackgroundIsolate() start")
    let message = "stopBackgroundIsolate"
    ULocationDriverPlugin.toDartChannel.sendMessage(message, reply: {reply in
      debugPrint("ULocationDriverPlugin: Sent via toDartChannelToForeground -> \(String(describing: reply))")
    })
  }

  func stopMainIsolate() {
    debugPrint("ULocationDriverPlugin: stopMainIsolate() start")
    let message = "stopMainIsolate"
    ULocationDriverPlugin.toDartChannel.sendMessage(message) { reply in
      debugPrint("ULocationDriverPlugin: Sent via toDartChannelToBackground -> \(String(describing: reply))")
    }
  }

  @objc func viewWillEnterForeground(_ notification: Notification?) {
    // 実行したい処理を記載(例：日付ラベルの更新)
    debugPrint("ULocationDriverPlugin() -> viewWillEnterForeground()")
    stopBackgroundIsolate()
    locationMonitoringStatus = activeForeground
    stateMachine()
    debugPrint("ULocationDriverPlugin() -> viewWillEnterForeground() -> \(locationMonitoringStatus)")
  }
  
  @objc func viewDidEnterBackground(_ notification: Notification?) {
    // 実行したい処理を記載
    debugPrint("ULocationDriverPlugin() -> viewDidEnterBackground()")
    stopMainIsolate()
    // FlutterEngineGroupを初期化
    ULocationDriverPlugin.flutterEngineGroup = FlutterEngineGroup(name: "ULocationDriverIsolateGroup", project: nil)
    
    if (!ULocationDriverPlugin.callbackHandler.isEmpty) {
      startBackgroundIsolate(callbackHandle: ULocationDriverPlugin.callbackHandler)
    }
    locationMonitoringStatus = activeBackground
    stateMachine()
    debugPrint("ULocationDriverPlugin() -> viewDidEnterBackground() -> \(locationMonitoringStatus)")
  }
  
  public static func register(with registrar: FlutterPluginRegistrar) {
    fromDartChannel = FlutterMethodChannel(name: "com.jimdo.uchida001tmhr.u_location_driver/fromDart", binaryMessenger: registrar.messenger())
    toDartChannel = FlutterBasicMessageChannel(
      name: "com.jimdo.uchida001tmhr.u_location_driver/toDart",
      binaryMessenger: registrar.messenger(),
      codec: FlutterStringCodec.sharedInstance()
    )
    let instance = ULocationDriverPlugin.shared
    registrar.addMethodCallDelegate(instance, channel: fromDartChannel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "registerBackgroundIsolate":
      if (call.arguments != nil) {
        ULocationDriverPlugin.callbackHandler = (call.arguments as! Dictionary<String, Any>)["callbackHandle"] as! String
      }
      locationMonitoringStatus = stopped
      stateMachine()
    case "activate":
      debugPrint("ULocationDriverPlugin() -> handle() -> activate")
      locationMonitoringStatus = activeForeground
      stateMachine(triggerUpdatingLocation: true)
    case "inactivate":
      debugPrint("ULocationDriverPlugin() -> handle() -> inactivate")
      locationMonitoringStatus = stopped
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
 
  func stateMachine(triggerUpdatingLocation: Bool = false) {
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
      case activeForeground:
        debugPrint("ULocationDriverPlugin() -> stateMachine() -> activeForeground")
        locationMonitoring(triggerUpdatingLocation: triggerUpdatingLocation)
        break
      case activeBackground, activeTerminated: // activeTerminatedとactiveBackgroundを統合
        debugPrint("ULocationDriverPlugin() -> stateMachine() -> activeBackground/activeTerminated")
        locationMonitoring()
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
    stateMachine()
  }
  
  public func locationMonitoring(triggerUpdatingLocation: Bool = false) {
    switch (locationMonitoringStatus) {
    case stopped:
      break
    case activeForeground:
      clLocationManager.delegate = self
      clLocationManager.distanceFilter = kCLDistanceFilterNone
      // clLocationManager.distanceFilter = kCLLocationAccuracyBest
      clLocationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
      if (triggerUpdatingLocation) {
        clLocationManager.startUpdatingLocation()
        debugPrint("ULocationDriverPlugin() -> startUpdatingLocation")
      }
      break
    case activeBackground, activeTerminated:
      if (CLLocationManager.significantLocationChangeMonitoringAvailable()) {
        clLocationManager.delegate = self
        clLocationManager.allowsBackgroundLocationUpdates = true
        clLocationManager.pausesLocationUpdatesAutomatically = false
        clLocationManager.distanceFilter = kCLDistanceFilterNone
        clLocationManager.distanceFilter = kCLLocationAccuracyBest
        // clLocationManager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        clLocationManager.startMonitoringSignificantLocationChanges()
        debugPrint("ULocationDriverPlugin() -> startMonitoringSignificantLocationChanges()")
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
  
