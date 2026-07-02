import SwiftUI

/// Home-hero weather chip (Seoul fixed — parity with Android; geolocation is a later add).
/// Re-fetches at most every 30 min; failures don't advance the clock so the next resume retries.
@MainActor
final class WeatherStore: ObservableObject {
    static let shared = WeatherStore()
    @Published var temp = "—"
    @Published var symbol = "sun.max.fill"
    @Published var live = false
    private var fetchedAt: Date?

    func refresh() {
        if let t = fetchedAt, Date().timeIntervalSince(t) < 30 * 60 { return }
        Task {
            do {
                let url = URL(string: "https://api.open-meteo.com/v1/forecast?latitude=37.5665&longitude=126.9780&current=temperature_2m,weather_code")!
                var req = URLRequest(url: url); req.timeoutInterval = 5
                let (data, _) = try await URLSession.shared.data(for: req)
                guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let cur = root["current"] as? [String: Any],
                      let t = cur["temperature_2m"] as? Double,
                      let code = cur["weather_code"] as? Int else { return }
                temp = "\(Int(t.rounded()))°"
                symbol = Self.symbol(for: code)
                live = true
                fetchedAt = Date()
            } catch { /* keep last value; retried on next resume */ }
        }
    }

    private static func symbol(for code: Int) -> String {
        switch code {
        case 0: "sun.max.fill"
        case 1, 2: "cloud.sun.fill"
        case 3, 45, 48: "cloud.fill"
        case 51...67, 80...82: "cloud.rain.fill"
        case 71...77: "snowflake"
        case 95...99: "cloud.bolt.fill"
        default: "sun.max.fill"
        }
    }
}
