/* Square Glass for TIDAL. GPL-3.0-or-later. */
package dev.nino.squareglass;

import android.content.Context;
import android.content.ContextWrapper;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;
import android.view.ViewGroup;
import java.util.WeakHashMap;

/** Horizontal row gestures enter the row's native context action pipeline. */
public final class SquareGestures implements View.OnTouchListener, View.OnAttachStateChangeListener {
    private static final WeakHashMap<View, SquareGestures> bindings = new WeakHashMap<>();
    private static Activity requestingActivity;
    private static int requestingAction;
    private float downX, downY;
    private boolean dragging, vertical;
    private boolean armed;
    private ViewGroup surface;
    private int previewAction;
    private float restingX;
    private Preview preview;

    private final int slop;
    private SquareGestures(View row) { slop = ViewConfiguration.get(row.getContext()).getScaledTouchSlop(); }
    public static void bind(View row) {
        if (bindings.containsKey(row)) return;
        SquareGestures binding = new SquareGestures(row); bindings.put(row, binding);
        row.setOnTouchListener(binding); row.addOnAttachStateChangeListener(binding);
    }
    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper && !(context instanceof Activity)) context = ((ContextWrapper)context).getBaseContext();
        return context instanceof Activity ? (Activity)context : null;
    }
    /** Only a menu synchronously opened by this row's native action may consume it. */
    public static int request(Activity activity) { return activity == requestingActivity ? requestingAction : 0; }
    public static String label(int action) {
        return action == 1 ? "Play next" : action == 2 ? "Add to queue" : action == 3 ? "Favorite" : "";
    }
    @Override public boolean onTouch(View row, MotionEvent event) {
        if (!row.isEnabled() || !row.isLongClickable() || !row.hasOnClickListeners()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ProbeDiagnostics.gestureStarts++;
                reset(row, false); restingX = row.getTranslationX(); downX = event.getRawX(); downY = event.getRawY(); vertical = false; return false;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                if (!dragging && Math.abs(dy) > slop && Math.abs(dy) >= Math.abs(dx)) vertical = true;
                if (vertical || (!dragging && Math.abs(dx) <= slop * 2)) return false;
                int action = SquareSettings.integer(dx > 0 ? "swipeRight" : "swipeLeft", dx > 0 ? 1 : 2, 0, 3);
                if (action == 0) {
                    if (dragging) { reset(row, true); vertical = true; return true; }
                    return false;
                }
                if (!dragging) {
                    if (!(row.getParent() instanceof ViewGroup)) return false;
                    dragging = true; row.cancelLongPress(); row.setPressed(false);
                    surface = (ViewGroup)row.getParent(); preview = new Preview(row);
                    surface.getOverlay().add(preview);
                }
                if (row.getParent() != null) row.getParent().requestDisallowInterceptTouchEvent(true);
                float progress = Math.min(1f, Math.abs(dx) / threshold(row));
                boolean ready = progress >= 1f;
                if (ready && (!armed || action != previewAction)) row.performHapticFeedback(HapticFeedbackConstants.GESTURE_START);
                armed = ready; previewAction = action;
                float limit=threshold(row);
                float travel=Math.abs(dx)<=limit ? Math.abs(dx) : limit+(Math.abs(dx)-limit)*.32f;
                row.setTranslationX(restingX+Math.signum(dx)*Math.min(travel,row.getWidth()*.45f));
                preview.update(row,action,progress);
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) return false;
                float distance = event.getRawX() - downX;
                boolean commit = Math.abs(distance) >= threshold(row);
                int chosen = SquareSettings.integer(distance > 0 ? "swipeRight" : "swipeLeft", distance > 0 ? 1 : 2, 0, 3);
                reset(row, true);
                if (commit && chosen != 0) {
                    ProbeDiagnostics.gestureCommits++;
                    requestingActivity = activity(row.getContext()); requestingAction = chosen;
                    try {
                        // Collection rows expose their context actions through Options;
                        // their long-click listener can decline the event.
                        if (openNativeActions(row)) ProbeDiagnostics.gestureRequests++;
                        else Toast.makeText(row.getContext(), "Unavailable for this track", Toast.LENGTH_SHORT).show();
                    } finally { requestingActivity = null; requestingAction = 0; }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                boolean consumed = dragging; reset(row, true); return consumed;
            case MotionEvent.ACTION_POINTER_DOWN:
                boolean active = dragging; reset(row, false); vertical = true; return active;
            default: return dragging;
        }
    }
    private static boolean openNativeActions(View row) {
        if (row.performLongClick()) return true;
        View options = row.findViewById(0x7f0b04de);
        return options != null && options.getVisibility() == View.VISIBLE
                && options.isEnabled() && options.hasOnClickListeners() && options.performClick();
    }
    private float threshold(View row) { return Math.max(72*row.getResources().getDisplayMetrics().density, row.getWidth() * .26f); }
    private void clear(View row) {
        row.setTranslationX(restingX);
        if(surface!=null && preview!=null) surface.getOverlay().remove(preview);
        preview=null; surface=null;
    }
    private void reset(View row, boolean animate) {
        LiquidViewMotion.cancel(row,"swipeReturn");
        boolean wasDragging=dragging;
        dragging=false; armed=false; previewAction=0;
        if(wasDragging && animate && SquareSettings.motion()) {
            LiquidViewMotion.animate(row,"swipeReturn",row.getTranslationX(),restingX,false,value -> {
                row.setTranslationX(value);
                if(preview!=null) preview.update(row,preview.action,preview.progress);
            },() -> clear(row));
        } else if(wasDragging || preview!=null) clear(row);
        if(row.getParent()!=null) row.getParent().requestDisallowInterceptTouchEvent(false);
    }
    @Override public void onViewAttachedToWindow(View view) {}
    @Override public void onViewDetachedFromWindow(View view) { reset(view,false); }
    /** Paint only the exposed edge. Native row contents, ripple and handlers stay intact. */
    private static final class Preview extends Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path icon=new Path();
        final float density,textScale;
        int action; float progress,travel;
        Preview(View row) {
            density=row.getResources().getDisplayMetrics().density;
            textScale=row.getResources().getDisplayMetrics().scaledDensity;
        }
        void update(View row,int action,float progress) {
            this.action=action; this.progress=progress; travel=row.getTranslationX();
            ((Drawable)this).setBounds(row.getLeft(),row.getTop(),row.getRight(),row.getBottom());
            ((Drawable)this).invalidateSelf();
        }
        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds=((Drawable)this).getBounds();
            float width=Math.abs(travel); if(width<1) return;
            float left=travel>0?bounds.left:bounds.right-width;
            float right=travel>0?bounds.left+width:bounds.right;
            canvas.save(); canvas.clipRect(left,bounds.top,right,bounds.bottom);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(action==1?0xff8054d9:action==2?0xffc86c0b:0xffcf3558);
            float cornerRadius = 14 * density;
            canvas.drawRoundRect(left, bounds.top, right, bounds.bottom, cornerRadius, cornerRadius, paint);
            float cx=(left+right)/2,cy=bounds.exactCenterY();
            canvas.save(); canvas.translate(cx,cy-7*density);
            float scale=density*(.88f+.12f*Math.min(1,progress)); canvas.scale(scale,scale);
            paint.setColor(0xffffffff); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); icon.reset();
            if(action==3) {
                icon.moveTo(0,9); icon.cubicTo(-17,-1,-8,-14,0,-7); icon.cubicTo(8,-14,17,-1,0,9);
            } else {
                icon.moveTo(-11,-8); icon.lineTo(8,-8); icon.moveTo(-11,-1); icon.lineTo(3,-1);
                icon.moveTo(-11,6); icon.lineTo(-4,6);
                if(action==1) { icon.moveTo(4,3); icon.lineTo(12,8); icon.lineTo(4,13); icon.close(); }
                else { icon.moveTo(8,3); icon.lineTo(8,13); icon.moveTo(3,8); icon.lineTo(13,8); }
            }
            canvas.drawPath(icon,paint); canvas.restore();
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(11*textScale);
            canvas.drawText(label(action),cx,cy+22*density,paint); canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
