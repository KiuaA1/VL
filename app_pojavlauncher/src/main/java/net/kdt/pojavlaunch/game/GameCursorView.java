package net.kdt.pojavlaunch.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.game.platform.Platform;
import net.kdt.pojavlaunch.game.platform.cursor.PlatformCursor;
import net.kdt.pojavlaunch.game.platform.cursor.PlatformCursorImplementor;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * A view that draws the platform cursor on the screen
 */
public class GameCursorView extends View implements PlatformCursorImplementor {
    private final Paint customCursorPaint = new Paint();
    private final Drawable cursorDrawable;
    private boolean noDraw = false;
    private float mouseScale = 1f;

    public GameCursorView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GameCursorView(Context context) {
        this(context, null);
    }

    public GameCursorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GameCursorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        int styleRes = R.drawable.ic_mouse_pointer;
        int bw = 36, bh = 54;
        android.graphics.drawable.Drawable loaded = null;
        try {
            // Custom imported cursor first
            java.io.File custom = new java.io.File(context.getFilesDir(), "vl_custom_cursor.png");
            if (custom.exists()) {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(custom.getAbsolutePath());
                if (bmp != null) {
                    loaded = new android.graphics.drawable.BitmapDrawable(context.getResources(), bmp);
                    bw = Math.max(16, bmp.getWidth());
                    bh = Math.max(16, bmp.getHeight());
                }
            }
            if (loaded == null && LauncherPreferences.DEFAULT_PREF != null) {
                String style = LauncherPreferences.DEFAULT_PREF.getString("vl_cursor_style", "default");
                if ("crosshair".equals(style)) {
                    styleRes = R.drawable.ic_cursor_crosshair;
                    bw = 32; bh = 32;
                } else if ("dot".equals(style)) {
                    styleRes = R.drawable.ic_cursor_dot;
                    bw = 16; bh = 16;
                } else if ("pointer".equals(style)) {
                    styleRes = R.drawable.ic_px_pointer_size;
                    bw = 40; bh = 40;
                }
            }
        } catch (Exception ignored) {}
        if (loaded != null) {
            cursorDrawable = loaded;
        } else {
            cursorDrawable = ContextCompat.getDrawable(context, styleRes);
            if (cursorDrawable == null) {
                cursorDrawable = ContextCompat.getDrawable(context, R.drawable.ic_mouse_pointer);
            }
        }
        assert cursorDrawable != null;
        cursorDrawable.setBounds(0, 0, bw, bh);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (noDraw) return;
        // Scale coordinates back to the full unresized screen size
        int dx = (int) (Platform.cursorX * ((GameView)getParent()).cursorRatioX);
        int dy = (int) (Platform.cursorY * ((GameView)getParent()).cursorRatioY);
        canvas.translate(dx, dy);
        PlatformCursor cursor = Platform.getCursor();
        canvas.scale(mouseScale, mouseScale);
        if (cursor == null) {
            cursorDrawable.draw(canvas);
        } else {
            canvas.drawBitmap(cursor.bitmap, -cursor.hotX, -cursor.hotY, customCursorPaint);
        }
    }

    @Override
    public void onCursorPosition() {
        if (!noDraw) post(this::invalidate);
    }

    @Override
    public void onCursorChanged() {
        post(this::invalidate);
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        noDraw = isGrabbing;
        invalidate();
    }

    public void setCursorScale(float scale) {
        this.mouseScale = scale;
    }
}
