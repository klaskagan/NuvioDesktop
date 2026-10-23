# TV handoff hardware checks

Development tools for checking the local remote-control interfaces. These Python
libraries are **not** required by the desktop application: production adapters
are Kotlin/JVM implementations.

Use Python 3.11+ in a virtual environment:

```sh
python3 -m venv /private/tmp/nuvio-tv-check
/private/tmp/nuvio-tv-check/bin/pip install -r scripts/tv-handoff/requirements.txt
```

With a TV turned on, pair once (replace `TV_IP` with its numeric local address):

```sh
/private/tmp/nuvio-tv-check/bin/python scripts/tv-handoff/preflight.py sony pair --host TV_IP
/private/tmp/nuvio-tv-check/bin/python scripts/tv-handoff/preflight.py lg pair --host TV_IP
```

Approve the LG prompt or enter the Sony code. Standard remote ports are used;
an Android wireless-debugging port is not needed for handoff.

Run `status` or `launch` with the same arguments. `launch` verifies that Nuvio is
reported in the foreground, not that a movie has resumed. Sony's default deep
link opens The Matrix details. Override `--app-id` for a different installed
variant, or `--link` for a different Nuvio details link.

For standby checks, enable the TV's network-wake setting, put it into standby,
then use `wake --mac AA:BB:CC:DD:EE:FF`. LG requires its active network adapter's
MAC address. Sony can first try its paired WAKEUP command without `--mac`.
Use `--broadcast` if the router requires the local subnet broadcast address.

Pairing files have owner-only permissions and live outside the repository.
Override `--state-dir` for isolated tests. `--report` produces a report without
pairing credentials or the TV address. A successful remote connection is not
proof that the physical screen lit up; confirm that separately.

Run offline tests:

```sh
python3 -m unittest discover -s scripts/tv-handoff -p 'test_*.py'
```

## Keeping receiver changes across upstream releases

Durable working checkouts on this Mac:

- `~/code/NuvioTV` — Android Nuvio receiver.
- `~/code/NuvioTVSmart` — JavaScript webOS receiver.
- `~/code/NuvioNativeLegacy` — native webOS and Android Legacy receiver.

Source overlays and exact bases are in `docs/tv-handoff/`; they do not contain
account data or private build configuration. A stock upstream app update does
not contain the overlay and can remove Continue on TV. Prepare a rebuilt version:

```sh
python3 scripts/tv-handoff/receiver_updates.py check
python3 scripts/tv-handoff/receiver_updates.py prepare \
  --receiver legacy --ref v2.0.8 --destination /absolute/new/folder --test
```

Use `android`, `webos`, or `legacy`. Omit `--ref` to try the newest stable tag.
Preparation creates a fresh checkout, applies the overlay with Git's three-way
merge, and optionally runs the receiver's tests. Existing checkouts and installed
apps are never reset or replaced. A conflict stops the process for review.
Private `local.properties` and build dependencies must be supplied locally before
packaging. Keep the same signing key and app ID for future in-place updates.

`.github/workflows/tv-receiver-compatibility.yml` provides the same compatibility
check through a manual GitHub Actions run once these changes are pushed to your
repository. It uploads the resulting diff for review. It does not sign, publish,
or install TV packages. Full automated packaging needs the platform build
runtimes and your own signing/configuration secrets; deployment should follow
compatibility and physical playback checks.

After a prepared version has passed build and playback checks, export its
updated overlay and base before committing it to this desktop repository:

```sh
python3 scripts/tv-handoff/receiver_updates.py export \
  --receiver legacy --source /absolute/new/folder --ref v2.0.8
```

Use the upstream tag/commit that the new folder was prepared from. This updates
the patch and its machine-readable base together, keeping the next update
reproducible. Review the resulting diff and retain the tested APK/IPK alongside
its checksums.
