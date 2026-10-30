# Sports module implementation and verification

This update extends the existing native Kotlin Android app. Sports remains the fifth destination in the existing navigation, visually between Diet and Progress. No Diet design changes were made. Home, Progress and Profile have only Sports data/status links, shared settings and checklist integration.

## Implementation steps

1. **Inspect architecture.** Read the existing activity, Sports/Workout dashboards, navigation, preferences, manifest, tests and offline packager. The supplied folder had no Git metadata; a source-only baseline was committed and `feature/sports-module` created. Signing material, APKs, build caches and runtime data are ignored.
2. **Dashboard.** Replaced `WorkoutDashboard.kt` with the black/dark-green/lime Sports UI, measured/manual step goal ring, current workout, six exercise cards, weekly records, streak and quick actions. `MainActivity.kt` retains the existing bottom navigation and adds Sports header actions.
3. **Reusable trackers/timers.** `SportsCore.kt` contains Android-independent models and transitions. `ActiveTimer` uses elapsed realtime plus Android boot identity, excluding pauses and wall-clock changes. `ExerciseCountdown` powers rounds and rest. The same adjustable rest controls are used in exercise sessions and the standalone timer.
4. **Bodyweight exercises.** Jumping Jacks, Mountain Climbers (rep/timed modes), Knee Push-ups and Push-ups have specific instructions, native two-pose illustrations, persistent reps, undo, complete/skip sets, rest notifications, target adjustment, comparison and personal-best data. Completion uses actual saved quantities, and skipped sets do not count as completed work. No camera counting is present.
5. **Walking/jogging.** Indoor/manual and treadmill modes run without GPS. Outdoor sessions request foreground location on demand and use `SportsTrackingService`, declared as a location/health foreground service, with an ongoing notification and Pause action. GPS rejects fixes older than 15 seconds, accuracy worse than 30 m, stationary jitter, gaps over 30 seconds and speeds exceeding the activity threshold. Pausing stops location collection; resuming starts a new location baseline. Current speed appears only with suitable speed information. Routes stay local; no map-provider account is required. Walking/jogging distances are separated within mixed plans.
6. **Plans/recovery/history.** Weekly defaults match the requested schedule. Each day supports custom names, recovery status, add/remove/reorder exercises, targets, rest and copying to another day. Recovery has gentle movement, a checklist, sleep entry using the existing shared daily key and an optional timer. A review stage precedes final save; the same session ID is upserted to prevent duplicate saves. History supports Today/This Week/All Time, session details, confirmed corrections (including dates, steps and sets) and confirmed deletion.
7. **Analytics/integration.** Nine interactive chart metrics use repository/sensor records and empty states. Consistency includes explicitly completed recovery days. `SportsIntegration.kt` exposes shared status and attach/detach listeners for Home/Progress. Profile exposes Sports targets, step goal, frequency, preferred time and rest settings. Sports completion contributes to the existing Home checklist without copying session records into another page. CSV export includes completed Sports sessions.
8. **Tests/build.** `tests/SportsModuleCheck.kt` and `tools/test_sports.py` exercise the pure domain and disk-backed repository. `tests/android/SportsInstrumentation.kt` and `tools/test_sports_android.py` exercise the UI, SQLite, permissions, service lifecycle and full process restart. Instrumentation is included only when explicitly building with `--instrumented`; it is excluded from the delivered debug APK.

## Storage and migration

The existing app uses SharedPreferences, not Room. Sports adds a native SQLite database, `sports.db`, schema version 1. Transactional aggregate records store sessions (including sets, targets, timing, optional sensor/GPS fields and route), weekly plans and the reusable timer. Serializable model IDs are explicitly versioned; future field/schema changes need compatible additive migration. Existing preferences are retained. Legacy `sport-session-*` and `workout-set-*` records are imported once with stable IDs; original keys are not deleted. Missing legacy timestamps, durations, sensors and calories remain unknown rather than being inferred. Imported set-only records retain zero recorded duration, explicitly labelled as not originally recorded.

Settings and daily sensor observations reuse the existing `fit100` preference file. No tracking data is bundled in APKs. Preview screenshots contain emulator-only test records produced during automated tests.

## Reproduce

From the parent workspace directory:

```powershell
python Fit100Android/tools/test_sports.py
python Fit100Android/tools/build_apk.py --qa --instrumented
python Fit100Android/tools/test_sports_android.py --serial emulator-5556
python Fit100Android/tools/build_apk.py --qa
```

The Android test runner resets location permission flags on the selected test device and creates/deletes test workouts; use a dedicated emulator. It runs a second instrumentation process to verify a saved workout survives a full app process stop. It pulls screenshots into `previews/sports-module/sports-qa`.

The offline packager uses the installed Kotlin 2.0.21 compiler, Android API 35 SDK tools, D8, zipalign and apksigner. It does not require resolving the unavailable Gradle plugin from the network. Debug output is `Fit100-sports-debug.apk`, signed with the existing local debug identity; version code 11, version name 1.0. The separate instrumented APK is for tests only.

## Verification limits

Real-world GPS movement, physical step-sensor accuracy, vibration/sound on physical hardware, battery endurance and manufacturer-specific background restrictions require physical-device testing. No simulated GPS points or heart-rate values are shown as measured data. Step collection covers observed intervals while the app is open or an explicit step-capable activity service is running; it is not presented as a guaranteed all-day Health Connect total. Sensor denial/unavailability has a manual-entry alternative. Calories are labelled MET-based estimates derived from saved weight and recorded time; they are not measured expenditure.

Foreground tracking is started only from a visible, user-initiated session. A stopped/killed GPS service is not silently restarted. On reopening, an interrupted outdoor session pauses at its last checkpoint; resume requires the user to reconnect. Time survives app recreation/backgrounding on the same boot. A device reboot pauses running clocks at their last checkpoint. Exact-alarm access and Android power policy can delay completion alerts; countdown values themselves use the monotonic clock.

A cold-boot Android System UI ANR was observed in the emulator, outside the Fit100 process. It was restarted before final visual review. Native illustrations are supplied instead of copying reference photographs.

Android implementation references: [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [motion sensors and step counters](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion), [location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions).

## Recorded results — 30 October 2026

- Pure JVM domain/repository suite: **56 assertions passed**. Includes clock rollback/reboot, pause/resume, elapsed background time, countdown adjustment after expiry, rep/undo boundaries, completed/skipped sets, quantity-based completion, observed-exercise calorie weighting, duplicate saves, disk reopening and deletion, plan editing/recovery and GPS accuracy/jump filtering.
- Android API 37 emulator: **35 instrumentation assertions passed**, plus a separate **full app-process restart persistence assertion**. Actual UI controls exercised walking pause/resume/save, Android location denial, indoor jogging, outdoor foreground-service start/pause/resume/finish, all six trackers, Add/Undo, three complete jumping-jack sets, rest start, timed Mountain Climbers pause/resume, plan editing through the UI, recovery, history/analytics entry and background countdown expiration. Legacy activity/set imports were checked for idempotency and distance preservation in an isolated test database.
- Final debug APK: compilation, resource linking, D8, zip alignment and v2/v3 signature verification passed. `aapt2` confirmed package `com.fit100.tracker`, version code 11, version name 1.0, min SDK 26, target SDK 35 and debugging enabled. No test instrumentation class or manifest entry is present in the delivered APK.
- Final debug APK was installed on the emulator and visually reviewed at normal width and approximately 320 dp with 1.3x font scaling. Bottom navigation and content remained readable; content scrolls and top tabs scroll horizontally. Screenshots are in `previews/sports-module/` and `previews/sports-module/sports-qa/`.
- SHA-256 (`Fit100-sports-debug.apk`): `ACE3A4B1E607C112E6C06B1C0C4B944DDDA2AA4B572B3A8F89EED77E3460EEF5`.
- Remaining verification: physical walking/jogging GPS movement, sensor-step accuracy, locked-screen alert sound/vibration on hardware, long-running battery behavior and manufacturer-specific service restrictions. Emulator service/permission tests do not establish GPS accuracy.
