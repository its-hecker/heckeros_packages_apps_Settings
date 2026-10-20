The 25-cell circular pose grid is extracted from the pinned Pixel SettingsGoogle
renderer identified in ORIGIN.json. Only the `anim.curve` classes are retained.
The original Google enrollment activities, credential code, sidecar and camera
controllers are excluded. The surrounding adapter and callback contract are
HeckerOS source. Original Google rendering bytes remain Google proprietary code;
the Settings package's Apache license does not relicense these bytes.

Native `FaceEnrollPreviewFragment` forwards real help codes 1101–1125 to the grid.
The HAL-owned SurfaceView, supported buffer size and credential flow are retained.
The animation callback cannot complete enrollment. Only the existing
`FaceEnrollEnrolling` callback with `remaining == 0` launches the finish page.

The original renderers have private Handler/Animator timers and no close method.
PixelFaceAnimationLifecycle visits only the pinned curve object graph to cancel
those timers on pause, error, completion and detach. Matching R8 keep rules are
in proguard.flags. Hardware validation on coral remains required, including
cancel/retry, removal/re-enrollment, screen off/on, dark theme and reduced motion.

Native Play Integrity page is a separate Settings component. It does not alter
biometric challenges, enrollment, authentication, face templates or the Face HAL.
