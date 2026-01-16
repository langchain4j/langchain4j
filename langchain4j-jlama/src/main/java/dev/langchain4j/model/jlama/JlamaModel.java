package dev.langchain4j.model.jlama;

import com.github.tjake.jlama.model.AbstractModel;
import com.github.tjake.jlama.model.ModelSupport;
import com.github.tjake.jlama.safetensors.DType;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import com.github.tjake.jlama.safetensors.prompt.Function;
import com.github.tjake.jlama.safetensors.prompt.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.internal.JsonSchemaElementUtils.toMap;

/**
 * A Jlama model. Very basic information. Allows the model to be loaded with different options.
 */
class JlamaModel {
    private final JlamaModelRegistry registry;

    private final ModelSupport.ModelType modelType;

    private final String modelName;

    private final Optional<String> owner;

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
                true,
                Optional.empty(),
                authToken,
                Optional.empty());
    }

    public ModelSupport.ModelType getModelType() {
        return this.modelType;
    }

    public String getModelName() {
        return this.modelName;
    }

    public Optional<String> getOwner() {
        return this.owner;
    }

    public String getModelId() {
        return this.modelId;
    }

    public class Loader {
        private Path workingDirectory;
        private DType workingQuantizationType = DType.I8;
        private DType quantizationType;
        private Integer threadCount;
        private AbstractModel.InferenceType inferenceType = AbstractModel.InferenceType.FULL_GENERATION;

        private Loader() {
        }

        public Loader quantized() {
            //For now only allow Q4 quantization at runtime
            this.quantizationType = DType.Q4;
            return this;
        }

        /**
         * Set the working quantization type. This is the type that the model will use for working inference memory.
         */
        public Loader workingQuantizationType(DType workingQuantizationType) {
            this.workingQuantizationType = workingQuantizationType;
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
                    workingQuantizationType,
                    Optional.ofNullable(quantizationType),
                    Optional.ofNullable(threadCount),
                    Optional.empty(),
                    SafeTensorSupport::loadWeights);
        }
    }

    public static Tool toTool(ToolSpecification toolSpecification) {
        Function.Builder builder = Function.builder()
                .name(toolSpecification.name());

        if (toolSpecification.description() != null) {
            builder.description(toolSpecification.description());
        }

        if (toolSpecification.parameters() != null) {
            JsonObjectSchema parameters = toolSpecification.parameters();
            for (Map.Entry<String, JsonSchemaElement> p : parameters.properties().entrySet()) {
                builder.addParameter(p.getKey(), toMap(p.getValue()), parameters.required().contains(p.getKey()));
            }
        }

        return Tool.from(builder.build());
    }
}
