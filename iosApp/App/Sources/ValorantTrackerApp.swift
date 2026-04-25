import SwiftUI
import WidgetKit
import UserNotifications
import UIKit

@main
struct ValorantTrackerApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]?) -> Bool {
        UNUserNotificationCenter.current().delegate = self

        BackgroundTaskManager.shared.registerBackgroundTasks()
        BackgroundTaskManager.shared.scheduleAppRefresh()

        NotificationScheduler.shared.requestPermission { granted in
            if granted {
                NotificationScheduler.shared.scheduleNotificationsIfNeeded()
            }
        }

        return true
    }
}

extension AppDelegate: UNUserNotificationCenterDelegate {
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
    
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        completionHandler()
    }
}


struct ContentView: View {
    @State private var nextMatchInfo: String = "Loading..."
    @State private var isRefreshing: Bool = false
    
    private let mandatoryRed = Color(red: 1.0, green: 0.17, blue: 0.17)
    private let darkBackground = Color(red: 0.05, green: 0.05, blue: 0.05)
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                darkBackground
                    .ignoresSafeArea()
                
                VStack(spacing: 16) {
                    Spacer()
                        .frame(height: geometry.size.height * 0.15)
                    
                    Image("MandatoryLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 80, height: 80)
                        .foregroundColor(mandatoryRed)
                    
                    Text("MANDATORY")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(mandatoryRed)
                    
                    Text("Valorant Tracker")
                        .font(.system(size: 16))
                        .foregroundColor(.white.opacity(0.7))
                    
                    Spacer()
                        .frame(height: 24)
                    
                    if isRefreshing {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: mandatoryRed))
                    } else {
                        Text(nextMatchInfo)
                            .font(.system(size: 14))
                            .foregroundColor(mandatoryRed)
                    }
                    
                    Spacer()
                    
                    VStack(spacing: 16) {
                        Button(action: testNotification) {
                            Text("Test Notification")
                                .font(.system(size: 16, weight: .medium))
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(mandatoryRed)
                                .foregroundColor(.white)
                                .cornerRadius(12)
                        }
                        .padding(.horizontal, 24)
                        
                        Button(action: refreshWidget) {
                            HStack {
                                if isRefreshing {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: mandatoryRed))
                                        .scaleEffect(0.8)
                                }
                                Text(isRefreshing ? "Refreshing..." : "Refresh Widget")
                            }
                            .font(.system(size: 16, weight: .medium))
                            .frame(maxWidth: .infinity)
                            .padding()
                            .foregroundColor(mandatoryRed)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(mandatoryRed, lineWidth: 2)
                            )
                        }
                        .disabled(isRefreshing)
                        .padding(.horizontal, 24)
                    }
                    .padding(.bottom, 32)
                }
            }
        }
        .onAppear {
            loadNextMatchInfo()
        }
        .onOpenURL { url in
            if url.scheme == "valoranttracker" {
                loadNextMatchInfo()
            }
        }
    }
    
    private func loadNextMatchInfo() {
        guard let url = URL(string: "https://vlr.orlandomm.net/api/v1/matches") else {
            nextMatchInfo = "Error"
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let matches = json["data"] as? [[String: Any]] else {
                DispatchQueue.main.async {
                    nextMatchInfo = "No upcoming match"
                }
                return
            }
            
            let teamId = "7967"
            for match in matches {
                guard let status = match["status"] as? String,
                      status == "Upcoming",
                      let teams = match["teams"] as? [[String: Any]] else { continue }
                
                if teams.contains(where: { ($0["id"] as? String) == teamId }) {
                    let opponent = teams.first { ($0["id"] as? String) != teamId }
                    let opponentName = opponent?["name"] as? String ?? "TBD"
                    let event = match["event"] as? String ?? ""
                    let timeUntil = match["in"] as? String ?? ""
                    
                    DispatchQueue.main.async {
                        nextMatchInfo = "vs \(opponentName) - \(event) in \(timeUntil)"
                    }
                    return
                }
            }
            
            DispatchQueue.main.async {
                nextMatchInfo = "No upcoming match"
            }
        }.resume()
    }
    
    private func testNotification() {
        let center = UNUserNotificationCenter.current()
        
        // First check current authorization status
        center.getNotificationSettings { settings in
            print("Current notification settings: \(settings.authorizationStatus.rawValue)")
            
            // AuthorizationStatus: 0=notDetermined, 1=authorized, 2=denied
            if settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional {
                DispatchQueue.main.async {
                    self.scheduleNotification(center: center)
                }
            } else if settings.authorizationStatus == .notDetermined {
                // Request permission
                center.requestAuthorization(options: [.alert, .badge, .sound, .criticalAlert]) { granted, error in
                    print("Permission granted: \(granted), error: \(error?.localizedDescription ?? "none")")
                    
                    if granted {
                        DispatchQueue.main.async {
                            self.scheduleNotification(center: center)
                        }
                    }
                }
            } else {
                print("Notification permission DENIED - go to Settings to enable")
            }
        }
    }
    
    private func scheduleNotification(center: UNUserNotificationCenter) {
        let content = UNMutableNotificationContent()
        content.title = "Next Match"
        content.body = "Mandatory vs Caldya Esport - Group Stage"
        content.sound = UNNotificationSound.default
        
        // Fire after 3 seconds so user has time to see it
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
        let request = UNNotificationRequest(identifier: "test-notification", content: content, trigger: trigger)
        
        center.add(request) { error in
            if let error = error {
                print("Failed to schedule: \(error.localizedDescription)")
            } else {
                print("Notification scheduled - will appear in 3 seconds")
            }
        }
    }
    
    private func refreshWidget() {
        isRefreshing = true
        loadNextMatchInfo()
        WidgetCenter.shared.reloadAllTimelines()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            isRefreshing = false
        }
    }
}
