package sh.tinywifi.canvasglsl.shader;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Handles multi-buffer shader rendering (bufferA -> bufferB -> image chain).
 */
public class MultiBufferRenderer {
    private ShaderConfig config;
    private int bufferAProgram = -1;
    private int bufferBProgram = -1;
    private int imageProgram = -1;

    public MultiBufferRenderer(ShaderConfig shaderConfig) {
        this.config = shaderConfig;
    }

    /**
     * Compiles all buffer shaders if they exist.
     * Common shader code is prepended to each buffer.
     */
    public boolean compileBufferShaders(String commonCode) {
        boolean success = true;

        // Compile bufferA
        String bufferACode = config.getBufferA();
        if (!bufferACode.isEmpty()) {
            String fullCode = commonCode + "\n" + bufferACode;
            bufferAProgram = compileProgram(fullCode);
            if (bufferAProgram == -1) {
                success = false;
            }
        }

        // Compile bufferB
        String bufferBCode = config.getBufferB();
        if (!bufferBCode.isEmpty()) {
            String fullCode = commonCode + "\n" + bufferBCode;
            bufferBProgram = compileProgram(fullCode);
            if (bufferBProgram == -1) {
                success = false;
            }
        }

        // Compile image
        String imageCode = config.getImageShader();
        if (!imageCode.isEmpty()) {
            String fullCode = commonCode + "\n" + imageCode;
            imageProgram = compileProgram(fullCode);
            if (imageProgram == -1) {
                success = false;
            }
        }

        return success;
    }

    /**
     * Compiles a shader program from fragment source.
     */
    private int compileProgram(String fragmentSource) {
        // This would use similar logic to ShaderRenderer.compileShader()
        // For now, returning -1 as placeholder - integrate with your shader compilation pipeline
        return -1;
    }

    /**
     * Gets the image program (final output).
     */
    public int getImageProgram() {
        return imageProgram;
    }

    public int getBufferAProgram() {
        return bufferAProgram;
    }

    public int getBufferBProgram() {
        return bufferBProgram;
    }

    /**
     * Cleans up all compiled programs.
     */
    public void cleanup() {
        if (bufferAProgram != -1) {
            GL20.glDeleteProgram(bufferAProgram);
            bufferAProgram = -1;
        }
        if (bufferBProgram != -1) {
            GL20.glDeleteProgram(bufferBProgram);
            bufferBProgram = -1;
        }
        if (imageProgram != -1) {
            GL20.glDeleteProgram(imageProgram);
            imageProgram = -1;
        }
    }
}
