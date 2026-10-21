package com.android.settings.biometrics.face;

import java.util.Arrays;

/** Geometry invariants and the relationship between HAL bucket order and visual guidance. */
public final class FacePoseGridGeometryTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--json")) { export(); return; }
        float[] centerX = new float[25], centerY = new float[25];
        for (int bucket = 0; bucket < 25; bucket++) {
            float[] points = FacePoseGridGeometry.cell(bucket);
            check(points.length >= 8 && points.length % 2 == 0, "closed cell data");
            double area = 0;
            for (int i = 0; i < points.length; i += 2) {
                float x = points[i], y = points[i + 1];
                check(Float.isFinite(x) && Float.isFinite(y), "finite cell coordinates");
                check(x * x + y * y <= 1.00001f, "all cell edges stay inside camera cutout");
                int next = (i + 2) % points.length;
                area += x * points[next + 1] - points[next] * y;
                centerX[bucket] += x / (points.length / 2);
                centerY[bucket] += y / (points.length / 2);
            }
            check(area > 0.0001, "nondegenerate clockwise cell, not a crossing polygon");
        }
        for (int bucket = 0; bucket < 25; bucket++) {
            if (bucket % 5 != 4) check(centerX[bucket] < centerX[bucket + 1], "HAL columns increase left to right");
            if (bucket < 20) check(centerY[bucket] < centerY[bucket + 5], "HAL rows increase top to bottom");
            int mirrored = (bucket / 5) * 5 + 4 - bucket % 5;
            check(Math.abs(centerX[bucket] + centerX[mirrored]) < 0.00001, "symmetric mesh");
        }
        check(Math.abs(centerX[12]) < 0.00001 && Math.abs(centerY[12]) < 0.00001, "1113 is central pose");
        for (int invalid : new int[] {-1, 25, Integer.MAX_VALUE}) {
            boolean rejected = false;
            try { FacePoseGridGeometry.cell(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid HAL bucket rejected");
        }
        boolean[] acquired = new boolean[25];
        check(FacePoseGridGeometry.nextMissing(acquired) == 12, "start guidance at real center");
        check(Arrays.equals(acquired, new boolean[25]), "guidance does not invent acquisitions");
        for (int i = 0; i < 25; i++) {
            int next = FacePoseGridGeometry.nextMissing(acquired);
            check(next >= 0 && next < 25 && !acquired[next], "guide only uncaptured real angles");
            acquired[next] = true;
        }
        check(FacePoseGridGeometry.nextMissing(acquired) == -1, "all captured stops decorative guidance");
        System.out.println("Hecker curved pose geometry and HAL bucket guidance: PASS");
    }

    private static void export() {
        StringBuilder json = new StringBuilder("[");
        for (int bucket = 0; bucket < 25; bucket++) {
            if (bucket > 0) json.append(',');
            json.append(Arrays.toString(FacePoseGridGeometry.cell(bucket)));
        }
        System.out.println(json.append(']'));
    }
}
