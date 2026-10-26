package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * A transparent full-screen overlay for the countdown day (countdown_party): confetti falls from the top in two
 * bursts, hearts pop up from the bottom and float away, and a line of text springs in at the centre, holds, and
 * fades. Everything finishes within the given duration, then the callback runs on the main thread. Like
 * PetalShowerView it takes no focus, clicks or accessibility, and allocates nothing per frame.
 */
public final class PartyView extends View {

    private static final int[] CONFETTI = {0xFFFF4D6D, 0xFFFFC93C, 0xFF4CC9F0, 0xFF80ED99, 0xFFB388EB, 0xFFFFFFFF, 0xFFFF8FAB};
    private static final int[] HEARTS = {0xFFFF4D6D, 0xFFFF758F, 0xFFFF8FAB, 0xFFE5383B, 0xFFFFB3C6};
    private static final double TAU = 2 * Math.PI;

    private final float density;
    private final Random rnd = new Random();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path heart = heartShape();
    private final RectF box = new RectF();

    private int confetti, hearts;
    private float[] cX = new float[0], cDelay = new float[0], cLife = new float[0], cW = new float[0], cH = new float[0],
            cSway = new float[0], cPhase = new float[0], cSpin = new float[0], cFlip = new float[0];
    private int[] cColor = new int[0];
    private float[] hX = new float[0], hDelay = new float[0], hLife = new float[0], hSize = new float[0],
            hSway = new float[0], hPhase = new float[0], hRise = new float[0];
    private int[] hColor = new int[0];

    private String message = "";
    private boolean playing;
    private long start, duration;
    private Runnable onDone;
    private final Runnable finish = this::finish;

    public PartyView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(44 * context.getResources().getDisplayMetrics().scaledDensity);
        text.setShadowLayer(8 * density, 0, 2 * density, 0x99000000);
        pill.setColor(0x8C000000);
    }

    /** Plays the party with {@code message} at the centre for {@code durationMs}, then runs {@code done}. */
    public void play(String message, long durationMs, Runnable done) {
        if (playing) {
            removeCallbacks(finish);
            finish();
        }
        this.message = message == null ? "" : message;
        duration = Math.max(1, durationMs);
        onDone = done;
        confetti = 140;
        hearts = 36;
        ensureCapacity();
        for (int i = 0; i < confetti; i++) {
            cX[i] = rnd.nextFloat();
            cDelay[i] = (i % 2 == 0 ? 0f : 0.22f) + rnd.nextFloat() * 0.12f;   // two bursts
            cLife[i] = 0.35f + rnd.nextFloat() * 0.25f;
            cW[i] = 6f + rnd.nextFloat() * 6f;
            cH[i] = 3f + rnd.nextFloat() * 4f;
            cSway[i] = 8f + rnd.nextFloat() * 30f;
            cPhase[i] = rnd.nextFloat() * (float) TAU;
            cSpin[i] = (90f + rnd.nextFloat() * 300f) * (rnd.nextBoolean() ? 1f : -1f);
            cFlip[i] = 1f + rnd.nextFloat() * 2.5f;
            cColor[i] = CONFETTI[rnd.nextInt(CONFETTI.length)];
        }
        for (int i = 0; i < hearts; i++) {
            hX[i] = 0.05f + 0.9f * rnd.nextFloat();
            hDelay[i] = rnd.nextFloat() * 0.6f;
            hLife[i] = 0.25f + rnd.nextFloat() * 0.15f;
            hSize[i] = 22f + rnd.nextFloat() * 26f;
            hSway[i] = 10f + rnd.nextFloat() * 20f;
            hPhase[i] = rnd.nextFloat() * (float) TAU;
            hRise[i] = 0.45f + rnd.nextFloat() * 0.4f;
            hColor[i] = HEARTS[rnd.nextInt(HEARTS.length)];
        }
        playing = true;
        start = SystemClock.uptimeMillis();
        postDelayed(finish, duration);
        postInvalidateOnAnimation();
    }

    private void finish() {
        if (!playing) return;
        playing = false;
        invalidate();
        Runnable done = onDone;
        onDone = null;
        if (done != null) done.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (playing) {
            removeCallbacks(finish);
            finish();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!playing) return;
        long now = SystemClock.uptimeMillis();
        float t = (now - start) / (float) duration;
        float secs = (now - start) / 1000f;
        float w = getWidth(), h = getHeight();

        for (int i = 0; i < hearts; i++) {
            float p = (t - hDelay[i]) / hLife[i];
            if (p <= 0f || p >= 1f) continue;
            float pop = p < 0.12f ? overshoot(p / 0.12f) : 1f;   // springs in, then floats
            float s = hSize[i] * density * pop;
            float x = hX[i] * w + hSway[i] * density * (float) Math.sin(TAU * 0.6 * secs + hPhase[i]);
            float y = h * (1.02f - hRise[i] * p);
            fill.setColor(hColor[i]);
            fill.setAlpha(p > 0.7f ? (int) (255 * (1f - (p - 0.7f) / 0.3f)) : 255);
            c.save();
            c.translate(x, y);
            c.scale(s, s);
            c.drawPath(heart, fill);
            c.restore();
        }

        for (int i = 0; i < confetti; i++) {
            float p = (t - cDelay[i]) / cLife[i];
            if (p <= 0f || p >= 1f) continue;
            float x = cX[i] * w + cSway[i] * density * (float) Math.sin(TAU * 0.8 * secs + cPhase[i]);
            float y = -20 * density + (h + 40 * density) * p;
            float flip = (float) Math.cos(TAU * cFlip[i] * secs + cPhase[i]);
            fill.setColor(cColor[i]);
            fill.setAlpha(p > 0.8f ? (int) (255 * (1f - (p - 0.8f) / 0.2f)) : 255);
            float hw = cW[i] * density / 2f, hh = cH[i] * density / 2f;
            c.save();
            c.translate(x, y);
            c.rotate(cSpin[i] * secs);
            c.scale(Math.max(0.1f, Math.abs(flip)), 1f);
            c.drawRect(-hw, -hh, hw, hh, fill);
            c.restore();
        }

        if (!message.isEmpty()) {
            // springs in over 0.7 s, holds, fades over the last 12 %
            float in = Math.min(1f, (now - start) / 700f);
            float scale = overshoot(in);
            int a = t > 0.88f ? (int) (255 * Math.max(0f, 1f - (t - 0.88f) / 0.12f)) : (int) (255 * Math.min(1f, in * 2f));
            float tw = text.measureText(message);
            Paint.FontMetrics fm = text.getFontMetrics();
            float padX = 36 * density, padY = 18 * density;
            float cx = w / 2f, cy = h * 0.46f;
            box.set(cx - tw / 2f - padX, cy + fm.ascent - padY, cx + tw / 2f + padX, cy + fm.descent + padY);
            c.save();
            c.scale(scale, scale, cx, cy);
            pill.setAlpha(a * 0x8C / 255);
            float r = box.height() / 2f;
            c.drawRoundRect(box, r, r, pill);
            text.setAlpha(a);
            c.drawText(message, cx, cy, text);
            c.restore();
        }
        postInvalidateOnAnimation();
    }

    /** 0 to 1 with a small spring past 1 near the end, as the launcher's focus overshoot does. */
    private static float overshoot(float x) {
        float k = 1.7f;
        float u = x - 1f;
        return u * u * ((k + 1f) * u + k) + 1f;
    }

    private void ensureCapacity() {
        if (cX.length < confetti) {
            cX = new float[confetti];
            cDelay = new float[confetti];
            cLife = new float[confetti];
            cW = new float[confetti];
            cH = new float[confetti];
            cSway = new float[confetti];
            cPhase = new float[confetti];
            cSpin = new float[confetti];
            cFlip = new float[confetti];
            cColor = new int[confetti];
        }
        if (hX.length < hearts) {
            hX = new float[hearts];
            hDelay = new float[hearts];
            hLife = new float[hearts];
            hSize = new float[hearts];
            hSway = new float[hearts];
            hPhase = new float[hearts];
            hRise = new float[hearts];
            hColor = new int[hearts];
        }
    }

    /** A heart 1 unit wide around the origin. */
    private static Path heartShape() {
        Path p = new Path();
        p.moveTo(0f, 0.35f);
        p.cubicTo(-0.55f, 0f, -0.5f, -0.45f, -0.25f, -0.45f);
        p.cubicTo(-0.1f, -0.45f, 0f, -0.35f, 0f, -0.22f);
        p.cubicTo(0f, -0.35f, 0.1f, -0.45f, 0.25f, -0.45f);
        p.cubicTo(0.5f, -0.45f, 0.55f, 0f, 0f, 0.35f);
        p.close();
        return p;
    }
}
