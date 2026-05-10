package com.miclaw.keyboardblur;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import de.robv.android.xposed.XposedBridge;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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
    private static final boolean DEFAULT_ENABLED = true;
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

    private static final String TAG = "KeyboardBlur";

    /**
     * 创建默认配置实例（当 XSharedPreferences 和 XML 都读不到时使用）
     */
    public static BlurConfig createDefault() {
        android.content.Context ctx = null;
        try {
            // 尝试通过反射获取 ActivityThread 的 Context
            Object at = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
            ctx = (Context) Class.forName("android.app.ActivityThread").getMethod("getSystemContext").invoke(at);
        } catch (Exception e) {
            // ignore
        }
        SharedPreferences defaults = null;
        if (ctx != null) {
            defaults = ctx.getSharedPreferences("keyboard_blur_config", Context.MODE_PRIVATE);
            // 写入默认值确保 isEnabled() 返回 true
            defaults.edit()
                .putBoolean(KEY_ENABLED, true)
                .putInt(KEY_BLUR_PORTRAIT, 20)
                .putInt(KEY_BLUR_LANDSCAPE, 5)
                .putBoolean(KEY_GLOSS_ENABLED, true)
                .putInt(KEY_GLOSS_ALPHA, 30)
                .putFloat(KEY_GLOSS_WIDTH_DP, 60f)
                .putInt(KEY_OVERLAY_ALPHA_NEW, 0)
                .apply();
        }
        if (defaults != null) {
            return new BlurConfig(defaults);
        }
        // 绝对兜底：创建内存级 SharedPreferences
        // 这种方式在 production 环境下不应该出现，但作为最终安全网
        return null;
    }

    /**
     * 直接从 XML 文件解析配置（XSharedPreferences 失败时的 fallback）
     */
    public static BlurConfig fromXmlFile(String packageName) {
        try {
            // 尝试多个可能的路径
            String[] paths = {
                "/data/user_de/0/" + packageName + "/shared_prefs/keyboard_blur_config.xml",
                "/data/data/" + packageName + "/shared_prefs/keyboard_blur_config.xml",
                "/data/user/0/" + packageName + "/shared_prefs/keyboard_blur_config.xml"
            };
            File f = null;
            for (String p : paths) {
                File candidate = new File(p);
                if (candidate.exists() && candidate.canRead()) {
                    f = candidate;
                    break;
                }
            }
            if (f == null) {
                Log.e(TAG, "No readable SharedPreferences XML found");
                return null;
            }
            Log.i(TAG, "Reading config from: " + f.getAbsolutePath());

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(f);

            // 解析 <boolean> 标签
            NodeList booleans = doc.getElementsByTagName("boolean");
            boolean enabled = DEFAULT_ENABLED;
            boolean cornerEnabled = DEFAULT_CORNER_ENABLED;
            boolean glossEnabled = DEFAULT_GLOSS_ENABLED;
            for (int i = 0; i < booleans.getLength(); i++) {
                Element el = (Element) booleans.item(i);
                String name = el.getAttribute("name");
                boolean val = "true".equals(el.getAttribute("value"));
                switch (name) {
                    case "enabled": enabled = val; break;
                    case "corner_enabled": cornerEnabled = val; break;
                    case "gloss_enabled": glossEnabled = val; break;
                }
            }

            // 解析 <int> 标签
            int blurPortrait = DEFAULT_BLUR_PORTRAIT;
            int blurLandscape = DEFAULT_BLUR_LANDSCAPE;
            int overlayAlpha = DEFAULT_OVERLAY_ALPHA;
            int dimAmount = DEFAULT_DIM_AMOUNT;
            int glossAlpha = DEFAULT_GLOSS_ALPHA;
            int overlayColor = DEFAULT_OVERLAY_COLOR_NEW;
            int overlayAlphaNew = DEFAULT_OVERLAY_ALPHA_NEW;
            NodeList ints = doc.getElementsByTagName("int");
            for (int i = 0; i < ints.getLength(); i++) {
                Element el = (Element) ints.item(i);
                String name = el.getAttribute("name");
                int val = Integer.parseInt(el.getAttribute("value"));
                switch (name) {
                    case "blur_portrait_radius": blurPortrait = val; break;
                    case "blur_landscape_radius": blurLandscape = val; break;
                    case "overlay_alpha": overlayAlpha = val; break;
                    case "dim_amount": dimAmount = val; break;
                    case "gloss_alpha": glossAlpha = val; break;
                    case "overlay_color": overlayColor = val; break;
                    case "overlay_alpha_new": overlayAlphaNew = val; break;
                }
            }

            // 解析 <float> 标签
            float cornerRadius = DEFAULT_CORNER_RADIUS;
            float glossWidthDp = DEFAULT_GLOSS_WIDTH_DP;
            NodeList floats = doc.getElementsByTagName("float");
            for (int i = 0; i < floats.getLength(); i++) {
                Element el = (Element) floats.item(i);
                String name = el.getAttribute("name");
                float val = Float.parseFloat(el.getAttribute("value"));
                switch (name) {
                    case "corner_radius_dp": cornerRadius = val; break;
                    case "gloss_width_dp": glossWidthDp = val; break;
                }
            }

            // 解析 <string> 标签
            String blurEngine = DEFAULT_BLUR_ENGINE;
            NodeList strings = doc.getElementsByTagName("string");
            for (int i = 0; i < strings.getLength(); i++) {
                Element el = (Element) strings.item(i);
                String name = el.getAttribute("name");
                String val = el.getTextContent().trim();
                if ("blur_engine".equals(name)) blurEngine = val;
            }

            Log.i(TAG, "XML parse success: enabled=" + enabled + " portrait=" + blurPortrait
                + " engine=" + blurEngine + " gloss=" + glossEnabled);

            // 将解析结果包装为 SharedPreferences（使用内存实现）
            android.content.SharedPreferences memPrefs = new MemSharedPreferences();
            memPrefs.edit()
                .putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_BLUR_PORTRAIT, blurPortrait)
                .putInt(KEY_BLUR_LANDSCAPE, blurLandscape)
                .putBoolean(KEY_CORNER_ENABLED, cornerEnabled)
                .putFloat(KEY_CORNER_RADIUS, cornerRadius)
                .putInt(KEY_OVERLAY_ALPHA, overlayAlpha)
                .putInt(KEY_DIM_AMOUNT, dimAmount)
                .putString(KEY_BLUR_ENGINE, blurEngine)
                .putBoolean(KEY_GLOSS_ENABLED, glossEnabled)
                .putInt(KEY_GLOSS_ALPHA, glossAlpha)
                .putFloat(KEY_GLOSS_WIDTH_DP, glossWidthDp)
                .putInt(KEY_OVERLAY_COLOR, overlayColor)
                .putInt(KEY_OVERLAY_ALPHA_NEW, overlayAlphaNew)
                .apply();
            return new BlurConfig(memPrefs);
        } catch (Exception e) {
            Log.e(TAG, "XML fallback failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * 内存级 SharedPreferences 实现，用于 XML fallback
     */
    private static class MemSharedPreferences implements SharedPreferences {
        private final java.util.Map<String, Object> map = new java.util.HashMap<>();
        @Override public java.util.Map<String, ?> getAll() { return java.util.Collections.unmodifiableMap(map); }
        @Override public String getString(String key, String defValue) { return map.containsKey(key) ? (String) map.get(key) : defValue; }
        @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { return map.containsKey(key) ? (java.util.Set<String>) map.get(key) : defValues; }
        @Override public int getInt(String key, int defValue) { return map.containsKey(key) ? (int) map.get(key) : defValue; }
        @Override public long getLong(String key, long defValue) { return map.containsKey(key) ? (long) map.get(key) : defValue; }
        @Override public float getFloat(String key, float defValue) { return map.containsKey(key) ? (float) map.get(key) : defValue; }
        @Override public boolean getBoolean(String key, boolean defValue) { return map.containsKey(key) ? (boolean) map.get(key) : defValue; }
        @Override public boolean contains(String key) { return map.containsKey(key); }
        @Override public SharedPreferences.Editor edit() { return new MemEditor(this); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
        private static class MemEditor implements SharedPreferences.Editor {
            private final MemSharedPreferences target;
            private final java.util.Map<String, Object> pending = new java.util.HashMap<>();
            MemEditor(MemSharedPreferences t) { target = t; }
            @Override public SharedPreferences.Editor putString(String k, String v) { pending.put(k, v); return this; }
            @Override public SharedPreferences.Editor putStringSet(String k, java.util.Set<String> v) { pending.put(k, v); return this; }
            @Override public SharedPreferences.Editor putInt(String k, int v) { pending.put(k, v); return this; }
            @Override public SharedPreferences.Editor putLong(String k, long v) { pending.put(k, v); return this; }
            @Override public SharedPreferences.Editor putFloat(String k, float v) { pending.put(k, v); return this; }
            @Override public SharedPreferences.Editor putBoolean(String k, boolean v) { pending.put(k, v); return this; }
            @Override public SharedPreferences.Editor remove(String k) { pending.remove(k); target.map.remove(k); return this; }
            @Override public SharedPreferences.Editor clear() { pending.clear(); target.map.clear(); return this; }
            @Override public void apply() { target.map.putAll(pending); }
            @Override public boolean commit() { target.map.putAll(pending); return true; }
        }
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
     * 根据引擎配置和系统版本，解析实际应使用的引擎。
     *
     * HyperOS/MIUI 上 Window.setBackgroundBlurRadius() 对 IME 窗口行为异常：
     * 会把整个屏幕都糊了（IME 窗口的不透明键盘视图挡住了键盘区域的模糊，
     * 导致键盘没变化但键盘之外的区域全糊）。因此 HyperOS 上 Auto 模式强制走 Kawase。
     */
    public String resolveEngine() {
        String engine = getBlurEngine();
        if (ENGINE_AUTO.equals(engine)) {
            if (isHyperOSOrMIUI()) {
                XposedBridge.log("[" + TAG + "] HyperOS/MIUI detected, forcing Kawase engine");
                return ENGINE_KAWASE;
            }
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? ENGINE_WINDOW : ENGINE_KAWASE;
        }
        return engine;
    }

    /**
     * 检测是否运行在 HyperOS / MIUI 上
     */
    private static boolean isHyperOSOrMIUI() {
        try {
            // 检查 System Properties
            String miuiVersion = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, "ro.miui.ui.version.name", "");
            if (miuiVersion != null && !miuiVersion.isEmpty()) return true;

            String hyperOS = (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class, String.class)
                    .invoke(null, "ro.mi.os.version.name", "");
            if (hyperOS != null && !hyperOS.isEmpty()) return true;

            // 兜底：检查 Build.MANUFACTURER
            if ("Xiaomi".equalsIgnoreCase(Build.MANUFACTURER)) return true;
        } catch (Throwable t) {
            // 反射失败，保守检查 MANUFACTURER
            if ("Xiaomi".equalsIgnoreCase(Build.MANUFACTURER)) return true;
        }
        return false;
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
