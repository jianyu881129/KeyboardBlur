package com.miclaw.keyboardblur;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * 模糊效果核心实现 — 双引擎架构
 * 
 * 引擎策略：
 * - Android 12+（API 31+）：优先 Window.setBackgroundBlurRadius（系统级，简单高效）
 * - Android 11 及以下：使用 BlurOverlayView（Kawase GPU 模糊）
 * - 用户可手动强制选择引擎
 */
public class BlurHelper {

    private static final String TAG = "KeyboardBlur";

    // 用于标记已添加的 BlurOverlayView，避免重复添加
    private static final int OVERLAY_VIEW_ID = 0x7F001234;

    /**
     * 对输入法窗口应用模糊效果（双引擎自动选择）
     */
    public static void applyBlur(Context context, Window window) {
        if (window == null) return;

        BlurConfig config = BlurConfig.get(context);
        if (!config.isEnabled()) {
            removeBlur(window, context);
            return;
        }

        String engine = config.resolveEngine();

        if (BlurConfig.ENGINE_WINDOW.equals(engine) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyWindowBlur(context, window, config);
        } else if (BlurConfig.ENGINE_KAWASE.equals(engine)
                || (BlurConfig.ENGINE_AUTO.equals(engine) && Build.VERSION.SDK_INT < Build.VERSION_CODES.S)) {
            applyKawaseBlur(context, window, config);
        } else {
            // AUTO 模式在 12+ 走 window
            applyWindowBlur(context, window, config);
        }
    }

    // ==================== Window 模糊引擎（Android 12+） ====================

    /**
     * 使用系统 Window.setBackgroundBlurRadius 实现模糊
     */
    private static void applyWindowBlur(Context context, Window window, BlurConfig config) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        int blurRadius = config.getBlurRadius(context);

        // 添加模糊标记
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        window.setBackgroundBlurRadius(blurRadius);

        // 将 DecorView 背景设为透明，让模糊效果透出来
        View decorView = window.getDecorView();
        decorView.setBackgroundColor(Color.TRANSPARENT);

        // 应用遮罩层和圆角
        applyOverlayAndCorner(context, window, config);

        // 确保 Kawase overlay 被移除（如果之前用过）
        removeKawaseOverlay(window);
    }

    // ==================== Kawase GPU 模糊引擎 ====================

    /**
     * 使用 Kawase GPU 模糊视图实现模糊（兼容 Android 11 及以下）
     */
    private static void applyKawaseBlur(Context context, Window window, BlurConfig config) {
        // 移除 window 模糊标记
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setBackgroundBlurRadius(0);
        }

        View decorView = window.getDecorView();
        decorView.setBackgroundColor(Color.TRANSPARENT);

        // 获取或创建内容容器
        FrameLayout container = getOrCreateContainer(window);

        // 检查是否已有 overlay
        BlurOverlayView existingOverlay = container.findViewById(OVERLAY_VIEW_ID);
        if (existingOverlay != null) {
            // 更新参数
            updateKawaseOverlay(existingOverlay, config);
            return;
        }

        // 创建新的 BlurOverlayView
        BlurOverlayView overlayView = new BlurOverlayView(context);
        overlayView.setId(OVERLAY_VIEW_ID);

        // 设置模糊半径
        float blurRadius = config.getBlurRadius(context);
        overlayView.setBlurRadius(blurRadius);

        // 设置遮罩
        int overlayColor = config.getOverlayColor();
        int overlayAlpha = config.getOverlayAlphaInt();
        overlayView.setOverlayColor(overlayColor, overlayAlpha);

        // 设置高光
        overlayView.setGlossEnabled(config.isGlossEnabled());
        overlayView.setGlossAlpha(config.getGlossAlpha());
        overlayView.setGlossWidthDp(config.getGlossWidthDp());

        // 设置圆角
        if (config.isCornerEnabled()) {
            overlayView.setCornerRadius(config.getCornerRadiusDp());
        }

        // 绑定窗口用于 PixelCopy
        overlayView.attachWindow(window);

        // 添加到容器（底层）
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        container.addView(overlayView, 0, lp);

        // 开始连续渲染
        overlayView.startContinuousRendering();

        // 截取屏幕内容作为模糊背景
        overlayView.post(() -> overlayView.captureAndSetBackground());
    }

    /**
     * 更新已有的 Kawase overlay 参数
     */
    private static void updateKawaseOverlay(BlurOverlayView overlay, BlurConfig config) {
        Context context = overlay.getContext();
        float blurRadius = config.getBlurRadius(context);
        overlay.setBlurRadius(blurRadius);
        overlay.setOverlayColor(config.getOverlayColor(), config.getOverlayAlphaInt());
        overlay.setGlossEnabled(config.isGlossEnabled());
        overlay.setGlossAlpha(config.getGlossAlpha());
        overlay.setGlossWidthDp(config.getGlossWidthDp());
        if (config.isCornerEnabled()) {
            overlay.setCornerRadius(config.getCornerRadiusDp());
        }
    }

    /**
     * 获取内容容器，如果不存在则创建
     */
    private static FrameLayout getOrCreateContainer(Window window) {
        View decorView = window.getDecorView();
        if (decorView instanceof FrameLayout) {
            return (FrameLayout) decorView;
        }
        // 如果 decorView 不是 FrameLayout（极少见），包装一层
        ViewGroup parent = (ViewGroup) decorView;
        if (parent.getChildCount() > 0 && parent.getChildAt(0) instanceof FrameLayout) {
            return (FrameLayout) parent.getChildAt(0);
        }
        FrameLayout container = new FrameLayout(window.getContext());
        // 将原有子视图移到 container 中
        while (parent.getChildCount() > 0) {
            View child = parent.getChildAt(0);
            parent.removeView(child);
            container.addView(child, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        parent.addView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return container;
    }

    /**
     * 移除 Kawase overlay 视图
     */
    private static void removeKawaseOverlay(Window window) {
        if (window == null) return;
        View decorView = window.getDecorView();
        BlurOverlayView overlay = decorView.findViewById(OVERLAY_VIEW_ID);
        if (overlay != null) {
            overlay.release();
            ViewGroup parent = (ViewGroup) overlay.getParent();
            if (parent != null) {
                parent.removeView(overlay);
            }
        }
    }

    // ==================== 共用：遮罩和圆角 ====================

    /**
     * 应用遮罩层和圆角到 Window 背景（Window 引擎用）
     */
    private static void applyOverlayAndCorner(Context context, Window window, BlurConfig config) {
        int overlayAlpha = config.getOverlayAlpha();
        int dimAmount = config.getDimAmount();

        if (overlayAlpha > 0 || dimAmount > 0) {
            int overlayColor = Color.argb(overlayAlpha, 0, 0, 0);
            int dimColor = Color.argb(dimAmount, 0, 0, 0);
            int finalColor = blendColors(overlayColor, dimColor);

            if (config.isCornerEnabled()) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(finalColor);
                bg.setCornerRadius(dpToPx(context, config.getCornerRadiusDp()));
                window.setBackgroundDrawable(bg);
            } else {
                window.setBackgroundDrawable(new ColorDrawable(finalColor));
            }
        } else if (config.isCornerEnabled()) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.TRANSPARENT);
            bg.setCornerRadius(dpToPx(context, config.getCornerRadiusDp()));
            window.setBackgroundDrawable(bg);
        } else {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    // ==================== 移除模糊 ====================

    /**
     * 移除模糊效果，恢复默认
     */
    public static void removeBlur(Window window, Context context) {
        if (window == null) return;

        // 移除 Window 模糊标记（Android 12+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setBackgroundBlurRadius(0);
        }

        // 移除 Kawase overlay
        removeKawaseOverlay(window);

        // 恢复默认背景
        window.setBackgroundDrawable(null);
    }

    /**
     * 移除模糊效果（兼容旧接口，context 可为 null）
     */
    public static void removeBlur(Window window) {
        if (window == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.setBackgroundBlurRadius(0);
        }
        removeKawaseOverlay(window);
        window.setBackgroundDrawable(null);
    }

    // ==================== 工具方法 ====================

    /**
     * 获取实际的模糊半径值（根据屏幕方向）
     */
    public static int getBlurRadius(Context context, BlurConfig config) {
        return config.getBlurRadius(context);
    }

    /**
     * 获取当前使用的引擎名称（用于 UI 显示）
     */
    public static String getCurrentEngineName(Context context) {
        BlurConfig config = BlurConfig.get(context);
        String resolved = config.resolveEngine();
        switch (resolved) {
            case BlurConfig.ENGINE_WINDOW:
                return "系统级 (Window)";
            case BlurConfig.ENGINE_KAWASE:
                return "Kawase GPU";
            default:
                return "自动";
        }
    }

    private static int dpToPx(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * 混合两个 ARGB 颜色（简单 alpha 合成）
     */
    private static int blendColors(int color1, int color2) {
        int a1 = Color.alpha(color1);
        int r1 = Color.red(color1);
        int g1 = Color.green(color1);
        int b1 = Color.blue(color1);

        int a2 = Color.alpha(color2);
        int r2 = Color.red(color2);
        int g2 = Color.green(color2);
        int b2 = Color.blue(color2);

        int a = Math.min(255, a1 + a2);
        int r = (r1 * a1 + r2 * a2) / Math.max(a, 1);
        int g = (g1 * a1 + g2 * a2) / Math.max(a, 1);
        int b = (b1 * a1 + b2 * a2) / Math.max(a, 1);

        return Color.argb(a, r, g, b);
    }
}
