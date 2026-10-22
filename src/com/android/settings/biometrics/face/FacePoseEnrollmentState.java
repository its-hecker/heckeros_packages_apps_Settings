/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.biometrics.face;

/** Presentation state for Pixel 4 vendor acquisition messages. Never enrolls or authenticates. */
public final class FacePoseEnrollmentState {
    public static final int CELL_COUNT = 25;
    private final boolean[] mAcquired = new boolean[CELL_COUNT];
    private boolean mCenterAcquired;
    private boolean mTerminal;
    private int mFovState = 2;
    private int mCandidateState;
    private int mConsecutiveSamples;

    public static boolean usesPoseGrid(String device, boolean halOwnsCamera) {
        // Coral/flame deliver pose acquisition messages on both preview paths.
        // This selects presentation only; camera ownership stays in the preview fragment.
        return halOwnsCamera || "coral".equals(device) || "flame".equals(device);
    }

    public static int bucketForHelp(int code) {
        return code >= 1101 && code <= 1125 ? code - 1101 : -1;
    }

    /** Returns true only for the first real acquisition of a pose cell. */
    public boolean onHelp(int code) {
        if (mTerminal) return false;
        int bucket = bucketForHelp(code);
        if (bucket >= 0) {
            if (code == 1108 || code == 1112 || code == 1113
                    || code == 1114 || code == 1118) mCenterAcquired = true;
            debounce(1, 6);
            boolean fresh = !mAcquired[bucket];
            mAcquired[bucket] = true;
            return fresh;
        }
        if ((code >= 4 && code <= 9) || code == 11) debounce(2, 10);
        else if (code >= 1126 && code <= 1133) debounce(3, 10);
        return false;
    }

    private void debounce(int state, int threshold) {
        if (state != mCandidateState) {
            mCandidateState = state;
            mConsecutiveSamples = 0;
        }
        mConsecutiveSamples = Math.min(threshold, mConsecutiveSamples + 1);
        if (mConsecutiveSamples >= threshold) mFovState = state;
    }

    public boolean isInFrame() { return mFovState == 1 && mCenterAcquired; }
    public boolean isTerminal() { return mTerminal; }
    public void finish() { mTerminal = true; }
    public boolean[] acquiredCells() { return mAcquired.clone(); }
}
