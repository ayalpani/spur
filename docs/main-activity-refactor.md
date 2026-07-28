# MainActivity refactor log

## Baseline

- Product baseline: TourEditor v2, merge commit `542c75c`
- Source file: `app/src/main/java/app/spur/MainActivity.kt`
- Baseline size: **9,403 lines**
- JVM test files: **15**
- Baseline checks: `testDebugUnitTest`, `assembleDebug`, and `lintDebug` passed
- Device: Galaxy A54 (`SM-A546B`)

The baseline device smoke test covered map startup, street/satellite styles,
follow and overview modes, controls, tour start/stop, background tracking,
process restart, history, existing local tours, editor selection, moment
composer, photo/audio/emoji entry points, home building selection, automatic
start settings, and menu color settings. No stored tour or moment was deleted.
External video capture was not launched to avoid exposing unrelated private
device content; the existing-media read path was verified.

Screenshots used for comparison are app-only temporary artifacts. They are
visually inspected and deleted after the checkpoint because the map and moment
thumbnails can contain private location data.

## Stable persistence and platform contracts

| Store | Stable names |
| --- | --- |
| SQLite | `spur.db`, version 4; tables `tours`, `track_points` |
| Home automation | file `home-auto-start`; keys `enabled`, `latitude`, `longitude`, `home-building`, `start-latitude`, `start-longitude` |
| Manual location | file `manual-location`; coordinate keys remain unchanged |
| Map settings | file `map-settings`; keys `default-zoom`, `default-rotation`, `map-control-color`, `map-control-foreground-color`, `trail-fill-color`, `trail-stroke-color` |
| Moments | file `map-moments`; encoded entry set remains unchanged |
| Photo places | file `photo-places`; photo ID remains the key |
| Emoji recents | file `emoji-picker`; key `recent-emojis`, newline separator |
| Tracking intent | extra `tour_id`; stop action `app.spur.STOP_TRACKING` |
| File sharing | authority `${applicationId}.fileprovider` |

## Extraction matrix

| Area | Target files | Verification |
| --- | --- | --- |
| Activity and app shell | `MainActivity.kt`, `SpurApp.kt`, `LocationOnboarding.kt` | JVM tests, build |
| Design system | `SpurDesign.kt`, `SpurSheets.kt`, `SpurIcons.kt` | Compose preview/device |
| Moments and media | `MomentComposer.kt`, `EmojiPicker.kt`, `VoiceMoments.kt`, `MediaMomentDetail.kt` | tests, device media entry |
| Map screen | `MapPage.kt`, `MapControls.kt`, `MapSurface.kt` | tests, device map controls |
| MapLibre support | style, camera/location, route, and moment-rendering files | build, lint, device map |
| Home automation | UI, building selector, and existing platform integration | tests, device sheet |
| Menus/settings | menu and color/rotation controls | tests, device sheets |
| History/player | `HistoryPage.kt`, `TourPlayer.kt` | tests, existing tour |
| Tour editor | coordinator, controls, and editor map | tests, existing tour |
| Pure helpers | formatting and decision helpers | JVM characterization tests |

## Commit protocol

Each extraction is mechanical and behavior-neutral. The original declaration
body is moved without changing its logic; only required top-level visibility
and imports may change. JVM tests and `assembleDebug` run after every extraction
commit. Lint and the Galaxy-A54 smoke test run at larger map/media/editor
checkpoints. Results and any deliberate file-size exception are recorded here.

## Results

| Checkpoint | Result |
| --- | --- |
| TourEditor-v2 baseline on `main` | JVM tests, build, lint, and Galaxy-A54 smoke test passed |
| Mechanical top-level extraction | `MainActivity.kt` reduced from 9,403 to 35 lines; JVM tests and build passed |
| Compose characterization | 2 onboarding instrumentation tests passed on Galaxy A54 |

Four cohesive declarations remain deliberately above the usual 500-line
guideline: `MapPage` (screen/state coordinator), `MapSurface` (single MapLibre
bridge), `PhotoDetailPage` (animated viewer), and the existing `TourStore`.
Splitting inside those functions would mix behavioral redesign into this
structural pass; they are recorded candidates for later focused refactors.
