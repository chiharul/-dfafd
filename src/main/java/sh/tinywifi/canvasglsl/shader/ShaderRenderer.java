package sh.tinywifi.canvasglsl.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.render.GlobalState;
import sh.tinywifi.canvasglsl.render.ShaderCanvas;
import sh.tinywifi.canvasglsl.render.ShaderPatcher;
import sh.tinywifi.canvasglsl.render.FullscreenQuad;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Random;

public class ShaderRenderer {
    private static final int GL_BLEND_SRC_RGB = 0x80C9;
    private static final int GL_BLEND_DST_RGB = 0x80C8;
    private static final int GL_BLEND_SRC_ALPHA = 0x80CB;
    private static final int GL_BLEND_DST_ALPHA = 0x80CA;
    private static final int GL_BLEND_EQUATION_RGB = 0x8009;
    private static final int GL_BLEND_EQUATION_ALPHA = 0x883D;

    private static boolean RENDERSYSTEM_DEPTH_AVAILABLE = true;
    private static boolean RENDERSYSTEM_BLEND_AVAILABLE = true;
    private static boolean RENDERSYSTEM_CULL_AVAILABLE = true;
    private static boolean RENDERSYSTEM_DEPTH_MASK_AVAILABLE = true;
    private static boolean RENDERSYSTEM_DEFAULT_BLEND_AVAILABLE = true;
    private static boolean RENDERSYSTEM_BLEND_SEPARATE_AVAILABLE = true;

    private int shaderProgram = -1;
    private int vertexShader = -1;
    private int fragmentShader = -1;

    // Multi-buffer support
    private int bufferAProgram = -1;
    private int bufferBProgram = -1;
    private BufferChain bufferChain;
    private ShaderConfig shaderConfig;

    private int timeUniform = -1;
    private int resolutionUniform = -1;
    private int mouseUniform = -1;
    private int imageUniform = -1;
    private int backbufferUniform = -1;
    private int frameUniform = -1;
    private int persistentFrameUniform = -1;
    private int speedUniform = -1;
    private int iTimeUniform = -1;
    private int iResolutionUniform = -1;
    private int iMouseUniform = -1;
    private int iFrameUniform = -1;
    private int iTimeDeltaUniform = -1;
    private int iDateUniform = -1;
    private int iSampleRateUniform = -1;
    private final int[] channelUniforms = new int[4];
    private final int[] channelResolutionUniforms = new int[4];
    private final int[] channelTimeUniforms = new int[4];
    private final int[] channelTextures = new int[4];
    private final int[] channelWidths = new int[4];
    private final int[] channelHeights = new int[4];
    private FullscreenQuad quad;
    private ShaderCanvas canvas;
    private boolean glResourcesInitialized;

    private final MinecraftClient mc;
    private long startTimeNanos;
    private long frameCounter = 0;
    private long lastFrameNanos = 0;
    private boolean panoramaSpeedChecked;
    private Method panoramaSpeedMethod;
    private double lastMouseClickX;
    private double lastMouseClickY;
    private boolean lastMouseDown;
    private boolean hasLoggedCompilationError = false;

    private static final String DEFAULT_VERTEX_SHADER = """
        #version 330 core
        layout(location = 0) in vec3 position;

        void main() {
            gl_Position = vec4(position, 1.0);
        }
        """;

    public ShaderRenderer() {
        this.mc = MinecraftClient.getInstance();
        this.startTimeNanos = System.nanoTime();
    }

    public void setShaderConfig(ShaderConfig config) {
        this.shaderConfig = config;
    }

    public void ensureInitialized() {
        if (glResourcesInitialized) {
            return;
        }
        RenderSystem.assertOnRenderThread();
        initializeQuadBuffer();
        initializeChannelTextures();
        if (canvas == null) {
            canvas = new ShaderCanvas();
        }
        if (bufferChain == null) {
            bufferChain = new BufferChain();
        }
        glResourcesInitialized = true;
    }

    private void initializeQuadBuffer() {
        if (quad != null) {
            return;
        }
        quad = FullscreenQuad.create();
    }

    private void initializeChannelTextures() {
        RenderSystem.assertOnRenderThread();
        final int size = 256;

        for (int i = 0; i < channelTextures.length; i++) {
            channelTextures[i] = GL11.glGenTextures();
            channelWidths[i] = size;
            channelHeights[i] = size;

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, channelTextures[i]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_REPEAT);

            ByteBuffer data = BufferUtils.createByteBuffer(size * size * 4);
            Random random = new Random(0xC0FFEE + i * 997);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float fx = x / (float) (size - 1);
                    float fy = y / (float) (size - 1);
                    int r;
                    int g;
                    int b;

                    switch (i) {
                        case 0 -> {
                            int value = random.nextInt(256);
                            r = value;
                            g = value;
                            b = value;
                        }
                        case 1 -> {
                            r = Math.min(255, Math.round(fx * 255f));
                            g = Math.min(255, Math.round(fy * 255f));
                            b = Math.min(255, Math.round(((fx + fy) * 0.5f) * 255f));
                        }
                        case 2 -> {
                            int stripe = ((x ^ y) & 15) * 16;
                            r = stripe;
                            g = 255 - stripe;
                            b = stripe / 2 + 64;
                        }
                        default -> {
                            r = random.nextInt(256);
                            g = random.nextInt(256);
                            b = random.nextInt(256);
                        }
                    }

                    data.put((byte) r);
                    data.put((byte) g);
                    data.put((byte) b);
                    data.put((byte) 255);
                }
            }
            data.flip();

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public boolean compileShader(String fragmentSource) {
        return compileShader(DEFAULT_VERTEX_SHADER, fragmentSource, null);
    }

    public boolean compileShader(String vertexSource, String fragmentSource, Identifier textureId) {
        RenderSystem.assertOnRenderThread();
        ensureInitialized();
        cleanupShader();
        hasLoggedCompilationError = false;

        try {
            // Check if we have multi-buffer shaders
            if (shaderConfig != null && shaderConfig.hasValidConfig()) {
                return compileMultiBuffer();
            }

            // Single shader mode
            String processedVertex = ShaderPatcher.patchVertex(vertexSource);
            String processedFragment = ShaderPatcher.patchFragment(fragmentSource);

            vertexShader = compileShaderPart(processedVertex, GL20.GL_VERTEX_SHADER);
            if (vertexShader == -1) return false;

            fragmentShader = compileShaderPart(processedFragment, GL20.GL_FRAGMENT_SHADER);
            if (fragmentShader == -1) return false;

            shaderProgram = GL20.glCreateProgram();
            GL20.glAttachShader(shaderProgram, vertexShader);
            GL20.glAttachShader(shaderProgram, fragmentShader);
            GL20.glLinkProgram(shaderProgram);

            if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(shaderProgram, 1024);
                CanvasGLSL.LOG.error("Failed to link shader program! Caused by: {}", log);
                return false;
            }

            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            vertexShader = -1;
            fragmentShader = -1;

            setupUniforms(shaderProgram);
            CanvasGLSL.LOG.info("Shader compiled successfully (single buffer mode)");
            return true;

        } catch (Exception e) {
            CanvasGLSL.LOG.error("Failed to compile shader", e);
            cleanupShader();
            return false;
        }
    }

    /**
     * Compiles all shaders in the buffer chain.
     */
    /**
     * Compiles all shaders in the buffer chain.
     */
    private boolean compileMultiBuffer() {
        String commonCode = shaderConfig.getCommonShader();
        String imageCode = shaderConfig.getImageShader();
        String bufferACode = shaderConfig.getBufferA();
        String bufferBCode = shaderConfig.getBufferB();

        System.out.println("[ShaderRenderer] Compiling multi-buffer shaders");
        System.out.println("  - Image: " + (imageCode.isEmpty() ? "EMPTY" : imageCode.length() + " bytes"));
        System.out.println("  - BufferA: " + (bufferACode.isEmpty() ? "EMPTY" : bufferACode.length() + " bytes"));
        System.out.println("  - BufferB: " + (bufferBCode.isEmpty() ? "EMPTY" : bufferBCode.length() + " bytes"));
        System.out.println("  - Common: " + (commonCode.isEmpty() ? "EMPTY" : commonCode.length() + " bytes"));

        // If we only have image shader, compile it directly (no buffer chain)
        if (!imageCode.isEmpty() && bufferACode.isEmpty() && bufferBCode.isEmpty()) {
            System.out.println("[ShaderRenderer] Single image shader detected, compiling directly");
            String fullCode = commonCode + "\n" + imageCode;
            shaderProgram = compileProgram(fullCode);
            if (shaderProgram == -1) {
                CanvasGLSL.LOG.error("Failed to compile image shader");
                return false;
            }
            setupUniforms(shaderProgram);
            CanvasGLSL.LOG.info("Image shader compiled successfully");
            return true;
        }

        // Compile bufferA if it exists
        if (!bufferACode.isEmpty()) {
            System.out.println("[ShaderRenderer] Compiling bufferA");
            String fullCode = commonCode + "\n" + bufferACode;
            bufferAProgram = compileProgram(fullCode);
            if (bufferAProgram == -1) {
                CanvasGLSL.LOG.error("Failed to compile bufferA");
                return false;
            }
            System.out.println("[ShaderRenderer] BufferA compiled successfully");
        }

        // Compile bufferB if it exists
        if (!bufferBCode.isEmpty()) {
            System.out.println("[ShaderRenderer] Compiling bufferB");
            String fullCode = commonCode + "\n" + bufferBCode;
            bufferBProgram = compileProgram(fullCode);
            if (bufferBProgram == -1) {
                CanvasGLSL.LOG.error("Failed to compile bufferB");
                return false;
            }
            System.out.println("[ShaderRenderer] BufferB compiled successfully");
        }

        // Compile image shader
        if (!imageCode.isEmpty()) {
            System.out.println("[ShaderRenderer] Compiling image shader");
            String fullCode = commonCode + "\n" + imageCode;
            shaderProgram = compileProgram(fullCode);
            if (shaderProgram == -1) {
                CanvasGLSL.LOG.error("Failed to compile image shader");
                return false;
            }
            System.out.println("[ShaderRenderer] Image shader compiled successfully");
        }

        setupUniforms(shaderProgram);
        CanvasGLSL.LOG.info("Multi-buffer shaders compiled successfully");
        return true;
    }

    /**
     * Compiles a single shader program.
     */
    /**
     * Compiles a single shader program.
     */
    private int compileProgram(String fragmentSource) {
        String processedVertex = ShaderPatcher.patchVertex(DEFAULT_VERTEX_SHADER);
        String processedFragment = ShaderPatcher.patchFragment(fragmentSource);

        int vShader = compileShaderPart(processedVertex, GL20.GL_VERTEX_SHADER);
        if (vShader == -1) {
            System.err.println("[ShaderRenderer] Failed to compile vertex shader");
            return -1;
        }

        int fShader = compileShaderPart(processedFragment, GL20.GL_FRAGMENT_SHADER);
        if (fShader == -1) {
            System.err.println("[ShaderRenderer] Failed to compile fragment shader");
            GL20.glDeleteShader(vShader);
            return -1;
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vShader);
        GL20.glAttachShader(program, fShader);
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program, 1024);
            System.err.println("[ShaderRenderer] Failed to link shader program: " + log);
            CanvasGLSL.LOG.error("Failed to link shader program: {}", log);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vShader);
            GL20.glDeleteShader(fShader);
            return -1;
        }

        GL20.glDeleteShader(vShader);
        GL20.glDeleteShader(fShader);
        System.out.println("[ShaderRenderer] Program compiled successfully: " + program);
        return program;
    }

    private void setupUniforms(int program) {
        timeUniform = GL20.glGetUniformLocation(program, "time");
        resolutionUniform = GL20.glGetUniformLocation(program, "resolution");
        mouseUniform = GL20.glGetUniformLocation(program, "mouse");
        frameUniform = GL20.glGetUniformLocation(program, "frame");
        persistentFrameUniform = GL20.glGetUniformLocation(program, "persistent_frame");
        speedUniform = GL20.glGetUniformLocation(program, "speed");
        iTimeUniform = GL20.glGetUniformLocation(program, "iTime");
        iResolutionUniform = GL20.glGetUniformLocation(program, "iResolution");
        iMouseUniform = GL20.glGetUniformLocation(program, "iMouse");
        iFrameUniform = GL20.glGetUniformLocation(program, "iFrame");
        iTimeDeltaUniform = GL20.glGetUniformLocation(program, "iTimeDelta");
        iDateUniform = GL20.glGetUniformLocation(program, "iDate");
        iSampleRateUniform = GL20.glGetUniformLocation(program, "iSampleRate");

        for (int i = 0; i < channelUniforms.length; i++) {
            channelUniforms[i] = GL20.glGetUniformLocation(program, "iChannel" + i);
            channelResolutionUniforms[i] = GL20.glGetUniformLocation(program, "iChannelResolution[" + i + "]");
            channelTimeUniforms[i] = GL20.glGetUniformLocation(program, "iChannelTime[" + i + "]");
        }
    }

    private int compileShaderPart(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 1024);
            String shaderType = (type == GL20.GL_VERTEX_SHADER) ? "vertex" : "fragment";

            if (!hasLoggedCompilationError) {
                CanvasGLSL.LOG.error("Failed to compile {} shader! Caused by: {}", shaderType, log);
                hasLoggedCompilationError = true;
            }
            return -1;
        }

        return shader;
    }

    public void render(int width, int height, float alpha, double quality) {
        RenderSystem.assertOnRenderThread();
        ensureInitialized();

        if (shaderProgram == -1) {
            CanvasGLSL.LOG.error("Render called but shader program is not compiled!");
            return;
        }

        quality = Math.max(0.05, quality);

        Window window = mc.getWindow();
        int framebufferWidth = width;
        int framebufferHeight = height;
        if (window != null) {
            framebufferWidth = Math.max(1, window.getFramebufferWidth());
            framebufferHeight = Math.max(1, window.getFramebufferHeight());
        } else {
            framebufferWidth = Math.max(1, framebufferWidth);
            framebufferHeight = Math.max(1, framebufferHeight);
        }

        int targetWidth = Math.max(1, (int) Math.round(framebufferWidth * quality));
        int targetHeight = Math.max(1, (int) Math.round(framebufferHeight * quality));

        RenderSystem.backupProjectionMatrix();

        IntBuffer viewportBuffer = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportBuffer);
        int prevViewportX = viewportBuffer.get(0);
        int prevViewportY = viewportBuffer.get(1);
        int prevViewportWidth = viewportBuffer.get(2);
        int prevViewportHeight = viewportBuffer.get(3);

        IntBuffer scissorBuffer = BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBuffer);
        int prevScissorX = scissorBuffer.get(0);
        int prevScissorY = scissorBuffer.get(1);
        int prevScissorWidth = scissorBuffer.get(2);
        int prevScissorHeight = scissorBuffer.get(3);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        java.nio.ByteBuffer colorMaskBuffer = BufferUtils.createByteBuffer(4);
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer);
        boolean prevColorMaskRed = colorMaskBuffer.get(0) != 0;
        boolean prevColorMaskGreen = colorMaskBuffer.get(1) != 0;
        boolean prevColorMaskBlue = colorMaskBuffer.get(2) != 0;
        boolean prevColorMaskAlpha = colorMaskBuffer.get(3) != 0;

        boolean framebufferSrgbEnabled = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);

        boolean depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMaskEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int prevBlendSrcRgb = GL11.glGetInteger(GL_BLEND_SRC_RGB);
        int prevBlendDstRgb = GL11.glGetInteger(GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11.glGetInteger(GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11.glGetInteger(GL_BLEND_DST_ALPHA);
        int prevBlendEqRgb = GL11.glGetInteger(GL_BLEND_EQUATION_RGB);
        int prevBlendEqAlpha = GL11.glGetInteger(GL_BLEND_EQUATION_ALPHA);

        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVAO = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevTexture2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int prevArrayBuffer = GL15.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

        canvas.resize(targetWidth, targetHeight);
        bufferChain.initialize(targetWidth, targetHeight);

        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            if (scissorEnabled) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glColorMask(true, true, true, true);
            if (framebufferSrgbEnabled) {
                GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            }

            long nowNanos = System.nanoTime();
            float currentTime = (nowNanos - startTimeNanos) / 1_000_000_000f;

            // Set time for buffer chain
            bufferChain.setTime(currentTime, (int) frameCounter);

            // Render buffer chain if available
// Bind iChannel0 - bufferA output (if exists)
            if (bufferAProgram != -1) {
                int bufferATexture = bufferChain.getBufferTexture("bufferA");
                System.out.println("[ShaderRenderer] BufferA texture ID: " + bufferATexture);
                if (bufferATexture >= 0 && channelUniforms[0] != -1) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, bufferATexture);
                    GL20.glUniform1i(channelUniforms[0], 0);
                    System.out.println("[ShaderRenderer] Bound bufferA to iChannel0");
                } else {
                    System.out.println("[ShaderRenderer] Failed to bind bufferA: texture=" + bufferATexture + ", uniform=" + (channelUniforms[0]));
                }
            }

            // Render final image to canvas
            canvas.write();
            GL11.glViewport(0, 0, targetWidth, targetHeight);

            GL20.glUseProgram(shaderProgram);

            if (timeUniform != -1) {
                GL20.glUniform1f(timeUniform, currentTime);
            }
            if (iTimeUniform != -1) {
                GL20.glUniform1f(iTimeUniform, currentTime);
            }

            if (resolutionUniform != -1) {
                GL20.glUniform2f(resolutionUniform, (float) targetWidth, (float) targetHeight);
            }
            if (iResolutionUniform != -1) {
                GL20.glUniform3f(iResolutionUniform, (float) targetWidth, (float) targetHeight, 1.0f);
            }

            if (mc.mouse != null) {
                float normalizedX = (float) mc.mouse.getX() / Math.max(framebufferWidth, 1);
                float normalizedY = (float) mc.mouse.getY() / Math.max(framebufferHeight, 1);

                if (mouseUniform != -1) {
                    GL20.glUniform2f(mouseUniform, normalizedX, normalizedY);
                }

                float pixelX = normalizedX * targetWidth;
                float pixelY = (1.0f - normalizedY) * targetHeight;
                boolean leftDown = window != null && GLFW.glfwGetMouseButton(window.getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
                if (leftDown && !lastMouseDown) {
                    lastMouseClickX = pixelX;
                    lastMouseClickY = pixelY;
                }
                lastMouseDown = leftDown;

                if (iMouseUniform != -1) {
                    GL20.glUniform4f(
                        iMouseUniform,
                        pixelX,
                        pixelY,
                        (float) lastMouseClickX,
                        (float) lastMouseClickY
                    );
                }
            } else {
                lastMouseDown = false;
                if (mouseUniform != -1) {
                    GL20.glUniform2f(mouseUniform, 0f, 0f);
                }
                if (iMouseUniform != -1) {
                    GL20.glUniform4f(iMouseUniform, 0f, 0f, (float) lastMouseClickX, (float) lastMouseClickY);
                }
            }

            if (frameUniform != -1) {
                GL20.glUniform1i(frameUniform, (int) frameCounter);
            }
            if (iFrameUniform != -1) {
                GL20.glUniform1i(iFrameUniform, (int) frameCounter);
            }
            if (persistentFrameUniform != -1) {
                GL20.glUniform1i(persistentFrameUniform, GlobalState.getFrame());
            }
            if (speedUniform != -1) {
                GL20.glUniform1f(speedUniform, resolvePanoramaSpeed());
            }

            float timeDelta = lastFrameNanos > 0 ? (nowNanos - lastFrameNanos) / 1_000_000_000f : 0.0f;
            lastFrameNanos = nowNanos;
            if (iTimeDeltaUniform != -1) {
                GL20.glUniform1f(iTimeDeltaUniform, timeDelta);
            }

            if (iDateUniform != -1) {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                float timeOfDay = now.getHour() * 3600f + now.getMinute() * 60f + now.getSecond() + now.getNano() / 1_000_000_000f;
                GL20.glUniform4f(iDateUniform,
                    now.getYear(),
                    now.getMonthValue() - 1,
                    now.getDayOfMonth(),
                    timeOfDay);
            }

            if (iSampleRateUniform != -1) {
                GL20.glUniform1f(iSampleRateUniform, 44100.0f);
            }

            // Bind iChannel0 - bufferA output (if exists)
            if (bufferAProgram != -1) {
                int bufferATexture = bufferChain.getBufferTexture("bufferA");
                if (bufferATexture >= 0 && channelUniforms[0] != -1) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, bufferATexture);
                    GL20.glUniform1i(channelUniforms[0], 0);
                }
            }

            // Bind iChannel1 - bufferB output (if exists)
            if (bufferBProgram != -1) {
                int bufferBTexture = bufferChain.getBufferTexture("bufferB");
                if (bufferBTexture >= 0 && channelUniforms[1] != -1) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE1);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, bufferBTexture);
                    GL20.glUniform1i(channelUniforms[1], 1);
                }
            }

            // Bind pre-generated noise textures to remaining channels
            for (int channel = 2; channel < channelUniforms.length; channel++) {
                if (channelUniforms[channel] != -1 && channelTextures[channel] != 0) {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + channel);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, channelTextures[channel]);
                    GL20.glUniform1i(channelUniforms[channel], channel);
                }

                if (channelResolutionUniforms[channel] != -1) {
                    GL20.glUniform3f(
                        channelResolutionUniforms[channel],
                        channelWidths[channel],
                        channelHeights[channel],
                        0f
                    );
                }

                if (channelTimeUniforms[channel] != -1) {
                    GL20.glUniform1f(channelTimeUniforms[channel], currentTime);
                }
            }

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            quad.bind();
            quad.draw();
            FullscreenQuad.unbind();

            GL20.glUseProgram(0);

            frameCounter++;
        } catch (Exception e) {
            CanvasGLSL.LOG.error("Error during shader rendering", e);
        } finally {
            if (canvas != null) {
                canvas.restore();
                canvas.blit(alpha);
            }

            GL11.glViewport(prevViewportX, prevViewportY, prevViewportWidth, prevViewportHeight);

            GL14.glBlendFuncSeparate(prevBlendSrcRgb, prevBlendDstRgb, prevBlendSrcAlpha, prevBlendDstAlpha);
            GL20.glBlendEquationSeparate(prevBlendEqRgb, prevBlendEqAlpha);
            if (blendEnabled) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }

            GL11.glDepthMask(depthMaskEnabled);
            if (depthTestEnabled) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }

            if (cullEnabled) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }

            GL11.glColorMask(prevColorMaskRed, prevColorMaskGreen, prevColorMaskBlue, prevColorMaskAlpha);

            if (framebufferSrgbEnabled) {
                GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB);
            } else {
                GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            }

            GL11.glScissor(prevScissorX, prevScissorY, prevScissorWidth, prevScissorHeight);
            if (scissorEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            GL13.glActiveTexture(prevActiveTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture2D);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevArrayBuffer);

            GL20.glUseProgram(prevProgram);
            GL30.glBindVertexArray(prevVAO);
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private float resolvePanoramaSpeed() {
        if (!panoramaSpeedChecked) {
            panoramaSpeedChecked = true;
            try {
                panoramaSpeedMethod = mc.options.getClass().getMethod("getPanoramaSpeed");
            } catch (NoSuchMethodException e) {
                panoramaSpeedMethod = null;
                CanvasGLSL.LOG.debug("Panorama speed option unavailable on this version; using default speed.");
            }
        }

        if (panoramaSpeedMethod != null) {
            try {
                Object option = panoramaSpeedMethod.invoke(mc.options);
                if (option instanceof SimpleOption<?> simpleOption) {
                    Object value = simpleOption.getValue();
                    if (value instanceof Number number) {
                        return number.floatValue();
                    }
                } else if (option instanceof Number number) {
                    return number.floatValue();
                }
            } catch (ReflectiveOperationException e) {
                CanvasGLSL.LOG.debug("Failed to resolve panorama speed via reflection; falling back to default.", e);
                panoramaSpeedMethod = null;
            }
        }

        return 1.0f;
    }

    private void cleanupShader() {
        if (fragmentShader != -1) {
            GL20.glDeleteShader(fragmentShader);
            fragmentShader = -1;
        }
        if (vertexShader != -1) {
            GL20.glDeleteShader(vertexShader);
            vertexShader = -1;
        }
        if (shaderProgram != -1) {
            GL20.glDeleteProgram(shaderProgram);
            shaderProgram = -1;
        }
        if (bufferAProgram != -1) {
            GL20.glDeleteProgram(bufferAProgram);
            bufferAProgram = -1;
        }
        if (bufferBProgram != -1) {
            GL20.glDeleteProgram(bufferBProgram);
            bufferBProgram = -1;
        }
    }

    public void cleanup() {
        RenderSystem.assertOnRenderThread();
        cleanupShader();
        for (int i = 0; i < channelTextures.length; i++) {
            if (channelTextures[i] != 0) {
                GL11.glDeleteTextures(channelTextures[i]);
                channelTextures[i] = 0;
            }
        }
        if (quad != null) {
            quad.close();
            quad = null;
        }
        if (canvas != null) {
            canvas.close();
            canvas = null;
        }
        if (bufferChain != null) {
            bufferChain.cleanup();
            bufferChain = null;
        }
        glResourcesInitialized = false;
    }

    public boolean isCompiled() {
        return shaderProgram != -1;
    }

    public ShaderCanvas getCanvas() {
        return canvas;
    }

    public void resetTime() {
        this.startTimeNanos = System.nanoTime();
        this.frameCounter = 0;
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public long getFrameCounter() {
        return frameCounter;
    }
}
