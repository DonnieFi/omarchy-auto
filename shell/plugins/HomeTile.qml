import QtQuick
import QtQuick.Layouts
import "../theme"

Item {
    id: root

    ColumnLayout {
        anchors.fill: parent
        spacing: 6

        RowLayout {
            Layout.fillWidth: true

            ColumnLayout {
                spacing: 0

                Text {
                    text: "12°"
                    color: Theme.foreground
                    font.family: Theme.mono
                    font.pixelSize: Math.max(26, root.height * 0.16)
                    font.bold: true
                }

                Text {
                    text: "VANCOUVER · CLEAR"
                    color: Theme.dim
                    font.family: Theme.mono
                    font.pixelSize: 8
                }
            }

            Item { Layout.fillWidth: true }

            Text {
                text: "☾"
                color: Theme.accent
                font.pixelSize: 20
            }
        }

        Item { Layout.fillHeight: true }

        Text {
            text: "THEME"
            color: Theme.dim
            font.family: Theme.mono
            font.pixelSize: 8
            font.letterSpacing: 1
        }

        Row {
            spacing: 4

            Repeater {
                model: Theme.themes

                Rectangle {
                    id: themeChip
                    required property int index
                    required property var modelData

                    width: 54
                    height: 22
                    color: themeChip.index === Theme.themeIndex ? Theme.accent : "transparent"
                    border.width: 1
                    border.color: themeChip.index === Theme.themeIndex ? Theme.accent : Theme.border
                    radius: 1

                    Text {
                        anchors.centerIn: parent
                        text: themeChip.modelData.name.split("-")[0].toUpperCase()
                        color: themeChip.index === Theme.themeIndex ? Theme.background : Theme.dim
                        font.family: Theme.mono
                        font.pixelSize: 7
                    }

                    MouseArea {
                        anchors.fill: parent
                        onClicked: Theme.themeIndex = themeChip.index
                    }
                }
            }
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: 6

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 24
                color: "transparent"
                border.width: 1
                border.color: Theme.border
                radius: 2

                Text {
                    anchors.centerIn: parent
                    text: "GARAGE"
                    color: Theme.foreground
                    font.family: Theme.mono
                    font.pixelSize: 8
                    font.letterSpacing: 1
                }

                MouseArea { anchors.fill: parent }
            }

            Rectangle {
                Layout.fillWidth: true
                Layout.preferredHeight: 24
                color: "transparent"
                border.width: 1
                border.color: Theme.border
                radius: 2

                Text {
                    anchors.centerIn: parent
                    text: "CAM"
                    color: Theme.foreground
                    font.family: Theme.mono
                    font.pixelSize: 8
                    font.letterSpacing: 1
                }

                MouseArea { anchors.fill: parent }
            }
        }

        Text {
            text: "HA · 3 SCENES"
            color: Theme.dim
            font.family: Theme.mono
            font.pixelSize: 8
        }
    }
}
