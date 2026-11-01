import QtQuick
import QtQuick.Layouts
import "../theme"

Item {
    id: root
    clip: true

    Canvas {
        id: map
        anchors.fill: parent

        onPaint: {
            var ctx = getContext("2d")
            ctx.clearRect(0, 0, width, height)

            ctx.fillStyle = Theme.surfaceAlt
            ctx.fillRect(0, 0, width, height)

            // Street grid
            ctx.strokeStyle = Qt.rgba(1, 1, 1, 0.055)
            ctx.lineWidth = 1
            var step = Math.max(40, width / 14)
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

            // City blocks
            ctx.fillStyle = Qt.rgba(1, 1, 1, 0.035)
            var blocks = [
                [0.05, 0.10, 0.18, 0.15],
                [0.30, 0.06, 0.14, 0.20],
                [0.52, 0.12, 0.20, 0.14],
                [0.78, 0.08, 0.14, 0.18],
                [0.08, 0.40, 0.16, 0.12],
                [0.34, 0.38, 0.18, 0.16],
                [0.62, 0.36, 0.12, 0.12],
                [0.80, 0.44, 0.12, 0.16],
                [0.14, 0.62, 0.14, 0.14],
                [0.40, 0.62, 0.20, 0.10],
                [0.70, 0.66, 0.16, 0.12]
            ]
            for (var i = 0; i < blocks.length; i++) {
                var b = blocks[i]
                ctx.fillRect(width * b[0], height * b[1], width * b[2], height * b[3])
            }

            // Route
            ctx.strokeStyle = Theme.accent
            ctx.lineWidth = Math.max(3, width * 0.006)
            ctx.lineCap = "round"
            ctx.lineJoin = "round"
            ctx.beginPath()
            ctx.moveTo(width * 0.14, height * 0.66)
            ctx.lineTo(width * 0.30, height * 0.50)
            ctx.lineTo(width * 0.46, height * 0.56)
            ctx.lineTo(width * 0.60, height * 0.36)
            ctx.lineTo(width * 0.84, height * 0.24)
            ctx.stroke()

            // Car marker
            var cx = width * 0.14
            var cy = height * 0.66
            ctx.strokeStyle = Qt.rgba(0.62, 0.81, 0.42, 0.35)
            ctx.lineWidth = 2
            ctx.beginPath()
            ctx.arc(cx, cy, Math.max(10, width * 0.022), 0, Math.PI * 2)
            ctx.stroke()
            ctx.fillStyle = Theme.green
            ctx.beginPath()
            ctx.arc(cx, cy, Math.max(5, width * 0.010), 0, Math.PI * 2)
            ctx.fill()

            // Destination
            ctx.strokeStyle = Theme.yellow
            ctx.lineWidth = 2
            ctx.beginPath()
            ctx.arc(width * 0.84, height * 0.24, Math.max(6, width * 0.012), 0, Math.PI * 2)
            ctx.stroke()
        }

        onWidthChanged: requestPaint()
        onHeightChanged: requestPaint()

        Connections {
            target: Theme
            function onThemeIndexChanged() { map.requestPaint() }
        }
    }

    Text {
        anchors.left: parent.left
        anchors.top: parent.top
        text: "N  ·  MAP / OSM"
        color: Theme.dim
        font.family: Theme.mono
        font.pixelSize: 9
        font.letterSpacing: 1
    }

    Rectangle {
        anchors.right: parent.right
        anchors.top: parent.top
        width: 150
        height: 54
        color: Qt.rgba(0, 0, 0, 0.55)
        border.width: 1
        border.color: Theme.border
        radius: 2

        Column {
            anchors.centerIn: parent
            spacing: 1

            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: "14 MIN"
                color: Theme.green
                font.family: Theme.mono
                font.pixelSize: 18
                font.bold: true
            }

            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: "6.4 KM  ·  21:58"
                color: Theme.dim
                font.family: Theme.mono
                font.pixelSize: 9
            }
        }
    }

    Rectangle {
        anchors.left: parent.left
        anchors.bottom: parent.bottom
        width: Math.min(parent.width * 0.66, 390)
        height: Math.min(parent.height * 0.30, 108)
        color: Qt.rgba(0, 0, 0, 0.62)
        border.width: 2
        border.color: Theme.accent
        radius: 2

        RowLayout {
            anchors.fill: parent
            anchors.margins: 10
            spacing: 10

            Text {
                text: "↰"
                color: Theme.accent
                font.family: Theme.mono
                font.pixelSize: Math.max(28, parent.height * 0.44)
                font.bold: true
            }

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 1

                Text {
                    text: "MARKET ST"
                    color: Theme.foreground
                    font.family: Theme.mono
                    font.pixelSize: Math.max(13, parent.height * 0.16)
                    font.bold: true
                    elide: Text.ElideRight
                    Layout.fillWidth: true
                }

                Text {
                    text: "in 300 m"
                    color: Theme.accent
                    font.family: Theme.mono
                    font.pixelSize: Math.max(11, parent.height * 0.12)
                }

                Text {
                    text: "then 1.2 km · Howe Ave"
                    color: Theme.dim
                    font.family: Theme.mono
                    font.pixelSize: 9
                    elide: Text.ElideRight
                    Layout.fillWidth: true
                }
            }
        }
    }

    Text {
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        text: "GPS · 6 SAT"
        color: Theme.dim
        font.family: Theme.mono
        font.pixelSize: 9
    }
}
