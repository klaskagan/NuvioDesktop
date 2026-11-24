# Nuvio Desktop project guidance

Nuvio uses Kotlin Multiplatform / Compose, with a separate native desktop player
and HTML/JavaScript player controls. Identify which UI surface owns the requested
change before editing it.

## UI and feature work

For new features or changes involving UI, navigation, player controls, or settings,
read `.agents/skills/nuvio-project-patterns/SKILL.md` before implementation. If the
skill is not in the session catalog, read that file directly.

- Desktop Details work (board, tabs, Rate, Quick pick, Sources, Watch it your way
  windows) follows the skill's "Details redesign" section: the design artifact is the
  source of truth, and the build tracker is updated when done.
- Find the closest existing screen or interaction and reuse its components and
  theme tokens. Name that reference in the first implementation update.
- Settings subpages belong in the existing Settings navigation. Playback selectors
  belong in the existing player overlay. New windows need a product reason or an
  explicit user request.
- Reuse the reference's actual shared components before matching radius, spacing,
  typography, icons, selection, focus and dismissal. Do not approximate a shared
  control with generic buttons or text glyphs. Fix ownership/context if a shared
  control is hidden; do not invent a replacement. Verify against the named reference.
- Desktop redesign rule: every transition is smooth, modelled on Home's hero carousel
  (layered parallax fades for navigation, crossfading states, animated lists, nothing pops). See "Smooth transitions" in
  `.agents/ui-consistency.md`. A redesign replaces the old layout; update tests that
  assert it instead of keeping it.
- When forking or delegating a frontend-design task (visual design, new or
  reshaped UI) to a subagent or session, tell it to invoke the
  `frontend-design:frontend-design` skill before designing.
- For Trakt API work (auth, sync, watch history, artwork), invoke the
  `trakt-api-guidance` skill if available, including when forking or
  delegating the task.
- For layout, loading, scrolling, gestures, and date presentation, read
  `.agents/ui-consistency.md`. Apply its shared invariants; keep feature-specific
  choices in the feature's documentation.
- Let each poster appear as soon as its own image loads. Do not gate shelves or
  grids on a whole visible row (cohort) being ready or add a shared viewport
  prefetch scheduler: that per-row reveal was removed because it made scrolling
  slow. Keep collection cover shapes independent of the global media poster
  style; preserve explicit exclusions.
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
- After implementing a desktop feature or visible fix, launch the actual MainKt
  application with `./gradlew :composeApp:run -Pnuvio.desktop.devName="<task>"`
  (labels the Dock/window with the task) and leave its development window
  open for the user's review before handing off. Compilation, automated UI tests,
  screenshots and probe windows do not substitute for this launch. Skip only if
  the user explicitly opts out or a concrete build/runtime blocker prevents it;
  report that blocker. Run focused tests as appropriate. Do not package a standalone
  `.app` or DMG unless explicitly requested; never replace the installed app.

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

## Packaged desktop helpers

External helper launches must resolve bundled source through
`compose.application.resources.dir` and durable local runtime paths, independent
of `user.dir`. Go3's `Go3RuntimePathsTest` is the reference. When a bug affects
release launches, check the packaged artifact from an unrelated working directory;
MainKt success alone does not verify the fix. Never bundle browser profiles or
credentials with helper resources.

## Repository boundaries

After the user approves the result, commit the approved changes and push them
to `origin/develop`.

Don't keep dead worktrees or branches. Once a task branch is merged into its
target (verify with `git merge-base --is-ancestor <branch> <target>`), stop your
own review app, then from the main checkout run `git worktree remove <path>` and
`git branch -d <branch>` (plus the remote branch if you pushed one). Never remove
a worktree or branch another session is still using, or one with unmerged or
uncommitted work.

Keep shared UI/state in commonMain and platform integrations in their existing
platform source sets. Follow the touched feature's localization and state patterns.
Use focused tests for changed behavior; avoid tests that only mirror cosmetic code.

Do not commit packages, caches, logs, local.properties, device identities, pairing
credentials, or signing keys. For TV receiver source/update work, read
`docs/tv-handoff/README.md` and `scripts/tv-handoff/README.md`. Do not contact or wake
physical TVs merely to validate an unrelated feature.

Treat these as project defaults, not a restriction on a user-requested redesign.
Update guidance when actual project conventions change; keep it short and concrete.

## Code search: CodeGraph and jbcontext

Use both indexes; they answer different questions.

- CodeGraph first (`codegraph_explore` MCP tool, or `codegraph explore "<query>"`
  in the shell) when you know symbol or file names, need callers, call paths or
  the blast radius of an edit, or are about to edit code. Treat its returned
  source as already read.
- `jbcontext search` when you only know the behavior, not the names, or when
  CodeGraph returns nothing useful (it matches words, not meaning).
- Use grep only to list every literal occurrence of a string.

<!-- jbcontext-instructions-start -->
# Tools

## Semantic Code Search (jbcontext)

`jbcontext search "<query>"` searches the project's code with a prebuilt index that ranks code blocks by relevance to the query and shows the code of the top results. The query is a short sentence describing the code and/or the names you know, e.g. `jbcontext search "where failed HTTP requests are retried"` or `jbcontext search "RetryPolicy maxAttempts handling"`. Narrow to a directory with `-p <path>`.

- `jbcontext search` is the main way to find code in this project, both by what it does and by the names you know. Use it alongside CodeGraph as described in "Code search" above; use `grep_search` only when you need every literal occurrence of a string.
- When you review the project codebase, search by behavior with `jbcontext search` and by names, callers and usages with CodeGraph. Use `grep_search` only to list every literal occurrence of a string.
- When you prepare tests, use `jbcontext search` to find the existing tests of the affected code and similar tests to copy the setup from.
- Run `jbcontext search` on its own and read its whole output: don't pipe it into `head`, `grep` or other commands. Open the files it returns with `open`, not with terminal commands.

<!-- jbcontext-instructions-end -->


## Progress reporting to the user

- Before the first edit, state in 2–3 plain sentences what you will change and
  which existing screen/component is the reference.
- Post a short progress note only at turning points (cause found, chunk finished,
  direction changed): what was found or changed, then what comes next and why.
  Name concrete files; do not narrate routine searches.
- Run long Gradle builds/tests so output streams (no `| tail`); say when a
  command may take minutes.
- Ask before destructive or ambiguous actions (git stash/reset, commits, pushes,
  account mutations) using a short question with concrete options.
- Finish with a structured handoff: a 1–2 sentence result, then `Changes`
  (what + why), `Verification` (exact commands and results), and `Notes`
  (unverified behavior, leftovers, follow-ups). Never claim visual checks you
  did not perform.
