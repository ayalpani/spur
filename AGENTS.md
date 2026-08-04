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

Never run `connectedDebugAndroidTest` or another uninstalling/clearing test task
on the personal Galaxy A54 while it contains local Spur data. Android
instrumentation tests must run on a disposable emulator or a dedicated test
profile. Use only `adb install -r`/`spurctl start` for the personal device after
confirming that the command preserves app data.

## Design system

- Use `SpurPrimaryButton` for the promoted action in sheets and forms.
- Its background and foreground come from the user's selected
  `LocalMapControlColors`; the default is black with white text.
- Primary labels use the component's shared `titleLarge` semibold typography.
- Use `SpurSecondaryButton` for secondary actions. It matches the primary
  button's 60 dp height and typography, with a transparent background and a
  1 dp black outline at 50% opacity.
- Use the compact regular-weight content style for the paired moment-picker
  buttons so all four labels stay on one line with their smaller icons.
- In vertically stacked dialog and confirmation actions, place the primary or
  confirming action above the secondary cancel/discard action.
- In bottom-sheet menus, use `SheetMenuNavigationItem` for destinations: no
  leading icon and a trailing chevron. Use `SheetMenuActionItem` for immediate
  actions: a leading Lucide icon and no chevron. Separate mixed navigation and
  action groups with `SheetMenuDivider`.
- Route every bottom-sheet-to-bottom-sheet change through the shared
  synchronized transition. The incoming and outgoing sheets must start in the
  same frame and use the same Material sheet motion spec; never await one sheet's
  closing animation before showing the next sheet.
- When an icon and text jointly label one action, render the icon at the full
  semantic foreground color and the label at `IconTextLabelAlpha`. Apply this
  through shared buttons and action-menu rows; do not dim standalone labels,
  headers, navigation chevrons, or decorative icons.
- Use `MapIconButton(secondary = true)` for secondary controls drawn over a
  map. All secondary map controls use `secondaryMapControlStyle`: it inverts
  the selected colors, gives only the background 25% transparency, and adds
  the shared 3 dp outline using the secondary font color at 25% opacity. Keep
  the shared round `Surface` so pressed feedback fills the complete 60 dp
  control.
- In the main map rail, keep the bottom location/follow control primary. Place
  the secondary blue moment-add control immediately above it. Place the
  manual-location reset above the map-style switcher on the left.
- Treat the map-style switcher and the inactive `Tour starten` control as
  secondary map controls using the same shared outline.
- Keep the moment-add control above the vertical map zoom control, with the
  zoom control directly adjacent to the bottom location/follow control.
  Its chevrons move one zoom level, while a drag that begins anywhere inside
  the control adjusts fractional zoom continuously and remains active beyond
  the control bounds. Give the whole interaction one short touch haptic and an
  active-color state. Keep it compact as three stacked parts: chevron up,
  a light vertical-ellipsis grip, and chevron down. Keep the middle row only as
  tall as its number or icon needs, but leave both chevrons centered in their
  ordinary generous touch areas. Fade the current integer zoom level from 1 to
  20 in when the control is touched. Keep it visible for one second after
  release, then fade back to the ellipsis. Keep both elements permanently
  overlaid in the same fixed slot and animate only complementary alpha values so
  the transition cannot affect layout. Let that number track continuous zoom by
  rounding to the nearest level. Render it bold at the persisted default level
  and regular with reduced foreground alpha elsewhere. Tapping a
  non-default middle control persists exactly that displayed integer level;
  tapping the current default is a no-op without feedback.
  Ordinary map gestures, arrows, and zoom drags never replace the default.
  Animate the location/follow control back to that default, and defer
  alternate-preview and road-detail rebuilds until drag release.
- Fade the main map controls out and back in with
  `MotionDurationDefaultMillis` around a direct map gesture; do not hide them
  abruptly through a map z-index change.
- Cancel the alternate-style map preview while a direct map gesture or waypoint
  rail scroll is active. Rebuild it only after a stable idle delay so its
  snapshotter never competes with interactive map movement.
- Keep the clickable Spur Asterisk in the upper-left map area as the entry to
  Home. It inherits the location movement rotation, including the Home-Zone
  stop rule. Keep the lower-right location/follow action behavior but render a
  static Lucide `PersonStanding` icon there; never rotate both controls.
- In the active-tour header, precede "Unterwegs" with a static Lucide connected-
  nodes icon. Do not use or animate the Asterisk in that header.
- Keep "In G-Maps öffnen" below the main menu's single divider, on the same
  action level as operations on the displayed tour such as "Tour löschen".
  Transfer the current map center, the nearest supported integer zoom derived
  from MapLibre's 512 px and Google Maps' 256 dp zoom-zero world sizes, and the
  road/satellite basemap through the official Google Maps URL. Google Maps
  cannot receive map bearing or tilt.
- Offer "Tour umbenennen" directly below "In G-Maps öffnen" for archived
  tours. Edit the title inline in the archive header with immediate focus,
  visible cursor, keyboard, explicit save, and Back/chevron cancellation. A
  custom title takes precedence over place metadata in Home; clearing it
  restores the ordinary fallback title.
- Present Home, including tour history, as a full-screen destination entering
  from the left,
  with a chevron header and Android Back support. Keep the single map instance
  mounted and visually still beneath that panel. Opening an archived tour must
  leave history beneath it in the back stack so Back returns to history first.
  Keep the history screen composed just outside the left edge while hidden.
  After the map has loaded, let that hidden screen preload its tour data and
  thumbnails so opening history only moves the prepared panel onscreen. Load
  the newest ten tours first, then hydrate older entries in the background. The
  hidden screen must not intercept map touches or Android Back.
- Lead Home with one open seven-calendar-day activity summary: an encouraging
  line, `N Touren in 7 Tagen`, combined duration and distance, and one distance
  bar per day. Keep all values on the same timeframe and avoid a grid of cards.
- Use the single neutral light-gray `NeutralSurface` token for subdued preview
  backgrounds, placeholders, and History section headers. Spur has no mint
  surface role; do not reintroduce a green-tinted near-neutral substitute.
- Render the at-home status as a square, borderless label with 25% transparency
  rather than a button; keep its background and text derived from the
  map-control color roles.
- Center the intended placement location when opening the moment composer. Put
  Photo/Video and Voice/Emoji in two paired rows of secondary buttons and make
  Cancel the full-width primary action; do not encode moment types with colors.
- Selecting a building only highlights it on the map. Never open a building
  details sheet or block interaction with the exposed map.
- Render game-road coverage as a broad translucent `GameRoadGreen` fill below
  and slightly wider than the tour trail, never as a hollow outline. Derive
  that green road history from the stored track points of every tour so it
  survives tour changes and app restarts; keep it independent of edge counts.
  Keep road coverage visible through MapLibre zoom 13. Render one global,
  deduplicated `MultiLineString` at every visible zoom; panning or crossing the
  detail-zoom boundary must never replace it with one cell or clear existing
  geometry. Show prepared global coverage immediately, then prepare missing
  cells only after camera movement ends and MapLibre reports idle. A prepared
  cell may append coverage once, never remove coverage already rendered. Prefer
  the lowest prepared zoom, collapse exact and directionally aligned
  near-duplicate paths within two meters into unique road arcs, and keep
  genuinely separate parallel streets. Use a narrower line at overview zooms
  to limit overdraw. Keep overlap indexing proportional to road length rather
  than the area of each segment's bounding box so diagonal roads cannot turn
  overview preparation into a multi-second task.
- Derive road-edge traversal counts from every completed tour and persist them
  in a separate per-map-cell cache. Exclude the active tour from that historical
  baseline, replay it only when a road cell is prepared, then match only newly
  appended waypoint segments against the prepared spatial index. Never rescan
  tour history or write the traversal cache for each live GPS update.
- Prepare a new detail cell from MapLibre's rendered road features inside the
  visible viewport; never scan every feature in the loaded vector source after
  camera movement. Query only after MapLibre reports the map idle with its
  requested tiles loaded; never persist an early non-empty partial render as a
  complete cell. React to later road-source updates, wait for the next idle,
  and merge that rendered viewport once per source revision so parent-tile
  fallbacks cannot permanently hide roads from subsequently loaded ideal tiles.
- Resolve exact-location map-moment clusters at a bounded street-level zoom
  where marker fan-out takes over; never zoom indefinitely toward coincident
  coordinates.
- In the tour-completion sheet, keep the header, statistics, and primary action
  stable while the route preview consumes the remaining height down to 120 dp.
  Render the preview at its actual container aspect ratio and fit the entire
  cleaned tour with padding; never crop toward the start, end, or center. Only
  fall back to scrolling when the fixed content plus that minimum cannot fit.
- In the waypoint rail, show the selected waypoint's clock time as the primary
  value and its compact one-based position as `current/total` directly below.
- Never rotate the main Spur/follow Asterisk while the current location is
  inside the Home Zone, regardless of noisy GPS speed readings.
- Confirm every successfully created tour with one short vibration and every
  successfully completed tour with a short double vibration. Apply the same
  haptics to manual and Home-Zone-driven lifecycle transitions; service restarts
  and failed or duplicate transitions must stay silent.
- Render traversal counts as a MapLibre symbol attached to the road midpoint so
  they move atomically with the map. The first traversal is communicated only
  by the green road coverage; show a textual `×2` badge starting with the second
  completed traversal at zoom 15 or closer. Never position road-count badges in
  a Compose overlay.
- Treat the trail as one semantic signal: its fill, the MapLibre location puck,
  and the selected follow button share the trail background token; its border
  and the location pulse share the complementary stroke token. Use the map
  control foreground as the selected follow icon's contrast color. Keep the
  Plus icon independent.
- Keep destructive actions on the established red treatment instead of
  overloading the primary-action colors.

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
