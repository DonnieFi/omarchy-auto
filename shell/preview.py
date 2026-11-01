#!/usr/bin/env python3
"""Run the Omarchy Auto shell without qmlscene.

This is a convenience runner for machines that only have PySide6. It loads the
same shell/main.qml with a QQuickView, so it stays a plain Qt Quick app.

    python3 shell/preview.py
    python3 shell/preview.py --size 800x1280
    python3 shell/preview.py --expanded Media
    python3 shell/preview.py --screenshot /tmp/omarchy-auto.png

Screenshot mode uses the offscreen platform and the software renderer, so it
also works over SSH and in CI.
"""

import argparse
import os
import sys
from pathlib import Path

SHELL_DIR = Path(__file__).resolve().parent


def parse_size(value):
    try:
        width, height = value.lower().split("x", 1)
        width, height = int(width), int(height)
        if width <= 0 or height <= 0:
            raise ValueError
        return width, height
    except (ValueError, AttributeError):
        raise argparse.ArgumentTypeError("expected WxH, for example 1280x800")


def main():
    parser = argparse.ArgumentParser(description="Preview the Omarchy Auto shell.")
    parser.add_argument(
        "--size",
        type=parse_size,
        default=(1280, 800),
        help="window size as WxH (default: 1280x800)",
    )
    parser.add_argument(
        "--expanded",
        choices=["Drive", "Media", "Comms", "Vehicle", "Home"],
        help="open one tile in the expanded overlay",
    )
    parser.add_argument(
        "--screenshot",
        metavar="PATH",
        help="render one frame to PATH and exit (implies offscreen)",
    )
    args = parser.parse_args()

    if args.screenshot:
        os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
        os.environ.setdefault("QT_QUICK_BACKEND", "software")

    try:
        from PySide6.QtCore import QTimer, QUrl
        from PySide6.QtGui import QColor, QGuiApplication
        from PySide6.QtQuick import QQuickView
    except ImportError:
        venv = SHELL_DIR.parent / ".venv"
        print(
            "PySide6 is not installed for this interpreter.\n"
            f"  python3 -m venv {venv}\n"
            f"  {venv}/bin/pip install -r {SHELL_DIR / 'requirements.txt'}\n"
            f"  {venv}/bin/python {SHELL_DIR / 'preview.py'}",
            file=sys.stderr,
        )
        return 1

    app = QGuiApplication(sys.argv)
    view = QQuickView()
    view.setTitle("Omarchy Auto")
    view.setResizeMode(QQuickView.SizeRootObjectToView)
    view.setColor(QColor("#1a1b26"))
    view.resize(*args.size)
    view.setSource(QUrl.fromLocalFile(str(SHELL_DIR / "main.qml")))

    if view.status() == QQuickView.Error:
        for error in view.errors():
            print(error.toString(), file=sys.stderr)
        return 1

    root = view.rootObject()
    if args.expanded:
        root.setProperty("expanded", args.expanded)

    view.show()

    if args.screenshot:
        output = Path(args.screenshot).expanduser().resolve()
        output.parent.mkdir(parents=True, exist_ok=True)

        def capture():
            image = view.grabWindow()
            if not image.save(str(output)):
                print(f"could not save {output}", file=sys.stderr)
                app.exit(1)
                return
            print(output)
            app.quit()

        QTimer.singleShot(900, capture)

    return app.exec()


if __name__ == "__main__":
    sys.exit(main())
