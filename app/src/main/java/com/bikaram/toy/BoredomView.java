package com.bikaram.toy;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.hardware.*;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.*;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import java.text.DateFormat;
import java.util.*;

@SuppressLint("ViewConstructor") // Programmatic view requires a non-null Activity host.
public final class BoredomView extends View
    implements SensorEventListener, Choreographer.FrameCallback {
  interface Host {
    void shareSnapshot(String text);
  }

  private enum Screen {
    HOME,
    GAME,
    SPEED,
    RESULTS,
    RECORDS,
    SETTINGS,
    ACHIEVEMENTS,
    SKINS,
    MODES,
    ABOUT,
    CHALLENGE
  }

  private static final int BG_TOP = Color.rgb(251, 248, 240),
      BG_BOTTOM = Color.rgb(232, 225, 211),
      INK = Color.rgb(29, 29, 27),
      MUTED = Color.rgb(105, 100, 91),
      ACCENT = Color.rgb(246, 195, 61);
  private static final float MAX_CARRY_SECONDS = 15f;
  private final Host host;
  private final GamePreferences prefs;
  private final PhysicsBall left = new PhysicsBall(-.05f), right = new PhysicsBall(.05f);
  private final SpeedMissionEngine missionEngine = new SpeedMissionEngine();
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG),
      text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
  private final RectF rect = new RectF();
  private final Path path = new Path();
  private final Random random = new Random();
  private final List<Particle> particles = new ArrayList<>(96);
  private final SensorManager sensorManager;
  private final Sensor accelerometer;
  private final Vibrator vibrator;
  private ToneGenerator tone;
  private Screen screen = Screen.HOME, previousScreen = Screen.HOME;
  private boolean hostActive, framePosted, sensorRegistered;
  private long lastFrameNanos, lastTapMs, lastSoundMs;
  private float density, scaledDensity, tiltX, shake, toastSeconds;
  private int streak, selectedRecordFilter;
  private String toast = "";
  private SpeedMission mission;
  private int missionIndex,
      completedMissions,
      speedTaps,
      speedBestStreak,
      speedStreak,
      speedLastSide = -1,
      highestDifficulty;
  private float maxCarry, carrySeconds, timeLeft, countdown, successPause, sessionSeconds;
  private GameRecord lastResult;
  private long classicStartedAt;
  private int classicTaps;
  private DailyChallenge activeChallenge;
  private int challengeProgress, challengeLastSide = -1;
  private float challengeTimeLeft;
  private boolean duelActive;
  private float duelTimeLeft;
  private int duelLeft, duelRight;
  private LinearGradient backgroundGradient;
  private RadialGradient skinGradient;
  private String cachedSkin = "";
  private float cachedRadius;

  private static final class Particle {
    float x, y, vx, vy, life;
    int color;
  }

  BoredomView(Context context, Host host) {
    super(context);
    this.host = host;
    prefs = new GamePreferences(context);
    density = getResources().getDisplayMetrics().density;
    scaledDensity = density * getResources().getConfiguration().fontScale;
    sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    vibrator = context.getSystemService(Vibrator.class);
    try {
      tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 28);
    } catch (RuntimeException ignored) {
    }
    text.setTypeface(Typeface.create("sans", Typeface.BOLD));
    setFocusable(true);
  }

  void onHostResume() {
    hostActive = true;
    lastFrameNanos = 0;
    updateSensorRegistration();
    requestFrame();
  }

  void onHostPause() {
    hostActive = false;
    removeFrame();
    unregisterSensor();
    prefs.flush();
  }

  void release() {
    onHostPause();
    if (tone != null) {
      tone.release();
      tone = null;
    }
  }

  boolean navigateBack() {
    if (screen == Screen.HOME) return false;
    if (screen == Screen.GAME) finishClassicRun();
    if (screen == Screen.RESULTS || screen == Screen.GAME || screen == Screen.SPEED)
      screen = Screen.HOME;
    else screen = previousScreen == Screen.GAME ? Screen.GAME : Screen.HOME;
    lastFrameNanos = 0;
    updateSensorRegistration();
    invalidate();
    requestFrame();
    return true;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    backgroundGradient = new LinearGradient(0, 0, 0, h, BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP);
    float radius = Math.min(w * .145f, dp(70)),
        anchorY = Math.max(dp(112), h * .19f),
        rope = Math.min(h * .285f, dp(260));
    left.layout(w * .41f, anchorY, rope, radius);
    right.layout(w * .59f, anchorY + dp(3), rope * 1.015f, radius);
    cachedRadius = 0;
  }

  @Override
  public void doFrame(long nanos) {
    framePosted = false;
    if (!hostActive) return;
    float dt =
        lastFrameNanos == 0 ? 1f / 60f : Math.min(.034f, (nanos - lastFrameNanos) / 1_000_000_000f);
    lastFrameNanos = nanos;
    boolean moving = update(dt);
    invalidate();
    if (moving) requestFrame();
    else lastFrameNanos = 0;
  }

  private boolean update(float dt) {
    boolean gameplay = screen == Screen.GAME || screen == Screen.SPEED;
    if (gameplay) updatePhysics(dt);
    if (screen == Screen.SPEED) updateSpeed(dt);
    if (screen == Screen.GAME && activeChallenge != null && activeChallenge.durationMs > 0) {
      challengeTimeLeft -= dt;
      if (challengeTimeLeft <= 0) {
        activeChallenge = null;
        showToast("Daily challenge timed out", 1.6f);
      }
    }
    if (screen == Screen.GAME && duelActive) {
      duelTimeLeft -= dt;
      if (duelTimeLeft <= 0) finishDuel();
    }
    shake *= Math.pow(.86f, dt * 60f);
    toastSeconds = Math.max(0, toastSeconds - dt);
    for (int i = particles.size() - 1; i >= 0; i--) {
      Particle q = particles.get(i);
      q.vy += dp(290) * dt;
      q.x += q.vx * dt;
      q.y += q.vy * dt;
      q.life -= dt;
      if (q.life <= 0) particles.remove(i);
    }
    boolean moving = gameplay && !(left.isResting() && right.isResting());
    if (moving && Math.abs(left.angularVelocity) + Math.abs(right.angularVelocity) < .024f) {
      left.settle();
      right.settle();
      moving = false;
    }
    return moving
        || screen == Screen.SPEED
        || activeChallenge != null
        || duelActive
        || !particles.isEmpty()
        || toastSeconds > 0
        || shake > .2f;
  }

  private void updatePhysics(float dt) {
    GameModeConfig mode = GameModeConfig.forId(prefs.mode());
    float intensity =
        prefs.reducedMotion()
            ? .72f
            : "CALM".equals(prefs.intensity())
                ? .82f
                : "WILD".equals(prefs.intensity()) ? 1.13f : 1f;
    left.setSoftLimit(mode.motionLimit);
    right.setSoftLimit(mode.motionLimit);
    float horizontal = prefs.deviceGravity() ? tiltX * dp(mode.sensorStrength) : 0;
    left.step(dt, dp(mode.gravity), mode.damping, horizontal);
    right.step(dt, dp(mode.gravity), mode.damping, horizontal);
    resolveCollision(mode.collisionStrength * intensity);
  }

  private void resolveCollision(float strength) {
    float dx = right.x - left.x,
        dy = right.y - left.y,
        d2 = dx * dx + dy * dy,
        min = left.radius + right.radius - dp(7);
    if (d2 >= min * min || d2 < 1) return;
    float overlap = (min - (float) Math.sqrt(d2)) / min, old = left.angularVelocity;
    left.angularVelocity = right.angularVelocity * .70f - overlap * strength;
    right.angularVelocity = old * .70f + overlap * strength;
    if (overlap > .035f) {
      playCollision();
      if (overlap > .10f) haptic(2);
    }
  }

  private void updateSpeed(float dt) {
    sessionSeconds += dt;
    if (countdown > 0) {
      countdown = Math.max(0, countdown - dt);
      return;
    }
    if (successPause > 0) {
      successPause -= dt;
      if (successPause <= 0) beginMission();
      return;
    }
    timeLeft -= dt;
    if (timeLeft <= 0) finishSpeedSession();
  }

  private void requestFrame() {
    if (hostActive && !framePosted) {
      framePosted = true;
      Choreographer.getInstance().postFrameCallback(this);
    }
  }

  private void removeFrame() {
    if (framePosted) Choreographer.getInstance().removeFrameCallback(this);
    framePosted = false;
    lastFrameNanos = 0;
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    paint.setShader(backgroundGradient);
    c.drawRect(0, 0, getWidth(), getHeight(), paint);
    paint.setShader(null);
    boolean shifted = shake > .2f && !prefs.reducedMotion();
    if (shifted) {
      c.save();
      c.translate((random.nextFloat() - .5f) * shake, (random.nextFloat() - .5f) * shake);
    }
    switch (screen) {
      case HOME:
        drawHome(c);
        break;
      case GAME:
        drawGame(c, false);
        break;
      case SPEED:
        drawGame(c, true);
        break;
      case RESULTS:
        drawResults(c);
        break;
      case RECORDS:
        drawRecords(c);
        break;
      case SETTINGS:
        drawSettings(c);
        break;
      case ACHIEVEMENTS:
        drawAchievements(c);
        break;
      case SKINS:
        drawChoices(c, false);
        break;
      case MODES:
        drawChoices(c, true);
        break;
      case ABOUT:
        drawAbout(c);
        break;
      case CHALLENGE:
        drawChallenge(c);
        break;
    }
    drawParticles(c);
    if (toastSeconds > 0) drawToast(c);
    if (shifted) c.restore();
  }

  private void drawHome(Canvas c) {
    label(c, "BIKARAM", getWidth() / 2f, dp(76), 12, MUTED, Paint.Align.CENTER);
    label(c, "بیکارم!", getWidth() / 2f, dp(126), 36, INK, Paint.Align.CENTER);
    label(c, "A premium waste of time", getWidth() / 2f, dp(154), 13, MUTED, Paint.Align.CENTER);
    drawMiniBalls(c, getWidth() / 2f, dp(225));
    button(c, dp(28), dp(292), getWidth() - dp(28), dp(354), "START", true);
    button(c, dp(28), dp(368), getWidth() - dp(28), dp(424), "⚡  SPEED MODE", false);
    String[] rows = {
      "Game Modes", "Challenges", "Records", "Achievements", "Skins", "Settings", "About"
    };
    float y = dp(442), row = Math.min(dp(46), (getHeight() - y - dp(14)) / rows.length);
    for (String name : rows) {
      label(c, name, dp(38), y + row * .62f, 14, INK, Paint.Align.LEFT);
      label(c, "›", getWidth() - dp(38), y + row * .62f, 21, MUTED, Paint.Align.RIGHT);
      paint.setColor(Color.argb(25, 0, 0, 0));
      c.drawRect(dp(36), y + row - 1, getWidth() - dp(36), y + row, paint);
      y += row;
    }
  }

  private void drawMiniBalls(Canvas c, float x, float y) {
    paint.setStrokeWidth(dp(4));
    paint.setColor(Color.rgb(69, 60, 52));
    c.drawLine(x - dp(27), y - dp(48), x - dp(22), y - dp(12), paint);
    c.drawLine(x + dp(27), y - dp(48), x + dp(22), y - dp(12), paint);
    paint.setColor(Color.rgb(193, 139, 106));
    c.drawOval(x - dp(43), y - dp(16), x - dp(3), y + dp(35), paint);
    paint.setColor(Color.rgb(178, 120, 92));
    c.drawOval(x + dp(3), y - dp(16), x + dp(43), y + dp(35), paint);
  }

  private void drawGame(Canvas c, boolean speed) {
    label(
        c,
        speed ? "SPEED MODE" : GameModeConfig.displayName(prefs.mode()).toUpperCase(Locale.US),
        dp(18),
        dp(42),
        12,
        MUTED,
        Paint.Align.LEFT);
    label(
        c,
        speed
            ? String.valueOf(completedMissions)
            : String.format(Locale.US, "%,d", prefs.todayTaps()),
        getWidth() / 2f,
        dp(43),
        23,
        INK,
        Paint.Align.CENTER);
    label(c, "Ⅱ", getWidth() - dp(20), dp(43), 19, INK, Paint.Align.RIGHT);
    if (speed) drawSpeedHud(c);
    else {
      label(c, "TAPS TODAY", getWidth() / 2f, dp(61), 9, MUTED, Paint.Align.CENTER);
      progress(
          c,
          dp(18),
          dp(72),
          getWidth() - dp(18),
          dp(77),
          Math.min(1f, prefs.todayTaps() / 500f),
          ACCENT);
      if (activeChallenge != null) {
        String timer =
            activeChallenge.durationMs > 0
                ? String.format(Locale.US, "  %.1fs", Math.max(0, challengeTimeLeft))
                : "";
        label(
            c,
            activeChallenge.title + "  " + challengeProgress + "/" + activeChallenge.target + timer,
            getWidth() / 2f,
            dp(101),
            11,
            INK,
            Paint.Align.CENTER);
      } else if (duelActive)
        label(
            c,
            String.format(
                Locale.US,
                "DUEL  L %d  •  R %d   %.1fs",
                duelLeft,
                duelRight,
                Math.max(0, duelTimeLeft)),
            getWidth() / 2f,
            dp(101),
            12,
            INK,
            Paint.Align.CENTER);
    }
    drawRig(c);
    drawBalls(c);
    if (!speed)
      label(
          c, boredomLabel(), getWidth() / 2f, getHeight() - dp(28), 12, MUTED, Paint.Align.CENTER);
    if (speed && countdown > 0) {
      String value = countdown > 1 ? String.valueOf((int) Math.ceil(countdown - 1)) : "GO";
      overlay(c, value, "Get both thumbs ready");
    } else if (speed && successPause > 0)
      overlay(c, "SUCCESS", String.format(Locale.US, "+%.1fs carry", carrySeconds));
  }

  private void drawSpeedHud(Canvas c) {
    if (mission == null) return;
    label(
        c,
        "MISSION #" + (missionIndex + 1),
        getWidth() / 2f,
        dp(86),
        11,
        MUTED,
        Paint.Align.CENTER);
    label(c, mission.title, getWidth() / 2f, dp(111), 17, INK, Paint.Align.CENTER);
    int target = mission.targetForProgress();
    label(
        c,
        Math.min(target, missionEngine.progress()) + " / " + target,
        getWidth() / 2f,
        dp(139),
        14,
        INK,
        Paint.Align.CENTER);
    progress(
        c,
        dp(28),
        dp(151),
        getWidth() - dp(28),
        dp(158),
        target == 0 ? 0 : Math.min(1f, missionEngine.progress() / (float) target),
        ACCENT);
    int color = timeLeft < 3 && countdown <= 0 ? Color.rgb(202, 48, 48) : INK;
    label(
        c,
        String.format(Locale.US, "%.1f s", Math.max(0, timeLeft)),
        dp(24),
        dp(184),
        21,
        color,
        Paint.Align.LEFT);
    label(
        c,
        String.format(Locale.US, "carry +%.1f", carrySeconds),
        getWidth() - dp(24),
        dp(181),
        11,
        MUTED,
        Paint.Align.RIGHT);
  }

  private void drawRig(Canvas c) {
    paint.setColor(Color.rgb(61, 56, 50));
    paint.setStrokeWidth(dp(7));
    paint.setStrokeCap(Paint.Cap.ROUND);
    c.drawLine(getWidth() * .27f, left.anchorY, getWidth() * .73f, left.anchorY, paint);
    paint.setColor(Color.argb(35, 0, 0, 0));
    c.drawOval(getWidth() * .22f, getHeight() * .67f, getWidth() * .78f, getHeight() * .72f, paint);
  }

  private void drawBalls(Canvas c) {
    drawBall(c, left, true);
    drawBall(c, right, false);
  }

  private void drawBall(Canvas c, PhysicsBall b, boolean isLeft) {
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(dp(7));
    paint.setColor(Color.rgb(74, 61, 53));
    c.drawLine(b.anchorX, b.anchorY, b.x, b.y - b.radius * .52f, paint);
    paint.setStyle(Paint.Style.FILL);
    c.save();
    c.translate(b.x, b.y);
    c.rotate((float) Math.toDegrees(b.angle) * .28f);
    float rx = b.radius * .90f, ry = b.radius * 1.14f;
    ensureSkinGradient(b.radius);
    paint.setShader(skinGradient);
    paint.setShadowLayer(dp(10), dp(2), dp(7), Color.argb(48, 0, 0, 0));
    c.drawOval(-rx, -ry, rx, ry, paint);
    paint.clearShadowLayer();
    paint.setShader(null);
    drawSkinDetails(c, rx, ry, isLeft);
    c.restore();
  }

  private void ensureSkinGradient(float radius) {
    String skin = prefs.skin();
    if (skin.equals(cachedSkin) && Math.abs(radius - cachedRadius) < .5f) return;
    cachedSkin = skin;
    cachedRadius = radius;
    int base = Color.rgb(188, 132, 100),
        dark = Color.rgb(103, 65, 51),
        light = Color.rgb(239, 192, 151);
    switch (skin) {
      case "COCONUT":
        base = Color.rgb(126, 82, 48);
        dark = Color.rgb(62, 37, 22);
        light = Color.rgb(181, 128, 76);
        break;
      case "WATERMELON":
        base = Color.rgb(68, 157, 76);
        dark = Color.rgb(16, 78, 37);
        light = Color.rgb(130, 211, 113);
        break;
      case "MOON":
        base = Color.rgb(183, 186, 184);
        dark = Color.rgb(85, 91, 95);
        light = Color.rgb(235, 237, 230);
        break;
      case "PINGPONG":
        base = Color.rgb(245, 242, 228);
        dark = Color.rgb(167, 161, 143);
        light = Color.WHITE;
        break;
      case "FOOTBALL":
        base = Color.rgb(232, 230, 219);
        dark = Color.rgb(72, 72, 69);
        light = Color.WHITE;
        break;
      case "DISCO":
        base = Color.rgb(137, 157, 181);
        dark = Color.rgb(44, 51, 70);
        light = Color.rgb(246, 251, 255);
        break;
    }
    skinGradient =
        new RadialGradient(
            -radius * .28f,
            -radius * .42f,
            radius * 1.85f,
            new int[] {light, base, dark},
            new float[] {0, .47f, 1},
            Shader.TileMode.CLAMP);
  }

  private void drawSkinDetails(Canvas c, float rx, float ry, boolean leftSide) {
    String skin = prefs.skin();
    if ("FOOTBALL".equals(skin)) {
      paint.setColor(Color.rgb(38, 38, 38));
      path.reset();
      for (int i = 0; i < 5; i++) {
        double a = -Math.PI / 2 + i * Math.PI * 2 / 5;
        float x = (float) Math.cos(a) * rx * .25f, y = (float) Math.sin(a) * ry * .22f;
        if (i == 0) path.moveTo(x, y);
        else path.lineTo(x, y);
      }
      path.close();
      c.drawPath(path, paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(2));
      for (int i = 0; i < 5; i++) {
        double a = i * Math.PI * 2 / 5;
        c.drawLine(
            (float) Math.cos(a) * rx * .26f,
            (float) Math.sin(a) * ry * .23f,
            (float) Math.cos(a) * rx * .78f,
            (float) Math.sin(a) * ry * .70f,
            paint);
      }
      paint.setStyle(Paint.Style.FILL);
    } else if ("WATERMELON".equals(skin)) {
      paint.setColor(Color.rgb(28, 101, 48));
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(3));
      for (int i = -2; i <= 2; i++)
        c.drawArc(-rx + i * dp(4), -ry, rx - i * dp(4), ry, 78, 204, false, paint);
      paint.setStyle(Paint.Style.FILL);
    } else if ("DISCO".equals(skin)) {
      paint.setColor(Color.argb(115, 255, 255, 255));
      paint.setStrokeWidth(dp(1.3f));
      for (float y = -ry + dp(10); y < ry; y += dp(11)) c.drawLine(-rx, y, rx, y, paint);
      for (float x = -rx + dp(10); x < rx; x += dp(11)) c.drawLine(x, -ry, x, ry, paint);
      paint.setColor(Color.WHITE);
      c.drawCircle(-rx * .28f, -ry * .35f, dp(3), paint);
    } else if ("MOON".equals(skin)) {
      paint.setColor(Color.argb(42, 20, 25, 28));
      c.drawCircle(-rx * .23f, ry * .2f, rx * .13f, paint);
      c.drawCircle(rx * .25f, -ry * .2f, rx * .09f, paint);
      c.drawCircle(rx * .15f, ry * .48f, rx * .06f, paint);
    } else if ("COCONUT".equals(skin)) {
      paint.setColor(Color.argb(105, 47, 27, 14));
      paint.setStrokeWidth(dp(1));
      for (int i = -3; i <= 3; i++)
        c.drawLine(i * rx * .2f, -ry * .82f, (i - 1) * rx * .15f, ry * .82f, paint);
    } else if ("PINGPONG".equals(skin)) {
      paint.setColor(Color.rgb(227, 91, 40));
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(2));
      c.drawArc(-rx * .38f, -ry * .2f, rx * .38f, ry * .2f, 190, 140, false, paint);
      paint.setStyle(Paint.Style.FILL);
    } else if ("CLASSIC".equals(skin)) {
      paint.setColor(Color.argb(72, 80, 45, 34));
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(1));
      for (int i = 0; i < 3; i++)
        c.drawArc(
            -rx * .52f,
            ry * (-.02f + i * .13f),
            rx * .52f,
            ry * (.08f + i * .13f),
            20,
            140,
            false,
            paint);
      paint.setColor(Color.rgb(66, 45, 36));
      for (int i = 0; i < 7; i++) {
        float side = ((i + (leftSide ? 0 : 1)) % 2 == 0 ? -1 : 1),
            x = side * rx * (.25f + .05f * (i % 2)),
            y = -ry * .25f + i * ry * .11f;
        c.drawLine(x, y, x + side * dp(3), y - dp(5 + i % 3), paint);
      }
      paint.setStyle(Paint.Style.FILL);
    }
    paint.setColor(Color.argb(55, 255, 255, 255));
    c.drawOval(-rx * .48f, -ry * .64f, rx * .03f, -ry * .18f, paint);
  }

  private void drawParticles(Canvas c) {
    for (Particle q : particles) {
      paint.setColor(q.color);
      paint.setAlpha((int) (255 * Math.min(1, q.life)));
      c.drawCircle(q.x, q.y, dp(3.5f), paint);
    }
    paint.setAlpha(255);
  }

  private void drawSettings(Canvas c) {
    screenTitle(c, "Settings");
    setting(c, 0, "Sound Effects", prefs.sound());
    setting(c, 1, "Haptic Feedback", prefs.haptics());
    setting(c, 2, "Motion / Device Gravity", prefs.deviceGravity());
    setting(c, 3, "Reduced Motion", prefs.reducedMotion());
    label(c, "ANIMATION INTENSITY", dp(28), dp(365), 10, MUTED, Paint.Align.LEFT);
    String[] levels = {"CALM", "NORMAL", "WILD"};
    for (int i = 0; i < 3; i++)
      chip(
          c,
          dp(28) + i * (getWidth() - dp(56)) / 3f,
          dp(380),
          dp(28) + (i + 1) * (getWidth() - dp(56)) / 3f - dp(6),
          dp(424),
          levels[i],
          levels[i].equals(prefs.intensity()));
    button(c, dp(28), dp(458), getWidth() - dp(28), dp(510), "Reset statistics", false);
    button(c, dp(28), dp(522), getWidth() - dp(28), dp(574), "Reset achievements", false);
    label(c, "Version 1.1.0", getWidth() / 2f, getHeight() - dp(28), 11, MUTED, Paint.Align.CENTER);
  }

  private void setting(Canvas c, int index, String name, boolean value) {
    float top = dp(105) + index * dp(59);
    label(c, name, dp(28), top + dp(29), 15, INK, Paint.Align.LEFT);
    rect.set(getWidth() - dp(76), top + dp(10), getWidth() - dp(28), top + dp(36));
    paint.setColor(value ? INK : Color.rgb(204, 199, 190));
    c.drawRoundRect(rect, dp(15), dp(15), paint);
    paint.setColor(Color.WHITE);
    c.drawCircle(value ? rect.right - dp(13) : rect.left + dp(13), rect.centerY(), dp(10), paint);
  }

  private void drawRecords(Canvas c) {
    screenTitle(c, "Records");
    String[] filters = {"ALL", "CLASSIC", "SPEED", "CHALLENGE"};
    float width = (getWidth() - dp(40)) / 4f;
    for (int i = 0; i < 4; i++)
      chip(
          c,
          dp(20) + i * width,
          dp(91),
          dp(20) + (i + 1) * width - dp(4),
          dp(130),
          filters[i],
          selectedRecordFilter == i);
    List<GameRecord> records = prefs.records();
    float y = dp(150);
    int shown = 0;
    for (GameRecord record : records) {
      if (selectedRecordFilter == 1 && !"CLASSIC".equals(record.mode)) continue;
      if (selectedRecordFilter == 2 && !"SPEED".equals(record.mode)) continue;
      if (selectedRecordFilter == 3 && !"CHALLENGE".equals(record.mode)) continue;
      if (shown++ >= 5) break;
      rect.set(dp(20), y, getWidth() - dp(20), y + dp(88));
      paint.setColor(Color.argb(150, 255, 255, 255));
      c.drawRoundRect(rect, dp(17), dp(17), paint);
      paint.setColor(skinPreviewColor(record.skinId));
      c.drawCircle(dp(51), y + dp(44), dp(19), paint);
      String title =
          "SPEED".equals(record.mode)
              ? record.missions + " missions"
              : String.format(Locale.US, "%,d taps", record.taps);
      label(c, title, dp(82), y + dp(31), 16, INK, Paint.Align.LEFT);
      label(
          c,
          GamePreferences.skinName(record.skinId) + "  •  " + formatDuration(record.durationMs),
          dp(82),
          y + dp(54),
          11,
          MUTED,
          Paint.Align.LEFT);
      label(
          c,
          DateFormat.getDateInstance(DateFormat.MEDIUM).format(new Date(record.timestamp)),
          dp(82),
          y + dp(72),
          10,
          MUTED,
          Paint.Align.LEFT);
      if ("SPEED".equals(record.mode) && record.missions == prefs.bestSpeed())
        label(
            c,
            "BEST",
            getWidth() - dp(34),
            y + dp(31),
            10,
            Color.rgb(150, 94, 0),
            Paint.Align.RIGHT);
      y += dp(99);
    }
    if (shown == 0)
      label(
          c,
          "No runs yet. Your thumbs are suspiciously productive.",
          getWidth() / 2f,
          dp(220),
          13,
          MUTED,
          Paint.Align.CENTER);
  }

  private void drawAchievements(Canvas c) {
    screenTitle(c, "Achievements");
    String[]
        names =
            {
              "First ten",
              "Century of boredom",
              "One thousand taps",
              "Ten thousand taps",
              "Legendary 100K",
              "First Mission",
              "Five Missions",
              "Ten Missions",
              "Twenty Missions",
              "Perfect Alternation",
              "Carry Master"
            },
        ids =
            {
              "10",
              "100",
              "1000",
              "10000",
              "100000",
              "speed_first",
              "speed_5",
              "speed_10",
              "speed_20",
              "perfect_alternation",
              "carry_master"
            };
    float y = dp(98);
    for (int i = 0; i < names.length; i++) {
      boolean unlocked = prefs.achievement(ids[i]);
      paint.setColor(unlocked ? ACCENT : Color.rgb(210, 205, 196));
      c.drawCircle(dp(42), y + dp(16), dp(13), paint);
      label(
          c,
          unlocked ? "✓" : "·",
          dp(42),
          y + dp(21),
          12,
          unlocked ? INK : MUTED,
          Paint.Align.CENTER);
      label(c, names[i], dp(70), y + dp(21), 13, unlocked ? INK : MUTED, Paint.Align.LEFT);
      y += dp(44);
    }
  }

  private void drawChoices(Canvas c, boolean modes) {
    screenTitle(c, modes ? "Game Modes" : "Skins");
    String[] ids = modes ? GameModeConfig.IDS : GamePreferences.SKIN_IDS,
        names = modes ? GameModeConfig.NAMES : GamePreferences.SKIN_NAMES;
    String current = modes ? prefs.mode() : prefs.skin();
    float y = dp(102);
    for (int i = 0; i < ids.length; i++) {
      rect.set(dp(24), y, getWidth() - dp(24), y + dp(56));
      paint.setColor(
          ids[i].equals(current) ? Color.rgb(255, 239, 189) : Color.argb(145, 255, 255, 255));
      c.drawRoundRect(rect, dp(16), dp(16), paint);
      if (!modes) {
        paint.setColor(skinPreviewColor(ids[i]));
        c.drawCircle(dp(54), y + dp(28), dp(16), paint);
      }
      label(c, names[i], modes ? dp(42) : dp(84), y + dp(34), 15, INK, Paint.Align.LEFT);
      y += dp(66);
    }
  }

  private void drawAbout(Canvas c) {
    screenTitle(c, "About");
    label(c, "بیکارم!", getWidth() / 2f, dp(146), 34, INK, Paint.Align.CENTER);
    label(
        c,
        "A lightweight, offline physics toy.",
        getWidth() / 2f,
        dp(183),
        14,
        MUTED,
        Paint.Align.CENTER);
    label(
        c,
        "No ads. No network. No tracking.",
        getWidth() / 2f,
        dp(216),
        13,
        INK,
        Paint.Align.CENTER);
    rect.set(dp(28), dp(255), getWidth() - dp(28), dp(405));
    paint.setColor(Color.argb(145, 255, 255, 255));
    c.drawRoundRect(rect, dp(20), dp(20), paint);
    label(c, "Built with Android Canvas", getWidth() / 2f, dp(300), 15, INK, Paint.Align.CENTER);
    label(
        c,
        "Frame-time physics • local records",
        getWidth() / 2f,
        dp(332),
        12,
        MUTED,
        Paint.Align.CENTER);
    label(c, "Version 1.1.0", getWidth() / 2f, dp(369), 12, MUTED, Paint.Align.CENTER);
  }

  private void drawChallenge(Canvas c) {
    screenTitle(c, "Challenges");
    DailyChallenge d = DailyChallenge.today();
    rect.set(dp(26), dp(105), getWidth() - dp(26), dp(310));
    paint.setColor(Color.argb(170, 255, 255, 255));
    c.drawRoundRect(rect, dp(22), dp(22), paint);
    label(c, d.title, getWidth() / 2f, dp(157), 22, INK, Paint.Align.CENTER);
    label(c, d.description, getWidth() / 2f, dp(197), 14, MUTED, Paint.Align.CENTER);
    label(
        c, "A fresh challenge every day", getWidth() / 2f, dp(239), 11, MUTED, Paint.Align.CENTER);
    button(c, dp(52), dp(326), getWidth() - dp(52), dp(386), "PLAY DAILY", true);
    button(c, dp(52), dp(410), getWidth() - dp(52), dp(470), "10 SECOND DUEL", false);
    label(
        c,
        "One player per side. Maximum useless glory.",
        getWidth() / 2f,
        dp(497),
        10,
        MUTED,
        Paint.Align.CENTER);
  }

  private void drawResults(Canvas c) {
    screenTitle(c, "Run complete");
    label(c, "MISSIONS COMPLETED", getWidth() / 2f, dp(137), 11, MUTED, Paint.Align.CENTER);
    label(
        c,
        String.valueOf(lastResult == null ? 0 : lastResult.missions),
        getWidth() / 2f,
        dp(204),
        52,
        INK,
        Paint.Align.CENTER);
    if (lastResult != null) {
      stat(c, "Total taps", String.valueOf(lastResult.taps), dp(260));
      stat(c, "Best streak", String.valueOf(speedBestStreak), dp(302));
      stat(c, "Session time", formatDuration(lastResult.durationMs), dp(344));
      stat(c, "Final score", String.valueOf(lastResult.score), dp(386));
      stat(c, "Skin", GamePreferences.skinName(lastResult.skinId), dp(428));
      stat(c, "Max carry", String.format(Locale.US, "%.1f s", lastResult.maxCarrySeconds), dp(470));
    }
    button(c, dp(26), dp(515), getWidth() / 2f - dp(7), dp(573), "HOME", false);
    button(c, getWidth() / 2f + dp(7), dp(515), getWidth() - dp(26), dp(573), "TRY AGAIN", true);
    button(c, dp(26), dp(584), getWidth() - dp(26), dp(634), "SHARE RESULT", false);
  }

  private void stat(Canvas c, String key, String value, float y) {
    label(c, key, dp(34), y, 13, MUTED, Paint.Align.LEFT);
    label(c, value, getWidth() - dp(34), y, 14, INK, Paint.Align.RIGHT);
  }

  private void screenTitle(Canvas c, String title) {
    label(c, "‹", dp(24), dp(49), 27, INK, Paint.Align.LEFT);
    label(c, title, getWidth() / 2f, dp(48), 22, INK, Paint.Align.CENTER);
  }

  @Override
  public boolean onTouchEvent(MotionEvent e) {
    if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
    performClick();
    float x = e.getX(), y = e.getY();
    if (screen != Screen.HOME && y < dp(78) && x < dp(70)) {
      navigateBack();
      return true;
    }
    switch (screen) {
      case HOME:
        touchHome(y);
        break;
      case GAME:
      case SPEED:
        touchGame(x, y);
        break;
      case SETTINGS:
        touchSettings(x, y);
        break;
      case RECORDS:
        if (y >= dp(88) && y <= dp(135)) {
          selectedRecordFilter = Math.min(3, (int) (x / (getWidth() / 4f)));
          invalidate();
        }
        break;
      case SKINS:
        touchChoice(y, false);
        break;
      case MODES:
        touchChoice(y, true);
        break;
      case CHALLENGE:
        if (y >= dp(315) && y < dp(400)) startDailyChallenge();
        else if (y >= dp(400) && y <= dp(485)) startDuel();
        break;
      case RESULTS:
        if (y >= dp(580) && lastResult != null)
          host.shareSnapshot(
              "Bikaram Speed Mode: "
                  + lastResult.missions
                  + " missions, "
                  + lastResult.taps
                  + " taps, skin "
                  + GamePreferences.skinName(lastResult.skinId));
        else if (y >= dp(500)) {
          if (x < getWidth() / 2f) {
            screen = Screen.HOME;
            invalidate();
          } else startSpeed();
        }
        break;
      default:
        break;
    }
    return true;
  }

  @Override
  public boolean performClick() {
    super.performClick();
    return true;
  }

  private void touchHome(float y) {
    if (y >= dp(282) && y <= dp(360)) {
      openGame();
      return;
    }
    if (y >= dp(362) && y <= dp(432)) {
      startSpeed();
      return;
    }
    float top = dp(442), row = Math.min(dp(46), (getHeight() - top - dp(14)) / 7f);
    int index = (int) ((y - top) / row);
    if (y < top || index < 0 || index > 6) return;
    Screen[] targets = {
      Screen.MODES,
      Screen.CHALLENGE,
      Screen.RECORDS,
      Screen.ACHIEVEMENTS,
      Screen.SKINS,
      Screen.SETTINGS,
      Screen.ABOUT
    };
    open(targets[index]);
  }

  private void touchGame(float x, float y) {
    if (y < dp(76) && x > getWidth() - dp(75)) {
      navigateBack();
      return;
    }
    if (screen == Screen.SPEED && (countdown > 0 || successPause > 0)) return;
    boolean hitLeft = distanceSquared(x, y, left.x, left.y) < left.radius * left.radius * 2.1f,
        hitRight = distanceSquared(x, y, right.x, right.y) < right.radius * right.radius * 2.1f;
    if (!hitLeft && !hitRight) {
      if (y > left.anchorY && y < getHeight() * .78f) hitLeft = x < getWidth() / 2f;
      else return;
    }
    tap(hitLeft ? 0 : 1);
  }

  private void touchSettings(float x, float y) {
    if (y >= dp(105) && y < dp(341)) {
      int row = Math.min(3, (int) ((y - dp(105)) / dp(59)));
      if (row == 0) prefs.setSound(!prefs.sound());
      else if (row == 1) prefs.setHaptics(!prefs.haptics());
      else if (row == 2) prefs.setDeviceGravity(!prefs.deviceGravity());
      else prefs.setReducedMotion(!prefs.reducedMotion());
      updateSensorRegistration();
      haptic(0);
      invalidate();
      return;
    }
    if (y >= dp(374) && y <= dp(432)) {
      int i = Math.min(2, (int) ((x - dp(28)) / ((getWidth() - dp(56)) / 3f)));
      if (i >= 0) {
        prefs.setIntensity(new String[] {"CALM", "NORMAL", "WILD"}[i]);
        invalidate();
      }
      return;
    }
    if (y >= dp(450) && y <= dp(518)) {
      prefs.resetStatistics();
      showToast("Statistics reset", 1.5f);
    } else if (y >= dp(518) && y <= dp(585)) {
      prefs.resetAchievements();
      showToast("Achievements reset", 1.5f);
    }
    invalidate();
  }

  private void touchChoice(float y, boolean modes) {
    int index = (int) ((y - dp(102)) / dp(66));
    String[] ids = modes ? GameModeConfig.IDS : GamePreferences.SKIN_IDS;
    if (index < 0 || index >= ids.length) return;
    if (modes) prefs.setMode(ids[index]);
    else {
      prefs.setSkin(ids[index]);
      cachedSkin = "";
    }
    haptic(0);
    invalidate();
  }

  private void open(Screen target) {
    previousScreen = screen;
    screen = target;
    updateSensorRegistration();
    invalidate();
  }

  private void openGame() {
    screen = Screen.GAME;
    classicStartedAt = System.currentTimeMillis();
    classicTaps = 0;
    lastFrameNanos = 0;
    updateSensorRegistration();
    requestFrame();
    invalidate();
  }

  private void tap(int side) {
    long now = System.currentTimeMillis();
    streak = now - lastTapMs < 430 ? streak + 1 : 1;
    lastTapMs = now;
    float chaos = Math.min(2.15f, 1f + streak * .025f);
    GameModeConfig mode = GameModeConfig.forId(prefs.mode());
    float intensity =
        prefs.reducedMotion()
            ? .72f
            : "CALM".equals(prefs.intensity())
                ? .82f
                : "WILD".equals(prefs.intensity()) ? 1.14f : 1f;
    float impulse = mode.tapImpulse * chaos * intensity * (random.nextBoolean() ? 1 : -1);
    if (side == 0) left.impulse(impulse);
    else right.impulse(impulse);
    if (streak > 14) {
      left.impulse((random.nextFloat() - .5f) * .18f * chaos);
      right.impulse((random.nextFloat() - .5f) * .18f * chaos);
    }
    if (streak > 25 && !prefs.reducedMotion()) shake = Math.min(dp(10), shake + dp(1));
    prefs.recordTap(streak);
    checkTapAchievements();
    haptic(0);
    playTap();
    if (screen == Screen.SPEED) handleSpeedTap(side);
    else {
      classicTaps++;
      if (duelActive) {
        if (side == 0) duelLeft++;
        else duelRight++;
      }
      handleChallengeTap(side);
    }
    requestFrame();
  }

  private void startDailyChallenge() {
    activeChallenge = DailyChallenge.today();
    challengeProgress = 0;
    challengeLastSide = -1;
    challengeTimeLeft = activeChallenge.durationMs / 1000f;
    prefs.setMode("NORMAL");
    openGame();
    showToast(activeChallenge.description, 1.5f);
  }

  private void startDuel() {
    activeChallenge = null;
    openGame();
    duelActive = true;
    duelTimeLeft = 10f;
    duelLeft = duelRight = 0;
    showToast("One player per side — GO!", 1.2f);
  }

  private void finishDuel() {
    duelActive = false;
    String result =
        duelLeft == duelRight
            ? "Draw — equally bored"
            : duelLeft > duelRight
                ? "LEFT wins " + duelLeft + "–" + duelRight
                : "RIGHT wins " + duelRight + "–" + duelLeft;
    prefs.saveRecord(
        new GameRecord(
            "CHALLENGE",
            Math.max(duelLeft, duelRight),
            0,
            duelLeft + duelRight,
            10_000,
            prefs.skin(),
            System.currentTimeMillis(),
            1,
            0));
    confetti(35);
    haptic(3);
    showToast(result, 2.2f);
  }

  private void handleChallengeTap(int side) {
    if (activeChallenge == null) return;
    boolean valid = activeChallenge.type != DailyChallenge.Type.LEFT_ONLY || side == 0;
    if (activeChallenge.type == DailyChallenge.Type.ALTERNATE && challengeLastSide != -1)
      valid = side != challengeLastSide;
    if (!valid) {
      activeChallenge = null;
      showToast("Challenge pattern broken", 1.6f);
      return;
    }
    challengeProgress++;
    challengeLastSide = side;
    if (challengeProgress >= activeChallenge.target) {
      long duration =
          activeChallenge.durationMs > 0
              ? (long) ((activeChallenge.durationMs / 1000f - challengeTimeLeft) * 1000)
              : System.currentTimeMillis() - classicStartedAt;
      prefs.saveRecord(
          new GameRecord(
              "CHALLENGE",
              challengeProgress,
              0,
              challengeProgress,
              duration,
              prefs.skin(),
              System.currentTimeMillis(),
              1,
              0));
      activeChallenge = null;
      confetti(50);
      haptic(3);
      showToast("Daily challenge complete!", 2f);
    }
  }

  private void finishClassicRun() {
    if (classicTaps > 0) {
      prefs.saveRecord(
          new GameRecord(
              "CLASSIC",
              classicTaps,
              0,
              classicTaps,
              System.currentTimeMillis() - classicStartedAt,
              prefs.skin(),
              System.currentTimeMillis(),
              0,
              0));
      classicTaps = 0;
    }
    activeChallenge = null;
    duelActive = false;
    prefs.flush();
  }

  private void startSpeed() {
    screen = Screen.SPEED;
    missionIndex =
        completedMissions = speedTaps = speedBestStreak = speedStreak = highestDifficulty = 0;
    speedLastSide = -1;
    carrySeconds = maxCarry = sessionSeconds = successPause = 0;
    beginMission();
    countdown = 4f;
    updateSensorRegistration();
    requestFrame();
    invalidate();
  }

  private void beginMission() {
    mission = SpeedMissionCatalog.forIndex(missionIndex, random);
    missionEngine.start(mission);
    timeLeft = mission.baseSeconds + carrySeconds;
    carrySeconds = 0;
    highestDifficulty = Math.max(highestDifficulty, mission.difficulty);
    speedStreak = 0;
    speedLastSide = -1;
  }

  private void handleSpeedTap(int side) {
    speedTaps++;
    speedStreak = speedLastSide == -1 || speedLastSide != side ? speedStreak + 1 : 1;
    speedLastSide = side;
    speedBestStreak = Math.max(speedBestStreak, speedStreak);
    SpeedMissionEngine.TapResult result = missionEngine.tap(side);
    if (result == SpeedMissionEngine.TapResult.FAILED) {
      showToast("Pattern broken", .55f);
      timeLeft = 0;
      return;
    }
    if (result == SpeedMissionEngine.TapResult.COMPLETE) {
      completedMissions++;
      carrySeconds = Math.min(MAX_CARRY_SECONDS, Math.max(0, timeLeft));
      maxCarry = Math.max(maxCarry, carrySeconds);
      unlockSpeedAchievements();
      if (mission.type == SpeedMission.Type.ALTERNATE) prefs.unlock("perfect_alternation");
      if (maxCarry >= 10) prefs.unlock("carry_master");
      missionIndex++;
      successPause = .48f;
      confetti(18);
      haptic(3);
    }
  }

  private void finishSpeedSession() {
    int score = completedMissions * 100 + speedTaps + highestDifficulty * 10;
    lastResult =
        new GameRecord(
            "SPEED",
            score,
            completedMissions,
            speedTaps,
            (long) (sessionSeconds * 1000),
            prefs.skin(),
            System.currentTimeMillis(),
            highestDifficulty,
            maxCarry);
    prefs.saveRecord(lastResult);
    prefs.flush();
    screen = Screen.RESULTS;
    updateSensorRegistration();
    removeFrame();
    invalidate();
  }

  private void unlockSpeedAchievements() {
    if (completedMissions >= 1) prefs.unlock("speed_first");
    if (completedMissions >= 5) prefs.unlock("speed_5");
    if (completedMissions >= 10) prefs.unlock("speed_10");
    if (completedMissions >= 20) prefs.unlock("speed_20");
  }

  private void checkTapAchievements() {
    for (long target : GamePreferences.TAP_ACHIEVEMENTS)
      if (prefs.totalTaps() >= target && !prefs.achievement(String.valueOf(target))) {
        prefs.unlock(String.valueOf(target));
        showToast("Achievement unlocked", 1.7f);
        confetti(24);
        haptic(3);
        break;
      }
  }

  private void playTap() {
    GameModeConfig mode = GameModeConfig.forId(prefs.mode());
    long now = System.currentTimeMillis();
    if (!prefs.sound() || mode.silent || tone == null || now - lastSoundMs < 65) return;
    lastSoundMs = now;
    try {
      tone.startTone(ToneGenerator.TONE_PROP_BEEP, 22);
    } catch (RuntimeException ignored) {
    }
  }

  private void playCollision() {
    GameModeConfig mode = GameModeConfig.forId(prefs.mode());
    long now = System.currentTimeMillis();
    if (!prefs.sound() || mode.silent || tone == null || now - lastSoundMs < 110) return;
    lastSoundMs = now;
    try {
      tone.startTone(ToneGenerator.TONE_PROP_NACK, 35);
    } catch (RuntimeException ignored) {
    }
  }

  private void haptic(int kind) {
    if (!prefs.haptics()
        || vibrator == null
        || !vibrator.hasVibrator()
        || GameModeConfig.forId(prefs.mode()).silent) return;
    int amplitude = kind == 3 ? 85 : kind == 2 ? 62 : 38;
    long duration = kind == 3 ? 18 : kind == 2 ? 12 : 7;
    try {
      vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
    } catch (RuntimeException ignored) {
    }
  }

  private void confetti(int count) {
    if (prefs.reducedMotion()) count = Math.min(8, count);
    int[] colors = {
      Color.rgb(237, 80, 72),
      ACCENT,
      Color.rgb(66, 145, 220),
      Color.rgb(72, 177, 116),
      Color.rgb(161, 93, 211)
    };
    for (int i = 0; i < count && particles.size() < 100; i++) {
      Particle q = new Particle();
      q.x = getWidth() / 2f;
      q.y = getHeight() * .35f;
      q.vx = (random.nextFloat() - .5f) * dp(360);
      q.vy = -random.nextFloat() * dp(350) - dp(70);
      q.life = .7f + random.nextFloat();
      q.color = colors[i % colors.length];
      particles.add(q);
    }
    requestFrame();
  }

  private void updateSensorRegistration() {
    boolean should =
        hostActive
            && prefs.deviceGravity()
            && (screen == Screen.GAME || screen == Screen.SPEED)
            && accelerometer != null;
    if (should && !sensorRegistered)
      sensorRegistered =
          sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    else if (!should) unregisterSensor();
  }

  private void unregisterSensor() {
    if (sensorRegistered) sensorManager.unregisterListener(this);
    sensorRegistered = false;
    tiltX = 0;
  }

  @Override
  public void onSensorChanged(SensorEvent e) {
    if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
      tiltX = tiltX * .84f + (-e.values[0]) * .16f;
      requestFrame();
    }
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}

  private void button(Canvas c, float l, float t, float r, float b, String value, boolean primary) {
    rect.set(l, t, r, b);
    paint.setColor(primary ? INK : Color.argb(155, 255, 255, 255));
    c.drawRoundRect(rect, dp(18), dp(18), paint);
    label(
        c,
        value,
        rect.centerX(),
        rect.centerY() + dp(5),
        14,
        primary ? Color.WHITE : INK,
        Paint.Align.CENTER);
  }

  private void chip(Canvas c, float l, float t, float r, float b, String value, boolean selected) {
    rect.set(l, t, r, b);
    paint.setColor(selected ? INK : Color.argb(135, 255, 255, 255));
    c.drawRoundRect(rect, dp(12), dp(12), paint);
    label(
        c,
        value,
        rect.centerX(),
        rect.centerY() + dp(4),
        9,
        selected ? Color.WHITE : MUTED,
        Paint.Align.CENTER);
  }

  private void progress(Canvas c, float l, float t, float r, float b, float value, int color) {
    rect.set(l, t, r, b);
    paint.setColor(Color.argb(34, 0, 0, 0));
    c.drawRoundRect(rect, dp(5), dp(5), paint);
    rect.right = l + (r - l) * Math.max(0, Math.min(1, value));
    paint.setColor(color);
    c.drawRoundRect(rect, dp(5), dp(5), paint);
  }

  private void overlay(Canvas c, String title, String subtitle) {
    rect.set(dp(35), getHeight() * .39f, getWidth() - dp(35), getHeight() * .57f);
    paint.setColor(Color.argb(229, 25, 25, 24));
    c.drawRoundRect(rect, dp(24), dp(24), paint);
    label(c, title, rect.centerX(), rect.centerY() - dp(2), 36, Color.WHITE, Paint.Align.CENTER);
    label(
        c, subtitle, rect.centerX(), rect.centerY() + dp(31), 11, Color.LTGRAY, Paint.Align.CENTER);
  }

  private void drawToast(Canvas c) {
    rect.set(dp(34), getHeight() - dp(90), getWidth() - dp(34), getHeight() - dp(40));
    paint.setColor(Color.argb(230, 25, 25, 24));
    c.drawRoundRect(rect, dp(17), dp(17), paint);
    label(c, toast, rect.centerX(), rect.centerY() + dp(5), 12, Color.WHITE, Paint.Align.CENTER);
  }

  private void showToast(String value, float seconds) {
    toast = value;
    toastSeconds = seconds;
    requestFrame();
    invalidate();
  }

  private void label(
      Canvas c, String value, float x, float y, float size, int color, Paint.Align align) {
    text.setTextAlign(align);
    text.setTextSize(sp(size));
    text.setColor(color);
    c.drawText(value, x, y, text);
  }

  private String boredomLabel() {
    long n = prefs.todayTaps();
    if (n < 20) return "Barely bored";
    if (n < 100) return "Properly bored";
    if (n < 500) return "Professionally bored";
    if (n < 2000) return "Master of nothing";
    return "Supreme boredom unlocked";
  }

  private int skinPreviewColor(String id) {
    switch (id) {
      case "FOOTBALL":
        return Color.rgb(225, 223, 214);
      case "COCONUT":
        return Color.rgb(119, 76, 44);
      case "DISCO":
        return Color.rgb(124, 150, 182);
      case "WATERMELON":
        return Color.rgb(64, 153, 75);
      case "MOON":
        return Color.rgb(179, 183, 183);
      case "PINGPONG":
        return Color.rgb(244, 240, 221);
      default:
        return Color.rgb(188, 132, 100);
    }
  }

  private String formatDuration(long ms) {
    long s = Math.max(0, ms / 1000);
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
  }

  private float distanceSquared(float x1, float y1, float x2, float y2) {
    float dx = x1 - x2, dy = y1 - y2;
    return dx * dx + dy * dy;
  }

  private float dp(float v) {
    return v * density;
  }

  private float sp(float v) {
    return v * scaledDensity;
  }
}
