# Build verification — 1 October 2026

- APK assembly: **passed**, `:app:assembleDebug`.
- Unit tests: **10 passed, 0 failed**, `:app:testDebugUnitTest`.
- Android lint: **passed with warnings**, `:app:lintDebug`. No lint errors remain. Warnings concern newer available dependency versions, intentionally portrait-only UI, platform backup metadata, a programmatically constructed AR view, and optional Kotlin syntax conveniences.
- Final build: Gradle 8.11.1, JDK 21.0.10, Android SDK 35, AGP 8.9.1, Kotlin 2.1.20.
- Build emitted a non-fatal SDK metadata-version warning because the installed command-line SDK tools are newer than AGP's SDK parser.
- APK: `ARAHA-debug.apk`, package `app.araha.navigation`, version `0.2.0` (version code 2), minimum Android API 26, target API 35, debug-signed for sideloading.
- ADB device check: **no connected devices**. Installation, rendered-screen inspection, camera/marker recognition, frame rate, offline operation, and physical route alignment were **not** tested on a phone.

The software uses the provisional metric graph from the supplied plan. This is a compiled prototype handoff; field acceptance remains pending. See `../README.md` for installation and device test steps, and `../FLOORPLAN_CALIBRATION.md` for geometry assumptions and editing.

Local reports from this build:

- `../app/build/reports/tests/testDebugUnitTest/index.html`
- `../app/build/reports/lint-results-debug.html`

Test coverage includes shortest-path selection, route creation from a live position, off-network route creation without an invented connector, off-corridor turn progression, disconnected-map rejection, sequential progress and arrival dwell, dwell interruption, east-facing marker axes, and transform composition/inversion. Passing these tests does not prove AR accuracy on the hotel floor.

Version 0.2.0 moves the marker to source pixel [686,606], the red dot opposite the lower toilet doorway, facing east. Temporary tracking uncertainty keeps the active route and session; arrows resume on valid camera tracking. Off-corridor estimates are advisory. The user reported initial arrows in version 0.1.0; this revised build still needs a device walk-through. The reference sign image and QR payload are unchanged.
