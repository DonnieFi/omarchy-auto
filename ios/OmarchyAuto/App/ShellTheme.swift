import SwiftUI
import ThemeTokens

/// One decoded theme token projected onto SwiftUI colors. This increment
/// ships a single working theme — Tokyo Night, the visual anchor — per the
/// omarchy-h7w.6 review amendment; the remaining palettes join after device
/// review.
struct ShellTheme {
    let token: ThemeToken
    let tokensLoaded: Bool

    /// Honest fallback when the tokens resource is missing from the bundle:
    /// flat Tokyo-Night-ish dark values and a visible TOKENS MISSING label
    /// (ADR-0009 — never invent data).
    static let missingTokens = ShellTheme(
        token: ThemeToken(
            id: "missing",
            name: "MISSING",
            shortName: "NONE",
            background: "#FF1A1B26",
            surface: "#FF131418",
            surfaceAlt: "#FF24283B",
            foreground: "#FFC0CAF5",
            dim: "#FF8A93B8",
            border: "#FF292E42",
            accent: "#FF7AA2F7",
            green: "#FF9ECE6A",
            yellow: "#FFE0AF68",
            red: "#FFF7768E",
            scrimAlpha: 89,
            stageAlpha: 133
        ),
        tokensLoaded: false
    )

    /// Loads the Tokyo Night token from a bundle's omarchy-themes.json —
    /// the same bytes the Linux design-tokens gate verifies against Theme.kt.
    static func tokyoNight(from bundle: Bundle = .main) -> ShellTheme {
        guard let file = try? ThemeTokens.load(from: bundle),
              let tokyo = file.themes.first(where: { $0.id == "tokyo-night" })
        else { return .missingTokens }
        return ShellTheme(token: tokyo, tokensLoaded: true)
    }

    var background: Color { color(token.background) }
    var surface: Color { color(token.surface) }
    var surfaceAlt: Color { color(token.surfaceAlt) }
    var foreground: Color { color(token.foreground) }
    var dim: Color { color(token.dim) }
    var border: Color { color(token.border) }
    var accent: Color { color(token.accent) }
    var green: Color { color(token.green) }
    var yellow: Color { color(token.yellow) }
    var red: Color { color(token.red) }

    var scrimOpacity: Double { Double(token.scrimAlpha) / 255.0 }
    var stageOpacity: Double { Double(token.stageAlpha) / 255.0 }

    private func color(_ hex: String) -> Color {
        guard let c = ThemeToken.components(ofHex: hex) else { return .black }
        return Color(
            .sRGB,
            red: Double(c.r) / 255.0,
            green: Double(c.g) / 255.0,
            blue: Double(c.b) / 255.0,
            opacity: Double(c.a) / 255.0
        )
    }
}
