#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"
ROOT="$(cd .. && pwd)"

# The ARMv7 tablet has no usable GPU/DRM path for Qt Quick, so force the
# software scene graph there. Desktops keep their normal accelerator.
case "$(uname -m)" in
    armv6*|armv7*) export QT_QUICK_BACKEND="${QT_QUICK_BACKEND:-software}" ;;
esac

if command -v qmlscene >/dev/null 2>&1; then
    exec qmlscene main.qml
fi

if [ -x /usr/lib/qt6/bin/qmlscene ]; then
    exec /usr/lib/qt6/bin/qmlscene main.qml
fi

if command -v qml6 >/dev/null 2>&1; then
    exec qml6 main.qml
fi

if command -v qs >/dev/null 2>&1; then
    exec qs quickshell.qml
fi

if command -v quickshell >/dev/null 2>&1; then
    exec quickshell quickshell.qml
fi

if [ -x "$ROOT/.venv/bin/python" ] && "$ROOT/.venv/bin/python" -c "import PySide6" >/dev/null 2>&1; then
    exec "$ROOT/.venv/bin/python" preview.py
fi

if command -v python3 >/dev/null 2>&1 && python3 -c "import PySide6" >/dev/null 2>&1; then
    exec python3 preview.py
fi

if command -v pyside6-qml >/dev/null 2>&1; then
    exec pyside6-qml main.qml
fi

cat >&2 <<EOF
No Qt 6 QML runtime found.

Options:
  - Install qml-qt6 / qmlscene on this machine.
  - Or use the PySide6 preview:
      python3 -m venv "$ROOT/.venv"
      "$ROOT/.venv/bin/pip" install -r "$ROOT/shell/requirements.txt"
      "$ROOT/shell/run.sh"
  - On the tablet, from this shell/ directory:
      QT_QUICK_BACKEND=software /usr/lib/qt6/bin/qmlscene main.qml
  - On a Quickshell host, from this shell/ directory:
      qs quickshell.qml
EOF
exit 1
