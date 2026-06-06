package sh.tinywifi.canvasglsl.shader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages Shadertoy-style shader configurations with multiple buffer layers.
 * Supports: bufferA, bufferB, bufferC, bufferD, image, common
 * Falls back to main shader if no config exists.
 */
public class ShaderConfig {
    private final Path configDir;
    public final Map<String, String> shaders = new HashMap<>();
    private String mainShader = "";
    private boolean hasConfig = false;

    public enum ShaderType {
        BUFFER_A("bufferA"),
        BUFFER_B("bufferB"),
        BUFFER_C("bufferC"),
        BUFFER_D("bufferD"),
        IMAGE("image"),
        COMMON("common");

        private final String name;

        ShaderType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public ShaderConfig(Path configDirectory) {
        this.configDir = configDirectory;
        loadConfig();
    }

    /**
     * Loads shader configuration from the config directory.
     * If config doesn't exist, mainShader remains as fallback.
     */
    public void loadConfig() {
        shaders.clear();
        hasConfig = false;

        if (configDir == null) {
            System.out.println("[ShaderConfig] Config directory is null");
            return;
        }

        if (!Files.exists(configDir)) {
            System.out.println("[ShaderConfig] Config directory does not exist: " + configDir);
            return;
        }

        System.out.println("[ShaderConfig] Loading shaders from: " + configDir);

        try {
            // Try to load each shader type
            for (ShaderType type : ShaderType.values()) {
                Path shaderFile = findShaderFile(configDir, type.getName());
                if (shaderFile != null && Files.exists(shaderFile)) {
                    String content = Files.readString(shaderFile, StandardCharsets.UTF_8);
                    shaders.put(type.getName(), content);
                    hasConfig = true;
                    System.out.println("[ShaderConfig] Loaded " + type.getName() + " from: " + shaderFile.getFileName() + " (" + content.length() + " bytes)");
                } else {
                    System.out.println("[ShaderConfig] " + type.getName() + " not found");
                }
            }
        } catch (IOException e) {
            System.err.println("[ShaderConfig] Failed to load shader config: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[ShaderConfig] Config loaded. hasConfig=" + hasConfig);
    }

    /**
     * Finds a shader file by type name, ignoring file extension.
     * Looks for files starting with the type name.
     */
    private Path findShaderFile(Path directory, String typeName) throws IOException {
        return Files.list(directory)
            .filter(p -> Files.isRegularFile(p))
            .filter(p -> p.getFileName().toString().toLowerCase().startsWith(typeName.toLowerCase()))
            .findFirst()
            .orElse(null);
    }
    public void updateShader(String shaderType, String content) {
        shaders.put(shaderType, content);
    }
    /**
     * Gets the shader for a specific type, or empty string if not found.
     */
    public String getShader(ShaderType type) {
        return shaders.getOrDefault(type.getName(), "");
    }

    /**
     * Gets the common shader code (shared by all buffers).
     */
    public String getCommonShader() {
        return shaders.getOrDefault(ShaderType.COMMON.getName(), "");
    }

    /**
     * Gets the image/final output shader.
     */
    public String getImageShader() {
        return shaders.getOrDefault(ShaderType.IMAGE.getName(), "");
    }

    /**
     * Gets bufferA shader.
     */
    public String getBufferA() {
        return shaders.getOrDefault(ShaderType.BUFFER_A.getName(), "");
    }

    /**
     * Gets bufferB shader.
     */
    public String getBufferB() {
        return shaders.getOrDefault(ShaderType.BUFFER_B.getName(), "");
    }

    /**
     * Gets bufferC shader.
     */
    public String getBufferC() {
        return shaders.getOrDefault(ShaderType.BUFFER_C.getName(), "");
    }

    /**
     * Gets bufferD shader.
     */
    public String getBufferD() {
        return shaders.getOrDefault(ShaderType.BUFFER_D.getName(), "");
    }

    /**
     * Sets the main/fallback shader (used when no config exists).
     */
    public void setMainShader(String shader) {
        this.mainShader = shader;
    }

    /**
     * Gets the main shader (fallback).
     */
    public String getMainShader() {
        return mainShader;
    }

    /**
     * Determines which shader should be rendered.
     * If config exists, uses image shader; otherwise uses main shader.
     */
    public String getActiveShader() {
        if (hasConfig && !getImageShader().isEmpty()) {
            return getImageShader();
        }
        return mainShader;
    }

    /**
     * Checks if a valid config with shader files was found.
     */
    public boolean hasValidConfig() {
        return hasConfig && (!getImageShader().isEmpty() || !getBufferA().isEmpty() || !getBufferB().isEmpty() || !getBufferC().isEmpty() || !getBufferD().isEmpty());
    }

    /**
     * Gets all loaded shaders as a map for debugging.
     */
    public Map<String, String> getAllShaders() {
        return new HashMap<>(shaders);
    }

    /**
     * Reloads configuration from disk.
     */
    public void reload() {
        loadConfig();
    }
}
