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
- For layout, loading, scrolling, gestures, and date presentation, read
  `.agents/ui-consistency.md`. Apply its shared invariants; keep feature-specific
  choices in the feature's documentation.
- Use the shared media viewport/shelf prefetch and row-readiness components for
  every movie/series shelf or grid, including portrait and square styles. Do not
  opt individual catalog identities into loading behavior. Keep collection cover
  shapes independent of the global media poster style; preserve explicit exclusions.
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
- After completing an implementation, build a standalone macOS review `.app`
  that the user can open by double-clicking in Finder, without installing a DMG
  or running Gradle. Keep it in the project's build output, separate from the
  installed app; never overwrite or replace the installed app. Verify the bundle
  exists and provide its absolute path/link in the final handoff. A DMG alone is
  not a sufficient review deliverable. Report any build blocker explicitly.

## Addon, collection, and catalog recovery

- Before manipulating or testing addons, collections, or catalogs, create and
  verify a restorable backup of the original affected state. Capture local and
  synced account state, including configured URLs, IDs, order, enabled flags,
  sources, artwork, and relevant settings. Do not begin mutations without it.
- Store backups outside the repository in a durable, access-restricted location;
  do not rely on chat history or temporary files alone. Never commit backups or
  expose tokens and credentials in logs, screenshots, or messages.
- Save a recovery checkpoint with the backup path, affected account/profile,
  original counts, restore procedure, and each completed mutation. Keep it current
  so work can recover after token/context limits, network errors, crashes, or
  interrupted tool calls. Preserve the original backup until restoration or the
  intended final state has been verified and handed off.
- Tests must isolate both profile files and authentication/preferences, and must
  not sync fixtures to a real account. Use the desktop UI test init script and
  in-memory fixtures; a separate home directory alone is insufficient on macOS.
- After an ambiguous or failed write, read the actual state before retrying.
  Restore unintended changes from the backup and verify IDs, contents, order,
  and counts. Check whether imports append or replace to avoid duplicates.

## Maintain agent guidance automatically

When a confirmed bug or accepted design correction exposes a reusable failure
pattern or changes a cross-feature convention, update the relevant agent
documentation in the same task, without waiting for a separate user request.
Treat this as part of completing the fix.

- Record the trigger/root cause, the behavior to preserve, an existing reference,
  and the regression check that would catch a repeat. Prefer a regression test
  and shared-component fix where appropriate; documentation alone is not proof.
- Put cross-screen UI invariants in `.agents/ui-consistency.md`, other shared
  lessons in the closest relevant agent reference, routing/workflow guidance in
  the applicable skill, and local behavior in feature documentation. Keep
  `AGENTS.md` short. Do not make a feature preference a global rule.
- Update or replace an existing rule before adding another. Remove contradictory
  or obsolete guidance and verify referenced files/tests still exist.
- Document confirmed findings, not speculative review concerns. If reproduction
  or verification is blocked, state that limitation in the task handoff rather
  than presenting an unverified fix as a project convention.
- Mention documentation changes in the handoff. This rule authorizes maintaining
  repository guidance during the task, not unrelated UI changes, live-account
  mutations, or background monitoring.

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

<!-- jbcontext-instructions-start -->
# Tools

## Semantic Code Search (jbcontext)

You have access to `jbcontext search` for searching the codebase semantically.
Use the `/context-search` skill or run `jbcontext search "<query>"` to find code by meaning, not just keywords.

### Query Tips

- Be descriptive: "Where is a function that validates user email addresses" > "email"
- Include context: "Find error handling middleware for HTTP requests with logging"
- Specify what you're looking for: "React component that renders a modal dialog"

### When to use

`jbcontext search` is a **code-discovery** tool. Reach for it only when a task requires finding or understanding code whose location you don't already know.

Skip it - go straight to the right tool - when:

- the task names the exact file, class, or symbol (keyword grep is faster);
- the relevant file is already open or identified;
- the task doesn't involve locating code at all - git operations (rebase, merge, commit), running tests or builds, shell/statusline/config setup, or reviewing a diff you already have.

### How to use it

- Start with `jbcontext search` before planning, editing, or exact search in unfamiliar code when you do not yet know the right file, subsystem, implementation, or related test.
- Use one focused natural-language query per search.
- Do not start with grep, ripgrep, or find when the search problem is still semantic or exploratory.
- Inspect the first relevant file or directory before issuing another broad semantic search.
- Use another broad `jbcontext search` only if the local path stops being productive.
- Once you know the relevant file, symbol, or directory, switch to direct file reads or exact search for local inspection.
- If you search again after finding a relevant area, narrow with `-p <path>`.

<!-- jbcontext-instructions-end -->