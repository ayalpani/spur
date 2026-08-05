# Spur architecture

This document describes the TourEditor-v2 baseline merged to `main` in
`542c75c` and the component boundaries created by the MainActivity refactor.
It is intentionally descriptive: the refactor changes ownership and file
boundaries, not behavior or state architecture.

## Runtime components

| Component | Responsibility | State owner |
| --- | --- | --- |
| `MainActivity` | Android activity lifecycle, splash hand-off, Compose entry | Android |
| `SpurApp` | permission onboarding, navigation between map/history/editor/player | Compose |
| `MapPage` | map-screen coordination, active-tour and moment UI state | Compose + `TourStore` |
| `MapSurface` | MapLibre bridge, listeners, style installation, camera/follow updates | `MapView` + Compose bridge |
| `TrackingService` | foreground location updates for the active tour | Android service + `TourStore` |
| `TourStore` | tours and track-point persistence | SQLite |
| Moment/media components | composer, local files, playback, detail and share actions | Compose + app files |
| Home automation | home building/start point, sensor-first departure arming, buffered confirmation, geofence fallback | SharedPreferences + Android location APIs |
| History/editor/player | reading, displaying, trimming and playing stored tours | Compose + `TourStore` |

Map overlay controls share `MapIconButton`. Its secondary variant swaps the
user-selected map-control foreground and background colors and uses the soft
3 dp outline from `secondaryMapControlStyle`, derived from the secondary font
color at 25% opacity. Its round clickable `Surface` keeps pressed feedback
bounded to, and filling, the complete control. The map-style switcher and
inactive tour-start control use the same style. The location/follow control
remains primary; the blue secondary moment-add control sits immediately above
it, while manual-location reset sits above the map-style switcher.
During direct map gestures, the main control rails fade out and back in over
`MotionDurationDefaultMillis`; the map does not abruptly cover them via z-index.
The MapLibre accuracy circle is hidden. The location pulse is black while the
map is free and switches to the selected trail color only while location
following is active; interrupting follow with a map gesture restores black.
The entire active follow button fades between full and half GPU-layer opacity
on the same shared signal period as that map pulse, revealing the map beneath
instead of merely darkening its inner colors.
MapLibre keeps ownership of moment clustering and coordinates. Moment and
cluster layers are inserted below the MapLibre location stack, making
`MapPersonaLayer` the topmost map renderer. There is deliberately no custom
collision detection or visual marker displacement.

## Data flow

```mermaid
flowchart LR
    UI["Compose screens"] --> Store["TourStore"]
    Service["TrackingService"] --> Store
    Store --> DB[("spur.db")]
    UI --> Prefs[("SharedPreferences")]
    UI --> Files[("app media files")]
    Receiver["HomeExitReceiver"] --> Service
    Map["MapLibre bridge"] <--> UI
    Store --> UI
```

Tour recording starts by creating a row in `tours` and passing its ID to
`TrackingService` under the stable `tour_id` extra. The main-thread location
callback copies each active-tour fix onto one serial `HandlerThread`; that
worker filters and appends it to `track_points`. A session token prevents late
worker results from updating a replaced or stopped tracking session. Departure
confirmation and Android lifecycle ownership remain on the main thread.

Compose polls a compact `TourRevision` made from tour metadata, point count,
highest point ID, and that point's timestamp. It reloads the complete point
list only for the first display or when this token changes, and polling pauses
while the activity is below `RESUMED`. Road fingerprints are refreshed after a
recognized tour change instead of on every timer tick. This is deliberately a
revision-plus-reload boundary, not a point-delta protocol or repository/state
architecture migration. Stopping finishes the row before the foreground
service is stopped.

Before an automatic departure is confirmed, the runtime preferences own the
candidate timestamp and measured pre-roll. While the app is resumed, `SpurApp`
reads that same pending representation for the map: the location label says
`Tourstart wird geprüft` and MapLibre renders the route that will be persisted
if confirmation succeeds. No provisional database tour is created; cancellation
removes the status and route, while confirmation transfers the existing points
into the ordinary active-tour flow.

Moments are encoded into the `map-moments` preference file. One background
`TourPresentation` resolves moment-to-point links, waypoint-rail entries, and
the point-ID selection index from the same point ordering. Marker bitmap
preparation is keyed only by marker media identity, so coordinate-only movement
rebuilds GeoJSON without decoding photo or video media again. Photo place names
are cached separately. Camera JPEG confirmation previews are decoded on
`Dispatchers.IO`; camera, audio, and video files remain local unless the user
explicitly invokes an Android share or export intent.

## Lifecycle contracts

The main map, building selector, and editor map each own a `MapView`. Their
Compose effect creates the view, mirrors the current lifecycle state, forwards
`ON_START`, `ON_RESUME`, `ON_PAUSE`, and `ON_STOP`, removes registered map
listeners, and finally calls `onDestroy`. Extraction must not reorder these
operations.

`TrackingService` is independent of the activity lifecycle. It uses a location
foreground service, returns `START_STICKY`, resumes the active tour after a
process restart, and removes location updates both when stopping and when the
service is destroyed. Destruction also invalidates pending tracking-session
results and shuts down the serial persistence thread with `quitSafely`.

Audio recording and playback are Compose-owned resources. Disposal stops and
releases the current `MediaRecorder`/`MediaPlayer`. CameraX remains isolated in
`CameraScreen` and `VideoCameraScreen`; both bind to the current lifecycle
owner. `VideoConfirmationScreen` owns preview playback. Video recordings are
finalized before preview, deleted when discarded, and retained only after the
user confirms them. Photo preview and capture belong to the same CameraX
`UseCaseGroup` and share the `PreviewView` viewport, so the saved photo contains
exactly the framing shown in the camera. Photo and video confirmation share
`AnimatedMediaConfirmationPanel`: the complete medium is fitted into the
remaining top-centered preview area, with both dimensions constrained by its
actual aspect ratio, while the fixed, non-draggable action panel occupies its
own space below instead of covering the medium. On entry the panel expands and
slides upward while the preview area shrinks in the same 420 ms transition. The
panel stops above the Android navigation area and therefore has rounded corners
on all four sides.

## Intended file boundaries

- app shell and onboarding
- design tokens, feedback, sheets, and icons
- moment composer, emoji picker, media recording/playback, and detail screens
- map screen coordinator and map controls
- MapLibre lifecycle/style/camera integration
- route, endpoint, selected-point, and moment rendering
- home automation UI and building selection
- settings and menus
- history, editor, and player
- pure formatting and decision helpers

Moving to ViewModel/UDF, dependency injection, a navigation rewrite, or a new
database layer is explicitly outside this refactor.
