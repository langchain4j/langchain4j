package dev.langchain4j.store.embedding.vearch.index.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.store.embedding.vearch.MetricType;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class FLATSearchParam extends SearchIndexParam {

    public FLATSearchParam() {
    }

    public FLATSearchParam(MetricType metricType) {
        super(metricType);
    }

    public static FLATSearchParamBuilder builder() {
        return new FLATSearchParamBuilder();
    }

    public static class FLATSearchParamBuilder extends SearchIndexParamBuilder<FLATSearchParam, FLATSearchParamBuilder> {

        @Override
        protected FLATSearchParamBuilder self() {
            return this;
        }

        @Override
        public FLATSearchParam build() {
            return new FLATSearchParam(metricType);
        }
    }
}
