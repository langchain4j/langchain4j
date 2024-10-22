package dev.langchain4j.store.embedding.vearch.index;

import dev.langchain4j.store.embedding.vearch.MetricType;

public class FLATParam extends IndexParam {

    public FLATParam() {
    }

    public FLATParam(MetricType metricType) {
        super(metricType);
    }

    public static FLATParamBuilder builder() {
        return new FLATParamBuilder();
    }

    public static class FLATParamBuilder extends IndexParamBuilder<FLATParam, FLATParamBuilder> {

        @Override
        protected FLATParamBuilder self() {
            return this;
        }

        @Override
        public FLATParam build() {
            return new FLATParam(metricType);
        }
    }
}
