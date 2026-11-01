pragma Singleton
// QtQuick, not QtQml: Qt 6.12's QML runtime only registers value types such as
// `color` through the QtQuick import.
import QtQuick

// Palette values follow Omarchy's MIT themes (tokyo-night, osaka-jade,
// gruvbox). Theme names are references; no upstream QML files are copied here.
QtObject {
    id: root

    property int themeIndex: 0

    readonly property var themes: [
        {
            name: "tokyo-night",
            background: "#1a1b26",
            surface: "#16161e",
            surfaceAlt: "#1f2335",
            foreground: "#c0caf5",
            dim: "#565f89",
            border: "#2f3549",
            accent: "#7aa2f7",
            green: "#9ece6a",
            yellow: "#e0af68",
            red: "#f7768e"
        },
        {
            name: "osaka-jade",
            background: "#111c18",
            surface: "#0d1512",
            surfaceAlt: "#17251f",
            foreground: "#d7e5d9",
            dim: "#5f7a6c",
            border: "#22382e",
            accent: "#509475",
            green: "#8bcbb1",
            yellow: "#d4b86a",
            red: "#d16d6d"
        },
        {
            name: "gruvbox",
            background: "#1d2021",
            surface: "#161819",
            surfaceAlt: "#282828",
            foreground: "#ebdbb2",
            dim: "#928374",
            border: "#3c3836",
            accent: "#7daea3",
            green: "#a9b665",
            yellow: "#d8a657",
            red: "#ea6962"
        }
    ]

    readonly property var active: themes[themeIndex]
    readonly property string themeName: active.name
    readonly property color background: active.background
    readonly property color surface: active.surface
    readonly property color surfaceAlt: active.surfaceAlt
    readonly property color foreground: active.foreground
    readonly property color dim: active.dim
    readonly property color border: active.border
    readonly property color accent: active.accent
    readonly property color green: active.green
    readonly property color yellow: active.yellow
    readonly property color red: active.red

    readonly property string mono: "monospace"
    readonly property int radius: 2
    readonly property int gap: 12

    function nextTheme() {
        themeIndex = (themeIndex + 1) % themes.length
    }
}
