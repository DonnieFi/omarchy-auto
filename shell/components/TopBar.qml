pragma ComponentBehavior: Bound

import QtQuick
import QtQuick.Layouts
import "../theme"

Rectangle {
    id: bar

    implicitHeight: 48
    color: Theme.surface

    property string clockText: Qt.formatDateTime(new Date(), "HH:mm")
    property int activeTile: 0
    readonly property var tileNames: ["DRIVE", "MEDIA", "COMMS", "VEHICLE", "HOME"]

    Timer {
        interval: 1000
        running: true
        repeat: true
        onTriggered: bar.clockText = Qt.formatDateTime(new Date(), "HH:mm")
    }

    Rectangle {
        anchors.bottom: parent.bottom
        width: parent.width
        height: 1
        color: Theme.border
    }

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 16
        anchors.rightMargin: 16
        spacing: 14

        Text {
            text: "OMARCHY"
            color: Theme.accent
            font.family: Theme.mono
            font.pixelSize: 14
            font.bold: true
            font.letterSpacing: 2
        }

        Text {
            text: "// " + bar.tileNames[Math.max(0, bar.activeTile)]
            color: Theme.dim
            font.family: Theme.mono
            font.pixelSize: 11
            font.letterSpacing: 1
        }

        Row {
            spacing: 5

            Repeater {
                model: 5

                Rectangle {
                    id: navChip
                    required property int index

                    width: 20
                    height: 18
                    color: navChip.index === bar.activeTile ? Theme.accent : "transparent"
                    border.width: 1
                    border.color: navChip.index === bar.activeTile ? Theme.accent : Theme.border
                    radius: 1

                    Text {
                        anchors.centerIn: parent
                        text: navChip.index + 1
                        color: navChip.index === bar.activeTile ? Theme.background : Theme.dim
                        font.family: Theme.mono
                        font.pixelSize: 10
                    }
                }
            }
        }

        Item { Layout.fillWidth: true }

        Text {
            text: bar.clockText
            color: Theme.foreground
            font.family: Theme.mono
            font.pixelSize: 16
            font.bold: true
        }

        Text {
            text: "12° CLEAR"
            color: Theme.dim
            font.family: Theme.mono
            font.pixelSize: 10
        }

        Text {
            text: "▂▄▆"
            color: Theme.green
            font.family: Theme.mono
            font.pixelSize: 11
        }

        Text {
            text: "84%"
            color: Theme.foreground
            font.family: Theme.mono
            font.pixelSize: 10
        }

        Text {
            text: "VOL 12"
            color: Theme.dim
            font.family: Theme.mono
            font.pixelSize: 10
        }

        Rectangle {
            width: 28
            height: 20
            color: Theme.surfaceAlt
            border.width: 1
            border.color: Theme.border
            radius: 1

            Text {
                anchors.centerIn: parent
                text: "◐"
                color: Theme.accent
                font.pixelSize: 12
            }

            MouseArea {
                anchors.fill: parent
                onClicked: Theme.nextTheme()
            }
        }
    }
}
