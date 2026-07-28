# Spur change contract

## Required checks

Run these commands from the repository root before handing off a change:

```sh
ANDROID_HOME=/Users/ayalpani/Library/Android/sdk ./gradlew testDebugUnitTest
ANDROID_HOME=/Users/ayalpani/Library/Android/sdk ./gradlew assembleDebug
ANDROID_HOME=/Users/ayalpani/Library/Android/sdk ./gradlew lintDebug
```

For changes to Compose, MapLibre, tracking, camera, audio, video, permissions, or
persistence, also install the exact branch build on the registered Galaxy A54
and exercise the affected flow. Do not replace device checks with screenshots
of unrelated screens or with an emulator-only result.

## Refactoring rules

- Make one behavior-neutral extraction per commit.
- Compile and run JVM tests after every extraction commit.
- Run lint and a focused Galaxy-A54 smoke test at each larger checkpoint.
- Keep `MainActivity` limited to Android activity lifecycle and the app entry.
- Prefer files below 500 lines. Document a deliberate exception when a cohesive
  screen coordinator is larger.
- Do not combine file extraction with ViewModel, dependency-injection, database,
  navigation, or state-management migrations.
- Preserve UI text, semantics, modifier ordering, lifecycle call ordering, and
  persisted representations during structural commits.
- Widen top-level visibility only from `private` to `internal`, and only when a
  file or test boundary requires it.

## Persistence and platform invariants

- SQLite database: `spur.db`, schema version 4.
- Tables and columns in `TourStore`: `tours` and `track_points` must remain
  readable without migration during this refactor.
- SharedPreferences files and keys documented in
  `docs/main-activity-refactor.md` are stable external contracts.
- `TrackingService.EXTRA_TOUR_ID` remains `tour_id`;
  `TrackingService.ACTION_STOP` remains `app.spur.STOP_TRACKING`.
- The FileProvider authority remains `${applicationId}.fileprovider`.
- Every owned `MapView` must receive matching `onCreate`, `onStart`, `onResume`,
  `onPause`, `onStop`, and `onDestroy` calls in the existing order.
- Media recorder/player resources and temporary camera resources must still be
  released from their existing disposal paths.

## Definition of done

A refactoring slice is done only when its diff is structural, required checks
are green, affected device flows work, documentation reflects the new owner,
and no private device artifact is retained in the repository or test output.
