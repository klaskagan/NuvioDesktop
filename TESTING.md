# Desktop tests

Run commands from the repository root. Desktop tests run on the computer, not an
Android emulator. `commonTest` tests are included in `:composeApp:desktopTest` too.

## Prerequisites on this Mac

Use a full JDK with JNI headers. The installed JDK 17 works:

```sh
export JAVA_HOME=/usr/local/opt/openjdk@17
```

For macOS native compilation, `xcrun --find clang++` must succeed and
`$JAVA_HOME/include/jni.h` must exist. Android Studio's bundled JBR may run Gradle
but lack those headers, causing `buildMacosHistoryGestures` to fail before tests
start. The Gradle JDK is distinct from the selected Android emulator or Project SDK.

## Terminal

Always use the isolation init script. It supplies a temporary app-data home and
an in-memory Java preferences factory for authentication. Changing `user.home`
alone does not isolate macOS Java preferences.

All desktop tests:

```sh
./gradlew -I scripts/desktop-ui-tests.init.gradle :composeApp:desktopTest
```

Calendar unit and UI tests:

```sh
./gradlew -I scripts/desktop-ui-tests.init.gradle :composeApp:desktopTest \
  --tests 'com.nuvio.app.features.trakt.TraktCalendarLocalTimeTest' \
  --tests 'com.nuvio.app.features.trakt.TraktCalendarTest' \
  --tests 'com.nuvio.app.TraktCalendarUiTest'
```

One test method (keep the quotes, especially for names containing spaces):

```sh
./gradlew -I scripts/desktop-ui-tests.init.gradle :composeApp:desktopTest \
  --tests 'com.nuvio.app.TraktCalendarUiTest.bothOrientationsKeepLabelsAndOpenRealMovieAndSeriesDetails'
```

Multiple `--tests` options select the union of those filters. Omit all filters to
run the whole desktop suite. `--rerun-tasks` forces tasks to execute again; it is
normally unnecessary because Gradle detects changed source files automatically.

## Android Studio

1. Open **Settings → Build, Execution, Deployment → Build Tools → Gradle**.
2. Set **Gradle JDK** to a full installed JDK, such as
   `/usr/local/opt/openjdk@17`. If it is not listed, use the JDK picker to add it.
   The installed Temurin 17 JDK is another suitable option.
3. Apply the change and sync Gradle if prompted.
4. In the run-configuration dropdown choose **Trakt calendar tests (isolated)**
   or **Desktop tests (isolated)**, then click Run or Debug. These shared Gradle
   configurations are stored in `.run/` and include the init script.

For an existing gutter-generated Gradle configuration, open **Edit Configurations**,
set **Gradle project** to the repository root, and add
`-I "$PROJECT_DIR$/scripts/desktop-ui-tests.init.gradle"` to its Gradle arguments.
Keep its `:composeApp:desktopTest` task and `--tests` filter in the tasks field.
For IDE test-mode configurations, put `--tests` next to the task (as gutter-generated
configurations do), rather than only in the separate script-arguments field. Use a **Gradle** run
configuration rather than an Android app configuration. A green gutter icon alone
does not ensure the isolation script has been added.

## Reading the result

- **Passed:** assertions ran and succeeded.
- **Skipped:** a test did not verify its assertions. Some full-app tests use
  `assumeTrue` to skip when isolation is missing; others use `check` and fail.
- **UP-TO-DATE / FROM-CACHE:** Gradle reused existing results rather than executing
  that task again.
- **Native build failure:** prerequisites failed before the test JVM started;
  inspect the compiler error above Gradle's final failure summary.

`BUILD SUCCESSFUL` can include skipped tests. Inspect test counts and skipped
results in `composeApp/build/reports/tests/desktopTest/index.html`; XML results
are in `composeApp/build/test-results/desktopTest/`. Calendar screenshots are in
`composeApp/build/reports/trakt-calendar/`.

The portrait date/time visibility change is covered by the existing real UI test:
portrait omits the secondary release label; landscape displays it; the title and
episode label remain visible with optional labels disabled. No boolean helper is
introduced solely to repeat the implementation in a unit test.

## Findings from the 2026-10-25 test review

Running without the init script caused 32 UI/artwork tests to stop at their isolation
checks. The calendar UI tests instead skip through `assumeTrue`, so their absence
does not make Gradle fail. With the init script, all 12 calendar tests executed and
passed, including portrait/landscape release-label visibility.

The full isolated run reported 1,833 tests, 2 failures and 2 skips:

- `WatchProgressIdentityTest`: the legacy completion fixture uses a 1,000 ms video
  and expects completion at 940 ms. Current `WatchingPolicies` intentionally treats
  durations below 121,000 ms as short placeholder clips and never marks them
  complete. A completion-migration fixture should use a realistic duration (for
  example 1,000,000 ms with a position of 940,000 ms). This is independent of the
  calendar presentation and was not changed during this review.
- `PluginRuntimeDesktopTest`: its 32-concurrent-scraper stress test hit its existing
  60-second timeout in the full suite. Both plugin tests passed in a smaller rerun;
  the timeout was not reproduced there. No plugin implementation was changed.

`ServerUserStateProjectionTest` also passed with isolated storage in both runs;
its failure from the original non-isolated invocation was not reproduced.
The full-suite HTML report from this review is preserved under
`composeApp/build/reports/calendar-test-diagnosis/full-suite/index.html`.
