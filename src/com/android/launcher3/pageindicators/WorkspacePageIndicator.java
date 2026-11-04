package com.android.launcher3.pageindicators;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.util.Themes;

/**
 * One UI style workspace page indicator.
 *
 * Instead of the AOSP scroll progress line, this draws a row of markers below
 * the hotseat: the current page is a house outline (the "home" marker, like
 * One UI) and every other page is a small dot. The markers stay visible at all
 * times. In edit mode (One UI "organize" / spring loaded) a plus button is
 * shown after the markers which appends a new empty page, mirroring the plus
 * at the end of the One UI page rail.
 */
public class WorkspacePageIndicator extends View implements Insettable, PageIndicator {

    private static final float DOT_RADIUS_DP = 1.8f;
    private static final float MARKER_GAP_DP = 16f;
    private static final float HOUSE_WIDTH_DP = 11.5f;
    private static final float HOUSE_HEIGHT_DP = 11f;
    private static final float HOUSE_STROKE_DP = 1.6f;
    private static final float PLUS_HALF_DP = 6f;
    private static final float PLUS_HIT_WIDTH_DP = 44f;
    private static final float PLUS_LEAD_GAP_DP = 12f;

    private final Launcher mLauncher;
    private final Paint mMarkerPaint;
    private final Path mHousePath = new Path();

    private int mActivePage = 0;
    private int mNumPages = 0;
    private boolean mInEditMode = false;

    // Geometry recomputed on draw; kept as fields so touch handling matches
    // exactly what is painted.
    private float mPlusHitLeft = Float.MAX_VALUE;
    private float mPlusHitRight = Float.MAX_VALUE;

    private final StateManager.StateListener<LauncherState> mStateListener =
            new StateManager.StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    setEditMode(finalState == LauncherState.EDIT_MODE
                            || finalState == LauncherState.SPRING_LOADED);
                }
            };

    public WorkspacePageIndicator(Context context) {
        this(context, null);
    }

    public WorkspacePageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkspacePageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLauncher = Launcher.getLauncher(context);

        Resources res = context.getResources();
        float density = res.getDisplayMetrics().density;
        mMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mMarkerPaint.setStrokeWidth(HOUSE_STROKE_DP * density);
        mMarkerPaint.setStyle(Paint.Style.STROKE);
        mMarkerPaint.setStrokeJoin(Paint.Join.ROUND);
        mMarkerPaint.setStrokeCap(Paint.Cap.ROUND);

        boolean darkText = Themes.getAttrBoolean(mLauncher, R.attr.isWorkspaceDarkText);
        mMarkerPaint.setColor(darkText ? Color.BLACK : Color.WHITE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mLauncher.getStateManager().addStateListener(mStateListener);
        setEditMode(mLauncher.getStateManager().getState() == LauncherState.EDIT_MODE
                || mLauncher.getStateManager().getState() == LauncherState.SPRING_LOADED);
    }

    @Override
    protected void onDetachedFromWindow() {
        mLauncher.getStateManager().removeStateListener(mStateListener);
        super.onDetachedFromWindow();
    }

    private void setEditMode(boolean editMode) {
        if (mInEditMode != editMode) {
            mInEditMode = editMode;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mNumPages <= 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float gap = MARKER_GAP_DP * density;
        float dotR = DOT_RADIUS_DP * density;

        float centerY = getHeight() / 2f;
        float startX = getWidth() / 2f - (mNumPages - 1) * gap / 2f;

        for (int i = 0; i < mNumPages; i++) {
            float cx = startX + i * gap;
            if (i == mActivePage) {
                drawHouse(canvas, cx, centerY,
                        HOUSE_WIDTH_DP * density, HOUSE_HEIGHT_DP * density);
            } else {
                mMarkerPaint.setStyle(Paint.Style.FILL);
                mMarkerPaint.setAlpha(150);
                canvas.drawCircle(cx, centerY, dotR, mMarkerPaint);
                mMarkerPaint.setStyle(Paint.Style.STROKE);
                mMarkerPaint.setAlpha(255);
            }
        }

        // The plus button stays visible at the end of the rail: on One UI it
        // lives in the page-overview editor, but this build has no overview
        // entry point yet, so the plus is exposed directly (ref04).
        float plusCx = startX + (mNumPages - 1) * gap + PLUS_LEAD_GAP_DP * density
                + PLUS_HALF_DP * density;
        mPlusHitLeft = plusCx - PLUS_HIT_WIDTH_DP * density / 2f;
        mPlusHitRight = plusCx + PLUS_HIT_WIDTH_DP * density / 2f;
        drawPlus(canvas, plusCx, centerY, PLUS_HALF_DP * density);
    }

    /** One UI home marker: a simple outlined house with a peaked roof. */
    private void drawHouse(Canvas canvas, float cx, float cy, float w, float h) {
        float halfW = w / 2f;
        float halfH = h / 2f;
        // The roof spans the full width and rises above the body.
        float eaveY = cy - halfH + h * 0.32f;
        mHousePath.reset();
        mHousePath.moveTo(cx - halfW, cy + halfH);
        mHousePath.lineTo(cx - halfW, eaveY);
        mHousePath.lineTo(cx, cy - halfH);
        mHousePath.lineTo(cx + halfW, eaveY);
        mHousePath.lineTo(cx + halfW, cy + halfH);
        mHousePath.close();
        mMarkerPaint.setStyle(Paint.Style.STROKE);
        mMarkerPaint.setAlpha(255);
        canvas.drawPath(mHousePath, mMarkerPaint);
    }

    private void drawPlus(Canvas canvas, float cx, float cy, float half) {
        mMarkerPaint.setStyle(Paint.Style.STROKE);
        mMarkerPaint.setAlpha(220);
        canvas.drawLine(cx - half, cy, cx + half, cy, mMarkerPaint);
        canvas.drawLine(cx, cy - half, cx, cy + half, mMarkerPaint);
        mMarkerPaint.setAlpha(255);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        boolean inPlusZone = x >= mPlusHitLeft && x <= mPlusHitRight;
        if (!inPlusZone) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            addNewPage();
        }
        return true;
    }

    /**
     * Appends an empty page at the end of the workspace and slides to it, like
     * the plus button on the One UI page rail. The page is marked user-created
     * so it survives [Workspace#stripEmptyScreens] while it is still empty.
     */
    private void addNewPage() {
        Workspace workspace = mLauncher.getWorkspace();
        if (workspace == null || workspace.getChildCount() == 0) {
            return;
        }
        workspace.addUserPage();
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        // The One UI rail is position-based (house + dots); scroll progress is
        // not drawn, but the workspace drives visibility through this call.
    }

    @Override
    public void setActiveMarker(int activePage) {
        if (activePage != mActivePage) {
            mActivePage = activePage;
            invalidate();
        }
    }

    @Override
    public void setMarkersCount(int numMarkers) {
        if (numMarkers != mNumPages) {
            mNumPages = numMarkers;
            invalidate();
        }
    }

    @Override
    public void setShouldAutoHide(boolean shouldAutoHide) {
        // One UI markers are always visible.
    }

    @Override
    public void pauseAnimations() { }

    @Override
    public void skipAnimationsToEnd() { }

    /**
     * Sits just above the navigation bar, inside the hotseat's bottom padding
     * area (like the One UI page rail below the hotseat icons).
     */
    @Override
    public void setInsets(Rect insets) {
        android.widget.FrameLayout.LayoutParams lp =
                (android.widget.FrameLayout.LayoutParams) getLayoutParams();
        int margin = insets.bottom
                + (int) (8f * getResources().getDisplayMetrics().density);
        if (lp.bottomMargin != margin) {
            lp.bottomMargin = margin;
            setLayoutParams(lp);
        }
    }
}
