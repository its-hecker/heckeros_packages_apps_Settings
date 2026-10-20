/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.biometrics.face;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.settings.biometrics.face.anim.FaceEnrollAnimationMultiAngleDrawable;
import com.google.android.settings.biometrics.face.anim.curve.DirectionIndicationHelper;
import com.google.android.settings.biometrics.face.anim.curve.DirectionIndicatorController;
import com.google.android.settings.biometrics.face.anim.curve.GridController;

/** Stock Pixel multi-angle grid over the existing HAL-owned preview surface. */
final class FacePoseGridView extends FrameLayout {
    private static final long GUIDANCE_DELAY_MS = 4000;
    private final FacePoseEnrollmentState mState = new FacePoseEnrollmentState();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ImageView mArrow;
    private final GridCanvas mCanvas;
    private final Paint mMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mMask = new Path();
    private final DirectionIndicationHelper mDirection = new DirectionIndicationHelper();
    private GridController mGrid;
    private DirectionIndicatorController mIndicator;
    private boolean mActive = true;
    private boolean mRunning;
    private boolean mShownInFrame;
    private boolean mGuidancePending;

    private final Runnable mShowGuidance = () -> {
        mGuidancePending = false;
        if (!mRunning || mState.isTerminal() || !mState.isInFrame()) return;
        boolean[] acquired = mState.acquiredCells();
        int bucket = mDirection.getNoProgressBucket(acquired);
        if (bucket < 0 || bucket >= FacePoseEnrollmentState.CELL_COUNT || acquired[bucket]) return;
        mGrid.pulseForNoActivity(bucket, 1);
        if (ValueAnimator.areAnimatorsEnabled()) {
            mIndicator.pulseForNoActivity(mDirection.getNoProgressPulseAngle(acquired), 1);
        }
        // The native error_text owns spoken HAL help; a decorative arrow is not a new event.
    };

    private final FaceEnrollAnimationMultiAngleDrawable.BucketListener mBucketListener =
            new FaceEnrollAnimationMultiAngleDrawable.BucketListener() {
        @Override public void onNoActivityAnimationFinished() { scheduleGuidance(); }
        @Override public void onStartFinishing() {
            // All boxes being filled is visual state only. remaining == 0 finishes enrollment.
        }
    };

    FacePoseGridView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        TypedValue background = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true);
        mMaskPaint.setColor(background.resourceId != 0
                ? context.getColor(background.resourceId) : background.data);
        mCanvas = new GridCanvas(context);
        LayoutParams gridLayout = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT, Gravity.CENTER);
        // Keep the existing preview cutout's exact 20 px inset and camera buffer dimensions.
        gridLayout.setMargins(20, 20, 20, 20);
        addView(mCanvas, gridLayout);
        mArrow = new ImageView(context);
        mArrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(mArrow, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    void onEnrollmentHelp(int code, CharSequence help) {
        if (mState.isTerminal()) return;
        boolean fresh = mState.onHelp(code);
        if (!mRunning) return;
        boolean inFrame = mState.isInFrame();
        if (inFrame != mShownInFrame) {
            mShownInFrame = inFrame;
            if (inFrame) mGrid.onUserEnterGood();
            else { mGrid.onUserLeaveGood(); stopGuidance(); }
        }
        int bucket = FacePoseEnrollmentState.bucketForHelp(code);
        if (fresh) {
            stopGuidance();
            if (inFrame) mGrid.onAcquired(bucket);
            else mGrid.setEarlyDone(bucket);
        } else if (inFrame && bucket >= 0) scheduleGuidance();
        else if (code >= 1126 && code <= 1133) stopGuidance();
        mCanvas.invalidate();
    }

    void setEnrollmentActive(boolean active) {
        mActive = active;
        updateRunning();
    }

    void finish() {
        mState.finish();
        updateRunning();
    }

    private void scheduleGuidance() {
        if (!mRunning || !mState.isInFrame() || mGuidancePending) return;
        mGuidancePending = true;
        mHandler.postDelayed(mShowGuidance, GUIDANCE_DELAY_MS);
    }

    private void stopGuidance() {
        mHandler.removeCallbacks(mShowGuidance);
        mGuidancePending = false;
        if (mIndicator != null) mIndicator.stopCurrentIndication();
        if (mGrid != null) mGrid.stopPulseForNoActivity();
        if (mArrow.getDrawable() instanceof AnimatedVectorDrawable) {
            AnimatedVectorDrawable arrow = (AnimatedVectorDrawable) mArrow.getDrawable();
            arrow.clearAnimationCallbacks();
            arrow.stop();
        }
        mArrow.setImageDrawable(null);
    }

    private void updateRunning() {
        boolean run = mActive && isAttachedToWindow() && getWindowVisibility() == VISIBLE
                && isShown() && !mState.isTerminal();
        if (run == mRunning) return;
        mRunning = run;
        if (run) {
            mGrid = new GridController(getContext(), mBucketListener);
            mIndicator = new DirectionIndicatorController(getContext(), mArrow);
            mIndicator.onBoundsChange(new Rect(20, 20, getWidth() - 20, getHeight() - 20));
            boolean[] acquired = mState.acquiredCells();
            for (int i = 0; i < acquired.length; i++) mGrid.restoreState(i, acquired[i]);
            mShownInFrame = mState.isInFrame();
            if (mShownInFrame) mGrid.onUserEnterGood();
            mCanvas.invalidate();
        } else {
            stopGuidance();
            PixelFaceAnimationLifecycle.stop(mGrid, mIndicator);
            mGrid = null;
            mIndicator = null;
            mCanvas.invalidate();
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateRunning();
    }

    @Override protected void onDetachedFromWindow() {
        mActive = false;
        updateRunning();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // View can deliver this during construction, before the child views exist.
        if (mCanvas != null) updateRunning();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (mCanvas != null) updateRunning();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mIndicator != null) mIndicator.onBoundsChange(new Rect(20, 20, w - 20, h - 20));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mMask.reset();
        mMask.setFillType(Path.FillType.EVEN_ODD);
        mMask.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        mMask.addCircle(getWidth() / 2f, getHeight() / 2f,
                Math.max(0, Math.min(getWidth(), getHeight()) / 2f - 20), Path.Direction.CW);
        canvas.drawPath(mMask, mMaskPaint);
    }

    private final class GridCanvas extends View {
        GridCanvas(Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            if (mGrid != null && mRunning) {
                mGrid.draw(canvas);
                // Original cell animators update their paints, not a View callback.
                if (ValueAnimator.areAnimatorsEnabled()) postInvalidateOnAnimation();
            }
        }
    }
}
