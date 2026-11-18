// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DesignTokens",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        // The library the future Xcode app/widget targets link (omarchy-h7w.6/.3).
        .library(name: "ThemeTokens", targets: ["ThemeTokens"]),
    ],
    targets: [
        .target(name: "ThemeTokens"),
        .testTarget(name: "ThemeTokensTests", dependencies: ["ThemeTokens"]),
    ]
)
