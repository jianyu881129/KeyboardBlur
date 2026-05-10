package com.miclaw.keyboardblur;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * 设置页面
 * 
 * 可以通过 LSPosed 管理器打开，也可以直接从桌面启动。
 * 所有设置实时保存到 SharedPreferences，Hook 端即时生效（下次键盘弹出时）。
 */
public class SettingsActivity extends Activity {

    private BlurConfig config;

    // ---- 原有 Views ----
    private TextView tvStatus;
    private TextView tvStatusDetail;
    private SwitchMaterial switchEnable;
    private Slider sliderBlurPortrait;
    private Slider sliderBlurLandscape;
    private Slider sliderOverlayAlpha;
    private Slider sliderDimAmount;
    private SwitchMaterial switchCorner;
    private LinearLayout layoutCornerRadius;
    private Slider sliderCornerRadius;
    private TextView tvBlurPortrait;
    private TextView tvBlurLandscape;
    private TextView tvOverlayAlpha;
    private TextView tvDimAmount;
    private TextView tvCornerRadius;
    private MaterialButton btnRestartIme;

    // ---- 新增 Views ----
    private RadioGroup radioGroupEngine;
    private RadioButton radioAuto;
    private RadioButton radioWindow;
    private RadioButton radioKawase;
    private TextView tvEngineStatus;
    private Slider sliderPassCount;
    private TextView tvPassCount;
    private Slider sliderSaturation;
    private TextView tvSaturation;
    private Slider sliderBrightness;
    private TextView tvBrightness;
    private SwitchMaterial switchGloss;
    private LinearLayout layoutGloss;
    private Slider sliderGlossAlpha;
    private TextView tvGlossAlpha;
    private Slider sliderGlossWidth;
    private TextView tvGlossWidth;
    private Slider sliderOverlayAlphaNew;
    private TextView tvOverlayAlphaNew;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        config = BlurConfig.get(this);
        initViews();
        loadConfig();
        setupListeners();
    }

    private void initViews() {
        // 原有
        tvStatus = findViewById(R.id.tv_status);
        tvStatusDetail = findViewById(R.id.tv_status_detail);
        switchEnable = findViewById(R.id.switch_enable);
        sliderBlurPortrait = findViewById(R.id.slider_blur_portrait);
        sliderBlurLandscape = findViewById(R.id.slider_blur_landscape);
        sliderOverlayAlpha = findViewById(R.id.slider_overlay_alpha);
        sliderDimAmount = findViewById(R.id.slider_dim_amount);
        switchCorner = findViewById(R.id.switch_corner);
        layoutCornerRadius = findViewById(R.id.layout_corner_radius);
        sliderCornerRadius = findViewById(R.id.slider_corner_radius);
        tvBlurPortrait = findViewById(R.id.tv_blur_portrait);
        tvBlurLandscape = findViewById(R.id.tv_blur_landscape);
        tvOverlayAlpha = findViewById(R.id.tv_overlay_alpha);
        tvDimAmount = findViewById(R.id.tv_dim_amount);
        tvCornerRadius = findViewById(R.id.tv_corner_radius);
        btnRestartIme = findViewById(R.id.btn_restart_ime);

        // 新增
        radioGroupEngine = findViewById(R.id.radio_group_engine);
        radioAuto = findViewById(R.id.radio_auto);
        radioWindow = findViewById(R.id.radio_window);
        radioKawase = findViewById(R.id.radio_kawase);
        tvEngineStatus = findViewById(R.id.tv_engine_status);
        sliderPassCount = findViewById(R.id.slider_pass_count);
        tvPassCount = findViewById(R.id.tv_pass_count);
        sliderSaturation = findViewById(R.id.slider_saturation);
        tvSaturation = findViewById(R.id.tv_saturation);
        sliderBrightness = findViewById(R.id.slider_brightness);
        tvBrightness = findViewById(R.id.tv_brightness);
        switchGloss = findViewById(R.id.switch_gloss);
        layoutGloss = findViewById(R.id.layout_gloss);
        sliderGlossAlpha = findViewById(R.id.slider_gloss_alpha);
        tvGlossAlpha = findViewById(R.id.tv_gloss_alpha);
        sliderGlossWidth = findViewById(R.id.slider_gloss_width);
        tvGlossWidth = findViewById(R.id.tv_gloss_width);
        sliderOverlayAlphaNew = findViewById(R.id.slider_overlay_alpha_new);
        tvOverlayAlphaNew = findViewById(R.id.tv_overlay_alpha_new);
    }

    private void loadConfig() {
        // 原有
        switchEnable.setChecked(config.isEnabled());
        sliderBlurPortrait.setValue(config.getBlurPortraitRadius());
        sliderBlurLandscape.setValue(config.getBlurLandscapeRadius());
        sliderOverlayAlpha.setValue(Math.round(config.getOverlayAlpha() * 100f / 255f));
        sliderDimAmount.setValue(Math.round(config.getDimAmount() * 100f / 255f));
        switchCorner.setChecked(config.isCornerEnabled());
        sliderCornerRadius.setValue(config.getCornerRadiusDp());

        tvBlurPortrait.setText(String.valueOf(config.getBlurPortraitRadius()));
        tvBlurLandscape.setText(String.valueOf(config.getBlurLandscapeRadius()));
        tvOverlayAlpha.setText(String.valueOf(Math.round(config.getOverlayAlpha() * 100f / 255f)));
        tvDimAmount.setText(String.valueOf(Math.round(config.getDimAmount() * 100f / 255f)));
        tvCornerRadius.setText(String.valueOf((int) config.getCornerRadiusDp()));

        layoutCornerRadius.setVisibility(config.isCornerEnabled() ? View.VISIBLE : View.GONE);

        // 新增
        loadEngineConfig();
        sliderPassCount.setValue(config.getBlurPassCount());
        tvPassCount.setText(String.valueOf(config.getBlurPassCount()));
        sliderSaturation.setValue(config.getBlurSaturation());
        tvSaturation.setText(String.format("%.1f", config.getBlurSaturation()));
        sliderBrightness.setValue(config.getBlurBrightness());
        tvBrightness.setText(String.format("%.1f", config.getBlurBrightness()));
        switchGloss.setChecked(config.isGlossEnabled());
        layoutGloss.setVisibility(config.isGlossEnabled() ? View.VISIBLE : View.GONE);
        sliderGlossAlpha.setValue(config.getGlossAlpha());
        tvGlossAlpha.setText(String.valueOf(config.getGlossAlpha()));
        sliderGlossWidth.setValue(config.getGlossWidthDp());
        tvGlossWidth.setText(String.valueOf((int) config.getGlossWidthDp()));
        sliderOverlayAlphaNew.setValue(config.getOverlayAlphaPercent());
        tvOverlayAlphaNew.setText(String.valueOf(config.getOverlayAlphaPercent()));

        updateStatus();
    }

    private void loadEngineConfig() {
        String engine = config.getBlurEngine();
        switch (engine) {
            case BlurConfig.ENGINE_WINDOW:
                radioWindow.setChecked(true);
                break;
            case BlurConfig.ENGINE_KAWASE:
                radioKawase.setChecked(true);
                break;
            default:
                radioAuto.setChecked(true);
                break;
        }
        updateEngineStatus();
    }

    private void setupListeners() {
        // 主开关
        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setEnabled(isChecked);
            updateStatus();
        });

        // 竖屏模糊半径
        sliderBlurPortrait.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setBlurPortraitRadius((int) value);
                tvBlurPortrait.setText(String.valueOf((int) value));
            }
        });

        // 横屏模糊半径
        sliderBlurLandscape.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setBlurLandscapeRadius((int) value);
                tvBlurLandscape.setText(String.valueOf((int) value));
            }
        });

        // 遮罩透明度
        sliderOverlayAlpha.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setOverlayAlpha((int) value);
                tvOverlayAlpha.setText(String.valueOf((int) value));
            }
        });

        // 背景变暗量
        sliderDimAmount.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setDimAmount((int) value);
                tvDimAmount.setText(String.valueOf((int) value));
            }
        });

        // 圆角开关
        switchCorner.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setCornerEnabled(isChecked);
            layoutCornerRadius.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 圆角半径
        sliderCornerRadius.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setCornerRadiusDp(value);
                tvCornerRadius.setText(String.valueOf((int) value));
            }
        });

        // ---- 新增监听器 ----

        // 引擎选择
        radioGroupEngine.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_auto) {
                config.setBlurEngine(BlurConfig.ENGINE_AUTO);
            } else if (checkedId == R.id.radio_window) {
                config.setBlurEngine(BlurConfig.ENGINE_WINDOW);
            } else if (checkedId == R.id.radio_kawase) {
                config.setBlurEngine(BlurConfig.ENGINE_KAWASE);
            }
            updateEngineStatus();
        });

        // Pass 次数
        sliderPassCount.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setBlurPassCount((int) value);
                tvPassCount.setText(String.valueOf((int) value));
            }
        });

        // 饱和度
        sliderSaturation.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setBlurSaturation(value);
                tvSaturation.setText(String.format("%.1f", value));
            }
        });

        // 亮度
        sliderBrightness.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setBlurBrightness(value);
                tvBrightness.setText(String.format("%.1f", value));
            }
        });

        // 高光开关
        switchGloss.setOnCheckedChangeListener((buttonView, isChecked) -> {
            config.setGlossEnabled(isChecked);
            layoutGloss.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // 高光透明度
        sliderGlossAlpha.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setGlossAlpha((int) value);
                tvGlossAlpha.setText(String.valueOf((int) value));
            }
        });

        // 高光宽度
        sliderGlossWidth.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setGlossWidthDp(value);
                tvGlossWidth.setText(String.valueOf((int) value));
            }
        });

        // 遮罩透明度（新）
        sliderOverlayAlphaNew.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                config.setOverlayAlphaPercent((int) value);
                tvOverlayAlphaNew.setText(String.valueOf((int) value));
            }
        });

        // 重启输入法按钮
        btnRestartIme.setOnClickListener(v -> restartCurrentIme());
    }

    private void updateStatus() {
        if (config.isEnabled()) {
            tvStatus.setText(R.string.status_active);
            tvStatus.setTextColor(getResources().getColor(R.color.status_active, getTheme()));
        } else {
            tvStatus.setText(R.string.status_inactive);
            tvStatus.setTextColor(getResources().getColor(R.color.status_inactive, getTheme()));
        }
    }

    private void updateEngineStatus() {
        String resolved = config.resolveEngine();
        String engineName;
        switch (resolved) {
            case BlurConfig.ENGINE_WINDOW:
                engineName = "系统级 (Window)";
                break;
            case BlurConfig.ENGINE_KAWASE:
                engineName = "Kawase GPU";
                break;
            default:
                engineName = "自动";
                break;
        }
        String sdkInfo = "API " + Build.VERSION.SDK_INT;
        tvEngineStatus.setText("当前引擎: " + engineName + " | " + sdkInfo);
    }

    /**
     * 重启当前输入法，使模糊效果立即生效
     */
    private void restartCurrentIme() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (getCurrentFocus() != null) {
                imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            }
        }
        Toast.makeText(this, "键盘已关闭，请在输入框中重新点击以刷新", Toast.LENGTH_SHORT).show();
    }
}
