"""Robot Framework library for the ToastCatcher Android app.

Everything goes through adb, so it works wherever your adb connection works
(USB, wifi, or your remote-control software's adb tunnel).

Usage in a .robot file:

    Library    ToastCatcher.py    serial=${SERIAL}

Keywords (Robot ignores case/spaces/underscores in names):
    Enable Toast Service            one-time setup: switch the accessibility service on
    Start Toast Capture             clear old toasts and start listening
    Stop Toast Capture              stop listening (captured toasts are kept)
    Get Toasts                      list of dicts: id, time, epoch_ms, package, text
    Get Toast Texts                 just the texts, as a list of strings
    Get Toasts And Clear            same as Get Toasts, then deletes them on the phone
    Clear Toasts                    delete captured toasts without reading them
    Toast Capture Status            dict: listening, count, service_enabled
    Wait For Toast                  poll until a toast matching a regex shows up
    Toasts Should Contain           fail unless a toast matching a regex was captured
"""
import json
import re
import subprocess
import time

from robot.api import logger
from robot.api.deco import keyword

AUTHORITY = "content://com.toastcatcher.provider"
SERVICE = "com.toastcatcher/com.toastcatcher.ToastService"


class ToastCatcher:
    ROBOT_LIBRARY_SCOPE = "GLOBAL"

    def __init__(self, serial=None, adb="adb", adb_timeout=60):
        self.serial = serial or None
        self.adb = adb
        self.adb_timeout = float(adb_timeout)

    # ------------------------------------------------------------------ adb plumbing
    def _adb(self, *args):
        cmd = [self.adb]
        if self.serial:
            cmd += ["-s", self.serial]
        cmd += list(args)
        logger.debug("adb: " + " ".join(cmd))
        proc = subprocess.run(
            cmd, capture_output=True, text=True, timeout=self.adb_timeout
        )
        if proc.returncode != 0:
            raise RuntimeError(
                "adb failed (%s): %s" % (proc.returncode, (proc.stderr or proc.stdout).strip())
            )
        return proc.stdout.replace("\r", "")

    def _call(self, method):
        out = self._adb("shell", "content", "call", "--uri", AUTHORITY, "--method", method)
        if "Result:" not in out:
            raise RuntimeError(
                "Toast Catcher app did not answer. Is it installed? Output was: " + out.strip()
            )
        match = re.search(r"Bundle\[\{(.*)\}\]", out)
        result = {}
        if match and match.group(1):
            for part in match.group(1).split(", "):
                key, _, value = part.partition("=")
                result[key] = value
        return result

    def _query(self, path):
        out = self._adb("shell", "content", "query", "--uri", AUTHORITY + path)
        toasts = []
        for line in out.splitlines():
            match = re.match(r"Row: \d+ json=(.*)$", line)
            if match:
                toasts.append(json.loads(match.group(1)))
        return toasts

    # ------------------------------------------------------------------ keywords
    @keyword
    def enable_toast_service(self):
        """One-time setup per phone: turns the accessibility service on via adb.

        Keeps any accessibility services that are already enabled.
        Needed because Android 13+ blocks switching it on by hand for sideloaded apps.
        """
        current = self._adb("shell", "settings", "get", "secure", "enabled_accessibility_services").strip()
        services = [] if current in ("", "null") else current.split(":")
        if SERVICE not in services:
            services.append(SERVICE)
            self._adb("shell", "settings", "put", "secure", "enabled_accessibility_services", ":".join(services))
        self._adb("shell", "settings", "put", "secure", "accessibility_enabled", "1")

    @keyword
    def start_toast_capture(self):
        """Delete all previous captures and start listening for new toasts."""
        result = self._call("start")
        logger.info("Toast capture started: %s" % result)

    @keyword
    def stop_toast_capture(self):
        """Stop listening. Toasts captured so far stay available."""
        result = self._call("stop")
        logger.info("Toast capture stopped: %s" % result)

    @keyword
    def get_toasts(self):
        """Return every toast captured since Start, oldest first (does not delete)."""
        return self._query("/toasts")

    @keyword
    def get_toast_texts(self):
        """Return just the text of every captured toast."""
        return [t["text"] for t in self._query("/toasts")]

    @keyword
    def get_toasts_and_clear(self):
        """Return every captured toast and delete them on the phone in one step."""
        return self._query("/toasts/drain")

    @keyword
    def clear_toasts(self):
        """Delete captured toasts without reading them."""
        self._adb("shell", "content", "delete", "--uri", AUTHORITY + "/toasts")

    @keyword
    def toast_capture_status(self):
        """Return a dict with listening, count and service_enabled (all strings)."""
        return self._call("status")

    @keyword
    def wait_for_toast(self, pattern, timeout=60, interval=2):
        """Poll until a captured toast's text matches the regex `pattern`.

        Returns the first matching toast (a dict). Fails if none shows up in `timeout` seconds.
        Searches regex-style, so `Test Passed` matches 'Test Passed!'.
        """
        regex = re.compile(pattern)
        deadline = time.time() + float(timeout)
        while True:
            for toast in self._query("/toasts"):
                if regex.search(toast["text"]):
                    return toast
            if time.time() >= deadline:
                raise AssertionError(
                    "No toast matching '%s' within %s seconds. Captured so far: %s"
                    % (pattern, timeout, [t["text"] for t in self._query("/toasts")])
                )
            time.sleep(float(interval))

    @keyword
    def toasts_should_contain(self, pattern):
        """Fail unless some captured toast matches the regex `pattern`."""
        regex = re.compile(pattern)
        texts = self.get_toast_texts()
        if not any(regex.search(t) for t in texts):
            raise AssertionError("No toast matching '%s'. Captured: %s" % (pattern, texts))
