package dev.langchain4j.store.embedding.vearch.index;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.store.embedding.vearch.MetricType;


public class HNSWParam extends IndexParam {

    /**
     * neighbors number of each node
     *
     * <p>default 32</p>
     */
    @JsonProperty("nlinks")
    private Integer nLinks;
    /**
     * expansion factor at construction time
     *
     * <p>default 40</p>
     * <p>The higher the value, the better the construction effect, and the longer it takes</p>
     */
    @JsonProperty("efConstruction")
    private Integer efConstruction;
    @JsonProperty("efSearch")
    private Integer efSearch;

    public HNSWParam() {
    }

    public HNSWParam(MetricType metricType, Integer nLinks, Integer efConstruction, Integer efSearch) {
        super(metricType);
        this.nLinks = nLinks;
        this.efConstruction = efConstruction;
        this.efSearch = efSearch;
    }

    public Integer getNLinks() {
        return nLinks;
    }

    public Integer getEfConstruction() {
        return efConstruction;
    }

    public Integer getEfSearch() {
        return efSearch;
    }

    public static HNSWParamBuilder builder() {
        return new HNSWParamBuilder();
    }

    public static class HNSWParamBuilder extends IndexParamBuilder<HNSWParam, HNSWParamBuilder> {

        private Integer nLinks;
        private Integer efConstruction;
        private Integer efSearch;

        public HNSWParamBuilder nLinks(Integer nLinks) {
            this.nLinks = nLinks;
            return this;
        }

        public HNSWParamBuilder efConstruction(Integer efConstruction) {
            this.efConstruction = efConstruction;
            return this;
        }

        public HNSWParamBuilder efSearch(Integer efSearch) {
            this.efSearch = efSearch;
            return this;
        }

        @Override
        protected HNSWParamBuilder self() {
            return this;
        }

        @Override
        public HNSWParam build() {
            return new HNSWParam(metricType, nLinks, efConstruction, efSearch);
        }
    }
}
