---
name: nuvio-project-patterns
description: Implement or review NuvioDesktop features using its existing Compose settings, navigation, theme tokens, and native player overlay patterns. Use for UI additions, settings pages, playback menus, and their state integration in this repository.
---

# Nuvio project patterns

Use the existing product as the reference. Before editing, locate the nearest
screen or interaction and inspect both its caller and shared components. A user
screenshot or explicit design choice takes precedence over these defaults.

## Choose the actual UI surface

Paths below are relative to the NuvioDesktop repository root.

- **Settings:** `composeApp/src/commonMain/kotlin/com/nuvio/app/features/settings/SettingsComponents.kt`.
  Reuse `SettingsSection`, `SettingsGroup`, `SettingsGroupDivider`, and
  `SettingsNavigationRow` where their behavior fits. Compare against
  `MediaServersSettingsPage.kt` in the same directory for layout and grouping.
  Route a subpage through `SettingsModels.kt` and `SettingsScreen.kt`; let the
  existing page header own the title and back navigation. Avoid nesting a second
  heading, Back footer, or unrelated rectangular panel inside that page.
- **Player selectors on desktop:** `composeApp/src/desktopMain/resources/player-ui/controls.html`
  and `controls.js`. Audio Tracks and Playback Speed use `player-rail-modal`,
  `audio-rail-panel`, and track rows. Extend these patterns for comparable menus,
  including keyboard focus, outside-click/Escape dismissal, scrolling, selected
  state, and hidden empty status areas. Inspect the linked stylesheet rather
  than approximating the appearance with Compose or a native system dialog.
- **Compose appearance:** `composeApp/src/commonMain/kotlin/com/nuvio/app/core/ui/Tokens.kt`,
  `Theme.kt`, and `AppTheme.kt`. Prefer `MaterialTheme.nuvio` tokens and existing
  typography/shapes. Follow the reference's responsive layout; do not assume
  every screen has the same desktop/tablet dimensions.
- **Icon actions:** compare
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/collection/CollectionManagementScreen.kt`
  when adding edit actions. Reuse its icon family, sizing, and accessible labeling
  where appropriate. Keep labels when an icon would be unclear.

Confirm these references still exist and represent the current design. Search for
an updated implementation if the repository has moved; do not copy obsolete code.

For changes to layout, loading states, scrolling, gestures, or date presentation,
read [UI consistency](../../ui-consistency.md). It records confirmed cross-screen
failure patterns and the behavior/tests that prevent recurrence. For Calendar
work, also read [the Calendar contract](../../../CALENDAR.md); its six-row grid,
orientation setting, and navigation choices are feature-specific.

## Integrate state and behavior

Keep shared feature state and contracts in their existing commonMain layer;
put OS/network/player implementations in the matching platform source set.
Follow adjacent feature code for resource strings, persistence, and navigation.
A desktop-only implementation may still need Android/iOS contract implementations
so shared source sets compile.

Keep navigation selection live inside retained callbacks and `snapshotFlow`.
Use the `selectedTab` State delegate in `MainAppContent.kt` as the reference:
`derivedStateOf` reads the desktop history or mobile selection, and callbacks read
that delegate when invoked. A plain `val` copied from state can leave a remembered
click handler treating a later click as a reselect, while `snapshotFlow` observes
no state reads. For callbacks that need a changing value without restarting their
owner, use `rememberUpdatedState` where appropriate.

For multi-step settings, use a single state for mutually exclusive panels such as
discovery versus Add/Edit. Clear incompatible results and transient errors when
switching modes. Guard late callbacks against cancellation, a changed selection,
and disposal. Let the owning UI layer manage its state updates.

Expose setup once in Settings and the frequent action in context when that matches
the feature. Avoid transient captions that briefly shift layout before data is
ready; reserve space only when it is needed by the intended design.

For the macOS trailer-player alternatives, read [the comparison contract](../../../docs/trailer-player-comparison.md).
The YouTube experiment keeps its WebView session across presentation changes and
uses the actual bundle identity as its HTTPS base URL. Keep YouTube controls
inside the bounded media view and Nuvio's expand/close header outside it. Hero/card
previews retain their Compose frame renderer; a child WebView cannot honor their
Compose fades or text layering. Verify both native bounds and visible video.

## Verify the changed experience

Run the smallest meaningful behavioral checks plus compilation appropriate to the
changed source sets. For visual changes, compare the actual UI with the chosen
reference: corners, padding, alignment, typography, icons, focus, selected rows,
clipping/scrolling, and loading/empty/error states. Exercise mode switching and
dismissal if changed. Do not claim visual verification from source inspection.

Use `./gradlew :composeApp:desktopTest --tests '<affected test class>'` for focused
JVM tests. See the repository's build scripts and `composeApp/build.gradle.kts`
for the current JDK and packaging requirements. Native macOS player checks may
require the packaged app: the development `:composeApp:run` controls have shown
behavior differing from the DMG build. Do not silently replace the user's installed
app or trigger TV/network activity as part of an unrelated UI check.

For navigation regressions, exercise the real `MainAppContent` screens and click
handlers, not only the history model or colored placeholder panes. Assert the
shown destination as well as selection. `MainAppTabNavigationTest` covers Home →
Search → Library → Settings → Library → Search → Home with both desktop layouts,
and returning to an initially selected Search tab. Keep repeated visits and tab
reselection in the regression sequence.

Full-app desktop UI tests require isolated profile storage. Run them with the
existing init script, which gives the test JVM a separate app-data home and an in-memory Java preferences store for auth:

```sh
./gradlew -I scripts/desktop-ui-tests.init.gradle :composeApp:desktopTest --tests 'com.nuvio.app.MainAppTabNavigationTest'
```

These integration tests skip without the isolation flag supplied by that script;
a skipped run does not verify the navigation regression. Do not use a real user
profile as a full-app test fixture.

At handoff, report the behavior changed, tests run, and any visual or hardware
checks that remain unverified. Follow `AGENTS.md`'s automatic documentation
maintenance rule: capture newly confirmed reusable failures in the appropriate
reference as part of the fix, and mention the documentation update in the handoff.
