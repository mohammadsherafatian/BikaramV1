package com.bikaram.toy;

import android.content.Context;
import android.graphics.*;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class BoredomView extends View implements SensorEventListener {
    interface Host { void shareSnapshot(String text); }

    private static final String[] MODES = {"NORMAL", "ZEN", "RAGE", "OFFICE", "TURBO", "GRAVITY"};
    private static final String[] MODE_FA = {"معمولی", "ذن", "اعصاب‌خورد", "اداری", "توربو", "جاذبه"};
    private static final String[] SKINS = {"CLASSIC", "FOOTBALL", "COCONUT", "DISCO", "WATERMELON", "MOON", "PINGPONG"};
    private static final String[] SKIN_FA = {"کلاسیک", "فوتبال", "نارگیل", "دیسکو", "هندوانه", "ماه", "پینگ‌پنگ"};
    private static final long[] ACHIEVEMENTS = {10, 100, 1000, 10000, 100000};
    private static final String[] ACHIEVEMENT_NAMES = {"اولین دست‌کاری", "کار و زندگی نداری؟", "برو یه کاری پیدا کن", "استاد اعظم بیکاری", "این دیگه نگران‌کننده‌ست"};

    private final Host host;
    private final GameState state;
    private final PhysicsBall left = new PhysicsBall(-0.08f);
    private final PhysicsBall right = new PhysicsBall(0.08f);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Vibrator vibrator;
    private ToneGenerator tone;
    private DailyChallenge challenge = DailyChallenge.today();

    private float density;
    private long lastFrameNanos;
    private long lastTapMs;
    private int streak;
    private float tiltX;
    private float shake;
    private int flashFrames;
    private boolean menuModes;
    private boolean menuSkins;
    private boolean statsOpen;
    private boolean challengeOpen;
    private String toast = "";
    private long toastUntil;

    private boolean challengeRunning;
    private long challengeStarted;
    private int challengeProgress;
    private boolean challengeFailed;
    private int lastChallengeSide = -1;

    private boolean duelActive;
    private long duelStarted;
    private int duelLeft;
    private int duelRight;
    private String duelResult = "";
    private long duelResultUntil;

    private final List<Particle> particles = new ArrayList<>();

    private static final class Particle {
        float x, y, vx, vy, life;
        int color;
    }

    BoredomView(Context context, Host host) {
        super(context);
        this.host = host;
        state = new GameState(context);
        density = getResources().getDisplayMetrics().density;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 35); } catch (Exception ignored) {}
        text.setTypeface(Typeface.create("sans", Typeface.BOLD));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        postInvalidateOnAnimation();
    }

    void startSensors() {
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    void stopSensors() { sensorManager.unregisterListener(this); }

    void release() { if (tone != null) tone.release(); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float r = Math.min(w * 0.155f, dp(74));
        float anchorY = h * 0.22f;
        float rope = h * 0.25f;
        left.layout(w * 0.42f, anchorY, rope, r);
        right.layout(w * 0.58f, anchorY + dp(4), rope * 1.02f, r);
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 1f / 60f : Math.min(0.033f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        update(dt);

        canvas.save();
        if (shake > 0.2f) canvas.translate((random.nextFloat() - .5f) * shake, (random.nextFloat() - .5f) * shake);
        drawBackground(canvas);
        drawHeader(canvas);
        drawPlayground(canvas);
        drawBalls(canvas);
        drawParticles(canvas);
        drawBottomBar(canvas);
        drawOverlays(canvas);
        canvas.restore();

        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        float gravity = dp(780);
        float damping = 0.987f;
        float impulseScale = 1f;
        switch (state.mode) {
            case "ZEN": gravity *= .65f; damping = .965f; impulseScale = .65f; break;
            case "RAGE": gravity *= 1.1f; damping = .993f; impulseScale = 1.55f; break;
            case "OFFICE": gravity *= .85f; damping = .98f; impulseScale = .75f; break;
            case "TURBO": gravity *= 1.35f; damping = .996f; impulseScale = 2.05f; break;
            case "GRAVITY": gravity *= .82f; damping = .99f; impulseScale = 1.05f; break;
        }
        float horizontal = state.mode.equals("GRAVITY") ? tiltX * dp(145) : tiltX * dp(18);
        left.step(dt, gravity, damping, horizontal);
        right.step(dt, gravity, damping, horizontal);
        resolveCollision();
        shake *= 0.87f;
        if (flashFrames > 0) flashFrames--;

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle q = particles.get(i);
            q.vy += dp(250) * dt;
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.life -= dt;
            if (q.life <= 0) particles.remove(i);
        }

        long ms = System.currentTimeMillis();
        if (duelActive && ms - duelStarted >= 10_000) finishDuel();
        if (challengeRunning && challenge.durationMs > 0 && ms - challengeStarted >= challenge.durationMs && challengeProgress < challenge.target) {
            challengeRunning = false;
            challengeFailed = true;
            showToast("وقت تموم شد 😵 دوباره بزن", 1900);
        }
    }

    private void resolveCollision() {
        float dx = right.x - left.x;
        float dy = right.y - left.y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float min = left.radius + right.radius - dp(8);
        if (d < min && d > 1) {
            float push = (min - d) / min;
            float tmp = left.angularVelocity;
            left.angularVelocity = right.angularVelocity * .88f - push * .5f;
            right.angularVelocity = tmp * .88f + push * .5f;
            if (!state.mode.equals("OFFICE") && random.nextFloat() < .12f) playBonk();
        }
    }

    private void drawBackground(Canvas c) {
        int top = Color.rgb(249, 245, 235);
        int bottom = Color.rgb(231, 224, 209);
        p.setShader(new LinearGradient(0, 0, 0, getHeight(), top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, getWidth(), getHeight(), p);
        p.setShader(null);
        if (flashFrames > 0) {
            p.setColor(Color.argb(45 + flashFrames * 8, 255, 220, 80));
            c.drawRect(0, 0, getWidth(), getHeight(), p);
        }
    }

    private void drawHeader(Canvas c) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Color.rgb(25, 25, 25));
        text.setTextSize(sp(28));
        c.drawText("بیکارم!", getWidth()/2f, dp(46), text);
        text.setTextSize(sp(12));
        text.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        c.drawText("وقتی واقعاً هیچ کاری نداری", getWidth()/2f, dp(67), text);
        text.setTypeface(Typeface.create("sans", Typeface.BOLD));

        drawPill(c, dp(14), dp(82), getWidth()*0.48f-dp(18), dp(42), boredomLabel(), Color.rgb(255,255,255));
        drawPill(c, getWidth()*0.52f, dp(82), getWidth()-dp(14), dp(42), String.format(Locale.US,"امروز %,d ضربه", state.todayTaps), Color.rgb(255,255,255));
    }

    private void drawPlayground(Canvas c) {
        float y = getHeight() * .18f;
        p.setColor(Color.rgb(53, 49, 43));
        p.setStrokeWidth(dp(7));
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(getWidth()*.29f, y, getWidth()*.71f, y, p);
        p.setColor(Color.argb(45,0,0,0));
        c.drawOval(getWidth()*.25f, getHeight()*.58f, getWidth()*.75f, getHeight()*.64f, p);

        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sp(13));
        text.setColor(Color.rgb(80,75,68));
        c.drawText("بزن روشون — پشت سر هم بزنی وحشی‌تر می‌شن", getWidth()/2f, getHeight()*.69f, text);

        if (duelActive) {
            long remain = Math.max(0, 10_000 - (System.currentTimeMillis()-duelStarted));
            text.setTextSize(sp(20)); text.setColor(Color.rgb(20,20,20));
            c.drawText(String.format(Locale.US,"دو نفره  %.1fs", remain/1000f), getWidth()/2f, getHeight()*.16f, text);
            text.setTextSize(sp(16));
            c.drawText("چپ: "+duelLeft+"     راست: "+duelRight, getWidth()/2f, getHeight()*.72f, text);
        }
        if (challengeRunning) {
            text.setTextSize(sp(15)); text.setColor(Color.rgb(20,20,20));
            c.drawText(challenge.title+"  "+challengeProgress+"/"+challenge.target, getWidth()/2f, getHeight()*.735f, text);
        }
    }

    private void drawBalls(Canvas c) {
        drawOneBall(c, left, true);
        drawOneBall(c, right, false);
    }

    private void drawOneBall(Canvas c, PhysicsBall b, boolean isLeft) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(8));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(Color.rgb(74,61,53));
        c.drawLine(b.anchorX, b.anchorY, b.x, b.y - b.radius*.55f, p);
        p.setStyle(Paint.Style.FILL);

        c.save();
        c.rotate((float)Math.toDegrees(b.angle)*.35f, b.x, b.y);
        float rx = b.radius * .90f;
        float ry = b.radius * 1.15f;
        RectF oval = new RectF(b.x-rx, b.y-ry, b.x+rx, b.y+ry);
        drawSkin(c, oval, b, isLeft);
        drawWrinklesAndHair(c, oval, b, isLeft);
        c.restore();
    }

    private void drawSkin(Canvas c, RectF oval, PhysicsBall b, boolean isLeft) {
        int base = Color.rgb(190,136,106);
        int dark = Color.rgb(116,75,59);
        int light = Color.rgb(234,184,145);
        switch (state.skin) {
            case "COCONUT": base=Color.rgb(126,83,50); dark=Color.rgb(70,43,25); light=Color.rgb(165,112,68); break;
            case "WATERMELON": base=Color.rgb(75,166,82); dark=Color.rgb(19,93,45); light=Color.rgb(112,204,106); break;
            case "MOON": base=Color.rgb(187,188,186); dark=Color.rgb(95,98,101); light=Color.rgb(225,226,220); break;
            case "PINGPONG": base=Color.rgb(248,245,234); dark=Color.rgb(184,180,164); light=Color.WHITE; break;
            case "FOOTBALL": base=Color.rgb(237,234,222); dark=Color.rgb(37,37,37); light=Color.WHITE; break;
            case "DISCO": base=Color.rgb(160,170,184); dark=Color.rgb(55,60,72); light=Color.rgb(235,242,255); break;
        }
        p.setShader(new RadialGradient(oval.centerX()-oval.width()*.18f, oval.centerY()-oval.height()*.24f,
                oval.width()*.85f, new int[]{light,base,dark}, new float[]{0f,.48f,1f}, Shader.TileMode.CLAMP));
        c.drawOval(oval,p); p.setShader(null);

        if (state.skin.equals("FOOTBALL")) {
            p.setColor(Color.rgb(40,40,40));
            for(int i=0;i<5;i++){
                double a=i*Math.PI*2/5 + (isLeft?0:.4);
                c.drawCircle(oval.centerX()+(float)Math.cos(a)*oval.width()*.20f, oval.centerY()+(float)Math.sin(a)*oval.height()*.22f, oval.width()*.09f,p);
            }
        } else if (state.skin.equals("WATERMELON")) {
            p.setColor(Color.rgb(31,112,54)); p.setStrokeWidth(dp(3)); p.setStyle(Paint.Style.STROKE);
            for(int i=-2;i<=2;i++) c.drawArc(new RectF(oval.left+i*dp(5),oval.top,oval.right-i*dp(5),oval.bottom),80,200,false,p);
            p.setStyle(Paint.Style.FILL);
        } else if (state.skin.equals("DISCO")) {
            p.setColor(Color.argb(105,255,255,255)); p.setStrokeWidth(dp(1.3f));
            for(float yy=oval.top+dp(12); yy<oval.bottom; yy+=dp(12)) c.drawLine(oval.left,yy,oval.right,yy,p);
            for(float xx=oval.left+dp(12); xx<oval.right; xx+=dp(12)) c.drawLine(xx,oval.top,xx,oval.bottom,p);
        } else if (state.skin.equals("MOON")) {
            p.setColor(Color.argb(40,30,30,30));
            c.drawCircle(oval.centerX()-oval.width()*.22f,oval.centerY()+oval.height()*.15f,oval.width()*.10f,p);
            c.drawCircle(oval.centerX()+oval.width()*.18f,oval.centerY()-oval.height()*.12f,oval.width()*.07f,p);
        }
    }

    private void drawWrinklesAndHair(Canvas c, RectF oval, PhysicsBall b, boolean isLeft) {
        if (!state.skin.equals("CLASSIC") && !state.skin.equals("COCONUT")) return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(dp(1.15f));
        p.setColor(Color.argb(95, 76, 45, 35));
        for (int i=0;i<4;i++) {
            float yy=oval.centerY() + (i-1.5f)*oval.height()*.10f;
            c.drawArc(new RectF(oval.left+oval.width()*.20f,yy-dp(4),oval.right-oval.width()*.20f,yy+dp(5)),20,140,false,p);
        }
        // Sparse stylized hair: deliberately non-erotic/cartoon-realistic.
        p.setColor(Color.rgb(58,42,35)); p.setStrokeWidth(dp(1.2f));
        int seed = isLeft ? 17 : 31;
        for (int i=0;i<10;i++) {
            float t=(i+1)/11f;
            float yy=oval.top+oval.height()*(.16f+.58f*t);
            float side=((i+seed)%2==0?-1:1);
            float xx=oval.centerX()+side*oval.width()*(.22f+.08f*((i*7)%3));
            float len=dp(5+((i*5+seed)%5));
            c.drawLine(xx,yy,xx+side*len*.55f,yy-len,p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawParticles(Canvas c) {
        for (Particle q : particles) {
            p.setColor(q.color); p.setAlpha((int)(255*Math.min(1,q.life)));
            c.drawCircle(q.x,q.y,dp(4),p);
        }
        p.setAlpha(255);
    }

    private void drawBottomBar(Canvas c) {
        float h=dp(70), y=getHeight()-h-dp(10), gap=dp(6), leftX=dp(8);
        String[] labels={"مود", "پوسته", "چالش", "۲ نفره", "آمار/ارسال"};
        for(int i=0;i<5;i++){
            float bw=(getWidth()-dp(16)-gap*4)/5f;
            RectF r=new RectF(leftX+i*(bw+gap),y,leftX+i*(bw+gap)+bw,y+h);
            p.setColor(i==3&&duelActive?Color.rgb(250,210,70):Color.rgb(30,30,30));
            c.drawRoundRect(r,dp(16),dp(16),p);
            text.setTextAlign(Paint.Align.CENTER); text.setTextSize(sp(10.5f));
            text.setColor(i==3&&duelActive?Color.BLACK:Color.WHITE);
            c.drawText(labels[i],r.centerX(),r.centerY()+dp(4),text);
        }
    }

    private void drawOverlays(Canvas c) {
        long now=System.currentTimeMillis();
        if (toastUntil>now) {
            float w=getWidth()*.82f; RectF r=new RectF((getWidth()-w)/2,getHeight()*.76f,(getWidth()+w)/2,getHeight()*.76f+dp(50));
            p.setColor(Color.argb(225,22,22,22)); c.drawRoundRect(r,dp(16),dp(16),p);
            text.setTextAlign(Paint.Align.CENTER); text.setTextSize(sp(13)); text.setColor(Color.WHITE);
            c.drawText(toast,r.centerX(),r.centerY()+dp(5),text);
        }
        if (!duelResult.isEmpty() && duelResultUntil>now) showCenterCard(c,"نبرد بیکاری",duelResult);
        if (menuModes) drawChoiceMenu(c,"مود بازی",MODE_FA,state.mode,true);
        if (menuSkins) drawChoiceMenu(c,"پوسته",SKIN_FA,state.skin,false);
        if (challengeOpen) drawChallengeCard(c);
        if (statsOpen) drawStats(c);
    }

    private void drawChoiceMenu(Canvas c,String title,String[] fa,String current,boolean modes) {
        RectF box=new RectF(dp(24),getHeight()*.18f,getWidth()-dp(24),getHeight()*.77f);
        p.setColor(Color.argb(245,250,247,240)); c.drawRoundRect(box,dp(24),dp(24),p);
        text.setColor(Color.BLACK); text.setTextSize(sp(21)); text.setTextAlign(Paint.Align.CENTER); c.drawText(title,box.centerX(),box.top+dp(42),text);
        int count=fa.length; float top=box.top+dp(70), row=(box.height()-dp(92))/count;
        for(int i=0;i<count;i++){
            String key=modes?MODES[i]:SKINS[i];
            RectF r=new RectF(box.left+dp(18),top+i*row,box.right-dp(18),top+(i+1)*row-dp(5));
            p.setColor(key.equals(current)?Color.rgb(245,206,72):Color.rgb(235,230,220)); c.drawRoundRect(r,dp(12),dp(12),p);
            text.setTextSize(sp(14)); c.drawText(fa[i],r.centerX(),r.centerY()+dp(5),text);
        }
    }

    private void drawChallengeCard(Canvas c) {
        RectF box=new RectF(dp(22),getHeight()*.25f,getWidth()-dp(22),getHeight()*.65f);
        p.setColor(Color.argb(248,252,249,242)); c.drawRoundRect(box,dp(24),dp(24),p);
        text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.BLACK); text.setTextSize(sp(21)); c.drawText(challenge.title,box.centerX(),box.top+dp(48),text);
        text.setTextSize(sp(15)); c.drawText(challenge.description,box.centerX(),box.top+dp(84),text);
        text.setTextSize(sp(12)); text.setColor(Color.DKGRAY); c.drawText("چالش هر روز عوض می‌شود",box.centerX(),box.top+dp(112),text);
        RectF btn=new RectF(box.left+dp(40),box.bottom-dp(78),box.right-dp(40),box.bottom-dp(26));
        p.setColor(Color.rgb(26,26,26)); c.drawRoundRect(btn,dp(16),dp(16),p); text.setColor(Color.WHITE); text.setTextSize(sp(14));
        c.drawText(challengeRunning?"در حال اجرا...":"شروع چالش",btn.centerX(),btn.centerY()+dp(5),text);
    }

    private void drawStats(Canvas c) {
        RectF box=new RectF(dp(20),getHeight()*.18f,getWidth()-dp(20),getHeight()*.72f);
        p.setColor(Color.argb(250,252,249,242)); c.drawRoundRect(box,dp(24),dp(24),p);
        text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.BLACK); text.setTextSize(sp(21)); c.drawText("کارنامه بیکاری",box.centerX(),box.top+dp(44),text);
        text.setTextSize(sp(15));
        c.drawText(String.format(Locale.US,"کل ضربه‌ها: %,d",state.totalTaps),box.centerX(),box.top+dp(92),text);
        c.drawText(String.format(Locale.US,"امروز: %,d",state.todayTaps),box.centerX(),box.top+dp(126),text);
        c.drawText("بهترین رگبار: "+state.bestStreak,box.centerX(),box.top+dp(160),text);
        c.drawText("برد دو نفره: "+state.duelWins,box.centerX(),box.top+dp(194),text);
        text.setTextSize(sp(14)); text.setColor(Color.rgb(120,70,20)); c.drawText(percentileJoke(),box.centerX(),box.top+dp(238),text);
        RectF btn=new RectF(box.left+dp(36),box.bottom-dp(76),box.right-dp(36),box.bottom-dp(24));
        p.setColor(Color.rgb(30,30,30)); c.drawRoundRect(btn,dp(16),dp(16),p); text.setColor(Color.WHITE); c.drawText("عکس رکورد رو بفرست",btn.centerX(),btn.centerY()+dp(5),text);
    }

    private void showCenterCard(Canvas c,String title,String body) {
        RectF box=new RectF(dp(30),getHeight()*.32f,getWidth()-dp(30),getHeight()*.52f);
        p.setColor(Color.argb(245,25,25,25)); c.drawRoundRect(box,dp(24),dp(24),p);
        text.setTextAlign(Paint.Align.CENTER); text.setColor(Color.WHITE); text.setTextSize(sp(20)); c.drawText(title,box.centerX(),box.top+dp(52),text);
        text.setTextSize(sp(15)); c.drawText(body,box.centerX(),box.top+dp(94),text);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction()!=MotionEvent.ACTION_DOWN) return true;
        float x=e.getX(), y=e.getY();

        if (menuModes) return handleChoice(x,y,true);
        if (menuSkins) return handleChoice(x,y,false);
        if (challengeOpen) {
            RectF box=new RectF(dp(22),getHeight()*.25f,getWidth()-dp(22),getHeight()*.65f);
            RectF btn=new RectF(box.left+dp(40),box.bottom-dp(78),box.right-dp(40),box.bottom-dp(26));
            if(btn.contains(x,y)){ startChallenge(); challengeOpen=false; }
            else challengeOpen=false;
            invalidate(); return true;
        }
        if (statsOpen) {
            RectF box=new RectF(dp(20),getHeight()*.18f,getWidth()-dp(20),getHeight()*.72f);
            RectF btn=new RectF(box.left+dp(36),box.bottom-dp(76),box.right-dp(36),box.bottom-dp(24));
            if(btn.contains(x,y)) host.shareSnapshot(shareText());
            else statsOpen=false;
            invalidate(); return true;
        }

        float bottomY=getHeight()-dp(80);
        if(y>=bottomY){
            float slot=getWidth()/5f; int i=Math.min(4,(int)(x/slot));
            if(i==0) menuModes=true;
            else if(i==1) menuSkins=true;
            else if(i==2) challengeOpen=true;
            else if(i==3) startDuel();
            else statsOpen=true;
            invalidate(); return true;
        }

        boolean hitLeft = dist(x,y,left.x,left.y) < left.radius*1.35f;
        boolean hitRight = dist(x,y,right.x,right.y) < right.radius*1.35f;
        if(!hitLeft&&!hitRight){
            // forgiving touch zones: left/right half in playfield
            if(y>getHeight()*.20f && y<getHeight()*.68f) hitLeft=x<getWidth()/2f; else return true;
        }
        tap(hitLeft?0:1);
        return true;
    }

    private boolean handleChoice(float x,float y,boolean modes) {
        RectF box=new RectF(dp(24),getHeight()*.18f,getWidth()-dp(24),getHeight()*.77f);
        String[] keys=modes?MODES:SKINS;
        float top=box.top+dp(70),row=(box.height()-dp(92))/keys.length;
        if(x>box.left&&x<box.right&&y>=top&&y<=top+row*keys.length){
            int index=Math.min(keys.length-1,(int)((y-top)/row));
            if(modes) state.setMode(keys[index]); else state.setSkin(keys[index]);
            showToast((modes?"مود: ":"پوسته: ")+(modes?MODE_FA[index]:SKIN_FA[index]),1200);
        }
        menuModes=false; menuSkins=false; invalidate(); return true;
    }

    private void tap(int side) {
        long now=System.currentTimeMillis();
        streak=(now-lastTapMs<430)?streak+1:1;
        lastTapMs=now;
        float chaos=Math.min(2.8f,1f+streak*.035f);
        float base=1.6f*chaos;
        if(state.mode.equals("ZEN")) base*=.65f;
        if(state.mode.equals("RAGE")) base*=1.5f;
        if(state.mode.equals("TURBO")) base*=2f;
        if(side==0) left.impulse((random.nextBoolean()?1:-1)*base); else right.impulse((random.nextBoolean()?1:-1)*base);
        if(streak>12) { left.impulse((random.nextFloat()-.5f)*.25f*chaos); right.impulse((random.nextFloat()-.5f)*.25f*chaos); }
        if(streak>22 || state.mode.equals("RAGE")) shake=Math.min(dp(22),shake+dp(2.2f));

        state.onTap(streak);
        if(duelActive){ if(side==0) duelLeft++; else duelRight++; }
        handleChallengeTap(side);
        tactile();
        secretEvents();
        checkAchievements();
        invalidate();
    }

    private void tactile() {
        if(state.mode.equals("OFFICE")) return;
        int amp=state.mode.equals("RAGE")?90:state.mode.equals("ZEN")?25:55;
        try { if(Build.VERSION.SDK_INT>=26) vibrator.vibrate(VibrationEffect.createOneShot(8,amp)); else vibrator.vibrate(8); } catch(Exception ignored){}
        if(tone!=null){
            int toneId= state.mode.equals("ZEN")?ToneGenerator.TONE_PROP_BEEP2:ToneGenerator.TONE_PROP_BEEP;
            try{ tone.startTone(toneId,25); }catch(Exception ignored){}
        }
    }

    private void playBonk(){ if(tone!=null) try{tone.startTone(ToneGenerator.TONE_PROP_NACK,45);}catch(Exception ignored){} }

    private void secretEvents() {
        long n=state.totalTaps;
        if(n==50){ flashFrames=9; showToast("۵۰ تا؟ تازه گرم شدی 😏",1600); }
        if(n==100){ left.impulse(8f); right.impulse(-8f); showToast("صدتا! کار و زندگی واقعاً تعطیله",1900); }
        if(n==500){ tiltX=random.nextBoolean()?8:-8; showToast("۵۰۰! جاذبه هم دیگه خسته شد",2000); }
        if(n==1000){ confetti(55); showToast("هزار تا! مدال بیکاری unlocked 🏆",2200); }
        if(n>0 && n%5000==0){ confetti(90); left.impulse(11); right.impulse(-11); showToast("این عدد رو جدی جدی زدی؟!",2000); }
    }

    private void checkAchievements() {
        for(int i=0;i<ACHIEVEMENTS.length;i++){
            long a=ACHIEVEMENTS[i];
            if(state.totalTaps>=a&&!state.achievementUnlocked(a)){
                state.unlockAchievement(a); showToast("🏅 "+ACHIEVEMENT_NAMES[i],2200); confetti(25); break;
            }
        }
    }

    private void confetti(int count) {
        int[] colors={Color.rgb(236,70,70),Color.rgb(245,195,55),Color.rgb(70,155,235),Color.rgb(80,190,125),Color.rgb(165,95,220)};
        for(int i=0;i<count;i++){
            Particle q=new Particle(); q.x=getWidth()/2f; q.y=getHeight()*.38f; q.vx=(random.nextFloat()-.5f)*dp(450); q.vy=-random.nextFloat()*dp(460)-dp(80); q.life=.8f+random.nextFloat()*1.4f; q.color=colors[i%colors.length]; particles.add(q);
        }
    }

    private void startDuel() {
        menuModes=menuSkins=statsOpen=challengeOpen=false;
        challengeRunning=false; duelActive=true; duelStarted=System.currentTimeMillis(); duelLeft=duelRight=0; duelResult="";
        showToast("۱۰ ثانیه! هر نفر یک سمت — برو!",1500);
    }

    private void finishDuel() {
        duelActive=false;
        if(duelLeft>duelRight){duelResult="چپ برد!  "+duelLeft+" - "+duelRight; state.addDuelWin();}
        else if(duelRight>duelLeft){duelResult="راست برد!  "+duelRight+" - "+duelLeft; state.addDuelWin();}
        else duelResult="مساوی! هر دوتون به یک اندازه بیکارید 😐";
        duelResultUntil=System.currentTimeMillis()+2600; confetti(40);
    }

    private void startChallenge() {
        challenge=DailyChallenge.today(); challengeRunning=true; challengeFailed=false; challengeProgress=0; lastChallengeSide=-1; challengeStarted=System.currentTimeMillis();
        showToast(challenge.description,1600);
    }

    private void handleChallengeTap(int side) {
        if(!challengeRunning) return;
        boolean ok=true;
        if(challenge.type==DailyChallenge.Type.LEFT_ONLY) ok=side==0;
        else if(challenge.type==DailyChallenge.Type.ALTERNATE && lastChallengeSide!=-1) ok=side!=lastChallengeSide;
        if(!ok){ challengeRunning=false; challengeFailed=true; showToast("ای بابا! قانون چالش رو شکستی 😵",1700); return; }
        challengeProgress++; lastChallengeSide=side;
        if(challengeProgress>=challenge.target){ challengeRunning=false; confetti(70); showToast("چالش امروز ترکید! ✅",2300); }
    }

    private String boredomLabel(){
        long n=state.todayTaps;
        if(n<20)return "یه کم بیکار";
        if(n<100)return "حوصله‌ت سر رفته";
        if(n<500)return "خیلی بیکار";
        if(n<2000)return "رسماً هیچ کاری نداری";
        return "استاد اعظم بیکاری";
    }

    private String percentileJoke(){
        long n=state.todayTaps;
        int pct=(int)Math.min(99,45+Math.log10(Math.max(1,n))*18);
        return "طبق آمار کاملاً الکی: از "+pct+"٪ مردم بیکارتری 😌";
    }

    private String shareText(){
        return "کارنامه بیکاری من 😂\nامروز: "+state.todayTaps+" ضربه\nکل: "+state.totalTaps+"\nبهترین رگبار: "+state.bestStreak+"\n"+percentileJoke()+"\n#بیکارم";
    }

    private void showToast(String s,long duration){toast=s; toastUntil=System.currentTimeMillis()+duration;}

    private void drawPill(Canvas c,float l,float t,float r,float h,String s,int color){
        RectF box=new RectF(l,t,r,t+h); p.setColor(color); c.drawRoundRect(box,h/2,h/2,p); text.setTextAlign(Paint.Align.CENTER); text.setTextSize(sp(11.5f)); text.setColor(Color.rgb(45,45,45)); c.drawText(s,box.centerX(),box.centerY()+dp(4),text);
    }

    private float dist(float x1,float y1,float x2,float y2){float dx=x1-x2,dy=y1-y2;return(float)Math.sqrt(dx*dx+dy*dy);}
    private float dp(float v){return v*density;}
    private float sp(float v){return v*getResources().getDisplayMetrics().scaledDensity;}

    @Override public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER) tiltX = tiltX*.82f + (-event.values[0])*.18f;
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {}
}
