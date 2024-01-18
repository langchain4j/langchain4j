package dev.langchain4j.store.embedding.vearch.api.space;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * As a constraint type of all Space property only
 *
 * @see dev.langchain4j.store.embedding.vearch.api.CreateSpaceRequest
 */
public abstract class SpacePropertyParam {

    @Getter
    @Setter
    @Builder
    public static class KeywordParam extends SpacePropertyParam {

        /**
         * whether to create an index
         */
        private Boolean index;
        /**
         * whether to allow multipart value
         */
        private Boolean array;
    }

    @Getter
    @Setter
    @Builder
    public static class IntegerParam extends SpacePropertyParam {

        /**
         * whether to create an index
         *
         * <p>set to true to support the use of numeric range filtering queries <b>(not supported in langchain4j now)</b></p>
         */
        private Boolean index;
    }

    @Getter
    @Setter
    @Builder
    public static class FloatParam extends SpacePropertyParam {

        /**
         * whether to create an index
         *
         * <p>set to true to support the use of numeric range filtering queries <b>(not supported in langchain4j now)</b></p>
         */
        private Boolean index;
    }

    @Getter
    @Setter
    @Builder
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
    }
}
