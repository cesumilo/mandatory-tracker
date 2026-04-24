import SwiftUI
import WidgetKit
import UserNotifications

@main
struct ValorantTrackerApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @State private var nextMatchInfo: String = "Loading..."
    @State private var isRefreshing: Bool = false
    
    private let mandatoryRed = Color(red: 1.0, green: 0.17, blue: 0.17)
    private let darkBackground = Color(red: 0.05, green: 0.05, blue: 0.05)
    
    var body: some View {
        ZStack {
            darkBackground.ignoresSafeArea()
            
            VStack(spacing: 16) {
                Spacer()
                
                Image("MandatoryLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 120, height: 120)
                    .foregroundColor(mandatoryRed)
                    .overlay(
                        Image(systemName: "sportscourt")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 60, height: 60)
                            .foregroundColor(mandatoryRed)
                    )
                
                Text("MANDATORY")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(mandatoryRed)
                
                Text("Valorant Tracker")
                    .font(.system(size: 16))
                    .foregroundColor(.white.opacity(0.7))
                
                if isRefreshing {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: mandatoryRed))
                } else {
                    Text(nextMatchInfo)
                        .font(.system(size: 14))
                        .foregroundColor(mandatoryRed)
                }
                
                Spacer()
                
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
                    .background(Color.clear)
                    .foregroundColor(mandatoryRed)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(mandatoryRed, lineWidth: 2)
                    )
                }
                .disabled(isRefreshing)
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
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
        center.requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            if granted {
                let content = UNMutableNotificationContent()
                content.title = "Next Match"
                content.body = "Mandatory vs Caldya Esport - Group Stage"
                content.sound = .default
                
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
                let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: trigger)
                
                center.add(request)
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