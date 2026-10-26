package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The month's landscape for season_scene, drawn from code in a 1600x900 space: January's eagles over snowy
 * peaks, October's owl under an orange moon, December's reindeer in a snowy forest, and so on.
 *
 * {@link #draw} paints the still landscape once, into a bitmap, on a background thread. {@link #act} paints the
 * animals on every frame at time {@code t} in seconds (0 for a still picture), on the main thread. Animals are
 * emoji painted as one-colour silhouettes, cached per emoji and colour, so a frame allocates nothing.
 * Every scene keeps its animals above the lower third, where the dock sits.
 */
final class SeasonScene {

    static final float W = 1600f, H = 900f;
    private static final float TAU = (float) (Math.PI * 2);

    static final String EAGLE = "🦅", SWAN = "🦢", RABBIT = "🐇", DUCK = "🦆",
            DEER = "🦌", WHALE = "🐋", DOLPHIN = "🐬", ELEPHANT = "🐘",
            OWL = "🦉", CAT = "🐈", PUMPKIN = "🎃", BUTTERFLY = "🦋",
            BAT = "🦇";

    /** 0 = January. */
    final int month;
    /** Colour of the focus glow and accents that suit this month. */
    final int accent;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();
    private final RectF dst = new RectF();
    private final Map<String, Bitmap> sprites = new HashMap<>();
    private final Map<Integer, PorterDuffColorFilter> tints = new HashMap<>();
    private Bitmap glowSprite;
    private Paint shadeLow, shadeClock;

    // Set by draw(), read by act(): the hills animals stand on, and November's candle flames.
    private Ridge[] r;
    private float[] candles = new float[0];

    SeasonScene(int month) {
        this.month = ((month % 12) + 12) % 12;
        this.accent = ACCENTS[this.month];
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeCap(Paint.Cap.ROUND);
    }

    private static final int[] ACCENTS = {0xFFFFC46B, 0xFFFF8FAB, 0xFFF7A8C4, 0xFF8FD3FF, 0xFFFFD23F, 0xFFFFA06B,
            0xFFFFB347, 0xFFA9C4FF, 0xFF4997D0, 0xFFFF8A2A, 0xFFFF9A1F, 0xFFFFD27A};

    // ---- random numbers: mulberry32, the same generator as the design mockup, so scenes match it ----------------

    static final class Rng {
        private int a;

        Rng(int seed) {
            a = seed;
        }

        float next() {
            a += 0x6D2B79F5;
            int t = (a ^ (a >>> 15)) * (1 | a);
            t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
            return (float) (((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0);
        }
    }

    /** A hill line: y at any x, from three sines with seeded phases. */
    static final class Ridge {
        final float y, a, f, p0, p1, p2;
        final int color, top;

        Ridge(float y, float a, int color, int top, int seed, float f) {
            this.y = y;
            this.a = a;
            this.color = color;
            this.top = top;
            this.f = f;
            Rng rng = new Rng(seed);
            p0 = rng.next() * 6;
            p1 = rng.next() * 6;
            p2 = rng.next() * 6;
        }

        float at(float x) {
            double u = x / W * TAU * f;
            double n = .5 * Math.sin(u + p0) + .3 * Math.sin(u * 2.3 + p1) + .2 * Math.sin(u * 5.1 + p2)
                    + .05 * Math.sin(x * .08 + p0);
            return (float) (y * H - a * H * (n * .5 + .5));
        }
    }

    private static float sin(double v) {
        return (float) Math.sin(v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int alpha(int color, float a) {
        int al = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (color & 0x00FFFFFF);
    }

    private void fill(int color) {
        p.setShader(null);
        p.setColor(color);
    }

    // ---- landscape building blocks -----------------------------------------------------------------------------

    private void sky(Canvas c, int[] cols, float[] pos) {
        p.setShader(new LinearGradient(0, 0, 0, H, cols, pos, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
    }

    private void stars(Canvas c, Rng R, int n, float maxY, float a) {
        for (int i = 0; i < n; i++) {
            float y = (float) Math.pow(R.next(), 1.6) * H * maxY;
            float al = a * (.25f + R.next() * .75f) * (1 - y / (H * maxY) * .6f);
            float rad = R.next() < .06f ? 1.8f : R.next() * 1.1f + .4f;
            fill(alpha(0xFFFFFFFF, al));
            c.drawCircle(R.next() * W, y, rad, p);
        }
    }

    private void milky(Canvas c, Rng R) {
        for (int i = 0; i < 1400; i++) {
            float t = R.next();
            float x = t * W;
            float y = lerp(H * .05f, H * .55f, t) + (R.next() + R.next() + R.next() - 1.5f) * 110;
            float al = R.next() * .5f;
            int col = R.next() < .3f ? 0xFFCDD8FF : 0xFFFFFFFF;
            float w = R.next() < .1f ? 2 : 1, h = R.next() < .1f ? 2 : 1;
            fill(alpha(col, al));
            c.drawRect(x, y, x + w, y + h, p);
        }
        c.save();
        c.translate(W / 2, H * .3f);
        c.rotate((float) Math.toDegrees(Math.atan2(H * .5f, W)));
        p.setShader(new LinearGradient(-W * .56f, 0, W * .56f, 0, new int[]{0x008CA0FF, 0x1AAA96FF, 0x008CA0FF},
                null, Shader.TileMode.CLAMP));
        c.drawRect(-W, -90, W, 90, p);
        p.setShader(null);
        c.restore();
    }

    private void aurora(Canvas c, Rng R, int[] cols) {
        for (int b = 0; b < 3; b++) {
            float base = H * (.18f + b * .07f), amp = 30 + R.next() * 40, ph = R.next() * 6;
            int col = cols[b % cols.length];
            for (int x = 0; x < W; x += 3) {
                float y = base + sin(x / 260f + ph) * amp + sin(x / 90f + ph * 2) * 12;
                float h = 90 + sin(x / 140f + ph) * 50;
                float al = .10f + .08f * sin(x / 60f + ph);
                p.setShader(new LinearGradient(0, y - h, 0, y, 0x00000000 | (col & 0xFFFFFF), alpha(col, al),
                        Shader.TileMode.CLAMP));
                c.drawRect(x, y - h, x + 3, y + 8, p);
            }
        }
        p.setShader(null);
    }

    private void orb(Canvas c, float fx, float fy, float rad, int col, int glow, float halo, boolean moon) {
        float x = fx * W, y = fy * H;
        p.setShader(new RadialGradient(x, y, rad * halo, new int[]{glow, glow, glow & 0x00FFFFFF},
                new float[]{0, .6f / halo, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        fill(col);
        c.drawCircle(x, y, rad, p);
        if (moon) {
            fill(0x1F000000);
            float[][] k = {{-.3f, -.2f, .22f}, {.25f, .1f, .16f}, {-.05f, .35f, .12f}, {.35f, -.35f, .09f}};
            for (float[] q : k) c.drawCircle(x + q[0] * rad, y + q[1] * rad, q[2] * rad, p);
        }
    }

    private void fillRidge(Canvas c, Ridge rd) {
        path.reset();
        path.moveTo(0, H);
        for (int x = 0; x <= W; x += 5) path.lineTo(x, rd.at(x));
        path.lineTo(W, H);
        path.close();
        if (rd.top != 0) {
            p.setShader(new LinearGradient(0, (rd.y - rd.a) * H, 0, rd.y * H, new int[]{rd.top, rd.color, rd.color},
                    new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
        } else {
            fill(rd.color);
        }
        c.drawPath(path, p);
        p.setShader(null);
    }

    private Ridge[] ridges(Canvas c, Ridge... rs) {
        for (Ridge rd : rs) fillRidge(c, rd);
        return rs;
    }

    private void water(Canvas c, float y0, int top, int bot, float rx, float rw, int rc) {
        float y = y0 * H;
        p.setShader(new LinearGradient(0, y, 0, H, top, bot, Shader.TileMode.CLAMP));
        c.drawRect(0, y, W, H, p);
        p.setShader(null);
        if (rw > 0) {
            for (int i = 0; i < 40; i++) {
                float yy = y + 6 + i * i * .32f;
                if (yy > H) break;
                float w = rw * (1 - i / 50f) * (.6f + .4f * sin(i * 1.7));
                fill(alpha(rc, .55f * (1 - i / 40f)));
                c.drawRect(rx * W - w / 2, yy, rx * W + w / 2, yy + 2.2f, p);
            }
        }
        s.setColor(0x14FFFFFF);
        s.setStrokeWidth(1.5f);
        for (int i = 0; i < 30; i++) {
            float yy = y + 8 + i * i * .3f;
            if (yy > H) break;
            float x = (i * 397) % W;
            c.drawLine(x, yy, x + 60 + i * 4, yy, s);
        }
    }

    private void rainbow(Canvas c) {
        int[] cols = {0xFFFF5A5A, 0xFFFFA94D, 0xFFFFE066, 0xFF69DB7C, 0xFF4DABF7, 0xFF9775FA};
        s.setStrokeWidth(16);
        s.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i < cols.length; i++) {
            float rad = 620 - i * 16;
            s.setColor(alpha(cols[i], .16f));
            oval.set(W * .58f - rad, H * .95f - rad, W * .58f + rad, H * .95f + rad);
            c.drawArc(oval, 180, 180, false, s);
        }
        s.setStrokeCap(Paint.Cap.ROUND);
    }

    private void fog(Canvas c, float y, int col, float a) {
        for (int i = 0; i < 3; i++) {
            float yy = (y + i * .05f) * H;
            int mid = alpha(col, a);
            p.setShader(new LinearGradient(0, yy - 50, 0, yy + 50, new int[]{0, mid, 0}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, yy - 50, W, yy + 50, p);
        }
        p.setShader(null);
    }

    private void tri(Canvas c, float x1, float y1, float x2, float y2, float x3, float y3) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        c.drawPath(path, p);
    }

    private void pine(Canvas c, float x, float b, float h, int col, int snow) {
        fill(col);
        c.drawRect(x - h * .03f, b - h * .15f, x + h * .03f, b + h * .01f, p);
        for (int i = 0; i < 4; i++) {
            float ty = b - h + i * h * .2f, by = ty + h * .42f, w = h * (.13f + i * .07f);
            fill(col);
            tri(c, x, ty, x - w, by, x + w, by);
            if (snow != 0) {
                fill(snow);
                tri(c, x, ty, x - w * .42f, ty + h * .18f, x + w * .3f, ty + h * .15f);
            }
        }
    }

    private interface Leaf {
        void at(float x, float y);
    }

    private void branch(Canvas c, Rng R, float x, float y, float len, float ang, float w, int d, int col, Leaf leaf) {
        float x2 = x + (float) Math.cos(ang) * len, y2 = y + sin(ang) * len;
        s.setColor(col);
        s.setStrokeWidth(w);
        path.reset();
        path.moveTo(x, y);
        path.quadTo((x + x2) / 2 + (R.next() - .5f) * len * .3f, (y + y2) / 2, x2, y2);
        c.drawPath(path, s);
        if (d > 0) {
            int k = R.next() < .75f ? 2 : 3;
            for (int i = 0; i < k; i++) {
                float nl = len * (.62f + R.next() * .18f);
                float na = ang + (i - (k - 1) / 2f) * (.45f + R.next() * .35f);
                branch(c, R, x2, y2, nl, na, w * .62f, d - 1, col, leaf);
            }
        } else if (leaf != null) {
            leaf.at(x2, y2);
        }
    }

    private void tree(Canvas c, Rng R, float x, float b, float h, int col, Leaf leaf, int depth) {
        branch(c, R, x, b, h * .36f, (float) (-Math.PI / 2) + (R.next() - .5f) * .15f, h * .06f, depth, col, leaf);
    }

    private void blossom(Canvas c, Rng R, float x, float b, float h, int trunk, int pink) {
        List<float[]> pts = new ArrayList<>();
        tree(c, R, x, b, h, trunk, (px, py) -> pts.add(new float[]{px, py}), 5);
        for (float[] q : pts) {
            for (int i = 0; i < 5; i++) {
                float al = .55f + R.next() * .4f;
                int col = R.next() < .25f ? 0xFFFFF0F5 : pink;
                fill(alpha(col, al));
                float cx = q[0] + (R.next() - .5f) * 36, cy = q[1] + (R.next() - .5f) * 30, rad = 6 + R.next() * 10;
                c.drawCircle(cx, cy, rad, p);
            }
        }
    }

    private void ellipse(Canvas c, float cx, float cy, float rx, float ry, float rot, Paint paint) {
        c.save();
        c.translate(cx, cy);
        if (rot != 0) c.rotate((float) Math.toDegrees(rot));
        oval.set(-rx, -ry, rx, ry);
        c.drawOval(oval, paint);
        c.restore();
    }

    private void acacia(Canvas c, float x, float b, float h, int col) {
        s.setColor(col);
        s.setStrokeWidth(h * .05f);
        path.reset();
        path.moveTo(x, b);
        path.quadTo(x + h * .05f, b - h * .45f, x - h * .18f, b - h * .78f);
        path.moveTo(x + h * .02f, b - h * .4f);
        path.quadTo(x + h * .15f, b - h * .6f, x + h * .26f, b - h * .8f);
        c.drawPath(path, s);
        fill(col);
        float[][] e = {{-.3f, -.84f, .34f, .07f}, {.18f, -.86f, .38f, .08f}, {-.02f, -.92f, .3f, .06f}};
        for (float[] q : e) ellipse(c, x + q[0] * h, b + q[1] * h, q[2] * h, q[3] * h, 0, p);
    }

    private void palm(Canvas c, Rng R, float x, float b, float h, int col, float lean) {
        s.setColor(col);
        s.setStrokeWidth(h * .035f);
        float tx = x + lean * h, ty = b - h;
        path.reset();
        path.moveTo(x, b);
        path.quadTo(x + lean * h * .2f, b - h * .6f, tx, ty);
        c.drawPath(path, s);
        s.setStrokeWidth(h * .022f);
        for (int i = 0; i < 8; i++) {
            float a = (float) (-Math.PI + i * Math.PI / 7) + (R.next() - .5f) * .2f, l = h * (.32f + R.next() * .1f);
            float ca = (float) Math.cos(a), sa = sin(a);
            path.reset();
            path.moveTo(tx, ty);
            path.quadTo(tx + ca * l * .6f, ty + sa * l * .6f - h * .06f, tx + ca * l, ty + sa * l * .5f + h * .1f);
            c.drawPath(path, s);
        }
    }

    private void canopy(Canvas c, Rng R, float y, int col, float rmin, float rmax) {
        fill(col);
        c.drawRect(0, y * H, W, H, p);
        for (float x = -40; x < W + 60; x += rmin * .9f) {
            float rad = rmin + R.next() * (rmax - rmin);
            c.drawCircle(x, y * H + (R.next() - .3f) * 30, rad, p);
        }
    }

    private void flowers(Canvas c, Rng R, float y0, int n, int[] pal) {
        for (int i = 0; i < n; i++) {
            float t = (float) Math.pow(R.next(), .7);
            float y = lerp(y0 * H, H, t), rad = 1.5f + t * 6;
            int col = pal[(int) Math.floor(R.next() * pal.length)];
            fill(alpha(col, .55f + t * .45f));
            c.drawCircle(R.next() * W, y, rad, p);
        }
    }

    private float[] candles(Canvas c, Rng R, int n, float y0, float y1) {
        float[] out = new float[n * 2];
        for (int i = 0; i < n; i++) {
            float x = W * (.05f + R.next() * .9f), y = lerp(y0, y1, R.next()) * H, h = 18 + R.next() * 16;
            p.setShader(new RadialGradient(x, y - h - 6, 46, 0xBFFFC86E, 0x00FF8C28, Shader.TileMode.CLAMP));
            c.drawRect(x - 50, y - h - 56, x + 50, y - h + 44, p);
            fill(0xFFF3E6CF);
            c.drawRect(x - 4, y - h, x + 4, y, p);
            fill(0xFFFFD27A);
            ellipse(c, x, y - h - 7, 3.2f, 7, 0, p);
            out[i * 2] = x;
            out[i * 2 + 1] = y - h - 7;
        }
        return out;
    }

    private void cabin(Canvas c, float x, float b, float sz) {
        fill(0xFF0B1428);
        c.drawRect(x - sz * .5f, b - sz * .55f, x + sz * .5f, b, p);
        tri(c, x - sz * .62f, b - sz * .52f, x, b - sz * .98f, x + sz * .62f, b - sz * .52f);
        fill(0xFFDFE8F8);
        path.reset();
        path.moveTo(x - sz * .64f, b - sz * .5f);
        path.lineTo(x, b - sz * .99f);
        path.lineTo(x + sz * .64f, b - sz * .5f);
        path.lineTo(x + sz * .56f, b - sz * .5f);
        path.lineTo(x, b - sz * .9f);
        path.lineTo(x - sz * .56f, b - sz * .5f);
        path.close();
        c.drawPath(path, p);
        float wx = x - sz * .2f, wy = b - sz * .35f;
        p.setShader(new RadialGradient(wx, wy, sz * .9f, 0x8CFFBE64, 0x00FFA03C, Shader.TileMode.CLAMP));
        c.drawRect(wx - sz, wy - sz, wx + sz, wy + sz, p);
        fill(0xFFFFC56B);
        c.drawRect(wx - sz * .1f, wy - sz * .1f, wx + sz * .1f, wy + sz * .08f, p);
        c.drawRect(x + sz * .12f, wy - sz * .1f, x + sz * .32f, wy + sz * .08f, p);
    }

    private void perch(Canvas c, boolean fromRight, float y, float len, int col) {
        c.save();
        if (fromRight) {
            c.translate(W, 0);
            c.scale(-1, 1);
        }
        s.setColor(col);
        s.setStrokeWidth(60);
        path.reset();
        path.moveTo(-10, H);
        path.quadTo(40, H * .5f, 20, -10);
        c.drawPath(path, s);
        s.setStrokeWidth(16);
        path.reset();
        path.moveTo(30, y * H + 20);
        path.quadTo(len * .5f, y * H - 10, len, y * H + 6);
        c.drawPath(path, s);
        s.setStrokeWidth(7);
        c.drawLine(len * .7f, y * H + 2, len * .85f, y * H - 40, s);
        c.restore();
    }

    private void reeds(Canvas c, Rng R, float x0, float x1, float b, int col) {
        s.setColor(col);
        s.setStrokeWidth(3);
        fill(col);
        for (int i = 0; i < 14; i++) {
            float x = lerp(x0, x1, R.next()) * W, h = 60 + R.next() * 90, l = (R.next() - .5f) * 30;
            path.reset();
            path.moveTo(x, b * H);
            path.quadTo(x + l * .3f, b * H - h * .5f, x + l, b * H - h);
            c.drawPath(path, s);
            if (R.next() < .5f) ellipse(c, x + l * .92f, b * H - h * .85f, 4, 14, l / 200, p);
        }
    }

    private void giraffe(Canvas c, float x, float b, float h, int col, boolean flip, float neck) {
        c.save();
        c.translate(x, b);
        if (flip) c.scale(-1, 1);
        fill(col);
        s.setColor(col);
        ellipse(c, 0, -h * .47f, h * .21f, h * .1f, -.12f, p);
        s.setStrokeWidth(h * .035f);
        float[][] legs = {{-.16f, -.06f}, {-.1f, -.02f}, {.12f, .04f}, {.17f, .08f}};
        for (float[] l : legs) c.drawLine(l[0] * h, -h * .45f, (l[0] + l[1] * .3f) * h, 0, s);
        s.setStrokeWidth(h * .012f);
        path.reset();
        path.moveTo(-h * .2f, -h * .5f);
        path.quadTo(-h * .27f, -h * .4f, -h * .25f, -h * .3f);
        c.drawPath(path, s);
        c.save();
        c.rotate((float) Math.toDegrees(neck), h * .17f, -h * .5f);
        path.reset();
        path.moveTo(h * .12f, -h * .55f);
        path.lineTo(h * .36f, -h);
        path.lineTo(h * .42f, -h * .98f);
        path.lineTo(h * .22f, -h * .46f);
        path.close();
        c.drawPath(path, p);
        ellipse(c, h * .45f, -h, h * .08f, h * .035f, .35f, p);
        c.drawLine(h * .37f, -h * 1.02f, h * .36f, -h * 1.08f, s);
        c.drawLine(h * .4f, -h * 1.02f, h * .4f, -h * 1.08f, s);
        c.restore();
        c.restore();
    }

    // ---- September's branch and quetzals ----------------------------------------------------------------------

    private static final float[][] BR = {{W, H * .5f}, {W * .86f, H * .44f}, {W * .66f, H * .5f}};

    private static float bzX(float u) {
        return (1 - u) * (1 - u) * BR[0][0] + 2 * (1 - u) * u * BR[1][0] + u * u * BR[2][0];
    }

    private static float bzY(float u) {
        return (1 - u) * (1 - u) * BR[0][1] + 2 * (1 - u) * u * BR[1][1] + u * u * BR[2][1];
    }

    /** A resplendent quetzal standing with its feet at (x, y), facing left unless flipped; the male has the long tail. */
    private void quetzal(Canvas c, float x, float y, float sz, boolean male, boolean flip, float t, float ph) {
        c.save();
        c.translate(x, y);
        if (flip) c.scale(-1, 1);
        float sw = sin(t * .9f + ph) * sz * .09f, bob = sin(t * 1.3f + ph) * sz * .012f;
        s.setColor(0xFF4A3420);
        s.setStrokeWidth(sz * .028f);
        c.drawLine(-sz * .03f, -sz * .09f, -sz * .05f, 0, s);
        c.drawLine(sz * .05f, -sz * .09f, sz * .05f, 0, s);
        c.translate(0, bob);
        if (male) {
            float[][] tails = {{0, 1.75f}, {sz * .05f, 1.55f}};
            for (int i = 0; i < 2; i++) {
                float o = tails[i][0], l = tails[i][1];
                s.setShader(new LinearGradient(0, -sz * .1f, 0, sz * l, 0xFF1AA35C, 0xFF0B5A36, Shader.TileMode.CLAMP));
                s.setStrokeWidth(sz * .06f);
                path.reset();
                path.moveTo(sz * .12f + o, -sz * .14f);
                path.cubicTo(sz * .2f + o, sz * .4f, sz * .18f + o + sw * .6f, sz * (l - .5f),
                        sz * .16f + o + sw * (1.2f + i * .3f), sz * l);
                c.drawPath(path, s);
            }
            s.setShader(null);
        }
        fill(0xFFE9EFE6);
        ellipse(c, sz * .12f, -sz * .02f, sz * .05f, sz * (male ? .13f : .2f), -.25f, p);
        if (!male) {
            fill(0xFF146B40);
            ellipse(c, sz * .14f + sw * .1f, sz * .06f, sz * .04f, sz * .22f, -.2f, p);
        }
        p.setShader(new LinearGradient(0, -sz * .65f, 0, -sz * .05f, male ? 0xFF35D07F : 0xFF2A9A62,
                male ? 0xFF11804A : 0xFF0E5F39, Shader.TileMode.CLAMP));
        ellipse(c, 0, -sz * .35f, sz * .19f, sz * .3f, .25f, p);
        fill(male ? 0xFFD4152F : 0xFF9C978E);
        ellipse(c, -sz * .06f, -sz * .24f, sz * .12f, sz * .17f, .2f, p);
        if (!male) {
            fill(0xFFC42135);
            ellipse(c, -sz * .03f, -sz * .12f, sz * .08f, sz * .08f, 0, p);
        }
        fill(male ? 0xFF0D7040 : 0xFF0C5534);
        ellipse(c, sz * .07f, -sz * .33f, sz * .14f, sz * .28f, .35f, p);
        s.setColor(0x7396F0AA);
        s.setStrokeWidth(sz * .012f);
        for (int i = 0; i < 4; i++) {
            path.reset();
            path.moveTo(-sz * .02f + i * sz * .04f, -sz * .5f);
            path.quadTo(-sz * .06f + i * sz * .05f, -sz * .3f, -sz * .03f + i * sz * .06f, -sz * .12f);
            c.drawPath(path, s);
        }
        c.save();
        c.translate(-sz * .07f, -sz * .64f);
        c.rotate((float) Math.toDegrees(.2f * Math.pow(sin(t * .6f + ph), 3)));
        fill(male ? 0xFF2FC777 : 0xFF24905A);
        c.drawCircle(0, 0, sz * .14f, p);
        if (male) {
            path.reset();
            path.moveTo(-sz * .12f, -sz * .04f);
            path.quadTo(-sz * .02f, -sz * .32f, sz * .15f, -sz * .05f);
            path.close();
            c.drawPath(path, p);
        }
        fill(0xFFF3C22F);
        tri(c, -sz * .12f, -sz * .01f, -sz * .22f, sz * .03f, -sz * .12f, sz * .06f);
        fill(0xFF08130C);
        c.drawCircle(-sz * .05f, -sz * .02f, sz * .028f, p);
        fill(0xFFFFFFFF);
        c.drawCircle(-sz * .058f, -sz * .03f, sz * .009f, p);
        c.restore();
        c.restore();
    }

    // ---- sprites --------------------------------------------------------------------------------------------

    /** The emoji at 160 px in a 240 px bitmap, painted in {@code color}, or in its own colours when color is 0. */
    Bitmap sprite(String emoji, int color) {
        String key = emoji + '|' + color;
        Bitmap b = sprites.get(key);
        if (b != null) return b;
        b = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(160);
        tp.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = tp.getFontMetrics();
        c.drawText(emoji, 120, 120 - (fm.ascent + fm.descent) / 2, tp);
        if (color != 0) {
            Paint tint = new Paint();
            tint.setColor(color);
            tint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            c.drawRect(0, 0, 240, 240, tint);
        }
        sprites.put(key, b);
        return b;
    }

    /** Draws an animal with its feet at (x, y): {@code sz} is its size, rot in radians around the feet. */
    void animal(Canvas c, String emoji, float x, float y, float sz, int color, boolean flip, float rot, float sx,
                float sy, float a) {
        Bitmap b = sprite(emoji, color);
        float d = sz * 1.5f;
        c.save();
        c.translate(x, y);
        if (rot != 0) c.rotate((float) Math.toDegrees(rot));
        c.scale((flip ? -1 : 1) * sx, sy);
        dst.set(-d / 2, -sz * .5f - d / 2, d / 2, -sz * .5f + d / 2);
        bp.setAlpha(Math.round(255 * a));
        c.drawBitmap(b, null, dst, bp);
        c.restore();
    }

    private void animal(Canvas c, String e, float x, float y, float sz, int color, boolean flip, float rot, float sy) {
        animal(c, e, x, y, sz, color, flip, rot, 1, sy, 1);
    }

    /** A soft round light in {@code color}, from one shared white sprite. */
    void glow(Canvas c, float x, float y, float rad, int color, float a) {
        if (glowSprite == null) {
            glowSprite = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setShader(new RadialGradient(32, 32, 32, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            new Canvas(glowSprite).drawRect(0, 0, 64, 64, g);
        }
        PorterDuffColorFilter f = tints.get(color);
        if (f == null) {
            f = new PorterDuffColorFilter(color | 0xFF000000, PorterDuff.Mode.SRC_IN);
            tints.put(color, f);
        }
        bp.setColorFilter(f);
        bp.setAlpha(Math.round(255 * Math.max(0, Math.min(1, a))));
        dst.set(x - rad, y - rad, x + rad, y + rad);
        c.drawBitmap(glowSprite, null, dst, bp);
        bp.setColorFilter(null);
    }

    private void ripple(Canvas c, float x, float y, float t, float ph, int col) {
        float k = (t * .45f + ph) % 1f;
        s.setColor(alpha(col, 1 - k));
        s.setStrokeWidth(1.5f);
        oval.set(x - (36 + k * 60), y - (5 + k * 8), x + 36 + k * 60, y + 5 + k * 8);
        c.drawOval(oval, s);
    }

    private static float hop(float t, float period, float ph) {
        float u = (((t + ph) % period) + period) % period / period;
        return u < .14f ? sin(u / .14f * Math.PI) : 0;
    }

    private static float graze(float t, float ph) {
        return (float) Math.pow(Math.max(0, sin(t * .35f + ph)), 6);
    }

    private static float breathe(float t, float ph) {
        return 1 + .012f * sin(t * 2 + ph);
    }

    /** The lower half darkened for the dock and text, and a soft shade behind the clock. Drawn every frame. */
    void shade(Canvas c) {
        if (shadeLow == null) {
            shadeLow = new Paint();
            shadeLow.setShader(new LinearGradient(0, H * .5f, 0, H, 0x00000000, 0x9E000000, Shader.TileMode.CLAMP));
            shadeClock = new Paint();
            shadeClock.setShader(new RadialGradient(W * .12f, H * .14f, W * .35f, 0x47000000, 0x00000000,
                    Shader.TileMode.CLAMP));
        }
        c.drawRect(0, H * .5f, W, H, shadeLow);
        c.drawRect(0, 0, W, H * .6f, shadeClock);
    }

    // ---- the twelve months -------------------------------------------------------------------------------------

    /** The still landscape. Runs once per month, off the main thread. */
    void draw(Canvas c) {
        Rng R = new Rng(month * 97 + 5);
        switch (month) {
            case 0:
                sky(c, new int[]{0xFF1B2350, 0xFF6B4A7A, 0xFFF2A57A, 0xFFFFD9A8}, new float[]{0, .42f, .68f, 1});
                stars(c, R, 50, .3f, .6f);
                orb(c, .66f, .62f, 55, 0xFFFFF0CF, 0x80FFC88C, 4.5f, false);
                r = ridges(c, new Ridge(.66f, .32f, 0xFF4E4C7C, 0xFFF7E7EF, 11, 1.1f),
                        new Ridge(.75f, .15f, 0xFF2D2B55, 0xFFB9AFD6, 12, 1.8f),
                        new Ridge(.87f, .07f, 0xFF141329, 0, 13, 2.6f));
                for (int i = 0; i < 30; i++) {
                    float x = R.next() * W;
                    pine(c, x, r[2].at(x) + 8, 50 + R.next() * 70, 0xFF0C0B1C, 0xFFCFC6E6);
                }
                break;
            case 1:
                sky(c, new int[]{0xFF2A1846, 0xFFA2457A, 0xFFF39A8C, 0xFFFFD1A8}, new float[]{0, .4f, .56f, 1});
                orb(c, .5f, .5f, 70, 0xFFFFE0BD, 0x8CFFAA8C, 4.5f, false);
                r = ridges(c, new Ridge(.53f, .12f, 0xFF6E3C6E, 0, 21, 1.3f), new Ridge(.56f, .06f, 0xFF3E2148, 0, 22, 2));
                water(c, .56f, 0xFFC0708C, 0xFF1A0D24, .5f, 150, 0xFFFFD9B8);
                reeds(c, R, .88f, 1, .66f, 0xFF14091C);
                break;
            case 2:
                sky(c, new int[]{0xFF355384, 0xFF9CB8D8, 0xFFF2DCE6}, new float[]{0, .55f, 1});
                orb(c, .62f, .24f, 38, 0xFFFFF7E6, 0x80FFF5DC, 6, false);
                r = ridges(c, new Ridge(.7f, .12f, 0xFF5F8A6A, 0, 31, 1), new Ridge(.8f, .08f, 0xFF3D6B4F, 0, 32, 1.6f),
                        new Ridge(.92f, .05f, 0xFF1F3A2C, 0, 33, 2.2f));
                blossom(c, R, W * .12f, r[2].at(W * .12f) + 10, 520, 0xFF2A1F22, 0xFFF6B6CC);
                blossom(c, R, W * .86f, r[2].at(W * .86f) + 10, 460, 0xFF2A1F22, 0xFFF3A6C2);
                break;
            case 3: {
                sky(c, new int[]{0xFF2E4664, 0xFF7D97A8, 0xFFD7E0D0}, new float[]{0, .55f, 1});
                rainbow(c);
                r = ridges(c, new Ridge(.55f, .12f, 0xFF56806A, 0, 41, 1.2f), new Ridge(.6f, .07f, 0xFF355F48, 0, 42, 1.9f));
                Leaf leaf = (px, py) -> {
                    fill(0xFF2C5640);
                    c.drawCircle(px, py, 14, p);
                };
                for (int i = 0; i < 9; i++) {
                    float x = R.next() * W, h = 120 + R.next() * 60;
                    tree(c, R, x, r[1].at(x) + 6, h, 0xFF21402F, leaf, 4);
                }
                water(c, .61f, 0xFF7D97A8, 0xFF122019, 0, 0, 0);
                reeds(c, R, .86f, 1, .66f, 0xFF0D1A12);
                break;
            }
            case 4: {
                sky(c, new int[]{0xFF3D6FA3, 0xFF9CC3D9, 0xFFF5DCA0, 0xFFF7E6B8}, new float[]{0, .5f, .82f, 1});
                orb(c, .82f, .3f, 48, 0xFFFFF4CC, 0x80FFE6A0, 4.5f, false);
                r = ridges(c, new Ridge(.66f, .1f, 0xFF7A9A5A, 0, 51, 1.1f), new Ridge(.75f, .07f, 0xFF557A3E, 0, 52, 1.6f),
                        new Ridge(.86f, .05f, 0xFF2F4A24, 0, 53, 2));
                Leaf leaf = (px, py) -> {
                    fill(0xFF5F8446);
                    c.drawCircle(px, py, 10, p);
                };
                for (int i = 0; i < 6; i++) {
                    float x = R.next() * W, h = 90 + R.next() * 40;
                    tree(c, R, x, r[0].at(x) + 4, h, 0xFF4C6A38, leaf, 3);
                }
                flowers(c, R, .64f, 620, new int[]{0xFFFFD23F, 0xFFFF7AA2, 0xFFFFFFFF, 0xFFB48CFF, 0xFFFF9248});
                break;
            }
            case 5:
                sky(c, new int[]{0xFF14284A, 0xFF3F5F8F, 0xFFF08A5D, 0xFFFFCF8A}, new float[]{0, .36f, .55f, 1});
                orb(c, .42f, .48f, 80, 0xFFFFE1A8, 0x8CFFAA6E, 4.5f, false);
                water(c, .52f, 0xFFE47B58, 0xFF08162B, .42f, 200, 0xFFFFD9A0);
                break;
            case 6:
                sky(c, new int[]{0xFF3A1A3A, 0xFFA8402E, 0xFFF5A342, 0xFFFFD27A}, new float[]{0, .38f, .72f, 1});
                orb(c, .6f, .52f, 150, 0xFFFFCF73, 0x8CFFAA50, 3, false);
                r = ridges(c, new Ridge(.58f, .04f, 0xFF7A3324, 0, 71, 1.4f), new Ridge(.68f, .03f, 0xFF241009, 0, 72, 1));
                acacia(c, W * .16f, r[1].at(W * .16f) + 6, 260, 0xFF1B0B06);
                acacia(c, W * .86f, r[1].at(W * .86f) + 6, 200, 0xFF1B0B06);
                acacia(c, W * .36f, r[0].at(W * .36f) + 2, 70, 0xFF4E2016);
                break;
            case 7:
                sky(c, new int[]{0xFF030614, 0xFF0D1638, 0xFF1D2A55}, new float[]{0, .6f, 1});
                stars(c, R, 420, .85f, 1);
                milky(c, R);
                // A low moon behind the owl's branch, so the owl reads as a silhouette against it.
                orb(c, .41f, .47f, 72, 0xFFE6EBFF, 0x4DAFC3FF, 5, true);
                r = ridges(c, new Ridge(.76f, .1f, 0xFF0A1024, 0, 81, 1.3f), new Ridge(.88f, .05f, 0xFF05081A, 0, 82, 2));
                perch(c, false, .56f, 720, 0xFF02040D);
                break;
            case 8:
                sky(c, new int[]{0xFF0B1838, 0xFF1F4565, 0xFF4F7F72}, new float[]{0, .5f, 1});
                stars(c, R, 60, .4f, .7f);
                canopy(c, R, .62f, 0xFF1B4A3A, 50, 90);
                fog(c, .62f, 0x80BEE6DC, .35f);
                canopy(c, R, .74f, 0xFF0F3326, 60, 110);
                canopy(c, R, .86f, 0xFF06190F, 70, 120);
                palm(c, R, W * .06f, H, 520, 0xFF030C07, .12f);
                s.setColor(0xFF1D140C);
                for (int k = 0; k < 3; k++) {
                    s.setStrokeWidth(22 - k * 6);
                    path.reset();
                    path.moveTo(BR[0][0], BR[0][1]);
                    path.quadTo(BR[1][0], BR[1][1], BR[2][0] + k * 40, BR[2][1] - k * 2);
                    c.drawPath(path, s);
                }
                s.setColor(0x993C6E46);
                s.setStrokeWidth(2);
                for (int i = 0; i < 14; i++) {
                    float u = .05f + R.next() * .85f, l = 20 + R.next() * 60, px = bzX(u), py = bzY(u);
                    path.reset();
                    path.moveTo(px, py + 6);
                    path.quadTo(px + 4, py + l * .5f, px - 2, py + l);
                    c.drawPath(path, s);
                }
                fill(0xFF0F3A22);
                for (int i = 0; i < 9; i++) {
                    float u = .88f + R.next() * .12f;
                    c.save();
                    c.translate(bzX(u), bzY(u));
                    c.rotate((float) Math.toDegrees(-1.2f + R.next() * 2.4f));
                    oval.set(-2, -6, 34, 6);
                    c.drawOval(oval, p);
                    c.restore();
                }
                break;
            case 9:
                sky(c, new int[]{0xFF0B0614, 0xFF2A1238, 0xFF5A2A3A, 0xFF8A4030}, new float[]{0, .5f, .82f, 1});
                stars(c, R, 90, .5f, 1);
                orb(c, .7f, .3f, 95, 0xFFFFB35C, 0x80FF8C32, 4.5f, true);
                r = ridges(c, new Ridge(.64f, .1f, 0xFF1A0D24, 0, 101, 1.2f), new Ridge(.8f, .06f, 0xFF0D0716, 0, 102, 1.8f));
                fog(c, .6f, 0x73BE96E6, .35f);
                tree(c, R, W * .12f, r[1].at(W * .12f) + 10, 640, 0xFF07030C, null, 6);
                tree(c, R, W * .4f, r[0].at(W * .4f) + 6, 200, 0xFF120A1C, null, 4);
                perch(c, true, .44f, 430, 0xFF07030C);
                break;
            case 10:
                sky(c, new int[]{0xFF1A0F3A, 0xFF5A2C6A, 0xFFE0704A, 0xFFFFB35C}, new float[]{0, .5f, .78f, 1});
                stars(c, R, 50, .35f, 1);
                r = ridges(c, new Ridge(.54f, .08f, 0xFF3A1E4A, 0, 111, 1.2f), new Ridge(.6f, .05f, 0xFF1E0F2A, 0, 112, 1.7f));
                flowers(c, R, .6f, 1300, new int[]{0xFFFF9A1F, 0xFFFFB000, 0xFFFF7A00, 0xFFFFC84A});
                candles = candles(c, R, 12, .62f, .7f);
                break;
            default:
                sky(c, new int[]{0xFF050A1E, 0xFF142A52, 0xFF3A5A8A}, new float[]{0, .55f, 1});
                stars(c, R, 220, .6f, 1);
                aurora(c, R, new int[]{0xE65AFFB4, 0xCC78C8FF, 0xB3A078FF});
                r = ridges(c, new Ridge(.64f, .22f, 0xFF4F6C9C, 0xFFEEF4FF, 121, 1.2f),
                        new Ridge(.75f, .1f, 0xFF263F6A, 0xFFC8D8F0, 122, 1.8f),
                        new Ridge(.88f, .04f, 0xFF18284A, 0xFF9FB4D6, 123, 2.4f));
                for (int i = 0; i < 34; i++) {
                    float x = R.next() * W;
                    if (Math.abs(x - W * .72f) < 90 || Math.abs(x - W * .52f) < 110) continue;
                    float b = r[1].at(x) + 20 + R.next() * 40, h = 70 + R.next() * 110;
                    pine(c, x, b, h, 0xFF0A1530, 0xFFDFE8F8);
                }
                cabin(c, W * .72f, r[1].at(W * .72f) + 14, 130);
                break;
        }
    }

    /** The animals at time t in seconds; t = 0 is the still pose. Main thread, after draw() has finished. */
    void act(Canvas c, float t) {
        switch (month) {
            case 0:
                animal(c, EAGLE, W * .3f + sin(t * .25f) * 70, H * .36f + sin(t * .5f) * 14, 80, 0xFF251B36, true,
                        (float) Math.cos(t * .25f) * .08f, 1);
                animal(c, EAGLE, W * .4f + sin(t * .25f + 2) * 50, H * .27f + sin(t * .45f + 1) * 10, 46, 0xFF3A2A4C, true,
                        (float) Math.cos(t * .25f + 2) * .08f, 1);
                break;
            case 1: {
                float dx = sin(t * .25f) * 14;
                for (int i = 0; i < 2; i++) {
                    float x = W * (i == 0 ? .58f : .7f) + dx * (i == 0 ? 1 : -1);
                    float y = H * .67f + sin(t * 1.2f + i * 1.3f) * 3, rot = sin(t * 1.2f + i * 1.3f) * .02f;
                    ripple(c, x, H * .672f, t, i * .5f, 0x4DFFE6EB);
                    animal(c, SWAN, x, y, 120, 0xFFFBEEF3, i == 0, rot, 1);
                    c.save();
                    c.translate(0, H * 1.344f);
                    c.scale(1, -1);
                    animal(c, SWAN, x, y, 120, 0xFFFBEEF3, i == 0, rot, 1, 1, .18f);
                    c.restore();
                }
                break;
            }
            case 2: {
                float[][] rb = {{.56f, 64, 0, 0, 34}, {.63f, 46, 1, 1.7f, 38}};
                for (float[] q : rb) {
                    boolean fl = q[2] == 1;
                    float x = W * q[0] + sin(t * .12f + q[3]) * 12, h = hop(t, 3.4f, q[3]);
                    animal(c, RABBIT, x, r[0].at(x) + q[4] - h * q[1] * .45f, q[1], 0xFF24402E, fl,
                            (fl ? 1 : -1) * h * .15f, breathe(t, q[3]));
                }
                break;
            }
            case 3: {
                float[][] dk = {{.58f, 56}, {.65f, 42}, {.7f, 34}};
                for (int i = 0; i < 3; i++) {
                    float x = W * dk[i][0] + sin(t * .12f + i * .8f) * 40, y = H * (.67f + i * .004f) + sin(t * 1.6f + i) * 2.2f;
                    ripple(c, x, y + 2, t, i * .33f, 0x4DE6F5FF);
                    animal(c, DUCK, x, y, dk[i][1], 0xFF13211A, false, sin(t * 1.6f + i) * .05f, 1);
                }
                break;
            }
            case 4: {
                float x1 = W * .6f, x2 = W * .67f + sin(t * .15f) * 16, h = hop(t, 5, 2.5f);
                animal(c, DEER, x1, r[0].at(x1) + 50, 120, 0xFF2A3D1C, false, -graze(t, 0) * .12f, breathe(t, 0));
                animal(c, DEER, x2, r[0].at(x2) + 48 - h * 14, 70, 0xFF2A3D1C, true, graze(t, 1.4f) * .12f, breathe(t, 1));
                break;
            }
            case 5: {
                float wy = H * .62f + sin(t * .45f) * 14;
                animal(c, WHALE, W * .64f, wy, 170, 0xFF0B1526, false, sin(t * .45f) * .05f, 1);
                for (int i = 0; i < 16; i++) {
                    float k = (t * .6f + i * .137f) % 1f;
                    fill(alpha(0xFFFFFFFF, .6f * (1 - k)));
                    c.drawCircle(W * .64f + sin(i * 12.9f) * 110, wy - 10 - k * 50 + (float) Math.cos(i * 7.3f) * 10,
                            2 + (i * 5) % 4, p);
                }
                for (int i = 0; i < 2; i++) {
                    float u = ((t + i * 2.2f) % 4.5f) / 4.5f, bx = W * (.76f + i * .07f);
                    if (u >= .4f) {
                        if (u < .48f) ripple(c, bx + 140, H * .6f, u * 20, 0, 0x66FFFFFF);
                        continue;
                    }
                    float v = u / .4f;
                    animal(c, DOLPHIN, bx + v * 140, H * .6f - sin(v * Math.PI) * 90, 64 - i * 8, 0xFF0B1526, true,
                            lerp(-.7f, .7f, v), 1);
                }
                break;
            }
            case 6: {
                giraffe(c, W * .52f, r[1].at(W * .52f) + 6, 230, 0xFF1B0B06, false, sin(t * .5f) * .07f);
                giraffe(c, W * .6f, r[1].at(W * .6f) + 6, 185, 0xFF1B0B06, true, sin(t * .5f + 2) * .07f);
                float ex = W * .72f + sin(t * .08f) * 30;
                animal(c, ELEPHANT, ex + W * .03f, r[1].at(ex + W * .03f) + 40, 130, 0xFF1B0B06, false,
                        sin(t * .9f) * .015f, breathe(t, 0));
                animal(c, ELEPHANT, ex - W * .03f, r[1].at(ex - W * .03f) + 36, 76, 0xFF1B0B06, false,
                        sin(t * .9f + 1) * .02f, 1);
                break;
            }
            case 7:
                animal(c, OWL, W * .39f, H * .565f, 100, 0xFF02040D, false, (float) (.09 * Math.pow(sin(t * .5f), 3)),
                        breathe(t, 0));
                break;
            case 8:
                quetzal(c, bzX(.3f), bzY(.3f) - 7, 150, true, false, t, 0);
                quetzal(c, bzX(.68f), bzY(.68f) - 7, 122, false, true, t, 1.7f);
                break;
            case 9: {
                animal(c, OWL, W * .8f, H * .445f, 92, 0xFF07030C, true, (float) (-.09 * Math.pow(sin(t * .5f + 1), 3)),
                        breathe(t, 0));
                animal(c, CAT, W * .52f, r[0].at(W * .52f) + 26, 80, 0xFF07030C, false, 0, breathe(t, 2));
                float[][] pk = {{.59f, 54}, {.64f, 40}, {.7f, 48}};
                for (int i = 0; i < 3; i++) {
                    float x = W * pk[i][0], y = r[0].at(x) + 30, sz = pk[i][1];
                    glow(c, x, y - sz * .45f, sz * 1.4f, 0xFF9628, .25f + .15f * sin(t * 7 + i * 2) * sin(t * 3.1f + i));
                    animal(c, PUMPKIN, x, y, sz, 0, false, 0, 1);
                }
                break;
            }
            case 10:
                for (int i = 0; i + 1 < candles.length; i += 2) {
                    int k = i / 2;
                    glow(c, candles[i], candles[i + 1], 34, 0xFFC86E, .3f + .25f * sin(t * 9 + k * 3) * sin(t * 4.3f + k));
                }
                break;
            default:
                animal(c, DEER, W * .48f, r[1].at(W * .48f) + 46, 104, 0xFF091226, false, -graze(t, .5f) * .12f,
                        breathe(t, 0));
                animal(c, DEER, W * .56f, r[1].at(W * .56f) + 46, 76, 0xFF091226, true, graze(t, 2.2f) * .12f,
                        breathe(t, 1));
                break;
        }
    }

    /** Frees the cached sprites; the scene can still draw, it rebuilds them. */
    void recycle() {
        for (Bitmap b : sprites.values()) b.recycle();
        sprites.clear();
        if (glowSprite != null) glowSprite.recycle();
        glowSprite = null;
    }
}
