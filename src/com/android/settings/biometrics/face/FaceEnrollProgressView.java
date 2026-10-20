/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.biometrics.face;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import com.android.settings.R;

/** Selects the Pixel pose grid for HAL-owned preview and the existing ring elsewhere. */
public class FaceEnrollProgressView extends FrameLayout {
    private final FacePoseGridView mGrid;
    private final FaceEnrollRingView mRing;

    public FaceEnrollProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (getResources().getBoolean(R.bool.config_face_enroll_hal_owns_camera)) {
            mGrid = new FacePoseGridView(context);
            mRing = null;
            setClipChildren(false);
            addView(mGrid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            mGrid = null;
            mRing = new FaceEnrollRingView(context, attrs);
            addView(mRing, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }

    public void setProgress(int steps, int remaining) {
        if (mRing != null) mRing.setProgress(steps, remaining);
        if (mGrid != null && remaining == 0) mGrid.finish();
    }

    public void onEnrollmentHelp(int code, CharSequence help) {
        if (mGrid != null) mGrid.onEnrollmentHelp(code, help);
        if (mRing != null) mRing.setHelpState();
    }

    public void setHelpState() { if (mRing != null) mRing.setHelpState(); }
    public void setErrorState() {
        if (mGrid != null) mGrid.finish();
        if (mRing != null) mRing.setErrorState();
    }
    public void setCompleteState() {
        if (mGrid != null) mGrid.finish();
        if (mRing != null) mRing.setCompleteState();
    }
    public void setEnrollmentActive(boolean active) {
        if (mGrid != null) mGrid.setEnrollmentActive(active);
    }
}
