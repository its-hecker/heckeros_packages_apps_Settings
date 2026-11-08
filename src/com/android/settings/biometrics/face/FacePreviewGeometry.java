/* Copyright (C) 2026 HeckerOS; SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.biometrics.face;

/** Camera preview geometry independent of display resolution and density. */
final class FacePreviewGeometry {
    private FacePreviewGeometry() {}

    /** Returns the TextureView scales and translations, preserving its camera orientation. */
    static float[] transform(int viewWidth, int viewHeight, int previewWidth, int previewHeight,
            float zoom, float offsetX, float offsetY) {
        if (viewWidth <= 0 || viewHeight <= 0 || previewWidth <= 0 || previewHeight <= 0
                || !Float.isFinite(zoom) || zoom < 1f
                || !Float.isFinite(offsetX) || !Float.isFinite(offsetY)) {
            throw new IllegalArgumentException("Invalid face preview geometry");
        }
        float x = (float) viewWidth / previewWidth;
        float y = (float) viewHeight / previewHeight;
        float smaller = Math.min(x, y);
        float scaleX = x / smaller * zoom;
        float scaleY = y / smaller * zoom;
        return new float[] {scaleX, scaleY,
                viewWidth * (1f - scaleX) / 2f + offsetX,
                viewHeight * (1f - scaleY) / 2f + offsetY};
    }
}
