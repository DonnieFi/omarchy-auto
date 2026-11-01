import QtQuick
import "../theme"

Item {
    id: tile

    property string index: "00"
    property string title: "TILE"
    property string meta: ""
    property bool active: false

    default property alias content: body.data
    signal expandRequested()

    Rectangle {
        anchors.fill: parent
        color: Theme.surface
        border.width: 2
        border.color: tile.active ? Theme.accent : Theme.border
        radius: Theme.radius
    }

    // btop-style cut-out legend: a gap in the top border with the tile title.
    Rectangle {
        x: 14
        y: -1
        width: legend.implicitWidth + 18
        height: 20
        color: Theme.background

        Text {
            id: legend
            anchors.centerIn: parent
            text: "[" + tile.index + " · " + tile.title + "]"
            color: tile.active ? Theme.accent : Theme.dim
            font.family: Theme.mono
            font.pixelSize: 11
            font.letterSpacing: 1
        }
    }

    Text {
        anchors.right: expandBtn.left
        anchors.rightMargin: 10
        anchors.top: parent.top
        anchors.topMargin: 8
        text: tile.meta
        color: Theme.dim
        font.family: Theme.mono
        font.pixelSize: 10
    }

    Text {
        id: expandBtn
        anchors.right: parent.right
        anchors.rightMargin: 10
        anchors.top: parent.top
        anchors.topMargin: 4
        text: "↗"
        color: Theme.dim
        font.family: Theme.mono
        font.pixelSize: 15

        MouseArea {
            anchors.fill: parent
            anchors.margins: -6
            onClicked: tile.expandRequested()
        }
    }

    Item {
        id: body
        anchors.fill: parent
        anchors.leftMargin: 12
        anchors.rightMargin: 12
        anchors.topMargin: 26
        anchors.bottomMargin: 12
    }
}
