import QtQuick
import QtQuick.Layouts
import "../theme"

Item {
    id: root

    ColumnLayout {
        anchors.fill: parent
        spacing: 4

        RowLayout {
            Layout.fillWidth: true
            spacing: 8

            Text {
                text: "0"
                color: Theme.foreground
                font.family: Theme.mono
                font.pixelSize: Math.max(34, root.height * 0.22)
                font.bold: true
            }

            ColumnLayout {
                spacing: 0

                Text {
                    text: "km/h"
                    color: Theme.dim
                    font.family: Theme.mono
                    font.pixelSize: 9
                }

                Text {
                    text: "GEAR · N"
                    color: Theme.accent
                    font.family: Theme.mono
                    font.pixelSize: 8
                }
            }

            Item { Layout.fillWidth: true }

            ColumnLayout {
                spacing: 0

                Text {
                    text: "RANGE"
                    color: Theme.dim
                    font.family: Theme.mono
                    font.pixelSize: 8
                    horizontalAlignment: Text.AlignRight
                }

                Text {
                    text: "412 km"
                    color: Theme.green
                    font.family: Theme.mono
                    font.pixelSize: Math.max(12, root.height * 0.07)
                    font.bold: true
                    horizontalAlignment: Text.AlignRight
                }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            height: 6
            color: Theme.border

            Rectangle {
                width: parent.width * 0.28
                height: parent.height
                color: Theme.accent
            }
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: 12

            Text {
                text: "COOLANT 88°"
                color: Theme.foreground
                font.family: Theme.mono
                font.pixelSize: 9
            }

            Text {
                text: "FUEL 62%"
                color: Theme.foreground
                font.family: Theme.mono
                font.pixelSize: 9
            }

            Text {
                text: "12.4 V"
                color: Theme.foreground
                font.family: Theme.mono
                font.pixelSize: 9
            }

            Item { Layout.fillWidth: true }

            Text {
                text: "OBD · MOCK"
                color: Theme.green
                font.family: Theme.mono
                font.pixelSize: 8
            }
        }

        Item { Layout.fillHeight: true }

        Text {
            text: "DTC · NONE  ·  ELM327 / 1 Hz"
            color: Theme.dim
            font.family: Theme.mono
            font.pixelSize: 8
        }
    }
}
