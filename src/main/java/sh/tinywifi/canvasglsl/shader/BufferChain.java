package sh.tinywifi.canvasglsl.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.texture.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import sh.tinywifi.canvasglsl.CanvasGLSL;
import sh.tinywifi.canvasglsl.render.FullscreenQuad;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages framebuffers and textures for multi-buffer shader rendering.
 */
public class BufferChain {
    private final Map<String, SimpleFramebuffer> buffers = new HashMap<>();
    private final Map<String, Integer> bufferPrograms = new HashMap<>();
    private final FullscreenQuad quad;
    private int width;
    private int height;
    private float currentTime;
    private int frameCounter;

    public BufferChain() {
        this.quad = FullscreenQuad.create();
        this.currentTime = 0f;
        this.frameCounter = 0;
    }

    /**
     * Initializes or resizes all buffers.
     */
    public void initialize(int width, int height) {
        RenderSystem.assertOnRenderThread();

        if (this.width == width && this.height == height) {
            return;
        }

        this.width = width;
        this.height = height;

        // Create/resize buffers
        for (String name : new String[]{"bufferA", "bufferB"}) {
            SimpleFramebuffer fb = buffers.get(name);
            if (fb == null) {
                fb = new SimpleFramebuffer("canvasglsl_" + name, width, height, false);
                buffers.put(name, fb);
            } else {
                fb.resize(width, height);
            }
        }
    }

    /**
     * Sets the current time for uniforms.
     */
    public void setTime(float time, int frame) {
        this.currentTime = time;
        this.frameCounter = frame;
    }

    /**
     * Sets the program for a specific buffer.
     */
    public void setProgram(String bufferName, int program) {
        bufferPrograms.put(bufferName, program);
    }

    /**
     * Renders a buffer to its framebuffer with all uniforms set.
     */
    /**
     * Renders a buffer to its framebuffer with all uniforms set.
     */
    public void renderBuffer(String bufferName, int program, int iChannel0Texture, int iChannel1Texture) {
        RenderSystem.assertOnRenderThread();

        SimpleFramebuffer fb = buffers.get(bufferName);
        if (fb == null) {
            CanvasGLSL.LOG.error("Buffer {} not initialized", bufferName);
            return;
        }

        // Bind framebuffer
        int prevFb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int fbId = getFramebufferId(fb);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbId);

        // Clear the framebuffer
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        // Setup viewport
        GL11.glViewport(0, 0, width, height);

        // Use shader program
        GL20.glUseProgram(program);

        // Set resolution uniform
        int iResLocation = GL20.glGetUniformLocation(program, "iResolution");
        if (iResLocation != -1) {
            GL20.glUniform3f(iResLocation, width, height, 1.0f);
        }

        // Set time uniform
        int iTimeLocation = GL20.glGetUniformLocation(program, "iTime");
        if (iTimeLocation != -1) {
            GL20.glUniform1f(iTimeLocation, currentTime);
        }

        // Set frame uniform
        int iFrameLocation = GL20.glGetUniformLocation(program, "iFrame");
        if (iFrameLocation != -1) {
            GL20.glUniform1i(iFrameLocation, frameCounter);
        }

        // Bind input textures
        if (iChannel0Texture >= 0) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, iChannel0Texture);
            int iChannel0Location = GL20.glGetUniformLocation(program, "iChannel0");
            if (iChannel0Location != -1) {
                GL20.glUniform1i(iChannel0Location, 0);
            }
        }

        if (iChannel1Texture >= 0) {
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, iChannel1Texture);
            int iChannel1Location = GL20.glGetUniformLocation(program, "iChannel1");
            if (iChannel1Location != -1) {
                GL20.glUniform1i(iChannel1Location, 1);
            }
        }

        // Draw quad
        quad.bind();
        quad.draw();
        FullscreenQuad.unbind();

        // Restore
        GL20.glUseProgram(0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFb);
    }

    /**
     * Gets the texture from a buffer.
     */
    public int getBufferTexture(String bufferName) {
        SimpleFramebuffer fb = buffers.get(bufferName);
        if (fb == null) return -1;

        GpuTexture color = fb.getColorAttachment();
        if (color instanceof GlTexture glTexture) {
            return glTexture.getGlId();
        }
        return -1;
    }

    /**
     * Cleans up all buffers.
     */
    public void cleanup() {
        for (SimpleFramebuffer fb : buffers.values()) {
            if (fb != null) {
                fb.delete();
            }
        }
        buffers.clear();
        bufferPrograms.clear();
        quad.close();
    }

    private static int getFramebufferId(SimpleFramebuffer framebuffer) {
        GpuTexture color = framebuffer.getColorAttachment();
        if (!(color instanceof GlTexture glTexture)) {
            throw new IllegalStateException("Expected GL texture attachment");
        }
        var device = RenderSystem.getDevice();
        if (!(device instanceof GlBackend backend)) {
            throw new IllegalStateException("Only OpenGL backend is supported");
        }
        return glTexture.getOrCreateFramebuffer(backend.getBufferManager(), framebuffer.getDepthAttachment());
    }
}
