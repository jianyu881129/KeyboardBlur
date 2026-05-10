package com.miclaw.keyboardblur;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.PixelCopy;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/**
 * 模糊叠加视图。
 * 从 DoubaoBlurHook.kt 的 BlurOverlayView 翻译为 Java。
 *
 * 结构：
 * - 底层 KawaseBlurView（GPU 模糊）
 * - 半透明遮罩层
 * - 顶部高光渐变（LinearGradient）
 * - 圆角裁剪（clipPath）
 */
public class BlurOverlayView extends FrameLayout {

    private KawaseBlurView kawaseBlurView;
    private View overlayMaskView;
    private View glossView;
    private float cornerRadiusPx = 0f;
    private int overlayColor = Color.TRANSPARENT;
    private int overlayAlpha = 0;
    private boolean glossEnabled = false;
    private int glossAlpha = 30;
    private float glossWidthDp = 60f;
    private boolean clipEnabled = false;
    private WeakReference<Window> attachedWindowRef;
    private boolean isCapturing = false;
    private float density;

    // PixelCopy 回用 Bitmap
    private Bitmap captureBitmap;

    public BlurOverlayView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        init();
    }

    private void init() {
        setClipChildren(true);

        // 底层：Kawase GPU 模糊视图
        kawaseBlurView = new KawaseBlurView(getContext());
        addView(kawaseBlurView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 中间层：半透明遮罩
        overlayMaskView = new View(getContext());
        overlayMaskView.setBackgroundColor(Color.TRANSPARENT);
        addView(overlayMaskView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 顶层：高光渐变
        glossView = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (!glossEnabled) return;
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                float w = getWidth();
                float h = glossWidthDp * density;
                paint.setShader(new LinearGradient(
                        0, 0, 0, h,
                        Color.argb(glossAlpha, 255, 255, 255),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
            }
        };
        glossView.setWillNotDraw(false);
        addView(glossView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        updateClipPath();
    }

    /**
     * 设置模糊半径（传递给 KawaseBlurView）
     */
    public void setBlurRadius(float radius) {
        if (kawaseBlurView != null) {
            kawaseBlurView.setBlurRadius(radius);
            kawaseBlurView.requestBlurRender();
        }
    }

    /**
     * 开始连续渲染
     */
    public void startContinuousRendering() {
        if (kawaseBlurView != null) {
            kawaseBlurView.startContinuousRendering();
        }
    }

    /**
     * 停止连续渲染
     */
    public void stopContinuousRendering() {
        if (kawaseBlurView != null) {
            kawaseBlurView.stopContinuousRendering();
        }
    }

    /**
     * 设置遮罩颜色和透明度
     */
    public void setOverlayColor(int color, int alpha) {
        this.overlayColor = color;
        this.overlayAlpha = alpha;
        int colorWithAlpha = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        overlayMaskView.setBackgroundColor(colorWithAlpha);
    }

    /**
     * 设置高光参数
     */
    public void setGlossEnabled(boolean enabled) {
        this.glossEnabled = enabled;
        glossView.setVisibility(enabled ? VISIBLE : GONE);
        glossView.invalidate();
    }

    public void setGlossAlpha(int alpha) {
        this.glossAlpha = alpha;
        glossView.invalidate();
    }

    public void setGlossWidthDp(float widthDp) {
        this.glossWidthDp = widthDp;
        glossView.invalidate();
    }

    /**
     * 设置圆角
     */
    public void setCornerRadius(float radiusDp) {
        this.cornerRadiusPx = radiusDp * density;
        updateClipPath();
    }

    /**
     * 更新圆角裁剪
     */
    private void updateClipPath() {
        if (cornerRadiusPx > 0) {
            clipEnabled = true;
            setOutlineProvider(null);
            setClipToOutline(false);
            // 使用 outline 的方式需要 API 21+，这里使用 setOutlineProvider
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                    }
                });
                setClipToOutline(true);
            } else {
                // 降级：用 setLayerType + Paint clip 实现
                setLayerType(LAYER_TYPE_SOFTWARE, null);
                setOutlineProvider(null);
            }
        } else {
            clipEnabled = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setOutlineProvider(null);
                setClipToOutline(false);
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (clipEnabled && cornerRadiusPx > 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Path clipPath = new Path();
            clipPath.addRoundRect(0, 0, getWidth(), getHeight(),
                    cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);
            canvas.clipPath(clipPath);
        }
        super.dispatchDraw(canvas);
    }

    /**
     * 绑定窗口，用于 PixelCopy 截图
     */
    public void attachWindow(Window window) {
        this.attachedWindowRef = new WeakReference<>(window);
    }

    /**
     * 通过 PixelCopy 截取键盘区域屏幕内容作为模糊背景输入
     */
    public void captureAndSetBackground() {
        if (isCapturing) return;
        Window window = attachedWindowRef != null ? attachedWindowRef.get() : null;
        if (window == null) return;

        View decorView = window.getDecorView();
        int width = decorView.getWidth();
        int height = decorView.getHeight();

        if (width <= 0 || height <= 0) return;

        // 复用 Bitmap
        if (captureBitmap == null || captureBitmap.getWidth() != width || captureBitmap.getHeight() != height) {
            if (captureBitmap != null && !captureBitmap.isRecycled()) {
                captureBitmap.recycle();
            }
            captureBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        }

        isCapturing = true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PixelCopy.request(window, captureBitmap, result -> {
                    isCapturing = false;
                    if (result == PixelCopy.SUCCESS) {
                        kawaseBlurView.setBackgroundImage(captureBitmap);
                    }
                }, null);
            } else {
                // 降级方案：用 Canvas 截取
                try {
                    Canvas canvas = new Canvas(captureBitmap);
                    decorView.draw(canvas);
                    kawaseBlurView.setBackgroundImage(captureBitmap);
                } catch (Exception e) {
                    // 静默失败
                }
                isCapturing = false;
            }
        } catch (Exception e) {
            isCapturing = false;
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        stopContinuousRendering();
        if (captureBitmap != null && !captureBitmap.isRecycled()) {
            captureBitmap.recycle();
            captureBitmap = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopContinuousRendering();
    }
}
