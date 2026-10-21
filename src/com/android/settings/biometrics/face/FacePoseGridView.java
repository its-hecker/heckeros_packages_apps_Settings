/* SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.biometrics.face;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.PathInterpolator;

/** Hecker's curved pose mesh over the unchanged, HAL-owned Pixel preview surface. */
final class FacePoseGridView extends View {
    private static final long GUIDANCE_DELAY_MS = 4000;
    private static final long CAPTURE_DURATION_MS = 260;
    private static final long GUIDANCE_DURATION_MS = 1100;
    private static final int PREVIEW_INSET_PX = 20;

    private final FacePoseEnrollmentState mState = new FacePoseEnrollmentState();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mMask = new Path();
    private final Path mGuideArrow = new Path();
    private final Path[] mCells = new Path[FacePoseGridGeometry.COUNT];
    private final float[] mReveal = new float[FacePoseGridGeometry.COUNT];
    private final RectF mHalo = new RectF();
    private final float mDensity;
    private final int mAccent, mNeutral;
    private boolean[] mAcquired = new boolean[FacePoseGridGeometry.COUNT];
    private boolean mActive = true, mRunning, mGuidancePending, mGuidanceShown;
    private int mGuideBucket = -1;
    private float mGuideStrength;
    private ValueAnimator mCaptureAnimator, mGuideAnimator;

    private final Runnable mClearGuidance = () -> {
        mGuideBucket = -1;
        mGuideStrength = 0;
        invalidate();
    };

    private final Runnable mShowGuidance = () -> {
        mGuidancePending = false;
        if (!mRunning || mState.isTerminal() || !mState.isInFrame()) return;
        mGuideBucket = FacePoseGridGeometry.nextMissing(mAcquired);
        if (mGuideBucket < 0) return;
        mGuidanceShown = true;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            mGuideStrength = 1;
            invalidate();
            mHandler.postDelayed(mClearGuidance, GUIDANCE_DURATION_MS);
            return;
        }
        mGuideAnimator = ValueAnimator.ofFloat(0, 1, 0);
        mGuideAnimator.setDuration(GUIDANCE_DURATION_MS);
        mGuideAnimator.addUpdateListener(animation -> {
            mGuideStrength = (float) animation.getAnimatedValue();
            invalidate();
        });
        mGuideAnimator.start();
        // Native error_text remains the only owner of spoken HAL guidance.
    };

    FacePoseGridView(Context context) {
        super(context);
        mDensity = getResources().getDisplayMetrics().density;
        mAccent = themeColor(android.R.attr.colorAccent, 0xff7daaff);
        mNeutral = themeColor(android.R.attr.textColorPrimary, 0xffe2e9fa);
        mMaskPaint.setColor(themeColor(android.R.attr.colorBackground, 0xff071019));
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setPathEffect(new CornerPathEffect(dp(3.5f)));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void onEnrollmentHelp(int code, CharSequence help) {
        if (mState.isTerminal()) return;
        boolean wasInFrame = mState.isInFrame();
        boolean fresh = mState.onHelp(code);
        mAcquired = mState.acquiredCells();
        if (!mRunning) return;
        if (fresh) {
            stopGuidance();
            mGuidanceShown = false;
            revealCapturedCells();
            scheduleGuidance();
        } else if (mState.isInFrame() && FacePoseEnrollmentState.bucketForHelp(code) >= 0) {
            scheduleGuidance();
        }
        if (!mState.isInFrame() || (code >= 1126 && code <= 1133)) stopGuidance();
        if (!wasInFrame && mState.isInFrame()) {
            mGuidanceShown = false;
            scheduleGuidance();
        }
        invalidate();
    }

    void setEnrollmentActive(boolean active) { mActive = active; updateRunning(); }

    void finish() {
        // Called by the existing native flow; captured UI cells cannot call this.
        mState.finish();
        updateRunning();
        invalidate();
    }

    private void revealCapturedCells() {
        if (mCaptureAnimator != null) mCaptureAnimator.cancel();
        if (!ValueAnimator.areAnimatorsEnabled()) { snapCapturedCells(); return; }
        float[] initial = mReveal.clone();
        mCaptureAnimator = ValueAnimator.ofFloat(0, 1);
        mCaptureAnimator.setDuration(CAPTURE_DURATION_MS);
        mCaptureAnimator.setInterpolator(new PathInterpolator(0.2f, 0, 0, 1));
        mCaptureAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            for (int i = 0; i < mReveal.length; i++) {
                float target = mAcquired[i] ? 1 : 0;
                mReveal[i] = initial[i] + (target - initial[i]) * progress;
            }
            invalidate();
        });
        mCaptureAnimator.start();
    }

    private void snapCapturedCells() {
        for (int i = 0; i < mReveal.length; i++) mReveal[i] = mAcquired[i] ? 1 : 0;
        invalidate();
    }

    private void scheduleGuidance() {
        if (!mRunning || !mState.isInFrame() || mGuidancePending || mGuidanceShown) return;
        mGuidancePending = true;
        mHandler.postDelayed(mShowGuidance, GUIDANCE_DELAY_MS);
    }

    private void stopGuidance() {
        mHandler.removeCallbacks(mShowGuidance);
        mHandler.removeCallbacks(mClearGuidance);
        mGuidancePending = false;
        if (mGuideAnimator != null) {
            mGuideAnimator.cancel();
            mGuideAnimator.removeAllUpdateListeners();
            mGuideAnimator = null;
        }
        mGuideBucket = -1;
        mGuideStrength = 0;
    }

    private void stopRendering() {
        mRunning = false;
        stopGuidance();
        if (mCaptureAnimator != null) {
            mCaptureAnimator.cancel();
            mCaptureAnimator.removeAllUpdateListeners();
            mCaptureAnimator = null;
        }
    }

    private void updateRunning() {
        boolean run = mActive && isAttachedToWindow() && getWindowVisibility() == VISIBLE
                && isShown() && !mState.isTerminal();
        if (run == mRunning) return;
        if (!run) { stopRendering(); return; }
        mRunning = true;
        mAcquired = mState.acquiredCells();
        snapCapturedCells();
        mGuidanceShown = false;
        scheduleGuidance();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); updateRunning();
    }

    @Override protected void onDetachedFromWindow() {
        stopRendering(); super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (mHandler != null) updateRunning();
    }

    @Override protected void onVisibilityChanged(View view, int visibility) {
        super.onVisibilityChanged(view, visibility);
        if (mHandler != null) updateRunning();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float cx = w / 2f, cy = h / 2f;
        // Same exact pixel inset and preview buffer as the original adapter.
        float radius = Math.max(0, Math.min(w, h) / 2f - PREVIEW_INSET_PX);
        float meshRadius = Math.max(0, radius - dp(10));
        float haloRadius = Math.max(0, radius - dp(2));
        mHalo.set(cx - haloRadius, cy - haloRadius, cx + haloRadius, cy + haloRadius);
        mMask.reset();
        mMask.setFillType(Path.FillType.EVEN_ODD);
        mMask.addRect(0, 0, w, h, Path.Direction.CW);
        mMask.addCircle(cx, cy, radius, Path.Direction.CW);
        for (int bucket = 0; bucket < mCells.length; bucket++) {
            float[] points = FacePoseGridGeometry.cell(bucket);
            Path cell = new Path();
            cell.moveTo(cx + points[0] * meshRadius, cy + points[1] * meshRadius);
            for (int i = 2; i < points.length; i += 2) {
                cell.lineTo(cx + points[i] * meshRadius, cy + points[i + 1] * meshRadius);
            }
            cell.close();
            mCells[bucket] = cell;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPath(mMask, mMaskPaint);
        boolean inFrame = mState.isInFrame();
        for (int bucket = 0; bucket < mCells.length; bucket++) {
            Path cell = mCells[bucket];
            if (cell == null) continue;
            float reveal = mReveal[bucket];
            float guide = bucket == mGuideBucket ? mGuideStrength : 0;
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(alpha(mAccent, (int) ((inFrame ? 30 : 15) * reveal)));
            canvas.drawPath(cell, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            if (reveal > 0 || guide > 0) {
                mPaint.setStrokeWidth(dp(5.5f));
                mPaint.setColor(alpha(mAccent, (int) (14 * Math.max(reveal, guide))));
                canvas.drawPath(cell, mPaint);
            }
            mPaint.setStrokeWidth(dp(1.15f + 0.55f * Math.max(reveal, guide)));
            int color = reveal > 0 ? mAccent : mNeutral;
            int opacity = (int) ((inFrame ? 102 : 58) + (inFrame ? 126 : 77) * reveal);
            if (guide > 0) { color = mAccent; opacity = Math.max(opacity, (int) (220 * guide)); }
            mPaint.setColor(alpha(color, opacity));
            canvas.drawPath(cell, mPaint);
        }
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(1));
        mPaint.setColor(alpha(mNeutral, 38));
        canvas.drawOval(mHalo, mPaint);
        drawGuidance(canvas);
        // No idle redraw loop: callbacks and bounded animators invalidate only as needed.
    }

    private void drawGuidance(Canvas canvas) {
        if (mGuideBucket < 0 || mGuideStrength <= 0 || !mRunning || !mState.isInFrame()) return;
        int x = mGuideBucket % FacePoseGridGeometry.SIDE - 2;
        int y = mGuideBucket / FacePoseGridGeometry.SIDE - 2;
        if (x == 0 && y == 0) return; // Center cell is the guidance for looking straight ahead.
        float angle = (float) Math.toDegrees(Math.atan2(y, x));
        mPaint.setColor(alpha(mAccent, (int) (220 * mGuideStrength)));
        mPaint.setStrokeWidth(dp(2.2f));
        canvas.drawArc(mHalo, angle - 13, 26, false, mPaint);
        double radians = Math.toRadians(angle);
        float ux = (float) Math.cos(radians), uy = (float) Math.sin(radians);
        float r = mHalo.width() / 2 - dp(6);
        float tipX = mHalo.centerX() + ux * r, tipY = mHalo.centerY() + uy * r;
        mGuideArrow.reset();
        mGuideArrow.moveTo(tipX - ux * dp(4) - uy * dp(3), tipY - uy * dp(4) + ux * dp(3));
        mGuideArrow.lineTo(tipX, tipY);
        mGuideArrow.lineTo(tipX - ux * dp(4) + uy * dp(3), tipY - uy * dp(4) - ux * dp(3));
        canvas.drawPath(mGuideArrow, mPaint);
    }

    private int themeColor(int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(attribute, value, true)) return fallback;
        return value.resourceId != 0 ? getContext().getColor(value.resourceId) : value.data;
    }

    private static int alpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) { return value * mDensity; }
}
