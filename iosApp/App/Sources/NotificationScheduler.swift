import Foundation
import UserNotifications
import UIKit

class NotificationScheduler {
    static let shared = NotificationScheduler()
    private let teamId = "7967"
    private let apiURL = "https://vlr.orlandomm.net/api/v1/matches"

    private init() {}

    func requestPermission(completion: @escaping (Bool) -> Void) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            completion(granted)
        }
    }

    func scheduleNotificationsIfNeeded() {
        fetchNextMatch { [weak self] match in
            guard let self = self, let match = match else { return }

            self.cancelExistingNotifications()

            let matchTime = match.timestamp
            let oneHourBefore = matchTime - 3600
            let now = Date().timeIntervalSince1970

            if oneHourBefore > now {
                self.scheduleNotification(for: match, at: oneHourBefore)
            } else if matchTime > now {
                self.scheduleNotification(for: match, at: matchTime)
            }
        }
    }

    private func fetchNextMatch(completion: @escaping (MatchData?) -> Void) {
        guard let url = URL(string: apiURL) else {
            completion(nil)
            return
        }

        URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let matches = json["data"] as? [[String: Any]] else {
                completion(nil)
                return
            }

            let teamId = "7967"
            for match in matches {
                guard let status = match["status"] as? String,
                      status == "Upcoming",
                      let teams = match["teams"] as? [[String: Any]],
                      teams.contains(where: { ($0["id"] as? String) == teamId }) else { continue }

                guard let matchId = match["id"] as? String,
                      let event = match["event"] as? String,
                      let teamsData = match["teams"] as? [[String: Any]] else { continue }

                let opponent = teamsData.first { ($0["id"] as? String) != teamId }
                let opponentName = opponent?["name"] as? String ?? "TBD"

                let matchData = MatchData(
                    id: matchId,
                    event: event,
                    opponent: opponentName,
                    timestamp: match["timestamp"] as? TimeInterval ?? 0
                )
                completion(matchData)
                return
            }
            completion(nil)
        }.resume()
    }

    private func scheduleNotification(for match: MatchData, at timestamp: TimeInterval) {
        let center = UNUserNotificationCenter.current()

        let content = UNMutableNotificationContent()
        content.title = "Match in 1 hour!"
        content.body = "Mandatory vs \(match.opponent) - \(match.event)"
        content.sound = .default

        let triggerDate = Date(timeIntervalSince1970: timestamp)
        let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: triggerDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)

        let request = UNNotificationRequest(
            identifier: "match-notification-\(match.id)",
            content: content,
            trigger: trigger
        )

        center.add(request) { error in
            if let error = error {
                print("Failed to schedule notification: \(error.localizedDescription)")
            } else {
                print("Notification scheduled for \(triggerDate)")
            }
        }
    }

    private func cancelExistingNotifications() {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            let matchNotifications = requests.filter { $0.identifier.hasPrefix("match-notification-") }
            let identifiers = matchNotifications.map { $0.identifier }
            center.removePendingNotificationRequests(withIdentifiers: identifiers)
        }
    }

    func showTestNotification() {
        let center = UNUserNotificationCenter.current()

        let content = UNMutableNotificationContent()
        content.title = "Test Notification"
        content.body = "Notifications are working!"
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false)
        let request = UNNotificationRequest(
            identifier: "test-notification",
            content: content,
            trigger: trigger
        )

        center.add(request)
    }
}

struct MatchData {
    let id: String
    let event: String
    let opponent: String
    let timestamp: TimeInterval
}
