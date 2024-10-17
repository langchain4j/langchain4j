package dev.langchain4j.store.embedding.vearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * As a constraint type of all Space property only
 *
 * @see CreateSpaceRequest
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public abstract class SpacePropertyParam {

    protected SpacePropertyType type;
    /**
     * Whether to create a index or not.
     */
    protected Boolean index;

    public SpacePropertyParam() {
    }

    protected SpacePropertyParam(SpacePropertyType type, Boolean index) {
        this.type = type;
        this.index = index;
    }

    public SpacePropertyType getType() {
        return type;
    }

    public Boolean getIndex() {
        return index;
    }

    protected abstract static class SpacePropertyParamBuilder<C extends SpacePropertyParam, B extends SpacePropertyParamBuilder<C, B>> {

        protected Boolean index;

        public B index(Boolean index) {
            this.index = index;
            return self();
        }

        protected abstract B self();

        public abstract C build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class StringParam extends SpacePropertyParam {

        /**
         * whether to allow multipart value
         */
        private Boolean array;

        public StringParam() {
        }

        public StringParam(Boolean index, Boolean array) {
            super(SpacePropertyType.STRING, index);
            this.array = array;
        }

        public Boolean getArray() {
            return array;
        }

        public static StringParamBuilder builder() {
            return new StringParamBuilder();
        }

        public static class StringParamBuilder extends SpacePropertyParamBuilder<StringParam, StringParamBuilder> {

            private Boolean array;

            public StringParamBuilder array(Boolean array) {
                this.array = array;
                return this;
            }

            @Override
            protected StringParamBuilder self() {
                return this;
            }

            @Override
            public StringParam build() {
                return new StringParam(index, array);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class IntegerParam extends SpacePropertyParam {

        public IntegerParam() {
        }

        public IntegerParam(Boolean index) {
            super(SpacePropertyType.INTEGER, index);
        }

        public static IntegerParamBuilder builder() {
            return new IntegerParamBuilder();
        }

        public static class IntegerParamBuilder extends SpacePropertyParamBuilder<IntegerParam, IntegerParamBuilder> {

            @Override
            protected IntegerParamBuilder self() {
                return this;
            }

            @Override
            public IntegerParam build() {
                return new IntegerParam(index);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class FloatParam extends SpacePropertyParam {

        public FloatParam() {
        }

        public FloatParam(Boolean index) {
            super(SpacePropertyType.FLOAT, index);
        }

        public static FloatParamBuilder builder() {
            return new FloatParamBuilder();
        }

        public static class FloatParamBuilder extends SpacePropertyParamBuilder<FloatParam, FloatParamBuilder> {

            @Override
            protected FloatParamBuilder self() {
                return this;
            }

            @Override
            public FloatParam build() {
                return new FloatParam(index);
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class VectorParam extends SpacePropertyParam {

        private Integer dimension;
        /**
         * "RocksDB" or "MemoryOnly". For HNSW and IVFFLAT and FLAT, it can only be run in MemoryOnly mode.
         *
         * @see SpaceStoreType
         */
        private SpaceStoreType storeType;
        private SpaceStoreParam storeParam;
        private String modelId;
        /**
         * default not normalized. if you set "normalization", "normal" it will normalized
         */
        private String format;

        public VectorParam() {
        }

        public VectorParam(Boolean index, Integer dimension, SpaceStoreType storeType,
                           SpaceStoreParam storeParam, String modelId, String format) {
            super(SpacePropertyType.VECTOR, index);
            this.dimension = dimension;
            this.storeType = storeType;
            this.storeParam = storeParam;
            this.modelId = modelId;
            this.format = format;
        }

        public Integer getDimension() {
            return dimension;
        }

        public SpaceStoreType getStoreType() {
            return storeType;
        }

        public SpaceStoreParam getStoreParam() {
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

        public static class VectorParamBuilder extends SpacePropertyParamBuilder<VectorParam, VectorParamBuilder> {

            private Integer dimension;
            private SpaceStoreType storeType;
            private SpaceStoreParam storeParam;
            private String modelId;
            private String format;

            public VectorParamBuilder dimension(Integer dimension) {
                this.dimension = dimension;
                return this;
            }

            public VectorParamBuilder storeType(SpaceStoreType storeType) {
                this.storeType = storeType;
                return this;
            }

            public VectorParamBuilder storeParam(SpaceStoreParam storeParam) {
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
            public VectorParam build() {
                return new VectorParam(index, dimension, storeType, storeParam, modelId, format);
            }
        }
    }
}
