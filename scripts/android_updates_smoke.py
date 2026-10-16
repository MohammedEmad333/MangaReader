#!/usr/bin/env python3
"""Android emulator smoke check: open the Updates tab using accessibility nodes."""
import re
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True, stderr=subprocess.STDOUT)


def ui_tree():
    # Capture command output as well as XML errors to diagnose stuck Android UI automation.
    result = adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    try:
        return ET.fromstring(adb("shell", "cat", "/sdcard/window.xml"))
    except subprocess.CalledProcessError as error:
        raise RuntimeError("UI dump output: " + result + "; read failure: " + error.output) from error


def center(bounds):
    values = [int(n) for n in re.findall(r"[0-9]+", bounds)]
    if len(values) != 4:
        raise ValueError("Invalid Android accessibility bounds: " + bounds)
    return ((values[0] + values[2]) // 2, (values[1] + values[3]) // 2)


def matches_node_label(node, label):
    for attribute in ("text", "content-desc"):
        value = node.get(attribute, "")
        if value == label or label in [part.strip() for part in value.split(",")]:
            return True
    return False


def find_node(root, label):
    return next(
        (
            node for node in root.iter("node")
            if matches_node_label(node, label)
        ),
        None,
    )


def wait_for(label, timeout=40):
    until = time.monotonic() + timeout
    last_error = None
    last_labels = []
    while time.monotonic() < until:
        try:
            root = ui_tree()
            last_labels = [
                (node.get("text") or node.get("content-desc") or "")[:100]
                for node in root.iter("node")
                if node.get("text") or node.get("content-desc")
            ][:50]
            node = find_node(root, label)
            if node is not None:
                return node
        except (ET.ParseError, subprocess.CalledProcessError, RuntimeError) as error:
            last_error = error
        time.sleep(3)
    raise RuntimeError(f"Could not find '{label}' in Android UI after {timeout}s: {last_error}; visible labels: {last_labels}")


def capture_screenshot(destination):
    with open(destination, "wb") as output:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=output, check=True)


def main():
    try:
        run_smoke()
    except Exception:
        Path("smoke-results").mkdir(exist_ok=True)
        for name, args in (
            ("ui.xml", ("shell", "cat", "/sdcard/window.xml")),
            ("logcat.txt", ("logcat", "-d", "-t", "1000")),
        ):
            try:
                Path("smoke-results", name).write_text(adb(*args))
            except subprocess.CalledProcessError:
                pass
        try:
            capture_screenshot("smoke-results/failure-screen.png")
        except (OSError, subprocess.CalledProcessError):
            pass
        raise


def run_smoke():
    tab = wait_for("Updates", timeout=90)
    x, y = center(tab.attrib["bounds"])
    adb("shell", "input", "tap", str(x), str(y))
    wait_for("Chapter updates")
    # These controls must be available even for a fresh library with no updates.
    wait_for("Refresh")
    wait_for("Search series or chapter")
    # Exercise refresh in an empty library and ensure the Updates screen stays alive.
    refresh = wait_for("Refresh")
    rx, ry = center(refresh.attrib["bounds"])
    adb("shell", "input", "tap", str(rx), str(ry))
    wait_for("Chapter updates")
    # Search must accept user input and remain on the Updates screen.
    search = wait_for("Search series or chapter")
    sx, sy = center(search.attrib["bounds"])
    adb("shell", "input", "tap", str(sx), str(sy))
    adb("shell", "input", "text", "smoketest")
    wait_for("smoketest")
    wait_for("Chapter updates")
    # Dismiss the soft keyboard, then verify that the Library bottom navigation tab still works.
    adb("shell", "input", "keyevent", "4")
    library = wait_for("Library")
    lx, ly = center(library.attrib["bounds"])
    adb("shell", "input", "tap", str(lx), str(ly))
    wait_for("Updates")
    if not adb("shell", "pidof", "com.mangareader.app").strip():
        raise RuntimeError("App exited during Updates navigation")
    # Capture the actual UI state for independent visual review.
    Path("smoke-results").mkdir(exist_ok=True)
    capture_screenshot("smoke-results/updates-screen.png")
    print("Updates tab opened successfully on Android emulator.")


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        assert center("[0,10][100,110]") == (50, 60)
        root = ET.fromstring('<hierarchy><node text="Updates" bounds="[0,0][30,20]"/></hierarchy>')
        assert find_node(root, "Updates") is not None
        assert find_node(root, "History") is None
        merged = ET.fromstring('<hierarchy><node content-desc="Home, Updates, Library"/></hierarchy>')
        assert find_node(merged, "Updates") is not None
        sample = ET.fromstring(
            '<hierarchy><node text="Refresh"/><node content-desc="Search series or chapter"/></hierarchy>'
        )
        assert find_node(sample, "Refresh") is not None
        assert find_node(sample, "Search series or chapter") is not None
        try:
            center("invalid")
            raise AssertionError("Invalid bounds should fail")
        except ValueError:
            pass
        print("Smoke helper tests passed.")
    else:
        main()
