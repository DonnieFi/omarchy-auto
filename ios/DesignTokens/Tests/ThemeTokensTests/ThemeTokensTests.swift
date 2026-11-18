import XCTest
@testable import ThemeTokens

/// Decodes the canonical design/omarchy-themes.json committed at the repo
/// root — the same bytes the Linux design-tokens gate verifies against
/// Theme.kt — through the staged Swift decoding contract.
final class ThemeTokensTests: XCTestCase {

    private func tokensURL() -> URL {
        // This file: <repo>/ios/DesignTokens/Tests/ThemeTokensTests/ThemeTokensTests.swift
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // ThemeTokensTests/
            .deletingLastPathComponent() // Tests/
            .deletingLastPathComponent() // DesignTokens/
            .deletingLastPathComponent() // ios/
            .deletingLastPathComponent() // repo root
            .appendingPathComponent("design/omarchy-themes.json")
    }

    func testAllTenThemesDecodeInDeclarationOrder() throws {
        let file = try ThemeTokens.load(contentsOf: tokensURL())
        XCTAssertEqual(file.schema, 1)
        XCTAssertEqual(file.colorFormat, "#AARRGGBB")
        XCTAssertEqual(file.themes.count, 10)
        XCTAssertEqual(
            file.themes.map(\.id),
            [
                "tokyo-night", "osaka-jade", "gruvbox", "catppuccin", "kanagawa",
                "everforest", "nord", "ethereal", "ristretto", "hackerman",
            ]
        )
    }

    func testTokyoNightCarriesTheKnownValues() throws {
        let file = try ThemeTokens.load(contentsOf: tokensURL())
        let tokyo = try XCTUnwrap(file.themes.first { $0.id == "tokyo-night" })
        XCTAssertEqual(tokyo.name, "TOKYO NIGHT")
        XCTAssertEqual(tokyo.shortName, "TOKYO")
        XCTAssertEqual(tokyo.background, "#FF1A1B26")
        XCTAssertEqual(tokyo.scrimAlpha, 89)
        XCTAssertEqual(tokyo.stageAlpha, 133)
    }

    func testEveryColorParsesWithFullOpacity() throws {
        let file = try ThemeTokens.load(contentsOf: tokensURL())
        for theme in file.themes {
            XCTAssertEqual(theme.colors.count, 10, "\(theme.id) must carry ten colors")
            for hex in theme.colors {
                let components = try XCTUnwrap(ThemeToken.components(ofHex: hex), "\(theme.id): \(hex)")
                XCTAssertEqual(components.a, 255, "\(theme.id): \(hex) must be fully opaque")
            }
        }
    }

    func testKnownArgbComponents() throws {
        let components = try XCTUnwrap(ThemeToken.components(ofHex: "#FF1A1B26"))
        XCTAssertEqual(components.a, 0xFF)
        XCTAssertEqual(components.r, 0x1A)
        XCTAssertEqual(components.g, 0x1B)
        XCTAssertEqual(components.b, 0x26)
    }

    func testValidateRejectsBadColorsAndOutOfRangeAlphas() throws {
        let good = try ThemeTokens.load(contentsOf: tokensURL())
        let sample = good.themes[0]

        let badColor = ThemeToken(
            id: sample.id, name: sample.name, shortName: sample.shortName,
            background: "#12345", surface: sample.surface, surfaceAlt: sample.surfaceAlt,
            foreground: sample.foreground, dim: sample.dim, border: sample.border,
            accent: sample.accent, green: sample.green, yellow: sample.yellow,
            red: sample.red, scrimAlpha: sample.scrimAlpha, stageAlpha: sample.stageAlpha
        )
        let badColorFile = ThemeTokenFile(
            schema: good.schema, source: good.source,
            colorFormat: good.colorFormat, alphaRange: good.alphaRange,
            themes: [badColor]
        )
        XCTAssertThrowsError(try ThemeTokens.validate(badColorFile)) { error in
            XCTAssertEqual(
                error as? ThemeTokenError,
                ThemeTokenError.invalidColor("#12345", themeID: sample.id)
            )
        }

        let badAlpha = ThemeToken(
            id: sample.id, name: sample.name, shortName: sample.shortName,
            background: sample.background, surface: sample.surface, surfaceAlt: sample.surfaceAlt,
            foreground: sample.foreground, dim: sample.dim, border: sample.border,
            accent: sample.accent, green: sample.green, yellow: sample.yellow,
            red: sample.red, scrimAlpha: 300, stageAlpha: sample.stageAlpha
        )
        let badAlphaFile = ThemeTokenFile(
            schema: good.schema, source: good.source,
            colorFormat: good.colorFormat, alphaRange: good.alphaRange,
            themes: [badAlpha]
        )
        XCTAssertThrowsError(try ThemeTokens.validate(badAlphaFile)) { error in
            XCTAssertEqual(
                error as? ThemeTokenError,
                ThemeTokenError.alphaOutOfRange(300, themeID: sample.id)
            )
        }
    }
}
