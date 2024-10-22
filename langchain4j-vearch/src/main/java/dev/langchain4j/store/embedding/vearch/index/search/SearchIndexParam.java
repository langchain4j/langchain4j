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
public abstract class SearchIndexParam {

    protected MetricType metricType;

    protected SearchIndexParam() {
    }

    protected SearchIndexParam(MetricType metricType) {
        this.metricType = metricType;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    protected abstract static class SearchIndexParamBuilder<C extends SearchIndexParam, B extends SearchIndexParamBuilder<C, B>> {

        protected MetricType metricType;

        public B metricType(MetricType metricType) {
            this.metricType = metricType;
            return self();
        }

        protected abstract B self();

        public abstract C build();
    }
}
