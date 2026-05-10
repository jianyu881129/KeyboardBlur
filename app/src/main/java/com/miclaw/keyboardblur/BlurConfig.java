package com.miclaw.keyboardblur;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

/**
 * 模糊配置管理
 * 
 * 所有配置项通过 SharedPreferences 存储，
 * Hook 端通过 XSharedPreferences 跨进程读取。
 */
public class BlurConfig {

    private static final String PREF_NAME = "keyboard_blur_config";

    // ---- 原有配置项 ----
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_BLUR_PORTRAIT = "blur_portrait_radius";
    private static final String KEY_BLUR_LANDSCAPE = "blur_landscape_radius";
    private static final String KEY_CORNER_ENABLED = "corner_enabled";
    private static final String KEY_CORNER_RADIUS = "corner_radius_dp";
    private static final String KEY_OVERLAY_ALPHA = "overlay_alpha";
    private static final String KEY_DIM_AMOUNT = "dim_amount";

    // ---- 新增配置项 ----
    private static final String KEY_BLUR_ENGINE = "blur_engine";
    private static final String KEY_BLUR_PASS_COUNT = "blur_pass_count";
    private static final String KEY_BLUR_SATURATION = "blur_saturation";
    private static final String KEY_BLUR_BRIGHTNESS = "blur_brightness";
    private static final String KEY_GLOSS_ENABLED = "gloss_enabled";
    private static final String KEY_GLOSS_ALPHA = "gloss_alpha";
    private static final String KEY_GLOSS_WIDTH_DP = "gloss_width_dp";
    private static final String KEY_OVERLAY_COLOR = "overlay_color";
    private static final String KEY_OVERLAY_ALPHA_NEW = "overlay_alpha_new";

    // ---- 原有默认值 ----
    private static final boolean DEFAULT_ENABLED = false;
    private static final int DEFAULT_BLUR_PORTRAIT = 20;
    private static final int DEFAULT_BLUR_LANDSCAPE = 5;
    private static final boolean DEFAULT_CORNER_ENABLED = false;
    private static final float DEFAULT_CORNER_RADIUS = 50f;
    private static final int DEFAULT_OVERLAY_ALPHA = 0;
    private static final int DEFAULT_DIM_AMOUNT = 0;

    // ---- 新增默认值 ----
    public static final String ENGINE_AUTO = "auto";
    public static final String ENGINE_WINDOW = "window";
    public static final String ENGINE_KAWASE = "kawase";
    private static final String DEFAULT_BLUR_ENGINE = ENGINE_AUTO;
    private static final int DEFAULT_BLUR_PASS_COUNT = 3;
    private static final float DEFAULT_BLUR_SATURATION = 1.0f;
    private static final float DEFAULT_BLUR_BRIGHTNESS = 1.0f;
    private static final boolean DEFAULT_GLOSS_ENABLED = false;
    private static final int DEFAULT_GLOSS_ALPHA = 30;
    private static final float DEFAULT_GLOSS_WIDTH_DP = 60f;
    private static final int DEFAULT_OVERLAY_COLOR_NEW = 0xFF000000;
    private static final int DEFAULT_OVERLAY_ALPHA_NEW = 0;

    private final SharedPreferences prefs;

    private BlurConfig(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /**
     * 获取实例（Settings 用）
     */
    public static BlurConfig get(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return new BlurConfig(prefs);
    }

    /**
     * 获取实例（Hook 端用，接收外部 SharedPreferences）
     */
    public static BlurConfig from(SharedPreferences prefs) {
        return new BlurConfig(prefs);
    }

    // ======== 原有 Getters ========

    public boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }

    /**
     * 根据屏幕方向返回对应的模糊半径
     */
    public int getBlurRadius(Context context) {
        boolean isLandscape = context.getResources().getConfiguration()
                .orientation == Configuration.ORIENTATION_LANDSCAPE;
        int radius = isLandscape
                ? prefs.getInt(KEY_BLUR_LANDSCAPE, DEFAULT_BLUR_LANDSCAPE)
                : prefs.getInt(KEY_BLUR_PORTRAIT, DEFAULT_BLUR_PORTRAIT);
        // Android setBackgroundBlurRadius 接受 0-250
        return Math.max(0, Math.min(250, radius));
    }

    public int getBlurPortraitRadius() {
        return prefs.getInt(KEY_BLUR_PORTRAIT, DEFAULT_BLUR_PORTRAIT);
    }

    public int getBlurLandscapeRadius() {
        return prefs.getInt(KEY_BLUR_LANDSCAPE, DEFAULT_BLUR_LANDSCAPE);
    }

    public boolean isCornerEnabled() {
        return prefs.getBoolean(KEY_CORNER_ENABLED, DEFAULT_CORNER_ENABLED);
    }

    public float getCornerRadiusDp() {
        return prefs.getFloat(KEY_CORNER_RADIUS, DEFAULT_CORNER_RADIUS);
    }

    /**
     * 遮罩透明度 0-255（配置存的是 0-100 百分比）
     */
    public int getOverlayAlpha() {
        int percent = prefs.getInt(KEY_OVERLAY_ALPHA, DEFAULT_OVERLAY_ALPHA);
        return Math.round(percent * 255f / 100f);
    }

    /**
     * 背景变暗量 0-255（配置存的是 0-100 百分比）
     */
    public int getDimAmount() {
        int percent = prefs.getInt(KEY_DIM_AMOUNT, DEFAULT_DIM_AMOUNT);
        return Math.round(percent * 255f / 100f);
    }

    // ======== 新增 Getters ========

    /**
     * 获取当前模糊引擎类型
     * @return ENGINE_AUTO / ENGINE_WINDOW / ENGINE_KAWASE
     */
    public String getBlurEngine() {
        String engine = prefs.getString(KEY_BLUR_ENGINE, DEFAULT_BLUR_ENGINE);
        if (engine == null) engine = DEFAULT_BLUR_ENGINE;
        // 验证值合法性
        if (!ENGINE_AUTO.equals(engine) && !ENGINE_WINDOW.equals(engine) && !ENGINE_KAWASE.equals(engine)) {
            engine = DEFAULT_BLUR_ENGINE;
        }
        return engine;
    }

    /**
     * 根据引擎配置和系统版本，解析实际应使用的引擎
     */
    public String resolveEngine() {
        String engine = getBlurEngine();
        if (ENGINE_AUTO.equals(engine)) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? ENGINE_WINDOW : ENGINE_KAWASE;
        }
        return engine;
    }

    /**
     * 是否应该使用 Kawase GPU 模糊引擎
     */
    public boolean shouldUseKawase() {
        return ENGINE_KAWASE.equals(resolveEngine());
    }

    /**
     * 是否应该使用系统 Window 模糊引擎
     */
    public boolean shouldUseWindow() {
        return ENGINE_WINDOW.equals(resolveEngine());
    }

    /**
     * 获取模糊 pass 次数 1-6
     */
    public int getBlurPassCount() {
        int count = prefs.getInt(KEY_BLUR_PASS_COUNT, DEFAULT_BLUR_PASS_COUNT);
        return Math.max(1, Math.min(6, count));
    }

    /**
     * 获取饱和度增强 0-2
     */
    public float getBlurSaturation() {
        float sat = prefs.getFloat(KEY_BLUR_SATURATION, DEFAULT_BLUR_SATURATION);
        return Math.max(0f, Math.min(2f, sat));
    }

    /**
     * 获取亮度调整 0.5-1.5
     */
    public float getBlurBrightness() {
        float bright = prefs.getFloat(KEY_BLUR_BRIGHTNESS, DEFAULT_BLUR_BRIGHTNESS);
        return Math.max(0.5f, Math.min(1.5f, bright));
    }

    /**
     * 是否启用顶部高光
     */
    public boolean isGlossEnabled() {
        return prefs.getBoolean(KEY_GLOSS_ENABLED, DEFAULT_GLOSS_ENABLED);
    }

    /**
     * 高光透明度 0-100
     */
    public int getGlossAlpha() {
        int alpha = prefs.getInt(KEY_GLOSS_ALPHA, DEFAULT_GLOSS_ALPHA);
        return Math.max(0, Math.min(100, alpha));
    }

    /**
     * 高光宽度 dp
     */
    public float getGlossWidthDp() {
        float width = prefs.getFloat(KEY_GLOSS_WIDTH_DP, DEFAULT_GLOSS_WIDTH_DP);
        return Math.max(10f, Math.min(200f, width));
    }

    /**
     * 遮罩颜色（带 alpha）
     */
    public int getOverlayColor() {
        return prefs.getInt(KEY_OVERLAY_COLOR, DEFAULT_OVERLAY_COLOR_NEW);
    }

    /**
     * 遮罩透明度（新配置项）0-100 百分比
     */
    public int getOverlayAlphaPercent() {
        return prefs.getInt(KEY_OVERLAY_ALPHA_NEW, DEFAULT_OVERLAY_ALPHA_NEW);
    }

    /**
     * 遮罩透明度转换为 0-255
     */
    public int getOverlayAlphaInt() {
        int percent = getOverlayAlphaPercent();
        return Math.round(percent * 255f / 100f);
    }

    // ======== 原有 Setters ========

    public void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public void setBlurPortraitRadius(int radius) {
        prefs.edit().putInt(KEY_BLUR_PORTRAIT, radius).apply();
    }

    public void setBlurLandscapeRadius(int radius) {
        prefs.edit().putInt(KEY_BLUR_LANDSCAPE, radius).apply();
    }

    public void setCornerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CORNER_ENABLED, enabled).apply();
    }

    public void setCornerRadiusDp(float radiusDp) {
        prefs.edit().putFloat(KEY_CORNER_RADIUS, radiusDp).apply();
    }

    public void setOverlayAlpha(int percent) {
        prefs.edit().putInt(KEY_OVERLAY_ALPHA, percent).apply();
    }

    public void setDimAmount(int percent) {
        prefs.edit().putInt(KEY_DIM_AMOUNT, percent).apply();
    }

    // ======== 新增 Setters ========

    public void setBlurEngine(String engine) {
        prefs.edit().putString(KEY_BLUR_ENGINE, engine).apply();
    }

    public void setBlurPassCount(int count) {
        prefs.edit().putInt(KEY_BLUR_PASS_COUNT, Math.max(1, Math.min(6, count))).apply();
    }

    public void setBlurSaturation(float saturation) {
        prefs.edit().putFloat(KEY_BLUR_SATURATION, Math.max(0f, Math.min(2f, saturation))).apply();
    }

    public void setBlurBrightness(float brightness) {
        prefs.edit().putFloat(KEY_BLUR_BRIGHTNESS, Math.max(0.5f, Math.min(1.5f, brightness))).apply();
    }

    public void setGlossEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GLOSS_ENABLED, enabled).apply();
    }

    public void setGlossAlpha(int alpha) {
        prefs.edit().putInt(KEY_GLOSS_ALPHA, Math.max(0, Math.min(100, alpha))).apply();
    }

    public void setGlossWidthDp(float widthDp) {
        prefs.edit().putFloat(KEY_GLOSS_WIDTH_DP, Math.max(10f, Math.min(200f, widthDp))).apply();
    }

    public void setOverlayColor(int color) {
        prefs.edit().putInt(KEY_OVERLAY_COLOR, color).apply();
    }

    public void setOverlayAlphaPercent(int percent) {
        prefs.edit().putInt(KEY_OVERLAY_ALPHA_NEW, Math.max(0, Math.min(100, percent))).apply();
    }

    /**
     * 批量保存（避免多次 apply）
     */
    public SharedPreferences.Editor edit() {
        return prefs.edit();
    }
}
