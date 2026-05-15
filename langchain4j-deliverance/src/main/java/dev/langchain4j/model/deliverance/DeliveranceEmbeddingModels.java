package dev.langchain4j.model.deliverance;

import io.teknek.deliverance.DType;
import io.teknek.deliverance.model.AbstractModel;

import java.nio.file.Path;

public final class DeliveranceEmbeddingModels {

    private DeliveranceEmbeddingModels() {
    }

    public static Builder builder(String modelName) {
        return new Builder().modelName(modelName);
    }

    public static Builder builder(String modelName, String authToken) {
        return new Builder().modelName(modelName).authToken(authToken);
    }

    public static Builder builder(Path modelCachePath, String modelName) {
        return new Builder().modelCachePath(modelCachePath).modelName(modelName);
    }

    public static Builder builder(Path modelCachePath, String modelName, String authToken) {
        return new Builder().modelCachePath(modelCachePath).modelName(modelName).authToken(authToken);
    }

    public static class Builder {

        private Path modelCachePath;
        private String modelName;
        private String authToken;
        private Integer threadCount;
        private DType workingMemoryType = DType.F32;
        private DType workingQuantizedType = DType.F32;

        public Builder modelCachePath(Path modelCachePath) {
            this.modelCachePath = modelCachePath;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder threadCount(Integer threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public Builder workingMemoryType(DType workingMemoryType) {
            this.workingMemoryType = workingMemoryType;
            return this;
        }

        public Builder workingQuantizedType(DType workingQuantizedType) {
            this.workingQuantizedType = workingQuantizedType;
            return this;
        }

        public AbstractModel build() {
            return DeliveranceModelSupport.loadEmbeddingModel(this);
        }

        Path modelCachePath() {
            return modelCachePath;
        }

        String modelName() {
            return modelName;
        }

        String authToken() {
            return authToken;
        }

        Integer threadCount() {
            return threadCount;
        }

        DType workingMemoryType() {
            return workingMemoryType;
        }

        DType workingQuantizedType() {
            return workingQuantizedType;
        }

        @Override
        public String toString() {
            return "DeliveranceEmbeddingModels.Builder(modelCachePath=" + modelCachePath
                    + ", modelName=" + modelName
                    + ", authToken=" + authToken
                    + ", threadCount=" + threadCount
                    + ", workingMemoryType=" + workingMemoryType
                    + ", workingQuantizedType=" + workingQuantizedType + ")";
        }
    }
}
