/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.biometrics.face;

/** Host regression checks for Flame/Coral preview centring and resize/calibration. */
public final class FacePreviewGeometryTest {
    private static void close(float actual, float expected) {
        if (Math.abs(actual - expected) > 0.002f) {
            throw new AssertionError(actual + " != " + expected);
        }
    }

    public static void main(String[] args) {
        for (int size : new int[] {453, 720, 946, 1260, 1440}) {
            for (int[] frame : new int[][] {{1440, 1080}, {1280, 960}, {1920, 1080}}) {
                float[] value = FacePreviewGeometry.transform(size, size, frame[0], frame[1],
                        1.05f, 0f, 0f);
                // Both transformed centre coordinates must remain on the view centre.
                close(size * value[0] / 2f + value[2], size / 2f);
                close(size * value[1] / 2f + value[3], size / 2f);
                if (value[0] < 1 || value[1] < 1) throw new AssertionError("Preview leaves a gap");
            }
        }
        float[] flame = FacePreviewGeometry.transform(946, 946, 1440, 1080, 1.05f, 0, 0);
        close(flame[2], -23.65f);
        close(flame[3], -189.2f);
        float[] calibrated = FacePreviewGeometry.transform(1260, 1260, 1440, 1080, 1.05f, 8, -5);
        close(630 * calibrated[0] + calibrated[2], 638);
        close(630 * calibrated[1] + calibrated[3], 625);
        try {
            FacePreviewGeometry.transform(0, 0, 1440, 1080, 1, 0, 0);
            throw new AssertionError("Invalid view accepted");
        } catch (IllegalArgumentException expected) {}
        System.out.println("Face preview: Flame/Coral, resize, supported stream fallback and calibration PASS");
    }
}
