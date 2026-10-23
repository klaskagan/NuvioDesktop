# TV receiver source patches

Apply each patch to its matching upstream checkout:

| Repository | Base commit | Patch |
| --- | --- | --- |
| NuvioMedia/NuvioTV | ce6ca448b9972d205ac2ba9f2a55a07743cc81f1 | NuvioTV.patch |
| NuvioMedia/NuvioTVSmart | c8674f2d47d79239cd1dd67c4752bde371e589f7 | NuvioTVSmart.patch |
| iqui27/nuvio-native-legacy (v2.0.8) | 87c8b8909f0d605f1d8351147fe5afb9de233b8d | NuvioNativeLegacy.patch |

Run `git apply /absolute/path/to/the/patch` inside the matching checkout.
See ../continue-on-tv.md for protocol, build instructions, tests and hardware limitations.
The patches contain source only; pairing credentials and generated packages are excluded.

Durable local checkouts (branch `codex/continue-on-tv`):

- `~/code/NuvioTV` — checkpoint `9e509e3`.
- `~/code/NuvioTVSmart` — checkpoint `50a23d7`.
- `~/code/NuvioNativeLegacy` — checkpoint `2e73096` (webOS and Android).

`../../scripts/tv-handoff/receiver_updates.py` can check upstream tags, prepare a
fresh checkout with a three-way overlay, run portable checks, and export a
reviewed overlay/base for the next update. See its README and BUILDING-LOCAL.md.
These local commits are not published to the upstream developers.
