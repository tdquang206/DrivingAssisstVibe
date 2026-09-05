# DrivingAssistVibe — Master Plan

Last updated: 2026-09-05

## Goal

Turn a spare Samsung Android phone into an offline-first dashcam and experimental
driver-assistance device. Mounted facing forward, it should monitor GPS speed,
detect vehicles, estimate the distance and following gap to the relevant vehicle
ahead, and give short English or Vietnamese voice warnings.

Drive Mode must continue through a foreground service while Google Maps or
another app is visible. A small draggable HUD provides optional status; voice
is the main warning channel. Video recording and local trip history come later.

Distance estimates are approximate. Warnings may be incorrect or missed. This
is not certified ADAS or collision-prevention equipment, and must never imply
guaranteed safety.

## Current work

**Phase 1: validate raw vehicle detection on the physical Samsung Galaxy S24.**

Raw car detection now works in the user's camera test against car images on a
screen. Preview box alignment is the next fix. This result does not yet establish
reliable detection of real vehicles or sustained driving performance.

| Phase | Scope | Status |
| --- | --- | --- |
| 0 | App shell, permissions, driving service, camera, GPS, HUD, TTS | Foundation implemented; full device acceptance pending |
| 1 | Raw vehicle detection, diagnostics, then lead selection | In progress; lead selection on hold |
| 2 | Calibration and experimental distance estimation | Planned |
| 3 | Following-gap calculation and stable voice warnings | Planned |
| 4 | Local trip logging, history, and route maps | Planned |
| 5 | Optional segmented video recording and retention | Planned |

An implemented feature is not automatically device-validated. Keep code progress
and physical-device acceptance separate when updating this plan.

## Confirmed progress

- Kotlin and Jetpack Compose app with Start/Stop Drive and developer navigation.
- Foreground service owns CameraX, GPS tracking, HUD and TTS components.
- Start/Stop button now observes service state instead of a screen-local flag.
- Vehicle Detection Debug uses the service's rear camera with live preview,
  class/confidence labels, raw detections, and detector statistics.
- Camera rotation is passed to preprocessing and applied once.
- Fixed normalized model coordinates being treated as pixels before removing
  letterbox padding. This was collapsing valid boxes to zero width.
- Debug panel includes detector revision, model fingerprint, processed-frame
  count, strongest-candidate coordinates, rejection reason, and visible errors.
- FPS interval counting and frame resource cleanup were corrected.
- Home and Debug show the app version and first six Git commit characters.
  `-dirty` means the build includes uncommitted changes; `unknown` means Git
  information was unavailable when building. This identifies the installed
  source revision; it does not check GitHub for updates.

The user's 2026-09-05 11:48 screenshot confirms `normalized-boxes-v2`, model
fingerprint `435e0b6375a3`, 10 detections, maximum vehicle confidence 0.90,
4.4 effective FPS, and 177 ms average inference time. These are observations
from one image-based test, not performance or accuracy guarantees.

Seven JVM/Robolectric tests passed at the detector-audit checkpoint, including
both S24 coordinate regression samples. The subsequent version-label APK also
built successfully. Physical preview alignment and long-run stability remain
unverified. See [the detector audit](vehicle-detector-audit.md) for details.

## Phase 0 — Driving foundation

Goal: prove the app can operate reliably while Google Maps is in the foreground.

Implemented foundation:

- Compose home screen and permission/readiness UI.
- Explicit Start/Stop Drive commands and a foreground-service notification.
- Rear CameraX analysis pipeline and GPS speed tracking.
- Floating speed HUD and English/Vietnamese TTS test controls.
- Service-owned driving state that survives home-screen recreation.

Remaining physical acceptance:

- [ ] Start Drive, open Maps, and confirm camera processing and GPS continue.
- [ ] Reopen the app and return from Debug; confirm the button says Stop Drive.
- [ ] Stop Drive; confirm camera/GPS processing, HUD and notification stop.
- [ ] Verify HUD dragging, touch behavior, and position persistence; record any gaps.
- [ ] Verify both voice languages and interaction with navigation audio.
- [ ] Test permission denial/revocation, missing voice data, and unavailable GPS.
- [ ] Check Samsung battery restrictions, screen dimming and continuous operation.

Do not silently bypass Android or Samsung restrictions. Guide the user to
settings where necessary. Treat camera-binding failures as failures, even if
the foreground service is still alive.

## Phase 1 — Vehicle detection

### Current step: raw detector and preview alignment

- [x] Isolate detection from the Activity and service orchestration.
- [x] Run the bundled YOLO model with a 640×640 input.
- [x] Parse supported car, truck, bus and motorcycle classes.
- [x] Add confidence, size-filter and NMS diagnostics.
- [x] Fix model-coordinate conversion and verify car detections appear.
- [x] Add visible build identity so test results can be tied to installed code.
- [ ] Correct analysis-to-preview coordinate mapping, including rotation,
  letterboxing, preview scaling and cropping. Use the same mapping for boxes
  and labels; verify portrait and landscape behavior.
- [ ] Test real cars at different positions, apparent sizes and lighting levels.
- [ ] Verify real motorcycles, trucks and buses.
- [ ] Assess inference speed, heat, frame handling and sustained CameraX stability.

The current debug screen must continue showing all supported raw detections.
Do not add lead selection, ego-lane ROI, distance estimates, tracking or warnings
to this raw-detector test screen.

### Required approval before lead selection

The user must explicitly confirm all six checks:

1. Cars are detected reliably.
2. Trucks, buses and motorcycles can be detected.
3. Bounding boxes align with the vehicles.
4. Inference speed is acceptable.
5. There is no growing frame-processing queue.
6. CameraX remains stable during continuous use.

A single screenshot, positive detection count, or zero skipped-frame count
does not complete these checks. Keep lead selection on hold until confirmation.

### Later Phase 1 work: lead selection

After approval, implement and test a replaceable lead selector that prioritizes
vehicles in the central driving region, rejects clearly lateral vehicles,
filters by confidence, and stabilizes the selected target between frames.
Keep a raw-detection debug view available for comparison. An existing selector
interface is not evidence that selection is implemented or approved.

## Phase 2 — Calibration and distance estimation

Goal: evaluate useful, confidence-aware estimates before enabling warnings.

- Build a mounted-phone calibration wizard: camera/lens, orientation, mounting
  height, pitch/horizon, and optional measured-distance samples.
- Store calibration per device/mount configuration.
- Introduce a replaceable `DistanceEstimator`; prefer ground-plane geometry
  where practical over assumptions based only on apparent vehicle width.
- Expose raw/smoothed distance, validity and confidence in diagnostics.
- Return no estimate when information is unreliable; never invent a distance.
- Compare estimates against measured distances, including changes in camera
  position, road slope, target class and visibility.

Exit condition: measured tests establish documented limitations and useful
validity/confidence handling. Warnings remain disabled during this evaluation.

## Phase 3 — Following gap and voice warnings

- Calculate following time as distance in metres divided by speed in metres
  per second, only when speed and distance are valid and speed is high enough.
- Add editable following-time and speed-band distance warning modes.
- Store thresholds in settings; do not embed them in warning logic.
- Add persistence, smoothing, hysteresis, cooldown, repeat intervals and severity.
- Represent unavailable inputs as UNKNOWN; do not treat missing data as safe.
- Provide short English/Vietnamese alerts with voice language independent of UI language.
- Verify threshold boundaries, noisy inputs, target loss and navigation audio.

Exit condition: warning behavior is stable and testable, with clear experimental
wording and no claims of guaranteed safety.

## Phase 4 — Local trips and map history

- Make GPS trip logging optional and local by default.
- Record timestamp, position, accuracy, speed, heading, valid distance/gap values,
  and warning events without unnecessary personal diagnostics.
- Add trip summaries: route, duration, distance, average/maximum speed and warning count.
- Display speed categories along routes and warning-event markers on a map.
- Preserve saved trips as the schema evolves; provide user-controlled deletion/export.

Maps/history must not become a dependency of the core driving pipeline.

## Phase 5 — Optional video recording

- Record video in segments, independently of detector inference frequency.
- Provide resolution and storage/retention settings.
- Remove the oldest eligible recordings when the configured limit is reached.
- Test low storage, interrupted recordings, stop/start, recovery and heat.

Core detection and voice functions must remain usable with recording disabled.

## Engineering rules

- Work incrementally. State the architecture and affected files before each step.
- Keep Camera, Location, Detection, Distance, Warning, Voice, Logging, Video and
  Overlay responsibilities separate; avoid broad refactors during diagnosis.
- Keep each step compiling and run checks appropriate to the change.
- Prioritize reliability, low heat, stable warnings and battery use over polish.
- Keep inference at a modest configurable rate rather than assuming 30 FPS.
- Keep camera frames and trip history on-device unless the user explicitly exports them.
- Clearly label any debug/mock data; never use it as real detection or distance evidence.
- Keep normal settings simple and advanced diagnostics in the developer area.
- Evaluate library upgrades against a concrete need and regression checks.
- Do not implement OBD-II in the MVP; preserve a replaceable speed-provider boundary.
- Document physical Samsung results and limitations; do not infer device success
  from compilation, unit tests or emulator behavior alone.

## Next action

Fix preview-coordinate alignment in Phase 1, rebuild with visible version
information, and repeat stationary real-vehicle tests. Record results against
the six approval checks before moving on to lead selection.
