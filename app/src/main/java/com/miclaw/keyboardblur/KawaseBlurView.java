package com.miclaw.keyboardblur;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.view.Choreographer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

/**
 * 基于 GLSurfaceView 的 Kawase 模糊视图。
 * 从豆包 smali 的 GL3ShaderViewKawaseBlur 翻译而来。
 *
 * 使用 4 组 FBO 进行降采样/升采样 Kawase 模糊，
 * 通过 Choreographer.FrameCallback 驱动连续渲染。
 */
public class KawaseBlurView extends GLSurfaceView {

    private KawaseRenderer renderer;
    private final AtomicBoolean isSetup = new AtomicBoolean(false);

    public KawaseBlurView(Context context) {
        super(context);
        init();
    }

    private void init() {
        setEGLContextClientVersion(3);

        setEGLConfigChooser(new EGLConfigChooser() {
            @Override
            public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
                int[] attribs = {
                        EGL10.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT
                        EGL10.EGL_RED_SIZE, 8,
                        EGL10.EGL_GREEN_SIZE, 8,
                        EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_ALPHA_SIZE, 8,
                        EGL10.EGL_DEPTH_SIZE, 0,
                        EGL10.EGL_STENCIL_SIZE, 0,
                        EGL10.EGL_NONE
                };
                EGLConfig[] configs = new EGLConfig[1];
                int[] numConfigs = new int[1];
                egl.eglChooseConfig(display, attribs, configs, 1, numConfigs);
                return configs[0];
            }
        });

        renderer = new KawaseRenderer();
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        setZOrderOnTop(true);
        getHolder().setFormat(android.graphics.PixelFormat.RGBA_8888);
    }

    public void setBlurRadius(float radius) {
        if (renderer != null) {
            renderer.setBlurRadius(radius);
        }
    }

    public void setBackgroundImage(Bitmap bitmap) {
        if (renderer != null) {
            renderer.setBackgroundImage(bitmap);
        }
    }

    public void requestBlurRender() {
        requestRender();
    }

    public void startContinuousRendering() {
        Choreographer.getInstance().postFrameCallback(frameCallback);
    }

    public void stopContinuousRendering() {
        Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            requestRender();
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    // ==================== Renderer ====================

    private class KawaseRenderer implements Renderer {

        // Shader 源码：降采样 5-tap
        private static final String DOWNSAMPLE_FRAGMENT_SHADER =
                "#version 300 es\n" +
                "precision highp float;\n" +
                "in vec2 v_texCoord;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform vec2 u_texelSize;\n" +
                "out vec4 fragColor;\n" +
                "void main() {\n" +
                "    vec4 sum = texture(u_texture, v_texCoord) * 0.5;\n" +
                "    vec2 offset = u_texelSize * 1.0;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(offset.x, 0.0)) * 0.125;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(-offset.x, 0.0)) * 0.125;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(0.0, offset.y)) * 0.125;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(0.0, -offset.y)) * 0.125;\n" +
                "    fragColor = sum;\n" +
                "}\n";

        // Shader 源码：上采样 Kawase 4-tap
        private static final String UPSAMPLE_FRAGMENT_SHADER =
                "#version 300 es\n" +
                "precision highp float;\n" +
                "in vec2 v_texCoord;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform vec2 u_texelSize;\n" +
                "uniform float u_blurOffset;\n" +
                "uniform float u_divider;\n" +
                "out vec4 fragColor;\n" +
                "void main() {\n" +
                "    vec2 offset = u_texelSize * u_blurOffset * u_divider;\n" +
                "    vec4 sum = vec4(0.0);\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(-offset.x, -offset.y));\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(offset.x, -offset.y));\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(-offset.x, offset.y));\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(offset.x, offset.y));\n" +
                "    fragColor = sum * 0.25;\n" +
                "}\n";

        // Shader 源码：8-tap 高质量 Kawase
        private static final String UPSAMPLE_HQ_FRAGMENT_SHADER =
                "#version 300 es\n" +
                "precision highp float;\n" +
                "in vec2 v_texCoord;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform vec2 u_texelSize;\n" +
                "uniform float u_blurOffset;\n" +
                "uniform float u_divider;\n" +
                "out vec4 fragColor;\n" +
                "void main() {\n" +
                "    vec2 offset = u_texelSize * u_blurOffset * u_divider;\n" +
                "    vec4 sum = vec4(0.0);\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(-offset.x, -offset.y)) * 1.0;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(0.0, -offset.y)) * 2.0;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(offset.x, -offset.y)) * 1.0;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(-offset.x, 0.0)) * 2.0;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(offset.x, 0.0)) * 2.0;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(-offset.x, offset.y)) * 1.0;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(0.0, offset.y)) * 2.0;\n" +
                "    sum += texture(u_texture, v_texCoord + vec2(offset.x, offset.y)) * 1.0;\n" +
                "    fragColor = sum / 12.0;\n" +
                "}\n";

        // Shader 源码：背景采样（支持 Y 轴翻转）
        private static final String BACKGROUND_FRAGMENT_SHADER =
                "#version 300 es\n" +
                "precision highp float;\n" +
                "in vec2 v_texCoord;\n" +
                "uniform sampler2D u_texture;\n" +
                "uniform bool u_revert;\n" +
                "out vec4 fragColor;\n" +
                "void main() {\n" +
                "    vec2 tc = v_texCoord;\n" +
                "    if (u_revert) {\n" +
                "        tc.y = 1.0 - tc.y;\n" +
                "    }\n" +
                "    fragColor = texture(u_texture, tc);\n" +
                "}\n";

        // 顶点着色器（所有 pass 共用）
        private static final String VERTEX_SHADER =
                "#version 300 es\n" +
                "in vec4 a_position;\n" +
                "in vec2 a_texCoord;\n" +
                "out vec2 v_texCoord;\n" +
                "void main() {\n" +
                "    gl_Position = a_position;\n" +
                "    v_texCoord = a_texCoord;\n" +
                "}\n";

        // 全屏四边形顶点
        private static final float[] QUAD_VERTICES = {
                -1.0f, -1.0f,  0.0f, 0.0f,
                 1.0f, -1.0f,  1.0f, 0.0f,
                -1.0f,  1.0f,  0.0f, 1.0f,
                 1.0f,  1.0f,  1.0f, 1.0f,
        };

        private FloatBuffer vertexBuffer;

        // Programs
        private int downsampleProgram;
        private int upsampleProgram;
        private int upsampleHqProgram;
        private int backgroundProgram;

        // FBO 组
        private static final int FBO_COUNT = 4;
        private int[] fboIds;
        private int[] textureIds;
        private int fboWidth;
        private int fboHeight;

        // Uniform locations
        private int uTextureDownsample;
        private int uTexelSizeDownsample;
        private int uTextureUpsample;
        private int uTexelSizeUpsample;
        private int uBlurOffsetUpsample;
        private int uDividerUpsample;
        private int uTextureHq;
        private int uTexelSizeHq;
        private int uBlurOffsetHq;
        private int uDividerHq;
        private int uTextureBg;
        private int uRevertBg;

        // 状态
        private float blurRadius = 20f;
        private int blurPassCount = 3;
        private Bitmap backgroundImage;
        private int bgTextureId = -1;
        private int viewWidth;
        private int viewHeight;
        private boolean fboInitialized = false;
        private final Object bgBitmapLock = new Object();
        private boolean needsBgUpload = false;

        @Override
        public void onSurfaceCreated(GL10 gl, javax.microedition.khronos.egl.EGLConfig config) {
            GLES30.glClearColor(0f, 0f, 0f, 0f);
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            GLES30.glDisable(GLES30.GL_CULL_FACE);

            // 创建顶点 buffer
            ByteBuffer bb = ByteBuffer.allocateDirect(QUAD_VERTICES.length * 4);
            bb.order(ByteOrder.nativeOrder());
            vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(QUAD_VERTICES);
            vertexBuffer.position(0);

            // 编译 shader 程序
            downsampleProgram = createProgram(VERTEX_SHADER, DOWNSAMPLE_FRAGMENT_SHADER);
            upsampleProgram = createProgram(VERTEX_SHADER, UPSAMPLE_FRAGMENT_SHADER);
            upsampleHqProgram = createProgram(VERTEX_SHADER, UPSAMPLE_HQ_FRAGMENT_SHADER);
            backgroundProgram = createProgram(VERTEX_SHADER, BACKGROUND_FRAGMENT_SHADER);

            // 获取 uniform 位置
            uTextureDownsample = GLES30.glGetUniformLocation(downsampleProgram, "u_texture");
            uTexelSizeDownsample = GLES30.glGetUniformLocation(downsampleProgram, "u_texelSize");

            uTextureUpsample = GLES30.glGetUniformLocation(upsampleProgram, "u_texture");
            uTexelSizeUpsample = GLES30.glGetUniformLocation(upsampleProgram, "u_texelSize");
            uBlurOffsetUpsample = GLES30.glGetUniformLocation(upsampleProgram, "u_blurOffset");
            uDividerUpsample = GLES30.glGetUniformLocation(upsampleProgram, "u_divider");

            uTextureHq = GLES30.glGetUniformLocation(upsampleHqProgram, "u_texture");
            uTexelSizeHq = GLES30.glGetUniformLocation(upsampleHqProgram, "u_texelSize");
            uBlurOffsetHq = GLES30.glGetUniformLocation(upsampleHqProgram, "u_blurOffset");
            uDividerHq = GLES30.glGetUniformLocation(upsampleHqProgram, "u_divider");

            uTextureBg = GLES30.glGetUniformLocation(backgroundProgram, "u_texture");
            uRevertBg = GLES30.glGetUniformLocation(backgroundProgram, "u_revert");
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            viewWidth = width;
            viewHeight = height;
            setupFbos(width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

            if (!fboInitialized) return;

            // 上传背景纹理
            uploadBackgroundTexture();

            // 绘制背景到 FBO[0]
            drawBackgroundToFbo();

            // 根据 blurRadius 决定 pass 策略
            int passCount = getPassCount();
            int downsampleCount = passCount;
            int upsampleCount = passCount;

            // 降采样
            int readFbo = 0;
            for (int i = 0; i < downsampleCount; i++) {
                int writeFbo = (i + 1) % FBO_COUNT;
                float divider = (float) Math.pow(2, i + 1);
                bindFbo(writeFbo);
                GLES30.glViewport(0, 0, fboWidth, fboHeight);
                drawDownsample(readFbo, divider);
                readFbo = writeFbo;
            }

            // 升采样
            for (int i = 0; i < upsampleCount; i++) {
                int writeFbo = (downsampleCount + i + 1) % FBO_COUNT;
                float divider = (float) Math.pow(2, upsampleCount - i);
                float offset = 1.0f;
                bindFbo(writeFbo);
                GLES30.glViewport(0, 0, fboWidth, fboHeight);
                if (passCount >= 3) {
                    drawUpsampleHq(readFbo, offset, divider);
                } else {
                    drawUpsample(readFbo, offset, divider);
                }
                readFbo = writeFbo;
            }

            // 最终输出到屏幕
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            GLES30.glViewport(0, 0, viewWidth, viewHeight);
            drawUpsample(readFbo, 1.0f, 1.0f);
        }

        private int getPassCount() {
            if (blurRadius >= 240) return 3;
            if (blurRadius >= 120) return 2;
            if (blurRadius >= 50) return 1;
            if (blurRadius >= 15) return 1;
            return 0;
        }

        private void setupFbos(int width, int height) {
            // 清理旧 FBO
            if (fboInitialized) {
                GLES30.glDeleteFramebuffers(FBO_COUNT, fboIds, 0);
                GLES30.glDeleteTextures(FBO_COUNT, textureIds, 0);
            }

            fboWidth = Math.max(1, width / 4);
            fboHeight = Math.max(1, height / 4);

            fboIds = new int[FBO_COUNT];
            textureIds = new int[FBO_COUNT];

            GLES30.glGenFramebuffers(FBO_COUNT, fboIds, 0);
            GLES30.glGenTextures(FBO_COUNT, textureIds, 0);

            for (int i = 0; i < FBO_COUNT; i++) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[i]);
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                        fboWidth, fboHeight, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[i]);
                GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                        GLES30.GL_TEXTURE_2D, textureIds[i], 0);
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            fboInitialized = true;
        }

        private void uploadBackgroundTexture() {
            synchronized (bgBitmapLock) {
                if (backgroundImage == null || backgroundImage.isRecycled()) return;
                if (needsBgUpload) {
                    if (bgTextureId > 0) {
                        GLES30.glDeleteTextures(1, new int[]{bgTextureId}, 0);
                    }
                    int[] tex = new int[1];
                    GLES30.glGenTextures(1, tex, 0);
                    bgTextureId = tex[0];
                    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bgTextureId);
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
                    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
                    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, backgroundImage, 0);
                    needsBgUpload = false;
                }
            }
        }

        private void drawBackgroundToFbo() {
            if (bgTextureId <= 0) return;
            bindFbo(0);
            GLES30.glViewport(0, 0, fboWidth, fboHeight);

            GLES30.glUseProgram(backgroundProgram);
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bgTextureId);
            GLES30.glUniform1i(uTextureBg, 0);
            GLES30.glUniform1i(uRevertBg, 1);

            drawQuad(backgroundProgram);
        }

        private void drawDownsample(int srcFboIndex, float divider) {
            GLES30.glUseProgram(downsampleProgram);

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[srcFboIndex]);
            GLES30.glUniform1i(uTextureDownsample, 0);

            float texelW = 1.0f / fboWidth;
            float texelH = 1.0f / fboHeight;
            GLES30.glUniform2f(uTexelSizeDownsample, texelW * divider, texelH * divider);

            drawQuad(downsampleProgram);
        }

        private void drawUpsample(int srcFboIndex, float offset, float divider) {
            GLES30.glUseProgram(upsampleProgram);

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[srcFboIndex]);
            GLES30.glUniform1i(uTextureUpsample, 0);

            float texelW = 1.0f / fboWidth;
            float texelH = 1.0f / fboHeight;
            GLES30.glUniform2f(uTexelSizeUpsample, texelW, texelH);
            GLES30.glUniform1f(uBlurOffsetUpsample, offset);
            GLES30.glUniform1f(uDividerUpsample, divider);

            drawQuad(upsampleProgram);
        }

        private void drawUpsampleHq(int srcFboIndex, float offset, float divider) {
            GLES30.glUseProgram(upsampleHqProgram);

            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureIds[srcFboIndex]);
            GLES30.glUniform1i(uTextureHq, 0);

            float texelW = 1.0f / fboWidth;
            float texelH = 1.0f / fboHeight;
            GLES30.glUniform2f(uTexelSizeHq, texelW, texelH);
            GLES30.glUniform1f(uBlurOffsetHq, offset);
            GLES30.glUniform1f(uDividerHq, divider);

            drawQuad(upsampleHqProgram);
        }

        private void bindFbo(int index) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboIds[index]);
        }

        private void drawQuad(int program) {
            int posLoc = GLES30.glGetAttribLocation(program, "a_position");
            int texLoc = GLES30.glGetAttribLocation(program, "a_texCoord");

            vertexBuffer.position(0);
            GLES30.glEnableVertexAttribArray(posLoc);
            GLES30.glVertexAttribPointer(posLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer);

            vertexBuffer.position(2);
            GLES30.glEnableVertexAttribArray(texLoc);
            GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, 16, vertexBuffer);

            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

            GLES30.glDisableVertexAttribArray(posLoc);
            GLES30.glDisableVertexAttribArray(texLoc);
        }

        void setBlurRadius(float radius) {
            this.blurRadius = radius;
        }

        void setBackgroundImage(Bitmap bitmap) {
            synchronized (bgBitmapLock) {
                if (this.backgroundImage != null && !this.backgroundImage.isRecycled()) {
                    this.backgroundImage.recycle();
                }
                this.backgroundImage = bitmap != null ? bitmap.copy(Bitmap.Config.ARGB_8888, false) : null;
                this.needsBgUpload = true;
            }
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
            int fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);

            int program = GLES30.glCreateProgram();
            GLES30.glAttachShader(program, vertexShader);
            GLES30.glAttachShader(program, fragmentShader);
            GLES30.glLinkProgram(program);

            int[] linked = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES30.glGetProgramInfoLog(program);
                GLES30.glDeleteProgram(program);
                throw new RuntimeException("Program link failed: " + log);
            }

            GLES30.glDeleteShader(vertexShader);
            GLES30.glDeleteShader(fragmentShader);
            return program;
        }

        private int loadShader(int type, String source) {
            int shader = GLES30.glCreateShader(type);
            GLES30.glShaderSource(shader, source);
            GLES30.glCompileShader(shader);

            int[] compiled = new int[1];
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES30.glGetShaderInfoLog(shader);
                GLES30.glDeleteShader(shader);
                throw new RuntimeException("Shader compile failed: " + log);
            }
            return shader;
        }
    }
}
