package ru.dndmemory.ui.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import androidx.core.graphics.PathParser;
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
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        iconPaint.setColor(Color.WHITE);
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

    private int markerColor(@NonNull MapMarkerDto marker) {
        if ("LOCATION".equalsIgnoreCase(marker.objectType)) return 0xFF0F9D76;
        if ("ITEM".equalsIgnoreCase(marker.objectType)) return 0xFFB9770F;
        return 0xFF2F6BFF;
    }

    private void drawMarkerIcon(@NonNull Canvas canvas, @NonNull MapMarkerDto marker,
                                float x, float y, float radius) {
        String data;
        if ("LOCATION".equalsIgnoreCase(marker.objectType)) {
            data = ru.dndmemory.ui.common.TypeIcons.pathFor("LOCATION", marker.locationType);
        } else if ("ITEM".equalsIgnoreCase(marker.objectType)) {
            data = ru.dndmemory.ui.common.TypeIcons.pathFor("ITEM", marker.itemType);
        } else {
            data = "M12 4.4a3.4 3.4 0 1 0 0 6.8a3.4 3.4 0 1 0 0-6.8M5 20.4c0-3.4 3.1-6 7-6s7 2.6 7 6";
        }
        if (data == null) return;
        Path path = PathParser.createPathFromPathData(data);
        if (path == null) return;
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        float size = radius * 1.15f;
        matrix.setScale(size / 24f, size / 24f);
        matrix.postTranslate(x - size / 2f, y - size / 2f);
        path.transform(matrix);
        iconPaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density * (size / (24f * 1.8f)));
        canvas.drawPath(path, iconPaint);
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
        float density = getResources().getDisplayMetrics().density;
        float radius = 18 * density;
        for (MapMarkerDto marker : markers) {
            float x = (float) marker.x * w;
            float y = (float) marker.y * h;
            paint.setColor(markerColor(marker));
            canvas.drawCircle(x, y, radius, paint);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * density);
            canvas.drawCircle(x, y, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            drawMarkerIcon(canvas, marker, x, y, radius);
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
