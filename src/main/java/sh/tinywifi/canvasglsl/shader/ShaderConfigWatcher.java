package sh.tinywifi.canvasglsl.shader;

import sh.tinywifi.canvasglsl.CanvasGLSL;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Watches the shader config directory for file changes and reloads automatically.
 */
public class ShaderConfigWatcher {
    private final Path configDir;
    private final ShaderConfig config;
    private WatchService watchService;
    private Thread watcherThread;
    private volatile boolean running = false;
    private final Map<String, Long> lastModified = new HashMap<>();

    public ShaderConfigWatcher(ShaderConfig shaderConfig, Path configDirectory) {
        this.config = shaderConfig;
        this.configDir = configDirectory;
    }

    /**
     * Starts watching the config directory for changes.
     */
    public void start() {
        if (running || configDir == null || !Files.exists(configDir)) {
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            configDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            running = true;

            watcherThread = new Thread(() -> {
                while (running) {
                    try {
                        WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                        if (key == null) continue;

                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                                Path modifiedFile = (Path) event.context();
                                onFileModified(configDir.resolve(modifiedFile));
                            }
                        }

                        key.reset();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "ShaderConfigWatcher");
            watcherThread.setDaemon(true);
            watcherThread.start();

            CanvasGLSL.LOG.info("Shader config watcher started for: {}", configDir);
        } catch (IOException e) {
            CanvasGLSL.LOG.error("Failed to start shader config watcher", e);
        }
    }

    /**
     * Stops watching the config directory.
     */
    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                CanvasGLSL.LOG.error("Failed to close watch service", e);
            }
        }
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
    }

    /**
     * Called when a shader file is modified.
     */
    private void onFileModified(Path modifiedFile) {
        if (!Files.isRegularFile(modifiedFile)) {
            return;
        }

        String fileName = modifiedFile.getFileName().toString().toLowerCase();

        // Check if it's one of our shader types
        for (ShaderConfig.ShaderType type : ShaderConfig.ShaderType.values()) {
            if (fileName.startsWith(type.getName().toLowerCase())) {
                try {
                    // Add debounce to prevent multiple rapid reloads
                    long lastMod = lastModified.getOrDefault(fileName, 0L);
                    long currentMod = Files.getLastModifiedTime(modifiedFile).toMillis();

                    if (currentMod - lastMod > 100) { // 100ms debounce
                        lastModified.put(fileName, currentMod);
                        String newContent = Files.readString(modifiedFile, StandardCharsets.UTF_8);
                        config.updateShader(type.getName(), newContent);  // Use the setter method
                        CanvasGLSL.LOG.info("Reloaded shader: {}", type.getName());
                    }
                } catch (IOException e) {
                    CanvasGLSL.LOG.error("Failed to reload shader file: {}", modifiedFile, e);
                }
                break;
            }
        }
    }

    public boolean isRunning() {
        return running;
    }
}
