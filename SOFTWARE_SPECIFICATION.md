# ARAHA — Minimal Android AR Indoor Navigation Prototype

Version 1.4 · 1 October 2026 · Implementation specification

**Current prototype scope:** one route from the toilet to Grand Ballroom's southern entrance on the supplied Jisik second-floor plan. A fixed iPhone 13 displays the sign; navigation runs on Android only. The runtime graph and `FLOORPLAN_CALIBRATION.md` supersede `floorplan-map-draft.json`. Geometry remains provisional until verified on site. First use requires the measured displayed-image width, and marker centre height is editable in setup.

## October 2026 field-test revision (overrides earlier strict tracking rules)

The user reported seeing arrows followed by an uncertain/off-track warning. Version 0.2.0 keeps the session, alignment and route alive during temporary tracking loss. Arrows wait for a valid camera pose and resume automatically without rescanning. Off-corridor estimates no longer hide arrows or reject route selection: an uncertain start uses the nearest mapped segment without adding a straight connection through unmapped space. Turn progression uses a 1 m waypoint tolerance; arrival still requires the final segment and a fresh tracked position held within its arrival tolerance. Explicit End, backgrounding and fatal camera errors still terminate the session.

The marker is now at the user's red dot: source-plan pixel [686,606], on the wall directly opposite the toilet's lower doorway, facing east toward the doorway. Centre height remains editable, default 1 m. The five-node route starts at [693,606] in the corridor and is approximately 19.57 m. Existing south-wall placement instructions and terminal-on-uncertainty requirements below are superseded by this revision.

## 1. Goal and scope

Build an Android app that guides a person to selected rooms on one hotel floor. Select a destination and scan a fixed QR/image sign once to initialize the session, then follow AR arrows. Subsequent destination selection uses the current tracked position without scanning again.

The prototype demonstrates the QR-started navigation experience associated with ARway. It uses its own implementation and assets; it does not require ARway's SDK or reproduce its entire platform. ARway documents QR access points followed by in-app localization. This prototype implements that core interaction with the app already installed. [ARway QR workflow](https://docs.arway.ai/maps-studio/web-studio/qr-codes)

**Minimum deliverable:** one installable Android app, one starting sign on a fixed iPhone 13, one floor-plan corridor network, and navigation from the toilet to Grand Ballroom. Additional rooms can use the same data format later.

**Included**

- Android only, portrait orientation, English interface.
- One hotel floor and one fixed starting sign.
- In-app QR scanning and automatic initial spatial alignment.
- A short list of room numbers.
- On-device routing over a manually authored corridor network, AR arrows, and an arrival message.
- Repeated destination selection after arrival within the same active AR session.
- Bundled configuration and assets; navigation works without internet.

**Excluded**

- Tracking recovery, relocalization, additional QR checkpoints, and drift correction.
- Automatic rerouting after deviation. Explicit destination selection can cancel the current route while preserving tracking.
- Initializing a new session away from the starting sign. Routing from the current position after initialization is included.
- iOS/iPhone, multiple floors, lifts or stairs as route segments.
- Backend, database server, accounts, analytics, and admin dashboard.
- Automatic floor-plan generation, LiDAR scanning, or a 3D building model.
- Opening the app from an external camera/QR app, instant-app delivery, and public Google Play launch.
- Voice instructions, indoor GPS, obstacle detection, and hotel-system integration.

The supported demo is a sequence of short walks over the configured corridor network in one uninterrupted AR session. An interrupted or unusable AR session ends; the user begins a new session at the starting sign. There is no recovery workflow.

## 2. Technical approach

Use **Kotlin, Jetpack Compose, Google ARCore, OpenGL ES, and ML Kit Barcode Scanning**, with a local JSON configuration. Use Android Studio and Gradle on Windows, macOS, or Linux to build an APK and install it on a physical Android phone. Use Google's Android SDKs and the ARCore sample rendering approach; no Unity or commercial navigation SDK is required.

- **Jetpack Compose:** home, room selection, overlays, and arrival screens.
- **OpenGL ES rendering surface:** AR camera background and simple arrow geometry, hosted beneath Compose overlays using Android view interoperability.
- **ARCore Session:** camera motion and the reference sign's initial spatial pose using Augmented Images.
- **ML Kit Barcode Scanning:** validates the QR payload using CPU camera images acquired from the same ARCore session. Use the bundled model to support offline scanning.
- **Bundled JSON:** room names, marker transform, corridor nodes, and connecting edges.

ARCore owns the camera. Acquire CPU images through `Frame.acquireCameraImage()` and pass them to ML Kit with the appropriate image rotation; do not open a competing CameraX or Camera2 capture session. Throttle scanning, allow at most one request in flight, and close each acquired image after processing completes, including failure paths. A temporarily unavailable CPU image skips that scan request. [ARCore Frame API](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame), [ML Kit Android barcode scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android)

**Device baseline:** a physical ARCore-supported Android phone running Android 8.0 (API 26) or later as a project choice. Declare the app AR Required, check ARCore availability and camera permission, and use `ArCoreApk.requestInstall()` to ensure Google Play Services for AR is ready before creating the session. Install/update these services and initialize the demo device before offline testing. Depth hardware is not required. The emulator may test interface and route logic, but cannot establish that indoor navigation works. [ARCore setup](https://developers.google.com/ar/develop/java/enable-arcore), [Supported devices](https://developers.google.com/ar/devices)

### 2.1 What the QR scan must do

Reading a QR payload identifies the starting sign. It does not by itself establish a correctly scaled, oriented AR route. The app also needs the physical sign's position and orientation relative to the phone.

Use one combined sign containing the QR code and a distinctive, asymmetric textured graphic. For this prototype, show the sign full-screen on a fixed iPhone 13 mounted at the toilet start location. Add the complete rendered sign image to a bundled ARCore `AugmentedImageDatabase`, with its measured on-screen width. ML Kit reads the QR; ARCore recognizes the same sign as an image target. The Android user performs one scan action.

Set the image database on the ARCore session configuration. Accept an initial image pose only when its `TrackingState` is `TRACKING` and its `TrackingMethod` is `FULL_TRACKING`; `LAST_KNOWN_POSE` alone is insufficient for initial alignment. Correct physical dimensions are essential to scale. Google advises against plain QR codes as Augmented Images targets, which is why the sign includes distinctive imagery. [Augmented Images guide](https://developers.google.com/ar/develop/java/augmented-images/guide), [Reference-image requirements](https://developers.google.com/ar/develop/augmented-images)

The full sign must pass an on-device recognition test before mapping the floor. Reliable tracking of an arbitrary plain QR graphic is not an assumed capability. The graphic is a practical part of the prototype sign, not another checkpoint.

### 2.2 After initial alignment

Capture the floor-to-AR transform once. Keep the same ARCore session alive during room selection, navigation, arrival, and selection of subsequent destinations. ARCore tracks movement after the sign leaves the camera view; the app does not need to keep seeing the QR code.

World tracking combines camera imagery and motion sensing. Its quality depends on the environment; a camera `TrackingState.TRACKING` value is not proof of absolute map accuracy. Short routes must therefore be physically validated on the hotel floor. [ARCore fundamental concepts](https://developers.google.com/ar/develop/fundamentals)

## 3. User flow and screens

### 3.1 Figma design reference and prototype mapping

The following supplied Figma frames are the visual reference for the Android prototype. Their airport copy, imagery, and multi-floor examples must be replaced with the selected hotel's name, floor, rooms, and measured route data.

- [Homepage — node 43:1688](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=43-1688)
- [Choose destination — node 59:1924](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=59-1924)
- [Scan surroundings / QR — node 66:3308](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=66-3308)
- [Location found — node 59:2184](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=59-2184)
- [Route recommendation — node 66:4064](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=66-4064)
- [AR navigation — node 66:3456](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=66-3456)
- [Arrival — node 66:3833](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=66-3833)

The visual language for included screens is: pale-grey background, dark blue-grey text, white rounded cards and sheets, orange as the primary action color, and Poppins typography where it is licensed and bundled. Adapt the supplied phone-frame layouts to Android screen sizes, system insets, and gesture/button navigation. Android status and navigation bars are supplied by the system; do not render the Figma status bar, home indicator, device outline, or device shadow as app UI. Use Android Back to return from selection or preview while preserving an active session; during scanning it cancels the attempt, and during navigation it ends the session. Backgrounding still ends the session.

The Figma home screen includes search, building change, microphone, saved, history, profile, and example popular/recent locations. They are not functional requirements for this prototype. The implemented home screen may retain the layout and bottom navigation styling, but it only needs to present configured hotel destinations and a clear way to begin navigation.

The Figma route-recommendation screen is an accessibility path-choice concept. It is intentionally not implemented: the app calculates and uses one shortest valid route. It may be omitted entirely, or shown only as a brief, non-interactive route preview before navigation. It must not offer multiple routes, accessibility filters, stairs/lift choices, or a path preference that the prototype cannot honor.

### 3.2 Flow

For a new session: `Home → Choose destination → Scan surroundings → Location found / route preview → AR navigation → Arrival`.

For a further destination while the AR session remains valid: `Arrival → Choose destination → Location found / route preview → AR navigation → Arrival`. The second flow does not show the QR scan screen because the first scan already aligned the floor map.

### Screen 1 — Home

Use the [Homepage frame](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=43-1688) as the visual reference. Display the hotel and floor context, configured destination cards, and **Choose destination** or a destination-card action.

Opening navigation proceeds to destination selection. Request camera permission when scanning becomes necessary. If permission is denied, show a settings instruction and do not open the scan view. If world tracking is unsupported, show an unsupported-device message.

### Screen 2 — Scan

Use the [Scan surroundings frame](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=66-3308) as the visual reference. Show the camera with “Scan the hotel's start sign” and “Fit the whole sign in view.” Begin AR world tracking and inspect its camera frames for the configured QR payload. The selected destination remains visible in the bottom sheet.

Move forward only after all three conditions hold: the expected payload has been read, the expected reference image has been detected with a stable full-tracking pose, and camera tracking is `TRACKING`. A valid QR alone must not advance the screen.

Use the same uninterrupted session for these observations. After successful alignment, show “Starting point found” and proceed to Location found / route preview for the already selected destination. An unrelated QR shows “Use the hotel's start sign” and remains on this screen. After 15 seconds without alignment, keep scanning and show a brief distance/lighting tip plus **Cancel**.

### Screen 3 — Choose room

Use the [Choose destination frame](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=59-1924) as the visual reference. Present a short list, such as “Room 201”, “Room 202”, and “Room 203”, with a destination action. These numbers are illustrative and must be replaced with real room labels.

When no current alignment exists, selecting a destination enters Scan. When an aligned AR session already exists, selecting a destination proceeds directly to Location found / route preview. In that case, use the latest tracked position to calculate a route; do not reuse the QR position or the previous destination coordinate. If the position cannot be connected unambiguously to the mapped walkable network, show “Cannot start a route from this position” and keep selection available without drawing arrows. No new scan is required while the session remains valid.

### Screen 3A — Location found / route preview

Use the [Location found frame](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=59-2184) as the visual reference. Show a confirmation such as “Location found,” the selected room, and a lightweight floor-plan route preview where the supplied floor-plan image is available. This screen is the visual confirmation that the app has a valid location and one calculated route; it is not a second localization process.

The [Route recommendation frame](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=66-4064) is not a separate functional screen in this version. If its bottom sheet is used, it must show exactly one calculated shortest route and one **Start navigation** action.

### Screen 4 — Navigate

Use the [AR navigation frame](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=66-3456) as the visual reference. Show the live camera, destination room number, the next direction and distance, directional arrows on the corridor floor, and **End session**. Do not implement the frame's voice-guidance or other utility controls in this prototype. Keep the interface otherwise empty.

Only show the current route section and a short look-ahead. Do not draw the entire route through walls. Arrows advance as the user follows the route. A final marker identifies the corridor-side stopping point for the room.

### Screen 5 — Arrival

Use the [Arrival frame](https://www.figma.com/design/y7iBMI8oPtLAzyaZZI6u0D/Team-7--AR-Indoor-Navigation-App?node-id=66-3833) as the visual reference. When the arrival conditions are met, remove route arrows and display “You have reached Room [number]” with **Choose another destination** and **End session**. Keep the camera and tracking active beneath the overlay. Choosing another destination returns to room selection without resetting the AR session, floor alignment, or current pose. End session clears the session and returns home.

### Session-ended message

If the app is backgrounded, the AR session is interrupted, or tracking becomes unusable, hide arrows and show: “Navigation ended. Begin again at the start sign.” Offer **Done** only; it returns home because the session has already ended. Do not attempt to restore the route or position.

This is terminal error handling, not recovery. The app observes ARCore camera tracking state and Android lifecycle events; it ends the attempt rather than implementing relocalization. [ARCore Camera API](https://developers.google.com/ar/reference/java/com/google/ar/core/Camera)

## 4. Functional requirements

Each requirement below is mandatory for this prototype.

- **FR-01 — Capability and permission:** check world-tracking support and camera permission before scanning. Explain failure without crashing.
- **FR-02 — QR validation:** accept exactly the bundled start-marker identifier. QR text is treated as data and never opened as a URL or executed.
- **FR-03 — Spatial initialization:** validate the reference sign and establish metric position and orientation before enabling navigation.
- **FR-04 — Room list:** display configured rooms linked to valid destination nodes; no login or search is required.
- **FR-05 — Route calculation:** calculate a shortest-distance route locally from the latest tracked position to the selected room using the corridor graph. No remote service is required.
- **FR-06 — AR guidance:** place arrows along the selected corridor route and orient each arrow toward the next segment.
- **FR-07 — Progress:** advance through route segments in order using the tracked camera position projected onto the floor.
- **FR-08 — Arrival:** declare arrival only near the selected endpoint after ordered route progress reaches the final segment.
- **FR-09 — End session:** End, End session, interruption, and unusable tracking stop guidance and clear the route and session. Arrival alone must not end tracking.
- **FR-10 — Offline operation:** all required room data, marker reference imagery, and arrow assets are installed with the app.
- **FR-11 — Local configuration:** a developer can add or adjust rooms and corridor connections by editing the bundled data and rebuilding, without changing navigation logic.
- **FR-12 — Continue after arrival:** allow another destination from the current tracked position, retaining the same session and floor alignment without another QR scan.

## 5. Floor setup and route data

### 5.1 Required physical preparation

1. Select one permitted starting location, preferably a lobby or lift landing on the chosen floor.
2. Rigidly mount the iPhone 13 that displays the sign. Record the rendered full-image width, screen centre height, and orientation. Keep the same full-screen image, maximum brightness, and physical position after calibration; do not resize or move it.
3. Establish a local metric coordinate frame: X and Z lie along the floor, Y points upward, and floor height is Y = 0. Mark its origin and axis directions on a sketch.
4. Use the existing floor plan as the authoring base. Establish metric scale from a printed dimension or a measured distance between identifiable points. Record the plan origin, rotation, and image-axis conversion to X/Z floor coordinates; verify another independent distance to catch distortion.
5. Mark the sign location and facing direction, corridor centerlines, turns, junctions, and stopping points outside room doors. Verify the sign pose and critical distances on site. Create graph nodes at these points and bidirectional edges along walkable corridors. Represent bends with extra nodes; never connect points through walls or private rooms. Record a usable corridor half-width for each edge.
6. Export the measured nodes and connections into the bundled configuration. No in-app floor-plan editor or floor-plan display is required. Walk routes from the sign and between rooms, correcting plan measurements or sign alignment before the demonstration.

The current test route is toilet to Grand Ballroom, including the west doorway and lower-corridor turn. Its provisional length is approximately 21.86 m. This is not a guaranteed tracking range. Test repeated destination selection after moving within the mapped corridor; drift accumulates across the whole session.

### 5.2 Data contract

Bundle `hotel-floor.json` and the reference sign image. No server API is required.

Required configuration fields:

- `schemaVersion`: integer; 2 for this graph-based format.
- `floorId`: stable string identifying the single floor.
- `floorName`: display label.
- `marker.id`: exact expected QR payload, for example `ARAHA:HOTEL-DEMO:F1:START:V1`.
- `marker.referenceImageName`: image asset identifier.
- `marker.physicalWidthMeters`: measured width of the complete reference image, excluding paper outside it.
- `marker.floorFromImage`: rigid 4×4 transform mapping ARCore Augmented Image center-local coordinates into the surveyed floor frame, stored as 16 column-major numbers. Recalibrate any transform authored for a different image-axis convention.
- `startNodeId`: node at the initial standing area; a setup reference, not a mandatory source for every route.
- `nodes`: array of unique `id` and `position: [x, 0, z]` values at junctions, bends, and destination stopping points.
- `edges`: array of unique `id`, `fromNodeId`, `toNodeId`, and `halfWidthMeters` values. All edges are bidirectional; weights are computed from metric endpoint distances. Edge widths describe surveyed walkable space, not permission to cross walls.
- `rooms`: array of objects with unique `id`, display `label`, and `destinationNodeId`.
- `positionSnapMaxMeters`: maximum distance from a tracked position to an eligible edge, initially 1.0; the smaller of this value and the edge half-width applies.
- `arrivalRadiusMeters`: 0.65 for this prototype, with an endpoint chosen to avoid adjacent rooms.
- `mapImage`: asset, pixel dimensions, origin pixel, and metres per pixel for the preview.
- `calibrationStatus`: human-readable survey status. Rooms also include a short `description`.

At load time reject unsupported schema versions, duplicate identifiers, missing assets, invalid transforms, nonfinite numbers, nonexistent node references, zero-length edges, nonpositive widths/tolerances, and room nodes disconnected from the start node. Check all configured room destinations are reachable. Verify walkable edge placement against the floor plan and on-site measurements. If configuration is invalid, show “Demo configuration unavailable” and disable navigation.

Use a small sample configuration only for development. Real hotel coordinates and room labels must be measured; they cannot be inferred from this specification.

## 6. Positioning, rendering, and navigation logic

### 6.1 Coordinate alignment

Use metres and ARCore's right-handed coordinate convention throughout. Define `T_A_B` as a transform taking coordinates in frame B into frame A.

- `T_floor_image`: surveyed transform from image-local coordinates to floor coordinates.
- `T_world_image`: ARCore's detected Augmented Image center pose, obtained from `getCenterPose()`.
- Compute `T_world_floor = T_world_image × inverse(T_floor_image)`.
- Render each floor waypoint as `p_world = T_world_floor × p_floor`.
- Compute the camera position in floor coordinates using `inverse(T_world_floor) × p_world_camera`.

Implement and test the reference-image axis convention explicitly; a sign mounted vertically is not a horizontal floor plane. Verify alignment using measured forward and sideways test points. Do not assume the phone faces along the corridor when scanning, or use magnetic heading to establish route orientation.

Accept initial alignment after the correct image is in `FULL_TRACKING` and its pose is stable over a short observation window. Initial tuning values: 0.5 seconds, less than 5 cm translation variation, and less than 3 degrees rotation variation while camera tracking is `TRACKING`. These are engineering starting values to validate on the demo device, not accuracy guarantees.

Freeze the accepted image-to-floor relationship using a session-local world anchor at the accepted image pose. Follow ARCore's anchor pose as its world estimate changes, but ignore later image detections and marker movement. Keep the session alive between screens. No persistent/Cloud Anchors, ongoing marker correction, or plane detection is required. Floor height comes from the surveyed transform.

### 6.2 Route calculation from the current position

On every **Start navigation** action:

1. Read the latest camera pose from the active session and convert it into floor coordinates. Require camera `TrackingState.TRACKING`.
2. Project its X/Z position onto eligible graph edges within the configured distance and surveyed walkable width. Use the nearest unambiguous projection. Incident edges meeting at the same junction may use that shared node; competing projections in separate corridors must be rejected rather than guessed.
3. Insert a temporary start node at the projection, splitting that edge into two connections weighted by their distances. Do not snap to a remote endpoint or force the route to begin at the QR sign.
4. Run Dijkstra's algorithm to the selected destination node. Return an ordered polyline, including the short connection from the actual floor position to its projection only where that connection lies within surveyed walkable space.
5. Clear previous route entities and progression, then render the new route. Preserve the AR session, world origin, and floor transform.

The graph is small and local; no navigation SDK is needed. If no valid source or path exists, show an explanation in room selection and draw no route. If already within the selected destination's arrival radius on the same mapped corridor, apply the arrival dwell rule without creating a zero-length arrow segment.

This projection attaches the route source to the graph; it must not change the underlying tracked pose or be treated as drift correction. Route calculation happens on explicit selection only; automatic off-route recalculation is out of scope.

### 6.3 Route rendering

Generate simple high-contrast arrow geometry programmatically. Place arrows approximately every 1 metre, 2–3 cm above the configured floor to avoid visual overlap with the floor surface. Size and spacing are tunable constants.

Render only the current segment and a small preview of the next segment where the physical layout permits. Use a maximum look-ahead of approximately 5 metres. At a corner, display a turn arrow before the turn; reveal the following corridor after segment progression. Do not depend on mesh-based occlusion.

### 6.4 Progress and arrival

Project the camera's floor position onto the active segment. Use horizontal X/Z distances, ignoring camera height. Advance when the user reaches within approximately 1 metre of the next waypoint, or passes its endpoint while remaining within the configured corridor tolerance. Process segments sequentially; never jump to an arbitrary nearby later segment.

Stop rendering arrows behind completed segments. When the final segment is active and horizontal distance to the final endpoint remains within `arrivalRadiusMeters` for 1 second, show arrival. Do not claim door recognition: this is arrival at a surveyed coordinate. If neighboring doors make the tolerance ambiguous, move the stopping point or tighten the tested threshold.

The user must follow the displayed route. Route deviation does not trigger rerouting. An **End** button remains available throughout.

### 6.5 Loss of tracking

During initialization, wait for camera `TrackingState.TRACKING`. After initial alignment, including room selection and arrival, hide any arrows immediately if the camera becomes `PAUSED` or `STOPPED` and terminate the session. Backgrounding, loss of foreground camera access, and unrecoverable session errors also terminate it. Do not resume this attempt if ARCore later reports `TRACKING`. The sign leaving view alone does not end navigation while camera tracking remains valid.

Ordinary drift can occur even when camera tracking is `TRACKING`; this prototype does not detect or correct it. Field testing, short routes, consistent lighting, and a fixed sign define the working demo conditions.

## 7. Small implementation structure

Use one Android Studio application module with these logical components; separate services or modules are unnecessary:

- `AppFlow`: states and screen transitions.
- `FloorConfiguration`: decodes and validates bundled data.
- `ARSessionController`: owns one session, camera frames, marker detection, initial alignment, and terminal tracking events.
- `QRValidator`: runs bundled ML Kit QR detection on a throttled background task, with at most one request in flight; releases acquired camera images and stops after initialization.
- `RouteController`: source projection, local graph routing, selected route, segment progress, and arrival calculation.
- `RouteRenderer`: OpenGL ES camera background, arrow geometry, and endpoint marker using ARCore view/projection matrices.

State progression for a new session: `home → roomSelection → scanning → locationFound → navigating → arrived`. With an active alignment, destination selection follows `arrived → roomSelection → locationFound → navigating`; it does not scan again. **Choose another destination** begins this second flow. End or End session returns home and stops tracking; scan cancellation also returns home. A route-calculation failure stays in room selection. Session failure from any aligned state transitions to `ended`, then home. A new scan creates a fresh session and clears previous transforms and route state. There is no recovery state.

Keep the rendering surface and ARCore session alive across aligned screens; Compose recomposition and navigation overlays must not recreate either. Publish UI state on the main thread, update rendering resources on the GL thread, and keep QR processing off the rendering path. Android lifecycle shutdown must stop frame processing, finish or cancel QR work safely, pause/close the session, and release camera/GL resources. A foreground return requires a new attempt at the start sign.

## 8. Nonfunctional requirements

- **Performance:** sustain approximately 30 FPS or better on the selected demo Android phone during a complete route; avoid repeated asset creation each frame.
- **Startup:** reach the scan camera within 3 seconds after permission is already granted on the demo device.
- **Scan target:** initialize within 5 seconds in at least 9 of 10 controlled scans with the complete sign visible, adequate lighting, and the validated scanning distance.
- **Stability:** complete the acceptance walks without a crash or session reset.
- **Privacy:** process camera frames on-device; do not record or upload imagery, guest identities, or location history. Request camera access only; no account, location, microphone, or photo-library access is needed.
- **Usability:** show room labels clearly, use large tappable controls, and leave most of the camera view unobstructed.
- **Configuration:** use one versioned, bundled data source so marker dimensions and route data ship together.

All timings and tolerances above are prototype acceptance targets, not claims that have already been measured.

## 9. Acceptance tests and definition of done

Perform field acceptance on the actual floor with the actual mounted sign and the selected Android phone. Record the phone model, Android version, Google Play Services for AR version, app build, sign dimensions, lighting conditions, and route lengths.

1. **Launch and permission — FR-01:** allow and deny camera permission in separate tests. Valid permission opens scanning; denial and unsupported capability show useful messages.
2. **Marker validation — FR-02/03:** scan an unrelated QR, then the correct sign. Only the configured sign initializes navigation. Repeat from left, center, and right viewing positions; route orientation must remain correct.
3. **Initialization reliability — FR-03:** perform 10 fresh scans under demo conditions; at least 9 initialize within 5 seconds. Inspect measured test points near the sign; displayed positions should be within 0.5 metres horizontally of their intended locations.
4. **Room selection — FR-04/05:** each room produces a valid corridor route from the current tracked position, including a position partway along an edge. Validate both travel directions. Ambiguous or off-network sources show an explanation with no arrows.
5. **Real navigation — FR-06/07/08:** walk from the toilet through the mapped corridor to Grand Ballroom. The route must finish at the intended entrance without a re-scan, manual alignment, or wrong-room arrival.
6. **Route alignment — FR-06:** at measured beginning, turn, and endpoint checkpoints, arrows must remain in the intended walkable corridor, with target horizontal error no greater than 1 metre. If corridor width requires a tighter tolerance, use that tighter tolerance. A route that points through a wall fails regardless of average error.
7. **Arrival — FR-08:** no arrival message at an earlier room or an earlier route segment. At the intended endpoint, arrival appears after the specified dwell time.
8. **Ending — FR-09:** End session, backgrounding, and a tracking interruption remove arrows and terminate the session. Returning to the foreground does not resume navigation.
9. **Offline — FR-10:** after installing the APK and completing Google Play Services for AR setup, enable airplane mode and complete one route from a fresh launch. QR scanning must work using the bundled ML Kit model without a model download.
10. **Data handling — FR-11:** test malformed configuration, a missing marker asset, and a disconnected destination. Navigation is disabled with an explanation rather than crashing. Edit a graph edge, rebuild, and verify the new data is used.
11. **Consecutive selections — FR-12:** scan once and navigate to Grand Ballroom. Move back along the mapped corridor, select Grand Ballroom again, and start. Repeat three times; each route must start at the latest tracked position without a scan or reset. Distinct-destination walking tests apply when additional surveyed rooms are configured.
12. **Same destination and lifecycle — FR-08/09/12:** select the room already reached and verify arrival without invalid arrows. Background the app separately during room selection and arrival; both terminate the session. Verify End session does the same.

Unit tests need only cover transform composition using known fixtures, graph validation, shortest paths including reverse travel and temporary edge sources, ambiguous/unreachable sources, ordered segment progression, and arrival gating. Real-device walking tests are mandatory; mocked AR tests alone do not establish completion.

**Field-accepted means:** the installed app passes these tests, the floor data and iPhone sign are delivered with the source, and another person can repeat the demonstration using the written setup instructions. A compiled APK with passing unit tests is a software handoff, not proof of field acceptance.

## 10. Implementation sequence and handoff

1. **Prove alignment first:** install a camera/AR shell, recognize the combined QR sign, and place test objects at measured floor positions. Do not build the rest around unverified marker alignment.
2. **Prove one real route:** survey a short route, render arrows, and walk it on the demonstration phone.
3. **Complete the minimal flow:** add the toilet-to-ballroom graph, current-position routing, segment progression, arrival, repeated destination selection, and session termination.
4. **Validate and hand over:** configure the remaining routes and run the acceptance tests.

Deliver the Android Studio/Gradle project, installable demo APK, bundled floor configuration, Augmented Image database and source reference image, the full-screen iPhone 13 sign image, iPhone mounting/display instructions, Android device installation instructions, floor setup sketch, and acceptance results. No cloud deployment is part of this delivery.

The user already has the floor plan. Before physical implementation, obtain that file and its scale or a known measured distance, verify on-site geometry, and arrange permission to mount the sign and test the corridors, an ARCore-supported Android phone, and a computer with Android Studio and USB debugging enabled on the demo phone. Use local APK installation for the prototype. These are implementation inputs; they do not add app features.

If marker alignment or a route fails the field tests, correct the sign or survey, or shorten the agreed demonstration route. Do not quietly add recovery, cloud positioning, or other features to this prototype scope.
