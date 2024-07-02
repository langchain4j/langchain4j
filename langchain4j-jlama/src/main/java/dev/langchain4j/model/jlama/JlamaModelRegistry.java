package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A registry for managing Jlama models on local disk.
 */
class JlamaModelRegistry {
    private static final Logger logger = LoggerFactory.getLogger(JlamaModelRegistry.class);

    private static final String DEFAULT_MODEL_CACHE_PATH = System.getProperty("user.home", "") + File.separator + ".jlama" + File.separator + "models";
    private final Path modelCachePath;

    private JlamaModelRegistry(Path modelCachePath) {
        this.modelCachePath = modelCachePath;
        if (!Files.exists(modelCachePath)) {
            try {
                Files.createDirectories(modelCachePath);
            } catch (IOException e) {
                throw new IOError(e);
            }
        }
    }

    public static List<ModelSupport.ModelType> availableModelTypes() {
        return Arrays.stream(ModelSupport.ModelType.values()).toList();
    }

    public static JlamaModelRegistry getOrCreate(Path modelCachePath) {
        return new JlamaModelRegistry(modelCachePath == null ? Path.of(DEFAULT_MODEL_CACHE_PATH) : modelCachePath);
    }

    public Path getModelCachePath() {
        return modelCachePath;
    }

    /**
     * List all the models available in the local cache.
     *
     * @return A list of JlamaModel objects.
     */
    public List<JlamaModel> listLocalModels() {
        List<JlamaModel> localModels = new ArrayList<>();

        for (File file : Objects.requireNonNull(modelCachePath.toFile().listFiles())) {
            if (file.isDirectory()) {
                File config = new File(file, "config.json");
                if (config.exists()) {
                    try {
                        ModelSupport.ModelType type = SafeTensorSupport.detectModel(config);
                        localModels.add(new JlamaModel(this, type, file.getName(), Optional.empty(), file.getName(), false));
                    } catch (IOException e) {
                        logger.warn("Error reading model config: " + config.getAbsolutePath(), e);
                    }
                }
            }
        }

        return localModels;
    }

    public JlamaModel downloadModel(String modelName) throws IOException {
        return downloadModel(modelName, Optional.empty());
    }

    public JlamaModel downloadModel(String modelName, Optional<String> authToken) throws IOException {
        String[] parts = modelName.split("/");
        if (parts.length == 0 || parts.length > 2) {
            throw new IllegalArgumentException("Model must be in the form owner/name");
        }

        String owner;
        String name;

        if (parts.length == 1) {
            owner = null;
            name = modelName;
        } else {
            owner = parts[0];
            name = parts[1];
        }

        File modelDir = SafeTensorSupport.maybeDownloadModel(modelCachePath.toString(), Optional.ofNullable(owner), name, Optional.empty(), authToken, Optional.empty());

        File config = new File(modelDir, "config.json");
        ModelSupport.ModelType type = SafeTensorSupport.detectModel(config);
        return new JlamaModel(this, type, modelDir.getName(), Optional.empty(), modelName, true);
    }
}
