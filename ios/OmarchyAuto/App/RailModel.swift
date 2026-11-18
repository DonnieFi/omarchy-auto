import Foundation

/// The four rail tiles, in the Android shell's declaration order
/// (ADR-0015: DRIVE / MEDIA / COMMS / HOME).
enum TileID: String, CaseIterable, Equatable, Identifiable {
    case drive
    case media
    case comms
    case home

    var id: String { rawValue }

    /// One-based rail number, matching the Android shell's [01..04] legends.
    var number: Int {
        switch self {
        case .drive: return 1
        case .media: return 2
        case .comms: return 3
        case .home: return 4
        }
    }

    /// Cut-out legend, e.g. "[01 · DRIVE]".
    var legend: String {
        let twoDigit = String(format: "%02d", number)
        return "[\(twoDigit) · \(rawValue.uppercased())]"
    }
}

/// Mounted-dashboard state. The app opens with every tile minimized so the
/// wallpaper is the first thing you see; tapping an unselected tile engages
/// it and fills the stage; tapping the engaged tile again clears the stage
/// (ADR-0010).
struct ShellModel: Equatable {
    var engaged: TileID?

    init(engaged: TileID? = nil) {
        self.engaged = engaged
    }

    mutating func tap(_ tile: TileID) {
        engaged = (engaged == tile) ? nil : tile
    }
}
