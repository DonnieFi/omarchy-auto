// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DesignTokens",
    platforms: [.iOS(.v16), .macOS(.v13)],
    targets: [
        .target(name: "ThemeTokens"),
        .testTarget(name: "ThemeTokensTests", dependencies: ["ThemeTokens"]),
    ]
)
