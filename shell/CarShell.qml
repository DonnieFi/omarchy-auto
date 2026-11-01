import QtQuick
import QtQuick.Layouts
import "theme"
import "components"
import "plugins"

Item {
    id: car
    width: 1280
    height: 800

    property string expanded: ""
    property bool wide: width >= 1000
    property int columns: wide ? 4 : 2
    // Four equal columns in landscape, two in portrait. Grid margins are 12
    // and the gap is 12; the Drive tile spans two columns.
    readonly property real cellWidth: (width - 24 - (columns - 1) * 12) / columns

    Rectangle {
        anchors.fill: parent
        color: Theme.background
    }

    // Subtle blueprint grid behind the tiles.
    Canvas {
        anchors.fill: parent

        onPaint: {
            var ctx = getContext("2d")
            ctx.clearRect(0, 0, width, height)
            ctx.strokeStyle = Qt.rgba(1, 1, 1, 0.035)
            ctx.lineWidth = 1
            var step = 48
            for (var x = 0; x < width; x += step) {
                ctx.beginPath()
                ctx.moveTo(x, 0)
                ctx.lineTo(x, height)
                ctx.stroke()
            }
            for (var y = 0; y < height; y += step) {
                ctx.beginPath()
                ctx.moveTo(0, y)
                ctx.lineTo(width, y)
                ctx.stroke()
            }
        }

        onWidthChanged: requestPaint()
        onHeightChanged: requestPaint()
    }

    ColumnLayout {
        anchors.fill: parent
        spacing: 0

        TopBar {
            Layout.fillWidth: true
            Layout.preferredHeight: 48
            activeTile: car.expanded === "" ? 0 : Math.max(0, ["Drive", "Media", "Comms", "Vehicle", "Home"].indexOf(car.expanded))
        }

        GridLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.margins: 12
            columns: car.columns
            columnSpacing: 12
            rowSpacing: 12

            Tile {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.columnSpan: 2
                Layout.rowSpan: car.wide ? 2 : 1
                Layout.preferredWidth: car.cellWidth * 2 + 12
                index: "01"
                title: "DRIVE"
                meta: "AA · OFF"
                active: true
                onExpandRequested: car.expanded = "Drive"

                DriveTile { anchors.fill: parent }
            }

            Tile {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredWidth: car.cellWidth
                index: "02"
                title: "MEDIA"
                meta: "SPOTIFY"
                onExpandRequested: car.expanded = "Media"

                MediaTile { anchors.fill: parent }
            }

            Tile {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredWidth: car.cellWidth
                index: "03"
                title: "COMMS"
                meta: "2 NEW"
                onExpandRequested: car.expanded = "Comms"

                CommsTile { anchors.fill: parent }
            }

            Tile {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredWidth: car.cellWidth
                index: "04"
                title: "VEHICLE"
                meta: "OBD · MOCK"
                onExpandRequested: car.expanded = "Vehicle"

                VehicleTile { anchors.fill: parent }
            }

            Tile {
                Layout.fillWidth: true
                Layout.fillHeight: true
                Layout.preferredWidth: car.cellWidth
                index: "05"
                title: "HOME"
                meta: "12°"
                onExpandRequested: car.expanded = "Home"

                HomeTile { anchors.fill: parent }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 26
            color: Theme.surface

            Rectangle {
                anchors.top: parent.top
                width: parent.width
                height: 1
                color: Theme.border
            }

            RowLayout {
                anchors.fill: parent
                anchors.leftMargin: 16
                anchors.rightMargin: 16
                spacing: 16

                Text {
                    text: "MOCK DATA  ·  BRIDGE · PHONE"
                    color: Theme.yellow
                    font.family: Theme.mono
                    font.pixelSize: 9
                }

                Text {
                    text: "GPS · 6 SAT"
                    color: Theme.dim
                    font.family: Theme.mono
                    font.pixelSize: 9
                }

                Text {
                    text: "OBD · MOCK · 1 Hz"
                    color: Theme.dim
                    font.family: Theme.mono
                    font.pixelSize: 9
                }

                Item { Layout.fillWidth: true }

                Text {
                    text: "THEME · " + Theme.themeName.toUpperCase()
                    color: Theme.accent
                    font.family: Theme.mono
                    font.pixelSize: 9
                }
            }
        }
    }

    // Expanded tile overlay.
    Rectangle {
        anchors.fill: parent
        visible: car.expanded !== ""
        color: Qt.rgba(0, 0, 0, 0.74)

        MouseArea {
            anchors.fill: parent
            onClicked: car.expanded = ""
        }

        Rectangle {
            anchors.centerIn: parent
            width: parent.width * 0.88
            height: parent.height * 0.86
            color: Theme.surface
            border.width: 2
            border.color: Theme.accent
            radius: Theme.radius

            MouseArea { anchors.fill: parent }

            Loader {
                anchors.fill: parent
                anchors.margins: 28
                source: car.expanded ? "plugins/" + car.expanded + "Tile.qml" : ""
            }

            Text {
                anchors.left: parent.left
                anchors.top: parent.top
                anchors.margins: 10
                text: "← BACK"
                color: Theme.accent
                font.family: Theme.mono
                font.pixelSize: 12

                MouseArea {
                    anchors.fill: parent
                    onClicked: car.expanded = ""
                }
            }
        }
    }
}
