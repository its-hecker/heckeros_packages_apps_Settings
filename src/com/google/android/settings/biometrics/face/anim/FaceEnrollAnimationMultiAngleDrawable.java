/* SPDX-License-Identifier: Apache-2.0 */
package com.google.android.settings.biometrics.face.anim;

/** Binary callback contract used by the original Pixel grid rendering classes. */
public final class FaceEnrollAnimationMultiAngleDrawable {
    private FaceEnrollAnimationMultiAngleDrawable() {}

    public interface BucketListener {
        void onNoActivityAnimationFinished();
        void onStartFinishing();
    }
}
