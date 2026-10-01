# Floor-plan calibration for the Android build

The authoritative runtime map is `app/src/main/assets/hotel-floor.json`. `floorplan-map-draft.json` is an **obsolete tracing draft**, retained for reference only. Its inferred connections and marker dimensions must not be used for navigation.

## Source and scale

`jisik-2f-clean.jpg` is 1024 × 1024. `Section 1.png` adds 100 pixels of padding on each side; subtract 100 from annotated-image coordinates to obtain source coordinates.

The supplied ballroom short width is **16 m**. The assumed endpoints `[159,248]` and `[460,320]` are 309.49 pixels apart, giving the provisional uniform scale **0.05170 m/pixel**. Those image endpoints were inferred, not confirmed on site. If 16 m describes an interior width instead of this exterior line, replace the endpoints and regenerate.

The measured **36.61 m** follows the lower corridor from next to the southern ballroom door to its blocked eastern end. Its precise plan endpoints remain unconfirmed, so it is an independent check and is not currently applied. Changing only route edge weights would break metric AR geometry; correct the scale/geometry instead.

## Proposed route

See `delivery/route-overlay.svg`. `tools/generate-assets.py` converts these original-image pixels into metric nodes:

| Node | X | Y | Interpretation |
|---|---:|---:|---|
| toilet-start | 693 | 606 | Standing in corridor between marker and toilet doorway |
| corridor-turn | 693 | 650 | Lower horizontal corridor |
| corridor-west | 430 | 650 | Continue west |
| ballroom-approach | 392 | 650 | Approach southern entrance |
| ballroom-door | 377 | 680 | Stop outside entrance |

This route is approximately **19.57 m**, not the full hallway measurement. Confirm every edge, especially the toilet doorway and narrow western passage. Half-widths of 0.32–0.55 m are provisional tolerances, not surveyed dimensions.

## Fixed iPhone marker

- Centre at original-image pixel `[686,606]`, corresponding to the red dot at `[786,706]` in the user's updated annotated image. This is the opposite wall across the corridor from the lower toilet doorway.
- Image centre height defaults to 1 m and is editable in Android setup.
- Upright on the opposite wall, screen facing east toward the toilet doorway.
- Display the whole `delivery/start-marker.png` (720 × 1080) without cropping. Measure the visible image width. Android requires this before starting. The old 65 × 141 mm draft values and unused 60 mm runtime seed are **not measured display sizes**.
- Keep the iPhone fixed, brightness high, and Auto-Lock off during the test. Restore Auto-Lock afterwards.

Floor axes: X east, Y up, Z south. Conversion:

`x = (pixelX - 686) × 0.05170`

`z = (pixelY - 606) × 0.05170`

ARCore image +X points right, +Y out of the image plane, and +Z down. For this east-facing sign, image-right points north; its floor-frame axes are `(-Z, +X, -Y)`. See [ARCore image coordinates](https://developers.google.com/ar/reference/java/com/google/ar/core/AugmentedImage#getCenterPose()).

## Edit and verify

Edit `origin`, `scale`, `trace`, and the marker transform in `tools/generate-assets.py`; regenerate with Python, Pillow and qrcode, then rebuild the APK. Width and height can be changed on-device before starting. Sideways movement or a different facing direction requires a map change.

Check alignment at the start, doorway, lower-corridor turn and ballroom endpoint. Correct geometry if arrows cross a wall or leave the actual corridor. Compilation and unit tests do not establish physical calibration. No custom drift correction is included. Temporary ARCore tracking uncertainty preserves the session; off-corridor estimates no longer stop guidance.
