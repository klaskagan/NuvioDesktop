# Nuvio Desktop project guidance

Nuvio uses Kotlin Multiplatform / Compose, with a separate native desktop player
and HTML/JavaScript player controls. Identify which UI surface owns the requested
change before editing it.

## UI and feature work

For new features or changes involving UI, navigation, player controls, or settings,
read `.agents/skills/nuvio-project-patterns/SKILL.md` before implementation. If the
skill is not in the session catalog, read that file directly.

- Find the closest existing screen or interaction and reuse its components and
  theme tokens. Name that reference in the first implementation update.
- Settings subpages belong in the existing Settings navigation. Playback selectors
  belong in the existing player overlay. New windows need a product reason or an
  explicit user request.
- Match the current reference's radius, spacing, typography, icons, selection,
  focus, and dismissal behavior. Avoid parallel styling for the same interaction.
- Keep loading, empty, error, and editor states coherent; competing forms should
  not remain open together. Prevent stale asynchronous work from changing a newer
  selection or reopening a dismissed view.
- Keep navigation selection observable in retained callbacks and `snapshotFlow`;
  avoid capturing a plain value that can become stale.
- Reproduce navigation bugs through real screens and click handlers, and assert
  the displayed destination. Run full-app UI tests with isolated profile storage.
- Verify visible changes in the appropriate running UI when tools permit. A build
  or unit test alone is not visual validation. State any unverified behavior.
- For collection hero/backdrop work, read `.agents/collection-backdrops.md` and
  reuse the seasonal catalog implementation as the reference.

## Repository boundaries

Keep shared UI/state in commonMain and platform integrations in their existing
platform source sets. Follow the touched feature's localization and state patterns.
Use focused tests for changed behavior; avoid tests that only mirror cosmetic code.

Do not commit packages, caches, logs, local.properties, device identities, pairing
credentials, or signing keys. For TV receiver source/update work, read
`docs/tv-handoff/README.md` and `scripts/tv-handoff/README.md`. Do not contact or wake
physical TVs merely to validate an unrelated feature.

Treat these as project defaults, not a restriction on a user-requested redesign.
Update guidance when actual project conventions change; keep it short and concrete.
