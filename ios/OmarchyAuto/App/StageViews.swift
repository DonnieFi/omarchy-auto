import SwiftUI

/// Stage content for the engaged tile. Every stage states honestly what is
/// real today and what is pending (ADR-0009); nothing is faked.
struct StageContentView: View {
    let tile: TileID?
    let theme: ShellTheme

    var body: some View {
        switch tile {
        case .none:
            emptyStage
        case .some(.drive):
            DriveStageView(theme: theme)
        case .some(.media):
            MediaStageView(theme: theme)
        case .some(.comms):
            CommsStageView(theme: theme)
        case .some(.home):
            HomeStageView(theme: theme)
        }
    }

    /// Nothing is engaged: the wallpaper is the view (ADR-0010).
    private var emptyStage: some View {
        VStack(spacing: 6) {
            Text("OMARCHY AUTO")
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundColor(theme.dim)
            Text("TAP A RAIL TILE TO ENGAGE · TAP AGAIN TO CLEAR")
                .font(.system(size: 9, weight: .regular, design: .monospaced))
                .foregroundColor(theme.dim.opacity(0.8))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - 01 · DRIVE

struct DriveStageView: View {
    let theme: ShellTheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("01 · DRIVE")
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundColor(theme.accent)
            Text("MAP · omarchy-h7w.8 PENDING")
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundColor(theme.dim)
            Text(
                "The Omarchy map, routing and guidance arrive with the routing "
                    + "increment (omarchy-h7w.8 / .9). No mock map is drawn here."
            )
            .font(.system(size: 10, design: .monospaced))
            .foregroundColor(theme.dim.opacity(0.9))
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

// MARK: - 02 · MEDIA

struct MediaStageView: View {
    let theme: ShellTheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("02 · MEDIA")
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundColor(theme.accent)
            Text("PLAYBACK VIA LINKS · omarchy-h7w.7 PENDING")
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundColor(theme.dim)
            Text(
                "iOS has no universal now-playing API (ADR-0017 cut-list). This "
                    + "stage will offer media links first; provider-specific playback "
                    + "is a separate, later authorization."
            )
            .font(.system(size: 10, design: .monospaced))
            .foregroundColor(theme.dim.opacity(0.9))
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

// MARK: - 03 · COMMS

struct CommsStageView: View {
    let theme: ShellTheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("03 · COMMS")
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundColor(theme.accent)
            Text("NO IN-APP INBOX ON iOS")
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundColor(theme.dim)
            Text(
                "iOS does not expose third-party notifications (ADR-0017 "
                    + "cut-list). Messaging stays in Apple's surfaces; unavailable "
                    + "actions are hidden here, not faked."
            )
            .font(.system(size: 10, design: .monospaced))
            .foregroundColor(theme.dim.opacity(0.9))
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

// MARK: - 04 · HOME · SETUP

struct HomeStageView: View {
    let theme: ShellTheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("04 · HOME · SETUP")
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundColor(theme.accent)

            HStack(spacing: 8) {
                Text("TOKYO NIGHT · ACTIVE")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(theme.foreground)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(theme.accent.opacity(0.16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .strokeBorder(theme.accent, lineWidth: 1)
                    )
                Text("THEMES 1 of 10 · rest after device review")
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundColor(theme.dim)
            }

            Text("WALLPAPER · Tokyo Studio — Tom Ballard, credited (ADR-0013).")
                .font(.system(size: 10, design: .monospaced))
                .foregroundColor(theme.dim.opacity(0.9))
            Text(
                "The other nine Omarchy-derived backgrounds stay out of builds "
                    + "until each image's rights are cleared (docs/ASSETS.md)."
            )
            .font(.system(size: 10, design: .monospaced))
            .foregroundColor(theme.dim.opacity(0.9))
            Text("SETUP · omarchy-h7w.6 PENDING")
                .font(.system(size: 10, weight: .semibold, design: .monospaced))
                .foregroundColor(theme.dim)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}
