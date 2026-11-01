import QtQuick
import QtQuick.Layouts
import "../theme"

Item {
    id: root

    property real artSize: Math.max(48, Math.min(root.width * 0.34, root.height * 0.62))

    RowLayout {
        anchors.fill: parent
        spacing: 12

        Rectangle {
            Layout.preferredWidth: root.artSize
            Layout.preferredHeight: root.artSize
            Layout.alignment: Qt.AlignVCenter
            color: Theme.surfaceAlt
            border.width: 1
            border.color: Theme.border
            radius: 2
            gradient: Gradient {
                GradientStop { position: 0.0; color: "#3b2f63" }
                GradientStop { position: 1.0; color: "#12131c" }
            }

            Text {
                anchors.centerIn: parent
                text: "M83"
                color: Theme.foreground
                font.family: Theme.mono
                font.pixelSize: Math.max(14, parent.width * 0.13)
                font.bold: true
            }

            Text {
                anchors.right: parent.right
                anchors.bottom: parent.bottom
                anchors.margins: 6
                text: "ALBUM"
                color: Theme.dim
                font.family: Theme.mono
                font.pixelSize: 8
            }
        }

        ColumnLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 4

            Text {
                text: "MIDNIGHT CITY"
                color: Theme.foreground
                font.family: Theme.mono
                font.pixelSize: Math.max(12, root.height * 0.055)
                font.bold: true
                elide: Text.ElideRight
                Layout.fillWidth: true
            }

            Text {
                text: "M83 · HURRY UP, WE'RE DREAMING"
                color: Theme.dim
                font.family: Theme.mono
                font.pixelSize: 9
                elide: Text.ElideRight
                Layout.fillWidth: true
            }

            Item { Layout.fillHeight: true }

            Rectangle {
                Layout.fillWidth: true
                height: 4
                color: Theme.border

                Rectangle {
                    width: parent.width * 0.42
                    height: parent.height
                    color: Theme.accent
                }
            }

            RowLayout {
                Layout.fillWidth: true

                Text {
                    text: "01:42"
                    color: Theme.dim
                    font.family: Theme.mono
                    font.pixelSize: 8
                }

                Item { Layout.fillWidth: true }

                Text {
                    text: "-02:21"
                    color: Theme.dim
                    font.family: Theme.mono
                    font.pixelSize: 8
                }
            }

            RowLayout {
                Layout.fillWidth: true
                spacing: 10

                Text {
                    text: "⏮"
                    color: Theme.dim
                    font.pixelSize: Math.max(14, root.height * 0.06)
                }

                Rectangle {
                    width: 42
                    height: 28
                    color: Theme.accent
                    radius: 2

                    Text {
                        anchors.centerIn: parent
                        text: "⏸"
                        color: Theme.background
                        font.pixelSize: 15
                    }

                    MouseArea { anchors.fill: parent }
                }

                Text {
                    text: "⏭"
                    color: Theme.dim
                    font.pixelSize: Math.max(14, root.height * 0.06)
                }

                Item { Layout.fillWidth: true }

                Text {
                    text: "♡"
                    color: Theme.dim
                    font.pixelSize: 14
                }
            }

            Text {
                text: "SOURCE · PHONE / SPOTIFY"
                color: Theme.dim
                font.family: Theme.mono
                font.pixelSize: 8
            }
        }
    }
}
