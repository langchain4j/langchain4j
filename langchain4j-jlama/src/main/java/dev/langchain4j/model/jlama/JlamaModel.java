package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import com.github.tjake.jlama.safetensors.prompt.Function;
import com.github.tjake.jlama.safetensors.prompt.Parameters;
import com.github.tjake.jlama.safetensors.prompt.Tool;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A Jlama model. Very basic information. Allows the model to be loaded with different options.
 */
class JlamaModel {
    private final JlamaModelRegistry registry;

    @Getter
    private final ModelSupport.ModelType modelType;

    @Getter
    private final String modelName;

    @Getter
    private final Optional<String> owner;

    @Getter
    private final String modelId;

    private final boolean isLocal;

    public JlamaModel(JlamaModelRegistry registry, ModelSupport.ModelType modelType, String modelName, Optional<String> owner, String modelId, boolean isLocal) {
        this.registry = registry;
        this.modelType = modelType;
        this.modelName = modelName;
        this.owner = owner;
        this.modelId = modelId;
        this.isLocal = isLocal;
    }

    public boolean isLocal() {
        return isLocal;
    }

    public Loader loader() {
        return new Loader();
    }

    public void download(Optional<String> authToken) throws IOException {
        SafeTensorSupport.maybeDownloadModel(
                registry.getModelCachePath().toString(),
                owner,
                modelName,
                Optional.empty(),
                authToken,
                Optional.empty());
    }

    public class Loader {
        private Path workingDirectory;
        private DType quantizationType;
        private Integer threadCount;
        private AbstractModel.InferenceType inferenceType = AbstractModel.InferenceType.FULL_GENERATION;

        private Loader() {
        }

        public Loader quantized() {
            //For now only allow Q4 quantization at load time
            this.quantizationType = DType.Q4;
            return this;
        }

        public Loader workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Loader threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public Loader inferenceType(AbstractModel.InferenceType inferenceType) {
            this.inferenceType = inferenceType;
            return this;
        }

        public AbstractModel load() {
            return ModelSupport.loadModel(
                    inferenceType,
                    new File(registry.getModelCachePath().toFile(), modelName),
                    workingDirectory == null ? null : workingDirectory.toFile(),
                    DType.F32,
                    DType.I8,
                    Optional.ofNullable(quantizationType),
                    Optional.ofNullable(threadCount),
                    Optional.empty());
        }
    }

    public static Tool toTool(ToolSpecification toolSpecification) {
        Function.Builder builder = Function.builder()
                .name(toolSpecification.name())
                .description(toolSpecification.description());

        for (Map.Entry<String, Map<String, Object>> p : toolSpecification.parameters().properties().entrySet()) {
            builder.addParameter(p.getKey(), p.getValue(), toolSpecification.parameters().required().contains(p.getKey()));
        }

        return Tool.from(builder.build());
    }
}
