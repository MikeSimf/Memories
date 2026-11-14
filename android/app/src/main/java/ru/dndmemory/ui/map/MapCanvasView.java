package ru.dndmemory.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.data.remote.dto.MapMarkerDto;

/** Просмотр карты с зумом, перетаскиванием и метками (только чтение). */
public class MapCanvasView extends View {

    public interface OnMarkerTap {
        void onTap(@NonNull MapMarkerDto marker);
    }

    private Bitmap bitmap;
    private final List<MapMarkerDto> markers = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private float scale = 1f;
    private float minScale = 1f;
    private float tx;
    private float ty;
    private float lastX;
    private float lastY;
    private boolean dragging;
    private OnMarkerTap listener;

    public MapCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setTextSize(28);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scale = Math.max(minScale, Math.min(6f, scale * detector.getScaleFactor()));
                invalidate();
                return true;
            }
        });
    }

    public void setListener(OnMarkerTap listener) { this.listener = listener; }

    public void setBitmap(@Nullable Bitmap bitmap) {
        this.bitmap = bitmap;
        invalidate();
    }

    public void setMarkers(@NonNull List<MapMarkerDto> items) {
        markers.clear();
        markers.addAll(items);
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        minScale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
        if (scale < minScale) scale = minScale;
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;
        if (tx == 0 && ty == 0) {
            tx = (getWidth() - w) / 2f;
            ty = (getHeight() - h) / 2f;
        }
        canvas.save();
        canvas.translate(tx, ty);
        canvas.drawBitmap(bitmap, null, new RectF(0, 0, w, h), null);
        paint.setStyle(Paint.Style.FILL);
        for (MapMarkerDto marker : markers) {
            float x = (float) marker.x * w;
            float y = (float) marker.y * h;
            paint.setColor(0xFFC46A2A);
            canvas.drawCircle(x, y, 18, paint);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            canvas.drawCircle(x, y, 18, paint);
            paint.setStyle(Paint.Style.FILL);
            String label = marker.name == null || marker.name.isEmpty() ? "?" : marker.name.substring(0, 1).toUpperCase();
            canvas.drawText(label, x, y + 10, text);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX(); lastY = event.getY(); dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging && event.getPointerCount() == 1) {
                    tx += event.getX() - lastX; ty += event.getY() - lastY;
                    lastX = event.getX(); lastY = event.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                if (event.getPointerCount() == 1 && bitmap != null && listener != null) {
                    float w = bitmap.getWidth() * scale;
                    float h = bitmap.getHeight() * scale;
                    MapMarkerDto best = null;
                    float bestDistance = 48 * getResources().getDisplayMetrics().density;
                    for (MapMarkerDto marker : markers) {
                        float x = tx + (float) marker.x * w;
                        float y = ty + (float) marker.y * h;
                        float dx = event.getX() - x, dy = event.getY() - y;
                        float d = (float) Math.sqrt(dx * dx + dy * dy);
                        if (d < bestDistance) { bestDistance = d; best = marker; }
                    }
                    if (best != null) listener.onTap(best);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}
