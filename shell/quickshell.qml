import QtQuick
import Quickshell
import "theme"

ShellRoot {
    FloatingWindow {
        title: "Omarchy Auto"
        implicitWidth: 1280
        implicitHeight: 800
        color: Theme.background

        CarShell {
            anchors.fill: parent
        }
    }
}
