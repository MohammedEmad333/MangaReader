#!/usr/bin/env python3
"""Android emulator smoke check: open the Updates tab using accessibility nodes."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, stderr=subprocess.STDOUT)


def ui_tree():
    adb("shell", "uiautomator", "dump", "/sdcard/yomu-smoke.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/yomu-smoke.xml"))


def center(bounds):
    values = [int(n) for n in re.findall(r"[0-9]+", bounds)]
    if len(values) != 4:
        raise ValueError("Invalid Android accessibility bounds: " + bounds)
    return ((values[0] + values[2]) // 2, (values[1] + values[3]) // 2)


def find_node(root, label):
    return next(
        (
            node for node in root.iter("node")
            if node.get("text") == label or node.get("content-desc") == label
        ),
        None,
    )


def wait_for(label, timeout=40):
    until = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < until:
        try:
            node = find_node(ui_tree(), label)
            if node is not None:
                return node
        except (ET.ParseError, subprocess.CalledProcessError) as error:
            last_error = error
        time.sleep(2)
    raise RuntimeError(f"Could not find '{label}' in Android UI after {timeout}s: {last_error}")


def main():
    tab = wait_for("Updates")
    x, y = center(tab.attrib["bounds"])
    adb("shell", "input", "tap", str(x), str(y))
    wait_for("Chapter updates")
    if not adb("shell", "pidof", "com.mangareader.app").strip():
        raise RuntimeError("App exited during Updates navigation")
    print("Updates tab opened successfully on Android emulator.")


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        assert center("[0,10][100,110]") == (50, 60)
        root = ET.fromstring('<hierarchy><node text="Updates" bounds="[0,0][30,20]"/></hierarchy>')
        assert find_node(root, "Updates") is not None
        assert find_node(root, "History") is None
        print("Smoke helper tests passed.")
    else:
        main()
