import SwiftUI
import ThemeTokens
import XCTest
@testable import OmarchyAuto

/// Hosted in the app: Bundle.main is the real OmarchyAuto bundle, so these
/// tests exercise the exact runtime paths (token decode, wallpaper staging).
final class ShellTests: XCTestCase {

    // MARK: Rail

    func testRailTilesMatchAndroidOrder() {
        XCTAssertEqual(TileID.allCases.map(\.rawValue), ["drive", "media", "comms", "home"])
        XCTAssertEqual(TileID.allCases.map(\.number), [1, 2, 3, 4])
        XCTAssertEqual(TileID.drive.legend, "[01 · DRIVE]")
        XCTAssertEqual(TileID.home.legend, "[04 · HOME]")
    }

    func testTapEngagesThenClears() {
        var shell = ShellModel()
        XCTAssertNil(shell.engaged, "the app opens with every tile minimized (ADR-0010)")
        shell.tap(.drive)
        XCTAssertEqual(shell.engaged, .drive)
        shell.tap(.drive)
        XCTAssertNil(shell.engaged, "tapping the engaged tile again clears the stage")
        shell.tap(.home)
        XCTAssertEqual(shell.engaged, .home)
        shell.tap(.media)
        XCTAssertEqual(shell.engaged, .media, "a new tap switches the engaged tile")
    }

    // MARK: Tokens — the bundle-load path the app uses at runtime

    func testAppBundleCarriesTheCanonicalTokensAndDecodesThem() throws {
        let file = try ThemeTokens.load(from: .main)
        XCTAssertEqual(file.schema, 1)
        let tokyo = try XCTUnwrap(file.themes.first { $0.id == "tokyo-night" })
        XCTAssertEqual(tokyo.name, "TOKYO NIGHT")
        XCTAssertEqual(tokyo.background, "#FF1A1B26")
        XCTAssertEqual(tokyo.scrimAlpha, 89)
        XCTAssertEqual(tokyo.stageAlpha, 133)
    }

    func testShellThemeProjectsTheTokyoNightPalette() throws {
        let theme = ShellTheme.tokyoNight(from: .main)
        XCTAssertTrue(theme.tokensLoaded, "the app bundle must carry decodable tokens")
        XCTAssertEqual(theme.token.shortName, "TOKYO")

        // Foreground #FFC0CAF5 → C0/CA/F5 at full opacity.
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        UIColor(theme.foreground).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        XCTAssertEqual(Double(red), 0xC0 / 255.0, accuracy: 0.005)
        XCTAssertEqual(Double(green), 0xCA / 255.0, accuracy: 0.005)
        XCTAssertEqual(Double(blue), 0xF5 / 255.0, accuracy: 0.005)
        XCTAssertEqual(Double(alpha), 1.0, accuracy: 0.005)

        XCTAssertEqual(theme.scrimOpacity, 89.0 / 255.0, accuracy: 1e-9)
        XCTAssertEqual(theme.stageOpacity, 133.0 / 255.0, accuracy: 1e-9)
    }

    // MARK: Assets — the one cleared wallpaper (ADR-0013)

    func testBundleCarriesTheClearedWallpaper() throws {
        let url = try XCTUnwrap(
            Bundle.main.url(forResource: "tokyo-night", withExtension: "webp"),
            "tokyo-night.webp must be staged in the app bundle (explicit public list)"
        )
        XCTAssertNotNil(UIImage(contentsOfFile: url.path))
    }

    // MARK: Honesty (ADR-0009)

    func testBatteryTextIsHonestWhenUnknown() {
        XCTAssertEqual(ShellView.batteryText(level: -1), "WAIT", "unknown battery is never faked")
        XCTAssertEqual(ShellView.batteryText(level: 0.42), "42%")
        XCTAssertEqual(ShellView.batteryText(level: 1), "100%")
    }

    func testClockTextUses24HourFormat() {
        var components = DateComponents()
        components.year = 2026
        components.month = 11
        components.day = 18
        components.hour = 9
        components.minute = 5
        let date = Calendar.current.date(from: components)!
        XCTAssertEqual(ShellView.clockText(date), "09:05")
    }
}
