package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * The month's weather over the season_scene landscape: snow in December, rain in April, petals in February and
 * March, butterflies in May and November, bats in October, birds in June and July, fireflies and shooting stars
 * in August, fireworks in January and September. State lives in fixed arrays, so a frame allocates nothing
 * (a shooting star's short gradient aside). Coordinates are SeasonScene's 1600x900 space.
 */
final class SeasonFx {

    private static final float W = SeasonScene.W, H = SeasonScene.H;

    private final SeasonScene scene;
    private final Random rnd = new Random();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private final boolean snow, rain, petals, butterflies, bats, gulls, birds, fireflies, meteors, fireworks;
    private final int petalColor;
    private final int[] colors;

    // one set of arrays per kind; only the month's kinds are filled
    private float[] sx, sy, sr, sv, sp;                       // snow
    private float[] rx, ry, rv;                               // rain
    private float[] px, py, pv, prot, pvr, pph, ps;           // petals
    private float[] bx, by, bvx, bph, bs;                     // butterflies and bats
    private int[] bcol;
    private float[] gx, gy, gv, gph, gs;                      // gulls or birds
    private float[] fx, fy, fph, fsp;                         // fireflies
    private final float[] mx = new float[4], my = new float[4], ml = new float[4], ma = new float[4];
    private float nextMeteor = 1;
    private static final int SPARKS = 420;
    private final float[] kx = new float[SPARKS], ky = new float[SPARKS], kvx = new float[SPARKS],
            kvy = new float[SPARKS], ka = new float[SPARKS];
    private final int[] kc = new int[SPARKS];
    private int nextSpark;
    private float nextBurst = .3f;

    SeasonFx(SeasonScene scene) {
        this.scene = scene;
        int m = scene.month;
        snow = m == 11;
        rain = m == 3;
        petals = m == 1 || m == 2 || m == 10;
        butterflies = m == 4 || m == 10;
        bats = m == 9;
        gulls = m == 5;
        birds = m == 6;
        fireflies = m == 7;
        meteors = m == 7;
        fireworks = m == 0 || m == 8;
        petalColor = m == 1 ? 0xFFFFB3C8 : m == 2 ? 0xFFF8C3D4 : 0xFFFFA31A;
        colors = m == 0 ? new int[]{0xFFFFD166, 0xFFFFFFFF, 0xFFFF8FAB}
                : m == 8 ? new int[]{0xFF4997D0, 0xFFFFFFFF, 0xFF9FD3FF}
                : m == 4 ? new int[]{0xFFFFD23F, 0xFFFF7AA2, 0xFFB48CFF, 0xFF7AD7FF}
                : new int[]{0xFFFF8A1F, 0xFFFF7A00, 0xFFFFA31A};
        s.setStrokeCap(Paint.Cap.ROUND);
        s.setStyle(Paint.Style.STROKE);
        seed();
    }

    private float r() {
        return rnd.nextFloat();
    }

    private void seed() {
        if (snow) {
            int n = 160;
            sx = new float[n]; sy = new float[n]; sr = new float[n]; sv = new float[n]; sp = new float[n];
            for (int i = 0; i < n; i++) {
                sx[i] = r() * W; sy[i] = r() * H; sr[i] = .8f + r() * 2.6f; sv[i] = 20 + r() * 50; sp[i] = r() * 6;
            }
        }
        if (rain) {
            int n = 130;
            rx = new float[n]; ry = new float[n]; rv = new float[n];
            for (int i = 0; i < n; i++) {
                rx[i] = r() * W; ry[i] = r() * H; rv[i] = 700 + r() * 400;
            }
        }
        if (petals) {
            int n = butterflies ? 16 : 36;
            px = new float[n]; py = new float[n]; pv = new float[n]; prot = new float[n]; pvr = new float[n];
            pph = new float[n]; ps = new float[n];
            for (int i = 0; i < n; i++) {
                px[i] = r() * W; py[i] = r() * H; pv[i] = 30 + r() * 40; prot[i] = r() * 6; pvr[i] = (r() - .5f) * 3;
                pph[i] = r() * 6; ps[i] = 5 + r() * 5;
            }
        }
        if (butterflies || bats) {
            int n = bats ? 11 : 9;
            bx = new float[n]; by = new float[n]; bvx = new float[n]; bph = new float[n]; bs = new float[n];
            bcol = new int[n];
            for (int i = 0; i < n; i++) {
                bx[i] = r() * W;
                if (bats) {
                    by[i] = H * (.08f + r() * .45f); bvx[i] = -(60 + r() * 90); bph[i] = r() * 6; bs[i] = 20 + r() * 30;
                    bcol[i] = 0xFF0A0610;
                } else {
                    by[i] = H * (.35f + r() * .4f); bvx[i] = (r() < .5f ? -1 : 1) * (30 + r() * 40); bph[i] = r() * 6;
                    bs[i] = 22 + r() * 16; bcol[i] = colors[i % colors.length];
                }
            }
        }
        if (gulls || birds) {
            int n = birds ? 9 : 6;
            gx = new float[n]; gy = new float[n]; gv = new float[n]; gph = new float[n]; gs = new float[n];
            for (int i = 0; i < n; i++) {
                gx[i] = r() * W; gy[i] = H * (.12f + r() * .3f); gv[i] = (birds ? 50 : 35) + r() * 25; gph[i] = r() * 6;
                gs[i] = birds ? 7 + r() * 6 : 12 + r() * 8;
            }
        }
        if (fireflies) {
            int n = 40;
            fx = new float[n]; fy = new float[n]; fph = new float[n]; fsp = new float[n];
            for (int i = 0; i < n; i++) {
                fx[i] = r() * W; fy[i] = H * (.58f + r() * .32f); fph[i] = r() * 6; fsp[i] = .5f + r();
            }
        }
    }

    private static float sin(double v) {
        return (float) Math.sin(v);
    }

    /** Advances everything by dt seconds; t is the scene clock. */
    void step(float dt, float t) {
        if (snow) for (int i = 0; i < sx.length; i++) {
            sy[i] += sv[i] * dt;
            sx[i] += sin(t + sp[i]) * 12 * dt;
            if (sy[i] > H) { sy[i] = -5; sx[i] = r() * W; }
        }
        if (rain) for (int i = 0; i < rx.length; i++) {
            ry[i] += rv[i] * dt;
            rx[i] -= rv[i] * dt * .15f;
            if (ry[i] > H) { ry[i] = -20; rx[i] = r() * W * 1.1f; }
        }
        if (petals) for (int i = 0; i < px.length; i++) {
            py[i] += pv[i] * dt;
            px[i] += (-18 + sin(t * 1.3f + pph[i]) * 30) * dt;
            prot[i] += pvr[i] * dt;
            if (py[i] > H + 10) { py[i] = -10; px[i] = r() * W * 1.1f; }
        }
        if (butterflies || bats) for (int i = 0; i < bx.length; i++) {
            bx[i] += bvx[i] * dt;
            by[i] += sin(t * (bats ? 2 : 1.6f) + bph[i]) * (bats ? 40 : 30) * dt;
            if (bats) {
                if (bx[i] < -60) { bx[i] = W + 60; by[i] = H * (.08f + r() * .45f); }
            } else {
                if (bx[i] < -40) bx[i] = W + 40;
                if (bx[i] > W + 40) bx[i] = -40;
            }
        }
        if (gulls || birds) for (int i = 0; i < gx.length; i++) {
            gx[i] += gv[i] * dt;
            if (gx[i] > W + 30) { gx[i] = -30; gy[i] = H * (.12f + r() * .3f); }
        }
        if (meteors) {
            nextMeteor -= dt;
            if (nextMeteor < 0) {
                nextMeteor = 1.5f + r() * 2.5f;
                for (int i = 0; i < mx.length; i++) if (ma[i] <= 0) {
                    mx[i] = W * (.2f + r() * .7f); my[i] = H * (.03f + r() * .25f); ml[i] = 0; ma[i] = 1;
                    break;
                }
            }
            for (int i = 0; i < mx.length; i++) if (ma[i] > 0) {
                mx[i] -= 900 * dt; my[i] += 420 * dt; ml[i] = Math.min(ml[i] + 1500 * dt, 240); ma[i] -= dt * 1.2f;
            }
        }
        if (fireworks) {
            nextBurst -= dt;
            if (nextBurst < 0) {
                nextBurst = .9f + r() * 1.4f;
                float x = W * (.3f + r() * .6f), y = H * (.12f + r() * .3f);
                int col = colors[rnd.nextInt(colors.length)];
                for (int i = 0; i < 70; i++) {
                    int k = nextSpark;
                    nextSpark = (nextSpark + 1) % SPARKS;
                    float a = r() * 7, v = 60 + r() * 170;
                    kx[k] = x; ky[k] = y; kvx[k] = (float) Math.cos(a) * v; kvy[k] = sin(a) * v; ka[k] = 1; kc[k] = col;
                }
            }
            for (int k = 0; k < SPARKS; k++) if (ka[k] > 0) {
                kx[k] += kvx[k] * dt; ky[k] += kvy[k] * dt; kvy[k] += 70 * dt; kvx[k] *= .985f; ka[k] -= dt * .7f;
            }
        }
    }

    private static int alpha(int color, float a) {
        return (Math.round(255 * Math.max(0f, Math.min(1f, a))) << 24) | (color & 0xFFFFFF);
    }

    void draw(Canvas c, float t) {
        if (snow) {
            p.setColor(0xD9FFFFFF);
            for (int i = 0; i < sx.length; i++) c.drawCircle(sx[i], sy[i], sr[i], p);
        }
        if (rain) {
            s.setColor(0x52DCEBFF);
            s.setStrokeWidth(1.4f);
            for (int i = 0; i < rx.length; i++) c.drawLine(rx[i], ry[i], rx[i] + 4, ry[i] - 24, s);
        }
        if (petals) {
            p.setColor(alpha(petalColor, .85f));
            for (int i = 0; i < px.length; i++) {
                c.save();
                c.translate(px[i], py[i]);
                c.rotate((float) Math.toDegrees(prot[i]));
                oval.set(-ps[i], -ps[i] * .55f, ps[i], ps[i] * .55f);
                c.drawOval(oval, p);
                c.restore();
            }
        }
        if (butterflies || bats) for (int i = 0; i < bx.length; i++) {
            float f = bats ? .35f + .65f * Math.abs(sin(t * 8 + bph[i])) : .25f + .75f * Math.abs(sin(t * 9 + bph[i]));
            if (bats) {
                scene.animal(c, SeasonScene.BAT, bx[i], by[i] + bs[i] * .5f, bs[i], bcol[i], false, 0, 1, f, 1);
            } else {
                scene.animal(c, SeasonScene.BUTTERFLY, bx[i], by[i] + bs[i] * .5f, bs[i], bcol[i], bvx[i] > 0, 0, f, 1, 1);
            }
        }
        if (gulls || birds) {
            s.setColor(birds ? 0xD91E0A08 : 0xD9FFF0E6);
            s.setStrokeWidth(2.4f);
            for (int i = 0; i < gx.length; i++) {
                float f = sin(t * 6 + gph[i]), x = gx[i], y = gy[i], sz = gs[i];
                c.drawLine(x - sz, y - f * sz * .3f, x - sz * .4f, y - sz * .5f * f - 2, s);
                c.drawLine(x - sz * .4f, y - sz * .5f * f - 2, x, y, s);
                c.drawLine(x, y, x + sz * .4f, y - sz * .5f * f - 2, s);
                c.drawLine(x + sz * .4f, y - sz * .5f * f - 2, x + sz, y - f * sz * .3f, s);
            }
        }
        if (fireflies) for (int i = 0; i < fx.length; i++) {
            float x = fx[i] + sin(t * .4f * fsp[i] + fph[i]) * 40, y = fy[i] + (float) Math.cos(t * .5f * fsp[i] + fph[i]) * 20;
            float a = Math.max(0, sin(t * 1.5f * fsp[i] + fph[i]));
            if (a > 0) scene.glow(c, x, y, 12, 0xDCFF8C, a);
        }
        if (meteors) {
            s.setStrokeWidth(2);
            for (int i = 0; i < mx.length; i++) if (ma[i] > 0) {
                float ex = mx[i] + ml[i] * .9f, ey = my[i] - ml[i] * .42f;
                s.setShader(new LinearGradient(mx[i], my[i], ex, ey, alpha(0xFFFFFF, ma[i]), 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawLine(mx[i], my[i], ex, ey, s);
            }
            s.setShader(null);
        }
        if (fireworks) for (int k = 0; k < SPARKS; k++) if (ka[k] > 0) {
            p.setColor(alpha(kc[k], ka[k] * .9f));
            c.drawRect(kx[k] - 1.4f, ky[k] - 1.4f, kx[k] + 1.4f, ky[k] + 1.4f, p);
        }
    }
}
