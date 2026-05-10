package com.miclaw.keyboardblur;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed 模块入口
 * 
 * 仅对豆包输入法 (com.bytedance.android.doubaoime) 生效。
 * 
 * Hook 策略：
 * 1. onCreateInputView     → 键盘视图首次创建时注入模糊
 * 2. onStartInputView     → 键盘弹出时应用模糊
 * 3. setInputView         → 视图设置时初始化
 * 4. onConfigurationChanged → 屏幕旋转时更新模糊半径
 * 5. onWindowHidden       → 键盘隐藏时清理资源
 * 6. Choreographer.FrameCallback → 连续渲染驱动
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "KeyboardBlur";
    private static final String TARGET_PACKAGE = "com.bytedance.android.doubaoime";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 仅在豆包输入法进程中执行
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        log("Detected target: " + lpparam.packageName);
        hookInputMethodService(lpparam);
    }

    private void hookInputMethodService(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> imeServiceClass;
        try {
            imeServiceClass = XposedHelpers.findClass(
                    "android.inputmethodservice.InputMethodService", null);
        } catch (Throwable t) {
            log("InputMethodService class not found, aborting.");
            return;
        }

        // Hook 1: onCreateInputView — 首次创建输入视图时注入模糊
        hookMethod(imeServiceClass, "onCreateInputView",
                new Class[]{},
                "onCreateInputView", param -> {
                    View view = (View) param.getResult();
                    if (view != null) {
                        applyBlurFromParam(param);
                    }
                });

        // Hook 2: onStartInputView — 键盘弹出时应用模糊
        hookMethod(imeServiceClass, "onStartInputView",
                new Class[]{EditorInfo.class, boolean.class},
                "onStartInputView", param -> applyBlurFromParam(param));

        // Hook 3: setInputView — 首次设置视图时初始化
        hookMethod(imeServiceClass, "setInputView",
                new Class[]{View.class},
                "setInputView", param -> applyBlurFromParam(param));

        // Hook 4: onConfigurationChanged — 横竖屏切换
        hookMethod(imeServiceClass, "onConfigurationChanged",
                new Class[]{Configuration.class},
                "onConfigChanged", param -> applyBlurFromParam(param));

        // Hook 5: onWindowHidden — 键盘隐藏时移除模糊
        hookMethod(imeServiceClass, "onWindowHidden",
                new Class[]{},
                "onWindowHidden", param -> {
                    try {
                        Window window = getServiceWindow(param.thisObject);
                        if (window != null) {
                            Context context = getServiceContext(param.thisObject);
                            BlurHelper.removeBlur(window, context);
                        }
                    } catch (Throwable t) {
                        log("removeBlur failed: " + t.getMessage());
                    }
                });

        // Hook 6: onWindowShown — 键盘显示时重新应用模糊（确保 Kawase 模式下连续渲染恢复）
        hookMethod(imeServiceClass, "onWindowShown",
                new Class[]{},
                "onWindowShown", param -> {
                    try {
                        // 延迟一帧确保窗口已完全显示
                        Window window = getServiceWindow(param.thisObject);
                        if (window != null) {
                            window.getDecorView().post(() -> applyBlurFromParam(param));
                        }
                    } catch (Throwable t) {
                        log("onWindowShown blur apply failed: " + t.getMessage());
                    }
                });

        // Hook 7: onCreateInputConnection — 捕获输入连接用于可能的截图操作
        hookMethod(imeServiceClass, "onCreateInputConnection",
                new Class[]{EditorInfo.class},
                "onCreateInputConnection", param -> {
                    try {
                        // 不修改返回值，仅作为额外触发点
                    } catch (Throwable t) {
                        log("onCreateInputConnection hook failed: " + t.getMessage());
                    }
                });
    }

    private void hookMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes,
                            String label, Callback callback) {
        try {
            // 手动展开 paramTypes 到 Object[]，避免 Class<?>[] 作为单个对象传入 varargs
            Object[] args = new Object[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, args, 0, paramTypes.length);
            args[paramTypes.length] = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        callback.onAfter(param);
                    } catch (Throwable t) {
                        log(label + " failed: " + t.getMessage());
                    }
                }
            };
            XposedHelpers.findAndHookMethod(clazz, methodName, args);
            log("Hooked " + methodName);
        } catch (Throwable t) {
            log("Failed to hook " + methodName + ": " + t.getMessage());
        }
    }

    private void applyBlurFromParam(XC_MethodHook.MethodHookParam param) {
        try {
            android.inputmethodservice.InputMethodService service =
                    (android.inputmethodservice.InputMethodService) param.thisObject;
            Context context = service.getApplicationContext();
            Dialog dialog = service.getWindow();
            if (dialog != null) {
                Window window = dialog.getWindow();
                if (window != null) {
                    log("applyBlurFromParam: context=" + (context != null) + " window=" + window);
                    BlurHelper.applyBlur(context, window);
                } else {
                    log("applyBlurFromParam: window is null (dialog.getWindow())");
                }
            } else {
                log("applyBlurFromParam: dialog is null (service.getWindow())");
            }
        } catch (Throwable t) {
            log("applyBlurFromParam failed: " + t.getMessage());
        }
    }

    private Window getServiceWindow(Object service) {
        try {
            Dialog dialog = ((android.inputmethodservice.InputMethodService) service).getWindow();
            return dialog != null ? dialog.getWindow() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private Context getServiceContext(Object service) {
        try {
            return ((android.inputmethodservice.InputMethodService) service).getApplicationContext();
        } catch (Throwable t) {
            return null;
        }
    }

    private interface Callback {
        void onAfter(XC_MethodHook.MethodHookParam param) throws Throwable;
    }

    private static void log(String msg) {
        XposedBridge.log("[" + TAG + "] " + msg);
    }
}
