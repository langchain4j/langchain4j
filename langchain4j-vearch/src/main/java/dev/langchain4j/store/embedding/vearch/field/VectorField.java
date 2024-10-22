package dev.langchain4j.store.embedding.vearch.field;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.store.embedding.vearch.StoreParam;
import dev.langchain4j.store.embedding.vearch.StoreType;
import dev.langchain4j.store.embedding.vearch.index.Index;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class VectorField extends Field {

    private Integer dimension;
    /**
     * "RocksDB" or "MemoryOnly". For HNSW and IVFFLAT and FLAT, it can only be run in MemoryOnly mode.
     *
     * @see StoreType
     */
    private StoreType storeType;
    private StoreParam storeParam;
    private String modelId;
    /**
     * default not normalized. if you set "normalization", "normal" it will normalized
     */
    private String format;

    public VectorField() {
    }

    public VectorField(String name, Index index, Integer dimension, StoreType storeType,
                       StoreParam storeParam, String modelId, String format) {
        super(name, FieldType.VECTOR, index);
        this.dimension = dimension;
        this.storeType = storeType;
        this.storeParam = storeParam;
        this.modelId = modelId;
        this.format = format;
    }

    public Integer getDimension() {
        return dimension;
    }

    public StoreType getStoreType() {
        return storeType;
    }

    public StoreParam getStoreParam() {
        return storeParam;
    }

    public String getModelId() {
        return modelId;
    }

    public String getFormat() {
        return format;
    }

    public static VectorParamBuilder builder() {
        return new VectorParamBuilder();
    }

    public static class VectorParamBuilder extends FieldParamBuilder<VectorField, VectorParamBuilder> {

        private Integer dimension;
        private StoreType storeType;
        private StoreParam storeParam;
        private String modelId;
        private String format;

        public VectorParamBuilder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public VectorParamBuilder storeType(StoreType storeType) {
            this.storeType = storeType;
            return this;
        }

        public VectorParamBuilder storeParam(StoreParam storeParam) {
            this.storeParam = storeParam;
            return this;
        }

        public VectorParamBuilder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public VectorParamBuilder format(String format) {
            this.format = format;
            return this;
        }

        @Override
        protected VectorParamBuilder self() {
            return this;
        }

        @Override
        public VectorField build() {
            return new VectorField(name, index, dimension, storeType, storeParam, modelId, format);
        }
    }
}
