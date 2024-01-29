package dev.langchain4j.store.embedding.vearch;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * As a constraint type of all Space property only
 *
 * @see CreateSpaceRequest
 */
public abstract class SpacePropertyParam {

    protected SpacePropertyType type;

    SpacePropertyParam(SpacePropertyType type) {
        this.type = type;
    }

    @Getter
    @Setter
    public static class StringParam extends SpacePropertyParam {

        /**
         * whether to create an index
         */
        private Boolean index;
        /**
         * whether to allow multipart value
         */
        private Boolean array;

        public StringParam() {
            super(SpacePropertyType.STRING);
        }

        @Builder
        public StringParam(Boolean index, Boolean array) {
            this();
            this.index = index;
            this.array = array;
        }
    }

    @Getter
    @Setter
    public static class IntegerParam extends SpacePropertyParam {

        /**
         * whether to create an index
         *
         * <p>set to true to support the use of numeric range filtering queries <b>(not supported in langchain4j now)</b></p>
         */
        private Boolean index;

        public IntegerParam() {
            super(SpacePropertyType.INTEGER);
        }

        @Builder
        public IntegerParam(Boolean index) {
            this();
            this.index = index;
        }
    }

    @Getter
    @Setter
    public static class FloatParam extends SpacePropertyParam {

        /**
         * whether to create an index
         *
         * <p>set to true to support the use of numeric range filtering queries <b>(not supported in langchain4j now)</b></p>
         */
        private Boolean index;

        public FloatParam() {
            super(SpacePropertyType.FLOAT);
        }

        @Builder
        public FloatParam(Boolean index) {
            this();
            this.index = index;
        }
    }

    @Getter
    @Setter
    public static class VectorParam extends SpacePropertyParam {

        private Boolean index;
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
            super(SpacePropertyType.VECTOR);
        }

        @Builder
        public VectorParam(Boolean index, Integer dimension, SpaceStoreType storeType,
                           SpaceStoreParam storeParam, String modelId, String format) {
            this();
            this.index = index;
            this.dimension = dimension;
            this.storeType = storeType;
            this.storeParam = storeParam;
            this.modelId = modelId;
            this.format = format;
        }
    }
}
