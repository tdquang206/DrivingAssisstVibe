Vehicle detector audit, 2026-09-05

The 11:33 S24 log still contains the old coordinate calculation. For raw
xywh [0.5016252, 0.36920905, 0.09093833, 0.05980053], an oriented 480x640
image and 80 pixels of horizontal padding, the corrected source produces:

- LTRB: [0.44154138, 0.33930879, 0.56279249, 0.39910931]
- Width: 0.12125111; height: 0.05980053; area: 0.00725088
- Confidence 0.44624457 passes 0.40, and this box passes all size checks.

The reported width 0 and height 0.00009343831 reproduce the old mapper,
which subtracts pixel padding directly from normalized model coordinates.
The installed code and this checkout's corrected mapper therefore differ.
The exact build/install source of that difference has not been established.

Code path review:

- CameraX uses the rear camera, RGBA output, one analysis executor, and
  KEEP_ONLY_LATEST. The analyzer processes synchronously; it does not enqueue
  inference jobs. Skipped frames are cumulative throttling counts, not queue
  depth. Long-duration device stability remains unverified.
- ImageProxy.toBitmap() is followed by a single rotation in the preprocessor.
  Pixels are letterboxed with 114 gray and supplied as RGB float32 in [0, 1].
- The bundled asset uses input [1,640,640,3] and output [1,84,8400].
  Runtime validation now rejects incompatible shapes or datatypes visibly.
  Asset SHA-256: 435e0b6375a32b862ed30cb630256d5dbefb9597c53c87125a66e44ab26cfd68.
- Car/motorcycle/bus/truck scores come from channels 6/7/9/11, respectively.
  Box coordinates are converted to input pixels before removing letterboxing.
  Confidence, box-size and NMS thresholds have not changed.
- NMS always retains at least one candidate when its input is nonempty.
  It cannot explain valid-box count zero.
- Detection counts reach the debug screen through the service StateFlow.
  Initialization/analysis errors are now visible instead of appearing as empty
  detections or stale statistics. The strongest candidate's raw coordinates,
  mapped size and rejection reason are shown on screen.
- Bitmap cleanup, duplicate ImageProxy closure and the FPS interval count were
  corrected; detector close is synchronized with inference.
- Preview drawing still stretches normalized coordinates over FILL_CENTER.
  CameraX preview crop/transform alignment needs a separate correction and
  physical verification. This is downstream of counting and cannot make
  Valid boxes or After NMS zero. Do not treat detection recovery as proof of
  preview alignment.

Dependency review:

- CameraX is pinned consistently to 1.5.0. The official release page lists
  newer releases, including stable 1.6.2. None of the reviewed release notes
  explains the exact arithmetic reproduced by the supplied log.
  https://developer.android.com/jetpack/androidx/releases/camera
- TensorFlow Lite is pinned to 2.16.1. Google now documents migration to
  LiteRT. A runtime migration is separate maintenance work; the device log
  already demonstrates successful inference and meaningful class scores.
  https://developers.google.com/edge/litert/migration
- Compose BOM 2024.09.00 is older UI infrastructure; it does not perform the
  box arithmetic. No dependency versions were changed during this audit.
- CameraX documents transforming analysis coordinates into preview coordinates:
  https://developer.android.com/media/camera/camerax/transform-output

Next physical-device check:

1. Build/install from D:\qcode\DrivingAssisstVibe\DrivingAssisstVibe, or use
   the APK produced under app/build/outputs/apk/debug/app-debug.apk.
2. Stop the existing drive, then launch the installed app and start a new drive.
3. Open Vehicle Detection Debug. Confirm Detector: normalized-boxes-v2 and
   Model SHA: 435e0b6375a3. The revision identifies this diagnostic code, not
   detection accuracy.
4. Point at a stationary car or the same car image. Capture the panel's raw
   xywh, mapped w/h/area, box-check reason and detection counts. The panel
   scrolls if needed. Model/load failures appear as ERROR.
5. Once counts recover, check cars, trucks, buses and motorcycles, box alignment,
   sustained processing performance, camera stability, and Start/Stop when
   returning from Maps. No lead selection or subsequent phase is approved yet.

The APK is a local debug build. If Android reports a signing-key mismatch with
the existing app, rebuild using that installation's original signing key;
do not erase app data merely to bypass the mismatch.

Validation in this checkout: testDebugUnitTest and assembleDebug succeeded.
All seven JVM/Robolectric tests passed, including both S24 coordinate samples.
The APK contains normalized-boxes-v2 and the matching model asset. Native
inference, preview alignment and continuous operation still require S24 testing.
