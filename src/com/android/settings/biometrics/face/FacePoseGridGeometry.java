/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.biometrics.face;

/** Geometry for the Hecker pose mesh. Pixel's 25 vendor buckets are row-major. */
final class FacePoseGridGeometry {
    static final int SIDE = 5;
    static final int COUNT = SIDE * SIDE;
    private static final int EDGE_SAMPLES = 8;
    private static final double GAP = 0.024;

    private FacePoseGridGeometry() {}

    /** Closed spherical cell outline in unit-circle coordinates; no Android dependencies. */
    static float[] cell(int bucket) {
        if (bucket < 0 || bucket >= COUNT) throw new IllegalArgumentException("pose bucket");
        int row = bucket / SIDE, column = bucket % SIDE;
        // Trim the pole singularities so the outer buckets remain legible rounded cells.
        double latitudeLimit = Math.toRadians(74), longitudeLimit = Math.toRadians(78);
        double latitudeStep = 2 * latitudeLimit / SIDE, longitudeStep = 2 * longitudeLimit / SIDE;
        double top = -latitudeLimit + row * latitudeStep + GAP;
        double bottom = -latitudeLimit + (row + 1) * latitudeStep - GAP;
        double left = -longitudeLimit + column * longitudeStep + GAP;
        double right = -longitudeLimit + (column + 1) * longitudeStep - GAP;
        float[] points = new float[4 * EDGE_SAMPLES * 2];
        int offset = 0;
        for (int edge = 0; edge < 4; edge++) {
            for (int sample = 0; sample < EDGE_SAMPLES; sample++) {
                double t = (double) sample / EDGE_SAMPLES;
                double latitude, longitude;
                if (edge == 0) { latitude = top; longitude = left + (right - left) * t; }
                else if (edge == 1) { latitude = top + (bottom - top) * t; longitude = right; }
                else if (edge == 2) { latitude = bottom; longitude = right - (right - left) * t; }
                else { latitude = bottom - (bottom - top) * t; longitude = left; }
                points[offset++] = (float) (Math.sin(longitude) * Math.cos(latitude));
                points[offset++] = (float) Math.sin(latitude);
            }
        }
        return points;
    }

    /** Guide toward the closest unacquired angle, never toward an already captured cell. */
    static int nextMissing(boolean[] acquired) {
        if (acquired == null || acquired.length != COUNT) {
            throw new IllegalArgumentException("pose state");
        }
        int best = -1, distance = Integer.MAX_VALUE;
        for (int bucket = 0; bucket < COUNT; bucket++) {
            if (acquired[bucket]) continue;
            int x = bucket % SIDE - 2, y = bucket / SIDE - 2;
            int squared = x * x + y * y;
            if (squared < distance) { best = bucket; distance = squared; }
        }
        return best;
    }
}
