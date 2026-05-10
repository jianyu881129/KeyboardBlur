# KeyboardBlur - LSP 键盘模糊模块

为输入法添加毛玻璃背景模糊效果的 LSPosed 模块，基于豆包输入法 Kawase Blur 算法重构。

## 功能

- **双引擎模糊**：
  - **Window Blur**（Android 12+）：系统级 `setBackgroundBlurRadius`，极快
  - **Kawase GPU**（全版本）：OpenGL ES 3.0 多 pass Kawase 模糊，GPU 加速
  - **Auto 模式**：自动根据系统版本选择最优引擎
- **顶部高光渐变**：模拟 iOS 风格的毛玻璃高光效果
- **半透明遮罩**：可叠加自定义颜色遮罩
- **圆角支持**：可为键盘窗口添加圆角
- **横竖屏独立配置**：竖屏和横屏可设置不同的模糊半径
- **键盘隐藏自动清理**：键盘收起时自动移除模糊，释放 GPU 资源

## Kawase 模糊算法

从豆包输入法 smali 逆向翻译而来，基于 OpenGL ES 3.0：

```
4 个 GLSL Shader：
├── 下采样：5-tap，权重 [0.5, 0.125×4]
├── 上采样：4-tap Kawase，u_blurOffset 控制强度
├── 高质量上采样：8-tap 加权，/12.0
└── 背景采样：支持 Y 轴翻转

4 组 FBO，按 1/4 分辨率创建
模糊半径分档：
  ≥240 → 3 次降采样 + 3 次升采样
  ≥120 → 2 + 2
  ≥50  → 1 + 1
  <50  → 基础模式
```

## 系统要求

- Android 12 (API 31) 及以上
- 已安装 LSPosed 框架（需要 AOSP 版本或 Zygisk 版本）
- Root 权限（KernelSU / Magisk）

## 安装

1. 从 [Actions](../../actions) 下载最新构建的 APK
2. 安装 APK
3. 在 LSPosed 管理器中启用模块
4. 勾选作用域：选择豆包输入法（`com.bytedance.android.doubaoime`）
5. 重启输入法（或重启手机）

## 配置项

| 配置 | 说明 | 默认值 |
|------|------|--------|
| 模糊引擎 | 自动 / 系统级 / Kawase GPU | 自动 |
| 竖屏模糊半径 | 0-200 | 20 |
| 横屏模糊半径 | 0-200 | 5 |
| 模糊 Pass 次数 | 1-6（Kawase 引擎） | 3 |
| 饱和度增强 | 0-200% | 100% |
| 亮度调整 | 50-150% | 100% |
| 圆角半径 | 0-100dp | 0（关闭） |
| 遮罩透明度 | 0-100% | 0 |
| 顶部高光 | 开关 + 透明度/宽度 | 关闭 |

## 项目结构

```
app/src/main/java/com/miclaw/keyboardblur/
├── HookEntry.java        # LSPosed 入口，Hook InputMethodService
├── BlurHelper.java       # 双引擎模糊调度
├── BlurConfig.java       # 配置读写（SharedPreferences）
├── KawaseBlurView.java   # GPU Kawase 模糊核心（GLSurfaceView + GLSL）
├── BlurOverlayView.java  # 模糊叠加容器（模糊+遮罩+高光+圆角）
└── SettingsActivity.java # 设置页面 UI
```

## 编译

### 方式一：GitHub Actions（推荐）
推送代码到 `main` 分支，自动构建 Debug + Release APK。

### 方式二：Android Studio
1. 用 Android Studio 打开项目目录
2. Sync Gradle
3. Build → Generate Signed APK

### 方式三：命令行
```bash
cd KeyboardBlur
./gradlew assembleRelease
```

## 技术参考

- 豆包输入法 `GL3ShaderViewKawaseBlur` smali 逆向
- Kawase Blur 算法：[Simple, Fast Blurring](http://www.iryoku.com/speed-up-your-blur)
- Android `Window.setBackgroundBlurRadius()` API
