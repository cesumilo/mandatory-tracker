import Foundation
import UIKit
import UserNotifications

class NotificationScheduler {
    static let shared = NotificationScheduler()
    private let teamId = "7967"

    private init() {}

    func requestPermission(completion: @escaping (Bool) -> Void) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            completion(granted)
        }
    }

    func scheduleNotificationsIfNeeded() {
        Task {
            await fetchNextMatch { [weak self] match in
                guard let self, let match else { return }

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
    }

    private func fetchNextMatch(completion: @escaping (MatchData?) -> Void) async {
        // Use VlrScraper to get upcoming matches
        let upcomingMatches = await VlrScraper.shared.getUpcomingMatches()

        guard let firstMatch = upcomingMatches.first else {
            completion(nil)
            return
        }

        // Find opponent name from the match
        let opponent = firstMatch.teams.first { $0.id != teamId }
        let opponentName = opponent?.name ?? "TBD"

        let matchData = MatchData(
            id: firstMatch.id,
            event: firstMatch.event,
            opponent: opponentName,
            timestamp: TimeInterval(firstMatch.timestamp)
        )

        completion(matchData)
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
            if let error {
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
            let identifiers = matchNotifications.map(\.identifier)
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
