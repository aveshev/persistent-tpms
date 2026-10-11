# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Lint (Detekt)
./gradlew detekt

# Unit tests for a specific module
./gradlew :<module>:testDebugUnitTest --tests "com.example.ClassName#methodName"

# Compose screenshot tests
./gradlew verifyPaparazzi       # verify
./gradlew recordPaparazzi       # record new snapshots

# Instrumented tests (requires emulator/device)
./gradlew pixel2api34DebugAndroidTest

# Full CI-equivalent run
./gradlew build verifyPaparazzi pixel2api34DebugAndroidTest
```

## Workflow

For UI/UX changes, build and deploy to a physical device (`./gradlew :app:phone:installDebug`) and get the user's explicit sign-off on the change before touching CI or Paparazzi golden screenshots (`recordPaparazzi`, `verifyPaparazzi`). Don't record/update golden screenshots until the user has confirmed the UI looks right.

## Architecture

Multi-module Android app (Kotlin, Jetpack Compose, API 27–36) organized into three layers:

- **`core/`** — cross-cutting infrastructure: `common` (utilities), `database` (SQLDelight driver), `ui` (Compose/Material3/Navigation), `test` / `android-test` (shared test helpers)
- **`data/`** — domain models and persistence: `vehicle` (tyre/sensor/vehicle SQLDelight DB), `unit` (pressure/temperature conversions), `app` (app-level config)
- **`feature/`** — UI features: `main` (BLE scanning + tyre monitoring), `background` (background service), `qrcode` (MLKit barcode), `unlocated`, `shortcut`, `unit`, `android-auto`
- **`app/phone/`** — application entry point, wires everything together, Paparazzi tests live here

## Key Patterns

### Dependency Injection — Metro
The project uses [Metro](https://github.com/ZacSweers/metro) (not Dagger/Hilt). The convention per module is:

```kotlin
// Public surface — what other modules consume
interface FeatureComponent {
    val someUseCase: SomeUseCase
    companion object : FeatureComponent by InternalComponent
}

// Actual wiring — internal to the module
@DependencyGraph(AppScope::class, bindingContainers = [Bindings::class])
internal interface InternalComponent : FeatureComponent {
    @DependencyGraph.Factory
    interface Factory {
        fun build(@Includes parent: ParentComponent): InternalComponent
    }
    companion object : InternalComponent by createGraphFactory<Factory>().build(...)
}
```

### Database — SQLDelight
Room is not used. SQLDelight generates type-safe Kotlin from `.sq` files. Migrations use `.sqm` files alongside `.db` snapshot files in `data/vehicle/src/main/sqldelight/`. Every new migration must be accompanied by an updated schema snapshot.

Readings (`Reading.sq`) are stored as the advertisement itself (`raw`, bytes), plus only what it doesn't carry: the tyre, the time, the sensor ID and the signal strength. Pressure, temperature, battery and alarm are decoded from `raw` when read back, by the same decoders as a live scan (`AdvertisingPacket.decode()`), so a new or fixed decoder needs no migration and applies to past readings. Only add a column for what a packet doesn't carry or what SQL must filter on. Vehicle IDs are stored as their 16 bytes. The app bundles its own SQLite (requery, 3.49), but SQLDelight's newest dialect is 3.38: functions such as `unhex()` run through the driver in a Kotlin `AfterVersion` step (see `afterVersion10`), not in a `.sqm`.

**Two app IDs.** The fork ships as "Persistent TPMS" with the application ID `com.aveshev.persistenttpms` (only `applicationId` changed: namespaces and Kotlin packages stay `com.masselis.tpmsadvanced.*`). `-Papp.brand=tpmsadvanced` builds the same code as "TPMS Advanced" under `com.masselis.tpmsadvanced` instead (`./gradlew :app:phone:installDebug -Papp.brand=tpmsadvanced`), with the stock black-on-green icon: use it to try a change on a phone without touching the Persistent TPMS the user runs there. Upstream downport branches, and fork branches that don't contain the rebrand, always install as `com.masselis.tpmsadvanced`. The two are separate apps on a phone, each with its own data, so the version checks below apply per app ID: use the ID of the build being installed in `run-as` and `-p`.

**Database version on test devices.** A build's database version is its highest `migrations/N.sqm` plus one: `develop` is at version 12 (`11.sqm`, snapshot `12.db`). Before `installDebug` on a phone that has app data, compare it with the device's version: back up the data (`adb exec-out run-as com.aveshev.persistenttpms tar cf - databases shared_prefs > <dir>/data.tar`), extract it, and read `sqlite3 databases/car.db "PRAGMA user_version;"`. Reading the tar keeps the WAL with the database, so the value is current.
- A build **below** the device's version crashes on launch with `Can't downgrade database`. Branches cut from `upstream/develop` carry upstream's own, shorter migration list, so a downport build hits this on a device whose `com.masselis.tpmsadvanced` data came from a fork build.
- A build **above** it migrates the data, and older builds can't open it afterwards: another session reviewing an older build on that phone loses it.
- Equal versions are only safe if both builds have the same migrations. The numbers were reused once (below), so the same number doesn't guarantee the same schema.

A crash on launch leaves the data untouched, because the failed upgrade rolls back. Don't try to fix a mismatch on the device. Install a build with the right version, or clear or restore the data. The user has a local backup/restore script at `~/tpms-backups/tpms-data.sh` (outside the repo, `restore <backup-dir> -s <serial>`). Ask before overwriting a device's data.

**Rear pressure migration squash (2026-09-26, aveshev/persistent-tpms#27, merge commit `b52d6fa`).** The rear pressure columns used to be two migrations, `5.sqm` + `6.sqm`. They are now a single `5.sqm`, matching upstream PR VincentMasselis/TPMS-advanced#469. Later migrations reused the numbers from `6.sqm` on, so a branch that doesn't contain `b52d6fa` has a different `6.sqm` (`separateRearPressure`) than `develop` (pressure calibration). Installed on a newer device, such a build crashes with `duplicate column name: separateRearPressure` or `Can't downgrade database`. Check with `git merge-base --is-ancestor b52d6fa HEAD`, and merge `develop` into the branch if it fails.

### Demo Mode
No build flavors — a single build. Demo mode (used for Play Store screenshots/testing) is a runtime setting toggled from the app's settings screen, backed by `ScannerDatabase.isDemo` in `data/vehicle`.

### Mock BLE packets
Debug builds accept BLE advertisements over adb, merged into the real scan so they go through the same scan filters and decoders as over-the-air packets (`data/vehicle/src/debug/.../MockAdvertisements.kt`; the release source set is a no-op). Either have the app encode a reading (`MockSensor.kt`, round-trip tested against the decoders), or replay raw bytes:

```bash
adb shell am broadcast -a com.masselis.tpmsadvanced.MOCK_ADVERTISEMENT -p com.aveshev.persistenttpms --es brand pecham --ef kpa 230 --ef celsius 21 --ei battery 30 --es address AA:BB:CC:DD:EE:01
adb shell am broadcast -a com.masselis.tpmsadvanced.MOCK_ADVERTISEMENT -p com.aveshev.persistenttpms --es bytes <hex>
```

- `brand`: `pecham`, `bekubee_ky`, `wicarlink`, `bekubee_tpms` or `sysgration`. `kpa` is required; `celsius`, `battery` (decivolts, a percentage for Sysgration), `id` and `rssi` are optional. Sysgration also takes `wheel` (`FL`/`FR`/`RL`/`RR`) and `alarm` (`--ez`); `flags` (`--ei`, 0 to 255) sets each brand's raw status byte, see `MockSensor`'s KDoc.
- Pecham and Bekubee KY derive the sensor ID from `address`, so keep it fixed across packets meant to come from one sensor (the others use `id`; Sysgration IDs are multiples of 256).
- Values are checked against what each format can carry, and numbers can be sent with `--ei` or `--ef`. A value sent with the wrong flag is rejected, not defaulted.
- Mock sensors bind through the normal screens, by tapping a wheel without a sensor ("Tap to assign"): any brand with "Scan via Bluetooth", whose steps follow the pressure: answer "in my hand", send the sensor near 0 kPa then at the tyre's pressure (on), near 0 (off), back up (on), each value 15 kPa or more apart, from one `address` (Pecham, Bekubee KY) or `id`. Sysgration also with "Assign detected sensor" at the wheel it advertises, where it shows "(detected)" for 10 minutes. An unassigned sensor's readings are never shown, stored or alerted on. A repeated identical packet is dropped as part of a burst, so change a value (`--ef kpa 231`) to resend. Only Sysgration advertises an alarm, shown as "Leaking?" with the read pressure. The other brands report their battery as a voltage (`--ei battery`, decivolts), compared to the vehicle's low voltage alarm (2.6 V by default, lowered in the cold, see `Voltage.alarmAt`). Sysgration reports a percentage (`--ei battery`, 0 to 100), compared to the low battery alarm (10 % by default).

Without adb, debug builds with the debug options on have "Simulate a reading" in the main screen's menu (`SimulatedReadings`, `SimulateReading.kt`): it sends a reading for a tyre of the current vehicle as its bound sensor, merged into the scan after the decoders, so it's shown, stored and alerted on like a real one. A tyre without one gets a Sysgration sensor advertising it, which is only detected there until assigned.

The app must be scanning (main screen open, or the background service running) with Bluetooth on. `adb logcat -s MockAdvertisements BluetoothLeScannerImpl` shows why a packet was dropped, or the decoded `Sensor content`. Real sensors' bytes are logged the same way (`Sensor found during scan`), so a real packet can be captured and replayed.

### Convention Plugins
Reusable Gradle config lives in `buildSrc/src/main/kotlin/` as convention plugins (`android-app`, `android-lib`, `compose`, `detekt`, `gitflow`, `monitor-resource`). Apply these to new modules rather than duplicating config.

### Versioning
A custom `GitflowPlugin` (git-cli backed, no JGit) manages semantic versioning automatically from the git-flow branch structure and validates the branch model (release must fork from `develop`, hotfix from `main`, no duplicate tags/branches, no foreign commits). Releases are cut via `./gradlew createRelease -Pgitflow.bump=major|minor|patch` on `develop` (`./gradlew createHotfix` from `main`, always patch), followed by `./gradlew pushGitflowBranch` — or via the `Gitflow` GitHub Actions `workflow_dispatch`, which runs the same tasks. A push to `main` also auto-opens a merge-commit back-merge PR into `develop`. See [CONTRIBUTING.md](CONTRIBUTING.md) for the full flow.

### Branching
New feature/fix work always branches from `develop`, never `main` — before creating a new branch, run `git checkout develop && git pull` first. `main` only holds released code and is the source for hotfix branches (see Versioning above).

### Remotes
This checkout has two remotes: `origin` (the user's fork, `aveshev/persistent-tpms`, formerly `TPMS-advanced-NE`) and `upstream` (`VincentMasselis/TPMS-advanced`, the original project). Nothing from this project — branches, pushes, PRs — should go to `upstream` unless specifically asked to do so. Default to `origin` for pushes and PRs (`gh repo set-default aveshev/persistent-tpms` keeps `gh pr create` from guessing wrong).

Features are built in this fork, then downported onto branches cut from `upstream/develop` for upstream PRs. Those branches carry upstream's older UI and upstream's own CLAUDE.md, which lacks these rules. Before opening an upstream PR, or pushing to a branch that is already the head of one (draft or not), the exact commit being proposed must have been installed on a physical device and signed off by the user. This applies even when a cloud session prepared and tested it. If there's no record of that sign-off, stop and ask. Build downports in a separate worktree, and check `migrations/` before installing across the fork and upstream bases, since their database versions can differ.

## Code Style

Chaining is the default control flow in this codebase, not an occasional idiom. Favor it over imperative statement sequences with intermediate named `val`s.

- **Chain, don't name.** Prefer scope functions (`let`, `also`, `run`; `apply`/`with` are rarer here) over declaring a `val` and using it a few lines later. Each intermediate value should live only inside the scope that needs it, then disappear. Chains 3-5 calls deep are normal, including for side-effecting code:
  ```kotlin
  database.selectAll()
      .execute()
      .firstOrNull { it.uuid != vehicle.uuid }
      ?.also { currentVehicleUseCase.setAsCurrent(it) }
      ?: error("Cannot delete the last vehicle in the database")
  ```
  Guard clauses / early returns don't need to be forced into a scope-function shape.
- **Extension functions are the chain-enabling tool.** When an API isn't chain-friendly, wrap it in a small extension function/property on its receiver instead of a free helper function, so the call site reads as a verb in the chain (e.g. `file.asToml()`, `Query.kt`'s `.asList()`/`.asOne()`, `Pressure.kt`'s `Float.kpa`/`.bar`). Put these in a file named after the receiver, never a generic `Extensions.kt`.
- **Extraction bar: 2+ call sites, or a real conceptual boundary.** Don't extract a `private fun` (or local `fun`) that's only called from a single call site — inline that logic into the caller instead. Exception: in long Compose screens, splitting a giant composable into several single-call-site `private fun` helpers (one per list section/dialog/etc.) is fine when it measurably helps readability — the goal is minimizing reader context-switching, not a hard rule.
- **Explicit visibility always.** Write `public`/`internal`/`private` explicitly, even on top-level declarations — never rely on the default.
- **Modeling**: value classes (`@JvmInline`) for zero-cost domain primitives, data classes for plain records, sealed interfaces/classes for state modeling (not for error channels). `val` almost exclusively; `var` only for local Compose state or explicit mutable accumulators.
- **Errors**: prefer `error(...)`, `require(...)`, `requireNotNull`/`checkNotNull` (with a lambda message when useful) over custom exception types or `Result`/`Either` wrappers. Recover from stream-level failures with `.catch { emit(FallbackState) }`.
- **Null handling**: elvis + scope functions instead of `if` (`x?.let { ... } ?: ...`), `takeIf` to turn sentinel values into `null`. Don't shy away from nullable types in domain APIs.
- **Boolean negation**: never use the prefix `!` operator to invert a condition; use a trailing `.not()` instead (e.g. `condition.not()` rather than `!condition`).
- **Comments**: feel free to comment more than the rest of the codebase does when it helps explain what generated code is doing — the user will trim what turns out to be unnecessary. When writing code meant to match the existing human-authored style long-term, keep comments purposeful (workarounds, why an exception is swallowed, non-obvious API contracts) rather than restating what the code does.
- **File organization**: one primary public type per file, matching the filename. Nest small, tightly-coupled types inside the type that owns them rather than splitting into sibling files.
- **Formatting**: 4-space indent, leading-dot chain wrapping (one call per line), fully-expanded imports (including importing enum constants directly so they can be used bare in `when`). Prefer `@Suppress` (`MaxLineLength`, `LongMethod`, etc.) over force-fragmenting cohesive logic to satisfy linters.
- **Compose**: public composables take a trailing `modifier: Modifier = Modifier` and often a `viewModel: XViewModel = viewModel { ... }` default for testability. Split a public stateful composable from a private stateless one that takes the resolved state, to support previews with mock state. Colocate `@Preview` functions (one per state variant) in the same file.
- **Tests**: JUnit4 + MockK + Turbine. `@Before fun setup()` plus a private `test()` factory built from fields set in `setup()`. Backtick natural-language names for scenario/behavior tests, camelCase for simple state-transition tests. Prefer `assertIs<State.X>(...)` over instanceof-style checks on sealed state.
