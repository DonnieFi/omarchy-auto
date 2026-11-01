import QtQuick
import QtQuick.Layouts
import "../theme"

Item {
    id: root

    property var messages: [
        { app: "SIGNAL", from: "Maya", text: "Running 5 min late", time: "21:38", unread: true },
        { app: "WHATSAPP", from: "Dad", text: "Call me when you park", time: "21:31", unread: true },
        { app: "CALENDAR", from: "Tomorrow", text: "Dentist · 09:30", time: "20:00", unread: false }
    ]

    ColumnLayout {
        anchors.fill: parent
        spacing: 6

        Repeater {
            model: root.messages

            Rectangle {
                id: messageCard
                required property var modelData

                Layout.fillWidth: true
                Layout.preferredHeight: 46
                color: messageCard.modelData.unread ? Theme.surfaceAlt : "transparent"
                border.width: 1
                border.color: messageCard.modelData.unread ? Theme.border : "transparent"
                radius: 2

                RowLayout {
                    anchors.fill: parent
                    anchors.margins: 7
                    spacing: 8

                    Rectangle {
                        Layout.fillHeight: true
                        Layout.preferredWidth: 3
                        color: messageCard.modelData.unread ? Theme.accent : Theme.border
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 1

                        RowLayout {
                            Layout.fillWidth: true

                            Text {
                                text: messageCard.modelData.app
                                color: messageCard.modelData.unread ? Theme.accent : Theme.dim
                                font.family: Theme.mono
                                font.pixelSize: 8
                                font.bold: true
                                font.letterSpacing: 1
                            }

                            Item { Layout.fillWidth: true }

                            Text {
                                text: messageCard.modelData.time
                                color: Theme.dim
                                font.family: Theme.mono
                                font.pixelSize: 8
                            }
                        }

                        Text {
                            text: messageCard.modelData.from + " · " + messageCard.modelData.text
                            color: Theme.foreground
                            font.family: Theme.mono
                            font.pixelSize: 10
                            elide: Text.ElideRight
                            Layout.fillWidth: true
                        }
                    }
                }
            }
        }

        Item { Layout.fillHeight: true }

        Rectangle {
            Layout.fillWidth: true
            Layout.preferredHeight: 24
            color: "transparent"
            border.width: 1
            border.color: Theme.border
            radius: 2

            Text {
                anchors.centerIn: parent
                text: "QUICK REPLY  ·  HOLD TO DICTATE"
                color: Theme.dim
                font.family: Theme.mono
                font.pixelSize: 8
                font.letterSpacing: 1
            }

            MouseArea { anchors.fill: parent }
        }
    }
}
