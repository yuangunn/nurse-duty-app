import Foundation
import WatchConnectivity
import SwiftData
import NurseDutyModel

/// Phone side of the watch sync. Pushes the latest WatchState to the watch (applicationContext,
/// latest-wins) and applies commands the watch sends back (transferUserInfo, guaranteed delivery).
final class PhoneConnectivity: NSObject, WCSessionDelegate {
    static let shared = PhoneConnectivity()
    private var container: ModelContainer?

    func start(container: ModelContainer) {
        self.container = container
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    /// Send the current state to the watch. Safe to call from any thread.
    func push() {
        guard let container, WCSession.isSupported(),
              WCSession.default.activationState == .activated else { return }
        let state = WatchSync.state(from: ModelContext(container))
        guard let data = try? JSONEncoder().encode(state) else { return }
        try? WCSession.default.updateApplicationContext(["state": data])
    }

    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any]) {
        guard let data = userInfo["command"] as? Data,
              let cmd = try? JSONDecoder().decode(WatchCommand.self, from: data),
              let container else { return }
        WatchSync.apply(cmd, to: ModelContext(container))
        push()   // re-send authoritative state
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState,
                 error: Error?) {
        push()
    }
    func sessionDidBecomeInactive(_ session: WCSession) {}
    func sessionDidDeactivate(_ session: WCSession) { WCSession.default.activate() }
}
