package com.xiaoxiao.pet;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import java.util.ArrayDeque;

/** A small speech stack. Children are sentences, oldest first; never four visible cards. */
final class PetBubbles extends ViewGroup {
    final ArrayDeque<Runnable> pending = new ArrayDeque<>();
    boolean above = true, retiring, disposed, animateLayout;
    float tailX;
    int heightLimit = Integer.MAX_VALUE;
    Runnable changed;

    PetBubbles(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    int dp(int value) { return Apple.dp(getContext(), value); }
    TextView latest() { return getChildCount() == 0 ? null : (TextView)getChildAt(getChildCount()-1); }
    void append(String text, Runnable displayed) {
        if (disposed) return;
        pending.add(() -> addSentence(text, displayed));
        drain();
    }
    void drain() {
        if (disposed || retiring || pending.isEmpty()) return;
        if (getChildCount() < 3) { pending.remove().run(); drain(); return; }
        retiring = true;
        View oldest = getChildAt(0);
        oldest.animate().cancel();
        Runnable finish = () -> {
            if (disposed) return;
            removeView(oldest);
            retiring = false;
            drain();
        };
        if (!UiMotion.enabled()) { finish.run(); return; }
        oldest.animate().alpha(0).translationY(above ? -dp(12) : dp(12))
                .scaleX(.94f).scaleY(.94f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(finish).start();
    }
    void addSentence(String text, Runnable displayed) {
        if (latest() != null) latest().setOnClickListener(null);
        TextView card = Apple.label(getContext(), text, 15, false);
        card.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        card.setLineSpacing(dp(2), 1f);
        card.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED);
        card.setPadding(dp(23), dp(18), dp(23), dp(18));
        card.setLayerType(View.LAYER_TYPE_SOFTWARE, null); // Soft shadow also on API 26.
        card.setBackground(new Surface(getContext()));
        card.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(card, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        animateLayout = UiMotion.enabled();
        if (animateLayout) card.setAlpha(0);
        if (changed != null) changed.run();
        // Receipt follows insertion, including the delayed eviction path.
        if (displayed != null) displayed.run();
    }
    void anchor(boolean above, float tailX) {
        this.above = above;
        this.tailX = tailX;
        requestLayout();
    }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec), height = 0;
        int count = getChildCount();
        for (int i=0; i<count; i++) {
            TextView card = (TextView)getChildAt(i);
            card.measure(MeasureSpec.makeMeasureSpec(Math.max(1,width-dp(12)), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            height += card.getMeasuredHeight();
        }
        // Older cards can leave sooner on a short screen / very large accessibility font.
        while (height > heightLimit && getChildCount() > 1 && !retiring) {
            View oldest = getChildAt(0);
            height -= oldest.getMeasuredHeight();
            oldest.animate().cancel();
            removeViewInLayout(oldest);
        }
        setMeasuredDimension(width, Math.min(height, heightLimit));
    }
    @Override protected void onLayout(boolean changedBounds, int l, int t, int r, int b) {
        int y = above ? getMeasuredHeight() : 0;
        for (int i=getChildCount()-1; i>=0; i--) {
            TextView card = (TextView)getChildAt(i);
            int age = getChildCount()-1-i, h = card.getMeasuredHeight(), w = card.getMeasuredWidth();
            int x = Math.max(dp(6), Math.min(getWidth()-w-dp(6), Math.round(tailX-w/2f)));
            int top = above ? y-h : y;
            card.layout(x, top, x+w, top+h);
            Surface surface = (Surface)card.getBackground();
            surface.tail = age==0;
            surface.above = above;
            surface.tip = tailX-x;
            surface.invalidateSelf();
            if (!(retiring && i==0)) {
                float opacity = age==0 ? 1f : age==1 ? .90f : .72f;
                float scale = age==0 ? 1f : age==1 ? .98f : .95f;
                card.setPivotX(Math.max(0,Math.min(w,tailX-x)));
                card.setPivotY(above ? h : 0);
                if (animateLayout) {
                    card.animate().cancel();
                    int shift = latest().getMeasuredHeight();
                    card.setTranslationY((above ? 1 : -1)*(age==0 ? dp(12) : shift));
                    if (age==0) { card.setScaleX(.92f); card.setScaleY(.92f); }
                    card.animate().alpha(opacity).scaleX(scale).scaleY(scale).translationY(0)
                            .setDuration(age==0 ? 360 : 300)
                            .setInterpolator(age==0 ? new OvershootInterpolator(.7f) : new DecelerateInterpolator())
                            .start();
                } else {
                    // Do not reset an in-flight animation on a position-only layout.
                    if (!UiMotion.enabled()) { card.setAlpha(opacity); card.setScaleX(scale); card.setScaleY(scale); card.setTranslationY(0); }
                }
            }
            y += above ? -h : h;
        }
        animateLayout = false;
    }
    void fadeOut(Runnable done) {
        if (disposed) return;
        if (!UiMotion.enabled()) { done.run(); return; }
        animate().alpha(0).translationY(above ? -dp(8) : dp(8)).setDuration(240).withEndAction(done).start();
    }
    void dispose() {
        disposed = true;
        pending.clear();
        animate().cancel();
        for (int i=0; i<getChildCount(); i++) getChildAt(i).animate().cancel();
        changed = null;
    }
    static final class Surface extends Drawable {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        final float density;
        boolean tail, above=true;
        float tip;
        Surface(Context context) { density=context.getResources().getDisplayMetrics().density; }
        float d(float n) { return n*density; }
        @Override public void draw(Canvas canvas) {
            Rect bounds=getBounds();
            RectF body=new RectF(bounds.left+d(7),bounds.top+d(8),bounds.right-d(7),bounds.bottom-d(8));
            path.reset();
            path.addRoundRect(body,d(18),d(18),Path.Direction.CW);
            if(tail) {
                float x=Math.max(body.left+d(20),Math.min(body.right-d(20),tip));
                Path tongue=new Path();
                if(above) { tongue.moveTo(x-d(7),body.bottom-d(2));tongue.quadTo(x-d(3),body.bottom+d(7),x+d(3),body.bottom+d(7));tongue.quadTo(x+d(2),body.bottom+d(2),x+d(9),body.bottom-d(2)); }
                else { tongue.moveTo(x-d(7),body.top+d(2));tongue.quadTo(x-d(3),body.top-d(7),x+d(3),body.top-d(7));tongue.quadTo(x+d(2),body.top-d(2),x+d(9),body.top+d(2)); }
                tongue.close();path.op(tongue,Path.Op.UNION);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0,body.top,0,body.bottom,Apple.dark?0xff373b48:0xffffffff,Apple.dark?0xff282c38:0xffedf4ff,Shader.TileMode.CLAMP));
            paint.setShadowLayer(d(5),0,d(2),Apple.dark?0x55000000:0x28384663);
            canvas.drawPath(path,paint);
            paint.clearShadowLayer();paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1));paint.setColor(Apple.dark?0xff50586b:0xffd4e2f4);canvas.drawPath(path,paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
