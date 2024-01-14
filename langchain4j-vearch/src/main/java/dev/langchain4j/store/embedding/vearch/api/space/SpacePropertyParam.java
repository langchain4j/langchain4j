package dev.langchain4j.store.embedding.vearch.api.space;

public abstract class SpacePropertyParam {

    public static class KeywordParam extends SpacePropertyParam {

        /**
         * whether to create an index
         */
        private Boolean index;
        /**
         * whether to allow multipart value
         */
        private Boolean array;

        public Boolean getIndex() {
            return index;
        }

        public void setIndex(Boolean index) {
            this.index = index;
        }

        public Boolean getArray() {
            return array;
        }

        public void setArray(Boolean array) {
            this.array = array;
        }
    }

    public static class IntegerParam extends SpacePropertyParam {

        /**
         * whether to create an index
         *
         * <p>set to true to support the use of numeric range filtering queries <b>(not supported in langchain4j now)</b></p>
         */
        private Boolean index;

        public Boolean getIndex() {
            return index;
        }

        public void setIndex(Boolean index) {
            this.index = index;
        }
    }

    public static class FloatParam extends SpacePropertyParam {

        /**
         * whether to create an index
         *
         * <p>set to true to support the use of numeric range filtering queries <b>(not supported in langchain4j now)</b></p>
         */
        private Boolean index;

        public Boolean getIndex() {
            return index;
        }

        public void setIndex(Boolean index) {
            this.index = index;
        }
    }

    public static class VectorParam extends SpacePropertyParam {

        private Integer dimension;
        private SpaceStoreType storeType;
        private SpaceStoreParam storeParam;
        private String modelId;

        public Integer getDimension() {
            return dimension;
        }

        public void setDimension(Integer dimension) {
            this.dimension = dimension;
        }

        public SpaceStoreType getStoreType() {
            return storeType;
        }

        public void setStoreType(SpaceStoreType storeType) {
            this.storeType = storeType;
        }

        public SpaceStoreParam getStoreParam() {
            return storeParam;
        }

        public void setStoreParam(SpaceStoreParam storeParam) {
            this.storeParam = storeParam;
        }

        public String getModelId() {
            return modelId;
        }

        public void setModelId(String modelId) {
            this.modelId = modelId;
        }
    }
}
