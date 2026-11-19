import SwiftUI

/// The Omarchy mounted dashboard: wallpaper, title bar, right rail of four
/// small tiles, one large stage, live footer. The visual grammar follows the
/// Android shell (ADR-0003/0010/0015): 2px borders, monospace legends,
/// one tile engages at a time, the app opens with nothing engaged.
struct ShellView: View {
    @State private var shell = ShellModel()
    @State private var theme: ShellTheme = .missingTokens
    @State private var battery = "WAIT"

    init() {
        _theme = State(initialValue: ShellTheme.tokyoNight())
        _shell = State(
            initialValue: ShellModel(engaged: Self.engagedStage(from: ProcessInfo.processInfo.arguments))
        )
    }

    /// CI screenshot hook: `-engageStage drive` pre-engages a stage so the
    /// runner can capture both the idle wallpaper view and an engaged stage
    /// without scripting taps.
    static func engagedStage(from arguments: [String]) -> TileID? {
        guard let index = arguments.firstIndex(of: "-engageStage"),
              index + 1 < arguments.count,
              let tile = TileID(rawValue: arguments[index + 1])
        else { return nil }
        return tile
    }

    var body: some View {
        ZStack {
            wallpaper
            Rectangle()
                .fill(theme.background)
                .opacity(theme.scrimOpacity)
            VStack(spacing: 8) {
                titleBar
                HStack(spacing: 8) {
                    stage
                    rail
                }
                footer
            }
            .padding(8)
        }
        .ignoresSafeArea()
        .onAppear {
            UIDevice.current.isBatteryMonitoringEnabled = true
            battery = Self.batteryText(level: UIDevice.current.batteryLevel)
        }
    }

    // MARK: Wallpaper

    /// Tom Ballard's Tokyo Studio image — the one cleared background
    /// (ADR-0013). If it is missing, a flat theme color shows instead; no
    /// fake art is ever drawn (ADR-0009). The image is proposed the exact
    /// screen size and clipped — scaledToFill alone expands the layout
    /// bounds and pushed the shell's rail off-screen (caught by the UI
    /// test: the rail button rect sat at x≈959 on a ~400pt iPhone).
    @ViewBuilder
    private var wallpaper: some View {
        GeometryReader { geo in
            if let url = Bundle.main.url(forResource: "tokyo-night", withExtension: "webp"),
               let image = UIImage(contentsOfFile: url.path) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: geo.size.width, height: geo.size.height)
                    .clipped()
            } else {
                theme.background
                    .frame(width: geo.size.width, height: geo.size.height)
            }
        }
        .ignoresSafeArea()
    }

    // MARK: Title bar

    private var titleBar: some View {
        HStack(spacing: 8) {
            Text("OMARCHY // AUTO")
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundColor(theme.foreground)
            if !theme.tokensLoaded {
                Text("· TOKENS MISSING")
                    .font(.system(size: 10, weight: .semibold, design: .monospaced))
                    .foregroundColor(theme.red)
            }
            Spacer()
            Button {
                shell.tap(.home)
            } label: {
                Text("THEME")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(theme.accent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(theme.accent.opacity(0.16))
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .strokeBorder(theme.accent, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: Stage

    private var stage: some View {
        ZStack {
            Rectangle()
                .fill(theme.surface)
                .opacity(theme.stageOpacity)
            Rectangle()
                .strokeBorder(theme.border, lineWidth: 2)
            StageContentView(tile: shell.engaged, theme: theme)
                .padding(16)
        }
        .frame(maxHeight: .infinity)
    }

    // MARK: Rail

    private var rail: some View {
        VStack(spacing: 8) {
            ForEach(TileID.allCases) { tile in
                RailTileView(
                    tile: tile,
                    engaged: shell.engaged == tile,
                    theme: theme
                ) {
                    shell.tap(tile)
                }
            }
            Spacer(minLength: 0)
        }
        .frame(width: 96)
    }

    // MARK: Footer

    private var footer: some View {
        HStack(spacing: 12) {
            TimelineView(.periodic(from: .now, by: 60)) { context in
                Text(Self.clockText(context.date))
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundColor(theme.foreground)
            }
            Spacer()
            Text("SPD — · GPS omarchy-h7w.7")
            Text("BATT \(battery)")
        }
        .font(.system(size: 10, weight: .semibold, design: .monospaced))
        .foregroundColor(theme.dim)
    }

    // MARK: Helpers

    static func clockText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    /// Battery level -1 (unknown, e.g. the simulator) is reported as WAIT,
    /// never faked as a number (ADR-0009).
    static func batteryText(level: Float) -> String {
        guard level >= 0, level <= 1 else { return "WAIT" }
        return "\(Int((level * 100).rounded()))%"
    }
}

/// One small rail tile: cut-out legend on top, tile name at the bottom,
/// 2px border, accent when engaged.
struct RailTileView: View {
    let tile: TileID
    let engaged: Bool
    let theme: ShellTheme
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 4) {
                Text(tile.legend)
                    .font(.system(size: 9, weight: .semibold, design: .monospaced))
                    .foregroundColor(engaged ? theme.accent : theme.dim)
                Spacer(minLength: 0)
                Text(tile.rawValue.uppercased())
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundColor(engaged ? theme.foreground : theme.dim)
            }
            .frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
            .padding(6)
            .background(
                Rectangle()
                    .fill(engaged ? theme.surfaceAlt.opacity(0.9) : theme.surface.opacity(theme.stageOpacity))
            )
            .overlay(
                Rectangle()
                    .strokeBorder(engaged ? theme.accent : theme.border, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("rail-\(tile.rawValue)")
    }
}
