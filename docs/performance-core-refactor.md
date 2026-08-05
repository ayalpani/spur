# Performance core refactor

This branch applies the verified core findings from the August 2026 Spur
performance review without changing the database schema, persisted point
representation, or Android intent contracts. It additionally makes the
existing stationary-cluster state visible as an intentional pause marker.

## Runtime boundaries

- `TrackingService` receives location callbacks on the main looper so departure
  confirmation and Android lifecycle state keep their existing owner. Active
  fixes are copied to one serial `HandlerThread` before persistence. Each active
  session has an ID; late worker notifications and automatic-finish results are
  ignored after a session change.
- `TourStore.collapseStationaryWindow` updates `distance_meters` with the local
  difference between the replaced tail and its cluster representative. It does
  not read the complete tour inside the write transaction.
- Five-minute stationary clusters remain part of the active tour and surface as
  violet pause markers with their duration. A 25 m pause spread and an
  accuracy-aware exit replace the former 100 m stationary radius, so the first
  resumed fixes are buffered and written instead of being swallowed.
- Tracking notifications read only `id`, `started_at`, and `distance_meters`.
  They are posted again only if the formatted visible text changes.
- `SpurApp` polls a `TourRevision` and reloads all points only for a first display
  or a changed revision. This intentionally remains a simple revision-plus-full-
  reload design; point deltas and a repository/state migration remain out of
  scope.
- Tour and road polling is inactive below `RESUMED`. Road fingerprints run on
  initial display and after recognized tour changes. The active duration clock
  belongs to `TourModeHeader`, not the map-page coordinator.
- One `TourPresentation`, calculated on `Dispatchers.Default`, supplies snapped
  map moments, ordered waypoint entries, and the point-ID lookup. Request
  generations prevent stale calculations from replacing newer input.
- Marker media identity excludes coordinates. Coordinate-only movement rebuilds
  MapLibre features while retaining prepared marker bitmaps and photo previews.
  Camera JPEG confirmation previews decode on `Dispatchers.IO`.

## Persistence and platform invariants

- SQLite remains `spur.db`, schema version 4, with readable `tours` and
  `track_points` tables and no migration.
- `TrackingService.EXTRA_TOUR_ID` remains `tour_id` and `ACTION_STOP` remains
  `app.spur.STOP_TRACKING`.
- SharedPreferences names and encodings, FileProvider authority, MapView
  lifecycle forwarding, and media disposal paths are unchanged.

## Performance evidence

The comparison uses the same API 36 emulator and a synthetic active tour with
6,000 points. It records a 60-second stationary phase and a 60-second phase
with one synthetic location change every four seconds. A separate 15,000-point
SQLite run is retained as a scaling stress case. Measurements include complete
point reload count and time, road-fingerprint work, runtime GC signals, and
Android frame statistics. The comparison image is generated outside the
repository and contains no Galaxy A54 data.

The instrumentation was applied only to disposable benchmark builds. It is not
part of the application or this branch. Both phases began after the map and
synthetic tour had loaded, and Android frame statistics and log buffers were
reset immediately before the timed window.

| 60-second phase | `main` | Refactor |
| --- | ---: | ---: |
| Idle: complete point reloads | 58 | 0 |
| Idle: cumulative reload time | 1,009 ms | 0 ms |
| Idle: road fingerprints | 58 | 0 |
| Idle: runtime GC signals | 40 | 1 |
| Idle: frame time p95 | 53 ms | 16 ms |
| Changes: complete point reloads | 60 | 14 |
| Changes: cumulative reload time | 385 ms | 100 ms |
| Changes: road fingerprints | 61 | 14 |
| Changes: cumulative fingerprint time | 71 ms | 46 ms |
| Changes: runtime GC signals | 6 | 1 |
| Changes: frame time p95 / p99 | 81 / 97 ms | 61 / 77 ms |

Fifteen update signals were issued during the change phase. The first retained
the existing revision, leaving fourteen real changes. The refactor therefore
performed exactly one complete reload and one road fingerprint per recognized
change, and none for the no-op. `main` continued both jobs on every polling
tick. Android's legacy janky-frame count was 37 on `main` and 67 on the branch
in this headless active phase; this counter moved opposite to p95/p99 and was
too scheduler-sensitive to claim as an improvement. The reload, fingerprint,
GC, and percentile results are the decision evidence.

For the SQLite-only stress check, one hundred revision reads versus one hundred
full point reads took 0.07 s versus 0.63 s at 6,000 points, and 0.16 s versus
1.57 s at 15,000 points. The exact branch build also opened the 15,000-point
tour on the emulator without an ANR or application crash.

## Device verification

The exact branch build was installed data-preservingly with `scripts/spurctl
start` on the registered Galaxy A54. The smoke test covered a manual tour start,
multiple stored location fixes, the waypoint rail, the foreground notification,
background/foreground restoration with the tracking service still active, the
moment composer and an emoji moment, live CameraX preview, JPEG capture and
confirmation preview, completion, and automatic-service re-arming. The
temporary photo was discarded and the smoke-test tour, including its test
moment, was deleted afterward. No device screenshot or private device artifact
was retained.
