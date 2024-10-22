package dev.langchain4j.store.embedding.vearch.index;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.store.embedding.vearch.MetricType;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Index param to construct field and space.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public abstract class IndexParam {

    /**
     * compute type
     */
    protected MetricType metricType;

    protected IndexParam() {
    }

    protected IndexParam(MetricType metricType) {
        this.metricType = metricType;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    protected abstract static class IndexParamBuilder<C extends IndexParam, B extends IndexParamBuilder<C, B>> {

        protected MetricType metricType;

        public B metricType(MetricType metricType) {
            this.metricType = metricType;
            return self();
        }

        protected abstract B self();

        public abstract C build();
    }
}
