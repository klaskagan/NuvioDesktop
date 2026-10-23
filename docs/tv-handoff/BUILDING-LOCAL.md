# Local receiver builds on this Mac

The source checkouts live under `~/code/`, each on
`codex/continue-on-tv`. Their original upstream remotes are retained.
`local.properties` contains local server configuration and stays ignored.

Build dependency caches are preserved under
`~/.cache/nuvio-tv-handoff/`: `p2p/` contains the pinned engine
and platform static archives, and `webos-cli/` contains the packaging CLI.
The Docker image `nuvio-webos-sdk` supplies the Intel-hosted webOS ARM SDK.
Android dependencies are in `~/.cache/nuvio-android` and the existing Android SDK.
The Android test build uses NDK 29.0.14206865 and platform 35.

Use these environment settings with the native checkout's existing build scripts:

```sh
# LG: package only, no upstream root-SSH deployment
NUVIO_CTR=docker NUVIO_BUILD_PLATFORM=linux/amd64 \
NUVIO_P2P_MOTOR="$HOME/.cache/nuvio-tv-handoff/p2p" \
NUVIO_RUST_LIB="$PWD/rust/mkvcore/target/armv7-unknown-linux-gnueabi/release/libmkvcore.a" \
NUVIO_ARES_PACKAGE="$HOME/.cache/nuvio-tv-handoff/webos-cli/node_modules/.bin/ares-package" \
bash tools/arm.sh --high-cache --ipk --build

# Android Legacy: separate signed debug app, retaining the stock installation
NUVIO_NDK_VERSION=29.0.14206865 \
NUVIO_P2P_MOTOR="$HOME/.cache/nuvio-tv-handoff/p2p" \
NUVIO_RUST_MKVCORE_PREBUILT="$PWD/build/android/rust-mkvcore" \
bash tools/android.sh --debug-only
```

Use Node 20 on PATH for webOS packaging, and set JAVA_HOME to a JDK 17 for
Android. The Android debug key is `~/.android/debug.keystore`; keep it to update
the same test app without reinstalling its data. No upstream signing key is
available, so a locally signed APK cannot replace the upstream production app.

The cached Rust archives are for this source revision. If upstream changes Rust
interfaces or the pinned P2P engine, rebuild those dependencies using the new
checkout's build scripts before packaging; do not assume old archives remain
compatible. The native scripts support a prebuilt Rust archive path for reuse
when its source and ABI match. Final packages and SHA256 hashes are under
`NuvioDesktop/build/continue-on-tv/`.
