# Raw location diagnostics

`RawLocationDiagnostics.kt` owns the additive `raw_locations` table in `spur.db`.
The database remains version 4; `TourStore.onOpen` creates the table and indexes
for existing installations. Existing `tours`, `track_points`, route rendering,
and filter thresholds are unchanged. `TourStore` remains the existing cohesive
SQLite owner (already over 500 lines); this change does not restructure it.

Each measured location reaching active-tour ingestion stores its tour ID, original
coordinates, fix timestamp, processing timestamp (`received_at`), reported
accuracy (NULL if unavailable), monotonic fix time when available, provider,
and ingestion decision. This is private location data, not a coordinate-free log.
It stays in app-private storage with the existing disabled Android backup policy.
Explicit whole-database exports also contain this table.

Decisions distinguish accepted fixes, stationary merges, poor accuracy,
non-monotonic timestamps, implausible speed, movement below the noise floor,
startup waiting, stationary-exit waiting, inactive tours, stale sessions, and
manual-location overrides. A pending exit can later be accepted as part of the
three-fix exit buffer. Decisions describe ingestion at that instant; later pause
clustering or user edits can change the route without changing this audit trail.

Measured automatic-start inputs are recorded as PRE_ROLL_ACCEPTED or
PRE_ROLL_DUPLICATE on replay. These rows represent replay attempts, not new GPS
callbacks. Synthetic Home endpoints and simulated locations are not raw GPS fixes.
The pre-tour armed/confirmation buffer keeps its existing behavior: measurements
filtered out before becoming automatic-start inputs are outside this tour audit.
No received sample can be recorded if Android never delivered it, and abrupt
process termination can discard callbacks still waiting on the tracking queue.
This data cannot by itself identify radio interference or a jammer.

TrackingService submits each Android-delivered batch to its existing background
tracking thread. One SQLite transaction covers the batch's route and raw rows;
there is no additional timer or RAM-only buffer. Single-fix callbacks commit
immediately rather than risking another unflushed buffer. `TourStore` serializes
transactions and deletion, so late callbacks cannot recreate data for deleted tours.

Rows expire 14 days after ingestion. Cleanup runs on database open and at most
once per day during normal ingestion (so continuous operation may retain rows
up to one cleanup interval longer). Deleting a tour deletes its raw rows in the
same transaction. Normal route/history queries never read this table.

Validation: JVM filter-decision regression and isolated Android database test for
v4 table creation, rejected-point preservation, unchanged route, reopen,
retention, deletion, and late callbacks. Never run instrumentation on the personal
Galaxy; use the dedicated Spur_API_36 emulator. Device smoke checks use in-place
installation only and must preserve existing tours and media.
