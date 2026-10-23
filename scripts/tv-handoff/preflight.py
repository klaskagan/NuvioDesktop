#!/usr/bin/env python3
"""Check paired TV wake/launch capabilities before integrating playback handoff.

No backend, ADB, or TV app modification is needed for these transport checks.
This deliberately does not claim that launching an app proves playback works.
"""

from __future__ import annotations

import argparse
import asyncio
import getpass
import ipaddress
import json
import os
from pathlib import Path
import re
import socket
import sys
import tempfile
import time
from urllib.parse import urlparse


def local_ip(value: str) -> str:
    try:
        address = ipaddress.ip_address(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("Use the TV's numeric local IPv4 address.") from error
    if address.version != 4 or not any(address in network for network in (
        ipaddress.ip_network("10.0.0.0/8"),
        ipaddress.ip_network("172.16.0.0/12"),
        ipaddress.ip_network("192.168.0.0/16"),
        ipaddress.ip_network("169.254.0.0/16"),
    )):
        raise argparse.ArgumentTypeError("TV address must be a private or link-local IPv4 address.")
    return str(address)


def magic_packet(mac: str) -> bytes:
    if not re.fullmatch(r"(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}|(?:[0-9a-fA-F]{2}-){5}[0-9a-fA-F]{2}|[0-9a-fA-F]{12}", mac):
        raise ValueError("MAC address must contain six hexadecimal pairs.")
    raw = bytes.fromhex(mac.replace(":", "").replace("-", ""))
    if raw == bytes(6) or raw[0] & 1:
        raise ValueError("Use the TV network adapter's unicast MAC address.")
    return b"\xff" * 6 + raw * 16


def secure_directory(path: Path) -> None:
    if path.is_symlink():
        raise ValueError("State directory must not be a symbolic link.")
    path.mkdir(parents=True, exist_ok=True, mode=0o700)
    path.chmod(0o700)


def save_private_json(path: Path, value: dict) -> None:
    """Write credentials atomically without ever creating a world-readable file."""
    secure_directory(path.parent)
    descriptor, temporary = tempfile.mkstemp(dir=path.parent, prefix=".tv-")
    try:
        with os.fdopen(descriptor, "w") as output:
            json.dump(value, output, indent=2)
            output.write("\n")
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def load_state(path: Path) -> dict:
    if path.is_symlink():
        raise ValueError("State file must not be a symbolic link.")
    if not path.exists():
        return {}
    return json.loads(path.read_text())


def wake(mac: str, broadcast: str) -> None:
    packet = magic_packet(mac)
    address = ipaddress.IPv4Address(broadcast)
    if address.is_multicast or address.is_loopback or address.is_unspecified:
        raise ValueError("Invalid network broadcast address.")
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as transport:
        transport.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        for _ in range(3):
            transport.sendto(packet, (str(address), 9))


async def wait_until(check, timeout: float, label: str) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if await check():
            return
        await asyncio.sleep(0.5)
    raise TimeoutError(label)


async def sony(args, state_path: Path, state: dict) -> dict:
    from androidtvremote2 import AndroidTVRemote, InvalidAuth

    cert = state_path.parent / "sony-cert.pem"
    key = state_path.parent / "sony-key.pem"
    remote = AndroidTVRemote("Nuvio Desktop handoff check", str(cert), str(key), args.host)
    try:
        if args.action == "pair":
            await remote.async_generate_cert_if_missing()
            for path in (cert, key):
                path.chmod(0o600)
            await asyncio.wait_for(remote.async_start_pairing(), 30)
            code = await asyncio.to_thread(getpass.getpass, "Enter the code displayed on the Sony TV: ")
            await asyncio.wait_for(remote.async_finish_pairing(code.strip()), 30)
            save_private_json(state_path, {"paired": True})
            return {"pairing": "accepted"}
        if not state.get("paired") or not cert.exists() or not key.exists():
            raise ValueError("Pair with the Sony TV first, while it is on.")
        if args.action == "wake" and args.mac:
            wake(args.mac, args.broadcast)
        try:
            await asyncio.wait_for(remote.async_connect(), args.timeout)
        except InvalidAuth as error:
            raise ValueError("Sony pairing is no longer valid; run pair again.") from error
        if args.action == "wake":
            # WAKEUP is idempotent; POWER would accidentally switch an awake TV off.
            remote.send_key_command("WAKEUP")

            async def awake():
                return remote.is_on is True

            await wait_until(awake, args.timeout, "Sony did not report an awake screen.")
        elif args.action == "launch":
            remote.send_launch_app_command(args.link)

            async def foreground():
                return remote.current_app == args.app_id

            await wait_until(foreground, args.timeout, "Sony did not report Nuvio in the foreground.")
        return {"awake": remote.is_on, "nuvio_foreground": remote.current_app == args.app_id}
    finally:
        remote.disconnect()


async def lg(args, state_path: Path, state: dict) -> dict:
    from aiowebostv import WebOsClient

    if args.action != "pair" and not state.get("client_key"):
        raise ValueError("Pair with the LG TV first, while it is on.")
    if args.action == "wake":
        if not args.mac:
            raise ValueError("LG wake requires --mac with the active network adapter's MAC address.")
        wake(args.mac, args.broadcast)
    client = WebOsClient(args.host, client_key=state.get("client_key"), connect_timeout=args.timeout)
    try:
        if args.action == "pair":
            print("Approve the connection prompt on the LG TV.", flush=True)
        # Give network standby time to restore the local remote-control service.
        deadline = time.monotonic() + args.timeout
        while True:
            try:
                await asyncio.wait_for(client.connect(), max(1, deadline - time.monotonic()))
                break
            except (ConnectionError, OSError, TimeoutError):
                await client.disconnect()
                if args.action != "wake" or time.monotonic() >= deadline:
                    raise
                await asyncio.sleep(1)
        if args.action == "pair":
            if not client.client_key:
                raise ValueError("LG did not issue a pairing key.")
            save_private_json(state_path, {"client_key": client.client_key})
            return {"pairing": "accepted"}
        if args.action == "launch":
            # Empty params exercise the same launch API the receiver will use.
            await client.launch_app_with_params(args.app_id, {})

            async def foreground():
                return await client.get_current_app() == args.app_id

            await wait_until(foreground, args.timeout, "LG did not report Nuvio in the foreground.")
        return {"remote_connected": True, "nuvio_foreground": await client.get_current_app() == args.app_id}
    finally:
        await client.disconnect()


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("platform", choices=("sony", "lg"))
    result.add_argument("action", choices=("pair", "status", "wake", "launch"))
    result.add_argument("--host", type=local_ip, required=True)
    result.add_argument("--mac", help="MAC of the active TV network adapter (required for LG wake)")
    result.add_argument("--broadcast", default="255.255.255.255")
    result.add_argument("--app-id", help="Installed Nuvio package/app ID; normally detected from platform defaults")
    result.add_argument("--link", default="nuvio://meta?type=movie&id=tt0133093", help="Sony Nuvio deep link (defaults to The Matrix details)")
    result.add_argument("--timeout", type=float, default=60)
    result.add_argument("--state-dir", type=Path, default=Path.home() / "Library/Application Support/Nuvio/tv-handoff-preflight")
    result.add_argument("--report", type=Path, help="Optional report file; excludes host address and pairing credentials")
    return result


async def run(args) -> dict:
    if not 1 <= args.timeout <= 180:
        raise ValueError("Timeout must be between 1 and 180 seconds.")
    if args.platform == "sony" and urlparse(args.link).scheme != "nuvio":
        raise ValueError("Sony launch checks accept only nuvio:// links.")
    args.app_id = args.app_id or ("com.nuvio.tv" if args.platform == "sony" else "space.nuvio.webos")
    directory = args.state_dir.expanduser() / f"{args.platform}-{args.host}"
    secure_directory(directory)
    state_path = directory / "pairing.json"
    state = load_state(state_path)
    started = time.monotonic()
    details = await (sony(args, state_path, state) if args.platform == "sony" else lg(args, state_path, state))
    report = {
        "platform": args.platform,
        "check": args.action,
        "passed": True,
        "elapsed_seconds": round(time.monotonic() - started, 2),
        "details": details,
        "playback_handoff_verified": False,
    }
    if args.report:
        save_private_json(args.report, report)
    return report


def main() -> int:
    args = parser().parse_args()
    # Client libraries may create private keys; restrict their files from the start.
    os.umask(0o077)
    try:
        print(json.dumps(asyncio.run(run(args)), indent=2))
        return 0
    except ModuleNotFoundError:
        print("Install scripts/tv-handoff/requirements.txt in a Python 3.11+ virtual environment first.", file=sys.stderr)
    except KeyboardInterrupt:
        print("Cancelled.", file=sys.stderr)
    except Exception as error:
        # Do not echo library exception bodies: they may include pairing credentials.
        print(f"Check failed ({type(error).__name__}). Check TV readiness, pairing and network settings.", file=sys.stderr)
        if isinstance(error, ValueError):
            print(str(error), file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
