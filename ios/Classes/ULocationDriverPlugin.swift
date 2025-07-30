import Flutter
import UIKit
import CoreLocation
import SwiftUI

let inactive = 0
let activeForeground = 1
let activeBackground = 2
var locationMonitoringStatus: Int = 0

@available(iOS 17.0, *)
public class ULocationDriverPlugin: NSObject, FlutterPlugin, CLLocationManagerDelegate, UIWindowSceneDelegate {
  
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
      locationMonitoringStatus = inactive
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
    // let locationMonitoringStatus = UserDefaults.standard.integer(forKey: "locationMonitoringStatus")
    if (locationMonitoringStatus != activeForeground) {
      task.cancel()
    }
  }

  class SceneDelegate: UIResponder, UIWindowSceneDelegate {

    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
      // Use this method to optionally configure and attach the UIWindow `window` to the provided UIWindowScene `scene`.
      // If using a storyboard, the `window` property will automatically be initialized and attached to the scene.
      // This delegate does not imply the connecting scene or session are new (see `application:configurationForConnectingSceneSession` instead).
      guard let _ = (scene as? UIWindowScene) else { return }
    }

    func sceneDidDisconnect(_ scene: UIScene) {
      // Called as the scene is being released by the system.
      // This occurs shortly after the scene enters the background, or when its session is discarded.
      // Release any resources associated with this scene that can be re-created the next time the scene connects.
      // The scene may re-connect later, as its session was not necessarily discarded (see `application:didDiscardSceneSessions` instead).
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
      // Called when the scene has moved from an inactive state to an active state.
      // Use this method to restart any tasks that were paused (or not yet started) when the scene was inactive.
    }

    func sceneWillResignActive(_ scene: UIScene) {
      // Called when the scene will move from an active state to an inactive state.
      // This may occur due to temporary interruptions (ex. an incoming phone call).
    }

    func sceneWillEnterForeground(_ scene: UIScene) {
      // Called as the scene transitions from the background to the foreground.
      // Use this method to undo the changes made on entering the background.
      locationMonitoringStatus = activeForeground
      debugPrint("sceneWillEnterForeground() -> \(locationMonitoringStatus)")
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
      // Called as the scene transitions from the foreground to the background.
      // Use this method to save data, release shared resources, and store enough scene-specific state information
      // to restore the scene back to its current state.
      locationMonitoringStatus = activeBackground
      debugPrint("sceneDidEnterBackground() -> \(locationMonitoringStatus)")
    }

  }
  
}
