HeckerOS face enrollment uses its own native curved 25-cell pose mesh in
`FacePoseGridView` and `FacePoseGridGeometry`. The cells keep the Pixel HAL's
row-major bucket order (help codes 1101–1125). The center is bucket 12 / code 1113.
Captured poses receive a short theme-accent outline/fill transition. The camera
preview remains visible through the low-opacity cells. A quiet outer ring and
one brief missing-angle cue replace the stock overlay's heavier scrims.

Rendering allocates cell paths only after a size change. There is no idle frame
loop. Capture/guidance animations are bounded, respect Android's animation-disable
setting, and are cancelled on pause, error, completion, visibility loss or detach.
Acquired poses survive visibility changes. Screen-reader guidance remains owned
by the existing native HAL help text; decorative cells do not announce themselves.

The existing HAL-owned SurfaceView, exact 20 px cutout inset, fixed camera buffer,
credential checks, cancellation, removal and enrollment flow are preserved.
Filling all 25 UI cells never finishes enrollment. Only the native biometric
callback with `remaining == 0` launches the finish page.

The original Pixel SettingsGoogle `anim.curve` archive remains pinned here with
its provenance and license in ORIGIN.json / NOTICE. It was used for the previous
stock renderer; the current Hecker pose view does not instantiate those classes.
Original Google bytes remain proprietary; the Settings package's Apache license
does not relicense them. Original activities, credentials, sidecar and camera
controllers are excluded from the archive.

Host geometry/callback checks and Android 17 Java/resource checks are source
validation. Coral hardware validation remains required for camera alignment,
contrast, cancel/retry, removal/re-enrollment, screen off/on, light/dark themes and
reduced motion. Play Integrity remains a separate Settings component and does
not alter biometric challenges, face templates, authentication or the Face HAL.
