package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Calendar;

/**
 * The month's landscape behind Home and the clock screensavers (config.xml / theme season_scene).
 *
 * The still landscape is drawn once per month into one 1280x720 bitmap on a background thread and stretched
 * over the screen. On top of it, each frame draws the month's animals and weather, about 30 times a second,
 * only between {@link #start} and {@link #stop}: Home calls them from onResume and onPause, the screensavers
 * while they show. With motion off (reduced_motion, or animations false) it draws one still frame.
 */
public class SeasonView extends View {

    private static final long FRAME_MS = 33;
    private static final int TEX_W = 1280;

    private boolean enabled, motion, running;
    private int fixedMonth;
    private SeasonScene scene;
    private SeasonFx fx;
    private Bitmap land;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dst = new Rect();
    private long clockStart = SystemClock.uptimeMillis(), lastFrame;

    public SeasonView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Opaque and full-screen: nothing under it needs drawing. */
    @Override
    public boolean isOpaque() {
        return enabled && land != null;
    }

    /** {@code month} 1 to 12 keeps that month; 0 follows the calendar. Off hides the view and draws nothing. */
    public void setup(boolean on, int month, boolean withMotion) {
        enabled = on;
        fixedMonth = month;
        motion = withMotion;
        setVisibility(on ? VISIBLE : GONE);
        if (on) ensureScene();
    }

    public boolean isOn() {
        return enabled;
    }

    /** The scene's accent colour, or 0 while off. */
    public int accent() {
        return enabled && scene != null ? scene.accent : 0;
    }

    private int month() {
        return fixedMonth > 0 ? fixedMonth - 1 : Calendar.getInstance().get(Calendar.MONTH);
    }

    public void start() {
        if (!enabled) return;
        ensureScene();
        running = motion;
        lastFrame = SystemClock.uptimeMillis();
        invalidate();
    }

    public void stop() {
        running = false;
    }

    /** Picks up a new month; cheap enough to call every minute. */
    public void update() {
        if (enabled) ensureScene();
    }

    private void ensureScene() {
        int m = month();
        if (scene != null && scene.month == m) return;
        if (scene != null) scene.recycle();
        scene = new SeasonScene(m);
        fx = new SeasonFx(scene);
        land = null;
        build();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (enabled && land == null) build();
    }

    private void build() {
        if (getWidth() == 0 || scene == null) return;
        final SeasonScene sc = scene;
        final int w = Math.min(getWidth(), TEX_W), h = Math.round(w * 9 / 16f);
        new Thread(() -> {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(w / SeasonScene.W, h / SeasonScene.H);
            sc.draw(c);
            post(() -> {
                if (sc == scene) {
                    if (land != null) land.recycle();
                    land = b;
                    invalidate();
                } else {
                    b.recycle();
                }
            });
        }, "season-scene").start();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!enabled || land == null || scene == null) return;
        dst.set(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(land, null, dst, bitmapPaint);
        long now = SystemClock.uptimeMillis();
        float t = running ? (now - clockStart) / 1000f : 0;
        float dt = Math.min(.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int save = canvas.save();
        canvas.scale(getWidth() / SeasonScene.W, getHeight() / SeasonScene.H);
        scene.act(canvas, t);
        scene.shade(canvas);
        if (running) {
            fx.step(dt, t);
            fx.draw(canvas, t);
        }
        canvas.restoreToCount(save);
        if (running) postInvalidateDelayed(FRAME_MS);
    }
}
