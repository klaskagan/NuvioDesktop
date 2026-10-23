"""Offline tests for the hardware-check tool; never connects to a TV."""

import argparse
import importlib.util
import json
from pathlib import Path
import stat
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("preflight", Path(__file__).with_name("preflight.py"))
preflight = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preflight)


class PreflightTests(unittest.TestCase):
    def test_wake_packet_has_correct_mac_repetition(self):
        packet = preflight.magic_packet("02:00:00:00:00:01")
        self.assertEqual(packet, b"\xff" * 6 + bytes.fromhex("020000000001") * 16)

    def test_rejects_invalid_or_multicast_mac(self):
        for value in ("", "00:00:00:00:00:00", "FF:FF:FF:FF:FF:FF", "aa:bb", "gg:00:00:00:00:00"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                preflight.magic_packet(value)

    def test_only_accepts_local_numeric_tv_addresses(self):
        for value in ("192.168.1.4", "10.1.2.3", "172.16.0.4"):
            self.assertEqual(preflight.local_ip(value), value)
        for value in ("8.8.8.8", "127.0.0.1", "localhost", "192.168.1.4:36481", "169.255.1.2"):
            with self.subTest(value=value), self.assertRaises(argparse.ArgumentTypeError):
                preflight.local_ip(value)

    def test_pairing_state_is_private_and_atomically_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "state" / "pairing.json"
            preflight.save_private_json(path, {"client_key": "test-only"})
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(path.parent.stat().st_mode), 0o700)
            preflight.save_private_json(path, {"paired": True})
            self.assertEqual(json.loads(path.read_text()), {"paired": True})
            self.assertEqual(list(path.parent.iterdir()), [path])

    def test_rejects_symlinked_state_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            (path / "link").symlink_to(path / "actual")
            with self.assertRaises(ValueError):
                preflight.secure_directory(path / "link")


if __name__ == "__main__":
    unittest.main()
