import UIKit

/// Picture in Picture "parking" from NuvioMobile-Enhanced keeps the player alive in PiP while the
/// user browses the app. It needs app-level route hooks this repository does not have yet, so
/// players are never parked here and PiP behaves as before.
@MainActor
final class PlayerPictureInPictureParking {
    static let shared = PlayerPictureInPictureParking()

    var hasParkedPlayer: Bool { false }

    func isParked(player: MPVPlayerViewController) -> Bool { false }

    func park(player: MPVPlayerViewController) -> Bool { false }

    func restore(player: MPVPlayerViewController, completion: @escaping (Bool) -> Void) -> Bool { false }

    func closeParked() {}
}
